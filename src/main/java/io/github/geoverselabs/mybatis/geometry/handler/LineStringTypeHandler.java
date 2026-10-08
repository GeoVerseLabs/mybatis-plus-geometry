package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.LineString;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS LineString geometry.
 * Converts between JTS LineString objects and database GEOMETRY columns through the configured
 * {@link GeometryHandlerStrategy}.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @LineStringTableField
 * private LineString route;
 * }</pre>
 *
 * @see AbstractGeometryTypeHandler
 */
@MappedTypes(LineString.class)
public class LineStringTypeHandler extends AbstractGeometryTypeHandler<LineString> {

    /**
     * Create a handler that uses the globally configured default SRID and strategy, resolved on
     * every call.
     */
    public LineStringTypeHandler() {
        super();
    }

    /**
     * Create a new LineStringTypeHandler with specified default SRID.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     */
    public LineStringTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new LineStringTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     * @param strategy the database-specific geometry handler strategy
     */
    public LineStringTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Declared with the concrete type so that subclasses compiled against 1.0.x, whose
     * {@code super} calls use this signature, keep linking.</p>
     */
    @Override
    protected void validateGeometry(LineString geometry) throws SQLException {
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
    protected LineString parseGeometry(String hexString) {
        return super.parseGeometry(hexString);
    }
}
