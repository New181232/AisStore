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
package dk.dma.ais.store.exporter;

import static java.util.Objects.requireNonNull;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.naming.ConfigurationException;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.IColumn;
import org.apache.cassandra.db.OnDiskAtom;
import org.apache.cassandra.db.columniterator.OnDiskAtomIterator;
import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.SSTableReader;
import org.apache.cassandra.io.sstable.SSTableScanner;

import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.store.cassandra.FullSchema;
import dk.dma.commons.util.FormatUtil;
import dk.dma.enav.util.function.EBlock;
import dk.dma.enav.util.function.Function;

/**
 * Processes Cassandras SSTables.
 * 
 * @author Kasper Nielsen
 */
public class CassandraColumnFamilyProcessor {

    /** The key of the message column. */
    private static final byte[] MESSAGE_COLUMN = FullSchema.MESSAGES_TIME.getName().getBytes();

    /** The number of bytes that have been read from disk. */
    final AtomicLong bytesRead = new AtomicLong();

    /** The name of the keyspace to use. */
    private final String keyspace;

    /** The maximum number of bytes we want to read, useful for tests. */
    private final long maxRead;

    /** If this processor has been consumed. */
    private final AtomicBoolean used = new AtomicBoolean();

    /** The URL to cassandra.yaml */
    private final String yamlUrl;

    public CassandraColumnFamilyProcessor(String yamlUrl, String keyspace) {
        this(yamlUrl, keyspace, Long.MAX_VALUE);
    }

    public CassandraColumnFamilyProcessor(String yamlUrl, String keyspace, long maxRead) {
        this.yamlUrl = requireNonNull(yamlUrl);
        this.keyspace = requireNonNull(keyspace);
        this.maxRead = maxRead;
    }

    public void process(Function<AisPacket, AisPacket> producer) throws Exception {
        if (used.getAndSet(true)) { // make sure we only use this instance once (statistics)
            throw new IllegalStateException("This processor can only be used one time");
        }

        System.setProperty("cassandra.config", "file://" + yamlUrl);

        DatabaseDescriptor.loadSchemas();
        if (Schema.instance.getNonSystemTables().size() < 1) {
            throw new ConfigurationException("no non-system tables are defined");
        }

        // Create a new immutable snapshot that we will operate on. This will also flush all data to disk
        String snapshotName = new Date().toString().replace(' ', '_').replace(':', '_');
        CassandraNodeTool node = new CassandraNodeTool();
        node.takeSnapshot(keyspace, snapshotName);

        // Start processing all SSTables
        long start = System.nanoTime();
        try {
            Exception failure = null;
            try {
                for (int i = 0; i < 50; i++) {
                    // processDataFileLocations(snapshotName, producer);
                }
            } catch (Exception e) {
                failure = e;
            } finally {
                start = System.nanoTime() - start;
                long s = TimeUnit.NANOSECONDS.toSeconds(start);
                double minuteAvg = bytesRead.get() * ((double) TimeUnit.MINUTES.toNanos(1) / (double) start);

                System.out.println("Read a total of " + FormatUtil.humanReadableByteCount(bytesRead.get(), true)
                        + " in " + String.format("%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) + " ("
                        + FormatUtil.humanReadableByteCount((long) minuteAvg, true) + "/min)");
                // producer.finished(failure);
                if (failure != null) {
                    throw failure;
                }
            }
        } finally {
            node.deleteSnapshot(keyspace, snapshotName);
        }
    }

    protected void processDataFileLocations(String snapshotName, EBlock<AisPacket> producer) throws Exception {
        for (String s : DatabaseDescriptor.getAllDataFileLocations()) {
            Path snapshots = Paths.get(s).resolve(keyspace).resolve(FullSchema.MESSAGES_TIME.getName())
                    .resolve("snapshots").resolve(snapshotName);
            // iterable through all data files (xxxx-Data)
            // if the dataformat changes hf needs to be upgraded to the current versino
            // http://svn.apache.org/repos/asf/cassandra/trunk/src/java/org/apache/cassandra/io/sstable/Descriptor.java
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(snapshots, keyspace + "-"
                    + FullSchema.MESSAGES_TIME.getName() + "-hf-*-Data.db")) {
                for (Path p : ds) { // for each data file
                    processDataFile(p, producer);
                    if (bytesRead.get() > maxRead) {
                        System.out.println("Read maximum limit of bytes, [max = " + maxRead + ", actual ="
                                + bytesRead.get() + "]");
                        return;
                    }
                }
            }
        }
    }

    protected void processDataFile(Path p, EBlock<AisPacket> producer) throws Exception {
        SSTableReader reader = SSTableReader.open(Descriptor.fromFilename(p.toString()));
        SSTableScanner scanner = reader.getDirectScanner();
        long sizeOnDisk = scanner.getLengthInBytes();
        System.out.println("Processing " + p + " [size = " + sizeOnDisk + ", uncompressed = "
                + reader.uncompressedLength() + "]");
        long start = System.nanoTime();
        try {
            long previousPosition = 0;
            while (scanner.hasNext()) {
                OnDiskAtomIterator columnIterator = scanner.next();
                try {
                    processRow(p, columnIterator, producer);
                } finally {
                    columnIterator.close();
                }
                long currentPosition = scanner.getCurrentPosition();
                if (bytesRead.addAndGet(currentPosition - previousPosition) > maxRead) {
                    return;
                }
                previousPosition = currentPosition;
            }
            start = System.nanoTime() - start;
        } finally {
            scanner.close();
        }
    }

    @SuppressWarnings("unused")
    protected void processRow(Path p, OnDiskAtomIterator columnIterator, EBlock<AisPacket> producer) throws Exception {
        while (columnIterator.hasNext()) {
            OnDiskAtom c = columnIterator.next();
            IColumn ic = (IColumn) c;
            if (Arrays.equals(MESSAGE_COLUMN, c.name().array())) {
                byte[] value = ic.value().array();
                String msg = new String(value);
                // AisPacket m = AisPacket.from(msg, 1);
                // producer.process(m);
            }
        }
    }
}
