package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.MultiPoint;

import java.io.IOException;

/**
 * Jackson serializer for JTS MultiPoint to GeoJSON format.
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiPoint",
 *   "coordinates": [[longitude, latitude], [longitude, latitude], ...]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiPointSerializer.class)
 * @JsonDeserialize(using = MultiPointDeserializer.class)
 * private MultiPoint locations;
 * }</pre>
 */
public class MultiPointSerializer extends JsonSerializer<MultiPoint> {

    @Override
    public void serialize(MultiPoint multiPoint, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (multiPoint == null) {
            gen.writeNull();
            return;
        }

        gen.writeStartObject();
        gen.writeStringField("type", "MultiPoint");

        // Write coordinates array [[longitude, latitude], ...]
        gen.writeArrayFieldStart("coordinates");
        for (Coordinate coord : multiPoint.getCoordinates()) {
            gen.writeStartArray();
            gen.writeNumber(coord.x);  // longitude
            gen.writeNumber(coord.y);  // latitude
            gen.writeEndArray();
        }
        gen.writeEndArray();

        gen.writeEndObject();
    }
}
