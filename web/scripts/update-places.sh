#!/usr/bin/env bash
# Rebuilds the places database (named buildings and water bodies) from an OSM extract.
# Needs curl, osmium-tool and bun. A running server picks up the new file by itself.
#
#   PLACES_PBF_URL    extract (default: Geofabrik Norway)
#   PLACES_POLY_URL   coverage polygon (default: the extract's .poly on Geofabrik)
#   PLACES_DB         output (default: ./places/places.sqlite)
#   PLACES_BBOX       optional minlon,minlat,maxlon,maxlat to cut a small area for local testing
#   PLACES_MIN_COUNT  refuse to write an index with fewer buildings or water bodies (default 0)
set -euo pipefail
cd "$(dirname "$0")/.."

PBF_URL=${PLACES_PBF_URL:-https://download.geofabrik.de/europe/norway-latest.osm.pbf}
DEFAULT_POLY_URL=""
[[ $PBF_URL == *-latest.osm.pbf ]] && DEFAULT_POLY_URL="${PBF_URL%-latest.osm.pbf}.poly"
POLY_URL=${PLACES_POLY_URL:-$DEFAULT_POLY_URL}
OUT=${PLACES_DB:-./places/places.sqlite}
BUN=${BUN:-bun}

WORK=$(mktemp -d "${TMPDIR:-/tmp}/places.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

fetch() {
  curl -fsSL --retry 3 --retry-all-errors \
    -A "where-places/1 (+https://github.com/henrik242/where)" -o "$2" "$1"
}

echo "Downloading $PBF_URL"
fetch "$PBF_URL" "$WORK/region.osm.pbf"
if fetch "$PBF_URL.md5" "$WORK/region.md5" 2>/dev/null; then
  expected=$(cut -d' ' -f1 "$WORK/region.md5")
  actual=$(md5sum "$WORK/region.osm.pbf" | cut -d' ' -f1)
  [[ $expected == "$actual" ]] || { echo "Checksum mismatch for $PBF_URL" >&2; exit 1; }
fi

SOURCE=$PBF_URL
POLY_ARGS=()
if [[ -n ${PLACES_BBOX:-} ]]; then
  IFS=, read -r minx miny maxx maxy <<< "$PLACES_BBOX"
  osmium extract -b "$PLACES_BBOX" "$WORK/region.osm.pbf" -o "$WORK/cut.osm.pbf"
  mv "$WORK/cut.osm.pbf" "$WORK/region.osm.pbf"
  printf 'bbox\n1\n%s %s\n%s %s\n%s %s\n%s %s\n%s %s\nEND\nEND\n' \
    "$minx" "$miny" "$maxx" "$miny" "$maxx" "$maxy" "$minx" "$maxy" "$minx" "$miny" > "$WORK/coverage.poly"
  POLY_ARGS=(--poly "$WORK/coverage.poly")
  SOURCE="$PBF_URL bbox $PLACES_BBOX"
elif [[ -n $POLY_URL ]]; then
  fetch "$POLY_URL" "$WORK/coverage.poly"
  POLY_ARGS=(--poly "$WORK/coverage.poly")
fi

# tags-filter ORs its expressions, so two passes give "(building OR water) AND named".
# The cheap tag pass goes first; referenced members are kept for multipolygon assembly.
osmium tags-filter "$WORK/region.osm.pbf" wr/building wr/natural=water -o "$WORK/tagged.osm.pbf"
osmium tags-filter "$WORK/tagged.osm.pbf" wr/name -o "$WORK/filtered.osm.pbf"

# Closed ways and multipolygon relations become (Multi)Polygons with tags as properties.
# Broken multipolygons are left out; -e lists them so a vanished lake can be traced.
osmium export "$WORK/filtered.osm.pbf" --geometry-types=polygon -e \
  -f geojsonseq -o "$WORK/places.geojsonl" 2> "$WORK/export-errors.log" || {
  cat "$WORK/export-errors.log" >&2
  exit 1
}
echo "osmium export: $(grep -c . "$WORK/export-errors.log" || true) error lines (first 5 below)"
head -n 5 "$WORK/export-errors.log"

mkdir -p "$(dirname "$OUT")"
"$BUN" scripts/build-places.ts "$WORK/places.geojsonl" "$OUT" \
  "${POLY_ARGS[@]}" --source "$SOURCE" --min-count "${PLACES_MIN_COUNT:-0}"
