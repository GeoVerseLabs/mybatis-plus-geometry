package io.github.geoverselabs.mybatis.geometry.config;

import io.github.geoverselabs.mybatis.geometry.jackson.GeoJsonOptions;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MyBatis Plus Geometry Extension.
 *
 * <p>Example configuration in application.yml:</p>
 * <pre>{@code
 * mybatis:
 *   geometry:
 *     default-srid: 4326
 *     database-type: MYSQL
 *     write-validation: BASIC
 *     coordinate-sequence: ARRAY
 *     geojson:
 *       coordinate-precision: 6
 * }</pre>
 */
@ConfigurationProperties(prefix = "mybatis.geometry")
public class GeometryProperties {

    /** Default SRID constant */
    public static final int DEFAULT_SRID = WkbUtil.DEFAULT_SRID;

    /** WGS84 SRID value */
    public static final int WGS84_SRID = 4326;

    /**
     * Default SRID for geometry objects without explicit SRID.
     * Default: 4326 (WGS84)
     */
    private int defaultSrid = DEFAULT_SRID;

    /**
     * Register the legacy SELECT interceptor that wraps geometry columns in HEX() (MySQL) or
     * encode(ST_AsEWKB()) (PostGIS). TypeHandlers read geometry columns natively, so the interceptor
     * is no longer needed and only doubles the bytes transferred. Default: false
     */
    private boolean interceptorEnabled = false;

    /**
     * Database type (auto-detected if not specified).
     * Supported values: MYSQL, POSTGRESQL
     */
    private DatabaseType databaseType;

    /**
     * Validation applied by TypeHandlers before writing a geometry: FULL runs OGC isValid(),
     * BASIC only rejects NaN/infinite coordinates, NONE skips validation. Default: BASIC
     */
    private GeometryValidation writeValidation = GeometryValidation.BASIC;

    /**
     * Write Z ordinates for geometries that have them (PostGIS only; MySQL and MariaDB are 2D).
     * Default: false (Z is dropped, as in previous versions)
     */
    private boolean preserveZ = false;

    /**
     * Coordinate storage of geometries created by the library: ARRAY (Coordinate objects) or
     * PACKED (double[] backed, about 2.7x less heap). Default: ARRAY
     */
    private CoordinateSequenceType coordinateSequence = CoordinateSequenceType.ARRAY;

    /**
     * GeoJSON serialization settings.
     */
    private final GeoJson geojson = new GeoJson();

    public int getDefaultSrid() {
        return defaultSrid;
    }

    public void setDefaultSrid(int defaultSrid) {
        this.defaultSrid = defaultSrid;
    }

    public boolean isInterceptorEnabled() {
        return interceptorEnabled;
    }

    public void setInterceptorEnabled(boolean interceptorEnabled) {
        this.interceptorEnabled = interceptorEnabled;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    public GeometryValidation getWriteValidation() {
        return writeValidation;
    }

    public void setWriteValidation(GeometryValidation writeValidation) {
        this.writeValidation = writeValidation == null ? GeometryValidation.BASIC : writeValidation;
    }

    public boolean isPreserveZ() {
        return preserveZ;
    }

    public void setPreserveZ(boolean preserveZ) {
        this.preserveZ = preserveZ;
    }

    public CoordinateSequenceType getCoordinateSequence() {
        return coordinateSequence;
    }

    public void setCoordinateSequence(CoordinateSequenceType coordinateSequence) {
        this.coordinateSequence = coordinateSequence == null ? CoordinateSequenceType.ARRAY : coordinateSequence;
    }

    public GeoJson getGeojson() {
        return geojson;
    }

    /**
     * Whether coordinate range validation should be enabled for GeoJSON input.
     * Uses {@code geojson.coordinate-range-validation} when set, otherwise enabled only when the
     * default SRID is 4326 (WGS84).
     *
     * @return true when longitudes/latitudes must be within WGS84 ranges
     */
    public boolean isCoordinateValidationEnabled() {
        Boolean explicit = geojson.getCoordinateRangeValidation();
        return explicit != null ? explicit : defaultSrid == WGS84_SRID;
    }

    /**
     * Build the GeoJSON options described by these properties.
     *
     * @return GeoJSON options for serializers and deserializers
     */
    public GeoJsonOptions toGeoJsonOptions() {
        return new GeoJsonOptions(geojson.getCoordinatePrecision(), isCoordinateValidationEnabled(),
            geojson.getValidation(), geojson.isFastDoubleParsing());
    }

    /**
     * GeoJSON serialization settings ({@code mybatis.geometry.geojson.*}).
     */
    public static class GeoJson {

        /**
         * Decimal places written for each ordinate (0-15). Unset writes full double precision.
         * RFC 7946 recommends 6 (about 10 cm) for WGS84 coordinates.
         */
        private Integer coordinatePrecision;

        /**
         * Validation of parsed GeoJSON: FULL also runs OGC isValid() on polygonal geometries,
         * BASIC only checks structure. Default: FULL
         */
        private GeometryValidation validation = GeometryValidation.FULL;

        /**
         * Reject longitudes outside [-180, 180] and latitudes outside [-90, 90]. Unset: enabled
         * only when default-srid is 4326.
         */
        private Boolean coordinateRangeValidation;

        /**
         * Parse coordinate numbers with Jackson's fast double parser (Jackson 2.14+). Default: true
         */
        private boolean fastDoubleParsing = true;

        public Integer getCoordinatePrecision() {
            return coordinatePrecision;
        }

        public void setCoordinatePrecision(Integer coordinatePrecision) {
            if (coordinatePrecision != null
                && (coordinatePrecision < 0 || coordinatePrecision > GeoJsonOptions.MAX_PRECISION)) {
                throw new IllegalArgumentException("mybatis.geometry.geojson.coordinate-precision must be between 0 and "
                    + GeoJsonOptions.MAX_PRECISION + ", got " + coordinatePrecision);
            }
            this.coordinatePrecision = coordinatePrecision;
        }

        public GeometryValidation getValidation() {
            return validation;
        }

        public void setValidation(GeometryValidation validation) {
            this.validation = validation == null ? GeometryValidation.FULL : validation;
        }

        public Boolean getCoordinateRangeValidation() {
            return coordinateRangeValidation;
        }

        public void setCoordinateRangeValidation(Boolean coordinateRangeValidation) {
            this.coordinateRangeValidation = coordinateRangeValidation;
        }

        public boolean isFastDoubleParsing() {
            return fastDoubleParsing;
        }

        public void setFastDoubleParsing(boolean fastDoubleParsing) {
            this.fastDoubleParsing = fastDoubleParsing;
        }
    }
}
