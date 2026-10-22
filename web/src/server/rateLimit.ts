/** Fixed-window request counter per key (client IP). In memory, so it resets on restart. */
export class RateLimiter {
  private windows = new Map<string, { count: number; resetAt: number }>();
  private nextSweep = 0;

  constructor(
    private limit: number,
    private windowMs: number,
    private now: () => number = Date.now
  ) {}

  /** Counts a request; returns seconds until the window resets when over the limit, else null. */
  hit(key: string): number | null {
    const now = this.now();
    this.sweep(now);
    let entry = this.windows.get(key);
    if (!entry || now >= entry.resetAt) {
      entry = { count: 0, resetAt: now + this.windowMs };
      this.windows.set(key, entry);
    }
    entry.count++;
    return entry.count > this.limit ? Math.ceil((entry.resetAt - now) / 1000) : null;
  }

  /** Drops expired windows once per window length, so the map can't grow without bound. */
  private sweep(now: number) {
    if (now < this.nextSweep) return;
    this.nextSweep = now + this.windowMs;
    for (const [key, entry] of this.windows) {
      if (now >= entry.resetAt) this.windows.delete(key);
    }
  }

  get size(): number {
    return this.windows.size;
  }
}

const LOOPBACK = new Set(['127.0.0.1', '::1', '::ffff:127.0.0.1']);

/**
 * The client's address. Behind the local Apache proxy the peer is loopback, and the real
 * client is the last X-Forwarded-For entry (the one Apache appended; earlier ones are
 * whatever the client sent). The header is ignored unless the peer is positively loopback,
 * since it is spoofable; an unknown peer shares one bucket.
 */
export function clientAddress(req: Request, peerAddress: string | undefined): string {
  if (!peerAddress) return 'unknown';
  if (!LOOPBACK.has(peerAddress)) return peerAddress;
  const forwarded = req.headers.get('X-Forwarded-For')?.split(',').pop()?.trim();
  return forwarded || peerAddress;
}
