package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.GeometryCollectionTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as JTS GeometryCollection type.
 * Automatically binds the GeometryCollectionTypeHandler for database conversion.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_map_layer", autoResultMap = true)
 * public class MapLayer extends BaseEntity {
 *     @GeometryCollectionTableField
 *     private GeometryCollection features;
 * }
 * }</pre>
 *
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = GeometryCollectionTypeHandler.class)
public @interface GeometryCollectionTableField {
}
