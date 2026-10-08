# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-10-08

### Fixed

- Reading entities with `MultiPoint`, `MultiLineString`, `MultiPolygon`, `GeometryCollection` or generic `Geometry` fields failed on every database: the SELECT interceptor did not recognise their annotations, and the TypeHandlers could only parse its hex output
- TypeHandlers created by MyBatis-Plus through `@TableField(typeHandler = …)` captured the MySQL fallback strategy and ignored `default-srid`; they now resolve the strategy and SRID on every call
- A user-defined `GeometryHandlerStrategy` bean was never used by reflectively created TypeHandlers
- SRID 0 could not be written (codecs replaced it with a hard-coded 4326); `default-srid` is now honoured, including 0
- Decoded geometries used a factory with SRID 0, so collection members and derived geometries (`buffer`, `union`, …) lost the SRID
- Writes mutated the caller's geometry (`setSRID`)
- Z ordinates were silently dropped on write, so PostGIS Z columns could not be written (see `preserve-z`); M ordinates were silently read as Z and are now rejected (M support is planned)
- Every write ran a full OGC `isValid()`, rejecting `updateById` of rows whose stored geometry is OGC-invalid
- Decoding errors lost their cause and were reported as "Unexpected database value type"
- Empty geometries could not be encoded by `WkbUtil` or serialized to GeoJSON (`POINT EMPTY` threw)
- Database detection matched `mysql`/`postgres` anywhere in the JDBC URL, including host and database names; it now uses the product name and URL scheme
- The application failed to start without Jackson on the classpath, and with more than one `DataSource` bean
- GeoJSON: non-numeric strings, `null` and booleans in positions were silently read as 0 (or 1 for `true`); NaN passed the WGS84 range check. Numeric strings such as `"116.4"` are still accepted
- GeoJSON: `MultiPolygon` input skipped ring orientation normalisation and validation; nested `GeometryCollection` and a `GeometryCollection` field holding a `Multi*` geometry could not be read back
- GeoJSON: serializers recursed infinitely when used through `@JsonSerialize` without the module, and failed with Jackson default typing (e.g. Redis cache serializers)
- GeoJSON errors are now `JsonMappingException`s with JSON location and path (HTTP 400 in Spring MVC)
- `WkbUtil` kept a `ThreadLocal` WKB reader per thread (classloader leak on redeploy)
- Deeply nested WKB values (thousands of nested collections) overflowed the stack; they are now rejected with an exception
- GeoJSON with a coordinate precision written through CBOR produced coordinates as text strings
- Database detection did not recognise the PostGIS JDBC wrapper URLs (`jdbc:postgresql_postGIS:`, `jdbc:postgres_jts:`, …)
- PostGIS reads of bytea expressions broke once pgjdbc switched a statement to binary transfer (after `prepareThreshold` executions); columns are now read with `getBytes()`
- SELECT interceptor (when enabled): `SELECT *` subqueries inside `exists`/`inSql`/`apply` received the outer entity's columns; `SELECT * FROM t WHERE …` treated `WHERE` as a table alias; mappers extending a custom base mapper, quoted identifiers, `DISTINCT`, hints, aliases without `AS`, columns with `$` and string literals containing commas or `from` were mishandled; same-named columns of joined tables were wrapped; XML-registered interceptors used the MySQL strategy on PostgreSQL; `SELECT DISTINCT … ORDER BY`, PostgreSQL `ISNULL`/`NOTNULL` and MySQL `/*! */` comments could be rewritten into invalid or different SQL. It now uses a SQL scanner that rewrites only the top-level select list and takes column names from MyBatis-Plus table metadata

### Added

