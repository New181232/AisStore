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
package dk.dma.ais.store.old.exporter;

import org.apache.cassandra.tools.NodeCmd;

import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dk.dma.ais.store.AisStoreSchema.Table.TABLE_PACKETS_TIME;
import static java.util.Objects.requireNonNull;

/**
 * Writing a snapshot takes a couple of tricks. Because someone decided it would be a good idea using System.exit(0) in
 * NodeCmd. We install a fake SecurityManager that catches whenever NodeCmd tries to exit with status code 0.
 * 
 * @author Kasper Nielsen
 */
public class CassandraNodeTool {

    private final String host;

    private final int port;

    public CassandraNodeTool() {
        this("localhost", 7199);
    }

    CassandraNodeTool(String host, int port) {
        this.host = requireNonNull(host);
        this.port = port;
    }

    public void deleteSnapshot(String keyspace, String snapshotName) throws Exception {
        execute("clearsnapshot", requireNonNull(keyspace), "-t", requireNonNull(snapshotName));
    }

    private void execute(String... args) throws Exception {
        List<String> list = new ArrayList<>(Arrays.asList("-h", host, "-p", "" + port));
        list.addAll(Arrays.asList(args));

        System.setSecurityManager(new NoExitSecurityManager());
        try {
            NodeCmd.main(list.toArray(new String[list.size()]));
        } catch (DummyException ok) {} finally {
            System.setSecurityManager(null);
        }
    }

    public void takeSnapshot(String keyspace, String snapshotName) throws Exception {
        execute("snapshot", requireNonNull(keyspace), "-cf", TABLE_PACKETS_TIME.toString(), "-t", requireNonNull(snapshotName));
    }

    @SuppressWarnings("serial")
    static class DummyException extends SecurityException {}

    static class NoExitSecurityManager extends SecurityManager {
        @Override
        public void checkExit(int status) {
            if (status == 0) {// only throw if successful, in this way we exit on error conditions
                throw new DummyException();
            }
        }

        @Override
        public void checkPermission(Permission perm) {}

        @Override
        public void checkPermission(Permission perm, Object context) {}
    }
}
