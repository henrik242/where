import { timingSafeEqual } from 'node:crypto';
import { CONFIG } from './config';

/** Verify an HMAC-SHA256 signature against a request body using TRACKING_HINT. */
export async function verifyHmacSignature(
  body: string,
  signature: string | null
): Promise<boolean> {
  if (!CONFIG.TRACKING_HINT || !signature) {
    return false;
  }

  try {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      'raw',
      encoder.encode(CONFIG.TRACKING_HINT),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );

    const signatureBytes = await crypto.subtle.sign(
      'HMAC',
      key,
      encoder.encode(body)
    );

    const expected = Buffer.from(signatureBytes);
    const actual = Buffer.from(signature, 'base64');
    if (expected.byteLength !== actual.byteLength) return false;
    return timingSafeEqual(expected, actual);
  } catch (error) {
    console.error('HMAC verification error:', error);
    return false;
  }
}
