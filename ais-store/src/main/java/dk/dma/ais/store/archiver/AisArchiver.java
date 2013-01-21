/*
 * Copyright (c) 2008 Kasper Nielsen.
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
package dk.dma.ais.store.archiver;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.google.inject.Injector;

import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.packet.AisPackets;
import dk.dma.ais.reader.AisReader;
import dk.dma.ais.reader.IAisPacketHandler;
import dk.dma.ais.reader.RoundRobinAisTcpReader;
import dk.dma.ais.store.cassandra.FullSchema;
import dk.dma.app.application.AbstractDaemon;
import dk.dma.app.cassandra.KeySpaceConnection;
import dk.dma.app.management.ManagedAttribute;
import dk.dma.app.management.ManagedResource;
import dk.dma.app.service.AbstractBatchedStage;
import dk.dma.app.service.io.FileWriterService;

/**
 * 
 * @author Kasper Nielsen
 */
@ManagedResource
public class AisArchiver extends AbstractDaemon {

    @Parameter(names = "-backup", description = "The backup directory")
    File backup = new File("./aisbackup");

    @Parameter(names = "-backupformat", description = "The backup Format")
    String backupFormat = "yyyy/MM-dd/'ais-store-failed' yyyy.MM.dd HH:mm'.txt.zip'";

    @Parameter(names = "-store", description = "A list of cassandra hosts that can store the data")
    List<String> cassandraSeeds = Arrays.asList("10.10.5.201");

    @Parameter(names = "-source", description = "A list of AIS sources")
    List<String> sources = Arrays.asList("ais163.sealan.dk:65262,ais167.sealan.dk:65261",
            "iala63.sealan.dk:4712,iala68.sealan.dk:4712", "10.10.5.144:65061");
    // List<String> sources = Arrays.asList("ais163.sealan.dk:65262");

    private volatile AbstractBatchedStage<AisPacket> s;

    /** {@inheritDoc} */
    @Override
    protected void externalShutdown() {}

    @ManagedAttribute
    public long getNumberOfProcessedPackages() {
        return s == null ? 0 : s.getNumberOfMessagesProcessed();
    }

    /** {@inheritDoc} */
    @Override
    protected void runDaemon(Injector injector) throws Exception {

        // Should check that key space exists

        // Start the backup filestage that will write files to disk if disconnected
        final AbstractBatchedStage<AisPacket> fileWriter = start(FileWriterService.dateService(backup.toPath(),
                backupFormat, AisPackets.OUTPUT_TO_TEXT));

        // Setup keyspace for cassandra
        KeySpaceConnection con = start(KeySpaceConnection.connect("aisdata", cassandraSeeds));

        // Start a stage that will write each packet to cassandra
        // We write batches of 1000 events at a time
        final AbstractBatchedStage<AisPacket> cassandra = s = start(con.createdBatchedStage(500, FullSchema.INSTANCE));

        // setup AisReaders
        for (String str : sources) {
            AisReader reader = new RoundRobinAisTcpReader().setCommaseparatedHostPort(str);
            start(AisTool.wrapAisReader(reader, new IAisPacketHandler() {
                @Override
                public void receivePacket(AisPacket aisPacket) {
                    // We use offer because we do not want to block receiving
                    if (!cassandra.getInputQueue().offer(aisPacket)) {
                        if (!fileWriter.getInputQueue().offer(aisPacket)) {
                            System.err.println("Could not persist packet");
                        }
                    }
                }
            }));
        }
    }

    public static void main(String[] args) throws Exception {
        // args = new String[] { "-source", "ais163.sealan.dk:65262", "-store", "localhost" };
        new AisArchiver().execute(args);
    }
}
