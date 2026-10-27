import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

/** Backend (SSR server gọi trực tiếp, không qua reverse proxy) và URL công khai của site (cho sitemap/robots). */
const API_ORIGIN = process.env['API_ORIGIN'] ?? 'http://localhost:8080';
const SITE_URL = process.env['SITE_URL']?.replace(/\/$/, '');
const SITEMAP_TTL_MS = 10 * 60 * 1000;

/**
 * Bản production gọi API bằng URL tương đối (`/api/v1`); khi SSR, Angular ghép thành URL theo host của request
 * (vd. https://shop.example.com/api/...) → đi vòng ra domain công khai, hoặc vào chính server này khi không có nginx.
 * Đổi các request đó sang API_ORIGIN ngay ở tầng fetch: HttpClient (và khóa cache truyền dữ liệu SSR → trình duyệt)
 * vẫn thấy URL gốc nên trình duyệt dùng lại dữ liệu đã render, không gọi API lần nữa.
 */
const realFetch = globalThis.fetch;
globalThis.fetch = (input, init) => {
  const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url);
  if (url.pathname.startsWith('/api/') && isSiteHost(url.hostname)) {
    const target = API_ORIGIN.replace(/\/$/, '') + url.pathname + url.search;
    return realFetch(input instanceof Request ? new Request(target, input) : target, init);
  }
  return realFetch(input, init);
};

const SITE_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  ...(SITE_URL ? [new URL(SITE_URL).hostname] : []),
  ...(process.env['NG_ALLOWED_HOSTS']?.split(',').map((h) => h.trim()) ?? []),
]);

function isSiteHost(hostname: string): boolean {
  return SITE_HOSTS.has(hostname);
}

const app = express();
/**
 * Angular SSR chỉ render cho host được phép (chống SSRF qua header Host) — host lạ thì rơi về CSR, mất thẻ SEO.
 * Danh sách: NG_ALLOWED_HOSTS (nếu đặt) hoặc host của SITE_URL + localhost. Sau nginx chỉ tin X-Forwarded-Proto/For
 * (nginx tự đặt Host); header x-forwarded-* không được tin cũng làm Angular rơi về CSR.
 */
const angularApp = new AngularNodeAppEngine({
  allowedHosts: process.env['NG_ALLOWED_HOSTS']
    ? undefined
    : [...(SITE_URL ? [new URL(SITE_URL).hostname] : []), 'localhost', '127.0.0.1'],
  trustProxyHeaders: ['x-forwarded-proto', 'x-forwarded-for'],
});

// Sau reverse proxy (nginx): lấy đúng giao thức / IP gốc từ X-Forwarded-*
app.set('trust proxy', 'loopback, linklocal, uniquelocal');
app.disable('x-powered-by');

/**
 * Header bảo mật cho mọi response của SSR server (P7-4). CSP chưa giới hạn script vì Angular SSR chèn script inline
 * (event replay) — bật `security.autoCsp` của Angular là bước tiếp theo (xem docs/security-review.md).
 */
app.use((_req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
  res.setHeader('Content-Security-Policy', "frame-ancestors 'self'; object-src 'none'; base-uri 'self'; form-action 'self'");
  next();
});

/** URL gốc của site: cấu hình SITE_URL (khuyến nghị, tránh giả mạo Host), không có thì theo request. */
function siteUrl(req: express.Request): string {
  return SITE_URL ?? `${req.protocol}://${req.get('host')}`;
}

app.get('/robots.txt', (req, res) => {
  res.type('text/plain').send(
    [
      '# ShopVie — trang công khai cho phép lập chỉ mục; khu vực cá nhân / quản trị thì không',
      'User-agent: *',
      'Allow: /',
      ...['/account', '/cart', '/checkout', '/payment', '/seller-center', '/admin', '/auth', '/search'].map((p) => `Disallow: ${p}`),
      '',
      `Sitemap: ${siteUrl(req)}/sitemap.xml`,
      '',
    ].join('\n'),
  );
});

interface SitemapData {
  categories: { slug: string }[];
  products: { slug: string; updatedAt: string }[];
}

let sitemapCache: { xml: string; site: string; at: number } | null = null;

/** sitemap.xml: trang chủ, danh mục, sản phẩm đang bán (dữ liệu từ backend, cache 10 phút). */
app.get('/sitemap.xml', async (req, res) => {
  const site = siteUrl(req);
  try {
    if (!sitemapCache || sitemapCache.site !== site || Date.now() - sitemapCache.at > SITEMAP_TTL_MS) {
      const response = await fetch(`${API_ORIGIN}/api/v1/seo/sitemap`);
      if (!response.ok) {
        throw new Error(`backend responded ${response.status}`);
      }
      const data = ((await response.json()) as { data: SitemapData }).data;
      sitemapCache = { xml: sitemapXml(site, data), site, at: Date.now() };
    }
    res.type('application/xml').send(sitemapCache.xml);
  } catch (err) {
    console.error('sitemap.xml failed:', err);
    res.status(503).type('text/plain').send('Sitemap temporarily unavailable');
  }
});

function sitemapXml(site: string, data: SitemapData): string {
  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const url = (path: string, extra = '') => `  <url><loc>${esc(site + path)}</loc>${extra}</url>`;
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    url('/', '<changefreq>daily</changefreq><priority>1.0</priority>'),
    ...data.categories.map((c) => url(`/c/${encodeURIComponent(c.slug)}`, '<changefreq>daily</changefreq>')),
    ...data.products.map((p) => url(`/p/${encodeURIComponent(p.slug)}`, `<lastmod>${p.updatedAt.slice(0, 10)}</lastmod>`)),
    '</urlset>',
    '',
  ].join('\n');
}

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
