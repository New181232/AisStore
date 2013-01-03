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
package dk.dma.app.cassandra;

import static java.util.Objects.requireNonNull;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import dk.dma.app.io.OutputStreamSink;
import dk.dma.app.util.function.EBlock;
import dk.dma.app.util.function.ESupplier;
import dk.dma.app.util.function.Function;
import dk.dma.app.util.function.Predicate;

/**
 * 
 * @author Kasper Nielsen
 */
public abstract class Query<T> implements Iterable<T> {

    /** {@inheritDoc} */
    @Override
    public Iterator<T> iterator() {
        try {
            return getResultsAsList().iterator();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public final Query<T> filter(final Predicate<? super T> predicate) {
        requireNonNull(predicate);
        return new InternalQuery<T>() {

            /** {@inheritDoc} */
            @Override
            Future<Void> streamResults(final Callback<T> callback, long limit) {
                return Query.this.streamResults(new Callback<T>() {
                    public void start() throws Exception {
                        callback.start();
                    }

                    public void stop() throws Exception {
                        callback.stop();
                    }

                    @Override
                    public void accept(T t) throws Exception {
                        if (predicate.test(t)) {
                            callback.accept(t);
                        }
                    }
                }, limit);
            }
        };
    }

    /**
     * Returns the result as a list.
     * 
     * @return the result as a list
     */
    public final List<T> getResultsAsList() throws Exception {
        final ArrayList<T> result = new ArrayList<>();
        ESupplier<T> s = createSupplier();
        for (T t = s.get(); t != null; t = s.get()) {
            result.add(t);
        }
        return result;
    }

    public final <R> Query<R> map(final Function<? super T, ? extends R> mapper) {
        requireNonNull(mapper);
        return new InternalQuery<R>() {

            /** {@inheritDoc} */
            @Override
            Future<Void> streamResults(final Callback<R> callback, long limit) {
                return Query.this.streamResults(new Callback<T>() {
                    public void start() throws Exception {
                        callback.start();
                    }

                    public void stop() throws Exception {
                        callback.stop();
                    }

                    @Override
                    public void accept(T t) throws Exception {
                        callback.accept(mapper.apply(t));
                    }
                }, limit);
            }
        };
    }

    protected abstract ESupplier<T> createSupplier() throws Exception;

    /**
     * Streams the result to the callback.
     * 
     * @param callback
     *            the callback to stream the result to
     * @return a future that returns when the query has finished
     */
    Future<Void> streamResults(final Callback<T> callback, final long limit) {
        requireNonNull(callback);
        Callable<Void> c = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                ESupplier<T> s = createSupplier();
                callback.start();
                try {
                    for (T t = s.get(); t != null; t = s.get()) {
                        callback.accept(t);
                    }
                } finally {
                    callback.stop();
                }
                return null;
            }
        };
        FutureTask<Void> task = new FutureTask<>(c);
        submit(task);
        return task;
    }

    public final Future<Void> streamResults(final OutputStream stream, final OutputStreamSink<T> sink, long limit) {
        requireNonNull(stream);
        requireNonNull(sink);
        return streamResults(new Callback<T>() {
            public void start() throws Exception {
                sink.header(stream);
            }

            public void stop() throws Exception {
                sink.footer(stream);
            }

            @Override
            public void accept(T t) throws Exception {
                sink.process(stream, t);
            }
        }, limit);
    }

    public final Future<Void> streamResults(final OutputStream stream, final OutputStreamSink<T> sink) {
        return streamResults(stream, sink, Long.MAX_VALUE);
    }

    /** {@inheritDoc} */
    protected void submit(Runnable runnable) {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("QueryThread[ + " + toString() + "]");
        t.start();
    }

    static abstract class InternalQuery<T> extends Query<T> {
        protected ESupplier<T> createSupplier() {
            throw new Error();// Should never been classed
        }

        protected void submit(Runnable runnable) {
            throw new Error();// Should never been classed
        }
    }

    abstract static class Callback<T> extends EBlock<T> {
        abstract void start() throws Exception;

        abstract void stop() throws Exception;
    }
}
