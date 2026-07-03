package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;

/**
 * Jackson serializer for JTS Polygon to GeoJSON format.
 * 
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "Polygon",
 *   "coordinates": [
 *     [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring
 *     [[lon1, lat1], ...]  // interior rings (holes)
 *   ]
 * }
 * }</pre>
 * 
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = PolygonSerializer.class)
 * @JsonDeserialize(using = PolygonDeserializer.class)
 * private Polygon boundary;
 * }</pre>
 */
public class PolygonSerializer extends JsonSerializer<Polygon> {
    
    @Override
    public void serialize(Polygon polygon, JsonGenerator gen, SerializerProvider provider) 
            throws IOException {
        if (polygon == null) {
            gen.writeNull();
            return;
        }
        
        gen.writeStartObject();
        gen.writeStringField("type", "Polygon");
        
        // Start coordinates array
        gen.writeArrayFieldStart("coordinates");
        
        // Write exterior ring
        writeCoordinateArray(gen, polygon.getExteriorRing().getCoordinates());
        
        // Write interior rings (holes)
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            writeCoordinateArray(gen, polygon.getInteriorRingN(i).getCoordinates());
        }
        
        gen.writeEndArray();
        gen.writeEndObject();
    }
    
    private void writeCoordinateArray(JsonGenerator gen, Coordinate[] coordinates) throws IOException {
        gen.writeStartArray();
        for (Coordinate coordinate : coordinates) {
            gen.writeStartArray();
            gen.writeNumber(coordinate.x);  // longitude
            gen.writeNumber(coordinate.y);  // latitude
            gen.writeEndArray();
        }
        gen.writeEndArray();
    }
}
