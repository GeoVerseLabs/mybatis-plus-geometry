package io.github.geoverselabs.mybatis.geometry.it;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.geoverselabs.mybatis.geometry.annotation.PointTableField;
import org.locationtech.jts.geom.Point;

/**
 * Entity for a table whose point column is constrained to SRID 4326 by the database.
 */
@TableName(value = "geo_srid", autoResultMap = true)
public class GeoSridPoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @PointTableField
    private Point pt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Point getPt() { return pt; }
    public void setPt(Point pt) { this.pt = pt; }
}
