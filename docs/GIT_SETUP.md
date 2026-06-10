# Git Setup Guide

This document provides instructions for setting up the Git repository for mybatis-plus-geometry.

## Quick Start

### Automated Setup

Run the initialization script for your platform:

**Linux/Mac:**
```bash
chmod +x init-git.sh
./init-git.sh
```

**Windows:**
```cmd
init-git.bat
```

### Manual Setup

If you prefer to set up manually:

```bash
# 1. Initialize Git repository
git init

# 2. Configure commit message template
git config commit.template .gitmessage

# 3. Configure line endings
git config core.autocrlf input
git config core.eol lf

# 4. Set default branch
git config init.defaultBranch main

# 5. Add remote repository
git remote add origin https://github.com/yoy0o/mybatis-plus-geometry.git

# 6. Stage all files
git add .

# 7. Make initial commit
git commit -m "feat: initial commit"

# 8. Push to GitHub
git push -u origin main
```

## Git Configuration Files

### .gitignore

Excludes build artifacts, IDE files, and sensitive data from version control.

Key exclusions:
- Build directories (`build/`, `.gradle/`)
- IDE files (`.idea/`, `*.iml`, `.vscode/`)
- Sensitive files (`*.key`, `*.pem`, `secring.gpg`)
- OS files (`.DS_Store`, `Thumbs.db`)

### .gitattributes

Ensures consistent line endings and file handling across platforms:
- Text files use LF line endings
- Shell scripts (`.sh`) always use LF
- Batch files (`.bat`) always use CRLF
- Binary files are properly marked

### .editorconfig

Maintains consistent coding style across different editors:
- Java: 4 spaces, max 120 characters per line
- YAML: 2 spaces
- Gradle: 4 spaces

### .gitmessage

Commit message template following conventional commits:

```
<type>: <subject>

<body>

<footer>
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Test changes
- `chore`: Build/tooling changes
- `perf`: Performance improvements
- `ci`: CI/CD changes

## GitHub Configuration

### Issue Templates

Located in `.github/ISSUE_TEMPLATE/`:
- `bug_report.md`: For reporting bugs
- `feature_request.md`: For requesting features

### Pull Request Template

Located at `.github/pull_request_template.md`

Includes checklist for:
- Code style compliance
- Tests
- Documentation
- Self-review

### Dependabot

Configured in `.github/dependabot.yml` to:
- Check Gradle dependencies weekly
- Check GitHub Actions weekly
- Auto-create PRs for updates

### Code Owners

Defined in `.github/CODEOWNERS`:
- Automatically requests review from maintainers
- Ensures proper oversight of critical changes

## Commit Message Guidelines

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Examples

```
feat(handler): add support for MultiPolygon type

Implement TypeHandler for JTS MultiPolygon geometry type.
Includes WKB conversion and validation.

Closes #42
```

```
fix(interceptor): correct HEX wrapping for aliased columns

The SQL interceptor was not properly handling column aliases
in SELECT queries. This fix ensures HEX() is applied correctly.

Fixes #38
```

```
docs(readme): update installation instructions

Add Maven and Gradle examples with latest version.
Include troubleshooting section for common issues.
```

## Branch Strategy

### Main Branches

- `main`: Production-ready code
- `develop`: Integration branch for features

### Feature Branches

Format: `feature/<issue-number>-<short-description>`

Example: `feature/42-multipolygon-support`

### Bugfix Branches

Format: `fix/<issue-number>-<short-description>`

Example: `fix/38-hex-wrapping-aliases`

### Release Branches

Format: `release/v<version>`

Example: `release/v1.1.0`

## Workflow

1. **Create Feature Branch**
   ```bash
   git checkout -b feature/42-multipolygon-support
   ```

2. **Make Changes and Commit**
   ```bash
   git add .
   git commit
   # Follow the commit message template
   ```

3. **Push to GitHub**
   ```bash
   git push -u origin feature/42-multipolygon-support
   ```

4. **Create Pull Request**
   - Go to GitHub
   - Click "New Pull Request"
   - Fill in the PR template
   - Request review

5. **After Merge**
   ```bash
   git checkout main
   git pull origin main
   git branch -d feature/42-multipolygon-support
   ```

## Release Process

1. **Update Version**
   ```bash
   # Update version in gradle.properties
   vim gradle.properties
   ```

2. **Update Changelog**
   ```bash
   # Add release notes to CHANGELOG.md
   vim CHANGELOG.md
   ```

3. **Commit Changes**
   ```bash
   git add gradle.properties CHANGELOG.md
   git commit -m "chore: bump version to 1.1.0"
   ```

4. **Create Tag**
   ```bash
   git tag -a v1.1.0 -m "Release version 1.1.0"
   ```

5. **Push Tag**
   ```bash
   git push origin v1.1.0
   ```

6. **GitHub Actions will automatically:**
   - Build the project
   - Run tests
   - Publish to Maven Central
   - Create GitHub Release

## Troubleshooting

### Line Ending Issues

If you see line ending warnings:

```bash
git config core.autocrlf input
git rm --cached -r .
git reset --hard
```

### Large Files

If you accidentally committed large files:

```bash
# Remove from history
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch path/to/large/file" \
  --prune-empty --tag-name-filter cat -- --all
```

### Revert Last Commit

```bash
# Soft reset (keeps changes)
git reset --soft HEAD~1

# Hard reset (discards changes)
git reset --hard HEAD~1
```

## Additional Resources

- [Git Documentation](https://git-scm.com/doc)
- [GitHub Guides](https://guides.github.com/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
