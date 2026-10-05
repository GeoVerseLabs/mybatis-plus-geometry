package io.github.geoverselabs.mybatis.geometry.stream;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Function;

/**
 * Streaming writers for large JSON and GeoJSON responses.
 *
 * <p>Every method consumes its items lazily, one at a time, through a single
 * {@link Iterable#iterator()} call, so results can be streamed straight from a MyBatis
 * {@code Cursor<T>} (which is an {@code Iterable}), from a {@code java.util.stream.Stream} passed as
 * {@code stream::iterator}, or from any other lazy source, without loading them into memory. Items
 * are serialised through the supplied {@link ObjectMapper}, so the serializers it has registered
 * apply, in particular the {@code GeometryJacksonModule} and its GeoJSON options (for example the
 * coordinate precision). Each item is serialised independently and the output reaches the stream in
 * chunks of the JSON generator's buffer size (a few kilobytes), so memory use does not grow with the
 * number of items.</p>
 *
 * <p>All methods:</p>
 * <ul>
 *   <li>return the number of items written;</li>
 *   <li>write UTF-8 encoded JSON;</li>
 *   <li>never close the {@code OutputStream} (nor the {@code Iterable}: close a {@code Cursor}
 *       yourself), and flush it once, after the last byte has been written; they do not flush per
 *       item;</li>
 *   <li>check that the mapper writes JSON before consuming any item;</li>
 *   <li>propagate exceptions thrown by the iterable or the functions unchanged. When writing fails,
 *       output still buffered by the JSON generator is discarded instead of being written, and the
 *       stream is not flushed, so an HTTP response that has not been committed yet can still report
 *       the error and a truncated document is never completed with closing brackets.</li>
 * </ul>
 *
 * <p>Example with Spring MVC (the MyBatis {@code SqlSession} must stay open, for example inside a
 * transaction, while the cursor is read):</p>
 * <pre>{@code
 * @GetMapping(value = "/shops", produces = GeoJsonMediaTypes.GEO_JSON)
 * public StreamingResponseBody shops() {
 *     return out -> transactionTemplate.executeWithoutResult(status -> {
 *         try (Cursor<Shop> shops = shopMapper.streamAll()) {
 *             GeoJsonStreams.writeFeatureCollection(objectMapper, shops,
 *                 Shop::getId, Shop::getLocation, shop -> Map.of("name", shop.getName()), out);
 *         } catch (IOException e) {
 *             throw new UncheckedIOException(e);
 *         }
 *     });
 * }
 * }</pre>
 *
 * <p>This class has no Spring dependency; it only needs Jackson databind. It is thread-safe as long
 * as the {@code ObjectMapper} is (that is, once it is no longer being configured).</p>
 *
 * @see GeoJsonMediaTypes
 */
public final class GeoJsonStreams {

    /** RFC 7464 record separator, written before each JSON text of a sequence. */
    private static final char RECORD_SEPARATOR = '\u001E';

    /** RFC 7464 line feed, written after each JSON text of a sequence. */
    private static final char LINE_FEED = '\n';

    private static final SerializableString TYPE = new SerializedString("type");
    private static final SerializableString ID = new SerializedString("id");
    private static final SerializableString GEOMETRY = new SerializedString("geometry");
    private static final SerializableString PROPERTIES = new SerializedString("properties");
    private static final SerializableString FEATURES = new SerializedString("features");
    private static final SerializableString FEATURE = new SerializedString("Feature");
    private static final SerializableString FEATURE_COLLECTION = new SerializedString("FeatureCollection");

    private GeoJsonStreams() {
        // static methods only
    }

    /**
     * Write the items as one JSON array ({@code [item, item, ...]}), serialising each item as
     * {@link ObjectMapper#writeValue} would (a {@code null} item is written as {@code null}).
     * Indentation follows the mapper's {@code SerializationFeature.INDENT_OUTPUT} setting.
     *
     * @param mapper the mapper used to serialise each item
     * @param items  the items, iterated once
     * @param out    the stream to write to; flushed, but not closed
     * @return the number of items written
     * @throws IOException              if writing or serialising fails
     * @throws IllegalArgumentException if the mapper does not write JSON (for example a YAML mapper)
     */
    public static long writeArray(ObjectMapper mapper, Iterable<?> items, OutputStream out) throws IOException {
        requireArguments(mapper, items, out);
        ObjectWriter writer = mapper.writer().without(SerializationFeature.FLUSH_AFTER_WRITE_VALUE);
        ItemWriter<Object> values = (gen, item) -> writer.writeValue(gen, item);
        return write(writer, out, false, gen -> writeArrayItems(gen, items, values));
    }

