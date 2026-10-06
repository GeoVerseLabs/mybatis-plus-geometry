package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.CoordinateSequences;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Streaming GeoJSON geometry reader shared by every deserializer of this package (internal API).
 *
 * <p>The reader makes a single pass over the parser's tokens; it never builds a {@code JsonNode}
 * tree and never re-parses input. Because RFC 7946 does not fix the order of object members,
 * {@code "coordinates"} may appear before {@code "type"}: coordinates are therefore parsed into a
 * small depth-aware intermediate structure of primitive {@code double[]} position lists, and the
 * JTS geometry is built once the whole object has been read.</p>
 *
 * <p>Rules applied:</p>
 * <ul>
 *   <li>Positions are arrays of at least two JSON numbers. A third number is the altitude and is
 *       kept (dimension-3 sequences; positions without altitude in a 3D geometry get Z = NaN);
 *       further numbers are ignored. Any non-number inside a position is rejected.</li>
 *   <li>Ordinates must be finite; when {@link GeoJsonOptions#coordinateRangeValidation()} is set,
 *       x/y must also be within the WGS84 longitude/latitude ranges.</li>
 *   <li>LineStrings need at least 2 points; polygon rings at least 4 points and must be closed.
 *       Polygon and MultiPolygon rings are normalised to the RFC 7946 right-hand rule (exterior
 *       counter-clockwise, holes clockwise).</li>
 *   <li>{@code "coordinates": []} / {@code "geometries": []} produce empty geometries.</li>
 *   <li>Nested GeometryCollections are supported up to {@link #MAX_COLLECTION_DEPTH} levels.</li>
 *   <li>{@link GeometryValidation#FULL} additionally requires polygons and multipolygons to be
 *       valid according to OGC rules.</li>
 *   <li>Unknown members ({@code bbox}, {@code crs}, type ids written by Jackson polymorphic
 *       handling, ...) are skipped.</li>
 * </ul>
 *
 * <p>Instances are single-use and not thread-safe; use the static {@code read} methods.</p>
 */
final class GeoJsonGeometryReader {

    /** Maximum nesting depth of GeometryCollections inside GeometryCollections. */
    static final int MAX_COLLECTION_DEPTH = 64;

    /** Deepest array nesting a valid {@code coordinates} member can have (MultiPolygon). */
    private static final int MAX_COORDINATE_DEPTH = 4;

    /**
     * {@code JsonParser.Feature.USE_FAST_DOUBLE_PARSER}, or null when the Jackson version on the
     * classpath predates it (2.14). Resolved by name so older Jackson versions still work.
     */
    static final JsonParser.Feature FAST_DOUBLE_PARSER = resolveParserFeature("USE_FAST_DOUBLE_PARSER");

    private final JsonParser parser;
    private final GeometryFactory factory;
    private final boolean rangeValidation;
    private final boolean fullValidation;

    /** Set while parsing a {@code coordinates} member when any position carries an altitude. */
    private boolean altitudeSeen;

    private GeoJsonGeometryReader(JsonParser parser, GeoJsonOptions options, GeometryFactory factory) {
        this.parser = parser;
        this.factory = factory;
        this.rangeValidation = options.coordinateRangeValidation();
        this.fullValidation = options.validation() == GeometryValidation.FULL;
    }

    /**
     * Read one GeoJSON geometry using {@link GeometryFactoryProvider#getFactory()}.
     *
     * @param parser  parser positioned at the geometry's {@code START_OBJECT}, at a
     *                {@code FIELD_NAME} inside it, or at {@code VALUE_NULL}; a parser without a
     *                current token is advanced once
     * @param target  the requested geometry type; the GeoJSON type must be assignable to it
     * @param options options to apply
     * @param <T>     geometry type
     * @return the geometry, or null for a JSON null
     * @throws IOException on invalid input ({@link GeoJsonParseException},
     *                     {@link InvalidCoordinateException}) or parser failure
     */
    static <T extends Geometry> T read(JsonParser parser, Class<T> target, GeoJsonOptions options)
            throws IOException {
        return read(parser, target, options, GeometryFactoryProvider.getFactory(), FAST_DOUBLE_PARSER);
    }

    /**
     * Read one GeoJSON geometry with an explicit factory and fast-double-parser feature.
     *
     * @param parser            parser positioned as described in {@link #read(JsonParser, Class, GeoJsonOptions)}
     * @param target            the requested geometry type
     * @param options           options to apply
     * @param factory           factory creating the geometries (its SRID and coordinate sequence
     *                          factory are honoured)
     * @param fastDoubleFeature the parser feature to enable when
     *                          {@link GeoJsonOptions#fastDoubleParsing()} is set; null when
     *                          unavailable
     * @param <T>               geometry type
     * @return the geometry, or null for a JSON null
     * @throws IOException on invalid input or parser failure
     */
    static <T extends Geometry> T read(JsonParser parser, Class<T> target, GeoJsonOptions options,
                                       GeometryFactory factory, JsonParser.Feature fastDoubleFeature)
            throws IOException {
        JsonToken first = parser.currentToken();
        if (first == null) {
            // A parser that has not been advanced yet (tolerated by earlier versions, which read
            // through ObjectCodec.readTree).
            first = parser.nextToken();
        }
        if (first == JsonToken.VALUE_NULL) {
            return null;
        }
        // Delegating parsers (for example JsonParserSequence) may switch the underlying parser
        // while we read, so a toggled feature could not be restored reliably: leave them alone.
        boolean toggle = options.fastDoubleParsing()
            && fastDoubleFeature != null
            && !(parser instanceof JsonParserDelegate)
            && !parser.isEnabled(fastDoubleFeature);
        if (toggle) {
            parser.enable(fastDoubleFeature);
        }
        try {
            Geometry geometry = new GeoJsonGeometryReader(parser, options, factory)
                .readGeometry(target, 0, -1);
            return target.cast(geometry);
        } finally {
            if (toggle) {
                parser.disable(fastDoubleFeature);
            }
        }
    }

    /**
     * Resolve a {@link JsonParser.Feature} by name.
     *
     * @param name the enum constant name
     * @return the feature, or null when this Jackson version does not define it
     */
    static JsonParser.Feature resolveParserFeature(String name) {
        try {
            return JsonParser.Feature.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Object level
    // ------------------------------------------------------------------------------------------

    /**
     * Read a geometry object. The current token is START_OBJECT, a FIELD_NAME inside the object
     * (Jackson hands over mid-object after consuming a polymorphic type id) or END_OBJECT.
     * On return the current token is the object's END_OBJECT.
     */
    private Geometry readGeometry(Class<? extends Geometry> target, int depth, int memberIndex)
            throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            token = parser.nextToken();
        } else if (token != JsonToken.FIELD_NAME && token != JsonToken.END_OBJECT) {
            throw new GeoJsonParseException(parser,
                "Invalid GeoJSON geometry: expected a JSON object but got " + describe(token), "type");
        }

        String typeName = null;
        GeoJsonType type = null;
        Node coordinates = null;
        int dimension = 2;
        List<Geometry> geometries = null;

        for (; token == JsonToken.FIELD_NAME; token = parser.nextToken()) {
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (name) {
                case "type" -> {
                    if (value != JsonToken.VALUE_STRING) {
                        throw new GeoJsonParseException(parser,
                            "Invalid 'type' field: expected a string but got " + describe(value), "type");
                    }
                    typeName = parser.getText();
                    type = GeoJsonType.of(typeName);
                    checkType(type, typeName, target, memberIndex);
                }
                case "coordinates" -> {
                    if (type == GeoJsonType.GEOMETRY_COLLECTION) {
                        parser.skipChildren(); // foreign member of a collection
                    } else if (value == JsonToken.START_ARRAY) {
                        altitudeSeen = false;
                        coordinates = readArrayBody(parser.nextToken(), 1);
                        dimension = altitudeSeen ? 3 : 2;
                    } else if (type != null) {
                        throw new GeoJsonParseException(parser,
                            "Missing or invalid 'coordinates' field", "coordinates");
                    } else {
                        // type not known yet: reported as missing when the object turns out to need it
                        parser.skipChildren();
                        coordinates = null;
                    }
                }
                case "geometries" -> {
                    if (type != null && type != GeoJsonType.GEOMETRY_COLLECTION) {
                        parser.skipChildren(); // foreign member of a single geometry
                    } else if (value == JsonToken.START_ARRAY) {
                        geometries = readGeometries(depth);
                    } else if (type != null) {
                        throw new GeoJsonParseException(parser,
                            "Missing or invalid 'geometries' field", "geometries");
                    } else {
                        parser.skipChildren();
                        geometries = null;
                    }
                }
                default -> parser.skipChildren();
            }
        }
        if (token != JsonToken.END_OBJECT) {
            throw new GeoJsonParseException(parser,
                "Invalid GeoJSON geometry: expected a field name or end of object but got "
                    + describe(token), "type");
        }

        if (typeName == null) {
            throw new GeoJsonParseException(parser,
                memberIndex >= 0 ? "Missing 'type' field in geometry at index " + memberIndex
                    : "Missing 'type' field", "type");
        }

        try {
            return build(type, coordinates, dimension, geometries);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            // JTS rejected the structure in a way not covered by the checks above
            throw new GeoJsonParseException(parser,
                "Invalid " + typeName + " geometry: " + e.getMessage(), "coordinates", e);
        }
    }

    /**
     * Fail fast (before any coordinate is parsed) when the GeoJSON type cannot produce an
     * instance of the target class. Each GeoJSON type maps to exactly one JTS class, so
     * {@code target.isAssignableFrom(jtsClass)} is equivalent to {@code target.isInstance(result)}:
     * a GeometryCollection target accepts MultiPoint, MultiLineString and MultiPolygon.
     */
    private void checkType(GeoJsonType type, String typeName, Class<? extends Geometry> target,
                           int memberIndex) throws IOException {
        if (type == null) {
            if (memberIndex >= 0) {
                throw new GeoJsonParseException(parser,
                    "Unsupported geometry type in GeometryCollection: " + typeName, "type");
            }
            if (target == Geometry.class) {
                throw new GeoJsonParseException(parser, "Unsupported GeoJSON type: " + typeName, "type");
            }
            throw GeoJsonParseException.forTypeMismatch(parser, target.getSimpleName(), typeName);
        }
        if (!target.isAssignableFrom(type.jtsClass)) {
            throw GeoJsonParseException.forTypeMismatch(parser, target.getSimpleName(), typeName);
        }
    }

    /** Read the members of a {@code geometries} array whose START_ARRAY is the current token. */
    private List<Geometry> readGeometries(int depth) throws IOException {
        if (depth >= MAX_COLLECTION_DEPTH) {
            throw new GeoJsonParseException(parser,
                "GeometryCollection nesting exceeds the maximum depth of " + MAX_COLLECTION_DEPTH, "geometries");
        }
        List<Geometry> members = new ArrayList<>();
        int index = 0;
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
            if (token != JsonToken.START_OBJECT) {
                throw new GeoJsonParseException(parser,
                    "Invalid geometry at index " + index + " in 'geometries': expected a JSON object but got "
                        + describe(token), "geometries");
            }
            members.add(readGeometry(Geometry.class, depth + 1, index));
            index++;
        }
        return members;
    }

    // ------------------------------------------------------------------------------------------
    // Coordinates parsing (type agnostic)
    // ------------------------------------------------------------------------------------------

    /**
     * Parse the content of an array whose START_ARRAY has been consumed; {@code first} is the
     * first token inside it (already current). On return the current token is the array's
     * END_ARRAY.
     *
     * @param depth array nesting depth of this array (the {@code coordinates} value is depth 1)
     */
    private Node readArrayBody(JsonToken first, int depth) throws IOException {
        if (depth > MAX_COORDINATE_DEPTH) {
            throw new GeoJsonParseException(parser,
                "Invalid 'coordinates': arrays nested deeper than " + MAX_COORDINATE_DEPTH + " levels",
                "coordinates");
        }
        if (first == JsonToken.END_ARRAY) {
            return Node.EMPTY;
        }
        if (isOrdinate(first)) {
            PositionList single = new PositionList(1);
            readPosition(single, first, -1);
            return Node.position(single);
        }
        if (first != JsonToken.START_ARRAY) {
            throw new GeoJsonParseException(parser,
                "Invalid coordinate value: expected a number or an array but got " + describe(first),
                "coordinates");
        }

        JsonToken childFirst = parser.nextToken();
        if (isOrdinate(childFirst)) {
            // An array of positions: read them straight into one primitive list.
            if (depth + 1 > MAX_COORDINATE_DEPTH) {
                throw new GeoJsonParseException(parser,
                    "Invalid 'coordinates': arrays nested deeper than " + MAX_COORDINATE_DEPTH + " levels",
                    "coordinates");
            }
            PositionList list = new PositionList(8);
            readPosition(list, childFirst, 0);
            int index = 1;
            for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY;
                 token = parser.nextToken(), index++) {
                if (token != JsonToken.START_ARRAY) {
                    throw new GeoJsonParseException(parser,
                        "Invalid coordinate pair at index " + index + ": expected an array of numbers but got "
                            + describe(token), "coordinates");
                }
                JsonToken positionFirst = parser.nextToken();
                if (!isOrdinate(positionFirst)) {
                    throw positionError(index, positionFirst);
                }
                readPosition(list, positionFirst, index);
            }
            return Node.positions(list);
        }

        // An array of arrays.
        List<Node> children = new ArrayList<>(4);
        children.add(readArrayBody(childFirst, depth + 1));
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
            if (token != JsonToken.START_ARRAY) {
                throw new GeoJsonParseException(parser,
                    "Invalid coordinate array: expected an array but got " + describe(token), "coordinates");
            }
            children.add(readArrayBody(parser.nextToken(), depth + 1));
        }
        return Node.nested(children);
    }

    /**
     * Read one position whose first number is the current token; on return the current token
     * is the position's END_ARRAY.
     *
     * @param index the position's index in its list, or -1 for a single position (Point)
     */
    private void readPosition(PositionList target, JsonToken first, int index) throws IOException {
        double x = ordinate(first, index);
        JsonToken token = parser.nextToken();
        if (!isOrdinate(token)) {
            throw positionError(index, token);
        }
        double y = ordinate(token, index);
        double z = Double.NaN;
        boolean hasZ = false;
        token = parser.nextToken();
        if (token != JsonToken.END_ARRAY) {
            if (isNumber(token)) {
                z = parser.getDoubleValue();
                hasZ = true;
            } else if (token == JsonToken.VALUE_STRING && isDecimal(parser.getText().trim())) {
                z = new BigDecimal(parser.getText().trim()).doubleValue();
                hasZ = true;
            } else {
                // 1.0.x ignored everything after the second element: any other altitude means "no Z"
                parser.skipChildren();
            }
            // Further elements (for example a measure) are ignored.
            for (token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
                if (token == null) {
                    throw new GeoJsonParseException(parser, "Unexpected end of input in a position", "coordinates");
                }
                parser.skipChildren();
            }
        }
        validateOrdinates(x, y, z, hasZ);
        if (hasZ) {
            altitudeSeen = true;
        }
        target.add(x, y, z, hasZ);
    }

    /**
     * The value of an ordinate token: a JSON number, or a string holding a decimal number, which
     * 1.0.x accepted (e.g. {@code ["116.4", "39.9"]}). Other strings are rejected.
     */
    private double ordinate(JsonToken token, int index) throws IOException {
        if (token != JsonToken.VALUE_STRING) {
            return parser.getDoubleValue();
        }
        String text = parser.getText().trim();
        try {
            // BigDecimal accepts plain decimal notation only (no NaN, Infinity, hex or 'd'/'f' suffixes)
            return new BigDecimal(text).doubleValue();
        } catch (NumberFormatException e) {
            String where = index >= 0 ? "Invalid coordinate pair at index " + index : "Invalid position";
            throw new GeoJsonParseException(parser,
                where + ": expected a number but got the string \"" + abbreviate(text) + "\"", "coordinates");
        }
    }

    private static boolean isDecimal(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 32 ? text : text.substring(0, 32) + "...";
    }

    private void validateOrdinates(double x, double y, double z, boolean hasZ) throws IOException {
        if (rangeValidation) {
            // Negated comparisons so that NaN is rejected as well.
            if (!(x >= -180 && x <= 180)) {
                throw InvalidCoordinateException.forLongitude(parser, x);
            }
            if (!(y >= -90 && y <= 90)) {
                throw InvalidCoordinateException.forLatitude(parser, y);
            }
        } else {
            if (!Double.isFinite(x)) {
                throw new InvalidCoordinateException(parser, "longitude", x,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            if (!Double.isFinite(y)) {
                throw new InvalidCoordinateException(parser, "latitude", y,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
        }
        if (hasZ && !Double.isFinite(z)) {
            throw new InvalidCoordinateException(parser, "altitude", z,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
    }

    private GeoJsonParseException positionError(int index, JsonToken token) {
        String where = index >= 0 ? "Invalid coordinate pair at index " + index : "Invalid position";
        if (token == JsonToken.END_ARRAY) {
            return new GeoJsonParseException(parser,
                index >= 0 ? where + ": a position must have at least 2 numbers"
                    : "Coordinates array must have at least 2 elements", "coordinates");
        }
        return new GeoJsonParseException(parser,
            where + ": expected a number but got " + describe(token), "coordinates");
    }

    // ------------------------------------------------------------------------------------------
    // Geometry building
    // ------------------------------------------------------------------------------------------

    private Geometry build(GeoJsonType type, Node coordinates, int dimension, List<Geometry> geometries)
            throws IOException {
        if (type == GeoJsonType.GEOMETRY_COLLECTION) {
            if (geometries == null) {
                throw new GeoJsonParseException(parser, "Missing or invalid 'geometries' field", "geometries");
            }
            return factory.createGeometryCollection(geometries.toArray(new Geometry[0]));
        }
        if (coordinates == null) {
            throw new GeoJsonParseException(parser, "Missing or invalid 'coordinates' field", "coordinates");
        }
        return switch (type) {
            case POINT -> buildPoint(coordinates, dimension);
            case LINE_STRING -> buildLineString(coordinates, dimension);
            case POLYGON -> validated(buildPolygon(coordinates, dimension, -1), "polygon");
            case MULTI_POINT -> buildMultiPoint(coordinates, dimension);
            case MULTI_LINE_STRING -> buildMultiLineString(coordinates, dimension);
            case MULTI_POLYGON -> validated(buildMultiPolygon(coordinates, dimension), "multipolygon");
            default -> throw new IllegalStateException("Unhandled GeoJSON type " + type);
        };
    }

    private Point buildPoint(Node node, int dimension) throws IOException {
        if (node.kind == Node.EMPTY_KIND) {
            return factory.createPoint();
        }
        if (node.kind != Node.POSITION_KIND) {
            throw structureError("Point", "a single position [x, y]");
        }
        return factory.createPoint(sequence(node.positions, 0, 1, dimension));
    }

    private LineString buildLineString(Node node, int dimension) throws IOException {
        if (node.kind == Node.EMPTY_KIND) {
            return factory.createLineString();
        }
        if (node.kind != Node.POSITIONS_KIND) {
            throw structureError("LineString", "an array of positions");
        }
        if (node.positions.size < 2) {
            throw new GeoJsonParseException(parser, "LineString must have at least 2 points", "coordinates");
        }
        return factory.createLineString(sequence(node.positions, 0, node.positions.size, dimension));
    }

    private MultiPoint buildMultiPoint(Node node, int dimension) throws IOException {
        if (node.kind == Node.EMPTY_KIND) {
            return factory.createMultiPoint(new Point[0]);
        }
        if (node.kind != Node.POSITIONS_KIND) {
            throw structureError("MultiPoint", "an array of positions");
        }
        PositionList list = node.positions;
        Point[] points = new Point[list.size];
        for (int i = 0; i < points.length; i++) {
            points[i] = factory.createPoint(sequence(list, i, 1, dimension));
        }
        return factory.createMultiPoint(points);
    }

    private MultiLineString buildMultiLineString(Node node, int dimension) throws IOException {
        if (node.kind == Node.EMPTY_KIND) {
            return factory.createMultiLineString(new LineString[0]);
        }
        if (node.kind != Node.NESTED_KIND) {
            throw structureError("MultiLineString", "an array of LineString coordinate arrays");
        }
        LineString[] lines = new LineString[node.children.size()];
        for (int i = 0; i < lines.length; i++) {
            Node child = node.children.get(i);
            if (child.kind != Node.POSITIONS_KIND && child.kind != Node.EMPTY_KIND) {
                throw new GeoJsonParseException(parser, "Invalid coordinate array at index " + i, "coordinates");
            }
            if (child.kind == Node.EMPTY_KIND || child.positions.size < 2) {
                throw new GeoJsonParseException(parser,
                    "LineString at index " + i + " must have at least 2 points", "coordinates");
            }
            lines[i] = factory.createLineString(sequence(child.positions, 0, child.positions.size, dimension));
        }
        return factory.createMultiLineString(lines);
    }

    /**
     * @param polygonIndex index inside a MultiPolygon, or -1 for a Polygon
     */
    private Polygon buildPolygon(Node node, int dimension, int polygonIndex) throws IOException {
        if (node.kind == Node.EMPTY_KIND) {
            if (polygonIndex >= 0) {
                throw new GeoJsonParseException(parser,
                    "Invalid polygon coordinate array at index " + polygonIndex, "coordinates");
            }
            return factory.createPolygon();
        }
        if (node.kind != Node.NESTED_KIND) {
            if (polygonIndex >= 0) {
                throw new GeoJsonParseException(parser,
                    "Invalid polygon coordinate array at index " + polygonIndex, "coordinates");
            }
            throw structureError("Polygon", "an array of linear rings");
        }
        List<Node> rings = node.children;
        LinearRing shell = buildRing(rings.get(0), dimension, true);
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++) {
            holes[i - 1] = buildRing(rings.get(i), dimension, false);
        }
        return factory.createPolygon(shell, holes);
    }

    private MultiPolygon buildMultiPolygon(Node node, int dimension) throws IOException {
        if (node.kind == Node.EMPTY_KIND) {
            return factory.createMultiPolygon(new Polygon[0]);
        }
        if (node.kind != Node.NESTED_KIND) {
            throw structureError("MultiPolygon", "an array of Polygon coordinate arrays");
        }
        Polygon[] polygons = new Polygon[node.children.size()];
        for (int i = 0; i < polygons.length; i++) {
            polygons[i] = buildPolygon(node.children.get(i), dimension, i);
        }
        return factory.createMultiPolygon(polygons);
    }

    private LinearRing buildRing(Node node, int dimension, boolean exterior) throws IOException {
        if (node.kind != Node.POSITIONS_KIND && node.kind != Node.EMPTY_KIND) {
            throw new GeoJsonParseException(parser,
                "Invalid coordinate array: a polygon ring must be an array of positions", "coordinates");
        }
        PositionList ring = node.positions;
        if (node.kind == Node.EMPTY_KIND || ring.size < 4) {
            throw new GeoJsonParseException(parser,
                "Invalid coordinate array: a polygon ring must have at least 4 points", "coordinates");
        }
        int last = ring.size - 1;
        if (ring.x(0) != ring.x(last) || ring.y(0) != ring.y(last)) {
            throw new GeoJsonParseException(parser,
                String.format(Locale.ROOT, "Invalid ring: first point (%f,%f) != last point (%f,%f)",
                    ring.x(0), ring.y(0), ring.x(last), ring.y(last)),
                "coordinates");
        }
        CoordinateSequence sequence = sequence(ring, 0, ring.size, dimension);
        // RFC 7946 right-hand rule: exterior rings counter-clockwise, holes clockwise.
        if (Orientation.isCCW(sequence) != exterior) {
            CoordinateSequences.reverse(sequence);
        }
        return factory.createLinearRing(sequence);
    }

    private <G extends Geometry> G validated(G geometry, String kind) throws IOException {
        if (fullValidation) {
            TopologyValidationError error = new IsValidOp(geometry).getValidationError();
            if (error != null) {
                throw new GeoJsonParseException(parser,
                    "Invalid " + kind + " geometry: not valid according to OGC rules (" + error + ")",
                    "coordinates");
            }
        }
        return geometry;
    }

    private GeoJsonParseException structureError(String type, String expected) {
        return new GeoJsonParseException(parser,
            "Invalid 'coordinates' for " + type + ": expected " + expected, "coordinates");
    }

    /**
     * Create a coordinate sequence for {@code count} positions of {@code list} starting at
     * {@code from}, honouring the factory's coordinate sequence implementation.
     */
    private CoordinateSequence sequence(PositionList list, int from, int count, int dimension) {
        CoordinateSequenceFactory sequenceFactory = factory.getCoordinateSequenceFactory();
        if (sequenceFactory instanceof PackedCoordinateSequenceFactory packed) {
            return packed.create(list.packed(from, count, dimension), dimension);
        }
        if (sequenceFactory instanceof CoordinateArraySequenceFactory) {
            Coordinate[] coordinates = new Coordinate[count];
            for (int i = 0; i < count; i++) {
                int p = from + i;
                coordinates[i] = dimension == 3
                    ? new Coordinate(list.x(p), list.y(p), list.z(p))
                    : new CoordinateXY(list.x(p), list.y(p));
            }
            return new CoordinateArraySequence(coordinates, dimension, 0);
        }
        CoordinateSequence sequence = sequenceFactory.create(count, dimension);
        for (int i = 0; i < count; i++) {
            int p = from + i;
            sequence.setOrdinate(i, CoordinateSequence.X, list.x(p));
            sequence.setOrdinate(i, CoordinateSequence.Y, list.y(p));
            if (dimension == 3 && sequence.getDimension() > 2) {
                sequence.setOrdinate(i, CoordinateSequence.Z, list.z(p));
            }
        }
        return sequence;
    }

    // ------------------------------------------------------------------------------------------
    // Helpers and intermediate structures
    // ------------------------------------------------------------------------------------------

    private static boolean isNumber(JsonToken token) {
        return token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT;
    }

    /** A token that may hold an ordinate: a number, or a string checked by {@link #ordinate}. */
    private static boolean isOrdinate(JsonToken token) {
        return isNumber(token) || token == JsonToken.VALUE_STRING;
    }

    private static String describe(JsonToken token) {
        if (token == null) {
            return "end of input";
        }
        return switch (token) {
            case VALUE_STRING -> "a string";
            case VALUE_TRUE, VALUE_FALSE -> "a boolean";
            case VALUE_NULL -> "null";
            case START_OBJECT -> "an object";
            case START_ARRAY -> "an array";
            case END_ARRAY -> "the end of an array";
            case END_OBJECT -> "the end of an object";
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> "a number";
            default -> token.name();
        };
    }

    /** GeoJSON geometry types and the JTS class each one produces. */
    enum GeoJsonType {
        POINT("Point", Point.class),
        LINE_STRING("LineString", LineString.class),
        POLYGON("Polygon", Polygon.class),
        MULTI_POINT("MultiPoint", MultiPoint.class),
        MULTI_LINE_STRING("MultiLineString", MultiLineString.class),
        MULTI_POLYGON("MultiPolygon", MultiPolygon.class),
        GEOMETRY_COLLECTION("GeometryCollection", GeometryCollection.class);

        final String geoJsonName;
        final Class<? extends Geometry> jtsClass;

        GeoJsonType(String geoJsonName, Class<? extends Geometry> jtsClass) {
            this.geoJsonName = geoJsonName;
            this.jtsClass = jtsClass;
        }

        static GeoJsonType of(String name) {
            for (GeoJsonType type : values()) {
                if (type.geoJsonName.equals(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Parsed {@code coordinates} array: empty, a single position, a list of positions, or a list
     * of nested arrays.
     */
    static final class Node {
        static final int EMPTY_KIND = 0;
        static final int POSITION_KIND = 1;
        static final int POSITIONS_KIND = 2;
        static final int NESTED_KIND = 3;

        static final Node EMPTY = new Node(EMPTY_KIND, null, null);

        final int kind;
        final PositionList positions;
        final List<Node> children;

        private Node(int kind, PositionList positions, List<Node> children) {
            this.kind = kind;
            this.positions = positions;
            this.children = children;
        }

        static Node position(PositionList single) {
            return new Node(POSITION_KIND, single, null);
        }

        static Node positions(PositionList list) {
            return new Node(POSITIONS_KIND, list, null);
        }

        static Node nested(List<Node> children) {
            return new Node(NESTED_KIND, null, children);
        }
    }

    /**
     * Growable list of positions backed by primitive arrays: interleaved x/y plus a lazily
     * allocated z array (NaN where a position has no altitude).
     */
    static final class PositionList {
        private double[] xy;
        private double[] z;
        int size;

        PositionList(int capacity) {
            this.xy = new double[Math.max(1, capacity) * 2];
        }

        void add(double x, double y, double zValue, boolean hasZ) {
            if (size * 2 == xy.length) {
                int capacity = xy.length; // doubles the number of positions
                xy = Arrays.copyOf(xy, capacity * 2);
                if (z != null) {
                    z = Arrays.copyOf(z, capacity);
                }
            }
            xy[size * 2] = x;
            xy[size * 2 + 1] = y;
            if (hasZ && z == null) {
                z = new double[xy.length / 2];
                Arrays.fill(z, Double.NaN);
            }
            if (z != null) {
                z[size] = hasZ ? zValue : Double.NaN;
            }
            size++;
        }

        double x(int index) {
            return xy[index * 2];
        }

        double y(int index) {
            return xy[index * 2 + 1];
        }

        double z(int index) {
            return z == null ? Double.NaN : z[index];
        }

        /** Packed ordinates of {@code count} positions from {@code from}, {@code dimension} per position. */
        double[] packed(int from, int count, int dimension) {
            if (dimension == 2) {
                if (from == 0 && count * 2 == xy.length) {
                    return xy;
                }
                return Arrays.copyOfRange(xy, from * 2, (from + count) * 2);
            }
            double[] data = new double[count * dimension];
            for (int i = 0; i < count; i++) {
                int p = from + i;
                data[i * dimension] = xy[p * 2];
                data[i * dimension + 1] = xy[p * 2 + 1];
                data[i * dimension + 2] = z(p);
            }
            return data;
        }
    }
}
