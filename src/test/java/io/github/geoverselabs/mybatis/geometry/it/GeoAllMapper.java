package io.github.geoverselabs.mybatis.geometry.it;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface GeoAllMapper extends BaseMapper<GeoAll> {

    /** Hand-written SQL with a table alias, mapped through the MyBatis-Plus auto result map. */
    @Select("SELECT g.id, g.name, g.pt, g.mpg, g.geom FROM geo_all g WHERE g.id = #{id}")
    @ResultMap("mybatis-plus_GeoAll")
    GeoAll selectWithAlias(@Param("id") Long id);
}
