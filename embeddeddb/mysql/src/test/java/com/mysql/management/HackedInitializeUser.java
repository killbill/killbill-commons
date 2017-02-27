/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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
 Copyright (C) 2007-2008 MySQL AB, 2008-2009 Sun Microsystems, Inc. All rights reserved.
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

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.mysql.management.util.QueryUtil;
import com.mysql.management.util.SQLRuntimeException;

public class HackedInitializeUser {

    private String userName;

    private String password;

    private String url;

    private PrintStream err;

    public InitializeUser(int port, String userName, String password,
                          PrintStream err) {
        this.userName = userName;
        this.password = password;
        this.url = "jdbc:mysql://127.0.0.1:" + port + "/mysql";
        this.err = err;

        try {
            Class.forName(com.mysql.jdbc.Driver.class.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /** returns true if the password was set with this attempt */
    public boolean initializeUser() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, userName, password);
            return false;
        } catch (SQLException e) {
            // Okay, current user not initialized;
        } finally {
            close(conn);
        }

        try {
            final String NO_PASSWORD = null;
            conn = DriverManager.getConnection(url, "root", NO_PASSWORD);
        } catch (SQLException e) {
            String msg = "User initialization error." //
                         + " Can not connect as " + userName + " with password." //
                         + " Can not connect as root without password." //
                         + " URL: " + url;
            throw new SQLRuntimeException(msg, e, null, null);
        }
        try {
            QueryUtil util = new QueryUtil(conn, err);
            // util.execute("drop user ''");
            // util.execute("drop user 'root'@'localhost'");
            // util.execute("drop user 'root'@'127.0.0.1'");
            util.execute("DELETE from user");
            // Binding parameters won't work with server-side prepared statements
            String sql = "grant all on *.* to '" + userName + "'@'localhost' identified by '" + password + "' with grant option";
            final Object[] params = new Object[]{};
            util.execute(sql, params);
            util.execute("flush privileges");
        } finally {
            close(conn);
        }

        try {
            conn = DriverManager.getConnection(url, userName, password);
            QueryUtil util = new QueryUtil(conn, err);
            util.execute("SELECT 1");
        } catch (SQLException e) {
            String msg = "User initialization error." //
                         + " Can not connect as " + userName + " with password" //
                         + " after creating user and password." //
                         + " URL: " + url;
            throw new SQLRuntimeException(msg, e, null, null);
        } finally {
            close(conn);
        }
        return true;
    }

    private void close(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Throwable t) {
                t.printStackTrace(err);
            }
        }
    }
}
