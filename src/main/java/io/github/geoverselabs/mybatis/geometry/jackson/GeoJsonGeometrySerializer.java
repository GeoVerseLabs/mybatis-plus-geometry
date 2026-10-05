package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;
import java.util.Objects;

/**
 * Common implementation of the GeoJSON geometry serializers (internal API).
 *
 * <p>Writes through {@link GeoJsonGeometryWriter} without consulting the
 * {@link SerializerProvider} for nested values, so every serializer also works when used alone
 * through {@code @JsonSerialize(using = ...)} on an {@code ObjectMapper} without
 * {@link GeometryJacksonModule}. Supports polymorphic type handling
 * ({@link #serializeWithType}) and the per-property {@link GeoJsonPrecision} annotation.</p>
 *
 * @param <T> the geometry type
 */
abstract class GeoJsonGeometrySerializer<T extends Geometry> extends StdSerializer<T>
        implements ContextualSerializer {

    private static final long serialVersionUID = 1L;

    /** Fixed options, or null to use {@link GeoJsonOptions#getGlobal()} at call time. */
    private final GeoJsonOptions options;

    /** Precision from {@link GeoJsonPrecision} (may be {@link GeoJsonPrecision#FULL}), or null. */
    private final Integer precisionOverride;

    GeoJsonGeometrySerializer(Class<T> type, GeoJsonOptions options, Integer precisionOverride) {
        super(type);
        this.options = options;
        this.precisionOverride = precisionOverride;
    }

    /**
     * Create a copy of this serializer with other settings; used for contextualisation.
     *
     * @param options           fixed options, or null for the global options
     * @param precisionOverride precision override, or null
     * @return the new serializer
     */
    abstract GeoJsonGeometrySerializer<T> withSettings(GeoJsonOptions options, Integer precisionOverride);

    /**
     * The options this serializer was created with.
     *
     * @return the fixed options, or null when the global options are used at call time
     */
    GeoJsonOptions options() {
        return options;
    }

    /**
     * The precision override from {@link GeoJsonPrecision}.
     *
     * @return the override, {@link GeoJsonPrecision#FULL}, or null when not overridden
     */
    Integer precisionOverride() {
        return precisionOverride;
    }

    /**
     * The precision to write with.
     *
     * @return decimal places, or null for full precision
     */
    Integer effectivePrecision() {
        if (precisionOverride != null) {
            return precisionOverride == GeoJsonPrecision.FULL ? null : precisionOverride;
        }
        return (options != null ? options : GeoJsonOptions.getGlobal()).coordinatePrecision();
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        GeoJsonGeometryWriter.writeObject(value, gen, effectivePrecision());
    }

    /**
     * Write the geometry with a type id (for example with
     * {@code ObjectMapper.activateDefaultTyping(...)}): the type id is written by
     * {@code typeSer} around the GeoJSON members.
     */
    @Override
    public void serializeWithType(T value, JsonGenerator gen, SerializerProvider provider,
                                  TypeSerializer typeSer) throws IOException {
        WritableTypeId typeId = typeSer.writeTypePrefix(gen, typeSer.typeId(value, JsonToken.START_OBJECT));
        GeoJsonGeometryWriter.writeMembers(value, gen, effectivePrecision());
        typeSer.writeTypeSuffix(gen, typeId);
    }

    /**
     * Apply {@link GeoJsonPrecision} found on the property (or on the container property whose
     * content this serializer writes).
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
            throws JsonMappingException {
        if (property == null) {
            return this;
        }
        GeoJsonPrecision annotation = property.getAnnotation(GeoJsonPrecision.class);
        Integer requested = annotation == null ? null : annotation.value();
        if (requested != null && requested != GeoJsonPrecision.FULL
                && (requested < 0 || requested > GeoJsonOptions.MAX_PRECISION)) {
            return provider.reportBadDefinition(property.getType(), String.format(
                "@GeoJsonPrecision on property '%s' must be between 0 and %d or GeoJsonPrecision.FULL, got %d",
                property.getName(), GeoJsonOptions.MAX_PRECISION, requested));
        }
        // An instance contextualised for another property never leaks its override.
        if (Objects.equals(precisionOverride, requested)) {
            return this;
        }
        return withSettings(options, requested);
    }
}
