package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.PointTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as JTS Point type.
 * Automatically binds the PointTypeHandler for database conversion.
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_warehouse", autoResultMap = true)
 * public class Warehouse extends BaseEntity {
 *     @PointTableField
 *     private Point location;
 * }
 * }</pre>
 * 
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = PointTypeHandler.class)
public @interface PointTableField {
}
