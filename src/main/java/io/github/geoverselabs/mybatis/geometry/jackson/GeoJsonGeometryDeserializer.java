package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;

/**
 * Common implementation of the GeoJSON geometry deserializers (internal API): a thin wrapper
 * around {@link GeoJsonGeometryReader}.
 *
 * @param <T> the geometry type
 */
abstract class GeoJsonGeometryDeserializer<T extends Geometry> extends StdDeserializer<T> {

    private static final long serialVersionUID = 1L;

    private final Class<T> geometryType;

    /** Fixed options, or null to use {@link GeoJsonOptions#getGlobal()} at call time. */
    private final GeoJsonOptions options;

    /** Range-validation override applied to the global options, or null. */
    private final Boolean rangeValidationOverride;

    GeoJsonGeometryDeserializer(Class<T> geometryType, GeoJsonOptions options, Boolean rangeValidationOverride) {
        super(geometryType);
        this.geometryType = geometryType;
        this.options = options;
        this.rangeValidationOverride = rangeValidationOverride;
    }

    /**
     * The options to read with: the fixed options, or the current global options (with the
     * range-validation override applied, if any).
     *
     * @return the effective options, never null
     */
    GeoJsonOptions effectiveOptions() {
        if (options != null) {
            return options;
        }
        GeoJsonOptions global = GeoJsonOptions.getGlobal();
        if (rangeValidationOverride == null
                || rangeValidationOverride == global.coordinateRangeValidation()) {
            return global;
        }
        return global.withCoordinateRangeValidation(rangeValidationOverride);
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return GeoJsonGeometryReader.read(parser, geometryType, effectiveOptions());
    }

    /**
     * Read a geometry carrying a polymorphic type id. When the type id is an object property
     * (for example {@code "@class"} written by {@code activateDefaultTyping(..., As.PROPERTY)}),
     * the GeoJSON {@code "type"} member already identifies the geometry and the reader skips
     * unknown members, so the object is read directly; this also works when the deserializer is
     * used through {@code @JsonDeserialize(using = ...)} without {@link GeometryJacksonModule}.
     * Other inclusion styles use Jackson's default handling.
     */
    @Override
    public Object deserializeWithType(JsonParser parser, DeserializationContext ctx,
                                      TypeDeserializer typeDeserializer) throws IOException {
        JsonTypeInfo.As inclusion = typeDeserializer.getTypeInclusion();
        JsonToken token = parser.currentToken();
        if ((inclusion == JsonTypeInfo.As.PROPERTY || inclusion == JsonTypeInfo.As.EXISTING_PROPERTY)
                && (token == JsonToken.START_OBJECT || token == JsonToken.FIELD_NAME)) {
            return deserialize(parser, ctx);
        }
        return super.deserializeWithType(parser, ctx, typeDeserializer);
    }
}
