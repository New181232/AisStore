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
package dk.dma.ais.store.write;

import static dk.dma.ais.store.AisStoreSchema.storeByArea;
import static dk.dma.ais.store.AisStoreSchema.storeByMmsi;
import static dk.dma.ais.store.AisStoreSchema.storeByTime;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.datastax.driver.core.Statement;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import dk.dma.ais.message.AisMessage;
import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.store.AisStoreConnection;
import dk.dma.commons.tracker.PositionTracker;
import dk.dma.enav.model.geometry.Position;

/**
 * The schema used in AisStore.
 * 
 * @author Kasper Nielsen
 */
public abstract class DefaultAisStoreWriter extends CassandraBatchedStagedWriter<AisPacket> {

    /**
     * The duration in milliseconds from when the latest positional message is received for a specific mmsi number is
     * still valid.
     */
    // TODO different for sat packets???
    public static final long POSITION_TIMEOUT_MS = TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES);

    /**
     * @param connection
     * @param batchSize
     */
    public DefaultAisStoreWriter(AisStoreConnection connection, int batchSize) {
        super(connection, batchSize);
    }

    /** A position tracker used to keeping an eye on previously received messages. */
    private final PositionTracker<Integer> tracker = new PositionTracker<>();

    public void handleMessage(List<Statement> batch, AisPacket packet) {
        // TODO FIX timestamp, uses best sometimes, and getTimestamp other time
        long ts = packet.getTimestamp().getTime();
        byte[] column = Bytes.concat(Longs.toByteArray(ts), packet.calculateHash128()); // the column
        byte[] data = packet.toByteArray(); // the serialized packet

        storeByTime(batch, ts, column, data); // Store packet by time

        // packets are only stored by time, if they are not a proper message
        AisMessage message = packet.tryGetAisMessage();
        if (message == null) {
            return;
        }

        storeByMmsi(batch, ts, column, message.getUserId(), data); // Store packet by mmsi

        Position p = message.getValidPosition();
        if (p == null) { // Try to find an estimated position
            // Use the last received position message unless the position has timed out (POSITION_TIMEOUT_MS)
            p = tracker.getLatestIfLaterThan(message.getUserId(), packet.getBestTimestamp() - POSITION_TIMEOUT_MS);
        } else { // Update the tracker with latest position
            tracker.update(message.getUserId(), p.withTime(packet.getBestTimestamp()));
        }
        storeByArea(batch, ts, column, message.getUserId(), p, data);
    }
}
