(function () {
    const TEMPLATE = `
<div class="uiptv-player-header" data-uiptv-header>
    <div class="uiptv-player-title" data-uiptv-title>
        <div id="media-title" class="uiptv-title-line">
            <span id="media-title-text"></span>
            <span id="media-loading-spinner" class="spinner-border spinner-border-sm text-info" role="status" aria-hidden="true" hidden></span>
        </div>
        <div id="media-subtitle" class="uiptv-subtitle"></div>
    </div>
    <div class="uiptv-player-controls" aria-label="Player controls">
        <button id="favorite-btn" class="uiptv-control-btn" type="button" title="Favorite" data-action="favorite" data-label="Favorite">
            <i class="bi bi-heart"></i>
        </button>
        <button id="reload-btn" class="uiptv-control-btn" type="button" title="Reload" data-action="reload" data-label="Reload">
            <i class="bi bi-arrow-clockwise"></i>
        </button>
        <div class="uiptv-control-menu uiptv-strategy-menu" data-menu="strategy">
            <button id="strategy-menu-btn" class="uiptv-control-btn" type="button" title="Reload strategy" data-action="strategy-menu" data-label="Strategy">
                <span id="strategy-label">Auto</span>
            </button>
            <div id="strategy-menu" class="uiptv-menu top-menu" hidden></div>
        </div>
        <button id="repeat-btn" class="uiptv-control-btn" type="button" title="Repeat" data-action="repeat" data-label="Repeat">
            <i class="bi bi-repeat"></i>
        </button>
        <button id="pip-btn" class="uiptv-control-btn" type="button" title="Picture in Picture" data-action="pip" data-label="Picture in Picture">
            <i class="bi bi-pip"></i>
        </button>
        <button id="mute-btn" class="uiptv-control-btn" type="button" title="Mute" data-action="mute" data-label="Mute">
            <i class="bi bi-volume-up"></i>
        </button>
        <button id="fullscreen-btn" class="uiptv-control-btn" type="button" title="Fullscreen" data-action="fullscreen" data-label="Fullscreen">
            <i class="bi bi-fullscreen"></i>
        </button>
        <div class="uiptv-control-menu" data-menu="quality">
            <button id="quality-menu-btn" class="uiptv-control-btn" type="button" title="Resolutions" data-action="quality-menu" data-label="Quality">
                <i class="bi bi-gear-fill"></i>
                <span id="quality-label">Quality</span>
            </button>
            <div id="quality-menu" class="uiptv-menu top-menu" hidden></div>
        </div>
        <div class="uiptv-control-menu" data-menu="audio">
            <button id="audio-menu-btn" class="uiptv-control-btn" type="button" title="Audio tracks" data-action="audio-menu" data-label="Audio">
                <i class="bi bi-music-note-list"></i>
                <span id="audio-label">Audio</span>
            </button>
            <div id="audio-menu" class="uiptv-menu top-menu" hidden></div>
        </div>
        <div class="uiptv-control-menu" data-menu="subtitle">
            <button id="subtitle-menu-btn" class="uiptv-control-btn" type="button" title="Subtitles" data-action="subtitle-menu" data-label="Subtitles">
                <i class="bi bi-badge-cc"></i>
                <span id="subtitle-label">Subtitles</span>
            </button>
            <div id="subtitle-menu" class="uiptv-menu top-menu" hidden></div>
        </div>
        <button id="stop-btn" class="uiptv-control-btn" type="button" title="Stop" data-action="stop" data-label="Stop">
            <i class="bi bi-stop-circle-fill"></i>
        </button>
    </div>
</div>
`;

    const resolveTitle = (title, mode) => {
        const clean = (value) => String(value || '').trim();
        const base = clean(title);
        const suffix = clean(mode);
        if (!base) return '';
        return suffix ? `${base} [${suffix}]` : base;
    };

    const mount = (root, options = {}) => {
        if (!root) return null;
        root.innerHTML = TEMPLATE;
        const header = root.querySelector('.uiptv-player-header');
        if (options.variant && header) {
            header.dataset.variant = options.variant;
        }
        if (header && !header.dataset.state) {
            header.dataset.state = 'inactive';
        }

        const nodes = {
            header,
            titleText: root.querySelector('#media-title-text'),
            titleSpinner: root.querySelector('#media-loading-spinner'),
            subtitle: root.querySelector('#media-subtitle'),
            controls: root.querySelector('.uiptv-player-controls'),
            buttons: Array.from(root.querySelectorAll('[data-action]')),
            menus: {
                strategy: root.querySelector('#strategy-menu'),
                quality: root.querySelector('#quality-menu'),
                audio: root.querySelector('#audio-menu'),
                subtitle: root.querySelector('#subtitle-menu')
            },
            menuButtons: {
                strategy: root.querySelector('#strategy-menu-btn'),
                quality: root.querySelector('#quality-menu-btn'),
                audio: root.querySelector('#audio-menu-btn'),
                subtitle: root.querySelector('#subtitle-menu-btn')
            },
            menuWrappers: {
                strategy: root.querySelector('[data-menu="strategy"]'),
                quality: root.querySelector('[data-menu="quality"]'),
                audio: root.querySelector('[data-menu="audio"]'),
                subtitle: root.querySelector('[data-menu="subtitle"]')
            }
        };

        let actionHandlers = {};

        const closeMenus = () => {
            Object.values(nodes.menus).forEach((menu) => {
                if (menu) menu.hidden = true;
            });
        };

        const toggleMenu = (menuKey) => {
            const menu = nodes.menus[menuKey];
            if (!menu) return;
            const willOpen = menu.hidden;
            closeMenus();
            menu.hidden = !willOpen;
        };

        const bindActions = (actions = {}, options = {}) => {
            const alwaysEnabledActions = new Set(['reload', 'strategy-menu']);
            actionHandlers = actions || {};
            const hideMissing = options.hideMissing === true;
            nodes.buttons.forEach((button) => {
                const action = button.dataset.action;
                let handler = actionHandlers[action];
                if (typeof handler !== 'function' && alwaysEnabledActions.has(action)) {
                    handler = () => {};
                    actionHandlers[action] = handler;
                }
                if (typeof handler !== 'function') {
                    if (hideMissing) {
                        button.hidden = true;
                        return;
                    }
                    button.classList.add('uiptv-disabled');
                    button.setAttribute('aria-disabled', 'true');
                    const label = button.dataset.label || button.title || 'Action';
                    button.title = `${label} is not available`;
                    button.addEventListener('click', (event) => {
                        event.preventDefault();
                        event.stopPropagation();
                    });
                    return;
                }
                button.hidden = false;
                button.addEventListener('click', (event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    if (button.classList.contains('uiptv-disabled')) {
                        return;
                    }
                    if (action === 'strategy-menu') return toggleMenu('strategy');
                    if (action === 'quality-menu') return toggleMenu('quality');
                    if (action === 'audio-menu') return toggleMenu('audio');
                    if (action === 'subtitle-menu') return toggleMenu('subtitle');
                    handler(event);
                });
            });

            Object.entries(nodes.menuButtons).forEach(([key, btn]) => {
                if (!btn) return;
                const actionKey = `${key}-menu`;
                if (typeof actionHandlers[actionKey] === 'function') {
                    btn.hidden = false;
                }
            });
        };

        const renderMenuItems = (menuEl, items = []) => {
            if (!menuEl) return;
            menuEl.innerHTML = '';
            if (!items || items.length === 0) {
                menuEl.hidden = true;
                return;
            }
            items.forEach((item) => {
                const button = document.createElement('button');
                button.type = 'button';
                button.className = `uiptv-menu-item${item.active ? ' active' : ''}${item.muted ? ' muted' : ''}`;
                button.textContent = item.label || '';
                button.disabled = !!item.disabled;
                button.addEventListener('click', (event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    if (item.disabled) return;
                    if (typeof item.onSelect === 'function') {
                        item.onSelect(item);
                    }
                    closeMenus();
                });
                menuEl.appendChild(button);
            });
        };

        const setMenus = ({strategy = [], quality = [], audio = [], subtitle = []} = {}) => {
            renderMenuItems(nodes.menus.strategy, strategy);
            renderMenuItems(nodes.menus.quality, quality);
            renderMenuItems(nodes.menus.audio, audio);
            renderMenuItems(nodes.menus.subtitle, subtitle);
            const setMenuAvailability = (key, items) => {
                const btn = nodes.menuButtons[key];
                if (!btn) return;
                if (key === 'strategy') {
                    btn.classList.remove('uiptv-disabled');
                    btn.removeAttribute('aria-disabled');
                    btn.title = btn.dataset.label || btn.title;
                    return;
                }
                const hasItems = items && items.length > 0;
                if (hasItems) {
                    btn.classList.remove('uiptv-disabled');
                    btn.removeAttribute('aria-disabled');
                    btn.title = btn.dataset.label || btn.title;
                } else {
                    btn.classList.add('uiptv-disabled');
                    btn.setAttribute('aria-disabled', 'true');
                    const label = btn.dataset.label || btn.title || 'Menu';
                    btn.title = `${label} is not available`;
                }
            };
            setMenuAvailability('strategy', strategy);
            setMenuAvailability('quality', quality);
            setMenuAvailability('audio', audio);
            setMenuAvailability('subtitle', subtitle);
        };

        const setTitle = ({title, mode, subtitle, loading} = {}) => {
            if (nodes.titleText) {
                nodes.titleText.textContent = resolveTitle(title, mode);
            }
            if (nodes.subtitle) {
                nodes.subtitle.textContent = String(subtitle || '').trim();
            }
            if (nodes.titleSpinner) {
                const isLoading = !!loading;
                nodes.titleSpinner.hidden = !isLoading;
                nodes.titleSpinner.style.display = isLoading ? 'inline-block' : 'none';
            }
        };

        const setState = ({repeatEnabled, isMuted, isFullscreen, isFavorite, isPlaying, strategyLabel} = {}) => {
            const repeatBtn = root.querySelector('#repeat-btn');
            const muteBtn = root.querySelector('#mute-btn');
            const fullscreenBtn = root.querySelector('#fullscreen-btn');
            const favoriteBtn = root.querySelector('#favorite-btn');
            const strategyLabelEl = root.querySelector('#strategy-label');
            if (nodes.header && typeof isPlaying !== 'undefined') {
                nodes.header.dataset.state = isPlaying ? 'active' : 'inactive';
                if (!isPlaying) {
                    closeMenus();
                }
            }
            if (strategyLabelEl && strategyLabel) {
                strategyLabelEl.textContent = strategyLabel;
            }
            if (repeatBtn) {
                repeatBtn.setAttribute('aria-pressed', repeatEnabled ? 'true' : 'false');
                const icon = repeatBtn.querySelector('i');
                if (icon) icon.className = repeatEnabled ? 'bi bi-repeat-1 text-warning' : 'bi bi-repeat';
            }
            if (muteBtn) {
                muteBtn.setAttribute('aria-pressed', isMuted ? 'true' : 'false');
                muteBtn.title = isMuted ? 'Unmute' : 'Mute';
                const icon = muteBtn.querySelector('i');
                if (icon) icon.className = isMuted ? 'bi bi-volume-mute-fill text-warning' : 'bi bi-volume-up';
            }
            if (fullscreenBtn) {
                fullscreenBtn.setAttribute('aria-pressed', isFullscreen ? 'true' : 'false');
                fullscreenBtn.title = isFullscreen ? 'Exit fullscreen' : 'Fullscreen';
                const icon = fullscreenBtn.querySelector('i');
                if (icon) icon.className = isFullscreen ? 'bi bi-fullscreen-exit' : 'bi bi-fullscreen';
            }
            if (favoriteBtn) {
                favoriteBtn.setAttribute('aria-pressed', isFavorite ? 'true' : 'false');
                const icon = favoriteBtn.querySelector('i');
                if (icon) icon.className = isFavorite ? 'bi bi-heart-fill text-danger' : 'bi bi-heart';
            }
        };

        document.addEventListener('click', (event) => {
            if (!root.contains(event.target)) {
                closeMenus();
            }
        });

        return {
            bindActions,
            setMenus,
            setTitle,
            setState,
            closeMenus
        };
    };

    window.UIPTVSharedPlayer = {
        mount
    };
})();
