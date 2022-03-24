/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.commons.metrics.servlets;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.killbill.commons.metrics.api.Counter;
import org.killbill.commons.metrics.api.Meter;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;

/**
 * {@link Filter} implementation which captures request information and a breakdown of the response
 * codes being returned.
 * <p>Use it in your servlet.xml like this:<p>
 * <pre>{@code
 * <filter>
 *     <filter-name>instrumentedFilter</filter-name>
 *     <filter-class>org.killbill.commons.metrics.servlets.InstrumentedFilter</filter-class>
 * </filter>
 * <filter-mapping>
 *     <filter-name>instrumentedFilter</filter-name>
 *     <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * }</pre>
 */
public class InstrumentedFilter implements Filter {

    public static final String REGISTRY_ATTRIBUTE = InstrumentedFilter.class.getName() + ".registry";

    private static final String METRIC_PREFIX = "name-prefix";

    private static final String NAME_PREFIX = "responseCodes.";
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int SERVER_ERROR = 500;
    private final Map<Integer, String> meterNamesByStatusCode;

    // initialized after call of init method
    private ConcurrentMap<Integer, Meter> metersByStatusCode;
    private Meter otherMeter;
    private Meter timeoutsMeter;
    private Meter errorsMeter;
    private Counter activeRequests;
    private Timer requestTimer;

    public InstrumentedFilter() {
        this.meterNamesByStatusCode = new HashMap<>(6);
        meterNamesByStatusCode.put(OK, NAME_PREFIX + "ok");
        meterNamesByStatusCode.put(CREATED, NAME_PREFIX + "created");
        meterNamesByStatusCode.put(NO_CONTENT, NAME_PREFIX + "noContent");
        meterNamesByStatusCode.put(BAD_REQUEST, NAME_PREFIX + "badRequest");
        meterNamesByStatusCode.put(NOT_FOUND, NAME_PREFIX + "notFound");
        meterNamesByStatusCode.put(SERVER_ERROR, NAME_PREFIX + "serverError");
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        final MetricRegistry metricsRegistry = getMetricsFactory(filterConfig);

        String metricName = filterConfig.getInitParameter(METRIC_PREFIX);
        if (metricName == null || metricName.isEmpty()) {
            metricName = getClass().getName();
        }

        this.metersByStatusCode = new ConcurrentHashMap<>(meterNamesByStatusCode.size());
        for (final Entry<Integer, String> entry : meterNamesByStatusCode.entrySet()) {
            metersByStatusCode.put(entry.getKey(),
                                   metricsRegistry.meter(String.format("%s.%s", metricName, entry.getValue())));
        }
        this.otherMeter = metricsRegistry.meter(String.format("%s.%s", metricName, NAME_PREFIX + "other"));
        this.timeoutsMeter = metricsRegistry.meter(String.format("%s.%s", metricName, "timeouts"));
        this.errorsMeter = metricsRegistry.meter(String.format("%s.%s", metricName, "errors"));
        this.activeRequests = metricsRegistry.counter(String.format("%s.%s", metricName, "activeRequests"));
        this.requestTimer = metricsRegistry.timer(String.format("%s.%s", metricName, "requests"));
    }

    private MetricRegistry getMetricsFactory(final FilterConfig filterConfig) {
        final MetricRegistry metricsRegistry;

        final Object o = filterConfig.getServletContext().getAttribute(REGISTRY_ATTRIBUTE);
        if (o instanceof MetricRegistry) {
            metricsRegistry = (MetricRegistry) o;
        } else {
            metricsRegistry = new NoOpMetricRegistry();
        }
        return metricsRegistry;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(final ServletRequest request,
                         final ServletResponse response,
                         final FilterChain chain) throws IOException, ServletException {
        final StatusExposingServletResponse wrappedResponse = new StatusExposingServletResponse((HttpServletResponse) response);
        activeRequests.inc(1);
        final long start = System.nanoTime();
        boolean error = false;
        try {
            chain.doFilter(request, wrappedResponse);
        } catch (final IOException | RuntimeException | ServletException e) {
            error = true;
            throw e;
        } finally {
            if (!error && request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new AsyncResultListener(requestTimer, start));
            } else {
                requestTimer.update(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                activeRequests.dec(1);
                if (error) {
                    errorsMeter.mark(1);
                } else {
                    markMeterForStatusCode(wrappedResponse.getStatus());
                }
            }
        }
    }

    private void markMeterForStatusCode(final int status) {
        final Meter metric = metersByStatusCode.get(status);
        if (metric != null) {
            metric.mark(1);
        } else {
            otherMeter.mark(1);
        }
    }

    private static class StatusExposingServletResponse extends HttpServletResponseWrapper {

        // The Servlet spec says: calling setStatus is optional, if no status is set, the default is 200.
        private int httpStatus = 200;

        public StatusExposingServletResponse(final HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(final int sc) throws IOException {
            httpStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(final int sc, final String msg) throws IOException {
            httpStatus = sc;
            super.sendError(sc, msg);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(final int sc, final String sm) {
            httpStatus = sc;
            super.setStatus(sc, sm);
        }

        @Override
        public int getStatus() {
            return httpStatus;
        }

        @Override
        public void setStatus(final int sc) {
            httpStatus = sc;
            super.setStatus(sc);
        }
    }

    private class AsyncResultListener implements AsyncListener {

        private final Timer timer;
        private final long start;
        private boolean done = false;

        public AsyncResultListener(final Timer timer, final long start) {
            this.timer = timer;
            this.start = start;
        }

        @Override
        public void onComplete(final AsyncEvent event) {
            if (!done) {
                final HttpServletResponse suppliedResponse = (HttpServletResponse) event.getSuppliedResponse();
                timer.update(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                activeRequests.dec(1);
                markMeterForStatusCode(suppliedResponse.getStatus());
            }
        }

        @Override
        public void onTimeout(final AsyncEvent event) {
            timer.update(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            activeRequests.dec(1);
            timeoutsMeter.mark(1);
            done = true;
        }

        @Override
        public void onError(final AsyncEvent event) {
            timer.update(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            activeRequests.dec(1);
            errorsMeter.mark(1);
            done = true;
        }

        @Override
        public void onStartAsync(final AsyncEvent event) {
        }
    }
}