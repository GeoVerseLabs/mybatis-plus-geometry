package io.github.geoverselabs.mybatis.geometry.exception;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * Exception thrown when GeoJSON parsing fails.
 * Includes the invalid field name for debugging.
 *
 * <p>Extends {@link JsonMappingException} (and therefore {@link java.io.IOException}), so Jackson
 * adds the JSON reference path of the failing property to the message (for example
 * {@code (through reference chain: Dto["location"])}) and frameworks such as Spring MVC report
 * the error as a client error (HTTP 400). When created with a {@link JsonParser} the exception
 * also carries the location of the offending token.</p>
 *
 * <p>This class is only used by the Jackson integration ({@code ...geometry.jackson}); Jackson
 * must be on the classpath to load it.</p>
 */
public class GeoJsonParseException extends JsonMappingException {

    private static final long serialVersionUID = 1L;

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
        this(null, message, invalidField, null, null, null);
    }

    /**
     * Create a new GeoJsonParseException with cause.
     *
     * @param message the error message
     * @param invalidField the name of the invalid field
     * @param cause the underlying cause
     */
    public GeoJsonParseException(String message, String invalidField, Throwable cause) {
        this(null, message, invalidField, null, null, cause);
    }

    /**
     * Create a new GeoJsonParseException for invalid field, recording the location of the
     * parser's current token.
     *
     * @param parser the parser positioned at (or near) the offending token; may be null
     * @param message the error message
     * @param invalidField the name of the invalid field
     */
    public GeoJsonParseException(JsonParser parser, String message, String invalidField) {
        this(parser, message, invalidField, null, null, null);
    }

    /**
     * Create a new GeoJsonParseException with cause, recording the location of the parser's
     * current token.
     *
     * @param parser the parser positioned at (or near) the offending token; may be null
     * @param message the error message
     * @param invalidField the name of the invalid field
     * @param cause the underlying cause
     */
    public GeoJsonParseException(JsonParser parser, String message, String invalidField, Throwable cause) {
        this(parser, message, invalidField, null, null, cause);
    }

    private GeoJsonParseException(JsonParser parser, String message, String invalidField,
                                  String expectedType, String actualType, Throwable cause) {
        super(parser, String.format("%s [field=%s]", message, invalidField), cause);
        this.expectedType = expectedType;
        this.actualType = actualType;
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
     * @return the exception, with {@link #getExpectedType()} and {@link #getActualType()} populated
     */
    public static GeoJsonParseException forTypeMismatch(String expectedType, String actualType) {
        return forTypeMismatch(null, expectedType, actualType);
    }

    /**
     * Create a GeoJsonParseException for type mismatch, recording the location of the parser's
     * current token.
     *
     * @param parser the parser positioned at (or near) the {@code type} member; may be null
     * @param expectedType the expected GeoJSON type
     * @param actualType the actual GeoJSON type found
     * @return the exception, with {@link #getExpectedType()} and {@link #getActualType()} populated
     */
    public static GeoJsonParseException forTypeMismatch(JsonParser parser, String expectedType, String actualType) {
        return new GeoJsonParseException(parser,
            String.format("Invalid GeoJSON type: expected %s, got %s", expectedType, actualType),
            "type", expectedType, actualType, null);
    }
}
