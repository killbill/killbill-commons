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
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class TestBusRemoteIntegration {

    private static final String SERVER_PORT_PROP = "org.killbill.test.server.port";

    private static final String INFLUX_ADDR_PROP = "org.killbill.test.influx.addr";
    private static final String INFLUX_DB_PROP = "org.killbill.test.influx.db";
    private static final String INFLUX_USER_PROP = "org.killbill.test.influx.user";
    private static final String INFLUX_PWD_PROP = "org.killbill.test.influx.pwd";

    private static final String JDBC_CONN_PROP = "org.killbill.test.jdbc.conn";
    private static final String JDBC_USER_PROP = "org.killbill.test.jdbc.user";
    private static final String JDBC_PWD_PROP = "org.killbill.test.jdbc.addr";

    public static final String DEFAULT_DATA_SERVER_PORT = "21345";

    private static final String DEFAULT_INFLUX_ADDR = "http://127.0.0.1:8086";
    private static final String DEFAULT_INFLUX_DB = "killbill";
    private static final String DEFAULT_INFLUX_USERNAME = "killbill";
    private static final String DEFAULT_INFLUX_PWD = "killbill";

    private static final String DEFAULT_JDBC_CONNECTION = "jdbc:mysql://127.0.0.1:3306/test_events";
    private static final String DEFAULT_DB_USERNAME = "root";
    private static final String DEFAULT_DB_PWD = "root";

    private final int serverPort;
    private final String influxDbAddr;
    private final String influxDbName;
    private final String influxUser;
    private final String influxPwd;
    private final String jdbcConn;
    private final String jdbcUser;
    private final String jdbcPwd;

    private static final Logger logger = LoggerFactory.getLogger(TestBusRemoteIntegration.class);

    private final InfluxDB influxDB;

    public TestBusRemoteIntegration(final String serverPort,
                                    final String influxDbAddr,
                                    final String influxDbName,
                                    final String influxUser,
                                    final String influxPwd,
                                    final String jdbcConn,
                                    final String jdbcUser,
                                    final String jdbcPwd) {
        this.serverPort = Integer.valueOf(serverPort);
        this.influxDbAddr = influxDbAddr;
        this.influxDbName = influxDbName;
        this.influxUser = influxUser;
        this.influxPwd = influxPwd;
        this.jdbcConn = jdbcConn;
        this.jdbcUser = jdbcUser;
        this.jdbcPwd = jdbcPwd;

        logger.info(String.format("Started test server serverPort='%d', influxDbAddr='%s', influxDbName='%s', influxUser='%s', influxPwd='%s'," +
                                  " jdbcConn='%s', jdbcUser='%s', jdbcPwd='%s'",
                                  this.serverPort,
                                  this.influxDbAddr,
                                  this.influxDbName,
                                  this.influxUser,
                                  this.influxPwd,
                                  this.jdbcConn,
                                  this.jdbcUser,
                                  this.jdbcPwd));

        this.influxDB = setupInfluxDB();
    }

    private void startServer() throws IOException, InterruptedException {

        final QueueGRPCServer queueServer = new QueueGRPCServer(serverPort, influxDB, jdbcConn, jdbcUser, jdbcPwd);

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

        public QueueGRPCServer(final int port, final InfluxDB influxDB, final String jdbcConn, final String jdbcUser, final String jdbcPwd) {
            activeTests = new HashMap<String, TestInstance>();
            server = ServerBuilder.forPort(port)
                                  .addService(new ControlService(activeTests, influxDB, jdbcConn, jdbcUser, jdbcPwd))
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
        try {
            InfluxDB influxDB = InfluxDBFactory.connect(influxDbAddr, influxUser, influxPwd);
            String dbName = influxDbName;
            influxDB.setDatabase(dbName);
            String rpName = "aRetentionPolicy";
            influxDB.createRetentionPolicy(rpName, dbName, "30d", "30m", 2, true);
            influxDB.setRetentionPolicy(rpName);

            influxDB.enableBatch(BatchOptions.DEFAULTS);
            return influxDB;

        } catch (org.influxdb.InfluxDBIOException e) {
            logger.warn("Failed to connect to influxDB, skip metrics");
        }
        return null;
    }

    private static class ControlService extends ControlApiGrpc.ControlApiImplBase {

        private final Map<String, TestInstance> activeTests;
        private final InfluxDB influxDB;
        private final String jdbcConn;
        private final String jdbcUser;
        private final String jdbcPwd;

        public ControlService(final Map<String, TestInstance> activeTests, final InfluxDB influxDB, final String jdbcConn, final String jdbcUser, final String jdbcPwd) {
            this.activeTests = activeTests;
            this.influxDB = influxDB;
            this.jdbcConn = jdbcConn;
            this.jdbcUser = jdbcUser;
            this.jdbcPwd = jdbcPwd;
        }

        public synchronized void initialize(InitMsg request, StreamObserver<StatusMsg> responseObserver) {

            logger.info(String.format("Initializing new test %s", request.getName()));

            if (activeTests.containsKey(request.getName())) {
                responseObserver.onNext(StatusMsg.newBuilder().setError(String.format("Test %s already exists", request.getName())).setSuccess(false).build());
            } else {

                final TestInstance testInstance = new TestInstance(request, influxDB, jdbcConn, jdbcUser, jdbcPwd);
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

                // We use the source to decide whether this is an event or an entry we want to manually add in the queue
                if (Strings.isNullOrEmpty(request.getSource())) {
                    instance.postEntry(request);
                } else {
                    instance.insertEntryIntoQueue(request);
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
                if (influxDB != null) {
                    influxDB.write(pointBuilder.build());
                }
            }
        }

    }

    public static void main(final String[] args) throws IOException, InterruptedException {

        final TestBusRemoteIntegration test = new TestBusRemoteIntegration(System.getProperty(SERVER_PORT_PROP, DEFAULT_DATA_SERVER_PORT),
                                                                           System.getProperty(INFLUX_ADDR_PROP, DEFAULT_INFLUX_ADDR),
                                                                           System.getProperty(INFLUX_DB_PROP, DEFAULT_INFLUX_DB),
                                                                           System.getProperty(INFLUX_USER_PROP, DEFAULT_INFLUX_USERNAME),
                                                                           System.getProperty(INFLUX_PWD_PROP, DEFAULT_INFLUX_PWD),
                                                                           System.getProperty(JDBC_CONN_PROP, DEFAULT_JDBC_CONNECTION),
                                                                           System.getProperty(JDBC_USER_PROP, DEFAULT_DB_USERNAME),
                                                                           System.getProperty(JDBC_PWD_PROP, DEFAULT_DB_PWD));
        test.startServer();
    }

}
