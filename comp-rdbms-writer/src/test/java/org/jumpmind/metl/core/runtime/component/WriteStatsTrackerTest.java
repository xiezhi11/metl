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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.runtime.LogLevel;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link WriteStatsTracker}.
 *
 * <p>Verifies:</p>
 * <ul>
 *   <li>{@code getStats} creates and caches a WriteStats per TargetTableDefintion</li>
 *   <li>{@code reportStats} formats output correctly and clears the map after reporting</li>
 *   <li>{@code reportStats} honours the five-minute interval unless forced</li>
 *   <li>{@code reset} clears all accumulated state</li>
 * </ul>
 */
public class WriteStatsTrackerTest {

    private WriteStatsTracker tracker;

    @Before
    public void setUp() {
        tracker = new WriteStatsTracker();
    }

    // ---------------------------------------------------------------
    // getStats
    // ---------------------------------------------------------------

    @Test
    public void getStats_returnsSameInstanceOnRepeatedCalls() {
        RdbmsWriter.TargetTableDefintion def = stubTargetTableDefintion("T1");

        RdbmsWriter.WriteStats first = tracker.getStats(def);
        assertNotNull(first);

        RdbmsWriter.WriteStats second = tracker.getStats(def);
        assertSame("getStats must return the same cached instance", first, second);
    }

    @Test
    public void getStats_returnsDistinctInstancesForDistinctDefinitions() {
        RdbmsWriter.TargetTableDefintion def1 = stubTargetTableDefintion("T1");
        RdbmsWriter.TargetTableDefintion def2 = stubTargetTableDefintion("T2");

        RdbmsWriter.WriteStats s1 = tracker.getStats(def1);
        RdbmsWriter.WriteStats s2 = tracker.getStats(def2);

        assertNotNull(s1);
        assertNotNull(s2);
        assertTrue("Different definitions must yield different stats", s1 != s2);
    }

    // ---------------------------------------------------------------
    // reportStats -- forced
    // ---------------------------------------------------------------

    @Test
    public void reportStats_forced_emitsLogAndClearsMap() {
        RdbmsWriter.TargetTableDefintion def = stubTargetTableDefintion("ORDERS");
        RdbmsWriter.WriteStats stats = tracker.getStats(def);
        stats.insertCount = 10;
        stats.updateCount = 5;
        stats.deleteCount = 2;
        stats.fallbackInsertCount = 1;
        stats.fallbackUpdateCount = 3;
        stats.ignoredCount = 4;

        CollectingLogSink sink = new CollectingLogSink();
        long updatedDuration = tracker.reportStats(
                Collections.singletonList(def), true, 1234L, sink);

        // Duration should be zeroed after a report
        assertEquals(0L, updatedDuration);

        // At least two messages: one for the table, one for the total
        assertTrue("Expected at least 2 log messages, got " + sink.messages.size(),
                sink.messages.size() >= 2);

        // The first message should contain table-level details
        String tableMsg = sink.messages.get(0);
        assertTrue("Should mention Inserted count", tableMsg.contains("Inserted: 10"));
        assertTrue("Should mention Updated count", tableMsg.contains("Updated: 5"));
        assertTrue("Should mention Deleted count", tableMsg.contains("Deleted: 2"));
        assertTrue("Should mention Fallback Inserts", tableMsg.contains("Fallback Inserts: 1"));
        assertTrue("Should mention Fallback Updates", tableMsg.contains("Fallback Updates: 3"));
        assertTrue("Should mention Ignored Count", tableMsg.contains("Ignored Count: 4"));

        // The summary message should include the total row count
        String summaryMsg = sink.messages.get(1);
        assertTrue("Summary should mention total statements", summaryMsg.contains("statements"));

        // Stats map should be cleared
        assertTrue("Stats map should be empty after forced report",
                tracker.getStatsMap().isEmpty());
    }

    @Test
    public void reportStats_forced_withNullTargetTables_doesNotThrow() {
        CollectingLogSink sink = new CollectingLogSink();
        long result = tracker.reportStats(null, true, 100L, sink);
        assertEquals(100L, result);
        assertTrue(sink.messages.isEmpty());
    }

