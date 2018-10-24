/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.bus.integration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.killbill.billing.rpc.test.queue.gen.ControlApiGrpc;
import org.killbill.billing.rpc.test.queue.gen.EventMsg;
import org.killbill.billing.rpc.test.queue.gen.InitMsg;
import org.killbill.billing.rpc.test.queue.gen.QueueApiGrpc;
import org.killbill.billing.rpc.test.queue.gen.StatusMsg;
import org.killbill.billing.rpc.test.queue.gen.TerminateMsg;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class TestBusRemoteIntegration {

    public static final int DEFAULT_DATA_SERVER_PORT = 21345;

    private static final String DEFAULT_INFLUX_DB_ADDR = "http://127.0.0.1:8086";
    private static final String DEFAULT_INFLUX_DB = "killbill";
    private static final String DEFAULT_INFLUX_USERNAME = "killbill";
    private static final String DEFAULT_INFLUX_PWD = "killbill";

    private static final String DEFAULT_JDBC_CONNECTION = "jdbc:mysql://127.0.0.1:3306/test_events";
    private static final String DEFAULT_DB_USERNAME = "root";
    private static final String DEFAULT_DB_PWD = "root";

    private static final Logger logger = LoggerFactory.getLogger(TestBusRemoteIntegration.class);

    private final InfluxDB influxDB;

    public TestBusRemoteIntegration() {
        this.influxDB = setupInfluxDB();
    }

    private void startServer() throws IOException, InterruptedException {

        final QueueGRPCServer queueServer = new QueueGRPCServer(DEFAULT_DATA_SERVER_PORT, influxDB);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may has been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    queueServer.stop();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.err.println("*** server shut down");
            }
        });

        queueServer.startAndWait();
    }

    public static class QueueGRPCServer {

        private final Server server;
        private final Map<String, TestInstance> activeTests;

        public QueueGRPCServer(final int port, final InfluxDB influxDB) {
            activeTests = new HashMap<String, TestInstance>();
            server = ServerBuilder.forPort(port)
                                  .addService(new ControlService(activeTests, influxDB))
                                  .addService(new QueueService(activeTests, influxDB))
                                  .build();

        }

        public void startAndWait() throws IOException, InterruptedException {
            server.start();
            server.awaitTermination();
        }

        public void stop() throws IOException {
            server.shutdown();
        }

    }

    private InfluxDB setupInfluxDB() {
        InfluxDB influxDB = InfluxDBFactory.connect(DEFAULT_INFLUX_DB_ADDR, DEFAULT_INFLUX_USERNAME, DEFAULT_INFLUX_PWD);
        String dbName = DEFAULT_INFLUX_DB;
        influxDB.setDatabase(dbName);
        String rpName = "aRetentionPolicy";
        influxDB.createRetentionPolicy(rpName, dbName, "30d", "30m", 2, true);
        influxDB.setRetentionPolicy(rpName);

        influxDB.enableBatch(BatchOptions.DEFAULTS);
        return influxDB;
    }

    private static class ControlService extends ControlApiGrpc.ControlApiImplBase {

        private final Map<String, TestInstance> activeTests;
        private final InfluxDB influxDB;

        public ControlService(final Map<String, TestInstance> activeTests, final InfluxDB influxDB) {
            this.activeTests = activeTests;
            this.influxDB = influxDB;
        }

        public synchronized void initialize(InitMsg request, StreamObserver<StatusMsg> responseObserver) {

            logger.info(String.format("Initializing new test %s", request.getName()));

            if (activeTests.containsKey(request.getName())) {
                responseObserver.onNext(StatusMsg.newBuilder().setError(String.format("Test %s already exists", request.getName())).setSuccess(false).build());
            } else {

                final TestInstance testInstance = new TestInstance(request, influxDB, DEFAULT_JDBC_CONNECTION, DEFAULT_DB_USERNAME, DEFAULT_DB_PWD);
                try {
                    testInstance.start();
                    activeTests.put(request.getName(), testInstance);
                    responseObserver.onNext(StatusMsg.newBuilder().setSuccess(true).build());
                } catch (EventBusException e) {
                    logger.warn("Failed to start bus ", e);
                    responseObserver.onNext(StatusMsg.newBuilder().setSuccess(false).build());
                }
            }
            responseObserver.onCompleted();
        }

        public synchronized void terminate(TerminateMsg request, StreamObserver<StatusMsg> responseObserver) {

            logger.info(String.format("Stopping test %s", request.getName()));

            final TestInstance testInstance = activeTests.get(request.getName());

            if (testInstance != null) {
                activeTests.remove(request.getName());
                try {
                    testInstance.stop();
                    responseObserver.onNext(StatusMsg.newBuilder().setSuccess(true).build());
                } catch (EventBusException e) {
                    logger.warn("Failed to stop bus ", e);
                    responseObserver.onNext(StatusMsg.newBuilder().setSuccess(false).build());
                }
            }
            responseObserver.onCompleted();
        }

    }

    private static class QueueService extends QueueApiGrpc.QueueApiImplBase {

        private final InfluxDB influxDB;
        private final Map<String, TestInstance> activeTests;

        public QueueService(final Map<String, TestInstance> activeTests, final InfluxDB influxDB) {
            this.influxDB = influxDB;
            this.activeTests = activeTests;
        }

        public void sendEvent(EventMsg request, StreamObserver<StatusMsg> responseObserver) {

            final TestInstance instance = activeTests.get(request.getName());
            if (instance == null) {
                logger.warn("Ignoring event for test name %s", request.getName());
                return;
            }

            final Point.Builder pointBuilder = Point.measurement("input_events")
                                                    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                                                    .tag(request.getKey(), request.getValue())
                                                    .tag("type", request.getType())
                                                    .tag("source", request.getSource())
                                                    .addField("searchKey1", request.getSearchKey1())
                                                    .addField("searchKey1", request.getSearchKey2());
            try {

                final PersistentBus bus = instance.getBus();
                if (bus !=  null) {
                    bus.post(new TestEvent(request));
                }

                long v = instance.incNbEvents();
                if (v % 100 == 0) {
                    System.err.println(String.format("Got %d events", v));
                }
                responseObserver.onNext(StatusMsg.newBuilder().setSuccess(true).build());
                responseObserver.onCompleted();

            } catch (final EventBusException e) {
                responseObserver.onNext(StatusMsg.newBuilder().setSuccess(false).build());
                responseObserver.onError(e);
                pointBuilder.tag("error", "true");
            } finally {
                influxDB.write(pointBuilder.build());
            }
        }

    }

    public static void main(final String[] args) throws IOException, InterruptedException {
        final TestBusRemoteIntegration test = new TestBusRemoteIntegration();
        test.startServer();
    }

}
