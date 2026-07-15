package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.MultiPoint;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS MultiPoint geometry.
 * Converts between JTS MultiPoint objects and database GEOMETRY columns using WKB format.
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @MultiPointTableField
 * private MultiPoint locations;
 * }</pre>
 */
@MappedTypes(MultiPoint.class)
public class MultiPointTypeHandler extends AbstractGeometryTypeHandler<MultiPoint> {

    /**
     * Create a new MultiPointTypeHandler with default SRID (4326).
     */
    public MultiPointTypeHandler() {
        super();
    }

    /**
     * Create a new MultiPointTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public MultiPointTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new MultiPointTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public MultiPointTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected MultiPoint parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkbAsMultiPoint(hexString);
    }

    @Override
    protected void validateGeometry(MultiPoint geometry) throws SQLException {
        if (!geometry.isValid()) {
            throw new SQLException("Invalid MultiPoint geometry: geometry validation failed");
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "MultiPoint";
    }
}
