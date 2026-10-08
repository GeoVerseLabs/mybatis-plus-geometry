package io.github.geoverselabs.mybatis.geometry.support;

/**
 * Storage layout of coordinates in geometries created by this library.
 */
public enum CoordinateSequenceType {

    /**
     * {@code Coordinate[]} backed sequences (JTS default). Each point is a separate
     * {@code Coordinate} object, so {@code getCoordinates()} returns live, mutable objects.
     */
    ARRAY,

    /**
     * Packed {@code double[]} backed sequences. Roughly 2.7x less heap per 2D point and less
     * GC pressure for large geometries; {@code getCoordinates()} returns copies.
     */
    PACKED
}
