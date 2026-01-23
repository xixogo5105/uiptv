const CACHE_NAME = 'uiptv-cache-v1';
const urlsToCache = [
  '/',
  '/index.html',
  '/css/spa.css',
  '/javascript/spa.js',
  'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.2/font/bootstrap-icons.css',
  'https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css',
  'https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/js/bootstrap.bundle.min.js',
  'https://vjs.zencdn.net/8.6.1/video-js.css',
  'https://vjs.zencdn.net/8.6.1/video.min.js',
  'https://cdn.jsdelivr.net/npm/videojs-http-streaming@3.9.0/dist/videojs-http-streaming.min.js',
  'https://cdn.jsdelivr.net/npm/videojs-youtube@3.0.1/dist/Youtube.min.js',
  'https://unpkg.com/vue@3/dist/vue.global.js'
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
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        if (response) {
          return response;
        }
        return fetch(event.request);
      })
  );
});