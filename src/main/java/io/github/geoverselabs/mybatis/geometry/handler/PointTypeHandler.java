package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Point;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS Point geometry.
 * Converts between JTS Point objects and database GEOMETRY columns using WKB format.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @PointTableField
 * private Point location;
 * }</pre>
 */
@MappedTypes(Point.class)
public class PointTypeHandler extends AbstractGeometryTypeHandler<Point> {

    /**
     * Create a new PointTypeHandler with default SRID (4326).
     */
    public PointTypeHandler() {
        super();
    }

    /**
     * Create a new PointTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public PointTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new PointTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public PointTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected Point parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkbAsPoint(hexString);
    }

    @Override
    protected void validateGeometry(Point point) throws SQLException {
        if (!point.isValid()) {
            throw new SQLException("Invalid Point geometry: coordinates may be NaN or infinite");
        }

        double x = point.getX();
        double y = point.getY();

        if (Double.isNaN(x) || Double.isInfinite(x)) {
            throw new SQLException("Invalid Point geometry: X coordinate is " +
                (Double.isNaN(x) ? "NaN" : "infinite"));
        }

        if (Double.isNaN(y) || Double.isInfinite(y)) {
            throw new SQLException("Invalid Point geometry: Y coordinate is " +
                (Double.isNaN(y) ? "NaN" : "infinite"));
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "Point";
    }
}
