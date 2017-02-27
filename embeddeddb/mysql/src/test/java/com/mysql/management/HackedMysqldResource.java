/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package com.mysql.management;


/*
 Copyright (C) 2004-2008 MySQL AB, 2008-2009 Sun Microsystems, Inc. All rights reserved.
 Use is subject to license terms.

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2 as
 published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 */

import com.mysql.jdbc.Driver;
import com.mysql.jdbc.MysqlErrorNumbers;
import com.mysql.management.util.CommandLineOptionsParser;
import com.mysql.management.util.InitializeUser;
import com.mysql.management.util.ListToString;
import com.mysql.management.util.NullPrintStream;
import com.mysql.management.util.Platform;
import com.mysql.management.util.ProcessUtil;
import com.mysql.management.util.Shell;
import com.mysql.management.util.Streams;
import com.mysql.management.util.Threads;
import com.mysql.management.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;


// Identical to its copied class MysqldResource, except for STEPH comment below
public final class HackedMysqldResource implements MysqldResourceI {

    public static final String MYSQL_C_MXJ = "mysql-c.mxj";

    public static final String DATA = "data";

    private String versionString;

    private Map options;

    private Shell shell;

    private final File baseDir;

    private final File dataDir;

    private final File pidFile;

    private final File portFile;

    private String msgPrefix;

    private String pid;

    private Properties platformProperties;

    private Properties connectorMxjProperties;

    private String osName_osArch;

    private PrintStream out;

    private PrintStream err;

    private PrintStream debug;

    private Exception trace;

    private int killDelay;

    private List completionListensers;

    private boolean readyForConnections;

    private String windowsKillCommand;

    // collaborators
    private HelpOptionsParser optionParser;

    private Utils utils;

    public HackedMysqldResource() {
        this(null, null, null, null, null, null);
    }

    public HackedMysqldResource(File baseDir) {
        this(baseDir, null, null, null, null, null);
    }

    public HackedMysqldResource(File baseDir, File dataDir) {
        this(baseDir, dataDir, null, null, null, null);
    }

    public HackedMysqldResource(File baseDir, File dataDir, String mysqlVersionString) {
        this(baseDir, dataDir, mysqlVersionString, null, null, null);
    }

    public HackedMysqldResource(File baseDir, File dataDir,
                                String mysqlVersionString, PrintStream out, PrintStream err, PrintStream debug) {
        this(baseDir, dataDir, mysqlVersionString, out, err, debug, null);
    }

    HackedMysqldResource(File pBaseDir, File pDataDir, String pMysqlVersionString,
                         PrintStream pOut, PrintStream pErr, PrintStream pDebug, Utils pUtils) {
        this.out = (pOut != null) ? pOut : System.out;
        this.err = (pErr != null) ? pErr : System.err;
        this.debug = (pDebug != null) ? pDebug : this.out;
        this.utils = (pUtils != null) ? pUtils : new Utils();
        this.platformProperties = utils.streams().loadProperties(
                PLATFORM_MAP_PROPERTIES, pErr);
        this.connectorMxjProperties = utils.streams().loadProperties(
                CONNECTOR_MXJ_PROPERTIES, pErr);

        this.baseDir = utils.files().validCononicalDir(pBaseDir,
                utils.files().tmp(MYSQL_C_MXJ));
        this.dataDir = utils.files().validCononicalDir(pDataDir,
                new File(baseDir, DATA));

        this.optionParser = new HelpOptionsParser(err, utils);

        this.killDelay = getKillDelyFromProperties(connectorMxjProperties);
        this.windowsKillCommand = getWindowsKillCommand(connectorMxjProperties);

        String className = utils.str().shortClassName(getClass());
        this.pidFile = utils.files().cononical(
                new File(dataDir, className + ".pid"));
        this.portFile = new File(dataDir, className + ".port");
        setVersion(false, pMysqlVersionString);
        this.msgPrefix = "[" + className + "] ";
        this.options = new HashMap();
        setShell(null);
        setOsAndArch(System.getProperty(Platform.OS_NAME), System
                .getProperty(Platform.OS_ARCH));
        this.completionListensers = new ArrayList();
        initTrace();
    }

    private void initTrace() {
        this.trace = new Exception();
    }

    /**
     * Starts mysqld passing it the parameters specified in the arguments map.
     * No effect if MySQL is already running
     */
    public synchronized void start(String threadName, Map mysqldArgs) {
        start(threadName, mysqldArgs, false);
    }

