package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.MultiPolygon;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS MultiPolygon geometry.
 * Converts between JTS MultiPolygon objects and database GEOMETRY columns using WKB format.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @MultiPolygonTableField
 * private MultiPolygon regions;
 * }</pre>
 */
@MappedTypes(MultiPolygon.class)
public class MultiPolygonTypeHandler extends AbstractGeometryTypeHandler<MultiPolygon> {

    /**
     * Create a new MultiPolygonTypeHandler with default SRID (4326).
     */
    public MultiPolygonTypeHandler() {
        super();
    }

    /**
     * Create a new MultiPolygonTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public MultiPolygonTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new MultiPolygonTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public MultiPolygonTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected MultiPolygon parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkbAsMultiPolygon(hexString);
    }

    @Override
    protected void validateGeometry(MultiPolygon geometry) throws SQLException {
        if (!geometry.isValid()) {
            throw new SQLException("Invalid MultiPolygon geometry: geometry validation failed");
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "MultiPolygon";
    }
}
