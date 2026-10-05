package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Jackson Module that registers GeoJSON serializers/deserializers for JTS geometry types.
 *
 * <p>Supported types:</p>
 * <ul>
 *   <li>Point</li>
 *   <li>LineString</li>
 *   <li>Polygon</li>
 *   <li>MultiPoint</li>
 *   <li>MultiLineString</li>
 *   <li>MultiPolygon</li>
 *   <li>GeometryCollection</li>
 *   <li>Geometry (generic, dispatches to specific type)</li>
 * </ul>
 *
 * <p>Output format conforms to RFC 7946 GeoJSON with coordinate order [longitude, latitude].</p>
 *
 * <h2>Reading</h2>
 * <ul>
 *   <li>Input is read in a single streaming pass; object members may come in any order
 *       ({@code "type"} after {@code "coordinates"} is fine) and unknown members such as
 *       {@code bbox}, {@code crs} or polymorphic type ids are skipped.</li>
 *   <li>Positions are arrays of at least two JSON numbers; strings, booleans and nulls are
 *       rejected. A third number is kept as the altitude (Z); further numbers are ignored.</li>
 *   <li>Ordinates must be finite. With {@link GeoJsonOptions#coordinateRangeValidation()} x/y must
 *       also be valid WGS84 longitude/latitude values.</li>
 *   <li>LineStrings need at least 2 positions; polygon rings at least 4 positions and must be
 *       closed. Polygon and MultiPolygon rings are re-oriented to the RFC 7946 right-hand rule.</li>
 *   <li>{@code "coordinates": []} and {@code "geometries": []} produce empty geometries; nested
 *       GeometryCollections are supported.</li>
 *   <li>A deserializer accepts any GeoJSON type whose JTS class is assignable to its target type,
 *       so the GeometryCollection deserializer also accepts MultiPoint, MultiLineString and
 *       MultiPolygon.</li>
 *   <li>With {@code GeometryValidation.FULL} (the default), polygons and multipolygons must be
 *       valid according to OGC rules.</li>
 *   <li>Errors are reported as {@link io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException}
 *       or {@link io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException}, both
 *       {@code JsonMappingException}s carrying the JSON path and location.</li>
 *   <li>Geometries are created by
 *       {@link io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider#getFactory()}
 *       (SRID and coordinate sequence implementation).</li>
 * </ul>
 *
 * <h2>Writing</h2>
 * <ul>
 *   <li>{@code "type"} is written first. Empty geometries are written with
 *       {@code "coordinates": []} ({@code "geometries": []}); empty members of multi-geometries
 *       are skipped.</li>
 *   <li>Rings follow the RFC 7946 right-hand rule; Z is written when present and finite.</li>
 *   <li>{@link GeoJsonOptions#coordinatePrecision()} or {@link GeoJsonPrecision} on a property
 *       rounds ordinates half-up to a number of decimals.</li>
 *   <li>Polymorphic type handling ({@code ObjectMapper.activateDefaultTyping}, as used by Redis
 *       cache serializers) is supported.</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new GeometryJacksonModule());
 * }</pre>
 */
public class GeometryJacksonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    private static final String MODULE_NAME = "GeometryJacksonModule";

    /**
     * Create a GeometryJacksonModule whose serializers and deserializers use
     * {@link GeoJsonOptions#getGlobal()} at call time (by default: full precision, WGS84 range
     * validation, full validation).
     */
    public GeometryJacksonModule() {
        super(MODULE_NAME);
        register(null, null);
    }

    /**
     * Create a GeometryJacksonModule with configurable coordinate validation. All other options
     * come from {@link GeoJsonOptions#getGlobal()} at call time.
     *
     * @param coordinateValidationEnabled when true, deserializers validate WGS84 range;
     *                                    when false, only validate Double.isFinite()
     */
    public GeometryJacksonModule(boolean coordinateValidationEnabled) {
        super(MODULE_NAME);
        register(null, coordinateValidationEnabled);
    }

    /**
     * Create a GeometryJacksonModule with fixed options.
     *
     * @param options the options for all serializers and deserializers; null to use
     *                {@link GeoJsonOptions#getGlobal()} at call time
     */
    public GeometryJacksonModule(GeoJsonOptions options) {
        super(MODULE_NAME);
        register(options, null);
    }

    private void register(GeoJsonOptions options, Boolean rangeValidation) {
        // Serializers - specific types first
        addSerializer(Point.class, new PointSerializer(options));
        addSerializer(LineString.class, new LineStringSerializer(options));
        addSerializer(Polygon.class, new PolygonSerializer(options));
        addSerializer(MultiPoint.class, new MultiPointSerializer(options));
        addSerializer(MultiLineString.class, new MultiLineStringSerializer(options));
        addSerializer(MultiPolygon.class, new MultiPolygonSerializer(options));
        addSerializer(GeometryCollection.class, new GeometryCollectionSerializer(options));
        // Generic Geometry serializer registered last to avoid Jackson type resolution issues
        addSerializer(Geometry.class, new GenericGeometrySerializer(options));

        // Deserializers - specific types first
        if (rangeValidation != null) {
            boolean validate = rangeValidation;
            addDeserializer(Point.class, new PointDeserializer(validate));
            addDeserializer(LineString.class, new LineStringDeserializer(validate));
            addDeserializer(Polygon.class, new PolygonDeserializer(validate));
            addDeserializer(MultiPoint.class, new MultiPointDeserializer(validate));
            addDeserializer(MultiLineString.class, new MultiLineStringDeserializer(validate));
            addDeserializer(MultiPolygon.class, new MultiPolygonDeserializer(validate));
            addDeserializer(GeometryCollection.class, new GeometryCollectionDeserializer(validate));
            addDeserializer(Geometry.class, new GenericGeometryDeserializer(validate));
        } else {
            addDeserializer(Point.class, new PointDeserializer(options));
            addDeserializer(LineString.class, new LineStringDeserializer(options));
            addDeserializer(Polygon.class, new PolygonDeserializer(options));
            addDeserializer(MultiPoint.class, new MultiPointDeserializer(options));
            addDeserializer(MultiLineString.class, new MultiLineStringDeserializer(options));
            addDeserializer(MultiPolygon.class, new MultiPolygonDeserializer(options));
            addDeserializer(GeometryCollection.class, new GeometryCollectionDeserializer(options));
            // Generic Geometry deserializer registered last to avoid Jackson type resolution issues
            addDeserializer(Geometry.class, new GenericGeometryDeserializer(options));
        }
    }
}
