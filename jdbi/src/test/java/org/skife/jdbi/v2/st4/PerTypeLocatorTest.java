/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.skife.jdbi.v2.st4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.JDBIQuarantineTests;
import org.skife.jdbi.v2.JDBITests;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.helpers.MapResultAsBean;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ST4StatementLocator;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.tweak.StatementLocator;

import static org.assertj.core.api.Assertions.assertThat;

public class PerTypeLocatorTest {

    @Rule
    public final H2Rule h2 = new H2Rule();

    @Test
    @Category(JDBIQuarantineTests.class)
    public void testNoFallback() throws Exception {
        StatementLocator sl = ST4StatementLocator.perType(ST4StatementLocator.UseSTGroupCache.YES);

        DBI dbi = new DBI(h2);
        dbi.setStatementLocator(sl);

        dbi.withHandle(new HandleCallback<Object>() {
            @Override
            public Object withHandle(final Handle h) throws Exception {
                h.createStatement("create table <name> (id int primary key, name text)")
                 .define("name", "something")
                 .execute();
                h.execute("insert into something (id, name) values (3, 'Carlos')");
                return null;
            }
        });

        Dao dao = dbi.onDemand(Dao.class);
        dao.insertFixtures();

        Something francisco = dao.findById(1);
        assertThat(francisco.getName()).isEqualTo("Francisco");
    }

    @Test
    @Category(JDBITests.class)
    public void testFallbackTemplate() throws Exception {
        StatementLocator sl = ST4StatementLocator.perType(ST4StatementLocator.UseSTGroupCache.YES,
                                                          "/explicit/sql.stg");
        DBI dbi = new DBI(h2);
        dbi.setStatementLocator(sl);

        dbi.withHandle(new HandleCallback<Object>() {
            @Override
            public Object withHandle(final Handle h) throws Exception {
                h.execute("create");
                h.execute("insert", 1, "Brian");
                String brian = h.createQuery("findNameById").bind("0", 1).mapTo(String.class).first();
                assertThat(brian).isEqualTo("Brian");
                return null;
            }
        });

    }

    public interface Dao {

        @SqlUpdate
        void insertFixtures();

        @SqlQuery
        @MapResultAsBean
        Something findById(int id);
    }
}