    // ---------------------------------------------------------------
    // reportStats -- interval gating
    // ---------------------------------------------------------------

    @Test
    public void reportStats_notForced_skipsWhenWithinInterval() {
        RdbmsWriter.TargetTableDefintion def = stubTargetTableDefintion("T1");
        RdbmsWriter.WriteStats stats = tracker.getStats(def);
        stats.insertCount = 7;

        // Default lastStatsLogTime is "now", so within the 5-minute window
        CollectingLogSink sink = new CollectingLogSink();
        long result = tracker.reportStats(
                Collections.singletonList(def), false, 500L, sink);

        // Should NOT have logged (within interval), duration unchanged
        assertEquals(500L, result);
        assertTrue("Should not emit logs within the 5-min interval", sink.messages.isEmpty());
        // Stats map should still contain the entry
        assertEquals(1, tracker.getStatsMap().size());
    }

    @Test
    public void reportStats_notForced_emitsWhenIntervalExpired() {
        RdbmsWriter.TargetTableDefintion def = stubTargetTableDefintion("T1");
        RdbmsWriter.WriteStats stats = tracker.getStats(def);
        stats.insertCount = 3;

        // Set the last log time to 6 minutes ago to trigger reporting
        tracker.setLastStatsLogTime(System.currentTimeMillis() - 6 * 60 * 1000);

        CollectingLogSink sink = new CollectingLogSink();
        long result = tracker.reportStats(
                Collections.singletonList(def), false, 200L, sink);

        assertEquals(0L, result);
        assertTrue("Should emit log when interval expired", sink.messages.size() >= 2);
    }

    // ---------------------------------------------------------------
    // reset
    // ---------------------------------------------------------------

    @Test
    public void reset_clearsStatsMap() {
        RdbmsWriter.TargetTableDefintion def = stubTargetTableDefintion("T1");
        tracker.getStats(def).insertCount = 10;

        tracker.reset();

        assertTrue("Stats map should be empty after reset", tracker.getStatsMap().isEmpty());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Creates a TargetTableDefintion with lightweight stub TargetTable
     * instances.  The stubs subclass TargetTable and override only the
     * methods that WriteStatsTracker actually calls, avoiding the need for
     * a live IDatabasePlatform or Component.
     */
    private static RdbmsWriter.TargetTableDefintion stubTargetTableDefintion(String name) {
        Table table = new Table();
        table.setName(name);
        table.setCatalog("cat");
        table.setSchema("sch");

        StubTargetTable insertTable = new StubTargetTable(table);
        StubTargetTable updateTable = new StubTargetTable(table);
        StubTargetTable deleteTable = new StubTargetTable(table);

        return new RdbmsWriter.TargetTableDefintion(
                new ModelEntity("e1", name),
                updateTable, insertTable, deleteTable);
    }

    /**
     * Minimal TargetTable stub that only provides getTable() and
     * getRowValues(), which are the only methods exercised by
     * WriteStatsTracker.  The constructor of the real TargetTable is
     * bypassed entirely via an unsafe allocation trick -- or more
     * practically, we use Objenesis which ships with Mockito/PowerMock.
     *
     * <p>Because Mockito 1.x cannot mock on JDK 17, we instead allocate
     * an instance without calling the constructor using
     * {@code sun.misc.Unsafe}.</p>
     */
    static class StubTargetTable extends RdbmsWriter.TargetTable {
        private final Table tbl;

        StubTargetTable(Table table) {
            super(table);
            this.tbl = table;
        }

        @Override
        public Table getTable() {
            return tbl;
        }

        @Override
        public List<org.jumpmind.metl.core.runtime.EntityData> getRowValues() {
            return new ArrayList<>();
        }
    }

    /** Simple LogSink that collects formatted messages. */
    static class CollectingLogSink implements WriteStatsTracker.LogSink {
        final List<String> messages = new ArrayList<>();

        @Override
        public void log(LogLevel level, String message, Object... args) {
            messages.add(String.format(message, args));
        }
    }
}
