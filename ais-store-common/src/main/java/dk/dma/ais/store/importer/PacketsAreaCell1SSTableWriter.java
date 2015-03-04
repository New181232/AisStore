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
package dk.dma.ais.store.importer;

import dk.dma.ais.packet.AisPacket;
import dk.dma.enav.model.geometry.Position;
import org.apache.cassandra.exceptions.InvalidRequestException;

import java.io.IOException;
import java.util.Objects;

import static dk.dma.ais.store.AisStoreSchema.getDigest;
import static dk.dma.ais.store.AisStoreSchema.getTimeBlock;

/**
 * Creates an AisStore Table/Schema writer, see AisStoreTableWriters for implementation.
 *
 * See also
 *   - http://www.datastax.com/dev/blog/bulk-loading
 *   - https://github.com/yukim/cassandra-bulkload-example/blob/master/src/main/java/bulkload/BulkLoad.java
 *
 * @param types note: need to be aware of super composite keys as partition key, for instance.
 * @author Jens Tuxen
 *
 */
public class PacketsAreaCell1SSTableWriter extends AisStoreSSTableWriter {

    /** Keyspace name */
    public static final String KEYSPACE = "aisdata";

    /** Table name */
    public static final String TABLE = "packets_area_cell1";

    /**
     * Schema for bulk loading table.
     * It is important not to forget adding keyspace name before table name,
     * otherwise CQLSSTableWriter throws exception.
     */
    public static final String SCHEMA = String.format(
        "CREATE TABLE %s.%s (" +
            "cellid int," +
            "timeblock int," +
            "time timestamp," +
            "digest blob," +
            "aisdata ascii," +
            "PRIMARY KEY ((cellid, timeblock), time, digest)" +
        ") WITH CLUSTERING ORDER BY (time ASC, digest ASC)", KEYSPACE, TABLE);

    /**
     * INSERT statement to bulk load.
     * It is like prepared statement. You fill in place holder for each data.
     */
    public static final String INSERT_STMT = String.format("INSERT INTO %s.%s (cellid, timeblock, time, digest, aisdata) VALUES (?, ?, ?, ?, ?)", KEYSPACE, TABLE);

    public PacketsAreaCell1SSTableWriter(String outputDir) {
        super(outputDir, SCHEMA, INSERT_STMT);
    }

    public void addPacket(AisPacket packet, Position p) {
        Objects.requireNonNull(packet);
        Objects.requireNonNull(p);

        final long ts = packet.getBestTimestamp();
        if (ts > 0) {
            final int cellid = p.getCellInt(1.0);
            try {
                writer.addRow(cellid, getTimeBlock(ts), ts, getDigest(packet), packet.toByteArray());
            } catch (InvalidRequestException e) {
                System.out.println("Failed to store message in " + TABLE + " due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (IOException e) {
                System.out.println("Failed to store message in " + TABLE + " due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } else {
            System.out.println("Cannot get timestamp from: " + packet.getStringMessage());
        }
    }
}
