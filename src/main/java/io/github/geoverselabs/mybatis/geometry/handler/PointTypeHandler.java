package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Point;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS Point geometry.
 * Converts between JTS Point objects and database GEOMETRY columns through the configured
 * {@link GeometryHandlerStrategy}.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @PointTableField
 * private Point location;
 * }</pre>
 *
 * @see AbstractGeometryTypeHandler
 */
@MappedTypes(Point.class)
public class PointTypeHandler extends AbstractGeometryTypeHandler<Point> {

    /**
     * Create a handler that uses the globally configured default SRID and strategy, resolved on
     * every call.
     */
    public PointTypeHandler() {
        super();
    }

    /**
     * Create a new PointTypeHandler with specified default SRID.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     */
    public PointTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new PointTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     * @param strategy the database-specific geometry handler strategy
     */
    public PointTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Declared with the concrete type so that subclasses compiled against 1.0.x, whose
     * {@code super} calls use this signature, keep linking.</p>
     */
    @Override
    protected void validateGeometry(Point geometry) throws SQLException {
        super.validateGeometry(geometry);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated see {@link AbstractGeometryTypeHandler#parseGeometry(String)}; declared with the
     *     concrete type for subclasses compiled against 1.0.x.
     */
    @Deprecated
    @Override
    protected Point parseGeometry(String hexString) {
        return super.parseGeometry(hexString);
    }
}
