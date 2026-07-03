package io.github.geoverselabs.mybatis.geometry.exception;

import java.io.IOException;

/**
 * Exception thrown when GeoJSON parsing fails.
 * Includes the invalid field name for debugging.
 */
public class GeoJsonParseException extends IOException {
    
    private final String expectedType;
    private final String actualType;
    private final String invalidField;
    
    /**
     * Create a new GeoJsonParseException for invalid field.
     *
     * @param message the error message
     * @param invalidField the name of the invalid field
     */
    public GeoJsonParseException(String message, String invalidField) {
        super(String.format("%s [field=%s]", message, invalidField));
        this.expectedType = null;
        this.actualType = null;
        this.invalidField = invalidField;
    }
    
    /**
     * Create a new GeoJsonParseException with cause.
     *
     * @param message the error message
     * @param invalidField the name of the invalid field
     * @param cause the underlying cause
     */
    public GeoJsonParseException(String message, String invalidField, Throwable cause) {
        super(String.format("%s [field=%s]", message, invalidField), cause);
        this.expectedType = null;
        this.actualType = null;
        this.invalidField = invalidField;
    }
    
    /**
     * Get the expected GeoJSON type.
     *
     * @return the expected type, or null if not a type mismatch error
     */
    public String getExpectedType() {
        return expectedType;
    }
    
    /**
     * Get the actual GeoJSON type found.
     *
     * @return the actual type, or null if not a type mismatch error
     */
    public String getActualType() {
        return actualType;
    }
    
    /**
     * Get the name of the invalid field.
     *
     * @return the invalid field name
     */
    public String getInvalidField() {
        return invalidField;
    }
    
    /**
     * Create a GeoJsonParseException for type mismatch.
     *
     * @param expectedType the expected GeoJSON type
     * @param actualType the actual GeoJSON type found
     * @return the exception
     */
    public static GeoJsonParseException forTypeMismatch(String expectedType, String actualType) {
        GeoJsonParseException ex = new GeoJsonParseException(
            String.format("Invalid GeoJSON type: expected %s, got %s", expectedType, actualType),
            "type"
        );
        return ex;
    }
}
