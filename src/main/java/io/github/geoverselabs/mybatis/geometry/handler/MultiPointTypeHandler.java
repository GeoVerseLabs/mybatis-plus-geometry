package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.MultiPoint;

/**
 * MyBatis TypeHandler for JTS MultiPoint geometry.
 * Converts between JTS MultiPoint objects and database GEOMETRY columns through the configured
 * {@link GeometryHandlerStrategy}.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @MultiPointTableField
 * private MultiPoint locations;
 * }</pre>
 *
 * @see AbstractGeometryTypeHandler
 */
@MappedTypes(MultiPoint.class)
public class MultiPointTypeHandler extends AbstractGeometryTypeHandler<MultiPoint> {

    /**
     * Create a handler that uses the globally configured default SRID and strategy, resolved on
     * every call.
     */
    public MultiPointTypeHandler() {
        super();
    }

    /**
     * Create a new MultiPointTypeHandler with specified default SRID.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     */
    public MultiPointTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new MultiPointTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     * @param strategy the database-specific geometry handler strategy
     */
    public MultiPointTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }
}
