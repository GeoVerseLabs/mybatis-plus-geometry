@echo off
REM Initialize Git repository for mybatis-plus-geometry
REM This script sets up the Git repository with proper configuration

echo Initializing Git repository for mybatis-plus-geometry...
echo.

REM Initialize git if not already initialized
if not exist .git (
    git init
    echo [OK] Git repository initialized
) else (
    echo [OK] Git repository already exists
)

REM Set commit message template
git config commit.template .gitmessage
echo [OK] Commit message template configured

REM Set line ending configuration
git config core.autocrlf input
git config core.eol lf
echo [OK] Line ending configuration set

REM Set default branch name
git config init.defaultBranch main
echo [OK] Default branch set to 'main'

REM Add remote (update with your actual repository URL)
set REMOTE_URL=https://github.com/GeoVerseLabs/mybatis-plus-geometry.git
git remote | findstr /C:"origin" >nul
if errorlevel 1 (
    echo.
    echo To add remote repository, run:
    echo   git remote add origin %REMOTE_URL%
) else (
    echo [OK] Remote 'origin' already configured
)

REM Stage all files
git add .
echo [OK] Files staged for commit

echo.
echo Git repository setup complete!
echo.
echo Next steps:
echo   1. Review staged files: git status
echo   2. Make initial commit: git commit -m "feat: initial commit"
echo   3. Add remote: git remote add origin ^<your-repo-url^>
echo   4. Push to GitHub: git push -u origin main
echo.
pause
