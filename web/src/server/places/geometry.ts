/** Planar geometry on lon/lat polygons, accurate enough for metre-scale lookups. */

export type Position = [lon: number, lat: number];
export type Ring = Position[];
/** Outer ring first, then holes, as in GeoJSON. */
export type Polygon = Ring[];

export interface BBox {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

const M_PER_DEG_LAT = 110_540;
const M_PER_DEG_LON_EQUATOR = 111_320;

export function metresPerDegLon(lat: number): number {
  return M_PER_DEG_LON_EQUATOR * Math.cos((lat * Math.PI) / 180);
}

/** Box of half-size `metres` around a point, for bbox prefiltering. */
export function bboxAround(lat: number, lon: number, metres: number): BBox {
  const dLat = metres / M_PER_DEG_LAT;
  const dLon = metres / metresPerDegLon(lat);
  return { minX: lon - dLon, maxX: lon + dLon, minY: lat - dLat, maxY: lat + dLat };
}

/** Bounding box of the outer rings. */
export function bbox(polygons: Polygon[]): BBox {
  const box = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity };
  for (const [outer] of polygons) {
    for (const [lon, lat] of outer ?? []) {
      box.minX = Math.min(box.minX, lon);
      box.maxX = Math.max(box.maxX, lon);
      box.minY = Math.min(box.minY, lat);
      box.maxY = Math.max(box.maxY, lat);
    }
  }
  return box;
}

/** Even-odd test over all rings, so holes count as outside. */
function insidePolygon(lon: number, lat: number, polygon: Polygon): boolean {
  let inside = false;
  for (const ring of polygon) {
    let prev = ring[ring.length - 1];
    for (const curr of ring) {
      if (prev) {
        const [x1, y1] = prev;
        const [x2, y2] = curr;
        if (y1 > lat !== y2 > lat && lon < ((x2 - x1) * (lat - y1)) / (y2 - y1) + x1) inside = !inside;
      }
      prev = curr;
    }
  }
  return inside;
}

export function containsPoint(polygons: Polygon[], lat: number, lon: number): boolean {
  return polygons.some(polygon => insidePolygon(lon, lat, polygon));
}

/** Metres from the point to the nearest edge, 0 when inside. Equirectangular, fine at this scale. */
export function distanceMeters(polygons: Polygon[], lat: number, lon: number): number {
  if (containsPoint(polygons, lat, lon)) return 0;
  const kx = metresPerDegLon(lat);
  const toLocal = ([x, y]: Position): Position => [(x - lon) * kx, (y - lat) * M_PER_DEG_LAT];
  let best = Infinity;
  for (const polygon of polygons) {
    for (const ring of polygon) {
      let prev = ring[ring.length - 1];
      for (const curr of ring) {
        if (prev) best = Math.min(best, distanceToSegment(toLocal(prev), toLocal(curr)));
        prev = curr;
      }
    }
  }
  return best;
}

/** Distance from the origin to segment a-b. */
function distanceToSegment([ax, ay]: Position, [bx, by]: Position): number {
  const dx = bx - ax;
  const dy = by - ay;
  const len2 = dx * dx + dy * dy;
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
  return Math.hypot(ax + t * dx, ay + t * dy);
}

function isPosition(value: unknown): value is Position {
  return (
    Array.isArray(value) &&
    value.length >= 2 &&
    Number.isFinite(value[0]) &&
    Number.isFinite(value[1])
  );
}

/** A ring needs at least 4 positions (closed triangle). */
function isRing(value: unknown): value is Ring {
  return Array.isArray(value) && value.length >= 4 && value.every(isPosition);
}

function isPolygon(value: unknown): value is Polygon {
  return Array.isArray(value) && value.length >= 1 && value.every(isRing);
}

/** Validates untrusted GeoJSON coordinates for a Polygon or MultiPolygon. */
export function toPolygons(type: unknown, coordinates: unknown): Polygon[] | null {
  if (type === 'Polygon') return isPolygon(coordinates) ? [coordinates] : null;
  if (type === 'MultiPolygon') {
    return Array.isArray(coordinates) && coordinates.length >= 1 && coordinates.every(isPolygon)
      ? coordinates
      : null;
  }
  return null;
}
