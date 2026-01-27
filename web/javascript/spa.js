const { createApp, ref, computed, onMounted, nextTick, watch } = Vue;

createApp({
    setup() {
        // State
        const activeTab = ref('bookmarks');
        const viewState = ref('accounts'); // accounts, categories, channels
        const searchQuery = ref('');

        const accounts = ref([]);
        const categories = ref([]);
        const channels = ref([]);
        const bookmarks = ref([]);
        const favorites = ref([]);

        const currentContext = ref({ accountId: null, categoryId: null });
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

        const filteredBookmarks = computed(() => {
            if (!searchQuery.value) return bookmarks.value;
            return bookmarks.value.filter(b =>
                b.channelName.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
                b.accountName.toLowerCase().includes(searchQuery.value.toLowerCase())
            );
        });

        const filteredFavorites = computed(() => {
            if (!searchQuery.value) return favorites.value;
            return favorites.value.filter(f => f.name.toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const currentChannelName = computed(() => currentChannel.value ? currentChannel.value.name : '');

        const isCurrentFavorite = computed(() => {
            if (!currentChannel.value) return false;
            return favorites.value.some(f => f.id === currentChannel.value.id && f.name === currentChannel.value.name);
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
            currentContext.value.accountId = accountId;
            try {
                const response = await fetch(window.location.origin + "/categories?accountId=" + accountId);
                categories.value = await response.json();
                viewState.value = 'categories';
                searchQuery.value = '';
            } catch (e) {
                console.error("Failed to load categories", e);
            }
        };

        const loadChannels = async (categoryId) => {
            currentContext.value.categoryId = categoryId;
            try {
                const response = await fetch(window.location.origin + "/channels?categoryId=" + categoryId + "&accountId=" + currentContext.value.accountId);
                channels.value = await response.json();
                viewState.value = 'channels';
                searchQuery.value = '';
            } catch (e) {
                console.error("Failed to load channels", e);
            }
        };

        const loadBookmarks = async () => {
            try {
                const response = await fetch(window.location.origin + "/bookmarks");
                bookmarks.value = await response.json();
            } catch (e) {
                console.error("Failed to load bookmarks", e);
            }
        };

        const loadFavorites = () => {
            const stored = localStorage.getItem('uiptv_favorites');
            if (stored) {
                favorites.value = JSON.parse(stored);
            }
        };

        const saveFavorites = () => {
            localStorage.setItem('uiptv_favorites', JSON.stringify(favorites.value));
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

        const goBackToAccounts = () => {
            viewState.value = 'accounts';
            searchQuery.value = '';
        };

        const goBackToCategories = () => {
            viewState.value = 'categories';
            searchQuery.value = '';
        };

        const playChannel = (channel) => {
            currentChannel.value = {
                id: channel.dbId,
                name: channel.name,
                logo: channel.logo,
                accountId: currentContext.value.accountId,
                categoryId: currentContext.value.categoryId,
                type: 'channel',
                drmType: channel.drmType,
                clearKeys: channel.clearKeys
            };
            startPlayback(window.location.origin + "/player?channelId=" + channel.dbId + "&categoryId=" + currentContext.value.categoryId + "&accountId=" + currentContext.value.accountId);
        };

        const playBookmark = (bookmark) => {
            currentChannel.value = {
                id: bookmark.dbId,
                name: bookmark.channelName,
                accountName: bookmark.accountName,
                type: 'bookmark'
            };
            startPlayback(window.location.origin + "/player?bookmarkId=" + bookmark.dbId);
        };

        const playFavorite = (fav) => {
            currentChannel.value = fav;
            if (fav.type === 'channel') {
                startPlayback(window.location.origin + "/player?channelId=" + fav.id + "&categoryId=" + fav.categoryId + "&accountId=" + fav.accountId);
            } else {
                startPlayback(window.location.origin + "/player?bookmarkId=" + fav.id);
            }
        };

        // --- NEW PLAYER LOGIC ---

        const startPlayback = async (url) => {
            await stopPlayback(); // Stop any current playback

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

        const stopPlayback = async () => {
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
            isPlaying.value = false;
            currentChannel.value = null;
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

            const index = favorites.value.findIndex(f => f.id === currentChannel.value.id && f.name === currentChannel.value.name);

            if (index === -1) {
                favorites.value.push(currentChannel.value);
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

            switchTab,
            loadCategories,
            loadChannels,
            goBackToAccounts,
            goBackToCategories,
            playChannel,
            playBookmark,
            playFavorite,
            stopPlayback,
            toggleFavorite,
            imageError,
            toggleTheme,
            resetApp,
            switchVideoTrack
        };
    }
}).mount('#app');
