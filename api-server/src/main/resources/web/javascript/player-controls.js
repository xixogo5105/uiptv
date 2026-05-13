(function () {
    const onControlClick = async (event, action, ensurePlayback, ...args) => {
        if (event) {
            if (typeof event.preventDefault === 'function') event.preventDefault();
            if (typeof event.stopPropagation === 'function') event.stopPropagation();
        }
        if (typeof action === 'function') {
            await action(...args);
        }
        if (typeof ensurePlayback === 'function') {
            await ensurePlayback();
        }
    };

    const ensurePlaybackNotPaused = async (video) => {
        if (!video) return;
        if (!video.paused || video.ended) return;
        try {
            await video.play();
        } catch (_) {
            // Ignore autoplay/focus errors.
        }
    };

    window.UIPTVControls = {
        onControlClick,
        ensurePlaybackNotPaused
    };
})();
