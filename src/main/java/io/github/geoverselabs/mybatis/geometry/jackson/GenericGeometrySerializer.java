package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;

/**
 * Jackson serializer for any JTS Geometry subtype to GeoJSON format.
 *
 * <p>Dispatches on the runtime type (Multi* types are checked before GeometryCollection, since
 * they are subclasses of it) and writes the GeoJSON directly, without looking up other
 * serializers. It therefore works through {@code @JsonSerialize(using = GenericGeometrySerializer.class)}
 * on a plain {@code ObjectMapper} without {@link GeometryJacksonModule}.</p>
 *
 * <p>Instances created with the no-arg constructor use {@link GeoJsonOptions#getGlobal()} at call
 * time; {@link #GenericGeometrySerializer(GeoJsonOptions)} fixes the options.
 * {@link GeoJsonPrecision} on a property overrides the coordinate precision for that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = GenericGeometrySerializer.class)
 * @JsonDeserialize(using = GenericGeometryDeserializer.class)
 * private Geometry geometry;
 * }</pre>
 */
public class GenericGeometrySerializer extends GeoJsonGeometrySerializer<Geometry> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public GenericGeometrySerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public GenericGeometrySerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private GenericGeometrySerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(Geometry.class, options, precisionOverride);
    }

    @Override
    GenericGeometrySerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new GenericGeometrySerializer(options, precisionOverride);
    }

    /**
     * Write {@code geometry} as a GeoJSON object (JSON null for null).
     *
     * @param geometry the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(Geometry geometry, JsonGenerator gen, SerializerProvider provider) throws IOException {
        super.serialize(geometry, gen, provider);
    }
}
