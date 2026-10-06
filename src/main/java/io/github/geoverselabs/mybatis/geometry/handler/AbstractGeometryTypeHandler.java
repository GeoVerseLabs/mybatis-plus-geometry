package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Set;

/**
 * Abstract base class for geometry TypeHandlers.
 * Converts JTS geometry objects to/from database GEOMETRY columns through a
 * {@link GeometryHandlerStrategy}.
 *
 * <p><strong>Configuration:</strong> handlers created with the no-arg constructor (as MyBatis and
 * MyBatis-Plus do for {@code @TableField(typeHandler = ...)}) resolve the strategy
 * ({@link GeometryStrategyFactory#getDefaultStrategy()}) and the default SRID
 * ({@link GeometryFactoryProvider#getConfiguredSrid()}) on every call, so configuration applied
 * after the handler was created takes effect. Explicit constructor arguments are used as given.</p>
 *
 * <p><strong>Write Operations (INSERT/UPDATE):</strong></p>
 * <ol>
 *   <li>Reject a geometry that is not an instance of the handler's Java type (for example a Polygon
 *       bound with a {@code PointTypeHandler} in hand-written SQL)</li>
 *   <li>Validate according to {@link GeometryDefaults#getWriteValidation()}
 *       ({@link #validateGeometry(Geometry)})</li>
 *   <li>Effective SRID: the geometry's SRID, or the default SRID when it is 0</li>
 *   <li>Convert with {@link GeometryHandlerStrategy#convertForDatabase(Geometry, int)}; the parameter
 *       object is never modified</li>
 *   <li>Bind {@code byte[]} with {@code setBytes} (MySQL), {@code String} with
 *       {@code setObject(value, Types.OTHER)} (PostGIS hex EWKB), anything else with {@code setObject}</li>
 * </ol>
 *
 * <p><strong>Read Operations (SELECT):</strong> the strategy reads the column
 * ({@link GeometryHandlerStrategy#read(ResultSet, String)}); built-in strategies decode unwrapped
 * geometry columns as well as the expressions produced by the optional SELECT interceptor. A value
 * whose geometry type does not match the handler's Java type raises an {@link SQLException}.</p>
 *
 * @param <T> the specific geometry type (Point, Polygon, LineString, ...)
 */
public abstract class AbstractGeometryTypeHandler<T extends Geometry> extends BaseTypeHandler<T> {

