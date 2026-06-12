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

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.metl.core.model.DataType;
import org.jumpmind.metl.core.model.ModelAttrib;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.model.RelationalModel;
import org.jumpmind.metl.core.runtime.resource.IDatasourceRuntime;
import org.jumpmind.metl.core.model.Component;

/**
 * Responsible for resolving target database {@link Table} metadata for each
 * entity in the input model and building the corresponding
 * {@link RdbmsWriter.TargetTableDefintion} list.
 *
 * <p>Handles three strategies for obtaining table metadata:</p>
 * <ol>
 *   <li>Lookup in the resource-level cache ({@link IDatasourceRuntime})</li>
 *   <li>Lookup via the {@link IDatabasePlatform} (JDBC metadata)</li>
 *   <li>Auto-creation from the model entity definition</li>
 * </ol>
 *
 * <p>Extracted from {@link RdbmsWriter} to isolate table-preparation
 * concerns from SQL execution and statistics tracking.</p>
 */
class TargetTablePreparer {

    private final IDatabasePlatform databasePlatform;
    private final String catalogName;
    private final String schemaName;
    private final String tablePrefix;
    private final String tableSuffix;
    private final boolean autoCreateTable;
    private final boolean useCachedMetadata;

    /** Optional callback for informational log messages (e.g. auto-create). */
    private final WriteStatsTracker.LogSink logSink;

    TargetTablePreparer(IDatabasePlatform databasePlatform,
                        String catalogName, String schemaName,
                        String tablePrefix, String tableSuffix,
                        boolean autoCreateTable, boolean useCachedMetadata,
                        WriteStatsTracker.LogSink logSink) {
        this.databasePlatform = databasePlatform;
        this.catalogName = catalogName;
        this.schemaName = schemaName;
        this.tablePrefix = tablePrefix != null ? tablePrefix : "";
        this.tableSuffix = tableSuffix != null ? tableSuffix : "";
        this.autoCreateTable = autoCreateTable;
        this.useCachedMetadata = useCachedMetadata;
        this.logSink = logSink;
    }

    /**
     * Builds a {@link RdbmsWriter.TargetTableDefintion} for every entity in
     * the given relational model whose target table can be resolved or
     * auto-created.
     *
     * @param model    the input relational model
     * @param resource optional datasource runtime for cache lookups (may be {@code null})
     * @param component the metl component, needed to read per-attribute insert/update enabled flags
     * @return a (possibly empty) list of target table definitions
     */
    List<RdbmsWriter.TargetTableDefintion> prepareTargetTables(RelationalModel model,
                                                                IDatasourceRuntime resource,
                                                                Component component) {
        List<RdbmsWriter.TargetTableDefintion> targetTables = new ArrayList<>();
        for (ModelEntity entity : model.getModelEntities()) {
            String tableName = tablePrefix + entity.getName() + tableSuffix;
            Table table = resolveTable(tableName, resource);
            if (table == null && autoCreateTable) {
                table = createTableFromEntity(entity, tableName);
                if (logSink != null) {
                    logSink.log(org.jumpmind.metl.core.runtime.LogLevel.INFO,
                            "Creating table: %s  on db: %s",
                            table.getName(), databasePlatform.getDataSource().toString());
                }
                databasePlatform.createTables(false, false, table);
            }
            if (table != null) {
                targetTables.add(new RdbmsWriter.TargetTableDefintion(
                        entity,
                        new RdbmsWriter.TargetTable(DmlType.UPDATE, entity, table.copy(), databasePlatform, component),
                        new RdbmsWriter.TargetTable(DmlType.INSERT, entity, table.copy(), databasePlatform, component),
                        new RdbmsWriter.TargetTable(DmlType.DELETE, entity, table.copy(), databasePlatform, component)));
            }
        }
        return targetTables;
    }

    /**
     * Attempts to resolve a {@link Table} first from the resource cache and
     * then from the database platform metadata.  If
     * {@code useCachedMetadata} is {@code true} and the resource cache
     * returns a non-null result, the platform lookup is skipped.
     */
    private Table resolveTable(String tableName, IDatasourceRuntime resource) {
        Table table = resource != null ? resource.getTableFromCache(catalogName, schemaName, tableName) : null;
        if (table == null || !useCachedMetadata) {
            table = databasePlatform.getTableFromCache(catalogName, schemaName, tableName, true);
            if (resource != null) {
                resource.putTableInCache(catalogName, schemaName, tableName, table);
            }
        }
        return table;
    }

    /**
     * Creates a minimal {@link Table} definition from a {@link ModelEntity},
     * mapping each attribute's {@link DataType} to a JDBC type code.
     */
    Table createTableFromEntity(ModelEntity entity, String tableName) {
        Table table = new Table();
        table.setName(tableName);
        List<ModelAttrib> attributes = entity.getModelAttributes();
        for (ModelAttrib attribute : attributes) {
            DataType dataType = attribute.getDataType();
            Column column = new Column(attribute.getName());
            if (dataType.isNumeric()) {
                column.setTypeCode(Types.DECIMAL);
            } else if (dataType.isBoolean()) {
                column.setTypeCode(Types.BOOLEAN);
            } else if (dataType.isTimestamp()) {
                column.setTypeCode(Types.TIMESTAMP);
            } else if (dataType.isBinary()) {
                column.setTypeCode(Types.BLOB);
            } else {
                column.setTypeCode(Types.LONGVARCHAR);
            }
            column.setPrimaryKey(attribute.isPk());
            table.addColumn(column);
        }
        return table;
    }

    // ---------------------------------------------------------------
    // Accessors (visible for testing)
    // ---------------------------------------------------------------

    String getTablePrefix() {
        return tablePrefix;
    }

    String getTableSuffix() {
        return tableSuffix;
    }

    boolean isAutoCreateTable() {
        return autoCreateTable;
    }

    boolean isUseCachedMetadata() {
        return useCachedMetadata;
    }
}
