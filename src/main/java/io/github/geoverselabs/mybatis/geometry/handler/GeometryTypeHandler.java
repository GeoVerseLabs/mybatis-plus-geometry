package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Geometry;

/**
 * MyBatis TypeHandler for JTS Geometry (generic/wildcard).
 * Converts between JTS Geometry objects and database GEOMETRY columns through the configured
 * {@link GeometryHandlerStrategy}.
 *
 * <p>Unlike the specific type handlers (PointTypeHandler, etc.), this handler accepts any
 * geometry type.</p>
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @GeometryTableField
 * private Geometry shape;
 * }</pre>
 *
 * @see AbstractGeometryTypeHandler
 */
@MappedTypes(Geometry.class)
public class GeometryTypeHandler extends AbstractGeometryTypeHandler<Geometry> {

    /**
     * Create a handler that uses the globally configured default SRID and strategy, resolved on
     * every call.
     */
    public GeometryTypeHandler() {
        super();
    }

    /**
     * Create a new GeometryTypeHandler with specified default SRID.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     */
    public GeometryTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new GeometryTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     * @param strategy the database-specific geometry handler strategy
     */
    public GeometryTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }
}
