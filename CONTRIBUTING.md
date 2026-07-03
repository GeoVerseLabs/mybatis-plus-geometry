# Contributing to MyBatis Plus Geometry Extension

English | [简体中文](CONTRIBUTING_zh.md)

Thank you for your interest in contributing to this project! This document provides guidelines and instructions for contributing.

## Code of Conduct

Please be respectful and constructive in all interactions. We welcome contributors of all experience levels.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/GeoVerseLabs/mybatis-plus-geometry/issues)
2. If not, create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details (Java version, Spring Boot version, database)

### Suggesting Features

1. Open an issue with the `enhancement` label
2. Describe the feature and its use case
3. Discuss the implementation approach

### Pull Requests

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make your changes
4. Write or update tests
5. Ensure all tests pass: `./gradlew test`
6. Commit with clear messages
7. Push and create a Pull Request

## Development Setup

### Prerequisites

- JDK 17 or later
- Gradle 8.x (wrapper included)
- IDE with Lombok support

### Building

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/mybatis-plus-geometry.git
cd mybatis-plus-geometry

# Build
./gradlew build

# Run tests
./gradlew test
```

### Code Style

- Follow standard Java conventions
- Use meaningful variable and method names
- Add Javadoc for public APIs
- Keep methods focused and small
- Write tests for new functionality

### Commit Messages

Use clear, descriptive commit messages:

```
feat: add support for MultiPolygon type
fix: correct WKB parsing for empty geometries
docs: update README with PostgreSQL examples
test: add property tests for coordinate validation
```

## Testing

- Write unit tests for new functionality
- Include edge cases
- Run the full test suite before submitting PR

## Questions?

Feel free to open an issue for any questions about contributing.

Thank you for contributing! 🎉