    public synchronized void start(String threadName, Map pMysqldArgs,
                                   boolean populateAllOptions) {
        if ((getShell() != null) || processRunning()) {
            printMessage("mysqld already running (process: " + pid() + ")");
            return;
        }

        final Map mysqldArgs = new HashMap(pMysqldArgs);

        int port = parseInt(mysqldArgs.get(MysqldResourceI.PORT), 3306);

        mysqldArgs.put(MysqldResourceI.PORT, "" + port);
        mysqldArgs.remove(MysqldResourceI.MYSQLD_VERSION);

        String initUserProp = (String) mysqldArgs
                .remove(MysqldResourceI.INITIALIZE_USER);
        boolean initUser = Boolean.valueOf(initUserProp).booleanValue();
        String user = (String) mysqldArgs
                .remove(MysqldResourceI.INITIALIZE_USER_NAME);
        String password = (String) mysqldArgs
                .remove(MysqldResourceI.INITIALIZE_PASSWORD);

        setKillDelay(parseInt(mysqldArgs.remove(MysqldResourceI.KILL_DELAY),
                killDelay));

        if (populateAllOptions) {
            options = optionParser.getOptionsFromHelp(getHelp(mysqldArgs));
        } else {
            options = new HashMap();
            options.putAll(mysqldArgs);
        }

        // printMessage("mysqld : " +
        // services.str().toString(mysqldArgs.entrySet()));
        out.flush();
        addCompletionListenser(new Runnable() {
            public void run() {
                setReadyForConnection(false);
                setShell(null);
                completionListensers.remove(this);
            }
        });
        setShell(exec(threadName, mysqldArgs, out, debug /* err would be a bit noisy */, true));

        reportPid();
        utils.files().writeString(portFile, port + utils.str().newLine());

        boolean ready = canConnectToServer(port, killDelay);
        setReadyForConnection(ready);

        if (initUser) {
            try {
                new InitializeUser(port, user, password, err).initializeUser();
            } catch (Throwable t) {
                t.printStackTrace(err);
            }
        }
    }

