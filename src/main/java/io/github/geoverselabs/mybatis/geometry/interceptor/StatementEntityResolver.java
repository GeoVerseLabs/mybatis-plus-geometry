package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.core.mapper.Mapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the entity class whose geometry columns apply to a {@link MappedStatement}, cached per
 * statement id (including negative results).
 *
 * <ol>
 *   <li>The type of the statement's result map, when MyBatis-Plus has table metadata for it
 *       (every entity-returning BaseMapper method, {@code @ResultMap("mybatis-plus_Entity")}, and
 *       plain MyBatis statements that return an entity). Rows are mapped onto that entity, so its
 *       geometry columns are wrapped whatever table the SQL reads.</li>
 *   <li>Otherwise the {@code T} of the mapper's {@code Mapper<T>}/{@code BaseMapper<T>} super
 *       interface, resolved through the whole generic interface hierarchy
 *       ({@code UserMapper extends SuperMapper<User>}, {@code MyBase<ID, T> extends BaseMapper<T>}).
 *       This covers {@code selectMaps}/{@code selectObjs} and DTO statements; since the rows are
 *       not mapped onto the entity, such statements are only rewritten when they read the
 *       entity's table.</li>
 * </ol>
 *
 * <p>The mapper interface is taken from the statement's {@link Configuration} (the exact class
 * MyBatis registered, so child class loaders and devtools restarts work), falling back to
 * {@link Resources#classForName(String)}, which tries the thread context class loader.</p>
 */
final class StatementEntityResolver {

    private static final Logger log = LoggerFactory.getLogger(StatementEntityResolver.class);

    /**
     * Resolved entity of a statement.
     *
     * @param entityClass the entity class
     * @param resultType  true when the rows are mapped onto the entity (result map type)
     */
    record Target(Class<?> entityClass, boolean resultType) {
    }

    private final Map<String, Optional<Target>> cache = new ConcurrentHashMap<>();

    /**
     * Resolve (cached) the entity of {@code statement}.
     *
     * @return the target, or null when none applies
     */
    Target resolve(MappedStatement statement) {
        String id = statement.getId();
        if (id == null) {
            return compute(statement);
        }
        Optional<Target> cached = cache.get(id);
        if (cached == null) {
            cached = Optional.ofNullable(compute(statement));
            cache.put(id, cached);
        }
        return cached.orElse(null);
    }

    void clear() {
        cache.clear();
    }

    int cacheSize() {
        return cache.size();
    }

    private static Target compute(MappedStatement statement) {
        if (statement.getResultMaps() != null) {
            for (ResultMap resultMap : statement.getResultMaps()) {
                Class<?> type = resultMap.getType();
                if (type != null && hasTableInfo(type)) {
                    return new Target(type, true);
                }
            }
        }
        String id = statement.getId();
        int dot = id == null ? -1 : id.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        Class<?> mapperClass = findMapperClass(statement.getConfiguration(), id.substring(0, dot));
        if (mapperClass == null) {
            return null;
        }
        Class<?> entity = resolveTypeArgument(mapperClass, Mapper.class, 0);
        if (entity == null) {
            log.debug("No entity type found for statement {}", id);
            return null;
        }
        return new Target(entity, false);
    }

    private static boolean hasTableInfo(Class<?> type) {
        try {
            return TableInfoHelper.getTableInfo(type) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The mapper interface registered for {@code namespace}, or the class of that name.
     */
    static Class<?> findMapperClass(Configuration configuration, String namespace) {
        if (configuration != null && configuration.getMapperRegistry() != null) {
            Collection<Class<?>> mappers = configuration.getMapperRegistry().getMappers();
            for (Class<?> mapper : mappers) {
                if (mapper.getName().equals(namespace)) {
                    return mapper;
                }
            }
        }
        try {
            return Resources.classForName(namespace);
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("Statement namespace {} is not a loadable class", namespace);
            return null;
        }
    }

    /**
     * Resolve the class bound to type parameter {@code index} of the generic type {@code target}
     * as seen from {@code type}, following type variables through super interfaces and super
     * classes.
     *
     * @return the bound class (the raw class of a parameterized argument), or null when it is
     *         unbound or {@code target} is not a super type
     */
    static Class<?> resolveTypeArgument(Class<?> type, Class<?> target, int index) {
        return resolve(type, target, index, Map.of(), new HashSet<>());
    }

    private static Class<?> resolve(Type type, Class<?> target, int index, Map<TypeVariable<?>, Type> bindings,
                                    Set<Class<?>> visited) {
        Class<?> raw;
        Map<TypeVariable<?>, Type> own = Map.of();
        if (type instanceof Class<?> c) {
            raw = c;
        } else if (type instanceof ParameterizedType parameterized
            && parameterized.getRawType() instanceof Class<?> c) {
            raw = c;
            TypeVariable<?>[] parameters = c.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            if (parameters.length == arguments.length) {
                own = new HashMap<>();
                for (int i = 0; i < parameters.length; i++) {
                    own.put(parameters[i], substitute(arguments[i], bindings));
                }
            }
        } else {
            return null;
        }
        if (raw == target) {
            TypeVariable<?>[] parameters = raw.getTypeParameters();
            return index < parameters.length ? toClass(own.get(parameters[index])) : null;
        }
        if (!visited.add(raw)) {
            return null;
        }
        for (Type superInterface : raw.getGenericInterfaces()) {
            Class<?> found = resolve(superInterface, target, index, own, visited);
            if (found != null) {
                return found;
            }
        }
        Type superClass = raw.getGenericSuperclass();
        return superClass == null ? null : resolve(superClass, target, index, own, visited);
    }

    private static Type substitute(Type argument, Map<TypeVariable<?>, Type> bindings) {
        if (argument instanceof TypeVariable<?> variable) {
            return bindings.get(variable);
        }
        return argument;
    }

    private static Class<?> toClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> c) {
            return c;
        }
        if (type instanceof WildcardType wildcard && wildcard.getUpperBounds().length == 1
            && wildcard.getLowerBounds().length == 0) {
            return toClass(wildcard.getUpperBounds()[0]);
        }
        return null;
    }
}
