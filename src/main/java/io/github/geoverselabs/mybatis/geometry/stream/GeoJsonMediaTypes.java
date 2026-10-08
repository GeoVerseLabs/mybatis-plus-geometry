package io.github.geoverselabs.mybatis.geometry.stream;

/**
 * Media types of the formats written by {@link GeoJsonStreams}.
 *
 * <p>Plain {@code String} constants, so they can be used in annotations such as Spring's
 * {@code @GetMapping(produces = GeoJsonMediaTypes.GEO_JSON)} without this library depending on
 * Spring.</p>
 */
public final class GeoJsonMediaTypes {

    /**
     * GeoJSON (RFC 7946, section 12): a single GeoJSON object such as a {@code FeatureCollection}.
     * Use with {@link GeoJsonStreams#writeFeatureCollection}.
     */
    public static final String GEO_JSON = "application/geo+json";

    /**
     * GeoJSON text sequence (RFC 8142): one GeoJSON object per RFC 7464 record.
     * Use with {@link GeoJsonStreams#writeFeatureSequence}.
     */
    public static final String GEO_JSON_SEQ = "application/geo+json-seq";

    /**
     * JSON text sequence (RFC 7464): one JSON text per record, each record being
     * {@code 0x1E} (record separator), the JSON text and {@code 0x0A} (line feed).
     * Use with {@link GeoJsonStreams#writeSequence}.
     */
    public static final String JSON_SEQ = "application/json-seq";

    private GeoJsonMediaTypes() {
        // constants only
    }
}
