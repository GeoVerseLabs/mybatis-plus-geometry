package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.mapper.Mapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.StatementEntityResolver.Target;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.KeyedMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.PlainShopMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.Shop;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.ShopMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.SuperMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.Warehouse;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.WarehouseMapper;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StatementEntityResolverTest {

    private static MybatisConfiguration configuration;

    @BeforeAll
    static void register() {
        configuration = TestConfigurations.standard();
    }

    private final StatementEntityResolver resolver = new StatementEntityResolver();

    private static MappedStatement statement(String id) {
        return configuration.getMappedStatement(id);
    }

    private static String id(Class<?> mapper, String method) {
        return mapper.getName() + "." + method;
    }

    // ------------------------------------------------------------------ generic type resolution

    interface Direct extends BaseMapper<Shop> {
    }

    interface Raw extends BaseMapper {
    }

    interface NestedArgument extends BaseMapper<List<String>> {
    }

    interface Unrelated extends Comparable<String> {
    }

    abstract static class GenericBase<A, B> implements Comparable<B> {
    }

    abstract static class Concrete extends GenericBase<Integer, Serializable> {
    }

    @Test
    void resolvesTheEntityThroughTheInterfaceHierarchy() {
        assertThat(StatementEntityResolver.resolveTypeArgument(ShopMapper.class, Mapper.class, 0)).isEqualTo(Shop.class);
        assertThat(StatementEntityResolver.resolveTypeArgument(Direct.class, BaseMapper.class, 0)).isEqualTo(Shop.class);
        // WarehouseMapper extends KeyedMapper<Long, Warehouse> extends SuperMapper<T> extends BaseMapper<T>
        assertThat(StatementEntityResolver.resolveTypeArgument(WarehouseMapper.class, Mapper.class, 0))
            .isEqualTo(Warehouse.class);
        assertThat(StatementEntityResolver.resolveTypeArgument(WarehouseMapper.class, KeyedMapper.class, 0))
            .isEqualTo(Long.class);
        assertThat(StatementEntityResolver.resolveTypeArgument(WarehouseMapper.class, SuperMapper.class, 0))
            .isEqualTo(Warehouse.class);
        assertThat(StatementEntityResolver.resolveTypeArgument(NestedArgument.class, Mapper.class, 0))
            .isEqualTo(List.class);
        assertThat(StatementEntityResolver.resolveTypeArgument(Concrete.class, Comparable.class, 0))
            .isEqualTo(Serializable.class);
    }

    @Test
    void unboundOrUnrelatedTypesResolveToNull() {
        assertThat(StatementEntityResolver.resolveTypeArgument(Raw.class, Mapper.class, 0)).isNull();
        assertThat(StatementEntityResolver.resolveTypeArgument(SuperMapper.class, Mapper.class, 0)).isNull();
        assertThat(StatementEntityResolver.resolveTypeArgument(Unrelated.class, Mapper.class, 0)).isNull();
        assertThat(StatementEntityResolver.resolveTypeArgument(String.class, Mapper.class, 0)).isNull();
        assertThat(StatementEntityResolver.resolveTypeArgument(ShopMapper.class, Mapper.class, 1)).isNull();
    }

    // ------------------------------------------------------------------ mapper lookup

    @Test
    void mapperClassComesFromTheRegistryFirst() {
        assertThat(StatementEntityResolver.findMapperClass(configuration, ShopMapper.class.getName()))
            .isSameAs(ShopMapper.class);
        // not registered in this configuration, but loadable (thread context class loader)
        assertThat(StatementEntityResolver.findMapperClass(new Configuration(), Direct.class.getName()))
            .isSameAs(Direct.class);
        assertThat(StatementEntityResolver.findMapperClass(null, Direct.class.getName())).isSameAs(Direct.class);
        assertThat(StatementEntityResolver.findMapperClass(configuration, "shopMapper")).isNull();
        assertThat(StatementEntityResolver.findMapperClass(configuration, "com.example.Missing")).isNull();
    }

    // ------------------------------------------------------------------ statements

    @Test
    void entityResultStatementsUseTheResultMapType() {
        // MyBatis-Plus 3.5.7 implements selectOne, selectPage and selectByMap on top of selectList
        for (String method : List.of("selectById", "selectList", "selectBatchIds", "findByName", "findStarById")) {
            Target target = resolver.resolve(statement(id(ShopMapper.class, method)));
            assertThat(target).as(method).isEqualTo(new Target(Shop.class, true));
        }
        assertThat(resolver.resolve(statement(id(WarehouseMapper.class, "selectById"))))
            .isEqualTo(new Target(Warehouse.class, true));
    }

    @Test
    void otherStatementsFallBackToTheMapperEntity() {
        for (String method : List.of("selectMaps", "selectObjs", "selectCount", "cities", "joinedMaps")) {
            Target target = resolver.resolve(statement(id(ShopMapper.class, method)));
            assertThat(target).as(method).isEqualTo(new Target(Shop.class, false));
        }
        assertThat(resolver.resolve(statement(id(WarehouseMapper.class, "maps"))))
            .isEqualTo(new Target(Warehouse.class, false));
    }

    @Test
    void plainMyBatisMappersReturningAnEntity() {
        assertThat(resolver.resolve(statement(id(PlainShopMapper.class, "all"))))
            .isEqualTo(new Target(Shop.class, true));
        assertThat(resolver.resolve(statement(id(PlainShopMapper.class, "maps")))).isNull();
    }

    @Test
    void statementsWithoutAMapperClass() {
        assertThat(resolver.resolve(adHoc("xmlOnly.findAll"))).isNull();
        assertThat(resolver.resolve(adHoc("noNamespace"))).isNull();
        assertThat(resolver.resolve(adHoc(".leadingDot"))).isNull();
    }

    @Test
    void resultsIncludingNegativesAreCachedPerStatementId() {
        MappedStatement selectById = statement(id(ShopMapper.class, "selectById"));
        Target first = resolver.resolve(selectById);
        assertThat(resolver.resolve(selectById)).isSameAs(first);
        assertThat(resolver.resolve(adHoc("xmlOnly.findAll"))).isNull();
        assertThat(resolver.resolve(adHoc("xmlOnly.findAll"))).isNull();
        assertThat(resolver.cacheSize()).isEqualTo(2);
        resolver.clear();
        assertThat(resolver.cacheSize()).isZero();
    }

    private static MappedStatement adHoc(String id) {
        Configuration plain = new Configuration();
        ResultMap resultMap = new ResultMap.Builder(plain, id + "-Inline", Map.class, new ArrayList<>()).build();
        return new MappedStatement.Builder(plain, id, new StaticSqlSource(plain, "SELECT location FROM shop"),
            SqlCommandType.SELECT).resultMaps(List.of(resultMap)).build();
    }
}
