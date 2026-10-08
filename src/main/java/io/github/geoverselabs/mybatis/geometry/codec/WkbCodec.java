package io.github.geoverselabs.mybatis.geometry.codec;

import org.locationtech.jts.geom.Geometry;

/**
 * Interface for encoding/decoding JTS Geometry objects to/from database-specific
 * binary or hex representations.
 *
 * <p>Each database strategy owns its codec implementation to encapsulate
 * the serialization format details. Implementations must never modify the geometry they encode.</p>
 *
 * <p>Implementations:</p>
 * <ul>
 *   <li>{@link MySQLWkbCodec} - 4-byte SRID prefix + standard WKB binary (byte[])</li>
 *   <li>{@link PostGISWkbCodec} - EWKB hex string with SRID flag in type field</li>
 * </ul>
 */
public interface WkbCodec {

    /**
     * Encode a JTS Geometry to database-specific representation, using the geometry's SRID.
     *
     * @param geometry the geometry to encode (non-null)
     * @return encoded representation (byte[] for MySQL, String hex for PostGIS)
     */
    Object encode(Geometry geometry);

    /**
     * Encode a JTS Geometry with an explicit SRID, without modifying the geometry.
     *
     * <p>The default implementation encodes the geometry itself when its SRID already matches,
     * and otherwise encodes a copy carrying {@code srid}. Built-in codecs override this method to
     * write the SRID directly.</p>
     *
     * @param geometry the geometry to encode (non-null, not modified)
     * @param srid     the SRID to store
     * @return encoded representation (byte[] for MySQL, String hex for PostGIS)
     */
    default Object encode(Geometry geometry, int srid) {
        if (geometry == null) {
            return null;
        }
        if (geometry.getSRID() == srid) {
            return encode(geometry);
        }
        Geometry copy = geometry.copy();
        copy.setSRID(srid);
        return encode(copy);
    }

    /**
     * Decode a database value to a JTS Geometry.
     *
     * @param dbValue the database value (byte[] or String hex depending on database)
     * @return decoded JTS Geometry, or null if dbValue is null
     * @throws IllegalArgumentException if the value cannot be decoded; the original failure is the cause
     */
    Geometry decode(Object dbValue);
}
