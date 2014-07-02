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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.exceptions.QueryValidationException;
import com.datastax.driver.core.querybuilder.Batch;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import dk.dma.commons.service.AbstractBatchedStage;
import dk.dma.commons.util.DurationFormatter;
import dk.dma.db.cassandra.CassandraConnection;

/**
 * 
 * @author Kasper Nielsen
 */
public abstract class CassandraBatchedStagedWriter<T> extends AbstractBatchedStage<T> {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(CassandraBatchedStagedWriter.class);

    /** The connection to Cassandra. */
    private final CassandraConnection connection;

    final MetricRegistry metrics = new MetricRegistry();

    final Meter persistedCount = metrics.meter(MetricRegistry.name("aistore", "cassandra",
            "Number of persisted AIS messages"));

    /** greater than 0 if the last batch was slow. */
    private int lastSlowBatch;

    /**
     * @param queueSize
     * @param maxBatchSize
     */
    public CassandraBatchedStagedWriter(CassandraConnection connection, int batchSize) {
        super(Math.min(100000, batchSize * 100), batchSize);
        this.connection = requireNonNull(connection);
        final JmxReporter reporter = JmxReporter.forRegistry(metrics).inDomain("fooo.erer.er").build();
        reporter.start();
    }

    /** {@inheritDoc} */
    @Override
    protected final void handleMessages(List<T> messages) {
        long start = System.nanoTime();
        // Create a batch of message that we want to write.
        List<RegularStatement> statements = new ArrayList<>();
        for (T t : messages) {
            try {
                handleMessage(statements, t);
            } catch (RuntimeException e) {
                LOG.warn("Failed to write message: " + t, e); // Just in case we cannot process a message
            }
        }

        // Try writing the batch
        try {
            Batch batch = QueryBuilder.batch(statements.toArray(new RegularStatement[statements.size()]));
            
            
            long beforeSend = System.nanoTime();
            connection.getSession().execute(batch);
            long total = System.nanoTime();
            // Is this an abnormal slow batch?
            boolean isSlow = TimeUnit.MILLISECONDS.convert(total - start, TimeUnit.NANOSECONDS) > 200
                    || messages.size() >= getBatchSize();
            if (isSlow || lastSlowBatch > 0) {
                LOG.info("Total time: " + DurationFormatter.DEFAULT.formatNanos(total - start) + ", prepping="
                        + DurationFormatter.DEFAULT.formatNanos(beforeSend - start) + ", sending="
                        + DurationFormatter.DEFAULT.formatNanos(total - beforeSend) + ", size=" + messages.size());
                // makes sure we write 10 info statements after the last slow batch we insert
                lastSlowBatch = isSlow ? 10 : lastSlowBatch - 1;
            }
            persistedCount.mark(messages.size());
            // sink.onSucces(messages);
        } catch (QueryValidationException e) {
            LOG.error("Could not execute query, this is an internal error", e);
        } catch (Exception e) {
            onFailure(messages, e);
            try {
                sleepUntilShutdown(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
                Thread.interrupted();
            }
        }
    }

    protected abstract void handleMessage(List<RegularStatement> statements, T message);

    public abstract void onFailure(List<T> messages, Throwable cause);

}
// batch.enableTracing();
// ExecutionInfo executionInfo = connection.getSession().execute(batch).getExecutionInfo();
// Thread.sleep(50); <~ we need to sleep when tracing
// System.out.println(executionInfo.getQueryTrace().getDurationMicros());
// for (QueryTrace.Event e : executionInfo.getQueryTrace().getEvents()) {
// System.out.println(e.getSourceElapsedMicros() + " : " + e.getDescription());
// }
