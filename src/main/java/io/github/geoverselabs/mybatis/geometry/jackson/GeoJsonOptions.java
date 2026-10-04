package io.github.geoverselabs.mybatis.geometry.jackson;

import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;

/**
 * Options controlling GeoJSON reading and writing.
 *
 * @param coordinatePrecision       number of decimal places written for each ordinate, or
 *                                  {@code null} to write full double precision. RFC 7946
 *                                  recommends 6 (about 10 cm) for WGS84 coordinates.
 * @param coordinateRangeValidation when true, input longitudes must be within [-180, 180] and
 *                                  latitudes within [-90, 90]; when false, input ordinates only
 *                                  need to be finite
 * @param validation                validation applied to parsed geometries; {@code NONE} is
 *                                  treated as {@code BASIC} because structural checks are
 *                                  required to build JTS geometries
 * @param fastDoubleParsing         parse coordinate numbers with Jackson's fast double parser
 *                                  (Jackson 2.14+; silently ignored on older versions)
 */
public record GeoJsonOptions(Integer coordinatePrecision,
                             boolean coordinateRangeValidation,
                             GeometryValidation validation,
                             boolean fastDoubleParsing) {

    /** Largest supported {@link #coordinatePrecision()}. */
    public static final int MAX_PRECISION = 15;

    /** Full precision output, WGS84 range checks, full validation, fast double parsing. */
    public static final GeoJsonOptions DEFAULTS = new GeoJsonOptions(null, true, GeometryValidation.FULL, true);

    private static volatile GeoJsonOptions global = DEFAULTS;

    public GeoJsonOptions {
        if (coordinatePrecision != null && (coordinatePrecision < 0 || coordinatePrecision > MAX_PRECISION)) {
            throw new IllegalArgumentException(
                "coordinatePrecision must be between 0 and " + MAX_PRECISION + ", got " + coordinatePrecision);
        }
        if (validation == null) {
            validation = GeometryValidation.FULL;
        }
    }

    /**
     * Options used by serializers and deserializers created through their no-arg constructors
     * (for example via {@code @JsonSerialize(using = ...)}). Spring Boot auto-configuration sets
     * this from {@code mybatis.geometry.*} properties.
     *
     * @return the process-wide options, never null
     */
    public static GeoJsonOptions getGlobal() {
        return global;
    }

    /**
     * Replace the process-wide options.
     *
     * @param options the options; null restores {@link #DEFAULTS}
     */
    public static void setGlobal(GeoJsonOptions options) {
        global = options == null ? DEFAULTS : options;
    }

    /**
     * Restore {@link #DEFAULTS} as the process-wide options. Intended for tests.
     */
    public static void resetGlobal() {
        global = DEFAULTS;
    }

    public GeoJsonOptions withCoordinatePrecision(Integer precision) {
        return new GeoJsonOptions(precision, coordinateRangeValidation, validation, fastDoubleParsing);
    }

    public GeoJsonOptions withCoordinateRangeValidation(boolean enabled) {
        return new GeoJsonOptions(coordinatePrecision, enabled, validation, fastDoubleParsing);
    }

    public GeoJsonOptions withValidation(GeometryValidation level) {
        return new GeoJsonOptions(coordinatePrecision, coordinateRangeValidation, level, fastDoubleParsing);
    }

    public GeoJsonOptions withFastDoubleParsing(boolean enabled) {
        return new GeoJsonOptions(coordinatePrecision, coordinateRangeValidation, validation, enabled);
    }
}
