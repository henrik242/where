import { describe, test, expect } from 'bun:test';
import { detectPlatform } from '../src/utils';

describe('detectPlatform', () => {
  test('should detect iPhone', () => {
    expect(detectPlatform('Where/1.0 (iPhone; iOS 17.0)')).toBe('ios');
  });

  test('should detect iPad', () => {
    expect(detectPlatform('Where/1.0 (iPad; iOS 17.0)')).toBe('ios');
  });

  test('should detect iOS in generic UA', () => {
    expect(detectPlatform('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)')).toBe('ios');
  });

  test('should detect Android', () => {
    expect(detectPlatform('Where/1.0 (Linux; Android 14)')).toBe('android');
  });

  test('should detect Android in generic UA', () => {
    expect(detectPlatform('Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36')).toBe('android');
  });

  test('should return null for desktop browser', () => {
    expect(detectPlatform('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36')).toBeNull();
  });

  test('should return null for empty string', () => {
    expect(detectPlatform('')).toBeNull();
  });

  test('should return null for Linux desktop', () => {
    expect(detectPlatform('Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36')).toBeNull();
  });

  test('should be case-insensitive', () => {
    expect(detectPlatform('where/1.0 (IPHONE; IOS 17.0)')).toBe('ios');
    expect(detectPlatform('where/1.0 (linux; ANDROID 14)')).toBe('android');
  });

  test('should prefer iOS over Android when both present', () => {
    // Edge case: iOS check runs first
    expect(detectPlatform('iPhone Android')).toBe('ios');
  });
});
