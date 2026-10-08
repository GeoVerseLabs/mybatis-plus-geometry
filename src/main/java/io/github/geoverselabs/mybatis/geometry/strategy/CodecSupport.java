package io.github.geoverselabs.mybatis.geometry.strategy;

import io.github.geoverselabs.mybatis.geometry.codec.WkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.WkbSupport;
import io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException;
import io.github.geoverselabs.mybatis.geometry.exception.WkbParseException;
import org.locationtech.jts.geom.Geometry;

/**
 * Codec invocation with consistent error reporting, shared by the built-in strategies.
 */
final class CodecSupport {

    private CodecSupport() {
    }

    /**
     * Decode a database value.
     *
     * @throws GeometryConversionException for unsupported value types
     * @throws WkbParseException           for malformed values, with the codec failure as cause
     */
    static Geometry decode(WkbCodec codec, Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        if (!(dbValue instanceof String) && !(dbValue instanceof byte[])) {
            throw new GeometryConversionException("Unexpected database value type", "Geometry",
                dbValue.getClass().getName());
        }
        try {
            return codec.decode(dbValue);
        } catch (IllegalArgumentException e) {
            String sample = dbValue instanceof byte[] bytes
                ? WkbSupport.hexPrefix(bytes)
                : WkbSupport.textPrefix((String) dbValue);
            throw new WkbParseException("Failed to decode geometry from database value: " + e.getMessage(),
                sample, e);
        }
    }

    /**
     * Encode a geometry with an explicit SRID.
     *
     * @throws GeometryConversionException if encoding fails, with the original failure as cause
     */
    static Object encode(WkbCodec codec, Geometry geometry, int srid) {
        if (geometry == null) {
            return null;
        }
        try {
            return codec.encode(geometry, srid);
        } catch (GeometryConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GeometryConversionException("Failed to encode geometry: " + e.getMessage(),
                geometry.getGeometryType(), null, e);
        }
    }
}