- Native column reads: TypeHandlers read MySQL/MariaDB geometry columns as binary WKB (`getBytes`) and PostGIS columns as hex EWKB, so no SQL rewriting is needed and MySQL transfers half the bytes
- `GeometryHandlerStrategy.read(...)` and `convertForDatabase(Geometry, int srid)` default methods; `WkbCodec.encode(Geometry, int srid)`
- TypeHandler beans for all eight geometry types, registered with MyBatis by Java type (wrapper `set()` parameters, result maps without `autoResultMap`)
- Properties `write-validation` (`FULL`/`BASIC`/`NONE`), `preserve-z`, `coordinate-sequence` (`ARRAY`/`PACKED`) and `geojson.coordinate-precision`, `geojson.validation`, `geojson.coordinate-range-validation`, `geojson.fast-double-parsing`
- Streaming GeoJSON reader (single pass, no JSON tree, `type` in any position, Jackson fast double parsing) and a shared writer that reads coordinates through `CoordinateSequence` and follows the RFC 7946 right-hand rule
- Configurable GeoJSON coordinate precision with zero-allocation number formatting, `@GeoJsonPrecision` for per-field overrides, and `GeoJsonOptions`
- GeoJSON Z (altitude) support on input and output
- `GeometryFactoryProvider.getFactory(srid)` caching and `setCoordinateSequenceType(...)`
- `GeoJsonStreams` and `GeoJsonMediaTypes`: stream JSON arrays, FeatureCollections and JSON/GeoJSON Text Sequences (RFC 7464/8142) from any `Iterable`, including a MyBatis `Cursor`, without loading all rows
- `GeometryFieldInterceptor` plugin property `databaseType` and instance method `clearCaches()`

### Changed

- Upgrade JTS to 1.20.0 (`api` dependency, so applications get it transitively)
- `interceptor-enabled` now defaults to `false`; the interceptor is only kept for compatibility. Its PostGIS wrapper is now `encode(ST_AsEWKB(col::geometry), 'hex')` (hex EWKB, also for `geography` columns), which `WkbUtil.fromWkb` decodes. Statements that do not return the entity (`selectMaps`, `selectObjs`, DTOs) are only rewritten when they read the entity's own table, so with dynamic table names their geometry values are returned unwrapped
- `write-validation` defaults to `BASIC` (finite coordinates) instead of a full OGC validation on every write; set `FULL` to restore the old behaviour
- GeoJSON output: rings are re-oriented to the RFC 7946 right-hand rule, empty geometries are written as `[]`, and Z is written when present
- GeoJSON input: 2D positions produce two-dimensional coordinate sequences (as the WKB reader does) instead of 3D coordinates with NaN Z
- Reading a column whose geometry type does not match the field type now fails with a clear error instead of a `ClassCastException` later
- Documented requirement: MyBatis-Plus 3.5.6+ (meta-annotated `@TableField`)
- TypeHandlers read through `GeometryHandlerStrategy.read(...)`. Subclasses written for 1.0.x keep linking and working: an overridden `ensureSrid` is applied to a copy of the geometry, an overridden `parseGeometry` still receives `getString()` of the column
- `GeometryAutoConfiguration` has a new constructor and new bean-method signatures (the Jackson module moved to a nested configuration); it is not meant to be subclassed or called directly
- `WkbUtil.fromWkb` also accepts hex EWKB

### Deprecated

- `WkbUtil.cleanupThreadLocal()` (no-op), `AbstractGeometryTypeHandler.parseGeometry(String)` and `ensureSrid(Geometry)`, `GeometryFieldInterceptor.clearCache()`

## [1.0.1] - 2025-07-15

### Added

- Generic `GeometryTypeHandler` — Wildcard handler that reads/writes any geometry subtype from a single column
- `MultiPointTypeHandler`, `MultiLineStringTypeHandler`, `MultiPolygonTypeHandler`, `GeometryCollectionTypeHandler`
- Jackson GeoJSON serializers: `MultiPointSerializer`, `MultiLineStringSerializer`, `MultiPolygonSerializer`, `GeometryCollectionSerializer`, `GenericGeometrySerializer`
- Jackson GeoJSON deserializers: `MultiPointDeserializer`, `MultiLineStringDeserializer`, `MultiPolygonDeserializer`, `GeometryCollectionDeserializer`, `GenericGeometryDeserializer`
- Convenience annotations: `@GeometryTableField`, `@MultiPointTableField`, `@MultiLineStringTableField`, `@MultiPolygonTableField`, `@GeometryCollectionTableField`
- `WkbUtil` extended with `toWkb`/`toWkbBytes`/`fromWkbAs*` methods for MultiPoint, MultiLineString, MultiPolygon, and GeometryCollection
- `GeometryJacksonModule` now registers serializers/deserializers for all geometry types including generic `Geometry`

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

[Unreleased]: https://github.com/GeoVerseLabs/mybatis-plus-geometry/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/GeoVerseLabs/mybatis-plus-geometry/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/GeoVerseLabs/mybatis-plus-geometry/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/GeoVerseLabs/mybatis-plus-geometry/releases/tag/v1.0.0
