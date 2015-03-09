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

import com.google.common.hash.Hashing;
import com.google.common.primitives.Ints;
import dk.dma.ais.packet.AisPacket;

import java.time.Duration;
import java.time.Instant;

/**
 * This file contains the schema that is being used to store data in AisStore. It also contains various utility methods.
 * 
 * @author Kasper Nielsen
 */
public class AisStoreSchema {

    /** Identifiers of the columns in use (names are shared across tables). */
    public static enum Column {
        /** Common name of column holding time block (i.e. no. of 10 minute blocks since the epoch. */
        COLUMN_TIMEBLOCK("timeblock"),

        /** Common name of column holding timestamp with millisecond precision. */
        COLUMN_TIMESTAMP("time"),

        /** Common name of column holding cellid (geographical area) */
        COLUMN_CELLID("cellid"),

        /** Common name of column holding MMSI no. */
        COLUMN_MMSI("mmsi"),

        /** Common name of column holding aisdata message digest (to avoid storing duplicates) */
        COLUMN_AISDATA_DIGEST("digest"),

        /** We store the actual AIS message in this column. */
        COLUMN_AISDATA("aisdata");

        private final String columnName;
        private Column(String columnName) {
            this.columnName = columnName;
        }
        public String toString() {
            return this.columnName;
        }
    }

    /** Identifiers of the different tables in use. */
    public static enum Table {
        /**
         * This table holds all packets stored in row with the number of 10 minute blocks since the
         * epoch as the key. Within each 10 minute blocks, packets are store ordered by timestamp (and a message
         * digest to avoid duplicates).
         */
        TABLE_PACKETS_TIME("packets_time"),

        /**
         * This table holds all packets (with a valid message) stored in row with the MMSI number as the key. The columns
         * are ordered by timestamp and a message digest to avoid duplicates.
         */
        TABLE_PACKETS_MMSI("packets_mmsi"),

        /** This table contains AIS packets ordered by timeblock and geographic cells of size 1 degree. */
        TABLE_PACKETS_AREA_CELL1("packets_area_cell1"),

        /** This table contains AIS packets ordered by timeblock and geographic cells of size 10 degrees. */
        TABLE_PACKETS_AREA_CELL10("packets_area_cell10"),

        /** This table holds AIS packets ordered by MMSI number with an unknown position. */
        TABLE_PACKETS_AREA_UNKNOWN("packets_area_unknown");

        private final String tableName;
        private Table(String tableName) {
            this.tableName = tableName;
        }
        public String toString() {
            return this.tableName;
        }
    }

    /**
     * Converts a milliseconds since epoch to a 10-minute blocks since epoch.
     *
     * @param timestamp the timestamp to convert
     * @return the converted value
     */
    public static final int getTimeBlock(Table table, Instant timestamp) {
        int timeblock = -1;
        switch (table) {
            case TABLE_PACKETS_TIME:
            case TABLE_PACKETS_AREA_CELL1:
            case TABLE_PACKETS_AREA_CELL10:
                timeblock = getTimeBlock(timestamp, Duration.ofMinutes(10));
                break;
            case TABLE_PACKETS_MMSI:
            case TABLE_PACKETS_AREA_UNKNOWN:
                timeblock =  getTimeBlock(timestamp, Duration.ofDays(30));
                break;
            default:
                throw new IllegalArgumentException(table.toString());
        }
        return timeblock;
    }

    private static final int getTimeBlock(Instant timestamp, Duration unit) {
        return Ints.checkedCast(timestamp.getEpochSecond()/unit.getSeconds());
    }

    /**
     * Calculates a message digest for the given messages (AisPackets).
     */
    public static final byte[] getDigest(AisPacket packet) {
        return Hashing.murmur3_128().hashUnencodedChars(packet.getStringMessage()).asBytes();
    }
}
