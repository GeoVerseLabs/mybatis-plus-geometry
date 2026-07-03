#!/bin/bash

# Initialize Git repository for mybatis-plus-geometry
# This script sets up the Git repository with proper configuration

set -e

echo "Initializing Git repository for mybatis-plus-geometry..."

# Initialize git if not already initialized
if [ ! -d .git ]; then
    git init
    echo "✓ Git repository initialized"
else
    echo "✓ Git repository already exists"
fi

# Set commit message template
git config commit.template .gitmessage
echo "✓ Commit message template configured"

# Set line ending configuration
git config core.autocrlf input
git config core.eol lf
echo "✓ Line ending configuration set"

# Set default branch name
git config init.defaultBranch main
echo "✓ Default branch set to 'main'"

# Add remote (update with your actual repository URL)
REMOTE_URL="https://github.com/GeoVerseLabs/mybatis-plus-geometry.git"
if ! git remote | grep -q origin; then
    echo ""
    echo "To add remote repository, run:"
    echo "  git remote add origin $REMOTE_URL"
else
    echo "✓ Remote 'origin' already configured"
fi

# Stage all files
git add .
echo "✓ Files staged for commit"

echo ""
echo "Git repository setup complete!"
echo ""
echo "Next steps:"
echo "  1. Review staged files: git status"
echo "  2. Make initial commit: git commit -m 'feat: initial commit'"
echo "  3. Add remote: git remote add origin <your-repo-url>"
echo "  4. Push to GitHub: git push -u origin main"
