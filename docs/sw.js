/* Schedule 的 Service Worker
 * 作用：把应用外壳缓存到本机，断网也能打开；让「添加到主屏幕」真正像个 App。
 * 策略：外壳资源缓存优先（秒开）；index.html 每次启动在后台静默更新，下次打开生效。
 * 说明：安卓 App 内是 file:// 加载，不会走这里；只有网页 / PWA 模式才注册。
 */

const CACHE = 'schedule-v1';
const SHELL = [
  './',
  './index.html',
  './manifest.json',
  './xlsx.full.min.js',
  './icons/icon-180.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png'
];

const OFFLINE = '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">' +
  '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">' +
  '<title>暂时打不开</title><style>' +
  'html,body{height:100%;margin:0}' +
  'body{display:grid;place-items:center;background:#FAF8F3;color:#2B2620;' +
  'font:15px/1.5 -apple-system,BlinkMacSystemFont,system-ui,"PingFang SC",sans-serif;text-align:center;padding:28px}' +
  '@media(prefers-color-scheme:dark){body{background:#14120E;color:#F5EFE4}}' +
  '.i{width:56px;height:56px;border-radius:50%;background:rgba(120,106,80,.12);' +
  'display:grid;place-items:center;margin:0 auto 18px;font-size:24px}' +
  'h1{font-size:19px;margin:0 0 8px;font-weight:600}' +
  'p{color:#8B8172;margin:0 0 22px;max-width:16em}' +
  'button{background:#4E6A56;color:#fff;border:0;border-radius:12px;padding:13px 24px;' +
  'font:600 15px system-ui}</style></head><body><div>' +
  '<div class="i">📶</div><h1>暂时打不开</h1>' +
  '<p>第一次打开需要联网。连上网后重试即可，之后就能离线用了。</p>' +
  '<button onclick="location.reload()">重试</button></div></body></html>';

function offlineResponse() {
  return new Response(OFFLINE, { headers: { 'Content-Type': 'text/html; charset=utf-8' } });
}

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(CACHE).then(function (c) {
      // 单个资源失败不拖垮整个安装
      return Promise.all(SHELL.map(function (u) {
        return c.add(new Request(u, { cache: 'reload' })).catch(function () {});
      }));
    }).then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (k) { return k !== CACHE; })
        .map(function (k) { return caches.delete(k); }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  const req = e.request;
  if (req.method !== 'GET') return;

  let url;
  try { url = new URL(req.url); } catch (err) { return; }
  if (url.origin !== location.origin) return; // 站外请求（如更新检查）不拦

  const isNav = req.mode === 'navigate' ||
    url.pathname.endsWith('/') ||
    url.pathname.endsWith('index.html');

  if (isNav) {
    // 只发一次网络请求：命中缓存时立刻返回，同时把新版本写回缓存
    const net = fetch(req).then(function (res) {
      if (res && res.ok) {
        const copy = res.clone();
        caches.open(CACHE).then(function (c) { c.put('./index.html', copy); });
      }
      return res;
    }).catch(function () { return null; });

    e.waitUntil(net); // 保证浏览器不提前杀掉 SW、更新能写完
    e.respondWith(
      caches.open(CACHE).then(function (c) {
        return c.match('./index.html').then(function (hit) { return hit || c.match('./'); });
      }).then(function (hit) {
        if (hit) return hit;
        return net.then(function (r) { return r || offlineResponse(); });
      })
    );
    return;
  }

  // 静态资源：缓存优先
  e.respondWith(
    caches.match(req).then(function (hit) {
      if (hit) return hit;
      return fetch(req).then(function (res) {
        if (res && res.ok && res.type === 'basic') {
          const copy = res.clone();
          caches.open(CACHE).then(function (c) { c.put(req, copy); });
        }
        return res;
      }).catch(function () {
        return new Response('', { status: 504, statusText: 'offline' });
      });
    })
  );
});

/* 页面可主动让新版本立刻接管 */
self.addEventListener('message', function (e) {
  if (e.data === 'skip-waiting') self.skipWaiting();
});
