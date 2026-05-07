/** Classify a User-Agent string as 'ios' | 'android' | null. Used for session-count analytics. */
export function detectPlatform(userAgent: string): 'ios' | 'android' | null {
  if (/iPhone|iPad|iOS/i.test(userAgent)) return 'ios';
  if (/Android/i.test(userAgent)) return 'android';
  return null;
}
