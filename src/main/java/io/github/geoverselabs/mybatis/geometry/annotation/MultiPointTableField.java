package io.github.geoverselabs.mybatis.geometry.annotation;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.handler.MultiPointTypeHandler;

import java.lang.annotation.*;

/**
 * Annotation to mark a field as JTS MultiPoint type.
 * Automatically binds the MultiPointTypeHandler for database conversion.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @TableName(value = "doc_sensor_cluster", autoResultMap = true)
 * public class SensorCluster extends BaseEntity {
 *     @MultiPointTableField
 *     private MultiPoint locations;
 * }
 * }</pre>
 *
 * <p>Note: The entity class must have {@code autoResultMap = true} in @TableName
 * for the TypeHandler to work correctly with SELECT queries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@TableField(typeHandler = MultiPointTypeHandler.class)
public @interface MultiPointTableField {
}
