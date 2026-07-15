package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.GeometryCollection;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS GeometryCollection geometry.
 * Converts between JTS GeometryCollection objects and database GEOMETRY columns using WKB format.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @GeometryCollectionTableField
 * private GeometryCollection geometryCollection;
 * }</pre>
 */
@MappedTypes(GeometryCollection.class)
public class GeometryCollectionTypeHandler extends AbstractGeometryTypeHandler<GeometryCollection> {

    /**
     * Create a new GeometryCollectionTypeHandler with default SRID (4326).
     */
    public GeometryCollectionTypeHandler() {
        super();
    }

    /**
     * Create a new GeometryCollectionTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public GeometryCollectionTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new GeometryCollectionTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public GeometryCollectionTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected GeometryCollection parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkbAsGeometryCollection(hexString);
    }

    @Override
    protected void validateGeometry(GeometryCollection geometry) throws SQLException {
        if (!geometry.isValid()) {
            throw new SQLException("Invalid GeometryCollection geometry: geometry validation failed");
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "GeometryCollection";
    }
}