    /**
     * Write the items as a JSON text sequence (RFC 7464, media type
     * {@link GeoJsonMediaTypes#JSON_SEQ}): for each item, the record separator {@code 0x1E}, the
     * item serialised as compact JSON (as {@link ObjectMapper#writeValue} would, but never indented)
     * and a line feed {@code 0x0A}. No items produce no bytes.
     *
     * <p>Use {@link #writeFeatureSequence} to write a GeoJSON text sequence (RFC 8142) of
     * Features; geometries passed here produce a valid GeoJSON text sequence too.</p>
     *
     * @param mapper the mapper used to serialise each item
     * @param items  the items, iterated once
     * @param out    the stream to write to; flushed, but not closed
     * @return the number of records written
     * @throws IOException              if writing or serialising fails
     * @throws IllegalArgumentException if the mapper does not write JSON (for example a YAML mapper)
     */
    public static long writeSequence(ObjectMapper mapper, Iterable<?> items, OutputStream out) throws IOException {
        requireArguments(mapper, items, out);
        ObjectWriter writer = mapper.writer()
            .without(SerializationFeature.FLUSH_AFTER_WRITE_VALUE, SerializationFeature.INDENT_OUTPUT);
        ItemWriter<Object> values = (gen, item) -> writer.writeValue(gen, item);
        return write(writer, out, true, gen -> writeRecords(gen, items, values));
    }

    /**
     * Write the items as a GeoJSON {@code FeatureCollection} (RFC 7946, media type
     * {@link GeoJsonMediaTypes#GEO_JSON}) of Features without an {@code id} member.
     *
     * <p>Equivalent to {@link #writeFeatureCollection(ObjectMapper, Iterable, Function, Function,
     * Function, OutputStream)} with an {@code id} function that always returns {@code null}.</p>
     *
     * @param mapper     the mapper used to serialise geometries and properties
     * @param items      the items, iterated once
     * @param geometry   extracts the Feature geometry from an item; {@code null} results are written
     *                   as {@code "geometry": null}
     * @param properties extracts the Feature properties from an item; the result should serialise to
     *                   a JSON object (a {@code Map}, a bean, a {@code JsonNode}, ...) and
     *                   {@code null} results are written as {@code "properties": null}
     * @param out        the stream to write to; flushed, but not closed
     * @param <T>        the item type
     * @return the number of Features written
     * @throws IOException              if writing or serialising fails, including when the mapper has
     *                                  no GeoJSON serializer for a geometry
     * @throws IllegalArgumentException if the mapper does not write JSON (for example a YAML mapper)
     */
    public static <T> long writeFeatureCollection(ObjectMapper mapper, Iterable<T> items,
                                                  Function<? super T, ? extends Geometry> geometry,
                                                  Function<? super T, ?> properties,
                                                  OutputStream out) throws IOException {
        requireFeatureArguments(mapper, items, geometry, properties, out);
        return featureCollection(mapper, items, new FeatureWriter<>(mapper, null, geometry, properties), out);
    }

