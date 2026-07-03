package io.github.geoverselabs.mybatis.geometry.exception;

/**
 * Exception thrown when WKB (Well-Known Binary) parsing fails.
 * Includes the problematic hex string prefix for debugging.
 */
public class WkbParseException extends GeometryConversionException {
    
    private final String hexPrefix;
    
    /**
     * Create a new WkbParseException.
     *
     * @param message the error message
     * @param hexString the WKB hex string that failed to parse
     */
    public WkbParseException(String message, String hexString) {
        super(message, "WKB", truncateHex(hexString));
        this.hexPrefix = truncateHex(hexString);
    }
    
    /**
     * Create a new WkbParseException with cause.
     *
     * @param message the error message
     * @param hexString the WKB hex string that failed to parse
     * @param cause the underlying cause
     */
    public WkbParseException(String message, String hexString, Throwable cause) {
        super(message, "WKB", truncateHex(hexString), cause);
        this.hexPrefix = truncateHex(hexString);
    }
    
    /**
     * Get the prefix of the problematic hex string.
     *
     * @return the hex string prefix (truncated to 40 characters)
     */
    public String getHexPrefix() {
        return hexPrefix;
    }
    
    private static String truncateHex(String hexString) {
        if (hexString == null) {
            return "null";
        }
        if (hexString.length() <= 40) {
            return hexString;
        }
        return hexString.substring(0, 40) + "...";
    }
}
