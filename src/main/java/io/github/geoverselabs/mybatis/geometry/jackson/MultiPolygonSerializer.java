package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;

/**
 * Jackson serializer for JTS MultiPolygon to GeoJSON format.
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiPolygon",
 *   "coordinates": [
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring
 *       [[lon1, lat1], ...]  // interior rings (holes)
 *     ],
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]]
 *     ]
 *   ]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiPolygonSerializer.class)
 * @JsonDeserialize(using = MultiPolygonDeserializer.class)
 * private MultiPolygon areas;
 * }</pre>
 */
public class MultiPolygonSerializer extends JsonSerializer<MultiPolygon> {

    @Override
    public void serialize(MultiPolygon multiPolygon, JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
        if (multiPolygon == null) {
            gen.writeNull();
            return;
        }

        gen.writeStartObject();
        gen.writeStringField("type", "MultiPolygon");
        gen.writeArrayFieldStart("coordinates");

        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
            gen.writeStartArray();

            // Exterior ring
            writeCoordinateArray(gen, polygon.getExteriorRing().getCoordinates());

            // Interior rings (holes)
            for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
                writeCoordinateArray(gen, polygon.getInteriorRingN(j).getCoordinates());
            }

            gen.writeEndArray();
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
