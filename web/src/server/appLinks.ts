// Verification files that let the apps claim where.synth.no/<id> links instead of the browser.

const ANDROID_PACKAGE = 'no.synth.where';
const IOS_APP_IDS = ['358L7UXVJ4.no.synth.where', '358L7UXVJ4.no.synth.where.debug'];

/** Android App Links. Null when no signing cert fingerprints are configured. */
export function assetLinks(certFingerprints: string | undefined): object[] | null {
  const fingerprints = (certFingerprints ?? '').split(',').map(f => f.trim()).filter(Boolean);
  if (fingerprints.length === 0) return null;
  return [{
    relation: ['delegate_permission/common.handle_all_urls'],
    target: { namespace: 'android_app', package_name: ANDROID_PACKAGE, sha256_cert_fingerprints: fingerprints },
  }];
}

/** iOS Universal Links. Matches the same paths as CLIENT_IDS_RE: /abc123 and /abc123,def456,... */
export const APPLE_APP_SITE_ASSOCIATION = {
  applinks: {
    details: [{
      appIDs: IOS_APP_IDS,
      components: [
        { '/': '/??????' },
        { '/': '/??????,*' },
      ],
    }],
  },
};