    // Will wait 250 miliseconds between each try.
    boolean canConnectToServer(int port, int milisecondsBeforeGivingUp) {
        int triesBeforeGivingUp = 1 + (milisecondsBeforeGivingUp / 1000) * 4;
        utils.str().classForName(Driver.class.getName());
        Connection conn = null;
        String bogusUser = "Connector/MXJ";
        String password = "Bogus Password";
        String url = "jdbc:mysql://127.0.0.1:" + port + "/test"
                + "?connectTimeout=150";

        for (int i = 0; i < triesBeforeGivingUp; i++) {
            try {
                conn = DriverManager.getConnection(url, bogusUser, password);
                return true; /* should never happen */
            } catch (SQLException e) {
                // Standard com.mysql.jdbc.Driver error
                if (e.getErrorCode() == MysqlErrorNumbers.ER_ACCESS_DENIED_ERROR ||
                        // STEPH we add the condition to make it work with MariaDb:
                        // mariadb seems to return error -1 and the message below.
                        e.getMessage().startsWith("Could not connect: Access denied")) {
                    return true;
                }
            } finally {
                try {
                    if (conn != null) {
                        conn.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace(err);
                }
            }
            utils.threads().pause(100);
        }
        return false;
    }

    private void setReadyForConnection(boolean ready) {
        readyForConnections = ready;
    }

    public synchronized boolean isReadyForConnections() {
        return readyForConnections;
    }

    private void reportPid() {
        final int CYCLES = 100;
        final int CYCLE_DELAY = killDelay / CYCLES;
        boolean printed = false;
        for (int i = 0; !printed && i < CYCLES; i++) {
            if (pidFile.exists() && pidFile.length() > 0) {
                utils.threads().pause(100);
                printMessage("mysqld running as process: " + pid());

                out.flush();
                printed = true;
            }
            utils.threads().pause(CYCLE_DELAY);
        }

        reportIfNoPidfile(printed);
    }

    synchronized String pid() {
        if (pid == null) {
            if (!pidFile.exists()) {
                return "No PID";
            }

            pid = utils.files().asString(pidFile).trim();
        }
        return pid;
    }

    void reportIfNoPidfile(boolean pidFileFound) {
        if (!pidFileFound) {
            printWarning("mysqld pid-file not found:  " + pidFile);
        }
    }

    /**
     * Kills the MySQL process.
     */
    public synchronized void shutdown() {
        boolean haveShell = (getShell() != null);
        if (!pidFile.exists() && !haveShell) {
            printMessage("Mysqld not running. No file: " + pidFile);
            return;
        }
        printMessage("stopping mysqld (process: " + pid() + ")");

        issueNormalKill();

        if (processRunning()) {
            issueForceKill();
        }

        if (shellRunning()) {
            destroyShell();
        }
        setShell(null);

        if (processRunning()) {
            printWarning("Process " + pid + "still running; not deleting "
                    + pidFile);
        } else {
            utils.threads().pause(150);
            pidFile.delete();
            pid = null;
            portFile.delete();

            utils.threads().pause(150);
            if (pidFile.exists()) {
                printMessage(pidFile + " still exits.");
            }
            if (portFile.exists()) {
                printMessage(portFile + " still exits.");
            }
        }

        setReadyForConnection(false);

        if (!options.isEmpty()) {
            printMessage("clearing options");
            options.clear();
        }

        printMessage("shutdown complete");
        out.flush();
    }

    void destroyShell() {
        String shellName = getShell().getName();
        printWarning("attempting to destroy thread " + shellName);
        getShell().destroyProcess();
        waitForShellToDie();
        String msg = (shellRunning() ? "not " : "") + "destroyed.";
        printWarning(shellName + " " + msg);
    }

    void issueForceKill() {
        printWarning("attempting to \"force kill\" " + pid());
        new ProcessUtil(pid(), err, err, baseDir, utils, windowsKillCommand)
                .forceKill();

        waitForProcessToDie();
        if (processRunning()) {
            String msg = (processRunning() ? "not " : "") + "killed.";
            printWarning(pid() + " " + msg);
        } else {
            printMessage("force kill " + pid() + " issued.");
        }
    }

    private void issueNormalKill() {
        if (!pidFile.exists()) {
            printWarning("Not running? File not found: " + pidFile);
            return;
        }

        new ProcessUtil(pid(), err, err, baseDir, utils, windowsKillCommand)
                .killNoThrow();
        waitForProcessToDie();
    }

    private void waitForProcessToDie() {
        long giveUp = System.currentTimeMillis() + killDelay;
        while (processRunning() && System.currentTimeMillis() < giveUp) {
            utils.threads().pause(250);
        }
    }

    private void waitForShellToDie() {
        long giveUp = System.currentTimeMillis() + killDelay;
        while (shellRunning() && System.currentTimeMillis() < giveUp) {
            utils.threads().pause(250);
        }
    }

    public synchronized Map getServerOptions() {
        if (options.isEmpty()) {
            options = optionParser.getOptionsFromHelp(getHelp(new HashMap()));
            options.put(BASEDIR, baseDir.getPath());
            options.put(DATADIR, dataDir.getPath());
        }
        return new HashMap(options);
    }

    public synchronized boolean isRunning() {
        return shellRunning() || processRunning();
    }

    private boolean processRunning() {
        if (!pidFile.exists()) {
            return false;
        }
        return new ProcessUtil(pid(), out, err, baseDir, utils,
                windowsKillCommand).isRunning();
    }

    private boolean shellRunning() {
        return (getShell() != null) && (getShell().isAlive());
    }

    public synchronized String getVersion() {
        return versionString;
    }

    private String getVersionDir() {
        return getVersion().replaceAll("\\.", "-");
    }

    private synchronized void setVersion(boolean checkRunning,
                                         String mysqlVersionString) {
        if (checkRunning && isRunning()) {
            throw new IllegalStateException("Already running");
        }

        if (mysqlVersionString == null || mysqlVersionString.equals("")) {
            versionString = System.getProperty(MYSQLD_VERSION,
                    connectorMxjProperties.getProperty(MYSQLD_VERSION))
                    + "";
        } else {
            versionString = mysqlVersionString;
        }
        versionString = versionString.trim();
    }

    public synchronized void setVersion(String mysqlVersionString) {
        setVersion(true, mysqlVersionString);
    }

    private void printMessage(String msg) {
        println(out, msg);
    }

    private void printWarning(String msg) {
        println(err, "");
        println(err, msg);
    }

    private void println(PrintStream stream, String msg) {
        stream.println(msgPrefix + msg);
    }

    /* called from constructor */
    final String getWindowsKillCommand(Properties props) {
        String key = WINDOWS_KILL_COMMAND;
        String defaultVal = "kill.exe";
        String fileVal = props.getProperty(key, defaultVal);
        String val = System.getProperty(key, fileVal).trim();
        return val.length() > 0 ? val : defaultVal;
    }

    /* called from constructor, over-ride with care */
    final void setOsAndArch(String osName, String osArch) {
        String key = stripUnwantedChars(osName + "-" + osArch);
        this.osName_osArch = platformProperties.getProperty(key, key);
    }

    String stripUnwantedChars(String str) {
        return str.replace(' ', '_').replace('/', '_').replace('\\', '_');
    }

    private Shell exec(String threadName, Map mysqldArgs,
                       PrintStream outStream, PrintStream errStream, boolean withListeners) {

        deployFiles();

        adjustParameterMap(mysqldArgs);
        String[] args = constructArgs(mysqldArgs);
        outStream.println(new ListToString().toString(args));

        Shell launch = utils.shellFactory().newShell(args, threadName,
                outStream, errStream);
        if (withListeners) {
            for (int i = 0; i < completionListensers.size(); i++) {
                Runnable listener = (Runnable) completionListensers.get(i);
                launch.addCompletionListener(listener);
            }
        }
        launch.setDaemon(true);

        printMessage("launching mysqld (" + threadName + ")");

        launch.start();
        return launch;
    }

    public void deployFiles() {
        makeMysqld();
        ensureEssentialFilesExist();
        try {
            makeMysqlClient();
        } catch (MissingResourceException e) {
            printMessage(e.getMessage() + " - OK.");
        }
    }

    private void adjustParameterMap(Map mysqldArgs) {
        ensureDir(mysqldArgs, baseDir, MysqldResourceI.BASEDIR);
        ensureDir(mysqldArgs, dataDir, MysqldResourceI.DATADIR);
        mysqldArgs.put(MysqldResourceI.PID_FILE, pidFile.getPath());
        ensureSocket(mysqldArgs);
    }

    File makeMysqld() {
        return extractExecutable(executableName());
    }

    File makeMysqlClient() {
        return extractExecutable(clientExecutableName());
    }

    private File extractExecutable(String executableName) {
        final File executable = new File(binDir(), executableName);
        if (!executable.exists()) {
            executable.getParentFile().mkdirs();
            String resource = getResourceName(executableName);
            utils.streams().createFileFromResource(resource, executable);
        }
        utils.files().addExecutableRights(executable, out, err);
        return executable;
    }

    String getResourceName() {
        return getResourceName(executableName());
    }

    private String getResourceName(String name) {
        String dir = os_arch();
        return getVersionDir() + Streams.RESOURCE_SEPARATOR + dir
                + Streams.RESOURCE_SEPARATOR + name;
    }

    String os_arch() {
        return osName_osArch;
    }

    private String executableName() {
        if (!isWindows()) {
            return "mysqld";
        }
        String key = "windows-mysqld-command";
        String defaultValue = "mysqld.exe";
        return connectorMxjProperties.getProperty(key, defaultValue);
    }

    private String clientExecutableName() {
        if (isWindows()) {
            return "mysql.exe";
        }
        return "mysql";
    }

    boolean isWindows() {
        return osName_osArch.startsWith("Win");
    }

    File getMysqldFilePointer() {
        return new File(binDir(), executableName());
    }

    private File binDir() {
        return new File(baseDir, "bin");
    }

    void ensureEssentialFilesExist() {
        if (utils.files().isEmpty(dataDir)) {
            String data_jar = getVersionDir() + Streams.RESOURCE_SEPARATOR
                    + "data_dir.jar";
            utils.streams().expandResourceJar(dataDir, data_jar);
        }
        utils.streams().expandResourceJar(baseDir,
                getVersionDir() + Streams.RESOURCE_SEPARATOR + shareJar());
    }

    void ensureSocket(Map mysqldArgs) {
        String socketString = (String) mysqldArgs.get(MysqldResourceI.SOCKET);
        if (socketString != null) {
            return;
        }
        mysqldArgs.put(MysqldResourceI.SOCKET, "mysql.sock");
    }

    private void ensureDir(Map mysqldArgs, File expected, String key) {
        String dirString = (String) mysqldArgs.get(key);
        if (dirString != null) {
            File asConnonical = utils.files().validCononicalDir(
                    new File(dirString));
            if (!expected.equals(asConnonical)) {
                String msg = dirString + " not equal to " + expected;
                throw new IllegalArgumentException(msg);
            }
        }
        mysqldArgs.put(key, utils.files().getPath(expected));
    }

    String[] constructArgs(Map mysqldArgs) {
        List strs = new ArrayList();
        strs.add(utils.files().getPath(getMysqldFilePointer()));

        strs.add("--no-defaults");
        if (isWindows()) {
            strs.add("--console");
        }
        Iterator it = mysqldArgs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            StringBuffer buf = new StringBuffer("--");
            buf.append(key);
            if (value != null) {
                buf.append("=");
                buf.append(value);
            }
            strs.add(buf.toString());
        }

        return utils.str().toStringArray(strs);
    }

