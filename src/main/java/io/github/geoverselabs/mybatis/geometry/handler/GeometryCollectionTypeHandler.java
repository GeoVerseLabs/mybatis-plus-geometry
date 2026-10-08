package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.GeometryCollection;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS GeometryCollection geometry.
 * Converts between JTS GeometryCollection objects and database GEOMETRY columns through the configured
 * {@link GeometryHandlerStrategy}.
 *
 * <p>Multi-geometries (MultiPoint, MultiLineString, MultiPolygon) are GeometryCollections in JTS
 * and are accepted as well.</p>
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @GeometryCollectionTableField
 * private GeometryCollection geometryCollection;
 * }</pre>
 *
 * @see AbstractGeometryTypeHandler
 */
@MappedTypes(GeometryCollection.class)
public class GeometryCollectionTypeHandler extends AbstractGeometryTypeHandler<GeometryCollection> {

    /**
     * Create a handler that uses the globally configured default SRID and strategy, resolved on
     * every call.
     */
    public GeometryCollectionTypeHandler() {
        super();
    }

    /**
     * Create a new GeometryCollectionTypeHandler with specified default SRID.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     */
    public GeometryCollectionTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new GeometryCollectionTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0
     * @param strategy the database-specific geometry handler strategy
     */
    public GeometryCollectionTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Declared with the concrete type so that subclasses compiled against 1.0.x, whose
     * {@code super} calls use this signature, keep linking.</p>
     */
    @Override
    protected void validateGeometry(GeometryCollection geometry) throws SQLException {
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
    protected GeometryCollection parseGeometry(String hexString) {
        return super.parseGeometry(hexString);
    }
}
