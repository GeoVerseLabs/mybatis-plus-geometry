package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.annotation.TableField;
import io.github.geoverselabs.mybatis.geometry.annotation.LineStringTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.PointTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.PolygonTableField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for scanning entity classes via reflection to identify
 * geometry-annotated fields and caching the metadata.
 *
 * <p>Extracted from GeometryFieldInterceptor for single responsibility
 * and independent testability.</p>
 */
public class GeometryFieldResolver {

    private static final Logger log = LoggerFactory.getLogger(GeometryFieldResolver.class);

    private final Map<Class<?>, Set<String>> geometryFieldsCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<String>> allFieldsCache = new ConcurrentHashMap<>();

    /**
     * Get geometry column names for an entity class.
     *
     * @param entityClass the entity class to scan
     * @return unmodifiable set of geometry column names
     */
    public Set<String> getGeometryFields(Class<?> entityClass) {
        return geometryFieldsCache.computeIfAbsent(entityClass, this::scanGeometryFields);
    }

    /**
     * Get all column names for an entity class, preserving declaration order.
     *
     * @param entityClass the entity class to scan
     * @return unmodifiable ordered list of all column names
     */
    public List<String> getAllFields(Class<?> entityClass) {
        return allFieldsCache.computeIfAbsent(entityClass, this::scanAllFields);
    }

    /**
     * Clear caches. Useful for testing or reconfiguration.
     */
    public void clearCache() {
        geometryFieldsCache.clear();
        allFieldsCache.clear();
    }

    private Set<String> scanGeometryFields(Class<?> clazz) {
        Set<String> fields = new HashSet<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (isGeometryField(field)) {
                    fields.add(resolveColumnName(field));
                }
            }
            current = current.getSuperclass();
        }
        log.debug("Scanned geometry fields for {}: {}", clazz.getSimpleName(), fields);
        return Collections.unmodifiableSet(fields);
    }

    private List<String> scanAllFields(Class<?> clazz) {
        List<String> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                    || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                TableField tf = field.getAnnotation(TableField.class);
                if (tf != null && !tf.exist()) {
                    continue;
                }
                String col = resolveColumnName(field);
                if (!fields.contains(col)) {
                    fields.add(col);
                }
            }
            current = current.getSuperclass();
        }
        log.debug("Scanned all fields for {}: {}", clazz.getSimpleName(), fields);
        return Collections.unmodifiableList(fields);
    }

    private boolean isGeometryField(Field field) {
        return field.isAnnotationPresent(PointTableField.class)
            || field.isAnnotationPresent(PolygonTableField.class)
            || field.isAnnotationPresent(LineStringTableField.class);
    }

    private String resolveColumnName(Field field) {
        TableField tf = field.getAnnotation(TableField.class);
        if (tf != null && !tf.value().isEmpty()) {
            return tf.value();
        }
        return camelToSnake(field.getName());
    }

    private String camelToSnake(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
