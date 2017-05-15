/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.commons.jdbi.template;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.killbill.commons.jdbi.JDBITestBase;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestKillBillSqlDaoStringTemplateFactory extends JDBITestBase {

    @Test(groups = "slow")
    public void testSimple() throws Exception {
        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);

        final int computed = somethingSqlDao.doMath(1);
        Assert.assertEquals(computed, 2);
    }

    @Test(groups = "slow")
    public void testWithProxy() throws Exception {
        final Class[] interfaces = {SomethingSqlDao.class};
        final InvocationHandler h = new SomethingProxy();
        final Object newSqlDaoObject = Proxy.newProxyInstance(SomethingSqlDao.class.getClassLoader(), interfaces, h);
        final SomethingSqlDao somethingSqlDao = SomethingSqlDao.class.cast(newSqlDaoObject);

        final int computed = somethingSqlDao.doMath(1);
        Assert.assertEquals(computed, 2);
    }

    @KillBillSqlDaoStringTemplate("/org/killbill/commons/jdbi/Something.sql.stg")
    private interface SomethingSqlDao {

        @SqlQuery
        public int doMath(@Define("val") final int val);
    }

    private class SomethingProxy implements InvocationHandler {

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
            return somethingSqlDao.doMath((Integer) args[0]);
        }
    }
}
