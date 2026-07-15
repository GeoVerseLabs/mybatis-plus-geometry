package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.MultiLineStringTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as JTS MultiLineString type.
 * Automatically binds the MultiLineStringTypeHandler for database conversion.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_road_network", autoResultMap = true)
 * public class RoadNetwork extends BaseEntity {
 *     @MultiLineStringTableField
 *     private MultiLineString roads;
 * }
 * }</pre>
 *
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = MultiLineStringTypeHandler.class)
public @interface MultiLineStringTableField {
}
