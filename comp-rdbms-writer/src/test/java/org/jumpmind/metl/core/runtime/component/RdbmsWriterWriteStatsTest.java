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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jumpmind.metl.core.runtime.component.RdbmsWriter.WriteStats;
import org.junit.Test;

/**
 * Unit tests for the write-statistics summarization extracted from
 * {@link RdbmsWriter}. The summary line and row count produced here are what an
 * operator reads when troubleshooting a load, so the exact wording, field
 * ordering and weighting are pinned down by these tests.
 */
public class RdbmsWriterWriteStatsTest {

    @Test
    public void testEmptyStatsReportNothing() {
        WriteStats stats = new WriteStats();
        assertFalse(stats.hasActivity());
        assertEquals(0, stats.weightedRowCount());
        assertEquals("", stats.describe());
    }

    @Test
    public void testDescribeOrderingAndSeparators() {
        WriteStats stats = new WriteStats();
        stats.insertCount = 2;
        stats.updateCount = 3;
        stats.deleteCount = 1;

        assertTrue(stats.hasActivity());
        assertEquals("Inserted: 2, Updated: 3, Deleted: 1", stats.describe());
        assertEquals(6, stats.weightedRowCount());
    }

    @Test
    public void testFallbackCountsAreWeightedDouble() {
        WriteStats stats = new WriteStats();
        stats.insertCount = 1;
        stats.fallbackUpdateCount = 3;
        stats.fallbackInsertCount = 2;

        // insert (1) + fallback update (3 * 2) + fallback insert (2 * 2)
        assertEquals(11, stats.weightedRowCount());
        assertEquals("Inserted: 1, Fallback Updates: 3, Fallback Inserts: 2", stats.describe());
    }

    @Test
    public void testIgnoredRowsAreReported() {
        WriteStats stats = new WriteStats();
        stats.ignoredCount = 4;

        assertTrue(stats.hasActivity());
        assertEquals(4, stats.weightedRowCount());
        assertEquals("Ignored Count: 4", stats.describe());
    }

    @Test
    public void testFullSummaryKeepsOriginalFieldOrder() {
        WriteStats stats = new WriteStats();
        stats.insertCount = 1;
        stats.fallbackUpdateCount = 1;
        stats.updateCount = 1;
        stats.deleteCount = 1;
        stats.fallbackInsertCount = 1;
        stats.ignoredCount = 1;

        assertEquals("Inserted: 1, Fallback Updates: 1, Updated: 1, Deleted: 1, Fallback Inserts: 1, Ignored Count: 1",
                stats.describe());
        // 1 + (1*2) + 1 + 1 + (1*2) + 1
        assertEquals(8, stats.weightedRowCount());
    }
}
