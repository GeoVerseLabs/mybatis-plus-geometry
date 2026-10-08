# Security Policy

English | [简体中文](SECURITY_zh.md)

## Supported Versions

We release patches for security vulnerabilities for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability within this project, please send an email to the maintainer. All security vulnerabilities will be promptly addressed.

**Please do not report security vulnerabilities through public GitHub issues.**

### What to Include

When reporting a vulnerability, please include:

1. A description of the vulnerability
2. Steps to reproduce the issue
3. Potential impact of the vulnerability
4. Any suggested fixes (if available)

### Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Fix Timeline**: Depends on severity
  - Critical: Within 7 days
  - High: Within 14 days
  - Medium: Within 30 days
  - Low: Next regular release

## Security Best Practices

When using this library:

1. Always use the latest stable version
2. Keep your dependencies up to date
3. Follow the principle of least privilege for database access
4. Validate and sanitize all user inputs before using them in spatial queries
5. Use parameterized queries to prevent SQL injection
6. Review the [OWASP Top 10](https://owasp.org/www-project-top-ten/) regularly

## Known Security Considerations

### SQL Injection

This library uses MyBatis Plus TypeHandlers which properly handle parameter binding. However:

- Always use parameterized queries
- Never concatenate user input directly into SQL strings
- Be cautious with dynamic SQL generation

### Data Validation

- Validate coordinate ranges before creating geometry objects
- Check for NaN and infinite values in coordinates
- Validate SRID values match your database configuration

## Disclosure Policy

When a security vulnerability is reported and confirmed:

1. We will work on a fix privately
2. We will prepare a security advisory
3. We will release a patched version
4. We will publish the security advisory with credit to the reporter (if desired)

Thank you for helping keep this project secure!
