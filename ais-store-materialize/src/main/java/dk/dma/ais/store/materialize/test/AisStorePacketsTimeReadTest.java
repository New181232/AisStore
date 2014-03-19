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
package dk.dma.ais.store.materialize.test;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Logger;

import com.google.inject.Injector;

import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.packet.AisPacketOutputSinks;
import dk.dma.ais.store.AisStoreQueryBuilder;
import dk.dma.ais.store.materialize.Scan;
import dk.dma.commons.util.io.OutputStreamSink;
import dk.dma.db.cassandra.CassandraConnection;
/**
 * Test of scan speed for AisStore packets_time
 * @author Jens Tuxen
 *
 */
@SuppressWarnings("deprecation")
public final class AisStorePacketsTimeReadTest extends Scan {
    Logger LOG = Logger.getLogger(AisStorePacketsTimeReadTest.class);
        
    BufferedOutputStream bos;
    OutputStreamSink<AisPacket> sink;
    protected CassandraConnection con;
    
    Integer batchSize = 100000;

    @Override
    public void run(Injector arg0) throws Exception {
        LOG.debug("Start Date:\t\t"+start.toLocaleString());
        if (stop == null) {
            stop = new Date(start.getTime()+(24*60*60*1000));
            LOG.debug("Stop date calculated as "+stop.toLocaleString());
        } else {
            throw new NotImplementedException("Sorry, stop is not supported in this cli-util. Use -start (+24h)");
        }
        
        con = CassandraConnection.create(keySpace, hosts);
        con.start();

        //sets up protected fields sw, pw, etc.
        this.init();
        bos = new BufferedOutputStream(new NullOutputStream());
        sink = AisPacketOutputSinks.OUTPUT_TO_TEXT;

        try {

            sw.setStartTime(System.currentTimeMillis());
            Iterable<AisPacket> iter = makeRequest();

            for (AisPacket p : iter) {
                this.accept(p);

                if (count.get() % batchSize == 0) {
                    sw.setEndTime(System.currentTimeMillis());
                    sw.print();
                    LOG.debug(sw.toCSV());
                }

            }

            sw.setEndTime(System.currentTimeMillis());
            long ms = System.currentTimeMillis() - sw.getStartTime();
            long s = ms / 1000;

            LOG.debug("Result:");

            LOG.debug("Total Packets   per 1day:\t" + count.get()
                    + " packets");
            LOG.debug("Average Packets per 1h:\t" + count.get() / 24);
            LOG.debug("Average Packets per 1min:\t " + count.get()
                    / 24 / 60);
            LOG.debug("Average Packets per 1sec:\t " + count.get()
                    / 24 / 60 / 60);

            LOG.debug("Read Speed:");
            LOG.debug("Average Packets per 1day:\t" + count.get() / s
                    * 60 * 60 * 24 + " packets/day");
            LOG.debug("Average Packets per 1h:\t" + count.get() / s
                    * 60 * 60  + " packets/h");
            LOG.debug("Average Packets per 1min:\t" + count.get() / s
                    * 60  + " packets/min");
            LOG.debug("Average Packets per 1sec:\t" + count.get() / s
                    + " packets/s");

            LOG.debug("Read/Write ratio:\t" + (count.get()/s * 60 * 60 * 24)
                    / count.get() + "");
            LOG.debug("Total Time To Extract 1day:\t" + s / 60
                    + " minutes");
            LOG.debug("Total Time To Extract 1h:\t" + s / 24 + " seconds");

            LOG.debug(sw.toCSV());
            sw.print();

            

        } finally {
            con.stop();
            pw.close();
        }
    }

    public static void main(String[] args) throws Exception {
        new AisStorePacketsTimeReadTest().execute(args);
    }

    @Override
    public void accept(AisPacket arg0) {
        final long c = count.getAndIncrement();
        try {
            this.process(bos, arg0, c);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void process(BufferedOutputStream bos, AisPacket p, long count)
            throws IOException {
        sink.process(bos, p, count);
    }
    
    @Override
    protected Iterable<AisPacket> makeRequest() {
        return con.execute(AisStoreQueryBuilder.forTime().setInterval(start.getTime(),stop.getTime()));
    }
    
    
    /** Writes to nowhere */
    class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            b++; //do something with the byte just to be sure
        }
    }
}
