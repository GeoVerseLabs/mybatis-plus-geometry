package io.github.geoverselabs.mybatis.geometry.codec;

import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PostGIS codec based on EWKB (SRID flag {@code 0x20000000} embedded in the type word).
 * Output can be passed directly to a geometry parameter ({@code ps.setObject(hex, Types.OTHER)}).
 *
 * <p>Encode output: {@code String} lowercase hex EWKB. Geometries are written as 2D unless
 * {@link GeometryDefaults#isPreserveZ()} is enabled and the geometry has at least one non-NaN Z
 * value, in which case 3D EWKB (Z flag) is written.</p>
 *
 * <p>Decode input: a {@code String} or {@code byte[]} holding</p>
 * <ul>
 *   <li>hex (E)WKB, optionally with the bytea prefix {@code \x} — the text output of a PostGIS
 *       {@code geometry} column, {@code encode(ST_AsEWKB(col), 'hex')} or a bytea expression such as
 *       {@code ST_AsEWKB(col)} read with {@code getString()};</li>
 *   <li>raw (E)WKB bytes;</li>
 *   <li>the legacy format "4-byte little-endian SRID + WKB" produced by earlier versions.</li>
 * </ul>
 * <p>An EWKB value without SRID flag decodes with SRID 0. Geometries with M ordinates are rejected.</p>
 *
 * <p>Thread safety: stateless; creates new WKB reader/writer instances on each invocation.</p>
 */
public class PostGISWkbCodec implements WkbCodec {

    private static final Logger log = LoggerFactory.getLogger(PostGISWkbCodec.class);

    private static final AtomicBoolean Z_DROP_REPORTED = new AtomicBoolean();

    /**
     * Encode with the geometry's SRID, or the configured default SRID when it is 0.
     *
     * @param geometry the geometry to encode (not modified)
     * @return hex EWKB, or null for a null geometry
     */
    @Override
    public Object encode(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return encode(geometry, WkbSupport.effectiveSrid(geometry));
    }

    /**
     * Encode as little-endian hex EWKB carrying {@code srid}.
     *
     * @param geometry the geometry to encode (not modified)
     * @param srid     the SRID to embed (0 writes plain WKB without SRID flag)
     * @return hex EWKB, or null for a null geometry
     */
    @Override
    public Object encode(Geometry geometry, int srid) {
        if (geometry == null) {
            return null;
        }
        boolean withZ;
        if (GeometryDefaults.isPreserveZ()) {
            withZ = WkbSupport.hasZ(geometry);
        } else {
            withZ = false;
            if (!Z_DROP_REPORTED.get() && WkbSupport.firstCoordinateHasZ(geometry)
                && Z_DROP_REPORTED.compareAndSet(false, true)) {
                log.warn("Z values of {} geometries are dropped on write because Z preservation is disabled; "
                    + "enable it with GeometryDefaults.setPreserveZ(true) (reported once)",
                    geometry.getGeometryType());
            }
        }
        return WkbSupport.toHex(WkbSupport.writeEwkb(geometry, srid, withZ), false);
    }

    /**
     * Decode hex or binary (E)WKB, or the legacy SRID-prefixed format.
     *
     * @param dbValue {@code String} or {@code byte[]}
     * @return the geometry, or null for null or empty input
     * @throws IllegalArgumentException if the value has another type or is malformed
     */
    @Override
    public Geometry decode(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        byte[] bytes;
        if (dbValue instanceof String text) {
            if (text.isEmpty()) {
                return null;
            }
            bytes = WkbSupport.hexToBytes(text);
        } else if (dbValue instanceof byte[] raw) {
            if (raw.length == 0) {
                return null;
            }
            bytes = raw;
            if (raw[0] != 0 && raw[0] != 1) {
                byte[] decoded = WkbSupport.tryDecodeAsciiHex(raw);
                if (decoded != null) {
                    bytes = decoded;
                }
            }
        } else {
            throw new IllegalArgumentException(
                "PostGISWkbCodec expects String hex or byte[], got: " + dbValue.getClass().getName());
        }
        if (bytes.length == 0) {
            return null;
        }
        return WkbSupport.readEwkbOrSridPrefixed(bytes);
    }
}
