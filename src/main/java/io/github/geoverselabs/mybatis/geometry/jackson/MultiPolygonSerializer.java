package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.MultiPolygon;

import java.io.IOException;

/**
 * Jackson serializer for JTS MultiPolygon to GeoJSON format.
 *
 * <p>Rings follow the RFC 7946 right-hand rule (exterior rings counter-clockwise, holes
 * clockwise); empty member polygons are skipped and an empty MultiPolygon is written with
 * {@code "coordinates": []}.</p>
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiPolygon",
 *   "coordinates": [
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring
 *       [[lon1, lat1], ...]                               // interior rings (holes)
 *     ],
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]]
 *     ]
 *   ]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonSerialize(using = MultiPolygonSerializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time; {@link #MultiPolygonSerializer(GeoJsonOptions)}
 * fixes the options. {@link GeoJsonPrecision} on a property overrides the coordinate precision for
 * that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiPolygonSerializer.class)
 * @JsonDeserialize(using = MultiPolygonDeserializer.class)
 * private MultiPolygon areas;
 * }</pre>
 */
public class MultiPolygonSerializer extends GeoJsonGeometrySerializer<MultiPolygon> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public MultiPolygonSerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public MultiPolygonSerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private MultiPolygonSerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(MultiPolygon.class, options, precisionOverride);
    }

    @Override
    MultiPolygonSerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new MultiPolygonSerializer(options, precisionOverride);
    }

    /**
     * Write {@code multiPolygon} as a GeoJSON object (JSON null for null).
     *
     * @param multiPolygon the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(MultiPolygon multiPolygon, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        super.serialize(multiPolygon, gen, provider);
    }
}
