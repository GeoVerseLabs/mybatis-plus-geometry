package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

/**
 * Jackson serializer for JTS Point to GeoJSON format.
 * 
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "Point",
 *   "coordinates": [longitude, latitude]
 * }
 * }</pre>
 * 
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = PointSerializer.class)
 * @JsonDeserialize(using = PointDeserializer.class)
 * private Point location;
 * }</pre>
 */
public class PointSerializer extends JsonSerializer<Point> {
    
    @Override
    public void serialize(Point point, JsonGenerator gen, SerializerProvider provider) 
            throws IOException {
        if (point == null) {
            gen.writeNull();
            return;
        }
        
        gen.writeStartObject();
        gen.writeStringField("type", "Point");
        
        // Write coordinates array [longitude, latitude]
        gen.writeArrayFieldStart("coordinates");
        gen.writeNumber(point.getX());  // longitude
        gen.writeNumber(point.getY());  // latitude
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
