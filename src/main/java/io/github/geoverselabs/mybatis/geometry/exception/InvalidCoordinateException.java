package io.github.geoverselabs.mybatis.geometry.exception;

import java.io.IOException;

/**
 * Exception thrown when coordinate values are out of valid range.
 */
public class InvalidCoordinateException extends IOException {
    
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
        super(String.format("Coordinate out of range: %s %f not in [%f, %f]", 
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
        return new InvalidCoordinateException("longitude", value, -180.0, 180.0);
    }
    
    /**
     * Create exception for invalid latitude.
     *
     * @param value the invalid latitude value
     * @return InvalidCoordinateException for latitude
     */
    public static InvalidCoordinateException forLatitude(double value) {
        return new InvalidCoordinateException("latitude", value, -90.0, 90.0);
    }
}
