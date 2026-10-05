package io.github.geoverselabs.mybatis.geometry.stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoJsonMediaTypesTest {

    @Test
    void definesTheRegisteredMediaTypes() {
        assertThat(GeoJsonMediaTypes.GEO_JSON).isEqualTo("application/geo+json");
        assertThat(GeoJsonMediaTypes.GEO_JSON_SEQ).isEqualTo("application/geo+json-seq");
        assertThat(GeoJsonMediaTypes.JSON_SEQ).isEqualTo("application/json-seq");
    }
}
