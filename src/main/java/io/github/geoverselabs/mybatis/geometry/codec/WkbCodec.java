package io.github.geoverselabs.mybatis.geometry.codec;

import org.locationtech.jts.geom.Geometry;

/**
 * Interface for encoding/decoding JTS Geometry objects to/from database-specific
 * binary or hex representations.
 *
 * <p>Each database strategy owns its codec implementation to encapsulate
 * the serialization format details.</p>
 *
 * <p>Implementations:</p>
 * <ul>
 *   <li>{@link MySQLWkbCodec} - 4-byte SRID prefix + standard WKB binary (byte[])</li>
 *   <li>{@link PostGISWkbCodec} - EWKB hex string with SRID flag in type field</li>
 * </ul>
 */
public interface WkbCodec {

    /**
     * Encode a JTS Geometry to database-specific representation.
     *
     * @param geometry the geometry to encode (non-null)
     * @return encoded representation (byte[] for MySQL, String hex for PostGIS)
     */
    Object encode(Geometry geometry);

    /**
     * Decode a database value to a JTS Geometry.
     *
     * @param dbValue the database value (byte[] or String hex depending on database)
     * @return decoded JTS Geometry, or null if dbValue is null
     */
    Geometry decode(Object dbValue);
}
