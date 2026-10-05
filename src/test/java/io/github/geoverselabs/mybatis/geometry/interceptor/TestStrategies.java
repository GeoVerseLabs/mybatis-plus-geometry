package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import org.locationtech.jts.geom.Geometry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * Minimal {@link GeometryHandlerStrategy} implementations whose SELECT wrapping is fixed by the test,
 * independent of the production strategies.
 */
final class TestStrategies {

    private TestStrategies() {
    }

    /** PostGIS hex EWKB read expression: {@code encode(ST_AsEWKB(col), 'hex') AS col}. */
    static Wrapping postgisEwkb() {
        return new Wrapping(DatabaseType.POSTGRESQL,
            column -> "encode(ST_AsEWKB(" + column + "), 'hex') AS " + simpleName(column));
    }

    /** MySQL style {@code HEX(col) AS col}. */
    static Wrapping mysqlHex() {
        return new Wrapping(DatabaseType.MYSQL, column -> "HEX(" + column + ") AS " + simpleName(column));
    }

    static String simpleName(String column) {
        int dot = column.lastIndexOf('.');
        return dot >= 0 ? column.substring(dot + 1) : column;
    }

    /** Strategy with a configurable wrapping function that counts its calls. */
    static class Wrapping implements GeometryHandlerStrategy {
        private final DatabaseType type;
        private final UnaryOperator<String> wrapping;
        final AtomicInteger wrapCalls = new AtomicInteger();

        Wrapping(DatabaseType type, UnaryOperator<String> wrapping) {
            this.type = type;
            this.wrapping = wrapping;
        }

        @Override
        public DatabaseType getSupportedDatabaseType() {
            return type;
        }

        @Override
        public String wrapColumnForSelect(String columnName) {
            wrapCalls.incrementAndGet();
            return wrapping.apply(columnName);
        }

        @Override
        public String getGeometryInputFunction() {
            return "?";
        }

        @Override
        public Object convertForDatabase(Geometry geometry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Geometry parseFromDatabase(Object dbValue) {
            throw new UnsupportedOperationException();
        }
    }
}
