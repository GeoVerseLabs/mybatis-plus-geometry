package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.GeometryTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as a generic JTS Geometry type.
 * Automatically binds the GeometryTypeHandler for database conversion.
 * This handler supports any geometry subtype (Point, LineString, Polygon,
 * MultiPoint, MultiLineString, MultiPolygon, GeometryCollection).
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_feature", autoResultMap = true)
 * public class Feature extends BaseEntity {
 *     @GeometryTableField
 *     private Geometry shape;
 * }
 * }</pre>
 *
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = GeometryTypeHandler.class)
public @interface GeometryTableField {
}
