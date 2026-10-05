package io.github.geoverselabs.mybatis.geometry.util;

import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryFactoryProviderTest {

    @AfterEach
    void reset() {
        GeometryFactoryProvider.reset();
    }

    @Test
    void defaultsToWgs84AndArraySequences() {
        GeometryFactory f = GeometryFactoryProvider.getFactory();
        assertThat(f.getSRID()).isEqualTo(4326);
        assertThat(GeometryFactoryProvider.getConfiguredSrid()).isEqualTo(4326);
        assertThat(GeometryFactoryProvider.getCoordinateSequenceType()).isEqualTo(CoordinateSequenceType.ARRAY);
        assertThat(f.getCoordinateSequenceFactory()).isSameAs(CoordinateArraySequenceFactory.instance());
        assertThat(f.createPoint(new Coordinate(1, 2)).getCoordinateSequence())
            .isInstanceOf(CoordinateArraySequence.class);
    }

    @Test
    void factoriesAreCachedPerSrid() {
        assertThat(GeometryFactoryProvider.getFactory(3857)).isSameAs(GeometryFactoryProvider.getFactory(3857));
        assertThat(GeometryFactoryProvider.getFactory(0)).isSameAs(GeometryFactoryProvider.getFactory(0));
        assertThat(GeometryFactoryProvider.getFactory(3857)).isNotSameAs(GeometryFactoryProvider.getFactory(4326));
        assertThat(GeometryFactoryProvider.getFactory(0).getSRID()).isZero();
        assertThat(GeometryFactoryProvider.getFactory(4326)).isSameAs(GeometryFactoryProvider.getFactory());
    }

    @Test
    void configuredSridDrivesDefaultFactory() {
        GeometryFactoryProvider.setDefaultSrid(3857);
        assertThat(GeometryFactoryProvider.getConfiguredSrid()).isEqualTo(3857);
        assertThat(GeometryFactoryProvider.getFactory().getSRID()).isEqualTo(3857);
        assertThat(GeometryFactoryProvider.getFactory()).isSameAs(GeometryFactoryProvider.getFactory(3857));

        GeometryFactoryProvider.setDefaultSrid(0);
        assertThat(GeometryFactoryProvider.getFactory().getSRID()).isZero();

        GeometryFactoryProvider.setDefaultSrid(4326);
        assertThat(GeometryFactoryProvider.getFactory().getSRID()).isEqualTo(4326);
    }

    @Test
    void packedSequenceType() {
        GeometryFactory arrayFactory = GeometryFactoryProvider.getFactory(3857);
        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);

        GeometryFactory packed = GeometryFactoryProvider.getFactory(3857);
        assertThat(GeometryFactoryProvider.getCoordinateSequenceType()).isEqualTo(CoordinateSequenceType.PACKED);
        assertThat(packed).isNotSameAs(arrayFactory);
        assertThat(packed.getSRID()).isEqualTo(3857);
        assertThat(packed.getCoordinateSequenceFactory()).isSameAs(PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
        Point p = GeometryFactoryProvider.getFactory().createPoint(new Coordinate(1, 2));
        assertThat(p.getCoordinateSequence()).isInstanceOf(PackedCoordinateSequence.Double.class);
        assertThat(packed).isSameAs(GeometryFactoryProvider.getFactory(3857));

        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);
        assertThat(GeometryFactoryProvider.getFactory(3857)).isSameAs(packed);
    }

    @Test
    void nullSequenceTypeMeansArray() {
        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);
        GeometryFactoryProvider.setCoordinateSequenceType(null);
        assertThat(GeometryFactoryProvider.getCoordinateSequenceType()).isEqualTo(CoordinateSequenceType.ARRAY);
        assertThat(GeometryFactoryProvider.getFactory().getCoordinateSequenceFactory())
            .isSameAs(CoordinateArraySequenceFactory.instance());
    }

    @Test
    void resetRestoresSridAndSequenceType() {
        GeometryFactoryProvider.setDefaultSrid(0);
        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);

        GeometryFactoryProvider.reset();

        assertThat(GeometryFactoryProvider.getConfiguredSrid()).isEqualTo(4326);
        assertThat(GeometryFactoryProvider.getCoordinateSequenceType()).isEqualTo(CoordinateSequenceType.ARRAY);
        assertThat(GeometryFactoryProvider.getFactory().getCoordinateSequenceFactory())
            .isSameAs(CoordinateArraySequenceFactory.instance());
    }

    @Test
    void cacheIsBoundedButFactoriesStayCorrect() {
        int count = GeometryFactoryProvider.MAX_CACHED_FACTORIES + 50;
        for (int srid = 100_000; srid < 100_000 + count; srid++) {
            assertThat(GeometryFactoryProvider.getFactory(srid).getSRID()).isEqualTo(srid);
        }
        // SRIDs cached before the limit keep their instance
        assertThat(GeometryFactoryProvider.getFactory(100_000)).isSameAs(GeometryFactoryProvider.getFactory(100_000));
        assertThat(GeometryFactoryProvider.getFactory(100_000 + count - 1).getSRID()).isEqualTo(100_000 + count - 1);
    }

    @Test
    void concurrentCallersShareOneInstance() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<GeometryFactory>> futures = IntStream.range(0, 64)
                .mapToObj(i -> pool.submit(() -> GeometryFactoryProvider.getFactory(32650)))
                .toList();
            Set<GeometryFactory> distinct = ConcurrentHashMap.newKeySet();
            for (Future<GeometryFactory> f : futures) {
                distinct.add(f.get());
            }
            assertThat(distinct).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
