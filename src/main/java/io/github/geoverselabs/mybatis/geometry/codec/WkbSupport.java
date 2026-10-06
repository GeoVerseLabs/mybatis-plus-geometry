package io.github.geoverselabs.mybatis.geometry.codec;

import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.InStream;
import org.locationtech.jts.io.OutStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * WKB encoding and decoding shared by {@link MySQLWkbCodec}, {@link PostGISWkbCodec} and
 * {@link io.github.geoverselabs.mybatis.geometry.util.WkbUtil}.
 *
 * <p>This is an internal helper of the library; its methods may change between versions.
 * Applications should use the codecs or {@code WkbUtil}.</p>
 *
 * <p>Formats:</p>
 * <ul>
 *   <li><strong>SRID-prefixed</strong>: 4-byte little-endian SRID followed by WKB. This is the
 *       MySQL/MariaDB internal geometry format and the format of {@code WkbUtil}.</li>
 *   <li><strong>(E)WKB</strong>: OGC/ISO WKB or PostGIS EWKB (SRID flag {@code 0x20000000} plus a
 *       4-byte SRID after the type word, Z flag {@code 0x80000000}).</li>
 * </ul>
 *
 * <p>Decoding always uses a new {@link WKBReader} built on
 * {@link GeometryFactoryProvider#getFactory(int)} for the decoded SRID, so every component of the
 * result carries that SRID and the configured coordinate sequence type. Encoding never modifies the
 * geometry. All methods are thread-safe and allocate the output in a single buffer.</p>
 */
public final class WkbSupport {

    /** Length of the SRID prefix of the MySQL internal format. */
    public static final int SRID_PREFIX_LENGTH = 4;

    /** EWKB flag: an SRID follows the type word. */
    static final int EWKB_SRID_FLAG = 0x20000000;

    /** EWKB flag: coordinates have a Z ordinate. */
    static final int EWKB_Z_FLAG = 0x80000000;

    /** EWKB flag: coordinates have an M ordinate. */
    static final int EWKB_M_FLAG = 0x40000000;

    /** Byte order byte plus type word. */
    private static final int HEADER_LENGTH = 5;

    /**
     * Deepest geometry collection nesting accepted. JTS reads WKB recursively, so deeper values are
     * rejected before they reach it instead of overflowing the stack.
     */
    static final int MAX_NESTING_DEPTH = 1000;

    /** {@link #wkbEnd(byte[], int, int)} result for values nested deeper than {@link #MAX_NESTING_DEPTH}. */
    static final int TOO_DEEP = -2;

    /** Number of bytes shown in error messages. */
    private static final int PREFIX_BYTES = 16;

    private static final HexFormat HEX_LOWER = HexFormat.of();
    private static final HexFormat HEX_UPPER = HexFormat.of().withUpperCase();

    private static final byte[] HEX_VALUES = new byte[128];

    static {
        Arrays.fill(HEX_VALUES, (byte) -1);
        for (int i = 0; i < 10; i++) {
            HEX_VALUES['0' + i] = (byte) i;
        }
        for (int i = 0; i < 6; i++) {
            HEX_VALUES['a' + i] = (byte) (10 + i);
            HEX_VALUES['A' + i] = (byte) (10 + i);
        }
    }

    private WkbSupport() {
    }

    // ==================== SRID ====================

    /**
     * SRID to store for a geometry: its own SRID, or the configured default
     * ({@link GeometryFactoryProvider#getConfiguredSrid()}, which may be 0) when the geometry SRID is 0.
     *
     * @param geometry the geometry (non-null)
     * @return the effective SRID
     */
    public static int effectiveSrid(Geometry geometry) {
        int srid = geometry.getSRID();
        return srid != 0 ? srid : GeometryFactoryProvider.getConfiguredSrid();
    }

    // ==================== Decoding ====================

    /**
     * Decode the SRID-prefixed format (4-byte little-endian SRID + WKB).
     * An SRID embedded in the WKB itself (EWKB) takes precedence over the prefix.
     *
     * @param bytes the encoded value
     * @return the decoded geometry
     * @throws IllegalArgumentException if the value is not valid SRID-prefixed WKB
     */
    public static Geometry readSridPrefixed(byte[] bytes) {
        if (bytes.length < SRID_PREFIX_LENGTH + HEADER_LENGTH) {
            throw new IllegalArgumentException("Input too short for SRID-prefixed WKB (" + bytes.length
                + " bytes): " + hexPrefix(bytes));
        }
        return readWkb(bytes, SRID_PREFIX_LENGTH, getIntLE(bytes, 0), false);
    }

    /**
     * Decode WKB or EWKB. The SRID is taken from the EWKB SRID flag, or is 0 when the flag is absent.
     *
     * @param bytes the encoded value
     * @return the decoded geometry
     * @throws IllegalArgumentException if the value is not valid (E)WKB
     */
    public static Geometry readEwkb(byte[] bytes) {
        return readWkb(bytes, 0, 0, false);
    }

    /**
     * Decode a PostGIS value that is either (E)WKB or the legacy SRID-prefixed format produced by
     * earlier versions of this library.
     *
     * <p>A value whose first byte is a WKB byte order marker ({@code 0x00}/{@code 0x01}) is read as
     * (E)WKB when its structure consumes the input exactly. Otherwise, and in the rare case where a
     * value is well-formed both as big-endian EWKB and as SRID prefix + WKB (for example a point with
     * SRID 8192; PostGIS itself emits little-endian EWKB), the SRID-prefixed reading is used when it
     * consumes the input exactly. Values matching neither are reported by the WKB reader.</p>
     *
     * @param bytes the encoded value
     * @return the decoded geometry
     * @throws IllegalArgumentException if the value is malformed
     */
    public static Geometry readEwkbOrSridPrefixed(byte[] bytes) {
        boolean wkbMarker = bytes.length > 0 && isByteOrder(bytes[0]);
        boolean ewkbExact = wkbMarker && wkbEnd(bytes, 0, 0) == bytes.length;
        if (ewkbExact && bytes[0] == 1) {
            return readWkb(bytes, 0, 0, true);
        }
        boolean prefixedExact = bytes.length >= SRID_PREFIX_LENGTH + HEADER_LENGTH
            && isByteOrder(bytes[SRID_PREFIX_LENGTH])
            && wkbEnd(bytes, SRID_PREFIX_LENGTH, 0) == bytes.length;
        if (prefixedExact) {
            return readWkb(bytes, SRID_PREFIX_LENGTH, getIntLE(bytes, 0), true);
        }
        // Not exact as SRID-prefixed: big-endian EWKB, or malformed input reported by the reader.
        return wkbMarker ? readEwkb(bytes) : readSridPrefixed(bytes);
    }

    /**
     * @param structureChecked true when {@link #wkbEnd(byte[], int, int)} already accepted the structure
     */
    private static Geometry readWkb(byte[] bytes, int offset, int defaultSrid, boolean structureChecked) {
        if (bytes.length - offset < HEADER_LENGTH) {
            throw new IllegalArgumentException("Input too short for WKB (" + bytes.length + " bytes): "
                + hexPrefix(bytes));
        }
        byte order = bytes[offset];
        if (!isByteOrder(order)) {
            throw new IllegalArgumentException("Invalid WKB byte order marker 0x"
                + HEX_LOWER.toHexDigits(order) + " at offset " + offset + ": " + hexPrefix(bytes));
        }
        boolean littleEndian = order == 1;
        int type = getInt(bytes, offset + 1, littleEndian);
        if (hasMeasure(type)) {
            throw new IllegalArgumentException("WKB geometries with M ordinates are not supported "
                + "(JTS 1.19 would read the measure as Z): " + hexPrefix(bytes));
        }
        int srid = defaultSrid;
        if ((type & EWKB_SRID_FLAG) != 0) {
            if (bytes.length - offset < HEADER_LENGTH + 4) {
                throw new IllegalArgumentException("Input too short for EWKB SRID: " + hexPrefix(bytes));
            }
            srid = getInt(bytes, offset + HEADER_LENGTH, littleEndian);
        }
        int end = structureChecked ? bytes.length : wkbEnd(bytes, offset, 0);
        if (end == TOO_DEEP) {
            throw new IllegalArgumentException("WKB geometry collections nested deeper than " + MAX_NESTING_DEPTH
                + " levels: " + hexPrefix(bytes));
        }
        WKBReader reader = new WKBReader(GeometryFactoryProvider.getFactory(srid));
        try {
            if (end > 0) {
                // Every count field was checked against the input length by the scan, so the
                // unbounded stream reader cannot over-allocate. (read(byte[]) caps counts at
                // length/16, which rejects collections of several empty members.)
                return reader.read(new SliceInStream(bytes, offset));
            }
            // Malformed or unusually deep structure: read(byte[]) bounds counts and reports errors.
            return reader.read(offset == 0 ? bytes : Arrays.copyOfRange(bytes, offset, bytes.length));
        } catch (ParseException | IOException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid WKB (" + e.getMessage() + "): " + hexPrefix(bytes), e);
        } catch (StackOverflowError e) {
            // backstop for malformed input the structure scan could not bound
            throw new IllegalArgumentException("WKB nested too deeply to decode: " + hexPrefix(bytes));
        }
    }

    /**
     * {@link InStream} over a byte array starting at an offset (avoids copying a prefixed value).
     */
    private static final class SliceInStream implements InStream {

        private final byte[] data;
        private int position;

        SliceInStream(byte[] data, int offset) {
            this.data = data;
            this.position = offset;
        }

        @Override
        public int read(byte[] buf) {
            int n = Math.min(buf.length, data.length - position);
            System.arraycopy(data, position, buf, 0, n);
            position += n;
            return n;
        }
    }

    private static boolean hasMeasure(int type) {
        int isoDimension = (type & 0xFFFF) / 1000;
        return (type & EWKB_M_FLAG) != 0 || isoDimension == 2 || isoDimension == 3;
    }

    /**
     * Walk the structure of a WKB geometry without building it.
     *
     * @return the offset just after the geometry starting at {@code offset}, -1 when the bytes do
     *     not form a well-formed WKB geometry there, or {@link #TOO_DEEP} when collections are nested
     *     deeper than {@link #MAX_NESTING_DEPTH}
     */
    static int wkbEnd(byte[] b, int offset, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            return TOO_DEEP;
        }
        if (offset < 0 || b.length - offset < HEADER_LENGTH || !isByteOrder(b[offset])) {
            return -1;
        }
        boolean le = b[offset] == 1;
        int type = getInt(b, offset + 1, le);
        if ((type & 0x10000000) != 0) {
            return -1;
        }
        int code = type & 0x0FFFFFFF;
        int base = code % 1000;
        int isoDimension = code / 1000;
        if (base < 1 || base > 7 || isoDimension > 3 || ((type & 0xE0000000) != 0 && isoDimension != 0)) {
            return -1;
        }
        boolean z = (type & EWKB_Z_FLAG) != 0 || isoDimension == 1 || isoDimension == 3;
        boolean m = (type & EWKB_M_FLAG) != 0 || isoDimension == 2 || isoDimension == 3;
        long coordinateSize = 8L * (2 + (z ? 1 : 0) + (m ? 1 : 0));
        long pos = offset + HEADER_LENGTH + ((type & EWKB_SRID_FLAG) != 0 ? 4 : 0);
        switch (base) {
            case 1:
                pos += coordinateSize;
                break;
            case 2: {
                int n = count(b, pos, le);
                if (n < 0) {
                    return -1;
                }
                pos += 4 + n * coordinateSize;
                break;
            }
            case 3: {
                int rings = count(b, pos, le);
                if (rings < 0) {
                    return -1;
                }
                pos += 4;
                for (int r = 0; r < rings && pos <= b.length; r++) {
                    int n = count(b, pos, le);
                    if (n < 0) {
                        return -1;
                    }
                    pos += 4 + n * coordinateSize;
                }
                break;
            }
            default: {
                int parts = count(b, pos, le);
                if (parts < 0) {
                    return -1;
                }
                pos += 4;
                for (int p = 0; p < parts; p++) {
                    pos = wkbEnd(b, (int) pos, depth + 1);
                    if (pos < 0) {
                        return (int) pos;
                    }
                }
                break;
            }
        }
        return pos <= b.length ? (int) pos : -1;
    }

    private static int count(byte[] b, long pos, boolean le) {
        if (pos + 4 > b.length) {
            return -1;
        }
        return getInt(b, (int) pos, le);
    }

    private static boolean isByteOrder(byte b) {
        return b == 0 || b == 1;
    }

    // ==================== Encoding ====================

    /**
     * Encode a geometry in the SRID-prefixed format: 4-byte little-endian SRID followed by 2D
     * little-endian WKB. Z and M ordinates are not written.
     *
     * @param geometry the geometry (non-null, not modified)
     * @param srid     the SRID written in the prefix
     * @return the encoded bytes
     */
    public static byte[] writeSridPrefixed(Geometry geometry, int srid) {
        byte[] out = new byte[SRID_PREFIX_LENGTH + wkbSize(geometry, 2)];
        putIntLE(out, 0, srid);
        writeWkb(geometry, 2, out, SRID_PREFIX_LENGTH);
        return out;
    }

    /**
     * Encode a geometry as little-endian EWKB with the given SRID (no SRID flag when it is 0).
     * The SRID is spliced in after the header of plain WKB, so the geometry is never modified.
     *
     * @param geometry the geometry (non-null, not modified)
     * @param srid     the SRID to embed
     * @param withZ    true to write Z ordinates (NaN for coordinates without Z)
     * @return the encoded bytes
     */
    public static byte[] writeEwkb(Geometry geometry, int srid, boolean withZ) {
        int dimension = withZ ? 3 : 2;
        int size = wkbSize(geometry, dimension);
        if (srid == 0) {
            byte[] out = new byte[size];
            writeWkb(geometry, dimension, out, 0);
            return out;
        }
        byte[] out = new byte[size + 4];
        writeWkb(geometry, dimension, out, 4);
        // move byte order + type word to the front, set the SRID flag, put the SRID after them
        System.arraycopy(out, 4, out, 0, HEADER_LENGTH);
        putIntLE(out, 1, getIntLE(out, 1) | EWKB_SRID_FLAG);
        putIntLE(out, HEADER_LENGTH, srid);
        return out;
    }

    /**
     * Whether the geometry has at least one coordinate with a non-NaN Z value.
     *
     * @param geometry the geometry (non-null)
     * @return true when a Z value is present
     */
    public static boolean hasZ(Geometry geometry) {
        if (geometry instanceof Point point) {
            return hasZ(point.getCoordinateSequence());
        }
        if (geometry instanceof LineString line) {
            return hasZ(line.getCoordinateSequence());
        }
        if (geometry instanceof Polygon polygon) {
            if (hasZ(polygon.getExteriorRing().getCoordinateSequence())) {
                return true;
            }
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                if (hasZ(polygon.getInteriorRingN(i).getCoordinateSequence())) {
                    return true;
                }
            }
            return false;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                if (hasZ(collection.getGeometryN(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Constant-time check used for one-time warnings: whether the first coordinate has a Z value.
     */
    static boolean firstCoordinateHasZ(Geometry geometry) {
        Coordinate first = geometry.getCoordinate();
        return first != null && !Double.isNaN(first.getZ());
    }

    private static boolean hasZ(CoordinateSequence sequence) {
        if (!sequence.hasZ()) {
            return false;
        }
        for (int i = 0, n = sequence.size(); i < n; i++) {
            if (!Double.isNaN(sequence.getZ(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exact size of the WKB that JTS {@link WKBWriter} produces for a geometry.
     */
    static int wkbSize(Geometry geometry, int dimension) {
        long size = wkbSizeLong(geometry, 8L * dimension);
        if (size > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException("Geometry too large for WKB encoding: " + size + " bytes");
        }
        return (int) size;
    }

    private static long wkbSizeLong(Geometry geometry, long coordinateSize) {
        if (geometry instanceof Point) {
            // an empty point is written as NaN ordinates
            return HEADER_LENGTH + coordinateSize;
        }
        if (geometry instanceof LineString line) {
            return HEADER_LENGTH + 4 + line.getNumPoints() * coordinateSize;
        }
        if (geometry instanceof Polygon polygon) {
            long size = HEADER_LENGTH + 4;
            if (polygon.isEmpty()) {
                return size;
            }
            size += 4 + polygon.getExteriorRing().getNumPoints() * coordinateSize;
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                size += 4 + polygon.getInteriorRingN(i).getNumPoints() * coordinateSize;
            }
            return size;
        }
        if (geometry instanceof GeometryCollection collection) {
            long size = HEADER_LENGTH + 4;
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                size += wkbSizeLong(collection.getGeometryN(i), coordinateSize);
            }
            return size;
        }
        throw new IllegalArgumentException("Unsupported geometry type: " + geometry.getGeometryType());
    }

    private static void writeWkb(Geometry geometry, int dimension, byte[] out, int offset) {
        ArrayOutStream stream = new ArrayOutStream(out, offset);
        try {
            new WKBWriter(dimension, ByteOrderValues.LITTLE_ENDIAN, false).write(geometry, stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (stream.position != out.length) {
            throw new IllegalStateException("WKB size mismatch for " + geometry.getGeometryType()
                + ": expected " + (out.length - offset) + " bytes, wrote " + (stream.position - offset));
        }
    }

    /**
     * {@link OutStream} writing into a pre-sized array.
     */
    private static final class ArrayOutStream implements OutStream {

        private final byte[] target;
        private int position;

        ArrayOutStream(byte[] target, int position) {
            this.target = target;
            this.position = position;
        }

        @Override
        public void write(byte[] buf, int len) {
            if (len > target.length - position) {
                throw new IllegalStateException("WKB output exceeds the computed size of " + target.length + " bytes");
            }
            System.arraycopy(buf, 0, target, position, len);
            position += len;
        }
    }

    // ==================== Hex ====================

    /**
     * Encode bytes as hex text.
     *
     * @param bytes     the bytes
     * @param upperCase true for {@code A-F}, false for {@code a-f}
     * @return the hex text
     */
    public static String toHex(byte[] bytes, boolean upperCase) {
        return (upperCase ? HEX_UPPER : HEX_LOWER).formatHex(bytes);
    }

    /**
     * Decode hex text (case-insensitive). A PostgreSQL bytea prefix {@code \x} is skipped.
     *
     * @param hex the hex text
     * @return the decoded bytes
     * @throws IllegalArgumentException if the text has odd length or contains a non-hex character
     */
    public static byte[] hexToBytes(CharSequence hex) {
        int start = hasByteaPrefix(hex) ? 2 : 0;
        int length = hex.length() - start;
        if ((length & 1) != 0) {
            throw new IllegalArgumentException("Hex string has odd length " + length + ": " + textPrefix(hex));
        }
        byte[] out = new byte[length >> 1];
        for (int i = 0, j = start; i < out.length; i++, j += 2) {
            int high = hexValue(hex.charAt(j));
            int low = hexValue(hex.charAt(j + 1));
            if ((high | low) < 0) {
                throw new IllegalArgumentException("Invalid hex character at index " + (high < 0 ? j : j + 1)
                    + ": " + textPrefix(hex));
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    /**
     * Decode bytes that hold ASCII hex text (optionally with a {@code \x} prefix), as returned by
     * {@code ResultSet.getBytes()} for a text column.
     *
     * @param ascii the ASCII bytes
     * @return the decoded bytes, or null when the input is not well-formed hex text
     */
    public static byte[] tryDecodeAsciiHex(byte[] ascii) {
        int start = ascii.length >= 2 && ascii[0] == '\\' && (ascii[1] == 'x' || ascii[1] == 'X') ? 2 : 0;
        int length = ascii.length - start;
        if (length == 0 || (length & 1) != 0) {
            return null;
        }
        byte[] out = new byte[length >> 1];
        for (int i = 0, j = start; i < out.length; i++, j += 2) {
            int high = hexValue(ascii[j]);
            int low = hexValue(ascii[j + 1]);
            if ((high | low) < 0) {
                return null;
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private static boolean hasByteaPrefix(CharSequence s) {
        return s.length() >= 2 && s.charAt(0) == '\\' && (s.charAt(1) == 'x' || s.charAt(1) == 'X');
    }

    private static int hexValue(int c) {
        return c >= 0 && c < 128 ? HEX_VALUES[c] : -1;
    }

    /**
     * Short hex rendering of the start of a value, for error messages.
     *
     * @param bytes the value
     * @return at most 16 bytes as hex, followed by "..." when truncated
     */
    public static String hexPrefix(byte[] bytes) {
        if (bytes.length <= PREFIX_BYTES) {
            return HEX_LOWER.formatHex(bytes);
        }
        return HEX_LOWER.formatHex(bytes, 0, PREFIX_BYTES) + "...";
    }

    /**
     * Short prefix of a text value, for error messages.
     *
     * @param text the value
     * @return at most 32 characters, followed by "..." when truncated
     */
    public static String textPrefix(CharSequence text) {
        int max = PREFIX_BYTES * 2;
        return text.length() <= max ? text.toString() : text.subSequence(0, max) + "...";
    }

    // ==================== Integers ====================

    private static int getInt(byte[] b, int offset, boolean littleEndian) {
        return littleEndian ? getIntLE(b, offset) : getIntBE(b, offset);
    }

    private static int getIntLE(byte[] b, int offset) {
        return (b[offset] & 0xFF) | (b[offset + 1] & 0xFF) << 8 | (b[offset + 2] & 0xFF) << 16
            | (b[offset + 3] & 0xFF) << 24;
    }

    private static int getIntBE(byte[] b, int offset) {
        return (b[offset] & 0xFF) << 24 | (b[offset + 1] & 0xFF) << 16 | (b[offset + 2] & 0xFF) << 8
            | (b[offset + 3] & 0xFF);
    }

    private static void putIntLE(byte[] b, int offset, int value) {
        b[offset] = (byte) value;
        b[offset + 1] = (byte) (value >>> 8);
        b[offset + 2] = (byte) (value >>> 16);
        b[offset + 3] = (byte) (value >>> 24);
    }
}
