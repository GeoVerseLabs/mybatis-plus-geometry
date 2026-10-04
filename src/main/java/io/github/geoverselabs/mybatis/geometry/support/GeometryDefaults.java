package io.github.geoverselabs.mybatis.geometry.support;

/**
 * Process-wide defaults used by components that MyBatis instantiates reflectively
 * (for example TypeHandlers referenced from {@code @TableField(typeHandler = ...)}), and
 * therefore cannot receive configuration through their constructors.
 *
 * <p>Spring Boot auto-configuration populates these values from {@code mybatis.geometry.*}
 * properties. Values are read on every use, so they may be changed at any time.</p>
 */
public final class GeometryDefaults {

    private static volatile GeometryValidation writeValidation = GeometryValidation.FULL;

    private GeometryDefaults() {
    }

    /**
     * Validation level applied by TypeHandlers before writing a geometry to the database.
     *
     * @return the current write validation level, never null
     */
    public static GeometryValidation getWriteValidation() {
        return writeValidation;
    }

    /**
     * Set the validation level applied by TypeHandlers before database writes.
     *
     * @param validation the level; null restores {@link GeometryValidation#FULL}
     */
    public static void setWriteValidation(GeometryValidation validation) {
        writeValidation = validation == null ? GeometryValidation.FULL : validation;
    }

    /**
     * Restore all defaults. Intended for tests.
     */
    public static void reset() {
        writeValidation = GeometryValidation.FULL;
    }
}