    /** The built-in handlers, whose typed overrides of the 1.0.x hooks only delegate to this class. */
    private static final Set<String> BUILT_IN_HANDLERS = Set.of(
        "io.github.geoverselabs.mybatis.geometry.handler.PointTypeHandler",
        "io.github.geoverselabs.mybatis.geometry.handler.LineStringTypeHandler",
        "io.github.geoverselabs.mybatis.geometry.handler.PolygonTypeHandler",
        "io.github.geoverselabs.mybatis.geometry.handler.MultiPointTypeHandler",
        "io.github.geoverselabs.mybatis.geometry.handler.MultiLineStringTypeHandler",
        "io.github.geoverselabs.mybatis.geometry.handler.MultiPolygonTypeHandler",
        "io.github.geoverselabs.mybatis.geometry.handler.GeometryCollectionTypeHandler",
        "io.github.geoverselabs.mybatis.geometry.handler.GeometryTypeHandler");

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Default SRID given to the constructor, or the configured SRID at construction time for the
     * no-arg constructor.
     *
     * @deprecated a snapshot that ignores later configuration changes for handlers created with the
     *     no-arg constructor; use {@link #getDefaultSrid()}.
     */
    @Deprecated
    protected final int defaultSrid;

    /**
     * Strategy given to the constructor, or the default strategy at construction time.
     *
     * @deprecated a snapshot that ignores later configuration changes for handlers created without
     *     an explicit strategy; use {@link #getStrategy()}.
     */
    @Deprecated
    protected final GeometryHandlerStrategy strategy;

    private final boolean explicitSrid;

    private final GeometryHandlerStrategy explicitStrategy;

    private final Class<?> geometryClass;

    /** A subclass other than the built-in handlers overrides {@link #ensureSrid(Geometry)} (1.0.x write hook). */
    private final boolean legacyEnsureSrid;

    /** A subclass other than the built-in handlers overrides {@link #parseGeometry(String)} (1.0.x read hook). */
    private final boolean legacyParseGeometry;

    /**
     * Create a handler that resolves the default SRID and strategy from the global configuration
     * ({@link GeometryFactoryProvider#getConfiguredSrid()},
     * {@link GeometryStrategyFactory#getDefaultStrategy()}) on every call.
     */
    protected AbstractGeometryTypeHandler() {
        this(false, GeometryFactoryProvider.getConfiguredSrid(), null);
    }

    /**
     * Create a handler with a fixed default SRID; the strategy is resolved on every call.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0 (0 keeps them at 0)
     */
    protected AbstractGeometryTypeHandler(int defaultSrid) {
        this(true, defaultSrid, null);
    }

    /**
     * Create a handler with a fixed default SRID and strategy.
     * This constructor supports dependency injection for testability.
     *
     * @param defaultSrid the SRID given to geometries whose SRID is 0 (0 keeps them at 0)
     * @param strategy    the database-specific geometry handler strategy; null resolves the default
     *                    strategy on every call
     */
    protected AbstractGeometryTypeHandler(int defaultSrid, GeometryHandlerStrategy strategy) {
        this(true, defaultSrid, strategy);
    }

    private AbstractGeometryTypeHandler(boolean explicitSrid, int defaultSrid, GeometryHandlerStrategy strategy) {
        this.explicitSrid = explicitSrid;
        this.defaultSrid = defaultSrid;
        this.explicitStrategy = strategy;
        this.strategy = strategy != null ? strategy : GeometryStrategyFactory.getDefaultStrategy();
        this.geometryClass = resolveGeometryClass(getRawType());
        this.legacyEnsureSrid = overriddenOutsideLibrary(getClass(), "ensureSrid", Geometry.class);
        this.legacyParseGeometry = overriddenOutsideLibrary(getClass(), "parseGeometry", String.class);
    }

    /**
     * Whether a class between {@code type} and this base class, other than the built-in handlers,
     * declares the given method. Such overrides come from subclasses written for 1.0.x, whose hooks are honoured.
     */
    private static boolean overriddenOutsideLibrary(Class<?> type, String name, Class<?> parameterType) {
        for (Class<?> c = type; c != null && c != AbstractGeometryTypeHandler.class; c = c.getSuperclass()) {
            if (BUILT_IN_HANDLERS.contains(c.getName())) {
                continue;
            }
            for (Method method : c.getDeclaredMethods()) {
                if (!method.isSynthetic() && method.getName().equals(name)
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[] {parameterType})) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Class<?> resolveGeometryClass(Type rawType) {
        if (rawType instanceof Class<?> type && Geometry.class.isAssignableFrom(type)) {
            return type;
        }
        return Geometry.class;
    }

    /**
     * The strategy used for the current call: the explicit one, or the current global default.
     *
     * @return the strategy, never null
     */
    protected GeometryHandlerStrategy getStrategy() {
        GeometryHandlerStrategy s = explicitStrategy;
        return s != null ? s : GeometryStrategyFactory.getDefaultStrategy();
    }

    /**
     * The SRID given to geometries whose SRID is 0: the explicit one, or the currently configured
     * {@link GeometryFactoryProvider#getConfiguredSrid()}. May be 0.
     *
     * @return the default SRID
     */
    protected int getDefaultSrid() {
        return explicitSrid ? defaultSrid : GeometryFactoryProvider.getConfiguredSrid();
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType)
            throws SQLException {
        if (parameter == null) {
            throw new SQLException("Parameter cannot be null");
        }
        if (!geometryClass.isInstance(parameter)) {
            // generics are erased: hand-written SQL can bind any geometry with this handler
            throw new SQLException(getClass().getSimpleName() + " cannot write a " + parameter.getGeometryType()
                + ": the mapped type is " + geometryClass.getName());
        }
        try {
            validateGeometry(parameter);
        } catch (RuntimeException e) {
            throw new SQLException("Invalid " + parameter.getGeometryType() + " geometry: " + e.getMessage(), e);
        }

        int srid = parameter.getSRID() != 0 ? parameter.getSRID() : getDefaultSrid();
        if (legacyEnsureSrid) {
            // a 1.0.x subclass chooses the SRID in ensureSrid: apply it to a copy, never the caller's object
            Geometry copy = parameter.copy();
            ensureSrid(copy);
            srid = copy.getSRID() != 0 ? copy.getSRID() : getDefaultSrid();
        }
        Object dbValue;
        try {
            dbValue = getStrategy().convertForDatabase(parameter, srid);
        } catch (RuntimeException e) {
            throw new SQLException("Failed to convert " + parameter.getGeometryType() + " to database format: "
                + e.getMessage(), e);
        }

        if (dbValue instanceof byte[] bytes) {
            // MySQL/MariaDB: internal geometry format
            ps.setBytes(i, bytes);
        } else if (dbValue instanceof String text) {
            // PostgreSQL: hex EWKB, sent untyped (Types.OTHER) so PostGIS parses it as geometry
            ps.setObject(i, text, Types.OTHER);
        } else if (dbValue != null) {
            ps.setObject(i, dbValue);
        } else {
            throw new SQLException("Strategy " + getStrategy().getClass().getName() + " converted a "
                + parameter.getGeometryType() + " to null");
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Geometry geometry;
        try {
            geometry = legacyParseGeometry ? parseGeometry(rs.getString(columnName)) : getStrategy().read(rs, columnName);
        } catch (RuntimeException e) {
            throw readFailure(columnName, e);
        }
        return checkType(geometry, columnName);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Geometry geometry;
        try {
            geometry = legacyParseGeometry ? parseGeometry(rs.getString(columnIndex)) : getStrategy().read(rs, columnIndex);
        } catch (RuntimeException e) {
            throw readFailure(columnIndex, e);
        }
        return checkType(geometry, columnIndex);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Geometry geometry;
        try {
            geometry = legacyParseGeometry ? parseGeometry(cs.getString(columnIndex)) : getStrategy().read(cs, columnIndex);
        } catch (RuntimeException e) {
            throw readFailure(columnIndex, e);
        }
        return checkType(geometry, columnIndex);
    }

    private SQLException readFailure(Object column, RuntimeException e) {
        return new SQLException("Failed to read " + getGeometryTypeName() + " from " + describe(column) + ": "
            + e.getMessage(), e);
    }

    @SuppressWarnings("unchecked")
    private T checkType(Geometry geometry, Object column) throws SQLException {
        if (geometry == null || geometryClass.isInstance(geometry)) {
            return (T) geometry;
        }
        throw new SQLException(describe(column) + " contains a " + geometry.getGeometryType()
            + " but the mapped type is " + geometryClass.getName());
    }

    private static String describe(Object column) {
        return column instanceof String ? "column '" + column + "'" : "column " + column;
    }

    /**
     * Parse a value with the current strategy and check its type.
     *
     * @param hexString the database value
     * @return the parsed geometry, or null if input is null/empty
     * @throws IllegalArgumentException if the value holds another geometry type
     * @deprecated the handler reads through {@link GeometryHandlerStrategy#read(ResultSet, String)}.
     *     It only calls this method, with {@code getString()} of the column as in 1.0.x, when a
     *     subclass overrides it; implement a custom strategy instead.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    protected T parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        Geometry geometry = getStrategy().parseFromDatabase(hexString);
        if (geometry == null || geometryClass.isInstance(geometry)) {
            return (T) geometry;
        }
        throw new IllegalArgumentException("Value is not a " + getGeometryTypeName() + " geometry, got: "
            + geometry.getGeometryType());
    }

    /**
     * Validate a geometry before it is written, according to
     * {@link GeometryDefaults#getWriteValidation()}:
     * {@link GeometryValidation#NONE} checks nothing, {@link GeometryValidation#BASIC} (default)
     * rejects NaN or infinite X/Y ordinates, {@link GeometryValidation#FULL} additionally requires
     * OGC validity ({@link Geometry#isValid()}). Empty geometries pass BASIC and FULL.
     *
     * <p>Subclasses may override this method to apply their own rules.</p>
     *
     * @param geometry the geometry to validate (not modified)
     * @throws SQLException if geometry is invalid
     */
    protected void validateGeometry(T geometry) throws SQLException {
        GeometryValidation level = GeometryDefaults.getWriteValidation();
        if (level == GeometryValidation.NONE) {
            return;
        }
        FiniteOrdinates finite = new FiniteOrdinates();
        geometry.apply(finite);
        if (finite.problem != null) {
            throw new SQLException("Invalid " + geometry.getGeometryType() + " geometry: " + finite.problem);
        }
        if (level == GeometryValidation.FULL) {
            TopologyValidationError error = new IsValidOp(geometry).getValidationError();
            if (error != null) {
                throw new SQLException("Invalid " + geometry.getGeometryType() + " geometry: " + error);
            }
        }
    }

    /**
     * Get the geometry type name for messages: the simple name of the handler's Java type.
     *
     * @return the geometry type name
     */
    protected String getGeometryTypeName() {
        return geometryClass.getSimpleName();
    }

    /**
     * Ensure the geometry has a valid SRID.
     * If SRID is 0, set it to the default SRID.
     *
     * @param geometry the geometry to check (modified in place)
     * @deprecated modifies the caller's geometry; the handler now passes the effective SRID to
     *     {@link GeometryHandlerStrategy#convertForDatabase(Geometry, int)} instead. When a subclass
     *     overrides this method, the handler calls it on a copy of the geometry and uses the copy's SRID.
     */
    @Deprecated
    protected void ensureSrid(Geometry geometry) {
        if (geometry.getSRID() == 0) {
            geometry.setSRID(getDefaultSrid());
        }
    }

    /**
     * Finds the first NaN or infinite X/Y ordinate without allocating coordinates.
     */
    private static final class FiniteOrdinates implements CoordinateSequenceFilter {

        private String problem;

        @Override
        public void filter(CoordinateSequence seq, int i) {
            double x = seq.getX(i);
            double y = seq.getY(i);
            if (!Double.isFinite(x)) {
                problem = "X coordinate is " + (Double.isNaN(x) ? "NaN" : "infinite");
            } else if (!Double.isFinite(y)) {
                problem = "Y coordinate is " + (Double.isNaN(y) ? "NaN" : "infinite");
            }
        }

        @Override
        public boolean isDone() {
            return problem != null;
        }

        @Override
        public boolean isGeometryChanged() {
            return false;
        }
    }
}
