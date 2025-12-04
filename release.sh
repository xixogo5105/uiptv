#!/bin/bash

# This script automates the release process for the UIPTV application.
# It updates the pom.xml version, commits the change, creates a Git tag,
# and pushes everything to the remote repository.

# Usage: ./release.sh <version>
# Example: ./release.sh 1.0.0 or ./release.sh v1.0.0

# --- Configuration ---
POM_FILE="pom.xml"
MAVEN_COMMAND="mvn"
GIT_COMMAND="git"

# --- Functions ---

# Function to display error messages and exit
error_exit() {
  echo "Error: $1" >&2
  exit 1
}

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# --- Pre-checks ---

# Check for required commands
if ! command_exists "$MAVEN_COMMAND"; then
  error_exit "Maven ($MAVEN_COMMAND) is not installed or not in your PATH. Please install it."
fi

if ! command_exists "$GIT_COMMAND"; then
  error_exit "Git ($GIT_COMMAND) is not installed or not in your PATH. Please install it."
fi

# Check if we are in a Git repository
if ! "$GIT_COMMAND" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  error_exit "Not inside a Git repository. Please run this script from the project root."
fi

# Check for uncommitted changes
if ! "$GIT_COMMAND" diff-index --quiet HEAD --; then
  error_exit "You have uncommitted changes. Please commit or stash them before running the release script."
fi

# --- Main Logic ---

# Get the version argument
RAW_VERSION="$1"

if [ -z "$RAW_VERSION" ]; then
  error_exit "No version specified. Usage: ./release.sh <version>"
fi

# Determine the version for pom.xml and the tag
if [[ "$RAW_VERSION" == v* ]]; then
  POM_VERSION="${RAW_VERSION#v}" # Remove 'v' prefix for pom.xml
  GIT_TAG="$RAW_VERSION"
else
  POM_VERSION="$RAW_VERSION"
  GIT_TAG="v$RAW_VERSION" # Add 'v' prefix for the tag
fi

echo "--- Starting Release Process ---"
echo "  Target POM Version: $POM_VERSION"
echo "  Target Git Tag:     $GIT_TAG"
echo ""

# 1. Update pom.xml version
echo "1. Updating $POM_FILE to version $POM_VERSION..."
"$MAVEN_COMMAND" versions:set -DnewVersion="$POM_VERSION" -DgenerateBackupPoms=false || error_exit "Failed to update $POM_FILE version."
echo "   $POM_FILE updated successfully."

# 2. Commit the version change
echo "2. Committing version change to Git..."
"$GIT_COMMAND" add "$POM_FILE" || error_exit "Failed to add $POM_FILE to Git staging."
"$GIT_COMMAND" commit -m "Prepare release $GIT_TAG" || error_exit "Failed to commit version change."
echo "   Commit successful."

# 3. Create the Git tag
echo "3. Creating Git tag $GIT_TAG..."
"$GIT_COMMAND" tag "$GIT_TAG" || error_exit "Failed to create Git tag $GIT_TAG. It might already exist."
echo "   Tag $GIT_TAG created successfully."

# 4. Push commit and tag to remote
echo "4. Pushing commit and tag to remote (origin main)..."
"$GIT_COMMAND" push origin main || error_exit "Failed to push commit to origin/main."
"$GIT_COMMAND" push origin "$GIT_TAG" || error_exit "Failed to push tag $GIT_TAG to origin."
echo "   Successfully pushed commit and tag to remote."

echo ""
echo "--- Release Process Completed Successfully! ---"
echo "The GitHub Actions workflow should now be triggered for tag $GIT_TAG."
echo "Check your GitHub repository for the release progress."
