package io.github.geoverselabs.mybatis.geometry.support;

/**
 * Validation level applied to geometries when they cross a library boundary
 * (GeoJSON input, database writes).
 *
 * <p>OGC validation ({@code Geometry.isValid()}) is O(n log n) and dominates the cost of
 * handling large polygons, so applications that already validate at the GeoJSON boundary
 * can lower the level used for database writes.</p>
 */
public enum GeometryValidation {

    /**
     * Structural checks plus OGC validity ({@code Geometry.isValid()}).
     */
    FULL,

    /**
     * Structural checks only: finite coordinates, closed rings and minimum point counts.
     */
    BASIC,

    /**
     * No validation beyond what JTS itself enforces when building geometries.
     */
    NONE
}
