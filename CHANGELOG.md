# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2025-07-03

### Added

- Initial release under GeoVerseLabs organization
- Support for JTS geometry types: Point, Polygon, LineString
- MyBatis Plus TypeHandlers for automatic WKB conversion
- Field annotations: `@PointTableField`, `@PolygonTableField`, `@LineStringTableField`
- Jackson serializers/deserializers for GeoJSON format
- `GeometryJacksonModule` — Unified Jackson Module that auto-registers Point/LineString/Polygon serializers and deserializers via Spring Boot `@AutoConfiguration`
- SQL interceptor for automatic HEX() wrapping in SELECT queries
- Spring Boot auto-configuration for 2.7+ and 3.x
- MySQL and PostgreSQL/PostGIS database support
- MariaDB 11.x compatibility
- Automatic database type detection from DataSource
- Configuration properties for SRID and interceptor settings
- Automatic coordinate validation for WGS84 (SRID 4326)
- `GeoJsonParseException` — Structured exceptions with explicit field names for malformed GeoJSON input
- `WkbCodec` interface — Clean abstraction for database-specific geometry encoding/decoding
- `GeometryFieldResolver` — Dedicated class for entity field scanning, annotation detection, and metadata caching
- Comprehensive documentation and examples

### Technical Highlights

- **WKB Codec via JTS** — `MySQLWkbCodec` and `PostGISWkbCodec` use JTS `WKBWriter`/`WKBReader` for all geometry types
- **TypeHandler Strategy Injection** — Supports both constructor injection (Spring) and no-arg reflection (MyBatis annotations)
- **Interceptor Decomposition** — Split into `GeometryFieldResolver` (reflection + caching) and `GeometrySqlRewriter` (SQL parsing + rewriting)
- **Hex Encoding** — Uses `java.util.HexFormat` (JDK 17+), no external codec dependency
- **PostGIS EWKB** — Produces standard EWKB hex format with SRID flag for direct PostGIS compatibility

### Database Support

- MySQL 8.0+ with native GEOMETRY type
- MariaDB 10.5+ with native GEOMETRY type
- PostgreSQL 12+ with PostGIS 3.0+ extension

### Dependencies

- JTS Core 1.19.0
- MyBatis Plus 3.5.7 (compileOnly)
- Spring Boot 2.7+ / 3.x (compileOnly)
- Jackson Databind (compileOnly)

[Unreleased]: https://github.com/GeoVerseLabs/mybatis-plus-geometry/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/GeoVerseLabs/mybatis-plus-geometry/releases/tag/v1.0.0
