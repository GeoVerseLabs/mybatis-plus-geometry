package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;

import java.io.IOException;

/**
 * Jackson serializer for JTS GeometryCollection to GeoJSON format.
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "GeometryCollection",
 *   "geometries": [
 *     { "type": "Point", "coordinates": [x, y] },
 *     { "type": "LineString", "coordinates": [[x1,y1], [x2,y2]] }
 *   ]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = GeometryCollectionSerializer.class)
 * @JsonDeserialize(using = GeometryCollectionDeserializer.class)
 * private GeometryCollection geometryCollection;
 * }</pre>
 */
public class GeometryCollectionSerializer extends JsonSerializer<GeometryCollection> {

    @Override
    public void serialize(GeometryCollection collection, JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
        if (collection == null) {
            gen.writeNull();
            return;
        }

        gen.writeStartObject();
        gen.writeStringField("type", "GeometryCollection");
        gen.writeArrayFieldStart("geometries");

        for (int i = 0; i < collection.getNumGeometries(); i++) {
            Geometry geom = collection.getGeometryN(i);
            provider.defaultSerializeValue(geom, gen);
        }

        gen.writeEndArray();
        gen.writeEndObject();
    }
}
