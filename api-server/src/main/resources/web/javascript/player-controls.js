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

    const createPlaybackNavigation = (items, currentItem, play, isPlayable = item => !!item?.cmd) => {
        if (!Array.isArray(items) || typeof play !== 'function' || typeof isPlayable !== 'function') return null;
        const playableItems = items.filter(item => item && isPlayable(item));
        const currentIndex = playableItems.indexOf(currentItem);
        if (playableItems.length < 2 || currentIndex < 0) return null;
        return {items: playableItems, currentIndex, play};
    };

    const movePlaybackNavigation = (navigation, offset) => {
        if (!navigation || !Array.isArray(navigation.items)) return null;
        const nextIndex = navigation.currentIndex + offset;
        if (nextIndex < 0 || nextIndex >= navigation.items.length) return null;
        return {...navigation, currentIndex: nextIndex};
    };

    window.UIPTVControls = {
        onControlClick,
        ensurePlaybackNotPaused,
        createPlaybackNavigation,
        movePlaybackNavigation
    };
})();
