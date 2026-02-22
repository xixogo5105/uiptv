const { createApp, ref, computed, onMounted, nextTick, watch } = Vue;

createApp({
    setup() {
        // State
        const activeTab = ref('bookmarks');
        const viewState = ref('accounts'); // accounts, categories, channels
        const searchQuery = ref('');
        const contentMode = ref('itv'); // itv, vod, series

        const accounts = ref([]);
        const categories = ref([]);
        const channels = ref([]);
        const episodes = ref([]);
        const bookmarks = ref([]);
        const favorites = ref([]);

        const currentContext = ref({ accountId: null, categoryId: null, accountType: null });
        const currentChannel = ref(null);
        const isPlaying = ref(false);
        const showOverlay = ref(false);
        const showBookmarkModal = ref(false);

        // Player State
        const playerKey = ref(0);
        const isYoutube = ref(false);
        const youtubeSrc = ref('');
        const playerInstance = ref(null); // Will hold Shaka player instance
        const videoPlayer = ref(null); // Ref to the <video> element
        const videoTracks = ref([]); // For resolution switching

        // Theme State: 'system', 'dark', 'light'
        const theme = ref('system');

        // Computed
        const searchPlaceholder = computed(() => {
            if (activeTab.value === 'accounts') {
                if (viewState.value === 'accounts') return 'Search accounts...';
                if (viewState.value === 'categories') return 'Search categories...';
                if (viewState.value === 'channels') return 'Search channels...';
            }
            if (activeTab.value === 'bookmarks') return 'Search bookmarks...';
            if (activeTab.value === 'favorites') return 'Search favorites...';
            if (activeTab.value === 'downloads') return 'Search downloads...';
            return 'Search...';
        });

        const filteredAccounts = computed(() => {
            if (!searchQuery.value) return accounts.value;
            return accounts.value.filter(a => a.accountName.toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredCategories = computed(() => {
            if (!searchQuery.value) return categories.value;
            return categories.value.filter(c => c.title.toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredChannels = computed(() => {
            if (!searchQuery.value) return channels.value;
            return channels.value.filter(c => c.name.toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredEpisodes = computed(() => {
            if (!searchQuery.value) return episodes.value;
            return episodes.value.filter(c => c.name.toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredBookmarks = computed(() => {
            if (!searchQuery.value) return bookmarks.value;
            const q = searchQuery.value.toLowerCase();
            return bookmarks.value.filter(b =>
                (b.channelName || '').toLowerCase().includes(q) ||
                (b.accountName || '').toLowerCase().includes(q)
            );
        });

        const filteredFavorites = computed(() => {
            if (!searchQuery.value) return favorites.value;
            return favorites.value.filter(f => f.name.toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const currentChannelName = computed(() => currentChannel.value ? currentChannel.value.name : '');

        const favoriteKey = (item) => {
            if (!item) return '';
            const id = String(item.id ?? item.dbId ?? '');
            const name = String(item.name ?? item.channelName ?? '').trim().toLowerCase();
            const type = item.type ?? (item.accountId && item.categoryId ? 'channel' : 'bookmark');
            return `${type}::${id}::${name}`;
        };

        const isCurrentFavorite = computed(() => {
            if (!currentChannel.value) return false;
            const key = favoriteKey(currentChannel.value);
            return favorites.value.some(f => favoriteKey(f) === key);
        });

        const themeIcon = computed(() => {
            if (theme.value === 'system') return 'bi-display';
            if (theme.value === 'dark') return 'bi-moon-fill';
            if (theme.value === 'light') return 'bi-sun-fill';
            return 'bi-display';
        });

        // Methods
        const loadAccounts = async () => {
            try {
                const response = await fetch(window.location.origin + "/accounts");
                accounts.value = await response.json();
            } catch (e) {
                console.error("Failed to load accounts", e);
            }
        };

        const loadCategories = async (accountId) => {
            const selected = typeof accountId === 'object'
                ? accountId
                : accounts.value.find(a => a.dbId === accountId);
            currentContext.value.accountId = selected?.dbId || accountId;
            currentContext.value.accountType = selected?.type || null;
            try {
                const response = await fetch(
                    window.location.origin + "/categories?accountId=" + currentContext.value.accountId + "&mode=" + contentMode.value
                );
                categories.value = await response.json();
                channels.value = [];
                episodes.value = [];
                viewState.value = 'categories';
                searchQuery.value = '';
            } catch (e) {
                console.error("Failed to load categories", e);
            }
        };

        const loadChannels = async (categoryId) => {
            currentContext.value.categoryId = categoryId;
            try {
                const response = await fetch(
                    window.location.origin + "/channels?categoryId=" + categoryId + "&accountId=" + currentContext.value.accountId + "&mode=" + contentMode.value
                );
                channels.value = await response.json();
                episodes.value = [];
                viewState.value = 'channels';
                searchQuery.value = '';
            } catch (e) {
                console.error("Failed to load channels", e);
            }
        };

        const loadSeriesEpisodes = async (seriesId) => {
            try {
                const response = await fetch(
                    window.location.origin + "/seriesEpisodes?seriesId=" + encodeURIComponent(seriesId) + "&accountId=" + currentContext.value.accountId
                );
                episodes.value = await response.json();
                viewState.value = 'episodes';
                searchQuery.value = '';
            } catch (e) {
                console.error("Failed to load series episodes", e);
            }
        };

        const loadBookmarks = async () => {
            try {
                const response = await fetch(window.location.origin + "/bookmarks");
                bookmarks.value = await response.json();
                enrichFavoritesFromBookmarks();
            } catch (e) {
                console.error("Failed to load bookmarks", e);
            }
        };

        const loadFavorites = () => {
            const stored = localStorage.getItem('uiptv_favorites');
            if (stored) {
                favorites.value = JSON.parse(stored);
                enrichFavoritesFromBookmarks();
            }
        };

        const saveFavorites = () => {
            localStorage.setItem('uiptv_favorites', JSON.stringify(favorites.value));
        };

        const enrichFavoritesFromBookmarks = () => {
            if (!favorites.value.length || !bookmarks.value.length) return;

            const bookmarkById = new Map(bookmarks.value.map(b => [String(b.dbId), b]));
            let changed = false;

            favorites.value = favorites.value.map(f => {
                if (f.logo || f.type !== 'bookmark') return f;
                const bookmark = bookmarkById.get(String(f.id));
                if (!bookmark || !bookmark.logo) return f;
                changed = true;
                return {
                    ...f,
                    logo: bookmark.logo,
                    accountName: f.accountName || bookmark.accountName,
                    name: f.name || bookmark.channelName
                };
            });

            if (changed) saveFavorites();
        };

        const switchTab = (tab) => {
            if (activeTab.value === tab && tab === 'accounts') {
                viewState.value = 'accounts';
                searchQuery.value = '';
            } else {
                activeTab.value = tab;
                searchQuery.value = '';
            }
        };

        const setContentMode = (mode) => {
            if (!['itv', 'vod', 'series'].includes(mode)) return;
            if (contentMode.value === mode) return;
            contentMode.value = mode;
            if (activeTab.value !== 'accounts') {
                activeTab.value = 'accounts';
            }
            viewState.value = 'accounts';
            categories.value = [];
            channels.value = [];
            episodes.value = [];
            currentContext.value.categoryId = null;
            searchQuery.value = '';
        };

        const goBackToAccounts = () => {
            viewState.value = 'accounts';
            searchQuery.value = '';
        };

        const goBackToCategories = () => {
            viewState.value = viewState.value === 'episodes' ? 'channels' : 'categories';
            searchQuery.value = '';
        };

        const buildPlayerUrlForChannel = (channel, modeOverride = null) => {
            const modeToUse = modeOverride || contentMode.value;
            const channelDbId = channel.dbId || '';
            const channelIdentifier = channel.channelId || channel.id || '';
            const query = new URLSearchParams();
            query.set('accountId', currentContext.value.accountId || '');
            query.set('categoryId', currentContext.value.categoryId || '');
            query.set('mode', modeToUse);

            if (channelDbId) {
                query.set('channelId', channelDbId);
                if (modeToUse === 'series') {
                    query.set('seriesId', channelIdentifier);
                }
            } else {
                query.set('channelId', channelIdentifier);
                query.set('name', channel.name || '');
                query.set('logo', channel.logo || '');
                query.set('cmd', channel.cmd || '');
                query.set('drmType', channel.drmType || '');
                query.set('drmLicenseUrl', channel.drmLicenseUrl || '');
                query.set('clearKeysJson', channel.clearKeysJson || '');
                query.set('inputstreamaddon', channel.inputstreamaddon || '');
                query.set('manifestType', channel.manifestType || '');
                if (modeToUse === 'series') {
                    query.set('seriesId', channelIdentifier);
                }
            }
            return window.location.origin + "/player?" + query.toString();
        };

        const playChannel = (channel) => {
            scrollToTop();
            const channelIdentifier = channel.dbId || channel.channelId || channel.id;
            currentChannel.value = {
                id: channelIdentifier,
                dbId: channel.dbId || '',
                channelId: channel.channelId || channelIdentifier,
                name: channel.name,
                logo: channel.logo,
                cmd: channel.cmd,
                drmType: channel.drmType,
                drmLicenseUrl: channel.drmLicenseUrl,
                clearKeysJson: channel.clearKeysJson,
                inputstreamaddon: channel.inputstreamaddon,
                manifestType: channel.manifestType,
                accountId: currentContext.value.accountId,
                categoryId: currentContext.value.categoryId,
                type: 'channel',
                mode: contentMode.value,
                clearKeys: channel.clearKeys
            };
            startPlayback(buildPlayerUrlForChannel(channel));
        };

        const playBookmark = (bookmark) => {
            scrollToTop();
            currentChannel.value = {
                id: bookmark.dbId,
                name: bookmark.channelName,
                logo: bookmark.logo,
                accountName: bookmark.accountName,
                type: 'bookmark'
            };
            startPlayback(window.location.origin + "/player?bookmarkId=" + bookmark.dbId);
        };

        const playFavorite = (fav) => {
            scrollToTop();
            const favoriteType = fav.type || (fav.accountId && fav.categoryId ? 'channel' : 'bookmark');
            currentChannel.value = {
                ...fav,
                type: favoriteType,
                name: fav.name || fav.channelName || ''
            };

            if (favoriteType === 'channel') {
                const modeToUse = fav.mode || 'itv';
                const query = new URLSearchParams();
                query.set('accountId', fav.accountId || '');
                query.set('categoryId', fav.categoryId || '');
                query.set('mode', modeToUse);
                if (fav.dbId) {
                    query.set('channelId', fav.dbId);
                    if (modeToUse === 'series') {
                        query.set('seriesId', fav.channelId || fav.id || '');
                    }
                } else {
                    query.set('channelId', fav.channelId || fav.id || '');
                    query.set('name', fav.name || '');
                    query.set('logo', fav.logo || '');
                    query.set('cmd', fav.cmd || '');
                    query.set('drmType', fav.drmType || '');
                    query.set('drmLicenseUrl', fav.drmLicenseUrl || '');
                    query.set('clearKeysJson', fav.clearKeysJson || '');
                    query.set('inputstreamaddon', fav.inputstreamaddon || '');
                    query.set('manifestType', fav.manifestType || '');
                    if (modeToUse === 'series') {
                        query.set('seriesId', fav.channelId || fav.id || '');
                    }
                }
                startPlayback(window.location.origin + "/player?" + query.toString());
            } else {
                startPlayback(window.location.origin + "/player?bookmarkId=" + fav.id);
            }
        };

        const handleChannelSelection = async (channel) => {
            if (contentMode.value === 'series' &&
                currentContext.value.accountType === 'XTREME_API' &&
                viewState.value === 'channels') {
                const seriesId = channel.channelId || channel.id || channel.dbId;
                await loadSeriesEpisodes(seriesId);
                return;
            }
            playChannel(channel);
        };

        // --- NEW PLAYER LOGIC ---

        const startPlayback = async (url) => {
            await stopPlayback(true); // Stop current playback without collapsing UI

            playerKey.value++;
            isPlaying.value = true;

            try {
                const response = await fetch(url);
                const channelData = await response.json();
                await initPlayer(channelData);
            } catch (e) {
                console.error("Failed to start playback", e);
                isPlaying.value = false;
            }
        };

        const stopPlayback = async (preserveUi = false) => {
            if (playerInstance.value) {
                try {
                    await playerInstance.value.destroy();
                } catch (e) {
                    console.warn("Error destroying Shaka player", e);
                }
                playerInstance.value = null;
            }
            if (videoPlayer.value) {
                videoPlayer.value.pause();
                videoPlayer.value.src = '';
                videoPlayer.value.removeAttribute('src'); // Good practice
                videoPlayer.value.load();
            }
            if (!preserveUi) {
                isPlaying.value = false;
                currentChannel.value = null;
            }
            isYoutube.value = false;
            youtubeSrc.value = '';
            videoTracks.value = [];
        };

        const initPlayer = async (channel) => {
            const uri = channel.url;
            if (!uri) {
                console.error("No URL provided for playback.");
                isPlaying.value = false;
                return;
            }

            if (uri.includes("youtube.com") || uri.includes("youtu.be")) {
                isYoutube.value = true;
                const videoId = uri.split('v=')[1] ? uri.split('v=')[1].split('&')[0] : uri.split('/').pop();
                youtubeSrc.value = `https://www.youtube.com/embed/${videoId}?autoplay=1`;
                return;
            }

            isYoutube.value = false;
            await nextTick();

            const video = videoPlayer.value;
            if (!video) {
                console.error("Video element not found.");
                return;
            }

            const isApple = /iPhone|iPad|iPod|Macintosh/i.test(navigator.userAgent);
            const canNative = video.canPlayType('application/vnd.apple.mpegurl');
            const hasDRM = channel.drm != null;

            if (hasDRM) {
                console.log("Player choice: Shaka (DRM)");
                await loadShaka(channel);
            } else if (isApple && canNative) {
                console.log("Player choice: Native (Apple HLS)");
                await loadNative(channel);
            } else if (canNative && uri.endsWith('.m3u8')) { // Be more specific for native
                 console.log("Player choice: Native (HLS)");
                 await loadNative(channel);
            } else {
                console.log("Player choice: Shaka (Fallback)");
                await loadShaka(channel);
            }
        };

        const loadNative = async (channel) => {
            await nextTick();
            const video = videoPlayer.value;
            if (video) {
                video.src = channel.url;
                try {
                    await video.play();
                } catch (e) {
                    console.warn("Autoplay was prevented.", e);
                }
            }
        };

        const loadShaka = async (channel) => {
            await nextTick();
            const video = videoPlayer.value;
            if (!video) return;

            // Install polyfills
            shaka.polyfill.installAll();

            // Check browser support
            if (!shaka.Player.isBrowserSupported()) {
                console.error("Shaka Player is not supported by this browser.");
                return;
            }

            const player = new shaka.Player(video);
            playerInstance.value = player;

            // Listen for errors
            player.addEventListener('error', (event) => {
                console.error('Shaka Player Error:', event.detail);
            });

            // Configure DRM if present
            if (channel.drm) {
                let drmConfig = {};
                if (channel.drm.licenseUrl) {
                    drmConfig.servers = { [channel.drm.type]: channel.drm.licenseUrl };
                }
                if (channel.drm.clearKeys) {
                    drmConfig.clearKeys = channel.drm.clearKeys;
                }
                player.configure({ drm: drmConfig });
            }

            try {
                await player.load(channel.url);
                console.log('Shaka: Video loaded successfully.');
                videoTracks.value = player.getVariantTracks();
            } catch (e) {
                console.error('Shaka: Error loading video:', e);
            }
        };

        const switchVideoTrack = (trackId) => {
            if (playerInstance.value) {
                playerInstance.value.selectVariantTrack(playerInstance.value.getVariantTracks().find(t => t.id === trackId), true);
            }
        };

        // --- END NEW PLAYER LOGIC ---

        const toggleFavorite = () => {
            if (!currentChannel.value) return;

            const favoriteItem = {
                ...currentChannel.value,
                id: currentChannel.value.id ?? currentChannel.value.dbId,
                name: currentChannel.value.name || currentChannel.value.channelName || '',
                type: currentChannel.value.type || (currentChannel.value.accountId && currentChannel.value.categoryId ? 'channel' : 'bookmark'),
                mode: currentChannel.value.mode || contentMode.value
            };

            const currentKey = favoriteKey(favoriteItem);
            const index = favorites.value.findIndex(f => favoriteKey(f) === currentKey);

            if (index === -1) {
                favorites.value.push(favoriteItem);
            } else {
                favorites.value.splice(index, 1);
            }
            saveFavorites();
        };

        const imageError = (e) => {
            e.target.style.display = 'none';
            const icon = e.target.nextElementSibling;
            if (icon && icon.classList.contains('icon-placeholder')) {
                icon.style.display = 'block';
            }
        };

        const mediaImageError = (e) => {
            e.target.onerror = null;
            e.target.removeAttribute('src');
            e.target.style.display = 'none';
            const fallback = e.target.parentElement?.querySelector('.nf-media-fallback');
            if (fallback) {
                fallback.classList.add('show');
            }
        };

        const scrollToTop = () => {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        };

        const toggleTheme = () => {
            if (theme.value === 'system') {
                theme.value = 'dark';
            } else if (theme.value === 'dark') {
                theme.value = 'light';
            } else {
                theme.value = 'system';
            }
            localStorage.setItem('uiptv_theme', theme.value);
            applyTheme();
        };

        const applyTheme = () => {
            if (theme.value === 'system') {
                document.documentElement.removeAttribute('data-theme');
            } else {
                document.documentElement.setAttribute('data-theme', theme.value);
            }
        };

        const resetApp = () => {
            stopPlayback();
            activeTab.value = 'bookmarks';
            viewState.value = 'accounts'; // Or any default view
            contentMode.value = 'itv';
            searchQuery.value = '';
        };

        // Lifecycle
        onMounted(() => {
            loadAccounts();
            loadBookmarks();
            loadFavorites();

            // Load theme
            const storedTheme = localStorage.getItem('uiptv_theme');
            if (storedTheme) {
                theme.value = storedTheme;
            }
            applyTheme();
        });

        return {
            activeTab,
            viewState,
            searchQuery,
            searchPlaceholder,
            filteredAccounts,
            filteredCategories,
            filteredChannels,
            filteredEpisodes,
            filteredBookmarks,
            filteredFavorites,
            currentChannelName,
            isCurrentFavorite,
            isPlaying,
            showOverlay,
            showBookmarkModal,
            playerKey,
            isYoutube,
            youtubeSrc,
            videoPlayer,
            videoTracks,
            theme,
            themeIcon,
            contentMode,

            switchTab,
            setContentMode,
            loadCategories,
            loadChannels,
            loadSeriesEpisodes,
            goBackToAccounts,
            goBackToCategories,
            playChannel,
            handleChannelSelection,
            playBookmark,
            playFavorite,
            stopPlayback,
            toggleFavorite,
            imageError,
            mediaImageError,
            toggleTheme,
            resetApp,
            switchVideoTrack
        };
    }
}).mount('#app');
