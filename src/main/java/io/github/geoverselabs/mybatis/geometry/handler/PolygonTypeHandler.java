package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Polygon;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS Polygon geometry.
 * Converts between JTS Polygon objects and database GEOMETRY columns using WKB format.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @PolygonTableField
 * private Polygon boundary;
 * }</pre>
 */
@MappedTypes(Polygon.class)
public class PolygonTypeHandler extends AbstractGeometryTypeHandler<Polygon> {

    /**
     * Create a new PolygonTypeHandler with default SRID (4326).
     */
    public PolygonTypeHandler() {
        super();
    }

    /**
     * Create a new PolygonTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public PolygonTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new PolygonTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public PolygonTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected Polygon parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkbAsPolygon(hexString);
    }

    @Override
    protected void validateGeometry(Polygon polygon) throws SQLException {
        if (!polygon.isValid()) {
            throw new SQLException("Invalid Polygon geometry: " + getValidationError(polygon));
        }

        // Check for NaN or infinite coordinates
        for (var coordinate : polygon.getCoordinates()) {
            if (Double.isNaN(coordinate.x) || Double.isInfinite(coordinate.x)) {
                throw new SQLException("Invalid Polygon geometry: X coordinate is " +
                    (Double.isNaN(coordinate.x) ? "NaN" : "infinite"));
            }
            if (Double.isNaN(coordinate.y) || Double.isInfinite(coordinate.y)) {
                throw new SQLException("Invalid Polygon geometry: Y coordinate is " +
                    (Double.isNaN(coordinate.y) ? "NaN" : "infinite"));
            }
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "Polygon";
    }

    private String getValidationError(Polygon polygon) {
        if (polygon.getExteriorRing().getNumPoints() < 4) {
            return "exterior ring must have at least 4 points";
        }
        if (!polygon.getExteriorRing().isClosed()) {
            return "exterior ring is not closed";
        }
        return "geometry is not valid according to OGC rules";
    }
}
