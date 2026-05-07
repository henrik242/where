import type { Server } from 'bun';
import { createApiHandler } from './api';
import { CONFIG } from './config';
import { CLIENT_IDS_RE } from '../shared/validation';
import { trackStore } from './store';
import { broadcastToAll, startStaleTrackChecker } from './tracking';
import { verifyHmacSignature } from './auth';
import { websocketHandlers } from './ws';

const CLIENT_DIR = `${import.meta.dir}/../client`;
const OG_META_PATH = `${import.meta.dir}/og-meta.html`;

const HTML_ALIASES = new Map([
  ['/', '/index.html'],
  ['/about', '/about.html'],
  ['/privacy', '/privacy.html'],
]);

interface FetchHandlerDeps {
  handleAPI: (req: Request) => Promise<Response>;
  ogMeta: string;
}

function makeFetchHandler({ handleAPI, ogMeta }: FetchHandlerDeps) {
  async function serveHtml(path: string): Promise<Response | null> {
    const file = Bun.file(`${CLIENT_DIR}${path}`);
    if (!(await file.exists())) return null;
    const html = await file.text();
    return new Response(html.replace('<!-- OG_META -->', ogMeta), {
      headers: { 'Content-Type': 'text/html; charset=utf-8' },
    });
  }

  return async function fetch(req: Request, server: Server): Promise<Response | undefined> {
    const url = new URL(req.url);

    if (url.pathname === '/ws') {
      return server.upgrade(req)
        ? undefined
        : new Response('WebSocket upgrade failed', { status: 400 });
    }

    if (url.pathname.startsWith('/api')) {
      return handleAPI(req);
    }

    if (url.pathname === '/ios') {
      return Response.redirect('https://apps.apple.com/app/where/id6760362061', 302);
    }
    if (url.pathname === '/android') {
      return Response.redirect('https://play.google.com/store/apps/details?id=no.synth.where', 302);
    }

    // Legacy ?clients= → /:clientIds path
    if (url.pathname === '/') {
      const clientsParam = url.searchParams.get('clients');
      if (clientsParam && CLIENT_IDS_RE.test(clientsParam)) {
        return Response.redirect(`${url.origin}/${clientsParam}`, 302);
      }
    }

    const filePath = HTML_ALIASES.get(url.pathname) ?? url.pathname;
    const file = Bun.file(`${CLIENT_DIR}${filePath}`);
    if (await file.exists()) {
      if (filePath.endsWith('.html')) {
        const html = await serveHtml(filePath);
        if (html) return html;
      }
      return new Response(file);
    }

    // SPA fallback for path-based client IDs (e.g. /abc123 or /abc123,def456)
    if (CLIENT_IDS_RE.test(url.pathname.slice(1))) {
      const html = await serveHtml('/index.html');
      if (html) return html;
    }

    return new Response('Not Found', { status: 404 });
  };
}

async function main() {
  const indexHtml = Bun.file(`${CLIENT_DIR}/index.html`);
  if (!(await indexHtml.exists())) {
    throw new Error(`CLIENT_DIR is misconfigured — ${CLIENT_DIR}/index.html not found`);
  }

  const ogMeta = await Bun.file(OG_META_PATH).text();

  const handleAPI = createApiHandler({
    store: trackStore,
    verifySignature: verifyHmacSignature,
    broadcast: broadcastToAll,
    getAdminKey: () => CONFIG.ADMIN_KEY,
  });

  const server = Bun.serve({
    port: CONFIG.PORT,
    fetch: makeFetchHandler({ handleAPI, ogMeta }),
    websocket: websocketHandlers,
  });

  startStaleTrackChecker();

  console.log(`🚀 Where Web running at http://localhost:${server.port}`);
  console.log(`📡 WebSocket available at ws://localhost:${server.port}/ws`);
  console.log(`🌐 Web interface at http://localhost:${server.port}`);
  console.log(`🔑 Admin: ${CONFIG.ADMIN_KEY ? 'ENABLED' : 'DISABLED'}`);
  console.log(`🔒 HMAC verification: ${CONFIG.TRACKING_HINT ? 'ENABLED' : 'DISABLED'}`);
  console.log(`⏱️  Auto-stop inactive tracks after 10 minutes`);
}

main().catch(err => {
  console.error('Fatal startup error:', err);
  process.exit(1);
});
