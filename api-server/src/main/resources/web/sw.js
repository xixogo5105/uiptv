const CACHE_NAME = 'uiptv-cache-v10';
const urlsToCache = [
  '/',
  '/index.html',
  '/myflix.html',
  '/player.html',
  '/css/player.css',
  '/css/spa.css',
  '/css/myflix.css',
  '/javascript/spa.js',
  '/javascript/myflix.js',
  '/javascript/player.js',
  'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.2/font/bootstrap-icons.css',
  'https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css',
  'https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/js/bootstrap.bundle.min.js',
  'https://unpkg.com/vue@3/dist/vue.global.prod.js',
  'https://cdn.jsdelivr.net/npm/shaka-player@5/dist/shaka-player.compiled.js'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        return cache.addAll(urlsToCache);
      })
  );
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') {
    return;
  }
  const requestUrl = new URL(event.request.url);
  if (
    requestUrl.pathname === '/player.html'
    || requestUrl.pathname === '/drm.html'
    || requestUrl.pathname === '/javascript/player.js'
    || requestUrl.pathname === '/javascript/drm-player.js'
  ) {
    event.respondWith(fetch(event.request));
    return;
  }
  if (requestUrl.pathname.startsWith('/hls/')) {
    return;
  }
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        if (response) {
          return response;
        }
        return fetch(event.request).catch(() => {
          // Avoid unhandled rejections for unreachable remote images/streams.
          if (event.request.mode === 'navigate') {
            return caches.match('/index.html');
          }
          return new Response('', { status: 504, statusText: 'Gateway Timeout' });
        });
      })
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.map(key => (key !== CACHE_NAME ? caches.delete(key) : Promise.resolve())))
    )
  );
});
