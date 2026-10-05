package io.github.geoverselabs.mybatis.geometry.jackson;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the number of decimal places written for the coordinates of one property.
 *
 * <p>Honoured by every geometry serializer of this package, for the annotated property itself
 * and for the elements of an annotated container (for example {@code List<Point>}). All other
 * options (the serializer's or module's {@link GeoJsonOptions}, or the global options) are kept;
 * only the precision changes.</p>
 *
 * <pre>{@code
 * public class PlaceDto {
 *     @GeoJsonPrecision(6)                     // about 10 cm for WGS84 coordinates
 *     private Point location;
 *
 *     @GeoJsonPrecision(GeoJsonPrecision.FULL) // full double precision, whatever the default
 *     private Polygon boundary;
 * }
 * }</pre>
 *
 * <p>The annotation may also be used as a meta-annotation inside a Jackson annotation bundle
 * ({@code @JacksonAnnotationsInside}).</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface GeoJsonPrecision {

    /** Write ordinates with full double precision. */
    int FULL = -1;

    /**
     * Number of decimal places: 0 to {@link GeoJsonOptions#MAX_PRECISION}, or {@link #FULL}.
     *
     * @return the precision
     */
    int value();
}
