/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.commons.embeddeddb.postgresql;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.airlift.command.Command;
import io.airlift.command.CommandFailedException;
import io.airlift.units.Duration;

import static com.google.common.base.StandardSystemProperty.OS_ARCH;
import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createTempDirectory;
import static java.util.concurrent.Executors.newCachedThreadPool;

// Forked from https://github.com/airlift/testing-postgresql-server (as of 0c18d5aa4e67114d5a3f4eb66e899c2397374157)
// Added Java 6 support and ability to configure the port
class KillBillEmbeddedPostgreSql implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(KillBillEmbeddedPostgreSql.class);

    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%s/%s?user=%s";

    private static final String PG_SUPERUSER = "postgres";
    private static final Duration PG_STARTUP_WAIT = new Duration(10, TimeUnit.SECONDS);
    private static final Duration COMMAND_TIMEOUT = new Duration(30, TimeUnit.SECONDS);

    private final ExecutorService executor = newCachedThreadPool(daemonThreadsNamed("testing-postgresql-server-%s"));
    private final Path serverDirectory;
    private final Path dataDirectory;
    private final int port;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, String> postgresConfig;
    private final Process postmaster;

    public KillBillEmbeddedPostgreSql() throws IOException {
        this(randomPort());
    }

    public KillBillEmbeddedPostgreSql(final int port) throws IOException {
        this.port = port;

        serverDirectory = createTempDirectory("testing-postgresql-server");
        dataDirectory = serverDirectory.resolve("data");

        postgresConfig = ImmutableMap.<String, String>builder()
                                     .put("timezone", "UTC")
                                     .put("synchronous_commit", "off")
                                     .put("checkpoint_segments", "64")
                                     .put("max_connections", "300")
                                     .build();

        try {
            unpackPostgres(serverDirectory);

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

    public Connection getPostgresDatabase() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl("postgres", "postgres"));
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }

        try {
            pgStop();
        } catch (final Exception e) {
            log.error("could not stop postmaster in " + serverDirectory.toString(), e);
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

    private void initdb() {
        system(pgBin("initdb"),
               "-A", "trust",
               "-U", PG_SUPERUSER,
               "-D", dataDirectory.toString(),
               "-E", "UTF-8");
    }

    private Process startPostmaster()
            throws IOException {
        final List<String> args = newArrayList(pgBin("postgres"),
                                               "-D", dataDirectory.toString(),
                                               "-p", String.valueOf(port),
                                               "-i",
                                               "-F");

        for (final Entry<String, String> config : postgresConfig.entrySet()) {
            args.add("-c");
            args.add(config.getKey() + "=" + config.getValue());
        }

        final Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();

        log.info("postmaster started on port {}. Waiting up to {} for startup to finish.", port, PG_STARTUP_WAIT);

        waitForServerStartup(process);

        return process;
    }

    private void waitForServerStartup(final Process process) throws IOException {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        while (Duration.nanosSince(start).compareTo(PG_STARTUP_WAIT) <= 0) {
            try {
                checkReady();
                log.debug("postmaster startup finished");
                return;
            } catch (final SQLException e) {
                lastCause = e;
                log.debug("while waiting for postmaster startup", e);
            }

            try {
                // check if process has exited
                final int value = process.exitValue();
                throw new IOException(format("postmaster exited with value %d, check stdout for more detail", value));
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
        throw new IOException("postmaster failed to start after " + PG_STARTUP_WAIT, lastCause);
    }

    private void checkReady() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = getPostgresDatabase();
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
        system(pgBin("pg_ctl"),
               "stop",
               "-D", dataDirectory.toString(),
               "-m", "fast",
               "-t", "5",
               "-w");
    }

    private String pgBin(final String binaryName) {
        return serverDirectory.resolve("bin").resolve(binaryName).toString();
    }

    private String system(final String... command) {
        try {
            return new Command(command)
                    .setTimeLimit(COMMAND_TIMEOUT)
                    .execute(executor)
                    .getCommandOutput();
        } catch (final CommandFailedException e) {
            throw new RuntimeException(e);
        }
    }

    private void unpackPostgres(final Path target) throws IOException {
        final String archiveName = format("/postgresql-%s.tar.gz", getPlatform());
        final URL url = KillBillEmbeddedPostgreSql.class.getResource(archiveName);
        if (url == null) {
            throw new RuntimeException("archive not found: " + archiveName);
        }

        final File archive = File.createTempFile("postgresql-", null);
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
            system("tar", "-xzf", archive.getPath(), "-C", target.toString());
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
