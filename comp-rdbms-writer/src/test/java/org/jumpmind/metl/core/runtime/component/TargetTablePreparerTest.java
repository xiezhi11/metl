/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.core.runtime.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.metl.core.model.DataType;
import org.jumpmind.metl.core.model.ModelAttrib;
import org.jumpmind.metl.core.model.ModelEntity;
import org.junit.Test;

/**
 * Unit tests for {@link TargetTablePreparer}.
 *
 * <p>These tests focus on the pure logic inside
 * {@link TargetTablePreparer#createTableFromEntity} and the
 * constructor-driven configuration, which do not require a live
 * {@link org.jumpmind.db.platform.IDatabasePlatform}.</p>
 */
public class TargetTablePreparerTest {

    // ---------------------------------------------------------------
    // createTableFromEntity -- column type mapping
    // ---------------------------------------------------------------

    @Test
    public void createTableFromEntity_mapsNumericType() {
        ModelEntity entity = new ModelEntity("e1", "Customer");
        ModelAttrib attr = new ModelAttrib("a1", "e1", "balance");
        attr.setDataType(DataType.DECIMAL);
        entity.getModelAttributes().add(attr);

        TargetTablePreparer preparer = newPreparer("", "", false, false);
        Table table = preparer.createTableFromEntity(entity, "Customer");

        assertEquals("Customer", table.getName());
        assertEquals(1, table.getColumnCount());
        assertEquals(Types.DECIMAL, table.getColumn(0).getMappedTypeCode());
    }

    @Test
    public void createTableFromEntity_mapsBooleanType() {
        ModelEntity entity = new ModelEntity("e1", "Flags");
        ModelAttrib attr = new ModelAttrib("a1", "e1", "active");
        attr.setDataType(DataType.BOOLEAN);
        entity.getModelAttributes().add(attr);

        TargetTablePreparer preparer = newPreparer("", "", false, false);
        Table table = preparer.createTableFromEntity(entity, "Flags");

        assertEquals(Types.BOOLEAN, table.getColumn(0).getMappedTypeCode());
    }

    @Test
    public void createTableFromEntity_mapsTimestampType() {
        ModelEntity entity = new ModelEntity("e1", "Events");
        ModelAttrib attr = new ModelAttrib("a1", "e1", "createdAt");
        attr.setDataType(DataType.TIMESTAMP);
        entity.getModelAttributes().add(attr);

        TargetTablePreparer preparer = newPreparer("", "", false, false);
        Table table = preparer.createTableFromEntity(entity, "Events");

        assertEquals(Types.TIMESTAMP, table.getColumn(0).getMappedTypeCode());
    }

    @Test
    public void createTableFromEntity_mapsBinaryType() {
        ModelEntity entity = new ModelEntity("e1", "Docs");
        ModelAttrib attr = new ModelAttrib("a1", "e1", "content");
        attr.setDataType(DataType.BLOB);
        entity.getModelAttributes().add(attr);

        TargetTablePreparer preparer = newPreparer("", "", false, false);
        Table table = preparer.createTableFromEntity(entity, "Docs");

        assertEquals(Types.BLOB, table.getColumn(0).getMappedTypeCode());
    }

    @Test
    public void createTableFromEntity_mapsStringTypeToLongVarchar() {
        ModelEntity entity = new ModelEntity("e1", "Notes");
        ModelAttrib attr = new ModelAttrib("a1", "e1", "text");
        attr.setDataType(DataType.VARCHAR);
        entity.getModelAttributes().add(attr);

        TargetTablePreparer preparer = newPreparer("", "", false, false);
        Table table = preparer.createTableFromEntity(entity, "Notes");

        assertEquals(Types.LONGVARCHAR, table.getColumn(0).getMappedTypeCode());
    }

    @Test
    public void createTableFromEntity_setsPrimaryKey() {
        ModelEntity entity = new ModelEntity("e1", "Users");
        ModelAttrib pkAttr = new ModelAttrib("a1", "e1", "id");
        pkAttr.setDataType(DataType.INTEGER);
        pkAttr.setPk(true);

        ModelAttrib nameAttr = new ModelAttrib("a2", "e1", "name");
        nameAttr.setDataType(DataType.VARCHAR);
        nameAttr.setPk(false);

        entity.getModelAttributes().add(pkAttr);
        entity.getModelAttributes().add(nameAttr);

        TargetTablePreparer preparer = newPreparer("", "", false, false);
        Table table = preparer.createTableFromEntity(entity, "Users");

        assertEquals(2, table.getColumnCount());

        Column idCol = table.getColumn(0);
        assertEquals("id", idCol.getName());
        assertTrue("Primary key flag should be set", idCol.isPrimaryKey());

        Column nameCol = table.getColumn(1);
        assertEquals("name", nameCol.getName());
    }

    @Test
    public void createTableFromEntity_multipleAttributes() {
        ModelEntity entity = new ModelEntity("e1", "Order");

        ModelAttrib id = new ModelAttrib("a1", "e1", "orderId");
        id.setDataType(DataType.BIGINT);
        id.setPk(true);

        ModelAttrib total = new ModelAttrib("a2", "e1", "total");
        total.setDataType(DataType.DOUBLE);

        ModelAttrib placed = new ModelAttrib("a3", "e1", "orderDate");
        placed.setDataType(DataType.DATE);

        ModelAttrib data = new ModelAttrib("a4", "e1", "payload");
        data.setDataType(DataType.BINARY);

        entity.getModelAttributes().add(id);
        entity.getModelAttributes().add(total);
        entity.getModelAttributes().add(placed);
        entity.getModelAttributes().add(data);

        TargetTablePreparer preparer = newPreparer("", "", false, false);
        Table table = preparer.createTableFromEntity(entity, "Order");

        assertEquals(4, table.getColumnCount());
        assertEquals(Types.DECIMAL, table.getColumn(0).getMappedTypeCode());   // BIGINT -> DECIMAL (numeric)
        assertEquals(Types.DECIMAL, table.getColumn(1).getMappedTypeCode());   // DOUBLE -> DECIMAL (numeric)
        assertEquals(Types.TIMESTAMP, table.getColumn(2).getMappedTypeCode()); // DATE -> TIMESTAMP
        assertEquals(Types.BLOB, table.getColumn(3).getMappedTypeCode());      // BINARY -> BLOB
    }

    // ---------------------------------------------------------------
    // Configuration accessors
    // ---------------------------------------------------------------

    @Test
    public void constructor_normalisesNullPrefixAndSuffix() {
        TargetTablePreparer preparer = newPreparer(null, null, false, false);
        assertEquals("", preparer.getTablePrefix());
        assertEquals("", preparer.getTableSuffix());
    }

    @Test
    public void constructor_preservesConfiguredPrefixAndSuffix() {
        TargetTablePreparer preparer = newPreparer("pre_", "_suf", true, true);
        assertEquals("pre_", preparer.getTablePrefix());
        assertEquals("_suf", preparer.getTableSuffix());
        assertTrue(preparer.isAutoCreateTable());
        assertTrue(preparer.isUseCachedMetadata());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private TargetTablePreparer newPreparer(String prefix, String suffix,
                                             boolean autoCreate, boolean useCached) {
        return new TargetTablePreparer(null, null, null, prefix, suffix, autoCreate, useCached, null);
    }
}
