/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.ais.store;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Select.Where;
import com.google.common.collect.AbstractIterator;
import com.google.common.primitives.Longs;
import dk.dma.ais.packet.AisPacket;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static dk.dma.ais.store.AisStoreSchema.COLUMN_TIMESTAMP;
import static java.util.Objects.requireNonNull;

/**
 * This will generate one complete query for a data range
 * 
 * @author Jens Tuxen
 */
class AisStoreCompleteQuery extends AbstractIterator<AisPacket> {

    /**
     * AisPacket implements Comparable. But I'm to afraid someone might break
     * the functionality someday. So we make a manual iterator
     * */
    static final Comparator<AisPacket> COMPARATOR = new Comparator<AisPacket>() {
        public int compare(AisPacket p1, AisPacket p2) {
            return Long.compare(p1.getBestTimestamp(), p2.getBestTimestamp());
        }
    };

    /** The number of results to get at a time. */
    private final int batchLimit;

    /** The session used for querying. */
    private final Session session;

    /** The name of the table we are querying. */
    private final String tableName;

    /** The name of the key row in the table. */
    private final String rowName;

    /**
     * The first timestamp for which to get packets (inclusive). Is constantly
     * updated to the timestamp of the last received packet as data comes in.
     */
    private long timeStart;

    /** The last timestamp for which to get packets (exclusive) */
    private final long timeStop;

    private int currentRow;

    private final int lastRow;

    /**
     * All queries are done asynchronously. This future holds the result of the
     * last query we made.
     */
    private ResultSetFuture future;

    /**
     * A list of packets that we have received from AisStore but have not yet
     * returned to the user.
     */
    private LinkedList<AisPacket> packets = new LinkedList<>();

    /** The number of packets that was retrieved. */
    private volatile long retrievedPackets;

    private volatile long lastestDateReceived;

    private final AisStoreQueryInnerContext inner;

    private Iterator<Row> it;
    private ResultSet rs;

    AisStoreCompleteQuery(Session session, AisStoreQueryInnerContext inner,
            int batchLimit, String tableName, String rowName, int rowStart,
            long timeStartInclusive, long timeStopExclusive) {
        this(session, inner, batchLimit, tableName, rowName, rowStart,
                rowStart, timeStartInclusive, timeStopExclusive);
    }

    AisStoreCompleteQuery(Session session, AisStoreQueryInnerContext inner,
            int batchLimit, String tableName, String rowName, int rowStart,
            int rowStop, long timeStartInclusive, long timeStopExclusive) {
        this.session = requireNonNull(session);
        this.tableName = requireNonNull(tableName);
        this.rowName = requireNonNull(rowName);
        this.currentRow = rowStart;
        this.lastRow = rowStop;
        this.batchLimit = batchLimit;
        this.timeStart = timeStartInclusive;
        this.timeStop = timeStopExclusive;
        this.inner = inner;

        execute();
        inner.queries.add(this);

    }

    long getNumberOfRetrievedPackets() {
        return retrievedPackets;
    }

    long getLatestRetrievedTimestamp() {
        return lastestDateReceived;
    }

    public AisPacket computeNext() {
        AisPacket next = packets.poll();
        if (next != null) {
            return next;
        }
        while (currentRow <= lastRow) {
            Row row = null;
            int innerReceived = 0;
            while (it.hasNext() && innerReceived < batchLimit) {

                // optimistic automatic-paging+fetch
                if (rs.getAvailableWithoutFetching() == 100
                        && !rs.isFullyFetched()) {
                    rs.fetchMoreResults();
                }

                row = it.next();
                packets.add(AisPacket.fromByteBuffer(row.getBytes(1)));
                retrievedPackets++;
                innerReceived++;
            }

            if (innerReceived > 0) {
                ByteBuffer buf = row.getBytes(0);
                byte[] bytes = new byte[buf.remaining()];
                buf.get(bytes);

                lastestDateReceived = Longs.fromByteArray(bytes);

                // currentRow == lastRow when packets_mmsi or packets_cell
                if (!(currentRow == lastRow)) {
                    currentRow = AisStoreSchema.getTimeBlock(new Date(
                            lastestDateReceived).getTime());
                    // System.out.println("Currently at: "+currentRow+" Last ROW is: "+lastRow);
                }

                System.out.println("Currently at: "
                        + new Date(lastestDateReceived));

            }

            if (!packets.isEmpty()) {
                return packets.poll();
            }

            if (rs.isFullyFetched() || future.isDone()) {
                currentRow = lastRow + 1;
                inner.finished(this);
                return endOfData();
            }

        }

        inner.finished(this);
        return endOfData();
    }

    /** execute takes over from advance, which is not necessary anymore */
    void execute() {
        // We need timehash to find out what the timestamp of the last received
        // packet is.
        // When the datastax driver supports unlimited fetching we will only
        // need aisdata
        Select s = QueryBuilder.select(COLUMN_TIMESTAMP, "aisdata").from(tableName);
        Where w = null;
        switch (tableName) {
        case AisStoreSchema.TABLE_TIME:
            List<Integer> blocks = new ArrayList<>();
            int block = currentRow;
            while (block <= lastRow) {
                blocks.add(block);
                block++;
            }
            Integer[] blocksInt = blocks.toArray(new Integer[blocks.size()]);
            // time must be greater than start
            w = s.where(QueryBuilder.in(rowName, (Object[]) blocksInt));
            break;
        default:
            w = s.where(QueryBuilder.eq(rowName, currentRow));
            break;
        }

        w.and(QueryBuilder.gt(COLUMN_TIMESTAMP, timeStart));
        w.and(QueryBuilder.lt(COLUMN_TIMESTAMP, timeStop)); // time must be less than stop
        s.limit(Integer.MAX_VALUE); // Sets the limit
        s.setFetchSize(batchLimit);
        s.setConsistencyLevel(ConsistencyLevel.ONE);
        System.out.println(s.getQueryString());
        future = session.executeAsync(s);
        rs = future.getUninterruptibly();
        it = rs.iterator();
    }

}
