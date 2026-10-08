package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonGeneratorImpl;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GeoJSON geometry writer shared by every serializer of this package (internal API).
 *
 * <ul>
 *   <li>Dispatches on the runtime type: Point, LineString (and LinearRing, written as
 *       {@code "LineString"}), Polygon, MultiPoint, MultiLineString, MultiPolygon and
 *       GeometryCollection (Multi* are checked before GeometryCollection). {@code "type"} is
 *       always the first member written.</li>
 *   <li>Reads ordinates through {@link CoordinateSequence#getX(int)} and friends, never
 *       {@code getCoordinates()}, so no coordinate arrays are copied.</li>
 *   <li>Empty geometries are written as {@code "coordinates": []} ({@code "geometries": []} for
 *       collections); empty members of multi-geometries are skipped.</li>
 *   <li>Polygon rings follow the RFC 7946 right-hand rule (exterior counter-clockwise, holes
 *       clockwise); a ring stored the other way round is written backwards.</li>
 *   <li>A third ordinate is written when the sequence has Z and the value is finite.</li>
 *   <li>Non-finite x/y ordinates are rejected with a {@link JsonGenerationException}.</li>
 *   <li>With a coordinate precision, ordinates are rounded half-up and written without
 *       allocating (a reusable {@code char[]} and
 *       {@link JsonGenerator#writeNumber(char[], int, int)}); trailing zeros are trimmed.</li>
 * </ul>
 *
 * <p>Instances are cheap, single-use and not thread-safe.</p>
 */
final class GeoJsonGeometryWriter {

    /** 2^53: above this, {@code |v| * 10^p} can no longer be rounded exactly with doubles/longs. */
    private static final double TWO_POW_53 = 9007199254740992.0;

    private static final double[] POW10_DOUBLE = new double[GeoJsonOptions.MAX_PRECISION + 1];
    private static final long[] POW10_LONG = new long[GeoJsonOptions.MAX_PRECISION + 1];

    static {
        double d = 1;
        long l = 1;
        for (int i = 0; i <= GeoJsonOptions.MAX_PRECISION; i++) {
            POW10_DOUBLE[i] = d;
            POW10_LONG[i] = l;
            d *= 10;
            l *= 10;
        }
    }

    /** Large enough for a sign, 16 integer digits, a decimal point and 15 decimals. */
    private static final int BUFFER_SIZE = 40;

    private final JsonGenerator gen;
    private final int precision;
    private final char[] buffer;
    /** Textual generators (JSON) take the formatted digits; binary ones (CBOR, Smile) need a double. */
    private final boolean formattedNumbers;
    private String currentType;

    /**
     * @param gen       the generator to write to
     * @param precision decimal places, or null for full double precision
     */
    GeoJsonGeometryWriter(JsonGenerator gen, Integer precision) {
        this.gen = gen;
        this.precision = precision == null ? -1 : precision;
        this.buffer = precision == null ? null : new char[BUFFER_SIZE];
        // UTF8JsonGenerator reports canWriteFormattedNumbers() == false although it writes the digits
        // verbatim, so recognise every textual JSON generator by type
        this.formattedNumbers = precision != null
            && (gen instanceof JsonGeneratorImpl || gen.canWriteFormattedNumbers());
    }

    /**
     * Write {@code geometry} as a complete GeoJSON object.
     *
     * @param geometry  the geometry, not null
     * @param gen       the generator
     * @param precision decimal places, or null for full precision
     * @throws IOException on generator failure or unsupported/non-finite input
     */
    static void writeObject(Geometry geometry, JsonGenerator gen, Integer precision) throws IOException {
        new GeoJsonGeometryWriter(gen, precision).writeObject(geometry);
    }

    /**
     * Write the members ({@code "type"}, {@code "coordinates"} or {@code "geometries"}) of
     * {@code geometry} into an object that the caller has already started.
     *
     * @param geometry  the geometry, not null
     * @param gen       the generator
     * @param precision decimal places, or null for full precision
     * @throws IOException on generator failure or unsupported/non-finite input
     */
    static void writeMembers(Geometry geometry, JsonGenerator gen, Integer precision) throws IOException {
        new GeoJsonGeometryWriter(gen, precision).writeMembers(geometry);
    }

    void writeObject(Geometry geometry) throws IOException {
        gen.writeStartObject(geometry);
        writeMembers(geometry);
        gen.writeEndObject();
    }

    void writeMembers(Geometry geometry) throws IOException {
        if (geometry instanceof Point point) {
            begin("Point");
            if (point.isEmpty()) {
                emptyArray();
            } else {
                writePosition(point.getCoordinateSequence(), 0);
            }
        } else if (geometry instanceof LineString line) {
            begin("LineString");
            writePositions(line.getCoordinateSequence(), false);
        } else if (geometry instanceof Polygon polygon) {
            begin("Polygon");
            writePolygon(polygon);
        } else if (geometry instanceof MultiPoint multiPoint) {
            begin("MultiPoint");
            gen.writeStartArray();
            for (int i = 0; i < multiPoint.getNumGeometries(); i++) {
                Point point = (Point) multiPoint.getGeometryN(i);
                if (!point.isEmpty()) {
                    writePosition(point.getCoordinateSequence(), 0);
                }
            }
            gen.writeEndArray();
        } else if (geometry instanceof MultiLineString multiLine) {
            begin("MultiLineString");
            gen.writeStartArray();
            for (int i = 0; i < multiLine.getNumGeometries(); i++) {
                LineString line = (LineString) multiLine.getGeometryN(i);
                if (!line.isEmpty()) {
                    writePositions(line.getCoordinateSequence(), false);
                }
            }
            gen.writeEndArray();
        } else if (geometry instanceof MultiPolygon multiPolygon) {
            begin("MultiPolygon");
            gen.writeStartArray();
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
                if (!polygon.isEmpty()) {
                    writePolygon(polygon);
                }
            }
            gen.writeEndArray();
        } else if (geometry instanceof GeometryCollection collection) {
            gen.writeStringField("type", "GeometryCollection");
            gen.writeFieldName("geometries");
            gen.writeStartArray();
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                writeObject(collection.getGeometryN(i));
            }
            gen.writeEndArray();
        } else {
            throw new JsonGenerationException(
                "Unsupported geometry type for GeoJSON: "
                    + (geometry == null ? "null" : geometry.getGeometryType()), gen);
        }
    }

    private void begin(String type) throws IOException {
        currentType = type;
        gen.writeStringField("type", type);
        gen.writeFieldName("coordinates");
    }

    private void emptyArray() throws IOException {
        gen.writeStartArray();
        gen.writeEndArray();
    }

    /** Write a polygon's rings as an array ({@code []} when the polygon is empty). */
    private void writePolygon(Polygon polygon) throws IOException {
        gen.writeStartArray();
        if (!polygon.isEmpty()) {
            writeRing(polygon.getExteriorRing(), true);
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                LinearRing hole = polygon.getInteriorRingN(i);
                if (!hole.isEmpty()) {
                    writeRing(hole, false);
                }
            }
        }
        gen.writeEndArray();
    }

    private void writeRing(LinearRing ring, boolean exterior) throws IOException {
        CoordinateSequence sequence = ring.getCoordinateSequence();
        boolean reverse = sequence.size() >= 4 && Orientation.isCCW(sequence) != exterior;
        writePositions(sequence, reverse);
    }

    private void writePositions(CoordinateSequence sequence, boolean reverse) throws IOException {
        gen.writeStartArray();
        int size = sequence.size();
        if (reverse) {
            for (int i = size - 1; i >= 0; i--) {
                writePosition(sequence, i);
            }
        } else {
            for (int i = 0; i < size; i++) {
                writePosition(sequence, i);
            }
        }
        gen.writeEndArray();
    }

    private void writePosition(CoordinateSequence sequence, int index) throws IOException {
        gen.writeStartArray();
        writeOrdinate(sequence.getX(index), "x");
        writeOrdinate(sequence.getY(index), "y");
        if (sequence.hasZ()) {
            double z = sequence.getZ(index);
            if (Double.isFinite(z)) {
                writeOrdinate(z, "z");
            }
        }
        gen.writeEndArray();
    }

    private void writeOrdinate(double value, String axis) throws IOException {
        if (!Double.isFinite(value)) {
            throw new JsonGenerationException("Cannot write non-finite " + axis + " ordinate " + value
                + " of a " + currentType + " as GeoJSON", gen);
        }
        if (precision < 0) {
            gen.writeNumber(value);
            return;
        }
        if (!formattedNumbers) {
            // e.g. CBOR writes writeNumber(String) as a text string, which is not a GeoJSON number
            gen.writeNumber(roundedValue(value, precision));
            return;
        }
        int length = formatFixed(value, precision, buffer);
        if (length >= 0) {
            gen.writeNumber(buffer, 0, length);
        } else {
            gen.writeNumber(formatBig(value, precision));
        }
    }

    /**
     * Format a finite {@code value} rounded half-up (away from zero on ties) to {@code precision}
     * decimals into {@code buffer}, without trailing zeros and without a sign for zero.
     *
     * @param value     finite value
     * @param precision decimals, 0 to {@link GeoJsonOptions#MAX_PRECISION}
     * @param buffer    output buffer of at least 40 chars
     * @return the number of chars written, or -1 when {@code |value| * 10^precision} reaches
     *         2^53 and {@link #formatBig(double, int)} must be used instead
     */
    static int formatFixed(double value, int precision, char[] buffer) {
        double scaled = Math.abs(value) * POW10_DOUBLE[precision];
        if (!(scaled < TWO_POW_53)) {
            return -1;
        }
        double floor = Math.floor(scaled);
        long units = (long) floor;
        if (scaled - floor >= 0.5) {
            units++;
        }
        if (units == 0) {
            buffer[0] = '0';
            return 1;
        }
        int pos = 0;
        if (value < 0) {
            buffer[pos++] = '-';
        }
        long divisor = POW10_LONG[precision];
        long integerPart = units / divisor;
        long fraction = units % divisor;
        pos = writeDigits(integerPart, buffer, pos);
        if (fraction != 0) {
            int digits = precision;
            while (fraction % 10 == 0) {
                fraction /= 10;
                digits--;
            }
            buffer[pos++] = '.';
            for (int i = pos + digits - 1; i >= pos; i--) {
                buffer[i] = (char) ('0' + fraction % 10);
                fraction /= 10;
            }
            pos += digits;
        }
        return pos;
    }

    /**
     * The double nearest to {@code value} rounded half-up (away from zero on ties) to
     * {@code precision} decimals: the value whose shortest representation
     * {@link #formatFixed(double, int, char[])} writes. Used for binary formats.
     *
     * @param value     finite value
     * @param precision decimals, 0 to {@link GeoJsonOptions#MAX_PRECISION}
     * @return the rounded value; zero is returned without a sign
     */
    static double roundedValue(double value, int precision) {
        double scaled = Math.abs(value) * POW10_DOUBLE[precision];
        if (!(scaled < TWO_POW_53)) {
            return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP).doubleValue();
        }
        double floor = Math.floor(scaled);
        long units = (long) floor;
        if (scaled - floor >= 0.5) {
            units++;
        }
        if (units == 0) {
            return 0.0;
        }
        // units < 2^53 and 10^precision <= 10^15 are exact, so the quotient is correctly rounded
        double rounded = units / POW10_DOUBLE[precision];
        return value < 0 ? -rounded : rounded;
    }

    /**
     * Slow path of {@link #formatFixed(double, int, char[])} for magnitudes whose scaled value
     * exceeds 2^53: rounds the shortest decimal representation of {@code value} half-up.
     *
     * @param value     finite value
     * @param precision decimals
     * @return the plain decimal representation, without trailing zeros
     */
    static String formatBig(double value, int precision) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return "0";
        }
        return rounded.stripTrailingZeros().toPlainString();
    }

    private static int writeDigits(long value, char[] buffer, int pos) {
        if (value == 0) {
            buffer[pos] = '0';
            return pos + 1;
        }
        int digits = 0;
        for (long v = value; v != 0; v /= 10) {
            digits++;
        }
        for (int i = pos + digits - 1; i >= pos; i--) {
            buffer[i] = (char) ('0' + value % 10);
            value /= 10;
        }
        return pos + digits;
    }
}
