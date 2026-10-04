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

    private static volatile GeometryValidation writeValidation = GeometryValidation.BASIC;

    private static volatile boolean preserveZ = false;

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
     * <p>The default is {@link GeometryValidation#BASIC}: databases store OGC-invalid geometries,
     * so a full {@code isValid()} check on every write rejects rows that were just read back and
     * costs O(n log n) per geometry. Use {@link GeometryValidation#FULL} to reject them.</p>
     *
     * @param validation the level; null restores {@link GeometryValidation#BASIC}
     */
    public static void setWriteValidation(GeometryValidation validation) {
        writeValidation = validation == null ? GeometryValidation.BASIC : validation;
    }

    /**
     * Whether Z ordinates are written to the database.
     *
     * @return true when geometries with Z values are written as 3D (PostGIS only)
     */
    public static boolean isPreserveZ() {
        return preserveZ;
    }

    /**
     * Write Z ordinates for geometries that have them. Only PostGIS supports 3D geometries;
     * MySQL and MariaDB always receive 2D WKB. Disabled by default because writing a 3D geometry
     * into a 2D PostGIS column fails, while previous versions silently dropped Z.
     *
     * @param enabled true to write 3D WKB for geometries with Z values
     */
    public static void setPreserveZ(boolean enabled) {
        preserveZ = enabled;
    }

    /**
     * Restore all defaults. Intended for tests.
     */
    public static void reset() {
        writeValidation = GeometryValidation.BASIC;
        preserveZ = false;
    }
}
