package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;

/**
 * Jackson serializer for JTS Polygon to GeoJSON format.
 *
 * <p>Rings are written following the RFC 7946 right-hand rule (exterior ring counter-clockwise,
 * holes clockwise) whatever their stored orientation. An empty polygon is written with
 * {@code "coordinates": []}.</p>
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "Polygon",
 *   "coordinates": [
 *     [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring (CCW)
 *     [[lon1, lat1], ...]                               // interior rings (CW)
 *   ]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonSerialize(using = PolygonSerializer.class)}) use {@link GeoJsonOptions#getGlobal()}
 * at call time; {@link #PolygonSerializer(GeoJsonOptions)} fixes the options.
 * {@link GeoJsonPrecision} on a property overrides the coordinate precision for that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = PolygonSerializer.class)
 * @JsonDeserialize(using = PolygonDeserializer.class)
 * private Polygon boundary;
 * }</pre>
 */
public class PolygonSerializer extends GeoJsonGeometrySerializer<Polygon> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public PolygonSerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public PolygonSerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private PolygonSerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(Polygon.class, options, precisionOverride);
    }

    @Override
    PolygonSerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new PolygonSerializer(options, precisionOverride);
    }

    /**
     * Write {@code polygon} as a GeoJSON object (JSON null for null).
     *
     * @param polygon the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(Polygon polygon, JsonGenerator gen, SerializerProvider provider) throws IOException {
        super.serialize(polygon, gen, provider);
    }
}
