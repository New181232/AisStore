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
package dk.dma.ais.store.web.rest;

import java.text.DecimalFormat;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.joda.time.Interval;
import org.joda.time.Period;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import dk.dma.ais.message.AisMessage;
import dk.dma.ais.message.AisPosition;
import dk.dma.ais.message.IVesselPositionMessage;
import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.store.web.rest.XStreamOutputStreamSink.OutputType;
import dk.dma.commons.util.io.OutputStreamSink;
import dk.dma.db.Query;
import dk.dma.enav.model.geometry.Position;

/**
 * 
 * @author Kasper Nielsen
 */
@Path("/track")
public class RestTrackService extends AbstractRestService {

    public RestTrackService() throws Exception {
        super();
    }

    private StreamingOutput execute(int mmsi, UriInfo info, OutputStreamSink<AisPacket> oss) {
        Interval interval = findInterval(info);
        Query<AisPacket> q = mqs.findByMMSI(mmsi, interval);
        return createStreamingOutput(q, oss);
    }

    @GET
    @Path("{mmsi : \\d+}/json")
    @Produces("application/json")
    public StreamingOutput json(@PathParam("mmsi") int mmsi, @Context UriInfo info) {
        return execute(mmsi, info, new Sink(OutputType.JSON, info));
    }

    @GET
    @Path("{mmsi : \\d+}/xml")
    @Produces("text/xml")
    public StreamingOutput xml(@PathParam("mmsi") int mmsi, @Context UriInfo info) {
        return execute(mmsi, info, new Sink(OutputType.XML, info));
    }

    /** A sink that uses XStream to write out the data */
    static class Sink extends XStreamOutputStreamSink<AisPacket> {
        static final DecimalFormat df = new DecimalFormat("###.#####");

        Position lastPosition;
        Long lastTimestamp;
        final Long sampleDuration;
        final Integer samplePositions;

        Sink(XStreamOutputStreamSink.OutputType outputType, UriInfo info) {
            super(AisPacket.class, "track", "point", outputType);
            String sp = info.getQueryParameters().getFirst("minDistance");
            String dur = info.getQueryParameters().getFirst("minDuration");
            samplePositions = sp == null ? null : Integer.parseInt(sp);
            sampleDuration = dur == null ? null : Period.parse(dur).toStandardSeconds().getSeconds() * 1000L;
        }

        /** {@inheritDoc} */
        @Override
        public void write(AisPacket p, HierarchicalStreamWriter writer, MarshallingContext context) {
            AisMessage m = p.tryGetAisMessage();
            IVesselPositionMessage im = (IVesselPositionMessage) m;
            Position pos = im.getPos().getGeoLocation();
            lastPosition = pos;
            lastTimestamp = p.getBestTimestamp();
            w(writer, "timestamp", p.getBestTimestamp());
            w(writer, "lon", df.format(pos.getLongitude()));
            w(writer, "lat", df.format(pos.getLatitude()));
            w(writer, "sog", im.getSog());
            w(writer, "cog", im.getCog());
            w(writer, "heading", im.getTrueHeading());
        }

        public boolean isPacketWriteable(AisPacket packet) {
            AisMessage m = packet.tryGetAisMessage();
            if (m instanceof IVesselPositionMessage) {
                AisPosition a = ((IVesselPositionMessage) m).getPos();
                if (a != null) {
                    Position pos = a.getGeoLocation();
                    if (pos != null) {
                        if (sampleDuration == null && samplePositions == null) {
                            return true;
                        }
                        if (samplePositions != null
                                && (lastPosition == null || lastPosition.rhumbLineDistanceTo(pos) >= samplePositions)) {
                            return true;
                        }
                        return sampleDuration != null
                                && (lastTimestamp == null || packet.getBestTimestamp() - lastTimestamp >= sampleDuration);
                    }
                }
            }
            return false;
        }
    };
}