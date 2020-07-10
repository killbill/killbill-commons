/*
 * Copyright 2004-2014 Brian McCallister
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
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
package org.skife.jdbi.v2;

import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.tweak.Argument;

import java.sql.PreparedStatement;

@Category(JDBITests.class)
public class TestBooleanIntegerArgument {

    @Test
    public void testTrue() throws Exception {

        PreparedStatement mockStmt = EasyMock.createMock(PreparedStatement.class);
        mockStmt.setInt(5, 1);

        EasyMock.replay(mockStmt);

        Argument arrrgh = new BooleanIntegerArgument(true);

        arrrgh.apply(5, mockStmt, null);

        EasyMock.verify(mockStmt);
    }

    @Test
    public void testFalse() throws Exception {

        PreparedStatement mockStmt = EasyMock.createMock(PreparedStatement.class);
        mockStmt.setInt(5, 0);

        EasyMock.replay(mockStmt);

        Argument arrrgh = new BooleanIntegerArgument(false);

        arrrgh.apply(5, mockStmt, null);

        EasyMock.verify(mockStmt);
    }
}
