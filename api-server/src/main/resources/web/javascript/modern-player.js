(function () {
    // Keep the UI and adapters on one release: their shared chunks carry state.
    const CDN = 'https://cdn.jsdelivr.net/npm/@videojs/cdn@10.1.0/';
    let loading;
    const ready = () => loading || (loading = Promise.all([
        import(CDN + 'video.js'),
        import(CDN + 'media/hlsjs-video.js'),
        import(CDN + 'media/dash-video.js')
    ]));

    const syncControls = (media) => {
        media.controls = !media.closest('video-player');
        if (!media.controls) media.removeAttribute('controls');
    };

    const mount = (container, tag = 'video') => {
        const host = document.createElement('video-player');
        const skin = document.createElement('video-skin');
        const media = document.createElement(tag);
        media.dataset.uiptvMedia = 'true';
        media.autoplay = true;
        media.preload = 'auto';
        media.setAttribute('playsinline', '');
        skin.appendChild(media);
        host.appendChild(skin);
        container.replaceChildren(host);
        syncControls(media);
        return media;
    };

    // Upgrading a native engine retains its media node and attached listeners.
    const wrap = (media) => {
        if (!customElements.get('video-player') || media.closest('video-player')) return;
        const host = document.createElement('video-player');
        const skin = document.createElement('video-skin');
        media.dataset.uiptvMedia = 'true';
        media.replaceWith(host);
        host.appendChild(skin);
        skin.appendChild(media);
        syncControls(media);
    };

    const connect = (media, onTracks) => {
        const host = media.closest('video-player');
        const player = {
            media,
            get store() { return host.store; },
            dispose() {
                unsubscribe?.();
                for (const name of ['onended', 'onerror', 'onvolumechange', 'ontimeupdate', 'ondurationchange', 'onloadedmetadata', 'onprogress']) media[name] = null;
                media.pause();
                if (media.tagName === 'VIDEO') {
                    media.removeAttribute('src');
                    media.load();
                }
                // Disconnecting a media component releases its streaming engine.
                host.remove();
            }
        };
        const unsubscribe = host.store.subscribe(() => onTracks(player));
        return player;
    };

    window.UIPTVModernPlayer = {ready, mount, wrap, connect, syncControls};
})();
