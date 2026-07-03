package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;

/**
 * Jackson serializer for JTS LineString to GeoJSON format.
 * 
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "LineString",
 *   "coordinates": [[lon1, lat1], [lon2, lat2], ...]
 * }
 * }</pre>
 * 
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = LineStringSerializer.class)
 * @JsonDeserialize(using = LineStringDeserializer.class)
 * private LineString route;
 * }</pre>
 */
public class LineStringSerializer extends JsonSerializer<LineString> {
    
    @Override
    public void serialize(LineString lineString, JsonGenerator gen, SerializerProvider provider) 
            throws IOException {
        if (lineString == null) {
            gen.writeNull();
            return;
        }
        
        gen.writeStartObject();
        gen.writeStringField("type", "LineString");
        
        // Write coordinates array
        gen.writeArrayFieldStart("coordinates");
        for (Coordinate coordinate : lineString.getCoordinates()) {
            gen.writeStartArray();
            gen.writeNumber(coordinate.x);  // longitude
            gen.writeNumber(coordinate.y);  // latitude
            gen.writeEndArray();
        }
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
