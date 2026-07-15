package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.MultiLineString;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS MultiLineString geometry.
 * Converts between JTS MultiLineString objects and database GEOMETRY columns using WKB format.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @MultiLineStringTableField
 * private MultiLineString routes;
 * }</pre>
 */
@MappedTypes(MultiLineString.class)
public class MultiLineStringTypeHandler extends AbstractGeometryTypeHandler<MultiLineString> {

    /**
     * Create a new MultiLineStringTypeHandler with default SRID (4326).
     */
    public MultiLineStringTypeHandler() {
        super();
    }

    /**
     * Create a new MultiLineStringTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public MultiLineStringTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new MultiLineStringTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public MultiLineStringTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected MultiLineString parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkbAsMultiLineString(hexString);
    }

    @Override
    protected void validateGeometry(MultiLineString geometry) throws SQLException {
        if (!geometry.isValid()) {
            throw new SQLException("Invalid MultiLineString geometry: geometry validation failed");
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "MultiLineString";
    }
}
