package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import io.github.geoverselabs.mybatis.geometry.handler.AbstractGeometryTypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the geometry columns of entity classes and caches the result per class.
 *
 * <p>A field is a geometry column when its effective {@link TableField#typeHandler()} is an
 * {@link AbstractGeometryTypeHandler}: {@code @TableField(typeHandler = PointTypeHandler.class)}
 * on the field, any of the built-in {@code @*TableField} annotations, or a user-defined annotation
 * meta-annotated with one of them. As in MyBatis-Plus, the first {@code @TableField} found in
 * declaration order (searching meta-annotations depth first) decides; when it declares no type
 * handler, any other geometry {@code @TableField} on the field still marks it. Fields annotated
 * {@code @TableField(exist = false)}, static and transient fields are ignored.</p>
 *
 * <p>Column names come from MyBatis-Plus metadata ({@link TableInfoHelper#getTableInfo(Class)})
 * when the entity is registered, so {@code @TableField(value)}, {@code @TableId(value)},
 * {@code @TableName(excludeProperty)}, the global column format and
 * {@code mapUnderscoreToCamelCase} are honoured exactly. Otherwise they are derived by
 * reflection the way MyBatis-Plus does by default (annotation value, else the property name in
 * snake case).</p>
 */
public class GeometryFieldResolver {

    private static final Logger log = LoggerFactory.getLogger(GeometryFieldResolver.class);

    private final Map<Class<?>, GeometryColumns> metadataCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<String>> allFieldsCache = new ConcurrentHashMap<>();

    /**
     * Create a resolver with empty caches.
     */
    public GeometryFieldResolver() {
    }

    /**
     * Get geometry column names for an entity class.
     *
     * @param entityClass the entity class to scan
     * @return unmodifiable set of geometry column names, as MyBatis-Plus emits them (empty for null)
     */
    public Set<String> getGeometryFields(Class<?> entityClass) {
        return resolve(entityClass).geometryNames();
    }

    /**
     * Get all column names for an entity class: the key column followed by the field columns
     * when MyBatis-Plus metadata is available, otherwise every persistent field in declaration
     * order.
     *
     * @param entityClass the entity class to scan
     * @return unmodifiable ordered list of all column names (empty for null)
     */
    public List<String> getAllFields(Class<?> entityClass) {
        if (entityClass == null) {
            return Collections.emptyList();
        }
        return allFieldsCache.computeIfAbsent(entityClass, this::scanAllColumns);
    }

    /**
     * Clear caches. Useful for testing or reconfiguration (for example after entity classes were
     * reloaded).
     */
    public void clearCache() {
        metadataCache.clear();
        allFieldsCache.clear();
    }

    /**
     * Column metadata of an entity class, cached.
     *
     * @param entityClass the entity class; null yields {@link GeometryColumns#EMPTY}
     * @return the metadata, never null
     */
    GeometryColumns resolve(Class<?> entityClass) {
        if (entityClass == null) {
            return GeometryColumns.EMPTY;
        }
        return metadataCache.computeIfAbsent(entityClass, this::scan);
    }

    /**
     * Whether {@code field} is mapped to a geometry column through its effective
     * {@link TableField#typeHandler()} (see the class documentation).
     *
     * @param field the entity field
     * @return true for geometry fields
     */
    static boolean isGeometryField(Field field) {
        List<TableField> tableFields = findAnnotations(field.getDeclaredAnnotations(), TableField.class);
        if (tableFields.isEmpty()) {
            return false;
        }
        Class<?> effective = tableFields.get(0).typeHandler();
        if (effective != UnknownTypeHandler.class) {
            return isGeometryTypeHandler(effective);
        }
        for (TableField tableField : tableFields) {
            if (isGeometryTypeHandler(tableField.typeHandler())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGeometryTypeHandler(Class<?> typeHandler) {
        return typeHandler != null && AbstractGeometryTypeHandler.class.isAssignableFrom(typeHandler);
    }

    private GeometryColumns scan(Class<?> entityClass) {
        TableInfo tableInfo = tableInfo(entityClass);
        GeometryColumns columns = tableInfo != null ? fromTableInfo(tableInfo) : fromReflection(entityClass);
        log.debug("Resolved geometry columns of {}: {}", entityClass.getName(), columns);
        return columns;
    }

    private List<String> scanAllColumns(Class<?> entityClass) {
        TableInfo tableInfo = tableInfo(entityClass);
        if (tableInfo != null) {
            return Collections.unmodifiableList(tableInfoColumns(tableInfo));
        }
        List<String> columns = new ArrayList<>();
        for (Field field : persistentFields(entityClass)) {
            String column = reflectedColumnName(field);
            if (!columns.contains(column)) {
                columns.add(column);
            }
        }
        return Collections.unmodifiableList(columns);
    }

    private static TableInfo tableInfo(Class<?> entityClass) {
        try {
            return TableInfoHelper.getTableInfo(entityClass);
        } catch (RuntimeException | LinkageError e) {
            log.debug("MyBatis-Plus table metadata unavailable for {}: {}", entityClass.getName(), e.toString());
            return null;
        }
    }

    private static GeometryColumns fromTableInfo(TableInfo tableInfo) {
        List<String> geometry = new ArrayList<>();
        for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
            Class<?> typeHandler = fieldInfo.getTypeHandler();
            boolean isGeometry = typeHandler != null
                ? isGeometryTypeHandler(typeHandler)
                : fieldInfo.getField() != null && isGeometryField(fieldInfo.getField());
            if (isGeometry) {
                geometry.add(fieldInfo.getColumn());
            }
        }
        return new GeometryColumns(tableInfo.getTableName(), geometry, tableInfoColumns(tableInfo));
    }

    private static List<String> tableInfoColumns(TableInfo tableInfo) {
        List<String> columns = new ArrayList<>();
        if (StringUtils.isNotBlank(tableInfo.getKeyColumn())) {
            columns.add(tableInfo.getKeyColumn());
        }
        for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
            columns.add(fieldInfo.getColumn());
        }
        return columns;
    }

    /**
     * Fallback for classes without MyBatis-Plus metadata: geometry columns by reflection. The
     * column list stays unknown, so {@code SELECT *} is not expanded for such classes.
     */
    private static GeometryColumns fromReflection(Class<?> entityClass) {
        List<String> geometry = new ArrayList<>();
        for (Field field : persistentFields(entityClass)) {
            if (isGeometryField(field)) {
                geometry.add(reflectedColumnName(field));
            }
        }
        TableName tableName = findClassAnnotation(entityClass, TableName.class);
        String table = tableName != null && StringUtils.isNotBlank(tableName.value()) ? tableName.value() : null;
        return new GeometryColumns(table, geometry, null);
    }

    /**
     * Non-static, non-transient fields (subclass first, overridden fields once) that are neither
     * {@code @TableField(exist = false)} nor listed in {@code @TableName(excludeProperty)}.
     */
    private static List<Field> persistentFields(Class<?> entityClass) {
        TableName tableName = findClassAnnotation(entityClass, TableName.class);
        Set<String> excluded = tableName == null
            ? Collections.emptySet()
            : new HashSet<>(Arrays.asList(tableName.excludeProperty()));
        List<Field> fields = new ArrayList<>();
        for (Field field : ReflectionKit.getFieldList(entityClass)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                || excluded.contains(field.getName())) {
                continue;
            }
            List<TableField> tableFields = findAnnotations(field.getDeclaredAnnotations(), TableField.class);
            if (!tableFields.isEmpty() && !tableFields.get(0).exist()) {
                continue;
            }
            fields.add(field);
        }
        return fields;
    }

    /**
     * Column name of a field the way MyBatis-Plus derives it by default: {@code @TableId} or the
     * effective {@code @TableField} value, else the property name in snake case
     * ({@code geoJSONPoint} becomes {@code geo_j_s_o_n_point}).
     */
    private static String reflectedColumnName(Field field) {
        List<TableId> tableIds = findAnnotations(field.getDeclaredAnnotations(), TableId.class);
        if (!tableIds.isEmpty() && StringUtils.isNotBlank(tableIds.get(0).value())) {
            return tableIds.get(0).value();
        }
        List<TableField> tableFields = findAnnotations(field.getDeclaredAnnotations(), TableField.class);
        if (!tableFields.isEmpty() && StringUtils.isNotBlank(tableFields.get(0).value())) {
            return tableFields.get(0).value();
        }
        return StringUtils.camelToUnderline(field.getName());
    }

    private static <A extends Annotation> A findClassAnnotation(Class<?> type, Class<A> annotationType) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            List<A> found = findAnnotations(current.getDeclaredAnnotations(), annotationType);
            if (!found.isEmpty()) {
                return found.get(0);
            }
        }
        return null;
    }

    /**
     * Every annotation of type {@code annotationType} among {@code annotations} and their
     * meta-annotations, in the order MyBatis-Plus searches them (declaration order, depth first,
     * each composed annotation type expanded once). The first element is the one MyBatis-Plus
     * uses.
     */
    static <A extends Annotation> List<A> findAnnotations(Annotation[] annotations, Class<A> annotationType) {
        List<A> found = new ArrayList<>(1);
        collect(annotations, annotationType, new HashSet<>(), found);
        return found;
    }

    private static <A extends Annotation> void collect(Annotation[] annotations, Class<A> annotationType,
                                                       Set<Class<? extends Annotation>> visited, List<A> found) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (annotationType.isAssignableFrom(type)) {
                found.add(annotationType.cast(annotation));
            } else if (!type.getName().startsWith("java.lang.annotation.") && visited.add(type)) {
                collect(type.getDeclaredAnnotations(), annotationType, visited, found);
            }
        }
    }
}
