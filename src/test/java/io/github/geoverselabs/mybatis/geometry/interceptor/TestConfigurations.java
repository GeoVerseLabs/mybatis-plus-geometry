package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import io.github.geoverselabs.mybatis.geometry.handler.PointTypeHandler;

/**
 * Builds MyBatis-Plus configurations with mappers registered, which populates
 * {@code TableInfoHelper} exactly as an application start-up does.
 */
final class TestConfigurations {

    private TestConfigurations() {
    }

    /**
     * Default MyBatis-Plus configuration with the shared fixtures registered. The Point handler is
     * registered globally, as the Spring Boot auto-configuration does, so fields whose first
     * {@code @TableField} declares no type handler still map.
     */
    static synchronized MybatisConfiguration standard() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(PointTypeHandler.class);
        configuration.addMapper(TestEntities.ShopMapper.class);
        configuration.addMapper(TestEntities.PlainShopMapper.class);
        configuration.addMapper(TestEntities.GeoAllMapper.class);
        configuration.addMapper(TestEntities.SiteMapper.class);
        configuration.addMapper(TestEntities.WarehouseMapper.class);
        configuration.addMapper(TestEntities.ParcelMapper.class);
        return configuration;
    }

    /** Global column format {@code `%s`}: every column is quoted with backticks. */
    static synchronized MybatisConfiguration quotedColumns() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.getDbConfig().setColumnFormat("`%s`");
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.addMapper(TestEntities.SpotMapper.class);
        return configuration;
    }

    /** {@code mapUnderscoreToCamelCase = false}: columns are the property names. */
    static synchronized MybatisConfiguration noCamelCase() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.addMapper(TestEntities.CamelMapper.class);
        return configuration;
    }
}
