/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.commons.embeddeddb.mysql;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.EmbeddedDB;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import com.mysql.management.MysqldResource;
import com.mysql.management.MysqldResourceI;

public class MySQLEmbeddedDB extends EmbeddedDB {

    protected final AtomicBoolean started = new AtomicBoolean(false);

    protected MysqlDataSource dataSource;
    protected int port;

    private File dbDir;
    private File dataDir;
    private MysqldResource mysqldResource;

    public MySQLEmbeddedDB() {
        // Avoid dashes - MySQL doesn't like them
        this("database" + UUID.randomUUID().toString().substring(0, 8),
             "user" + UUID.randomUUID().toString().substring(0, 8),
             "pass" + UUID.randomUUID().toString().substring(0, 8));
    }

    public MySQLEmbeddedDB(final String databaseName, final String username, final String password) {
        super(databaseName, username, password, null);

        setPort();
        this.jdbcConnectionString = "jdbc:mysql://localhost:" + port + "/" + databaseName + "?createDatabaseIfNotExist=true&allowMultiQueries=true";
    }

    public MySQLEmbeddedDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        super(databaseName, username, password, jdbcConnectionString);
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.MYSQL;
    }

    @Override
    public void initialize() throws IOException {
        dataSource = new MysqlDataSource();
        dataSource.setDatabaseName(databaseName);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setPort(port);
        // See http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html
        dataSource.setURL(jdbcConnectionString);
    }

    @Override
    public void start() throws IOException {
        if (started.get()) {
            throw new IOException("MySQL is already running: " + jdbcConnectionString);
        }
        startMysql();

        refreshTableNames();
    }

    @Override
    public void refreshTableNames() throws IOException {
        final String query = String.format("select table_name from information_schema.tables where table_schema = '%s' and table_type = 'BASE TABLE';", databaseName);
        try {
            executeQuery(query, new ResultSetJob() {
                @Override
                public void work(final ResultSet resultSet) throws SQLException {
                    allTables.clear();
                    while (resultSet.next()) {
                        allTables.add(resultSet.getString(1));
                    }
                }
            });
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public DataSource getDataSource() throws IOException {
        if (!started.get()) {
            throw new IOException("MySQL is not running");
        }
        return dataSource;
    }

    @Override
    public void stop() throws IOException {
        if (!started.get()) {
            throw new IOException("MySQL is not running");
        }
        stopMysql();
    }

    @Override
    public String getCmdLineConnectionString() {
        return String.format("mysql -u%s -p%s -P%s -S%s/mysql.sock %s", username, password, port, dataDir, databaseName);
    }

    private void startMysql() throws IOException {
        dbDir = File.createTempFile("mysqldb", "");
        if (!dbDir.delete()) {
            logger.warn("Unable to delete " + dbDir.getAbsolutePath());
        }
        if (!dbDir.mkdir()) {
            throw new IOException("Unable to create " + dbDir.getAbsolutePath());
        }

        dataDir = File.createTempFile("mysqldata", "");
        if (!dataDir.delete()) {
            logger.warn("Unable to delete " + dataDir.getAbsolutePath());
        }
        if (!dataDir.mkdir()) {
            throw new IOException("Unable to create " + dataDir.getAbsolutePath());
        }

        final PrintStream out = new PrintStream(new LoggingOutputStream(logger), true);
        mysqldResource = new MysqldResource(dbDir, dataDir, null, out, out);

        final Map<String, String> dbOpts = new HashMap<String, String>();
        dbOpts.put(MysqldResourceI.PORT, Integer.toString(port));
        dbOpts.put(MysqldResourceI.INITIALIZE_USER, "true");
        dbOpts.put(MysqldResourceI.INITIALIZE_PASSWORD, password);
        dbOpts.put(MysqldResourceI.INITIALIZE_USER_NAME, username);
        dbOpts.put("default-time-zone", "+00:00");

        mysqldResource.start("test-mysqld-thread", dbOpts);
        if (!mysqldResource.isRunning()) {
            throw new IllegalStateException("MySQL did not start.");
        } else {
            started.set(true);
            logger.info("MySQL started: " + getCmdLineConnectionString());
        }
    }

    private void stopMysql() throws IOException {
        if (mysqldResource != null) {
            try {
                mysqldResource.shutdown();
            } catch (NullPointerException npe) {
                logger.warn("Failed to shutdown mysql properly ", npe);
            }
            try {
                deleteRecursive(dataDir);
                deleteRecursive(dbDir);
            } catch (FileNotFoundException e) {
                throw new IOException(e);
            }

            started.set(false);
            logger.info("MySQL stopped: " + getCmdLineConnectionString());
        }
    }

    private static boolean deleteRecursive(final File path) throws FileNotFoundException {
        if (!path.exists()) {
            throw new FileNotFoundException(path.getAbsolutePath());
        }
        boolean ret = true;
        if (path.isDirectory()) {
            final File[] files = path.listFiles();
            if (files != null) {
                for (final File f : files) {
                    ret = ret && deleteRecursive(f);
                }
            }
        }
        return ret && path.delete();
    }

    private void setPort() {
        // New socket on any free port
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            port = socket.getLocalPort();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
