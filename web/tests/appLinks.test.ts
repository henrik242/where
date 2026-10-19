import { describe, test, expect } from 'bun:test';
import { assetLinks } from '../src/server/appLinks';

describe('assetLinks', () => {
  test('null without fingerprints', () => {
    expect(assetLinks(undefined)).toBeNull();
    expect(assetLinks(' , ')).toBeNull();
  });
});
