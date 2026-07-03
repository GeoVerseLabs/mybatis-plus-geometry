package io.github.geoverselabs.mybatis.geometry.exception;

/**
 * Base exception for geometry conversion errors.
 * Thrown when geometry conversion between formats fails.
 */
public class GeometryConversionException extends RuntimeException {
    
    private final String geometryType;
    private final String context;
    
    /**
     * Create a new GeometryConversionException.
     *
     * @param message the error message
     */
    public GeometryConversionException(String message) {
        super(message);
        this.geometryType = null;
        this.context = null;
    }
    
    /**
     * Create a new GeometryConversionException with cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public GeometryConversionException(String message, Throwable cause) {
        super(message, cause);
        this.geometryType = null;
        this.context = null;
    }
    
    /**
     * Create a new GeometryConversionException with context.
     *
     * @param message the error message
     * @param geometryType the type of geometry being converted
     * @param context additional context information
     */
    public GeometryConversionException(String message, String geometryType, String context) {
        super(formatMessage(message, geometryType, context));
        this.geometryType = geometryType;
        this.context = context;
    }
    
    /**
     * Create a new GeometryConversionException with context and cause.
     *
     * @param message the error message
     * @param geometryType the type of geometry being converted
     * @param context additional context information
     * @param cause the underlying cause
     */
    public GeometryConversionException(String message, String geometryType, String context, Throwable cause) {
        super(formatMessage(message, geometryType, context), cause);
        this.geometryType = geometryType;
        this.context = context;
    }
    
    /**
     * Get the geometry type involved in the conversion.
     *
     * @return the geometry type, or null if not specified
     */
    public String getGeometryType() {
        return geometryType;
    }
    
    /**
     * Get additional context information.
     *
     * @return the context, or null if not specified
     */
    public String getContext() {
        return context;
    }
    
    private static String formatMessage(String message, String geometryType, String context) {
        StringBuilder sb = new StringBuilder(message);
        if (geometryType != null) {
            sb.append(" [type=").append(geometryType).append("]");
        }
        if (context != null) {
            sb.append(" [context=").append(context).append("]");
        }
        return sb.toString();
    }
}
