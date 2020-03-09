/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.commons.embeddeddb.mssql;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import static com.google.common.base.StandardSystemProperty.OS_ARCH;
import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createTempDirectory;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class EmbeddedMsSQL implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedMsSQL.class);

    private static final String JDBC_FORMAT = "jdbc:sqlserver://localhost:%s;databaseName=%s;user=%s";

    private static final String SQLSERVER_SUPERUSER = "SA";

    private static final Duration _STARTUP_WAIT = new Duration(10);// new Duration(10, TimeUnit.SECONDS);

    private final ExecutorService executor = newCachedThreadPool(daemonThreadsNamed("testing-mssql-server-%s"));
    private final Path serverDirectory;
    private final Path dataDirectory;
    private final int port;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, String> mssqlConfig;
    private final Process postmaster;

    public EmbeddedMsSQL() throws IOException {
        this(randomPort());
    }

    public EmbeddedMsSQL(final int port) throws IOException {
        this.port = port;

        serverDirectory = createTempDirectory("testing-mssql-server");
        dataDirectory = serverDirectory.resolve("data");

        mssqlConfig = ImmutableMap.<String, String>builder()
                .put("timezone", "UTC")
                .put("synchronous_commit", "off")
                .put("max_connections", "300")
                .build();

        try {
            unpackSqlserver(serverDirectory);

            sqlServerVersion();
            initdb();
            postmaster = startPostmaster();
        } catch (final IOException e) {
            close();
            throw e;
        }
    }

    private static int randomPort() throws IOException {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static void checkSql(final boolean expression, final String message) throws SQLException {
        if (!expression) {
            throw new SQLException(message);
        }
    }

    private static String getPlatform() {
        return (OS_NAME.value() + "-" + OS_ARCH.value()).replace(' ', '_');
    }

    public String getJdbcUrl(final String userName, final String dbName) {
        return format(JDBC_FORMAT, port, dbName, userName);
    }

    public int getPort() {
        return port;
    }

    public Connection getMssqlDatabase() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl("SA", "killbillg"));
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }

        try {
            pgStop();
        } catch (final Exception e) {
            log.error("could not stop SQL_SERVER in " + serverDirectory.toString(), e);
            if (postmaster != null) {
                postmaster.destroy();
            }
        }

        deleteRecursively(serverDirectory.toAbsolutePath().toFile());

        executor.shutdownNow();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("serverDirectory", serverDirectory)
                          .add("port", port)
                          .toString();
    }

    private void sqlServerVersion() {
        log.info((pgBin("sqlserver")));
    }

    private void initdb() {
        /*system(pgBin("initdb"),
               "-A", "trust",
               "-U", SQLSEREVR_SUPERUSER,
               "-D", dataDirectory.toString(),
               "-E", "UTF-8");*/
    }

    private Process startPostmaster()
            throws IOException {
        final List<String> args = newArrayList(pgBin("mssql"),
                                               "-D", dataDirectory.toString(),
                                               "-p", String.valueOf(port),
                                               "-i",
                                               "-F");

        for (final Entry<String, String> config : mssqlConfig.entrySet()) {
            args.add("-c");
            args.add(config.getKey() + "=" + config.getValue());
        }

        final Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();

        log.info("SQL Server started on port %d");

        startOutputProcessor(process.getInputStream());

        waitForServerStartup(process);

        return process;
    }

    private void waitForServerStartup(final Process process) throws IOException {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        while (Duration.millis(start).compareTo(_STARTUP_WAIT) <= 0) {
            try {
                checkReady();
                log.debug("SQL_SERVER startup finished");
                return;
            } catch (final SQLException e) {
                lastCause = e;
                log.debug("while waiting for SQL_SERVER startup", e);
            }

            try {
                // check if process has exited
                final int value = process.exitValue();
                throw new IOException(format("SQL_SERVER exited with value %d, check stdout for more detail", value));
            } catch (final IllegalThreadStateException ignored) {
                // process is still running, loop and try again
            }

            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IOException("SQL_SERVER failed to start after " + _STARTUP_WAIT, lastCause);
    }

    private void checkReady() throws SQLException {

        try {
            Socket ignored = new Socket("localhost", port);
            // connect succeeded
        } catch (IOException e) {
            throw new SQLException(e);
        }

        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = getMssqlDatabase();
            statement = connection.createStatement();
            resultSet = statement.executeQuery("SELECT 42");
            checkSql(resultSet.next(), "no rows in result set");
            checkSql(resultSet.getInt(1) == 42, "wrong result");
            checkSql(!resultSet.next(), "multiple rows in result set");
        } finally {
            if (connection != null) {
                connection.close();
            }
            if (statement != null) {
                statement.close();
            }
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    private void pgStop() {
        /*system(pgBin("pg_ctl"),
               "stop",
               "-D", dataDirectory.toString(),
               "-m", "fast",
               "-t", "5",
               "-w");*/
    }

    private String pgBin(final String binaryName) {
        return serverDirectory.resolve("bin").resolve(binaryName).toString();
    }

    private void startOutputProcessor(final InputStream in) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteStreams.copy(in, System.err);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void unpackSqlserver(final Path target) throws IOException {
        final String archiveName = format("/mssql-%s.tar.gz", getPlatform());
        final URL url = EmbeddedMsSQL.class.getResource(archiveName);
        if (url == null) {
            throw new RuntimeException("archive not found: " + archiveName);
        }

        final File archive = File.createTempFile("mssql-", null);
        try {
            InputStream in = null;
            try {
                in = url.openStream();
                copy(in, archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } finally {
            if (!archive.delete()) {
                log.warn("failed to delete {}", archive);
            }
        }
    }

    private static boolean deleteRecursively(final File file) {
        boolean success = true;
        if (file.isDirectory()) {
            success = deleteDirectoryContents(file);
        }

        return file.delete() && success;
    }

    private static boolean deleteDirectoryContents(final File directory) {
        Preconditions.checkArgument(directory.isDirectory(), "Not a directory: %s", directory);

        // Don't delete symbolic link directories
        if (isSymbolicLink(directory)) {
            return false;
        }

        boolean success = true;
        for (final File file : listFiles(directory)) {
            success = deleteRecursively(file) && success;
        }
        return success;
    }

    private static boolean isSymbolicLink(final File file) {
        try {
            final File canonicalFile = file.getCanonicalFile();
            final File absoluteFile = file.getAbsoluteFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                   // or the canonical parent path is not the same as the files parent path
                   !canonicalFile.getParent().equals(absoluteFile.getParentFile().getCanonicalPath());
        } catch (final IOException e) {
            // error on the side of caution
            return true;
        }
    }

    private static ImmutableList<File> listFiles(final File dir) {
        final File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.<File>of();
        }
        return ImmutableList.<File>copyOf(files);
    }

    /**
     * Creates a {@link ThreadFactory} that creates named daemon threads.
     * using the specified naming format.
     */
    private static ThreadFactory daemonThreadsNamed(final String nameFormat) {
        return new ThreadFactoryBuilder()
                .setNameFormat(nameFormat)
                .setDaemon(true)
                .setThreadFactory(new ContextClassLoaderThreadFactory(Thread.currentThread().getContextClassLoader()))
                .build();
    }

    private static class ContextClassLoaderThreadFactory implements ThreadFactory {
        private final ClassLoader classLoader;

        ContextClassLoaderThreadFactory(final ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable);
            thread.setContextClassLoader(classLoader);
            return thread;
        }
    }
}
