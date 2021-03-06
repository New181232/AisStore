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

import dk.dma.commons.app.CliCommandList;

/**
 * The command line interface to AisStore.
 * 
 * @author Kasper Nielsen
 */
public class Main {

    public static void main(String[] args) throws Exception {
        CliCommandList c = new CliCommandList("AisStore");
        c.add(Archiver.class, "archive", "Reads data from AIS datasources and stores data into Cassandra");
        c.add(FileImport.class, "import", "Imports data from text files and stores data into Cassandra");
        c.add(FileExport.class, "export", "Exports data from Cassandra into text files");
        c.add(FileSSTableConverter.class, "generate", "Generate SSTables from AIS data, which can be imported into Cassandra later");
        c.add(FileExportRest.class, "exportRest", "Utilize REST to retrieve data from AisStore");
        c.add(FileDiff.class, "diff", "Finds data in the input file which are not stored in Cassandra");
        c.add(CassandraStats.class, "stats", "Output statistics about contents in Cassandra (expensive operation)");
        c.invoke(args);
    }
}
