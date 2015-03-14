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

import dk.dma.ais.message.AisMessage;
import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.store.AisStoreSchema.Table;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Date;

import static dk.dma.ais.store.AisStoreSchema.Table.TABLE_PACKETS_MMSI;
import static dk.dma.ais.store.AisStoreSchema.getDigest;
import static dk.dma.ais.store.AisStoreSchema.timeBlock;

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
public class PacketsMmsiSSTableWriter extends SSTableWriter {

    private static final Logger LOG = LoggerFactory.getLogger(PacketsMmsiSSTableWriter.class);

    public PacketsMmsiSSTableWriter(String outputDir, String keyspace) {
        super(
                outputDir,
                keyspace,
                String.format(
                        "CREATE TABLE %s.%s (" +
                                "mmsi int," +
                                "timeblock int," +
                                "time timestamp," +
                                "digest blob," +
                                "aisdata ascii," +
                                "PRIMARY KEY ((mmsi, timeblock), time, digest)" +
                                ") WITH CLUSTERING ORDER BY (time ASC, digest ASC)", keyspace, TABLE_PACKETS_MMSI.toString()
                ),
                String.format(
                        "INSERT INTO %s.%s (mmsi, timeblock, time, digest, aisdata) VALUES (?, ?, ?, ?, ?)", keyspace, TABLE_PACKETS_MMSI.toString()
                )
        );
    }

    @Override
    public void accept(AisPacket packet) {
        incNumberOfPacketsProcessed();

        final long ts = packet.getBestTimestamp();
        if (ts > 0) {
            AisMessage message = packet.tryGetAisMessage();
            if (message != null) {
                final int mmsi = message.getUserId();
                if (mmsi >= 0) {
                    try {
                        writer().addRow(mmsi, timeBlock(table(), Instant.ofEpochMilli(ts)), new Date(ts), ByteBuffer.wrap(getDigest(packet)), packet.getStringMessage());
                    } catch (InvalidRequestException e) {
                        LOG.error("Failed to store message in " + table().toString() + " due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    } catch (IOException e) {
                        LOG.error("Failed to store message in " + table().toString() + " due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                } else {
                    LOG.error("Cannot get MMSI from: " + packet.getStringMessage());
                }
            } else {
                LOG.error("Cannot decode: " + packet.getStringMessage());
            }
        } else {
            LOG.error("Cannot get timestamp from: " + packet.getStringMessage());
        }
    }

    @Override
    public Table table() {
        return TABLE_PACKETS_MMSI;
    }
}
