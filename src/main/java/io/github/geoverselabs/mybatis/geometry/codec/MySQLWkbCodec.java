package io.github.geoverselabs.mybatis.geometry.codec;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;

/**
 * MySQL WKB codec: 4-byte little-endian SRID prefix + standard WKB.
 * Internally uses JTS WKBWriter/WKBReader for encoding and decoding.
 *
 * <p>Encode output: {@code byte[]} (used with {@code ps.setBytes()})</p>
 * <p>Decode input: {@code String} hex (from HEX() SQL function)</p>
 *
 * <p>Thread safety: creates new WKBWriter/WKBReader instances on each invocation.</p>
 */
public class MySQLWkbCodec implements WkbCodec {

    private static final int SRID_PREFIX_LENGTH = 4;

    @Override
    public Object encode(Geometry geometry) {
        if (geometry == null) return null;
        int srid = geometry.getSRID() == 0 ? 4326 : geometry.getSRID();
        geometry.setSRID(srid);

        WKBWriter writer = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN);
        byte[] wkb = writer.write(geometry);

        // Prepend 4-byte little-endian SRID prefix
        byte[] result = new byte[SRID_PREFIX_LENGTH + wkb.length];
        ByteBuffer.wrap(result, 0, SRID_PREFIX_LENGTH)
                  .order(ByteOrder.LITTLE_ENDIAN)
                  .putInt(srid);
        System.arraycopy(wkb, 0, result, SRID_PREFIX_LENGTH, wkb.length);
        return result;
    }

    @Override
    public Geometry decode(Object dbValue) {
        if (dbValue == null) return null;
        if (!(dbValue instanceof String hexString)) {
            throw new IllegalArgumentException(
                "MySQLWkbCodec expects String hex, got: " + dbValue.getClass().getName());
        }
        if (hexString.isEmpty()) return null;

        try {
            byte[] bytes = HexFormat.of().parseHex(hexString);
            if (bytes.length < SRID_PREFIX_LENGTH + 5) {
                throw new IllegalArgumentException("Input too short for MySQL WKB format");
            }
            // Parse SRID
            int srid = ByteBuffer.wrap(bytes, 0, SRID_PREFIX_LENGTH)
                                 .order(ByteOrder.LITTLE_ENDIAN)
                                 .getInt();
            // Parse standard WKB
            byte[] wkb = new byte[bytes.length - SRID_PREFIX_LENGTH];
            System.arraycopy(bytes, SRID_PREFIX_LENGTH, wkb, 0, wkb.length);

            WKBReader reader = new WKBReader();
            Geometry geom = reader.read(wkb);
            geom.setSRID(srid);
            return geom;
        } catch (ParseException e) {
            String prefix = hexString.substring(0, Math.min(20, hexString.length()));
            throw new IllegalArgumentException(
                "Failed to decode MySQL WKB, hex prefix: " + prefix, e);
        }
    }
}
