#!/bin/bash
set -euo pipefail


# ===============================
# Configuration
# ===============================
REMOTE="origin"
DEFAULT_BRANCH="main"
TAG_PREFIX="v"   # set to "" if you do not want v0.0.8 style tags

# ===============================
# Helper functions
# ===============================
error() {
  echo "❌ $1"
  exit 1
}

info() {
  echo "▶ $1"
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || error "Could not change to project directory: $SCRIPT_DIR"

# ===============================
# Validate input
# ===============================
VERSION="$1"

if [ -z "$VERSION" ]; then
  error "No version supplied. Usage: sh release.sh <version> (e.g. 0.0.8)"
fi

# Simple semver-ish validation
if ! echo "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  error "Invalid version format. Expected: X.Y.Z"
fi

TAG="${TAG_PREFIX}${VERSION}"

# ===============================
# Git checks
# ===============================
info "Checking git repository state..."

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || error "Not a git repository"

CURRENT_BRANCH=$(git symbolic-ref --short HEAD)

if [ "$CURRENT_BRANCH" != "$DEFAULT_BRANCH" ]; then
  error "You must be on the '$DEFAULT_BRANCH' branch (current: $CURRENT_BRANCH)"
fi

if [ -n "$(git status --porcelain)" ]; then
  error "Working tree is not clean. Commit or stash changes first."
fi

info "Fetching latest refs from $REMOTE"
git fetch "$REMOTE" --tags

LOCAL_HEAD=$(git rev-parse "$DEFAULT_BRANCH")
REMOTE_HEAD=$(git rev-parse "$REMOTE/$DEFAULT_BRANCH")

if [ "$LOCAL_HEAD" != "$REMOTE_HEAD" ]; then
  error "Local '$DEFAULT_BRANCH' is behind/diverged from '$REMOTE/$DEFAULT_BRANCH'. Pull/rebase first."
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  error "Tag '$TAG' already exists."
fi

if git ls-remote --tags "$REMOTE" "refs/tags/$TAG" | grep -q "$TAG"; then
  error "Tag '$TAG' already exists on remote '$REMOTE'."
fi

# ===============================
# Update version in pom.xml
# ===============================
info "Updating pom.xml to version $VERSION"
mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
git add pom.xml

# ===============================
# Create release commit
# ===============================
info "Creating release commit for version $VERSION"

git commit -m "Release $VERSION"

# ===============================
# Create tag
# ===============================
info "Creating git tag $TAG"

git tag -a "$TAG" -m "Release $VERSION"

# ===============================
# Push changes
# ===============================
info "Pushing commit and tag to $REMOTE"

git push "$REMOTE" "$DEFAULT_BRANCH"
git push "$REMOTE" "$TAG"

# ===============================
# Done
# ===============================
info "Release $VERSION completed successfully 🎉"
