package io.github.geoverselabs.mybatis.geometry.codec;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MySQL/MariaDB codec for the internal geometry format: 4-byte little-endian SRID prefix + WKB.
 *
 * <p>Encode output: {@code byte[]} (used with {@code ps.setBytes()}). Geometries are always written
 * as 2D because MySQL and MariaDB do not store Z or M ordinates.</p>
 *
 * <p>Decode input:</p>
 * <ul>
 *   <li>{@code byte[]} in the internal format, as returned by {@code ResultSet.getBytes()} for a
 *       geometry column;</li>
 *   <li>{@code byte[]} holding ASCII hex text, as returned by {@code getBytes()} for a
 *       {@code HEX(column)} expression;</li>
 *   <li>{@code String} hex (case-insensitive) of the internal format, e.g. from {@code HEX(column)}.</li>
 * </ul>
 *
 * <p>Thread safety: stateless; creates new WKB reader/writer instances on each invocation.</p>
 */
public class MySQLWkbCodec implements WkbCodec {

    private static final Logger log = LoggerFactory.getLogger(MySQLWkbCodec.class);

    private static final AtomicBoolean Z_DROP_REPORTED = new AtomicBoolean();

    /**
     * Encode with the geometry's SRID, or the configured default SRID when it is 0.
     *
     * @param geometry the geometry to encode (not modified)
     * @return the internal format bytes, or null for a null geometry
     */
    @Override
    public Object encode(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return encode(geometry, WkbSupport.effectiveSrid(geometry));
    }

    /**
     * Encode as 4-byte little-endian {@code srid} followed by 2D little-endian WKB.
     *
     * @param geometry the geometry to encode (not modified)
     * @param srid     the SRID to store
     * @return the internal format bytes, or null for a null geometry
     */
    @Override
    public Object encode(Geometry geometry, int srid) {
        if (geometry == null) {
            return null;
        }
        if (!Z_DROP_REPORTED.get() && WkbSupport.firstCoordinateHasZ(geometry)
                && Z_DROP_REPORTED.compareAndSet(false, true)) {
            log.warn("MySQL/MariaDB do not store Z ordinates: Z values of {} geometries are dropped on write "
                + "(reported once)", geometry.getGeometryType());
        }
        return WkbSupport.writeSridPrefixed(geometry, srid);
    }

    /**
     * Decode the internal format from raw bytes, ASCII hex bytes or a hex string.
     *
     * @param dbValue {@code byte[]} or {@code String}
     * @return the geometry, or null for null or empty input
     * @throws IllegalArgumentException if the value has another type or is malformed
     */
    @Override
    public Geometry decode(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        if (dbValue instanceof byte[] bytes) {
            if (bytes.length == 0) {
                return null;
            }
            if (bytes.length >= WkbSupport.SRID_PREFIX_LENGTH + 5
                    && (bytes[WkbSupport.SRID_PREFIX_LENGTH] == 0 || bytes[WkbSupport.SRID_PREFIX_LENGTH] == 1)) {
                return WkbSupport.readSridPrefixed(bytes);
            }
            byte[] decoded = WkbSupport.tryDecodeAsciiHex(bytes);
            if (decoded == null) {
                throw new IllegalArgumentException("Not a MySQL geometry value (neither internal format nor hex text): "
                    + WkbSupport.hexPrefix(bytes));
            }
            return WkbSupport.readSridPrefixed(decoded);
        }
        if (dbValue instanceof String hex) {
            if (hex.isEmpty()) {
                return null;
            }
            return WkbSupport.readSridPrefixed(WkbSupport.hexToBytes(hex));
        }
        throw new IllegalArgumentException(
            "MySQLWkbCodec expects byte[] or String hex, got: " + dbValue.getClass().getName());
    }
}
