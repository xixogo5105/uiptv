const CACHE_NAME = 'uiptv-cache-v27';
const urlsToCache = [
  '/',
  '/index.html',
  '/manifest.json',
  '/icon.ico',
  '/icon.png',
  '/icon-192.png',
  '/icon-512.png',
  '/icon-maskable-512.png',
  '/css/shared-player.css',
  '/css/spa.css',
  '/javascript/playback-utils.js',
  '/javascript/player-controls.js',
  '/javascript/shared-player.js',
  '/javascript/bookmark-watch-utils.js',
  '/javascript/spa.js',
  'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.13.1/font/bootstrap-icons.css',
  'https://unpkg.com/vue@3/dist/vue.global.prod.js',
  'https://cdn.jsdelivr.net/npm/shaka-player@5/dist/shaka-player.compiled.js',
  'https://cdn.jsdelivr.net/npm/mpegts.js@1.8.0/dist/mpegts.min.js'
];

const urlsToCacheSet = new Set(urlsToCache);

const isVersionedStaticAsset = (requestUrl) => {
  if (requestUrl.origin !== self.location.origin) {
    return false;
  }
  return requestUrl.pathname.startsWith('/css/')
    || requestUrl.pathname.startsWith('/javascript/')
    || requestUrl.pathname === '/manifest.json'
    || requestUrl.pathname === '/icon.ico'
    || requestUrl.pathname === '/icon.png'
    || requestUrl.pathname === '/icon-192.png'
    || requestUrl.pathname === '/icon-512.png'
    || requestUrl.pathname === '/icon-maskable-512.png';
};

const isPrecachedRequest = (requestUrl) => {
  if (requestUrl.origin === self.location.origin) {
    return urlsToCacheSet.has(requestUrl.pathname);
  }
  return urlsToCacheSet.has(requestUrl.href);
};

const isAppShellNavigation = (request, requestUrl) => {
  if (requestUrl.origin !== self.location.origin) {
    return false;
  }
  return request.mode === 'navigate'
    || (request.headers.get('accept') || '').includes('text/html');
};

const cacheFirst = async (request, requestUrl) => {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }

  try {
    const response = await fetch(request);
    if (response && (response.ok || response.type === 'opaque')) {
      const cache = await caches.open(CACHE_NAME);
      await cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    if (isVersionedStaticAsset(requestUrl)) {
      const fallback = await caches.match(requestUrl.pathname);
      if (fallback) {
        return fallback;
      }
    }
    throw error;
  }
};

const networkFirstAppShell = async (request) => {
  const cache = await caches.open(CACHE_NAME);
  try {
    const response = await fetch(request);
    if (response && response.ok) {
      await cache.put(request, response.clone());
    }
    return response;
  } catch (_) {
    return (await caches.match(request, {ignoreSearch: true}))
      || (await caches.match('/index.html'));
  }
};

self.addEventListener('install', event => {
  self.skipWaiting();
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

  if (isAppShellNavigation(event.request, requestUrl)) {
    event.respondWith(networkFirstAppShell(event.request));
    return;
  }

  if (isPrecachedRequest(requestUrl) || isVersionedStaticAsset(requestUrl)) {
    event.respondWith(cacheFirst(event.request, requestUrl));
  }
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.map(key => (key !== CACHE_NAME ? caches.delete(key) : Promise.resolve())))
    ).then(() => self.clients.claim())
  );
});
