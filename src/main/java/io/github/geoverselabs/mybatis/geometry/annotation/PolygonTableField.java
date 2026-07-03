package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.PolygonTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as JTS Polygon type.
 * Automatically binds the PolygonTypeHandler for database conversion.
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_zone", autoResultMap = true)
 * public class Zone extends BaseEntity {
 *     @PolygonTableField
 *     private Polygon boundary;
 * }
 * }</pre>
 * 
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = PolygonTypeHandler.class)
public @interface PolygonTableField {
}
