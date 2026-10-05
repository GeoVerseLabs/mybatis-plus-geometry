package io.github.geoverselabs.mybatis.geometry.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.GeoAllMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.ParcelMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.PlainShopMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.Shop;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.ShopMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.SiteMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.SpotMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.WarehouseMapper;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.geoverselabs.mybatis.geometry.strategy.MySQLGeometryStrategy;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Runs real MyBatis-Plus statements through the full plugin chain (including MyBatis-Plus'
 * pagination interceptor) against a mocked JDBC driver and checks the SQL that reaches
 * {@code Connection.prepareStatement}.
 */
class GeometryFieldInterceptorTest {

    private final RecordingDataSource jdbc = new RecordingDataSource();

    @AfterEach
    void resetDefaultStrategy() {
        GeometryStrategyFactory.clearCache();
    }

    private DbType dbType = DbType.MYSQL;

    private SqlSessionFactory factory(Interceptor geometryInterceptor) {
        MybatisConfiguration configuration = TestConfigurations.standard();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), jdbc.dataSource()));
        MybatisPlusInterceptor mybatisPlus = new MybatisPlusInterceptor();
        mybatisPlus.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        configuration.addInterceptor(mybatisPlus);
        if (geometryInterceptor != null) {
            configuration.addInterceptor(geometryInterceptor);
        }
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    /** Run {@code action} against a mapper and return the (whitespace-normalised) SQL it prepared. */
    private <M> String sqlOf(Interceptor interceptor, Class<M> mapperType, Consumer<M> action) {
        try (SqlSession session = factory(interceptor).openSession(true)) {
            jdbc.clear();
            action.accept(session.getMapper(mapperType));
            return normalize(jdbc.lastSql());
        }
    }

    private String shopSql(Consumer<ShopMapper> action) {
        return sqlOf(new GeometryFieldInterceptor(new MySQLGeometryStrategy()), ShopMapper.class, action);
    }

    // ------------------------------------------------------------------ BaseMapper statements

    @Test
    void selectById() {
        assertThat(shopSql(m -> m.selectById(1L)))
            .isEqualTo("SELECT id,name,HEX(location) AS location FROM shop WHERE id=?");
    }

    @Test
    void selectListWithWrappers() {
        assertThat(shopSql(m -> m.selectList(Wrappers.<Shop>query().eq("name", "x"))))
            .isEqualTo("SELECT id,name,HEX(location) AS location FROM shop WHERE (name = ?)");
        assertThat(shopSql(m -> m.selectList(new QueryWrapper<Shop>().select("id", "location").orderByAsc("id"))))
            .isEqualTo("SELECT id,HEX(location) AS location FROM shop ORDER BY id ASC");
        assertThat(shopSql(m -> m.selectList(new QueryWrapper<Shop>().select("id", "name"))))
            .isEqualTo("SELECT id,name FROM shop");
        assertThat(shopSql(m -> m.selectList(new QueryWrapper<Shop>().select("DISTINCT location"))))
            .isEqualTo("SELECT DISTINCT HEX(location) AS location FROM shop");
        assertThat(shopSql(m -> m.selectOne(Wrappers.<Shop>query().eq("id", 1).last("LIMIT 1"))))
            .isEqualTo("SELECT id,name,HEX(location) AS location FROM shop WHERE (id = ?) LIMIT 1");
    }

    @Test
    void subqueriesInWrappersAreLeftAlone() {
        // audit sel-1: the outer select list has no geometry column, the subquery must not be touched
        assertThat(shopSql(m -> m.selectList(new QueryWrapper<Shop>().select("id", "name")
            .exists("SELECT * FROM region r WHERE r.shop_id = shop.id"))))
            .isEqualTo("SELECT id,name FROM shop WHERE (EXISTS (SELECT * FROM region r WHERE r.shop_id = shop.id))");
        assertThat(shopSql(m -> m.selectList(new QueryWrapper<Shop>()
            .inSql("id", "SELECT shop_id FROM region WHERE location IS NOT NULL"))))
            .isEqualTo("SELECT id,name,HEX(location) AS location FROM shop "
                + "WHERE (id IN (SELECT shop_id FROM region WHERE location IS NOT NULL))");
    }

    @Test
    void selectPageRecordsAndCount() {
        assertThat(shopSql(m -> m.selectPage(new Page<>(2, 10, false), Wrappers.<Shop>query().eq("name", "x"))))
            .isEqualTo("SELECT id,name,HEX(location) AS location FROM shop WHERE (name = ?) LIMIT ?,?");
        // with a count query (which finds no rows here, so the records query is skipped)
        assertThat(shopSql(m -> m.selectPage(new Page<>(1, 10), null)))
            .isEqualTo("SELECT COUNT(*) AS total FROM shop");
    }

    @Test
    void postgresPagination() {
        dbType = DbType.POSTGRE_SQL;
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(TestStrategies.postgisEwkb());
        assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectPage(new Page<>(3, 5, false), null)))
            .isEqualTo("SELECT id,name,encode(ST_AsEWKB(location), 'hex') AS location FROM shop LIMIT ? OFFSET ?");
    }

    @Test
    void logicDelete() {
        String sql = sqlOf(new GeometryFieldInterceptor(new MySQLGeometryStrategy()), ParcelMapper.class,
            m -> m.selectById(1L));
        assertThat(sql).startsWith("SELECT id,HEX(boundary) AS boundary,deleted FROM parcel WHERE id=?")
            .contains("deleted=0");
    }

    @Test
    void mapsObjectsAndCount() {
        assertThat(shopSql(m -> m.selectMaps(Wrappers.<Shop>query().select("id", "location"))))
            .isEqualTo("SELECT id,HEX(location) AS location FROM shop");
        assertThat(shopSql(m -> m.selectObjs(Wrappers.<Shop>query().select("location"))))
            .isEqualTo("SELECT HEX(location) AS location FROM shop");
        assertThat(shopSql(m -> m.selectCount(null))).isEqualTo("SELECT COUNT( * ) AS total FROM shop");
    }

    @Test
    void writesAreNotTouched() {
        assertThat(shopSql(m -> m.deleteById(1L))).isEqualTo("DELETE FROM shop WHERE id=?");
        assertThat(shopSql(m -> m.update(Wrappers.<Shop>update().set("name", "n").eq("location", "x"))))
            .isEqualTo("UPDATE shop SET name=? WHERE (location = ?)");
    }

    // ------------------------------------------------------------------ hand-written SQL

    @Test
    void selectStarIsExpanded() {
        // audit sel-2 / sel-10: keywords are not aliases, multi-line SQL and t.* are handled
        assertThat(shopSql(m -> m.findByName("x")))
            .isEqualTo("SELECT id, name, HEX(location) AS location FROM shop WHERE name = ?");
        assertThat(shopSql(m -> m.findByNameMultiLine("x")))
            .isEqualTo("SELECT id, name, HEX(location) AS location FROM shop WHERE name = ?");
        assertThat(shopSql(m -> m.findStarById(1L)))
            .isEqualTo("SELECT s.id, s.name, HEX(s.location) AS location FROM shop s WHERE s.id = ?");
    }

    @Test
    void aliasWithoutAs() {
        assertThat(shopSql(m -> m.implicitAlias(1L)))
            .isEqualTo("SELECT HEX(w.location) AS loc, w.name FROM shop w WHERE w.id = ?");
    }

    @Test
    void otherTablesAreNotTouched() {
        // audit sel-13: DTO results from another table, joined same-named columns
        assertThat(shopSql(ShopMapper::cities)).isEqualTo("SELECT id, location FROM city");
        assertThat(shopSql(ShopMapper::joinedMaps))
            .isEqualTo("SELECT s.id, c.location FROM shop s JOIN city c ON c.id = s.city_id");
        assertThat(shopSql(ShopMapper::union))
            .isEqualTo("SELECT id, location FROM shop UNION ALL SELECT id, location FROM shop_archive");
    }

    @Test
    void plainMyBatisMapperReturningTheEntity() {
        assertThat(sqlOf(new GeometryFieldInterceptor(new MySQLGeometryStrategy()), PlainShopMapper.class,
            PlainShopMapper::all)).isEqualTo("SELECT id, name, HEX(location) AS location FROM shop");
        assertThat(sqlOf(new GeometryFieldInterceptor(new MySQLGeometryStrategy()), PlainShopMapper.class,
            PlainShopMapper::maps)).isEqualTo("SELECT id, location FROM shop");
    }

    // ------------------------------------------------------------------ entity resolution

    @Test
    void everyGeometryColumnIsWrapped() {
        // audit sel-4: Multi*, GeometryCollection, Geometry, composed and direct annotations
        String sql = sqlOf(new GeometryFieldInterceptor(new MySQLGeometryStrategy()), GeoAllMapper.class,
            m -> m.selectById(1L));
        for (String column : List.of("pt", "ls", "pg", "mpt", "mls", "mpg", "gc", "geom", "composed", "deep",
            "direct", "geo_loc")) {
            assertThat(sql).contains("HEX(" + column + ") AS " + column);
        }
        assertThat(sql).startsWith("SELECT id,name,").contains(",text FROM geo_all");
    }

    @Test
    void columnNamesMatchMyBatisPlus() {
        // audit sel-5, sel-7, sel-8, sel-9: naming, quoted names, annotation order, @TableId(value)
        String sql = sqlOf(new GeometryFieldInterceptor(new MySQLGeometryStrategy()), SiteMapper.class,
            m -> m.selectById(1L));
        assertThat(sql).isEqualTo("SELECT pk_id,HEX(geo_j_s_o_n_point) AS geo_j_s_o_n_point,"
            + "HEX(location2_d) AS location2_d,HEX(`point`) AS `point`,HEX(annotated_first) AS annotated_first,"
            + "label FROM site WHERE pk_id=?");
    }

    @Test
    void globalColumnFormat() {
        MybatisConfiguration configuration = TestConfigurations.quotedColumns();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), jdbc.dataSource()));
        configuration.addInterceptor(new GeometryFieldInterceptor(new MySQLGeometryStrategy()));
        try (SqlSession session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            session.getMapper(SpotMapper.class).selectById(1L);
        }
        assertThat(normalize(jdbc.lastSql()))
            .contains(",HEX(`point`) AS `point`,HEX(shape) AS shape,`label` FROM spot WHERE ");
    }

    @Test
    void intermediateMapperInterfaces() {
        // audit sel-3: WarehouseMapper extends KeyedMapper<Long, Warehouse> extends SuperMapper<T>
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(new MySQLGeometryStrategy());
        assertThat(sqlOf(interceptor, WarehouseMapper.class, m -> m.selectById(1L)))
            .isEqualTo("SELECT id,name,HEX(area) AS area FROM warehouse WHERE id=?");
        assertThat(sqlOf(interceptor, WarehouseMapper.class, WarehouseMapper::maps))
            .isEqualTo("SELECT id, HEX(area) AS area FROM warehouse");
    }

    @Test
    void statementsWithoutMapperClassRunUnchanged() {
        MybatisConfiguration configuration = TestConfigurations.standard();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), jdbc.dataSource()));
        configuration.addInterceptor(new GeometryFieldInterceptor(new MySQLGeometryStrategy()));
        ResultMap resultMap = new ResultMap.Builder(configuration, "xmlOnly.rm", Map.class, new ArrayList<>()).build();
        configuration.addMappedStatement(new MappedStatement.Builder(configuration, "xmlOnly.findAll",
            new StaticSqlSource(configuration, "SELECT location FROM shop"), SqlCommandType.SELECT)
            .resultMaps(List.of(resultMap)).build());
        try (SqlSession session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            session.selectList("xmlOnly.findAll");
            session.clearCache();
            session.selectList("xmlOnly.findAll");
        }
        assertThat(jdbc.preparedSql()).containsExactly("SELECT location FROM shop", "SELECT location FROM shop");
    }

    // ------------------------------------------------------------------ strategy selection

    @Test
    void noArgInterceptorResolvesTheDefaultStrategyPerStatement() {
        // audit sel-17: created before the application configured the default strategy
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor();
        GeometryStrategyFactory.setDefaultStrategy(TestStrategies.postgisEwkb());
        assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L)))
            .isEqualTo("SELECT id,name,encode(ST_AsEWKB(location), 'hex') AS location FROM shop WHERE id=?");
        GeometryStrategyFactory.setDefaultStrategy(GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL));
        assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L)))
            .isEqualTo("SELECT id,name,HEX(location) AS location FROM shop WHERE id=?");
    }

    @Test
    void databaseTypePropertyPinsTheStrategy() {
        GeometryStrategyFactory.setDefaultStrategy(GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL));
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor();
        Properties properties = new Properties();
        properties.setProperty(GeometryFieldInterceptor.DATABASE_TYPE_PROPERTY, "postgresql");
        interceptor.setProperties(properties);

        GeometryHandlerStrategy postgis = GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL);
        assertThat(interceptor.sqlRewriter().currentStrategy()).isSameAs(postgis);
        String expression = GeometrySqlRewriter.wrapExpression(postgis, "location", SqlDialect.POSTGRESQL);
        assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L)))
            .isEqualTo(normalize("SELECT id,name," + expression + " AS location FROM shop WHERE id=?"));
    }

    @Test
    void databaseTypeValues() {
        assertThat(GeometryFieldInterceptor.parseDatabaseType("MySQL")).isEqualTo(DatabaseType.MYSQL);
        assertThat(GeometryFieldInterceptor.parseDatabaseType(" mariadb ")).isEqualTo(DatabaseType.MYSQL);
        assertThat(GeometryFieldInterceptor.parseDatabaseType("POSTGRESQL")).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(GeometryFieldInterceptor.parseDatabaseType("postgres")).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(GeometryFieldInterceptor.parseDatabaseType("PostGIS")).isEqualTo(DatabaseType.POSTGRESQL);
        assertThatThrownBy(() -> GeometryFieldInterceptor.parseDatabaseType("oracle"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("oracle");

        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(new MySQLGeometryStrategy());
        GeometrySqlRewriter before = interceptor.sqlRewriter();
        interceptor.setProperties(null);
        interceptor.setProperties(new Properties());
        Properties blank = new Properties();
        blank.setProperty(GeometryFieldInterceptor.DATABASE_TYPE_PROPERTY, " ");
        interceptor.setProperties(blank);
        assertThat(interceptor.sqlRewriter()).isSameAs(before);
        Properties invalid = new Properties();
        invalid.setProperty(GeometryFieldInterceptor.DATABASE_TYPE_PROPERTY, "db2");
        assertThatThrownBy(() -> interceptor.setProperties(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorsAcceptNulls() {
        GeometryStrategyFactory.setDefaultStrategy(GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL));
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(null, null);
        assertThat(interceptor.sqlRewriter().currentStrategy())
            .isSameAs(GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL));
        assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L)))
            .isEqualTo("SELECT id,name,HEX(location) AS location FROM shop WHERE id=?");
        assertThat(sqlOf(new GeometryFieldInterceptor((GeometryHandlerStrategy) null), ShopMapper.class,
            m -> m.selectById(1L))).isEqualTo("SELECT id,name,HEX(location) AS location FROM shop WHERE id=?");
    }

    // ------------------------------------------------------------------ failures

    @Test
    void failuresExecuteTheOriginalSqlAndAreLoggedOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger(GeometryFieldInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(
                new TestStrategies.Wrapping(DatabaseType.MYSQL, column -> {
                    throw new IllegalStateException("boom");
                }));
            for (int i = 0; i < 3; i++) {
                assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L)))
                    .isEqualTo("SELECT id,name,location FROM shop WHERE id=?");
            }
            assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN)).hasSize(1);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void resolverFailuresNeverBreakTheQuery() {
        Logger logger = (Logger) LoggerFactory.getLogger(GeometryFieldInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GeometryFieldResolver failing = new GeometryFieldResolver() {
                @Override
                GeometryColumns resolve(Class<?> entityClass) {
                    throw new NoClassDefFoundError("com/example/Missing");
                }
            };
            GeometryFieldInterceptor interceptor =
                new GeometryFieldInterceptor(failing, new GeometrySqlRewriter(new MySQLGeometryStrategy()));
            assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L)))
                .isEqualTo("SELECT id,name,location FROM shop WHERE id=?");
            assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L)))
                .isEqualTo("SELECT id,name,location FROM shop WHERE id=?");
            assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN)).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).contains(ShopMapper.class.getName() + ".selectById");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void interceptNeverThrowsForUnexpectedHandlers() throws Throwable {
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(new MySQLGeometryStrategy());
        StatementHandler handler = mock(StatementHandler.class);
        org.apache.ibatis.plugin.Invocation invocation = new org.apache.ibatis.plugin.Invocation(handler,
            StatementHandler.class.getMethod("getParameterHandler"), new Object[0]);
        assertThatCode(() -> interceptor.intercept(invocation)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ plugin plumbing and caches

    @Test
    void wrapsStatementHandlersOnly() {
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(new MySQLGeometryStrategy());
        Executor executor = mock(Executor.class);
        assertThat(interceptor.plugin(executor)).isSameAs(executor);
        Object plain = new Object();
        assertThat(interceptor.plugin(plain)).isSameAs(plain);
        Object wrapped = interceptor.plugin(mock(StatementHandler.class));
        assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
        assertThat(wrapped).isInstanceOf(StatementHandler.class);
    }

    @Test
    @SuppressWarnings("deprecation")
    void cachesCanBeCleared() {
        GeometryFieldInterceptor interceptor = new GeometryFieldInterceptor(new MySQLGeometryStrategy());
        assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L))).contains("HEX(location)");
        assertThat(interceptor.sqlRewriter().cacheSize()).isPositive();
        interceptor.clearCaches();
        assertThat(interceptor.sqlRewriter().cacheSize()).isZero();
        assertThat(sqlOf(interceptor, ShopMapper.class, m -> m.selectById(1L))).contains("HEX(location)");
        assertThatCode(GeometryFieldInterceptor::clearCache).doesNotThrowAnyException();
    }

    @Test
    void withoutTheInterceptorNothingIsRewritten() {
        assertThat(sqlOf(null, ShopMapper.class, m -> m.selectById(1L)))
            .isEqualTo("SELECT id,name,location FROM shop WHERE id=?");
    }
}
