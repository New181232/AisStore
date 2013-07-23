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

import dk.dma.ais.store.exporter2.FileExport;
import dk.dma.commons.app.CliCommandList;
import dk.dma.commons.app.CliCommandList.Command;

/**
 * The command line interface to AisStore.
 * 
 * @author Kasper Nielsen
 */
public class Main {

    public static void main(String[] args) throws Exception {
        CliCommandList c = new CliCommandList("AisStore");
        c.add("archive", "Reads data from AIS datasources and stores data into Cassandra", new Command() {
            public void execute(String[] args) throws Exception {
                Archiver.main(args);
            }
        });

        c.add("import", "Imports data from text files and stores data into Cassandra", new Command() {
            public void execute(String[] args) throws Exception {
                // FileImport.main(args);
            }
        });

        c.add("export", "Exports data from Cassandra into text files", new Command() {
            public void execute(String[] args) throws Exception {
                FileExport.main(args);
            }
        });

        c.invoke(args);
    }
}
