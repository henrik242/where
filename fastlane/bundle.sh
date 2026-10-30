#!/usr/bin/env bash
# Installs (default) or updates fastlane via Bundler.
# Usage: fastlane/bundle.sh [update]
#
# brew upgrade ruby deletes gems/bundler-* but leaves their gemspecs, so the
# BUNDLED WITH version resolves to a gem with no files. Prune those first.

set -euo pipefail

cd "$(dirname "$0")/.."

gemdir="$(gem env gemdir)"
for spec in "$gemdir"/specifications/bundler-*.gemspec; do
  [ -e "$spec" ] || continue
  name="$(basename "$spec" .gemspec)"
  if [ ! -d "$gemdir/gems/$name" ]; then
    echo "Removing orphaned $name gemspec"
    rm "$spec"
  fi
done

version="$(awk '/^BUNDLED WITH/ { getline; print $1 }' Gemfile.lock)"
if ! gem list -i bundler -v "$version" >/dev/null; then
  gem install bundler -v "$version" --no-document --user-install 2>/dev/null || gem install bundler -v "$version" --no-document
fi

bundle config set --local path 'vendor/bundle'

case "${1:-install}" in
  install) bundle install ;;
  update) bundle update fastlane ;;
  *) echo "Usage: $0 [update]" >&2; exit 1 ;;
esac
