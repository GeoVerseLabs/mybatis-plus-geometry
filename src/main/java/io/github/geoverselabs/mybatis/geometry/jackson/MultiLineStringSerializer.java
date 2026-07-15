package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;

import java.io.IOException;

/**
 * Jackson serializer for JTS MultiLineString to GeoJSON format.
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiLineString",
 *   "coordinates": [[[lon1, lat1], [lon2, lat2], ...], [[lon3, lat3], [lon4, lat4], ...]]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiLineStringSerializer.class)
 * @JsonDeserialize(using = MultiLineStringDeserializer.class)
 * private MultiLineString routes;
 * }</pre>
 */
public class MultiLineStringSerializer extends JsonSerializer<MultiLineString> {

    @Override
    public void serialize(MultiLineString multiLineString, JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
        if (multiLineString == null) {
            gen.writeNull();
            return;
        }

        gen.writeStartObject();
        gen.writeStringField("type", "MultiLineString");
        gen.writeArrayFieldStart("coordinates");

        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            LineString line = (LineString) multiLineString.getGeometryN(i);
            writeCoordinateArray(gen, line.getCoordinates());
        }

        gen.writeEndArray();
        gen.writeEndObject();
    }

    private void writeCoordinateArray(JsonGenerator gen, Coordinate[] coords) throws IOException {
        gen.writeStartArray();
        for (Coordinate c : coords) {
            gen.writeStartArray();
            gen.writeNumber(c.x);
            gen.writeNumber(c.y);
            gen.writeEndArray();
        }
        gen.writeEndArray();
    }
}
