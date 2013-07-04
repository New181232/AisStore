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

import org.joda.time.Interval;

import dk.dma.ais.packet.AisPacket;
import dk.dma.enav.model.geometry.Area;

/**
 * The entry interface to access AisStore.
 * 
 * @author Kasper Nielsen
 */
public interface AisStore {

    /**
     * Finds all packets for the specified area in the given interval.
     * 
     * @param area
     *            the area
     * @param start
     *            the start date (inclusive)
     * @param end
     *            the end date (exclusive)
     * @return a new query
     */
    Query<AisPacket> findByArea(Area shape, Interval interval);

    /**
     * Finds all packets for the specified mmsi number in the given interval.
     * 
     * @param mmsi
     *            the mssi number
     * @param start
     *            the start date (inclusive)
     * @param end
     *            the end date (exclusive)
     * @return a new query
     */
    Query<AisPacket> findByMMSI(int mmsi, Interval interval);

    /**
     * Finds all packets received in the given interval. Should only be used for small time intervals.
     * 
     * @param mmsi
     *            the mssi number
     * @param start
     *            the start date (inclusive)
     * @param end
     *            the end date (exclusive)
     * @return a new query
     */
    Query<AisPacket> findByTime(Interval interval);
}

//
//
// /**
// * Finds the positions of all vessels at the specified date. If the specified time resolution is
// * {@link TimeUnit#HOURS} it will return the latest reported position from the same hour as the specified date.
// * Likewise if the specified time resolution is {@link TimeUnit#DAYS} it will return the latest reported position
// * from the same day as the specified date.
// *
// * @param date
// * the date
// * @param timeResolution
// * the resolution either {@link TimeUnit#HOURS} or {@link TimeUnit#DAYS}
// * @return a result object
// * @throws IllegalArgumentException
// * if time resolution is not either {@link TimeUnit#HOURS} or {@link TimeUnit#DAYS}
// * @throws Exception
// */
// Query<PositionTime> findAllPositions(Date date, TimeUnit timeResolution) throws Exception;
//
//
//
// Query<PositionTime> findCells(Date date, TimeUnit timeResolution) throws Exception;
//
// // uses mmsi_hour or mmsi_day
// Query<CellPositionMmsi> findCells(int mmsi, Interval interval, CellResolution cellResolution) throws Exception;
//
// Query<CellPositionMmsi> findInCell(int cellId, CellResolution resolution, Interval interval) throws Exception;
//
// Query<PositionTime> findPositions(int mmsi, Interval interval, CellResolution resolution, TimeUnit
// timeResolution)
// throws Exception;