    /**
     * Write the items as a GeoJSON {@code FeatureCollection} (RFC 7946, media type
     * {@link GeoJsonMediaTypes#GEO_JSON}):
     * <pre>{@code
     * {"type":"FeatureCollection","features":[
     *   {"type":"Feature","id":...,"geometry":{...},"properties":{...}}, ...]}
     * }</pre>
     *
     * <p>The Feature members are written by this method, so the mapper's configuration (inclusion
     * rules, naming strategies, ...) cannot drop or rename them; it applies to the member values. The
     * {@code id}, {@code geometry} and {@code properties} values are serialised without a type id of
     * their own, so the members stay plain GeoJSON even when the mapper has default typing enabled.
     * Each function is called exactly once per item, in the order id, geometry, properties, and
     * receives {@code null} items as they are. Indentation follows the mapper's
     * {@code SerializationFeature.INDENT_OUTPUT} setting.</p>
     *
     * @param mapper     the mapper used to serialise ids, geometries and properties
     * @param items      the items, iterated once
     * @param id         extracts the Feature id from an item; RFC 7946 expects it to serialise to a
     *                   JSON string or number, and the {@code id} member is omitted when the result
     *                   is {@code null}
     * @param geometry   extracts the Feature geometry from an item; {@code null} results are written
     *                   as {@code "geometry": null}
     * @param properties extracts the Feature properties from an item; the result should serialise to
     *                   a JSON object (a {@code Map}, a bean, a {@code JsonNode}, ...) and
     *                   {@code null} results are written as {@code "properties": null}
     * @param out        the stream to write to; flushed, but not closed
     * @param <T>        the item type
     * @return the number of Features written
     * @throws IOException              if writing or serialising fails, including when the mapper has
     *                                  no GeoJSON serializer for a geometry
     * @throws IllegalArgumentException if the mapper does not write JSON (for example a YAML mapper)
     */
    public static <T> long writeFeatureCollection(ObjectMapper mapper, Iterable<T> items,
                                                  Function<? super T, ?> id,
                                                  Function<? super T, ? extends Geometry> geometry,
                                                  Function<? super T, ?> properties,
                                                  OutputStream out) throws IOException {
        requireFeatureArguments(mapper, items, geometry, properties, out);
        Objects.requireNonNull(id, "id");
        return featureCollection(mapper, items, new FeatureWriter<>(mapper, id, geometry, properties), out);
    }

    /**
     * Write the items as a GeoJSON text sequence (RFC 8142, media type
     * {@link GeoJsonMediaTypes#GEO_JSON_SEQ}) of Features without an {@code id} member: one RFC 7464
     * record ({@code 0x1E}, compact Feature JSON, {@code 0x0A}) per item. No items produce no bytes.
     *
     * <p>Equivalent to {@link #writeFeatureSequence(ObjectMapper, Iterable, Function, Function,
     * Function, OutputStream)} with an {@code id} function that always returns {@code null}.</p>
     *
     * @param mapper     the mapper used to serialise geometries and properties
     * @param items      the items, iterated once
     * @param geometry   extracts the Feature geometry from an item; {@code null} results are written
     *                   as {@code "geometry": null}
     * @param properties extracts the Feature properties from an item; the result should serialise to
     *                   a JSON object and {@code null} results are written as
     *                   {@code "properties": null}
     * @param out        the stream to write to; flushed, but not closed
     * @param <T>        the item type
     * @return the number of Features (records) written
     * @throws IOException              if writing or serialising fails, including when the mapper has
     *                                  no GeoJSON serializer for a geometry
     * @throws IllegalArgumentException if the mapper does not write JSON (for example a YAML mapper)
     */
    public static <T> long writeFeatureSequence(ObjectMapper mapper, Iterable<T> items,
                                                Function<? super T, ? extends Geometry> geometry,
                                                Function<? super T, ?> properties,
                                                OutputStream out) throws IOException {
        requireFeatureArguments(mapper, items, geometry, properties, out);
        return featureSequence(mapper, items, new FeatureWriter<>(mapper, null, geometry, properties), out);
    }

    /**
     * Write the items as a GeoJSON text sequence (RFC 8142, media type
     * {@link GeoJsonMediaTypes#GEO_JSON_SEQ}): one RFC 7464 record ({@code 0x1E}, compact Feature
     * JSON, {@code 0x0A}) per item, each Feature written as by
     * {@link #writeFeatureCollection(ObjectMapper, Iterable, Function, Function, Function, OutputStream)}
     * but never indented. No items produce no bytes.
     *
     * @param mapper     the mapper used to serialise ids, geometries and properties
     * @param items      the items, iterated once
     * @param id         extracts the Feature id from an item; RFC 7946 expects it to serialise to a
     *                   JSON string or number, and the {@code id} member is omitted when the result
     *                   is {@code null}
     * @param geometry   extracts the Feature geometry from an item; {@code null} results are written
     *                   as {@code "geometry": null}
     * @param properties extracts the Feature properties from an item; the result should serialise to
     *                   a JSON object and {@code null} results are written as
     *                   {@code "properties": null}
     * @param out        the stream to write to; flushed, but not closed
     * @param <T>        the item type
     * @return the number of Features (records) written
     * @throws IOException              if writing or serialising fails, including when the mapper has
     *                                  no GeoJSON serializer for a geometry
     * @throws IllegalArgumentException if the mapper does not write JSON (for example a YAML mapper)
     */
    public static <T> long writeFeatureSequence(ObjectMapper mapper, Iterable<T> items,
                                                Function<? super T, ?> id,
                                                Function<? super T, ? extends Geometry> geometry,
                                                Function<? super T, ?> properties,
                                                OutputStream out) throws IOException {
        requireFeatureArguments(mapper, items, geometry, properties, out);
        Objects.requireNonNull(id, "id");
        return featureSequence(mapper, items, new FeatureWriter<>(mapper, id, geometry, properties), out);
    }

