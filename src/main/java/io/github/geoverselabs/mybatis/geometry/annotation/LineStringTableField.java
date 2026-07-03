package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.LineStringTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as JTS LineString type.
 * Automatically binds the LineStringTypeHandler for database conversion.
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_route", autoResultMap = true)
 * public class Route extends BaseEntity {
 *     @LineStringTableField
 *     private LineString path;
 * }
 * }</pre>
 * 
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = LineStringTypeHandler.class)
public @interface LineStringTableField {
}
