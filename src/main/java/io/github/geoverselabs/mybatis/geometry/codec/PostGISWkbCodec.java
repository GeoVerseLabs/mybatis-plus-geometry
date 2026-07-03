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
 * PostGIS EWKB codec: uses JTS WKBWriter's EWKB mode (SRID flag 0x20000000 embedded in type field).
 * Output can be directly parsed by PostGIS ST_GeomFromEWKB.
 *
 * <p>Thread safety: creates new WKBWriter/WKBReader instances on each invocation.</p>
 *
 * <p>Encode output: {@code String} hex EWKB (used with {@code ps.setObject(str, Types.OTHER)})</p>
 * <p>Decode input: {@code String} hex (4-byte LE SRID prefix + standard WKB from SELECT)</p>
 */
public class PostGISWkbCodec implements WkbCodec {

    private static final int SRID_PREFIX_LENGTH = 4;
    private static final int DEFAULT_SRID = 4326;

    @Override
    public Object encode(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        int srid = geometry.getSRID() == 0 ? DEFAULT_SRID : geometry.getSRID();
        geometry.setSRID(srid);

        WKBWriter writer = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, true); // includeSRID=true
        byte[] ewkb = writer.write(geometry);
        return HexFormat.of().formatHex(ewkb);
    }

    @Override
    public Geometry decode(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        if (!(dbValue instanceof String hexString)) {
            throw new IllegalArgumentException(
                "PostGISWkbCodec expects String hex, got: " + dbValue.getClass().getName());
        }
        if (hexString.isEmpty()) {
            return null;
        }

        try {
            byte[] bytes = HexFormat.of().parseHex(hexString);
            if (bytes.length < SRID_PREFIX_LENGTH + 5) {
                throw new IllegalArgumentException("Input too short for PostGIS WKB format");
            }
            // PostGIS SELECT returns format: 4-byte LE SRID + standard WKB
            int srid = ByteBuffer.wrap(bytes, 0, SRID_PREFIX_LENGTH)
                         .order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] wkb = new byte[bytes.length - SRID_PREFIX_LENGTH];
            System.arraycopy(bytes, SRID_PREFIX_LENGTH, wkb, 0, wkb.length);

            WKBReader reader = new WKBReader();
            Geometry geom = reader.read(wkb);
            geom.setSRID(srid);
            return geom;
        } catch (ParseException e) {
            String prefix = hexString.substring(0, Math.min(20, hexString.length()));
            throw new IllegalArgumentException(
                "Failed to decode PostGIS WKB, hex prefix: " + prefix, e);
        }
    }
}
