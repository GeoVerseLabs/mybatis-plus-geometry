package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.*;

import java.io.IOException;

/**
 * Jackson serializer for any JTS Geometry subtype to GeoJSON format.
 *
 * <p>Delegates to the type-specific serializer registered for the actual runtime type
 * via {@link SerializerProvider#defaultSerializeValue}.</p>
 *
 * <p>The instanceof checks are ordered so that Multi* types (which extend GeometryCollection)
 * are checked before GeometryCollection itself.</p>
 */
public class GenericGeometrySerializer extends JsonSerializer<Geometry> {

    @Override
    public void serialize(Geometry geometry, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (geometry == null) {
            gen.writeNull();
            return;
        }

        // Delegate to the specific serializer registered for the actual runtime type.
        // Multi* types must be checked before GeometryCollection since they are subclasses.
        if (geometry instanceof Point point) {
            provider.defaultSerializeValue(point, gen);
        } else if (geometry instanceof MultiPoint mp) {
            provider.defaultSerializeValue(mp, gen);
        } else if (geometry instanceof MultiLineString mls) {
            provider.defaultSerializeValue(mls, gen);
        } else if (geometry instanceof LineString ls) {
            provider.defaultSerializeValue(ls, gen);
        } else if (geometry instanceof MultiPolygon mp) {
            provider.defaultSerializeValue(mp, gen);
        } else if (geometry instanceof Polygon p) {
            provider.defaultSerializeValue(p, gen);
        } else if (geometry instanceof GeometryCollection gc) {
            provider.defaultSerializeValue(gc, gen);
        } else {
            throw new IOException("Unsupported geometry type: " + geometry.getGeometryType());
        }
    }
}
