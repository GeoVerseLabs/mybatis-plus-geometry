package io.github.geoverselabs.mybatis.geometry.util;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Provider for JTS GeometryFactory instances.
 * Provides a singleton factory with configurable SRID.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * GeometryFactory factory = GeometryFactoryProvider.getFactory();
 * Point point = factory.createPoint(new Coordinate(121.5, 31.2));
 * }</pre>
 */
public final class GeometryFactoryProvider {
    
    /** Default GeometryFactory with WGS84 SRID */
    private static final GeometryFactory DEFAULT_FACTORY = 
        new GeometryFactory(new PrecisionModel(), WkbUtil.DEFAULT_SRID);
    
    /** Configurable SRID for custom factory */
    private static volatile int configuredSrid = WkbUtil.DEFAULT_SRID;
    
    /** Custom factory with configured SRID */
    private static volatile GeometryFactory customFactory = null;
    
    private GeometryFactoryProvider() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Get the default GeometryFactory with SRID 4326 (WGS84).
     *
     * @return default GeometryFactory instance
     */
    public static GeometryFactory getFactory() {
        if (customFactory != null) {
            return customFactory;
        }
        return DEFAULT_FACTORY;
    }
    
    /**
     * Get a GeometryFactory with the specified SRID.
     *
     * @param srid the Spatial Reference System Identifier
     * @return GeometryFactory with the specified SRID
     */
    public static GeometryFactory getFactory(int srid) {
        if (srid == WkbUtil.DEFAULT_SRID) {
            return DEFAULT_FACTORY;
        }
        return new GeometryFactory(new PrecisionModel(), srid);
    }
    
    /**
     * Configure the default SRID for the factory.
     * This affects subsequent calls to {@link #getFactory()}.
     *
     * @param srid the Spatial Reference System Identifier
     */
    public static synchronized void setDefaultSrid(int srid) {
        if (srid != configuredSrid) {
            configuredSrid = srid;
            if (srid == WkbUtil.DEFAULT_SRID) {
                customFactory = null;
            } else {
                customFactory = new GeometryFactory(new PrecisionModel(), srid);
            }
        }
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
     * Reset to default configuration (SRID 4326).
     */
    public static synchronized void reset() {
        configuredSrid = WkbUtil.DEFAULT_SRID;
        customFactory = null;
    }
}
