package io.github.geoverselabs.mybatis.geometry.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.geoverselabs.mybatis.geometry.jackson.GeometryJacksonModule;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class GeoJsonStreamsPropertiesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new GeometryJacksonModule());

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Property(tries = 200)
    void sequenceWritesExactlyOneRecordPerItem(@ForAll("texts") List<String> items) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = GeoJsonStreams.writeSequence(MAPPER, items, out);

        byte[] bytes = out.toByteArray();
        assertThat(count).isEqualTo(items.size());
        // JSON escapes control characters, so separators and line feeds only appear as framing
        assertThat(occurrences(bytes, (byte) 0x1E)).isEqualTo(items.size());
        assertThat(occurrences(bytes, (byte) 0x0A)).isEqualTo(items.size());
        List<String> records = GeoJsonStreamsTest.records(bytes);
        List<String> parsed = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            assertThat(records.get(i)).isEqualTo(MAPPER.writeValueAsString(items.get(i)));
            parsed.add(MAPPER.readValue(records.get(i), String.class));
        }
        assertThat(parsed).isEqualTo(items);
    }

    @Property(tries = 200)
    void arrayRoundTrips(@ForAll("texts") List<String> items) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = GeoJsonStreams.writeArray(MAPPER, items, out);

        assertThat(count).isEqualTo(items.size());
        assertThat(MAPPER.readValue(out.toByteArray(), new TypeReference<List<String>>() { })).isEqualTo(items);
    }

    @Property(tries = 100)
    void featureSequenceHoldsTheFeaturesOfTheFeatureCollection(@ForAll("places") List<Place> places)
            throws IOException {
        Function<Place, Object> properties = place -> Map.of("name", place.name());
        ByteArrayOutputStream collection = new ByteArrayOutputStream();
        ByteArrayOutputStream sequence = new ByteArrayOutputStream();

        long collected = GeoJsonStreams.writeFeatureCollection(MAPPER, places,
            Place::id, Place::location, properties, collection);
        long sequenced = GeoJsonStreams.writeFeatureSequence(MAPPER, places,
            Place::id, Place::location, properties, sequence);

        assertThat(collected).isEqualTo(places.size());
        assertThat(sequenced).isEqualTo(places.size());
        JsonNode features = MAPPER.readTree(collection.toByteArray()).get("features");
        List<String> records = GeoJsonStreamsTest.records(sequence.toByteArray());
        assertThat(records).hasSize(places.size());
        for (int i = 0; i < places.size(); i++) {
            JsonNode feature = MAPPER.readTree(records.get(i));
            Place place = places.get(i);
            assertThat(feature).isEqualTo(features.get(i));
            assertThat(records.get(i)).as("compact record").isEqualTo(MAPPER.writeValueAsString(feature));
            assertThat(feature.get("type").asText()).isEqualTo("Feature");
            assertThat(feature.has("id")).isEqualTo(place.id() != null);
            assertThat(feature.get("properties").get("name").asText()).isEqualTo(place.name());
            JsonNode coordinates = feature.get("geometry").get("coordinates");
            assertThat(coordinates.get(0).doubleValue()).isEqualTo(place.location().getX());
            assertThat(coordinates.get(1).doubleValue()).isEqualTo(place.location().getY());
        }
    }

    @Provide
    Arbitrary<List<String>> texts() {
        return text().list().ofMaxSize(12);
    }

    @Provide
    Arbitrary<List<Place>> places() {
        Arbitrary<Place> place = Combinators.combine(
            Arbitraries.longs().injectNull(0.3),
            text(),
            Arbitraries.doubles().between(-180, 180).ofScale(12),
            Arbitraries.doubles().between(-90, 90).ofScale(12)
        ).as((id, name, x, y) -> new Place(id, name, GEOMETRY_FACTORY.createPoint(new Coordinate(x, y))));
        return place.list().ofMaxSize(8);
    }

    private static Arbitrary<String> text() {
        return Arbitraries.strings()
            .withCharRange('\u0000', 'ÿ')
            .withChars('\u001E', '\n', ' ', '北')
            .ofMaxLength(16);
    }

    private static int occurrences(byte[] bytes, byte value) {
        int count = 0;
        for (byte b : bytes) {
            if (b == value) {
                count++;
            }
        }
        return count;
    }

    record Place(Long id, String name, Point location) {
    }
}
