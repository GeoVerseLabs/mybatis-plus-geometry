package io.github.geoverselabs.mybatis.geometry.util;

import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provider for the JTS {@link GeometryFactory} instances used by this library.
 *
 * <p>Factories are cached per SRID, so geometries created for the same SRID share one factory and
 * operations on them ({@code buffer}, {@code union}, {@code getGeometryN}, ...) keep that SRID.
 * The coordinate storage of new geometries is selected with
 * {@link #setCoordinateSequenceType(CoordinateSequenceType)}.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * GeometryFactory factory = GeometryFactoryProvider.getFactory();
 * Point point = factory.createPoint(new Coordinate(121.5, 31.2));
 * }</pre>
 *
 * <p>All methods are thread-safe.</p>
 */
public final class GeometryFactoryProvider {

    /**
     * Upper bound of cached factories. SRIDs normally come from a handful of configured values, but
     * decoded database values can carry arbitrary SRIDs; beyond this many distinct SRIDs new factories
     * are created per call instead of growing the cache without limit.
     */
    static final int MAX_CACHED_FACTORIES = 256;

    private static final PrecisionModel PRECISION_MODEL = new PrecisionModel();

    /** Configurable SRID returned by {@link #getConfiguredSrid()}. */
    private static volatile int configuredSrid = WkbUtil.DEFAULT_SRID;

    /** Factories for the current coordinate sequence type, keyed by SRID. Replaced when the type changes. */
    private static volatile FactoryCache cache = new FactoryCache(CoordinateSequenceType.ARRAY);

    private GeometryFactoryProvider() {
        // Utility class, prevent instantiation
    }

    /**
     * Get the factory for the configured default SRID ({@link #getConfiguredSrid()}, 4326 unless
     * changed) and the configured coordinate sequence type.
     *
     * @return the shared GeometryFactory instance
     */
    public static GeometryFactory getFactory() {
        return getFactory(configuredSrid);
    }

    /**
     * Get a GeometryFactory with the specified SRID and the configured coordinate sequence type.
     * Instances are cached, so repeated calls with the same SRID return the same factory.
     *
     * @param srid the Spatial Reference System Identifier (0 means unknown)
     * @return GeometryFactory with the specified SRID
     */
    public static GeometryFactory getFactory(int srid) {
        return cache.get(srid);
    }

    /**
     * Configure the default SRID for the factory.
     * This affects subsequent calls to {@link #getFactory()} and the SRID that TypeHandlers created
     * without an explicit SRID assign to geometries whose SRID is 0.
     *
     * @param srid the Spatial Reference System Identifier (0 is allowed and means "unknown")
     */
    public static void setDefaultSrid(int srid) {
        configuredSrid = srid;
    }

    /**
     * Get the currently configured default SRID.
     *
     * @return the configured SRID
     */
    public static int getConfiguredSrid() {
        return configuredSrid;
    }

    /**
     * Select how coordinates of geometries created by this library (database reads, GeoJSON input)
     * are stored. Geometries created earlier are not affected.
     *
     * @param type the coordinate sequence type; null restores {@link CoordinateSequenceType#ARRAY}
     */
    public static synchronized void setCoordinateSequenceType(CoordinateSequenceType type) {
        CoordinateSequenceType effective = type == null ? CoordinateSequenceType.ARRAY : type;
        if (cache.type != effective) {
            cache = new FactoryCache(effective);
        }
    }

    /**
     * Get the configured coordinate sequence type.
     *
     * @return the coordinate sequence type, never null
     */
    public static CoordinateSequenceType getCoordinateSequenceType() {
        return cache.type;
    }

    /**
     * Reset to default configuration (SRID 4326, {@link CoordinateSequenceType#ARRAY}).
     */
    public static synchronized void reset() {
        configuredSrid = WkbUtil.DEFAULT_SRID;
        cache = new FactoryCache(CoordinateSequenceType.ARRAY);
    }

    /**
     * Get the JTS coordinate sequence factory for a sequence type.
     *
     * @param type the sequence type
     * @return the matching JTS factory
     */
    static CoordinateSequenceFactory sequenceFactory(CoordinateSequenceType type) {
        return type == CoordinateSequenceType.PACKED
            ? PackedCoordinateSequenceFactory.DOUBLE_FACTORY
            : CoordinateArraySequenceFactory.instance();
    }

    /**
     * Immutable association of a sequence type with its per-SRID factories.
     */
    private static final class FactoryCache {

        private final CoordinateSequenceType type;
        private final CoordinateSequenceFactory sequenceFactory;
        private final ConcurrentMap<Integer, GeometryFactory> factories = new ConcurrentHashMap<>();

        FactoryCache(CoordinateSequenceType type) {
            this.type = type;
            this.sequenceFactory = sequenceFactory(type);
        }

        GeometryFactory get(int srid) {
            GeometryFactory factory = factories.get(srid);
            if (factory != null) {
                return factory;
            }
            factory = new GeometryFactory(PRECISION_MODEL, srid, sequenceFactory);
            if (factories.size() < MAX_CACHED_FACTORIES) {
                GeometryFactory existing = factories.putIfAbsent(srid, factory);
                if (existing != null) {
                    return existing;
                }
            }
            return factory;
        }
    }
}
