package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.MultiLineString;

/**
 * MyBatis TypeHandler for JTS MultiLineString geometry.
 * Converts between JTS MultiLineString objects and database GEOMETRY columns through the configured
 * {@link GeometryHandlerStrategy}.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @MultiLineStringTableField
 * private MultiLineString routes;
 * }</pre>
 *
 * @see AbstractGeometryTypeHandler
 */
@MappedTypes(MultiLineString.class)
public class MultiLineStringTypeHandler extends AbstractGeometryTypeHandler<MultiLineString> {

    /**
     * Create a handler that uses the globally configured default SRID and strategy, resolved on
     * every call.
     */
    public MultiLineStringTypeHandler() {
        super();
    }

    /**
     * Create a new MultiLineStringTypeHandler with specified default SRID.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     */
    public MultiLineStringTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new MultiLineStringTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     * @param strategy the database-specific geometry handler strategy
     */
    public MultiLineStringTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }
}
