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

import static java.util.Objects.requireNonNull;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;

import dk.dma.ais.message.AisMessage;
import dk.dma.ais.packet.AisPacket;
import dk.dma.commons.util.io.OutputStreamSink;

/**
 * 
 * @author Kasper Nielsen
 */
public class ReflectionBasedTableOutputStreamSink extends OutputStreamSink<AisPacket> {

    /** Just a placeholder for NULL in the methods map. */
    private static final Method NULL = ReflectionBasedTableOutputStreamSink.class.getDeclaredMethods()[0];

    private final String[] columns;

    private final AtomicLong counter = new AtomicLong();

    /** A cache of methods. */
    private final ConcurrentHashMap<Map.Entry<Class<?>, String>, Method> methods = new ConcurrentHashMap<>();

    /** A date formatter. */
    private final DateTimeFormatter fmt = ISODateTimeFormat.dateTime();

    private final DateTimeFormatter utc = new DateTimeFormatterBuilder().appendDayOfMonth(2).appendLiteral('-')
            .appendMonthOfYear(2).appendLiteral('-').appendYear(2, 2).appendLiteral(' ').appendHourOfDay(2)
            .appendLiteral(':').appendMinuteOfHour(2).appendLiteral(':').appendSecondOfMinute(2).toFormatter();

    final byte[] separator;

    final boolean writeHeader;

    ReflectionBasedTableOutputStreamSink(String format, boolean writeHeader, String separator) {
        columns = format.split(";");
        this.writeHeader = writeHeader;
        this.separator = requireNonNull(separator).getBytes(StandardCharsets.US_ASCII);
    }

    /** {@inheritDoc} */
    @Override
    public void process(OutputStream stream, AisPacket message) throws IOException {
        AisMessage m = message.tryGetAisMessage();
        if (m != null) {
            long n = counter.incrementAndGet();
            for (int i = 0; i < columns.length; i++) {
                String c = columns[i];
                if (c.equals("n")) {
                    stream.write(Long.toString(n).getBytes(StandardCharsets.US_ASCII));
                } else if (c.equals("timestamp")) {
                    stream.write(Long.toString(message.getBestTimestamp()).getBytes(StandardCharsets.US_ASCII));
                } else if (c.equals("utc")) {
                    DateTime dateTime = new DateTime(new Date(message.getBestTimestamp()));
                    String str = utc.print(dateTime);
                    stream.write(str.getBytes(StandardCharsets.US_ASCII));
                } else if (c.equals("time")) {
                    DateTime dateTime = new DateTime(new Date(message.getBestTimestamp()));
                    String str = fmt.print(dateTime);
                    stream.write(str.getBytes(StandardCharsets.US_ASCII));
                } else {
                    Method g = findGetter(c, m.getClass());
                    if (g != null) {
                        try {
                            Object o = g.invoke(m);
                            String s = o.toString();
                            stream.write(s.getBytes(StandardCharsets.US_ASCII));
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            throw new IOException(e);
                        }
                    }
                }
                if (i < columns.length - 1) {
                    stream.write(separator);
                }
            }
            stream.write('\n');
        }
    }

    /** {@inheritDoc} */
    @Override
    public void header(OutputStream stream) throws IOException {
        // Writes the name of each column as the header
        if (writeHeader) {
            for (int i = 0; i < columns.length; i++) {
                stream.write(columns[i].getBytes(StandardCharsets.US_ASCII));
                if (i < columns.length - 1) {
                    stream.write(separator);
                }
            }
            stream.write('\n');
        }
    }

    private Method findGetter(String nameOfColumn, Class<?> type) throws IOException {
        Entry<Class<?>, String> key = new SimpleImmutableEntry<Class<?>, String>(type, nameOfColumn);
        Method m = methods.get(key);
        if (m == null) {
            m = NULL;
            BeanInfo info = null;
            try {
                info = Introspector.getBeanInfo(type);
            } catch (IntrospectionException e) {
                throw new IOException(e);
            }
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                if (nameOfColumn.equals(pd.getName())) {
                    m = pd.getReadMethod();
                }
            }
            methods.put(key, m);
        }
        return m == NULL ? null : m;
    }
}
