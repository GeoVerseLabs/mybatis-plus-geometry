package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.MultiPolygonTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as JTS MultiPolygon type.
 * Automatically binds the MultiPolygonTypeHandler for database conversion.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_land_parcel", autoResultMap = true)
 * public class LandParcel extends BaseEntity {
 *     @MultiPolygonTableField
 *     private MultiPolygon boundaries;
 * }
 * }</pre>
 *
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = MultiPolygonTypeHandler.class)
public @interface MultiPolygonTableField {
}
