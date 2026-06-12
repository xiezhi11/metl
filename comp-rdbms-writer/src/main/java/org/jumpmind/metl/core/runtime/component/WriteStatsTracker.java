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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.metl.core.runtime.LogLevel;
import org.jumpmind.metl.core.util.LogUtils;

/**
 * Tracks write statistics (insert, update, delete, fallback, ignored counts)
 * per target table and periodically reports them via a {@link LogSink}.
 *
 * <p>Extracted from {@link RdbmsWriter} to separate statistics concerns from
 * SQL execution and table-preparation logic.</p>
 */
class WriteStatsTracker {

    /**
     * Callback interface that mirrors the
     * {@code AbstractComponentRuntime#log(LogLevel, String, Object...)} signature.
     */
    @FunctionalInterface
    interface LogSink {
        void log(LogLevel level, String message, Object... args);
    }

    private final Map<RdbmsWriter.TargetTableDefintion, RdbmsWriter.WriteStats> statsMap = new HashMap<>();
    private long lastStatsLogTime = System.currentTimeMillis();

    // ---------------------------------------------------------------
    // Stats access
    // ---------------------------------------------------------------

    RdbmsWriter.WriteStats getStats(RdbmsWriter.TargetTableDefintion targetTableDefinition) {
        RdbmsWriter.WriteStats stats = statsMap.get(targetTableDefinition);
        if (stats == null) {
            stats = new RdbmsWriter.WriteStats();
            statsMap.put(targetTableDefinition, stats);
        }
        return stats;
    }

    // ---------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------

    /**
     * Formats and logs per-table write statistics when either
     * {@code force} is {@code true} or more than five minutes have elapsed
     * since the last report.  After logging the map is cleared and the
     * {@code sqlDuration} accumulator is reset.
     *
     * @param targetTables the list of target table definitions
     * @param force        if {@code true}, always emit the report
     * @param sqlDuration  accumulated SQL execution duration in milliseconds
     * @param logSink      callback used to emit log messages
     * @return the updated (possibly zeroed) sqlDuration value -- the caller
     *         should use the return value to keep its own accumulator in sync
     */
    long reportStats(List<RdbmsWriter.TargetTableDefintion> targetTables, boolean force,
                     long sqlDuration, LogSink logSink) {
        if (targetTables == null) {
            return sqlDuration;
        }
        if (force || System.currentTimeMillis() - lastStatsLogTime > 5 * 60 * 1000) {
            int rowCount = 0;
            for (RdbmsWriter.TargetTableDefintion table : targetTables) {
                RdbmsWriter.WriteStats stats = statsMap.get(table);
                if (stats != null) {
                    StringBuilder msg = new StringBuilder();
                    if (stats.insertCount > 0) {
                        msg.append("Inserted: ");
                        msg.append(stats.insertCount);
                        rowCount += stats.insertCount;
                    }
                    if (stats.fallbackUpdateCount > 0) {
                        if (msg.length() > 0) {
                            msg.append(", ");
                        }
                        msg.append("Fallback Updates: ");
                        msg.append(stats.fallbackUpdateCount);
                        rowCount += stats.fallbackUpdateCount * 2;
                    }
                    if (stats.updateCount > 0) {
                        if (msg.length() > 0) {
                            msg.append(", ");
                        }
                        msg.append("Updated: ");
                        msg.append(stats.updateCount);
                        rowCount += stats.updateCount;
                    }
                    if (stats.deleteCount > 0) {
                        if (msg.length() > 0) {
                            msg.append(", ");
                        }
                        msg.append("Deleted: ");
                        msg.append(stats.deleteCount);
                        rowCount += stats.deleteCount;
                    }
                    if (stats.fallbackInsertCount > 0) {
                        if (msg.length() > 0) {
                            msg.append(", ");
                        }
                        msg.append("Fallback Inserts: ");
                        msg.append(stats.fallbackInsertCount);
                        rowCount += stats.fallbackInsertCount * 2;
                    }
                    if (stats.ignoredCount > 0) {
                        if (msg.length() > 0) {
                            msg.append(", ");
                        }
                        msg.append("Ignored Count: ");
                        msg.append(stats.ignoredCount);
                        rowCount += stats.ignoredCount;
                    }
                    if (msg.length() > 0) {
                        logSink.log(LogLevel.INFO, "%s: %s",
                                table.getInsertTable().getTable().getFullyQualifiedTableName(),
                                msg.toString());
                    }
                }
            }
            logSink.log(LogLevel.INFO, "Ran a total of %d statements in %s", rowCount,
                    LogUtils.formatDuration(sqlDuration));
            sqlDuration = 0;
            statsMap.clear();
            lastStatsLogTime = System.currentTimeMillis();
        }
        return sqlDuration;
    }

    // ---------------------------------------------------------------
    // Lifecycle helpers
    // ---------------------------------------------------------------

    void reset() {
        statsMap.clear();
        lastStatsLogTime = System.currentTimeMillis();
    }

    /** Visible for testing -- returns an unmodifiable view of the current stats map. */
    Map<RdbmsWriter.TargetTableDefintion, RdbmsWriter.WriteStats> getStatsMap() {
        return java.util.Collections.unmodifiableMap(statsMap);
    }

    /** Visible for testing -- overrides the stats-log timestamp. */
    void setLastStatsLogTime(long millis) {
        this.lastStatsLogTime = millis;
    }
}
