package io.github.geoverselabs.mybatis.geometry.strategy;

import org.locationtech.jts.geom.Geometry;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Strategy interface for database-specific geometry handling.
 * Implementations provide database-specific SQL functions and conversion logic.
 *
 * <p>This abstraction allows the library to support multiple databases
 * (MySQL, PostgreSQL/PostGIS) with different geometry handling approaches.
 * TypeHandlers read values through the {@code read} methods and write values produced by
 * {@link #convertForDatabase(Geometry, int)}, so a custom strategy controls both directions.</p>
 *
 * <p>Third-party implementations only need the abstract methods; the {@code read} methods and
 * {@link #convertForDatabase(Geometry, int)} have default implementations.</p>
 */
public interface GeometryHandlerStrategy {

    /**
     * Get the database type this strategy supports.
     *
     * @return the supported database type
     */
    DatabaseType getSupportedDatabaseType();

    /**
     * Wrap a geometry column for SELECT query. Only used when the optional SELECT interceptor is
     * enabled; TypeHandlers can read unwrapped geometry columns of the built-in databases.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>MySQL: {@code HEX(column) AS column}</li>
     *   <li>PostGIS: {@code encode(ST_AsEWKB(column::geometry), 'hex') AS column}</li>
     * </ul>
     *
     * @param columnName the column name to wrap
     * @return the wrapped column expression
     */
    String wrapColumnForSelect(String columnName);

    /**
     * Get the SQL function for geometry input.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>MySQL: {@code ?} (direct binary)</li>
     *   <li>PostGIS: {@code ?} (hex EWKB is accepted directly)</li>
     * </ul>
     *
     * @return the SQL function placeholder
     */
    String getGeometryInputFunction();

    /**
     * Convert a JTS geometry to database-specific format, using the geometry's SRID.
     *
     * @param geometry the geometry to convert (not modified)
     * @return the database-specific representation
     */
    Object convertForDatabase(Geometry geometry);

    /**
     * Convert a JTS geometry to database-specific format with an explicit SRID, without modifying
     * the geometry.
     *
     * <p>The default implementation converts the geometry itself when its SRID already matches and
     * otherwise converts a copy carrying {@code srid}.</p>
     *
     * @param geometry the geometry to convert (not modified)
     * @param srid     the SRID to store
     * @return the database-specific representation, or null for a null geometry
     */
    default Object convertForDatabase(Geometry geometry, int srid) {
        if (geometry == null) {
            return null;
        }
        if (geometry.getSRID() == srid) {
            return convertForDatabase(geometry);
        }
        Geometry copy = geometry.copy();
        copy.setSRID(srid);
        return convertForDatabase(copy);
    }

    /**
     * Parse geometry from database result.
     *
     * @param dbValue the value from database
     * @return the parsed JTS geometry
     */
    Geometry parseFromDatabase(Object dbValue);

    /**
     * Read a geometry column from a result set.
     *
     * <p>The default implementation reads {@link ResultSet#getString(String)} and delegates to
     * {@link #parseFromDatabase(Object)}.</p>
     *
     * @param rs          the result set
     * @param columnLabel the column label
     * @return the geometry, or null for SQL NULL
     * @throws SQLException if the driver fails
     */
    default Geometry read(ResultSet rs, String columnLabel) throws SQLException {
        String value = rs.getString(columnLabel);
        return value == null ? null : parseFromDatabase(value);
    }

    /**
     * Read a geometry column from a result set.
     *
     * <p>The default implementation reads {@link ResultSet#getString(int)} and delegates to
     * {@link #parseFromDatabase(Object)}.</p>
     *
     * @param rs          the result set
     * @param columnIndex the 1-based column index
     * @return the geometry, or null for SQL NULL
     * @throws SQLException if the driver fails
     */
    default Geometry read(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : parseFromDatabase(value);
    }

    /**
     * Read a geometry OUT parameter from a callable statement.
     *
     * <p>The default implementation reads {@link CallableStatement#getString(int)} and delegates to
     * {@link #parseFromDatabase(Object)}.</p>
     *
     * @param cs             the callable statement
     * @param parameterIndex the 1-based parameter index
     * @return the geometry, or null for SQL NULL
     * @throws SQLException if the driver fails
     */
    default Geometry read(CallableStatement cs, int parameterIndex) throws SQLException {
        String value = cs.getString(parameterIndex);
        return value == null ? null : parseFromDatabase(value);
    }
}
