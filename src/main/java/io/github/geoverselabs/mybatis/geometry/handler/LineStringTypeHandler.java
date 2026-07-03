package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.LineString;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS LineString geometry.
 * Converts between JTS LineString objects and database GEOMETRY columns using WKB format.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @LineStringTableField
 * private LineString route;
 * }</pre>
 */
@MappedTypes(LineString.class)
public class LineStringTypeHandler extends AbstractGeometryTypeHandler<LineString> {

    /**
     * Create a new LineStringTypeHandler with default SRID (4326).
     */
    public LineStringTypeHandler() {
        super();
    }

    /**
     * Create a new LineStringTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public LineStringTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new LineStringTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public LineStringTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected LineString parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkbAsLineString(hexString);
    }

    @Override
    protected void validateGeometry(LineString lineString) throws SQLException {
        if (!lineString.isValid()) {
            throw new SQLException("Invalid LineString geometry");
        }

        if (lineString.getNumPoints() < 2) {
            throw new SQLException("Invalid LineString geometry: must have at least 2 points");
        }

        // Check for NaN or infinite coordinates
        for (var coordinate : lineString.getCoordinates()) {
            if (Double.isNaN(coordinate.x) || Double.isInfinite(coordinate.x)) {
                throw new SQLException("Invalid LineString geometry: X coordinate is " +
                    (Double.isNaN(coordinate.x) ? "NaN" : "infinite"));
            }
            if (Double.isNaN(coordinate.y) || Double.isInfinite(coordinate.y)) {
                throw new SQLException("Invalid LineString geometry: Y coordinate is " +
                    (Double.isNaN(coordinate.y) ? "NaN" : "infinite"));
            }
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "LineString";
    }
}
