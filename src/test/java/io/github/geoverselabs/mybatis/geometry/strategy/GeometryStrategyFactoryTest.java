package io.github.geoverselabs.mybatis.geometry.strategy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeometryStrategyFactoryTest {

    @AfterEach
    void reset() {
        GeometryStrategyFactory.clearCache();
    }

    @Test
    void strategiesAreCachedPerType() {
        GeometryHandlerStrategy mysql = GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL);
        assertThat(mysql).isInstanceOf(MySQLGeometryStrategy.class)
            .isSameAs(GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL));
        assertThat(GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL))
            .isInstanceOf(PostGISGeometryStrategy.class);
    }

    @Test
    void defaultStrategyFallsBackToMySqlWithoutFreezingIt() {
        assertThat(GeometryStrategyFactory.getDefaultStrategy()).isInstanceOf(MySQLGeometryStrategy.class);

        GeometryHandlerStrategy postgis = GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL);
        GeometryStrategyFactory.setDefaultStrategy(postgis);
        assertThat(GeometryStrategyFactory.getDefaultStrategy()).isSameAs(postgis);

        GeometryStrategyFactory.setDefaultStrategy(null);
        assertThat(GeometryStrategyFactory.getDefaultStrategy()).isInstanceOf(MySQLGeometryStrategy.class);
    }

    @Test
    void clearCacheForgetsDefault() {
        GeometryStrategyFactory.setDefaultStrategy(new PostGISGeometryStrategy());
        GeometryStrategyFactory.clearCache();
        assertThat(GeometryStrategyFactory.getDefaultStrategy()).isInstanceOf(MySQLGeometryStrategy.class);
    }

    @ParameterizedTest
    @CsvSource({
        "jdbc:mysql://localhost:3306/db, MYSQL",
        "jdbc:mariadb://localhost:3306/db, MYSQL",
        "JDBC:MySQL://host/db, MYSQL",
        "'jdbc:mysql:loadbalance://h1,h2/db', MYSQL",
        "jdbc:postgresql://localhost:5432/db, POSTGRESQL",
        "jdbc:postgresql:db, POSTGRESQL",
        "jdbc:postgresql:mysql, POSTGRESQL",
        "jdbc:p6spy:mysql://localhost/db, MYSQL",
        "jdbc:log4jdbc:postgresql://localhost/db, POSTGRESQL",
        "jdbc:tc:postgis:16-3.4:///db, POSTGRESQL",
        "jdbc:tc:mysql:8.0:///db, MYSQL",
        "jdbc:aws-wrapper:postgresql://host/db, POSTGRESQL",
        "jdbc:postgresql://mysql-migration.internal:5432/gis, POSTGRESQL",
        "jdbc:postgresql://db.example.com/mariadb_archive, POSTGRESQL",
        "jdbc:mysql://postgres-host:3306/postgresql, MYSQL",
        "jdbc:pgsql://localhost/db, POSTGRESQL"
    })
    void detectsTypeFromUrlScheme(String url, DatabaseType expected) {
        assertThat(GeometryStrategyFactory.detectDatabaseType(url)).isEqualTo(expected);
        assertThat(GeometryStrategyFactory.fromJdbcUrl(url)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:h2:mem:mysql",
        "jdbc:h2:mysql",
        "jdbc:hsqldb:mem:postgresql",
        "jdbc:sqlserver://host;databaseName=postgresql",
        "jdbc:oracle:thin:@mysql-host:1521:postgres",
        "mysql://localhost/db",
        "jdbc:"
    })
    void unknownSchemesFallBackToMySql(String url) {
        assertThat(GeometryStrategyFactory.fromJdbcUrl(url)).isNull();
        assertThat(GeometryStrategyFactory.detectDatabaseType(url)).isEqualTo(DatabaseType.MYSQL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullOrEmptyUrlFallsBackToMySql(String url) {
        assertThat(GeometryStrategyFactory.detectDatabaseType(url)).isEqualTo(DatabaseType.MYSQL);
    }

    @Test
    void productNames() {
        assertThat(GeometryStrategyFactory.fromProductName("MySQL")).isEqualTo(DatabaseType.MYSQL);
        assertThat(GeometryStrategyFactory.fromProductName("MariaDB")).isEqualTo(DatabaseType.MYSQL);
        assertThat(GeometryStrategyFactory.fromProductName(" mariadb ")).isEqualTo(DatabaseType.MYSQL);
        assertThat(GeometryStrategyFactory.fromProductName("PostgreSQL")).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(GeometryStrategyFactory.fromProductName("Oracle")).isNull();
        assertThat(GeometryStrategyFactory.fromProductName("H2")).isNull();
        assertThat(GeometryStrategyFactory.fromProductName(null)).isNull();
    }

    @Test
    void dataSourceDetectionPrefersProductName() throws SQLException {
        Connection connection = connection("PostgreSQL", "jdbc:p6spy:unknown://mysql-host/db");
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        assertThat(GeometryStrategyFactory.detectDatabaseType(dataSource)).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(GeometryStrategyFactory.detectStrategy(dataSource)).isInstanceOf(PostGISGeometryStrategy.class);
        verify(connection, org.mockito.Mockito.times(2)).close();
    }

    @Test
    void dataSourceDetectionFallsBackToUrlScheme() throws SQLException {
        Connection connection = connection("SomeProxy", "jdbc:p6spy:postgresql://mysql-host/db");
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        assertThat(GeometryStrategyFactory.detectDatabaseType(dataSource)).isEqualTo(DatabaseType.POSTGRESQL);
    }

    @Test
    void dataSourceDetectionDefaultsToMySql() throws SQLException {
        DataSource unknown = mock(DataSource.class);
        Connection connection = connection("Oracle", "jdbc:oracle:thin:@host:1521:xe");
        when(unknown.getConnection()).thenReturn(connection);
        assertThat(GeometryStrategyFactory.detectDatabaseType(unknown)).isEqualTo(DatabaseType.MYSQL);

        DataSource failing = mock(DataSource.class);
        when(failing.getConnection()).thenThrow(new SQLException("down"));
        assertThat(GeometryStrategyFactory.detectDatabaseType(failing)).isEqualTo(DatabaseType.MYSQL);

        assertThat(GeometryStrategyFactory.detectDatabaseType((DataSource) null)).isEqualTo(DatabaseType.MYSQL);
    }

    @Test
    void connectionDetectionDoesNotCloseTheConnection() throws SQLException {
        Connection connection = connection("MariaDB", "jdbc:mariadb://localhost/db");
        assertThat(GeometryStrategyFactory.detectDatabaseType(connection)).isEqualTo(DatabaseType.MYSQL);
        verify(connection, never()).close();

        Connection failing = mock(Connection.class);
        when(failing.getMetaData()).thenThrow(new SQLException("closed"));
        assertThat(GeometryStrategyFactory.detectDatabaseType(failing)).isEqualTo(DatabaseType.MYSQL);
        assertThat(GeometryStrategyFactory.detectDatabaseType((Connection) null)).isEqualTo(DatabaseType.MYSQL);
    }

    private static Connection connection(String productName, String url) throws SQLException {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn(productName);
        when(metaData.getURL()).thenReturn(url);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metaData);
        return connection;
    }
}
