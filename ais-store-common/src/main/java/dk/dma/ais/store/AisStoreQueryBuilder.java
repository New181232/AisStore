/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.ais.store;

import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL1;
import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL10;
import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL10_KEY;
import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL1_KEY;
import static dk.dma.ais.store.AisStoreSchema.TABLE_MMSI;
import static dk.dma.ais.store.AisStoreSchema.TABLE_MMSI_KEY;
import static dk.dma.ais.store.AisStoreSchema.TABLE_TIME;
import static dk.dma.ais.store.AisStoreSchema.TABLE_TIME_KEY;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Set;

import org.joda.time.Interval;

import com.datastax.driver.core.Session;

import dk.dma.db.cassandra.CassandraQueryBuilder;
import dk.dma.enav.model.geometry.Area;
import dk.dma.enav.model.geometry.grid.Cell;
import dk.dma.enav.model.geometry.grid.Grid;

/**
 * 
 * @author Kasper Nielsen
 */
public final class AisStoreQueryBuilder extends CassandraQueryBuilder<AisStoreQueryResult> {

    /** The bounding area. */
    final Area area;

    /** The number of results to fetch at a time. */
    int batchLimit = 3000; // magic constant found by trial;

    /** The list of MMSI number to retrieve. */
    final int[] mmsi;

    /** The start epoch time (inclusive) */
    long startTimeInclusive;

    /** The start epoch time (exclusive) */
    long stopTimeExclusive;

    private AisStoreQueryBuilder(Area area, int[] mmsi) {
        this.area = area;
        this.mmsi = mmsi;
    }

    public AisStoreQueryResult execute(Session s) {
        requireNonNull(s);
        AisStoreQueryInnerContext inner = new AisStoreQueryInnerContext();
        ArrayList<AisStorePartialQuery> queries = new ArrayList<>();
        if (area != null) {
            Set<Cell> cells1 = Grid.GRID_1_DEGREE.getCells(area);
            Set<Cell> cells10 = Grid.GRID_10_DEGREES.getCells(area);

            int factor = 10;// magic constant

            // Determines if use the tables of size 1 degree, or size 10 degrees
            boolean useCell1 = cells10.size() * factor > cells1.size();
            String tableName = useCell1 ? TABLE_AREA_CELL1 : TABLE_AREA_CELL10;
            String keyName = useCell1 ? TABLE_AREA_CELL1_KEY : TABLE_AREA_CELL10_KEY;
            Set<Cell> cells = useCell1 ? cells1 : cells10;

            // We create multiple queries and use a priority queue to return packets from each ship sorted by their
            // timestamp
            for (Cell c : cells) {
                queries.add(new AisStorePartialQuery(s, inner, batchLimit, tableName, keyName, c.getCellId(),
                        startTimeInclusive, stopTimeExclusive));
            }
        } else if (mmsi != null) {
            for (int m : mmsi) {
                queries.add(new AisStorePartialQuery(s, inner, batchLimit, TABLE_MMSI, TABLE_MMSI_KEY, m,
                        startTimeInclusive, stopTimeExclusive));
            }
        } else {
            int start = AisStoreSchema.getTimeBlock(startTimeInclusive);
            int stop = AisStoreSchema.getTimeBlock(stopTimeExclusive - 1);
            queries.add(new AisStorePartialQuery(s, inner, batchLimit, TABLE_TIME, TABLE_TIME_KEY, start, stop,
                    startTimeInclusive, stopTimeExclusive));
        }
        return new AisStoreQueryResult(inner, queries);

    }

    public AisStoreQueryBuilder setFetchSize(int limit) {
        this.batchLimit = limit;
        return this;
    }

    public AisStoreQueryBuilder setInterval(Interval interval) {
        return setInterval(interval.getStartMillis(), interval.getEndMillis());
    }

    /**
     * @param start
     *            the start date (inclusive)
     * @param end
     *            the end date (exclusive)
     * @return
     */
    public AisStoreQueryBuilder setInterval(long startMillies, long stopMillies) {
        this.startTimeInclusive = startMillies;
        this.stopTimeExclusive = stopMillies;
        return this;
    }

    /**
     * Creates a new builder for packets received from within the specified area in the given interval.
     * 
     * @param area
     *            the area
     * @return a query builder
     * @throws NullPointerException
     *             if the specified area is null
     */
    public static AisStoreQueryBuilder forArea(Area area) {
        return new AisStoreQueryBuilder(requireNonNull(area), null);
    }

    /**
     * Finds all packets for one or more MMSI numbers.
     * 
     * @param mmsi
     *            one or more MMSI numbers
     * @return a query builder
     */
    public static AisStoreQueryBuilder forMmsi(int... mmsi) {
        if (mmsi.length == 0) {
            throw new IllegalArgumentException("Must request at least 1 mmsi number");
        }
        return new AisStoreQueryBuilder(null, mmsi);
    }

    /**
     * Finds all packets. Should be used together with {@link #setInterval(long, long)} to limit the amount of data to
     * return.
     * 
     * @return a query builder
     */
    public static AisStoreQueryBuilder forTime() {
        return new AisStoreQueryBuilder(null, null);
    }
}