    // ------------------------------------------------------------------------------------------
    // Framing

    private static <T> long featureCollection(ObjectMapper mapper, Iterable<T> items,
                                              ItemWriter<? super T> features, OutputStream out) throws IOException {
        return write(mapper.writer(), out, false, gen -> {
            gen.writeStartObject();
            gen.writeFieldName(TYPE);
            gen.writeString(FEATURE_COLLECTION);
            gen.writeFieldName(FEATURES);
            long count = writeArrayItems(gen, items, features);
            gen.writeEndObject();
            return count;
        });
    }

    private static <T> long featureSequence(ObjectMapper mapper, Iterable<T> items,
                                            ItemWriter<? super T> features, OutputStream out) throws IOException {
        ObjectWriter writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        return write(writer, out, true, gen -> writeRecords(gen, items, features));
    }

    private static <T> long writeArrayItems(JsonGenerator gen, Iterable<T> items, ItemWriter<? super T> writer)
            throws IOException {
        gen.writeStartArray();
        long count = 0;
        for (T item : items) {
            writer.write(gen, item);
            count++;
        }
        gen.writeEndArray();
        return count;
    }

    private static <T> long writeRecords(JsonGenerator gen, Iterable<T> items, ItemWriter<? super T> writer)
            throws IOException {
        long count = 0;
        for (T item : items) {
            gen.writeRaw(RECORD_SEPARATOR);
            writer.write(gen, item);
            gen.writeRaw(LINE_FEED);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------------------------------
    // Generator lifecycle

    /**
     * Writes a whole document onto the generator.
     */
    @FunctionalInterface
    private interface Document {

        /**
         * @return the number of items written
         */
        long write(JsonGenerator gen) throws IOException;
    }

    /**
     * Create a generator for {@code out}, write the document, then complete it and flush
     * {@code out} once. On failure the rest of the document is discarded instead (see
     * {@link CallerStream}) and the failure is rethrown unchanged.
     */
    private static long write(ObjectWriter writer, OutputStream out, boolean textSequence, Document document)
            throws IOException {
        requireJson(writer.getFactory());
        CallerStream target = new CallerStream(out);
        JsonGenerator gen = writer.createGenerator(target, JsonEncoding.UTF8);
        if (textSequence) {
            // Records are RS + compact JSON text + LF: no pretty printing (it adds line feeds) and
            // no separator between root values (a space by default).
            gen.setPrettyPrinter(null);
            gen.setRootValueSeparator(null);
        }
        long count;
        try {
            count = document.write(gen);
            gen.close(); // writes the buffered output; CallerStream neither closes nor flushes out
        } catch (Throwable failure) {
            // Release the generator without emitting the rest of the incomplete document: closing it
            // would otherwise write the buffered output and close the open arrays and objects.
            target.discard();
            try {
                gen.close();
            } catch (IOException | RuntimeException suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        }
        out.flush();
        return count;
    }

    private static void requireJson(JsonFactory factory) {
        String format = factory.getFormatName();
        // Custom JsonFactory subclasses may report null; every non-JSON factory reports its format.
        if (format != null && !JsonFactory.FORMAT_NAME_JSON.equals(format)) {
            throw new IllegalArgumentException("ObjectMapper must write JSON, but its factory writes " + format);
        }
    }

    /**
     * The caller's stream as seen by the generator: forwards writes, but never closes or flushes
     * the target (the caller owns it, and it is flushed once after a successful write), and drops all
     * writes once {@link #discard() discarded}.
     */
    private static final class CallerStream extends OutputStream {

        private final OutputStream target;

        private boolean discarded;

        CallerStream(OutputStream target) {
            this.target = target;
        }

        void discard() {
            discarded = true;
        }

        @Override
        public void write(int b) throws IOException {
            if (!discarded) {
                target.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (!discarded) {
                target.write(b, off, len);
            }
        }

        @Override
        public void flush() {
            // the target is flushed once, after the whole document has been written
        }

        @Override
        public void close() {
            // the caller owns the target
        }
    }

    // ------------------------------------------------------------------------------------------
    // Items

    /**
     * Writes one item onto the generator.
     *
     * @param <T> the item type
     */
    @FunctionalInterface
    private interface ItemWriter<T> {

        void write(JsonGenerator gen, T item) throws IOException;
    }

    /**
     * Writes an item as a GeoJSON Feature.
     *
     * @param <T> the item type
     */
    private static final class FeatureWriter<T> implements ItemWriter<T> {

        private final ObjectMapper mapper;

        /** Null when Features have no id. */
        private final Function<? super T, ?> id;

        private final Function<? super T, ? extends Geometry> geometry;

        private final Function<? super T, ?> properties;

        FeatureWriter(ObjectMapper mapper, Function<? super T, ?> id,
                      Function<? super T, ? extends Geometry> geometry, Function<? super T, ?> properties) {
            this.mapper = mapper;
            this.id = id;
            this.geometry = geometry;
            this.properties = properties;
        }

        @Override
        public void write(JsonGenerator gen, T item) throws IOException {
            Object featureId = id == null ? null : id.apply(item);
            Geometry featureGeometry = geometry.apply(item);
            Object featureProperties = properties.apply(item);

            // A fresh provider per Feature, as ObjectMapper.writeValue uses one per call: per-document
            // state such as @JsonIdentityInfo ids must not accumulate over an unbounded stream.
            SerializerProvider provider = mapper.getSerializerProviderInstance();
            gen.writeStartObject();
            gen.writeFieldName(TYPE);
            gen.writeString(FEATURE);
            if (featureId != null) {
                gen.writeFieldName(ID);
                writeUntyped(provider, gen, featureId);
            }
            gen.writeFieldName(GEOMETRY);
            if (featureGeometry == null) {
                gen.writeNull();
            } else {
                writeGeometry(provider, gen, featureGeometry);
            }
            gen.writeFieldName(PROPERTIES);
            if (featureProperties == null) {
                gen.writeNull();
            } else {
                writeUntyped(provider, gen, featureProperties);
            }
            gen.writeEndObject();
        }

        /**
         * Serialise a Feature member value without a type id: GeoJSON members must not be wrapped
         * or extended with type information, even when the mapper uses default typing.
         */
        private static void writeUntyped(SerializerProvider provider, JsonGenerator gen, Object value)
                throws IOException {
            provider.findValueSerializer(value.getClass(), null).serialize(value, gen, provider);
        }

        private static void writeGeometry(SerializerProvider provider, JsonGenerator gen, Geometry geometry)
                throws IOException {
            JsonSerializer<Object> serializer = provider.findValueSerializer(geometry.getClass(), null);
            if (serializer instanceof BeanSerializerBase) {
                // Without a GeoJSON serializer Jackson introspects the JTS getters and recurses until
                // the stack overflows (Point.getEnvelope() returns a Point), after writing kilobytes
                // of output; fail fast with an actionable message instead.
                throw InvalidDefinitionException.from(gen,
                    "No GeoJSON serializer registered for " + geometry.getClass().getName()
                        + "; register GeometryJacksonModule with the ObjectMapper",
                    provider.constructType(geometry.getClass()));
            }
            serializer.serialize(geometry, gen, provider);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Arguments

    private static void requireArguments(ObjectMapper mapper, Iterable<?> items, OutputStream out) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(out, "out");
    }

    private static void requireFeatureArguments(ObjectMapper mapper, Iterable<?> items, Function<?, ?> geometry,
                                                Function<?, ?> properties, OutputStream out) {
        requireArguments(mapper, items, out);
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(properties, "properties");
    }
}