    protected void finalize() throws Throwable {
        if (getShell() != null) {
            printWarning("resource released without closure.");
            trace.printStackTrace(err);
        }
        super.finalize();
    }

    String shareJar() {
        String shareJar = "share_dir.jar";
        if (isWindows()) {
            String key = "windows-share-dir-jar";
            String defaultVal = "win_" + shareJar;
            shareJar = connectorMxjProperties.getProperty(key, defaultVal);
        }
        return shareJar;
    }

    void setShell(Shell shell) {
        this.shell = shell;
    }

    Shell getShell() {
        return shell;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public File getDataDir() {
        return dataDir;
    }

    /**
     * the kill-delay is read from the properties file, but a value in System
     * Properties will act as an over-ride.
     */
    int getKillDelyFromProperties(final Properties props) {
        final int defaultDelayFiveMinutes = 5 * 60 * 1000;
        final String key = KILL_DELAY;
        String propsVal = props.getProperty(key, "" + defaultDelayFiveMinutes);
        String sysProp = System.getProperty(key, propsVal);
        return parseInt(sysProp, defaultDelayFiveMinutes);
    }

    public synchronized void setKillDelay(int millis) {
        this.killDelay = millis;
    }

    public synchronized void addCompletionListenser(Runnable listener) {
        completionListensers.add(listener);
    }

    private String getHelp(Map params) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream capturedOut = new PrintStream(bos);

        params.put("help", null);
        params.put("verbose", null);

        exec("getOptions", params, capturedOut, capturedOut, false).join();

        params.remove("help");
        params.remove("verbose");

        utils.threads().pause(500);
        capturedOut.flush();
        capturedOut.close(); // should flush();

        return new String(bos.toByteArray());
    }

