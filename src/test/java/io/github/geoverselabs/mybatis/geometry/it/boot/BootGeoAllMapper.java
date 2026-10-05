package io.github.geoverselabs.mybatis.geometry.it.boot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.geoverselabs.mybatis.geometry.it.GeoAll;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper registered through Spring Boot mapper scanning in {@code SpringBootPostgisIT}.
 */
@Mapper
public interface BootGeoAllMapper extends BaseMapper<GeoAll> {
}
