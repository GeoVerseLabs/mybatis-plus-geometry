package io.github.geoverselabs.mybatis.geometry.exception;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.util.Locale;

/**
 * Exception thrown when coordinate values are out of valid range.
 *
 * <p>Extends {@link JsonMappingException} (and therefore {@link java.io.IOException}), so Jackson
 * adds the JSON reference path of the failing property to the message and frameworks such as
 * Spring MVC report the error as a client error (HTTP 400). When created with a
 * {@link JsonParser} the exception also carries the location of the offending ordinate.</p>
 *
 * <p>This class is only used by the Jackson integration ({@code ...geometry.jackson}); Jackson
 * must be on the classpath to load it.</p>
 */
public class InvalidCoordinateException extends JsonMappingException {

    private static final long serialVersionUID = 1L;

    private final String coordinateType;
    private final double value;
    private final double minValue;
    private final double maxValue;

    /**
     * Create a new InvalidCoordinateException.
     *
     * @param coordinateType the type of coordinate (longitude/latitude)
     * @param value the invalid value
     * @param minValue the minimum valid value
     * @param maxValue the maximum valid value
     */
    public InvalidCoordinateException(String coordinateType, double value, double minValue, double maxValue) {
        this(null, coordinateType, value, minValue, maxValue);
    }

    /**
     * Create a new InvalidCoordinateException, recording the location of the parser's current
     * token.
     *
     * @param parser the parser positioned at (or near) the offending ordinate; may be null
     * @param coordinateType the type of coordinate (longitude/latitude/altitude)
     * @param value the invalid value
     * @param minValue the minimum valid value
     * @param maxValue the maximum valid value
     */
    public InvalidCoordinateException(JsonParser parser, String coordinateType, double value,
                                      double minValue, double maxValue) {
        super(parser, String.format(Locale.ROOT, "Coordinate out of range: %s %f not in [%f, %f]",
            coordinateType, value, minValue, maxValue));
        this.coordinateType = coordinateType;
        this.value = value;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /**
     * Get the coordinate type.
     *
     * @return the coordinate type (longitude/latitude)
     */
    public String getCoordinateType() {
        return coordinateType;
    }

    /**
     * Get the invalid value.
     *
     * @return the invalid coordinate value
     */
    public double getValue() {
        return value;
    }

    /**
     * Get the minimum valid value.
     *
     * @return the minimum valid value
     */
    public double getMinValue() {
        return minValue;
    }

    /**
     * Get the maximum valid value.
     *
     * @return the maximum valid value
     */
    public double getMaxValue() {
        return maxValue;
    }

    /**
     * Create exception for invalid longitude.
     *
     * @param value the invalid longitude value
     * @return InvalidCoordinateException for longitude
     */
    public static InvalidCoordinateException forLongitude(double value) {
        return forLongitude(null, value);
    }

    /**
     * Create exception for invalid latitude.
     *
     * @param value the invalid latitude value
     * @return InvalidCoordinateException for latitude
     */
    public static InvalidCoordinateException forLatitude(double value) {
        return forLatitude(null, value);
    }

    /**
     * Create exception for invalid longitude, recording the parser location.
     *
     * @param parser the parser positioned at (or near) the offending ordinate; may be null
     * @param value the invalid longitude value
     * @return InvalidCoordinateException for longitude
     */
    public static InvalidCoordinateException forLongitude(JsonParser parser, double value) {
        return new InvalidCoordinateException(parser, "longitude", value, -180.0, 180.0);
    }

    /**
     * Create exception for invalid latitude, recording the parser location.
     *
     * @param parser the parser positioned at (or near) the offending ordinate; may be null
     * @param value the invalid latitude value
     * @return InvalidCoordinateException for latitude
     */
    public static InvalidCoordinateException forLatitude(JsonParser parser, double value) {
        return new InvalidCoordinateException(parser, "latitude", value, -90.0, 90.0);
    }
}