    public synchronized int getPort() {
        final int defaultVal = 0;
        if (isRunning() && portFile.exists()) {
            return parseInt(utils.files().asString(portFile), defaultVal);
        }
        return defaultVal;
    }

    int parseInt(Object parseMe, int defaultVal) {
        return utils.str().parseInt(parseMe, defaultVal, err);
    }

    // ---------------------------------------------------------
    static void printUsage(PrintStream out) {
        String command = "java " + MysqldResource.class.getName();
        String basedir = " --" + MysqldResourceI.BASEDIR;
        String datadir = " --" + MysqldResourceI.DATADIR;
        out.println("Usage to start: ");
        out.println(command + " [ server options ]");
        out.println();
        out.println("Usage to shutdown: ");
        out.println(command + " --shutdown [" + basedir
                + "=/full/path/to/basedir ]");
        out.println();
        out.println("Common server options include:");
        out.println(basedir + "=/full/path/to/basedir");
        out.println(datadir + "=/full/path/to/datadir");
        out.println(" --" + MysqldResourceI.SOCKET
                + "=/full/path/to/socketfile");
        out.println();
        out.println("Example:");
        out.println(command + basedir + "=/home/duke/dukeapp/db" + datadir
                + "=/data/dukeapp/data" + " --max_allowed_packet=65000000");
        out.println(command + " --shutdown" + basedir
                + "=/home/duke/dukeapp/db");
        out.println();
    }

    public static void main(String[] args) {
        CommandLineOptionsParser clop = new CommandLineOptionsParser(args);
        if (clop.containsKey("help")) {
            printUsage(System.out);
            return;
        }

        PrintStream out = System.out;
        PrintStream err = System.err;
        if (clop.containsKey("silent")) {
            clop.remove("silent");
            PrintStream devNull = new NullPrintStream();
            out = devNull;
            err = devNull;
        }

        MysqldResource mysqld = new MysqldResource(clop.getBaseDir(), clop
                .getDataDir(), clop.getVersion(), out, err);

        Integer newKillDelay = clop.getKillDelay(err);
        if (newKillDelay != null) {
            mysqld.setKillDelay(newKillDelay.intValue());
        }

        if (clop.isShutdown()) {
            mysqld.shutdown();
            return;
        }

        mysqld.start(new Threads().newName(), clop.asMap());
    }
}
