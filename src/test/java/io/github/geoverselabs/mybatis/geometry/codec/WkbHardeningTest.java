package io.github.geoverselabs.mybatis.geometry.codec;

import io.github.geoverselabs.mybatis.geometry.exception.WkbParseException;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decoding of hostile or unusual WKB values and of the formats accepted by {@link WkbUtil#fromWkb}.
 */
class WkbHardeningTest {

    /** Little-endian GEOMETRYCOLLECTION nesting {@code depth} levels around an empty collection. */
    private static String nestedCollections(int depth) {
        StringBuilder hex = new StringBuilder("E6100000");
        hex.append("010700000001000000".repeat(depth));
        hex.append("010700000000000000");
        return hex.toString();
    }

    @Test
    void decodesDeepButReasonableNesting() {
        Geometry g = WkbUtil.fromWkb(nestedCollections(500));
        assertThat(g.getGeometryType()).isEqualTo("GeometryCollection");
        assertThat(g.getSRID()).isEqualTo(4326);
    }

    @Test
    void rejectsExcessiveNestingWithAnExceptionInsteadOfStackOverflow() {
        assertThatThrownBy(() -> WkbUtil.fromWkb(nestedCollections(20_000)))
            .isInstanceOf(WkbParseException.class)
            .hasMessageContaining("nested deeper than " + WkbSupport.MAX_NESTING_DEPTH);
        assertThatThrownBy(() -> new MySQLWkbCodec().decode(nestedCollections(WkbSupport.MAX_NESTING_DEPTH + 5)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nested deeper than");
    }

    @Test
    void structureScanReportsTooDeepSeparatelyFromMalformed() {
        byte[] deep = WkbSupport.hexToBytes(nestedCollections(WkbSupport.MAX_NESTING_DEPTH + 1).substring(8));
        assertThat(WkbSupport.wkbEnd(deep, 0, 0)).isEqualTo(WkbSupport.TOO_DEEP);
        byte[] ok = WkbSupport.hexToBytes(nestedCollections(WkbSupport.MAX_NESTING_DEPTH - 1).substring(8));
        assertThat(WkbSupport.wkbEnd(ok, 0, 0)).isEqualTo(ok.length);
        byte[] truncated = WkbSupport.hexToBytes("0107000000010000000107");
        assertThat(WkbSupport.wkbEnd(truncated, 0, 0)).isEqualTo(-1);
    }

    @Test
    void fromWkbAcceptsEwkbAsWellAsTheSridPrefixedFormat() {
        // hex EWKB of SRID=4326;POINT(1 2), as returned by a PostGIS column or the 1.1 interceptor wrapper
        Geometry ewkb = WkbUtil.fromWkb("0101000020e6100000000000000000f03f0000000000000040");
        assertThat(ewkb.toText()).isEqualTo("POINT (1 2)");
        assertThat(ewkb.getSRID()).isEqualTo(4326);

        // 1.0.x format: 4-byte little-endian SRID + WKB
        Geometry prefixed = WkbUtil.fromWkb("E61000000101000000000000000000F03F0000000000000040");
        assertThat(prefixed.toText()).isEqualTo("POINT (1 2)");
        assertThat(prefixed.getSRID()).isEqualTo(4326);
    }
}
