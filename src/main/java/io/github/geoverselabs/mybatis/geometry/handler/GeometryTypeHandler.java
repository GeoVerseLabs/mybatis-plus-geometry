package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Geometry;

import java.sql.SQLException;

/**
 * MyBatis TypeHandler for JTS Geometry (generic/wildcard).
 * Converts between any JTS Geometry subtype and database GEOMETRY columns using WKB format.
 *
 * <p>Unlike specific type handlers (PointTypeHandler, etc.), this handler does not
 * perform type checking on the parsed geometry. It accepts any geometry type that
 * can be decoded from WKB.</p>
 *
 * <p>Usage in entity:</p>
 * <pre>{@code
 * @GeometryTableField
 * private Geometry shape;
 * }</pre>
 */
@MappedTypes(Geometry.class)
public class GeometryTypeHandler extends AbstractGeometryTypeHandler<Geometry> {

    /**
     * Create a new GeometryTypeHandler with default SRID (4326).
     */
    public GeometryTypeHandler() {
        super();
    }

    /**
     * Create a new GeometryTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    public GeometryTypeHandler(int defaultSrid) {
        super(defaultSrid);
    }

    /**
     * Create a new GeometryTypeHandler with specified default SRID and strategy.
     *
     * @param defaultSrid the default SRID to use
     * @param strategy the database-specific geometry handler strategy
     */
    public GeometryTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        super(defaultSrid, strategy);
    }

    @Override
    protected Geometry parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return WkbUtil.fromWkb(hexString);
    }

    @Override
    protected void validateGeometry(Geometry geometry) throws SQLException {
        if (!geometry.isValid()) {
            throw new SQLException("Invalid Geometry: " + geometry.getGeometryType()
                + " - geometry validation failed");
        }
    }

    @Override
    protected String getGeometryTypeName() {
        return "Geometry";
    }
}
