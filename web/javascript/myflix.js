const { createApp, ref, computed, onMounted, nextTick } = Vue;

createApp({
    setup() {
        const searchQuery = ref('');
        const accounts = ref([]);
        const bookmarks = ref([]);
        const favorites = ref([]);

        const createBrowserState = () => ({
            viewState: ref('accounts'), // accounts, categories, channels, episodes, seriesDetail
            accountId: ref(null),
            accountType: ref(null),
            categoryId: ref(null),
            categories: ref([]),
            channels: ref([]),
            episodes: ref([]),
            seriesDetail: ref(null),
            navigationStack: ref([])
        });

        const browsers = {
            itv: createBrowserState(),
            vod: createBrowserState(),
            series: createBrowserState()
        };

        const currentChannel = ref(null);
        const isPlaying = ref(false);
        const showOverlay = ref(false);
        const showBookmarkModal = ref(false);

        const playerKey = ref(0);
        const isYoutube = ref(false);
        const youtubeSrc = ref('');
        const playerInstance = ref(null);
        const videoPlayerHome = ref(null);
        const videoPlayerSeries = ref(null);
        const videoTracks = ref([]);
        const isBusy = ref(false);
        const busyMessage = ref('Loading...');

        const theme = ref('system');
        const selectedSeriesSeason = ref('');

        const isSupportedAccount = (account) => !!account && (account.type === 'STALKER_PORTAL' || account.type === 'XTREME_API');

        const filteredSupportedAccounts = computed(() => {
            const q = searchQuery.value.toLowerCase();
            const list = accounts.value.filter(isSupportedAccount);
            if (!q) return list;
            return list.filter(a => (a.accountName || '').toLowerCase().includes(q));
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
            const q = searchQuery.value.toLowerCase();
            return favorites.value.filter(f => (f.name || '').toLowerCase().includes(q));
        });

        const filterBySearch = (arr, field) => {
            if (!searchQuery.value) return arr;
            const q = searchQuery.value.toLowerCase();
            return arr.filter(i => (i?.[field] || '').toLowerCase().includes(q));
        };

        const filteredModeCategories = (mode) => filterBySearch(browsers[mode].categories.value, 'title');
        const filteredModeChannels = (mode) => filterBySearch(browsers[mode].channels.value, 'name');
        const filteredModeEpisodes = (mode) => filterBySearch(browsers[mode].episodes.value, 'name');

        const resolveEpisodeSeason = (episode) => {
            if (!episode) return '';
            const rawSeason = String(episode.season || '').trim();
            if (rawSeason) {
                const numeric = rawSeason.replace(/[^0-9]/g, '');
                if (numeric) return String(parseInt(numeric, 10));
            }
            const name = String(episode.name || '');
            const sMatch = name.match(/(?:season|s)\s*([0-9]{1,2})/i);
            if (sMatch?.[1]) return String(parseInt(sMatch[1], 10));
            const xMatch = name.match(/([0-9]{1,2})\s*x\s*[0-9]{1,2}/i);
            if (xMatch?.[1]) return String(parseInt(xMatch[1], 10));
            return '';
        };

        const seriesSeasonTabs = computed(() => {
            const eps = browsers.series.episodes.value || [];
            const unique = new Set();
            for (const episode of eps) {
                const season = resolveEpisodeSeason(episode);
                if (season) unique.add(season);
            }
            return Array.from(unique)
                .sort((a, b) => Number(a) - Number(b))
                .map(s => ({ value: s, label: `Season ${s}` }));
        });

        const filteredSeriesDetailEpisodes = computed(() => {
            let episodes = filteredModeEpisodes('series');
            if (!selectedSeriesSeason.value) return episodes;
            return episodes.filter(ep => resolveEpisodeSeason(ep) === selectedSeriesSeason.value);
        });

        const setDefaultSeriesSeason = () => {
            const tabs = seriesSeasonTabs.value;
            selectedSeriesSeason.value = tabs.length > 0 ? tabs[0].value : '';
        };

        const selectSeriesSeason = (season) => {
            selectedSeriesSeason.value = season;
        };

        const normalizeTitle = (value) => (value || '')
            .toString()
            .toLowerCase()
            .replace(/[^a-z0-9 ]/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();

        const resolveEpisodeNumber = (episode) => {
            if (!episode) return '';
            const raw = String(episode.episodeNum || '').trim();
            if (raw) {
                const numeric = raw.replace(/[^0-9]/g, '');
                if (numeric) return String(parseInt(numeric, 10));
            }
            const name = String(episode.name || '');
            const sxe = name.match(/[sS]\s*([0-9]{1,2})\s*[eE]\s*([0-9]{1,3})/);
            if (sxe?.[2]) return String(parseInt(sxe[2], 10));
            const xStyle = name.match(/[0-9]{1,2}\s*x\s*([0-9]{1,3})/i);
            if (xStyle?.[1]) return String(parseInt(xStyle[1], 10));
            const ep = name.match(/(?:episode|ep)\s*([0-9]{1,3})/i);
            if (ep?.[1]) return String(parseInt(ep[1], 10));
            return '';
        };

        const cleanEpisodeTitle = (value) => {
            if (!value) return '';
            return value
                .replace(/^\s*season\s*\d+\s*[-:]\s*/i, '')
                .replace(/^\s*s\d+\s*[-:]\s*/i, '')
                .trim();
        };

        const getEpisodeDisplayTitle = (episode, idx = 0) => {
            const cleaned = cleanEpisodeTitle(String(episode?.name || ''));
            return cleaned || `Episode ${idx + 1}`;
        };

        const enrichEpisodesFromMeta = (episodes, detail) => {
            const metaList = Array.isArray(detail?.episodesMeta) ? detail.episodesMeta : [];
            if (!Array.isArray(episodes) || episodes.length === 0 || metaList.length === 0) {
                return episodes || [];
            }

            const bySeasonEpisode = new Map();
            const byTitle = new Map();
            for (const row of metaList) {
                const season = String(row?.season || '').replace(/[^0-9]/g, '');
                const episodeNum = String(row?.episodeNum || '').replace(/[^0-9]/g, '');
                if (season && episodeNum) {
                    bySeasonEpisode.set(`${parseInt(season, 10)}:${parseInt(episodeNum, 10)}`, row);
                }
                const titleKey = normalizeTitle(row?.title || '');
                if (titleKey) {
                    byTitle.set(titleKey, row);
                }
            }

            return episodes.map((episode) => {
                const season = resolveEpisodeSeason(episode);
                const epNum = resolveEpisodeNumber(episode);
                let meta = null;
                if (season && epNum) {
                    meta = bySeasonEpisode.get(`${season}:${epNum}`) || null;
                }
                if (!meta) {
                    meta = byTitle.get(normalizeTitle(cleanEpisodeTitle(episode?.name || ''))) || null;
                }
                if (!meta) {
                    return episode;
                }
                return {
                    ...episode,
                    logo: meta.logo || episode.logo,
                    description: episode.description || meta.plot || '',
                    releaseDate: episode.releaseDate || meta.releaseDate || '',
                    season: episode.season || meta.season || '',
                    episodeNum: episode.episodeNum || meta.episodeNum || ''
                };
            });
        };

        const isModeState = (mode, state) => browsers[mode].viewState.value === state;
        const getSeriesDetail = () => browsers.series.seriesDetail.value || null;
        const isSeriesDetailOpen = computed(() => isModeState('series', 'seriesDetail'));

        const currentChannelName = computed(() => currentChannel.value ? currentChannel.value.name : '');

        const favoriteKey = (item) => {
            if (!item) return '';
            const id = String(item.id ?? item.dbId ?? item.channelId ?? '');
            const name = String(item.name ?? item.channelName ?? '').trim().toLowerCase();
            const type = item.type ?? (item.accountId && item.categoryId ? 'channel' : 'bookmark');
            const mode = item.mode || 'itv';
            return `${type}::${mode}::${id}::${name}`;
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

        const loadAccounts = async () => {
            try {
                const response = await fetch(window.location.origin + '/accounts');
                accounts.value = await response.json();
            } catch (e) {
                console.error('Failed to load accounts', e);
            }
        };

        const resetModeState = (mode) => {
            const b = browsers[mode];
            b.accountId.value = null;
            b.accountType.value = null;
            b.categoryId.value = null;
            b.categories.value = [];
            b.channels.value = [];
            b.episodes.value = [];
            b.seriesDetail.value = null;
            b.navigationStack.value = [];
            b.viewState.value = 'accounts';
            if (mode === 'series') {
                selectedSeriesSeason.value = '';
            }
        };

        const loadModeCategories = async (mode, account) => {
            const b = browsers[mode];
            b.accountId.value = account.dbId;
            b.accountType.value = account.type;
            b.categoryId.value = null;
            b.channels.value = [];
            b.episodes.value = [];
            b.seriesDetail.value = null;
            b.navigationStack.value = [];

            try {
                isBusy.value = true;
                busyMessage.value = 'Loading categories...';
                const response = await fetch(
                    `${window.location.origin}/categories?accountId=${b.accountId.value}&mode=${mode}`
                );
                b.categories.value = await response.json();
                b.viewState.value = 'categories';
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load categories', e);
            } finally {
                isBusy.value = false;
            }
        };

        const loadModeChannels = async (mode, categoryId) => {
            const b = browsers[mode];
            b.categoryId.value = categoryId;
            b.episodes.value = [];
            b.seriesDetail.value = null;
            b.navigationStack.value = [];

            try {
                isBusy.value = true;
                busyMessage.value = 'Loading channels...';
                const response = await fetch(
                    `${window.location.origin}/channels?categoryId=${categoryId}&accountId=${b.accountId.value}&mode=${mode}`
                );
                b.channels.value = await response.json();
                b.viewState.value = 'channels';
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load channels', e);
            } finally {
                isBusy.value = false;
            }
        };

        const loadModeEpisodes = async (mode, seriesId) => {
            const b = browsers[mode];
            try {
                isBusy.value = true;
                busyMessage.value = 'Loading episodes...';
                const response = await fetch(
                    `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}`
                );
                b.episodes.value = await response.json();
                b.viewState.value = 'episodes';
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load episodes', e);
            } finally {
                isBusy.value = false;
            }
        };

        const loadModeSeriesChildren = async (mode, movieId) => {
            const b = browsers[mode];
            try {
                isBusy.value = true;
                busyMessage.value = 'Loading series...';
                const response = await fetch(
                    `${window.location.origin}/channels?categoryId=${b.categoryId.value}&accountId=${b.accountId.value}&mode=${mode}&movieId=${encodeURIComponent(movieId)}`
                );
                b.episodes.value = await response.json();
                b.viewState.value = 'episodes';
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load series children', e);
            } finally {
                isBusy.value = false;
            }
        };

        const openSeriesDetails = async (seriesItem) => {
            const b = browsers.series;
            const seriesId = seriesItem.channelId || seriesItem.id || seriesItem.dbId;

            b.navigationStack.value.push({
                state: b.viewState.value,
                channels: b.channels.value,
                episodes: b.episodes.value,
                detail: b.seriesDetail.value
            });

            const detail = {
                name: seriesItem.name || 'Series',
                cover: seriesItem.logo || '',
                plot: '',
                cast: '',
                director: '',
                genre: '',
                releaseDate: '',
                rating: '',
                tmdb: '',
                imdbUrl: '',
                episodesMeta: []
            };

            try {
                isBusy.value = true;
                busyMessage.value = 'Loading series details...';
                const response = await fetch(
                    `${window.location.origin}/seriesDetails?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}&seriesName=${encodeURIComponent(seriesItem.name || '')}`
                );
                const data = await response.json();
                if (data?.seasonInfo) {
                    Object.assign(detail, data.seasonInfo);
                    detail.name = data.seasonInfo.name || detail.name;
                    detail.cover = data.seasonInfo.cover || detail.cover;
                }
                if (Array.isArray(data?.episodesMeta)) {
                    detail.episodesMeta = data.episodesMeta;
                }
                if (Array.isArray(data?.episodes) && data.episodes.length > 0) {
                    b.episodes.value = data.episodes;
                } else {
                    b.episodes.value = [];
                }
            } catch (e) {
                console.error('Failed to load series details', e);
                b.episodes.value = [];
            } finally {
                isBusy.value = false;
            }

            if (b.accountType.value !== 'XTREME_API') {
                await loadModeSeriesChildren('series', seriesId);
            }

            b.episodes.value = enrichEpisodesFromMeta(b.episodes.value, detail);

            b.seriesDetail.value = detail;
            setDefaultSeriesSeason();
            b.viewState.value = 'seriesDetail';
            searchQuery.value = '';
            scrollToTop();
        };

        const goBackMode = (mode) => {
            const b = browsers[mode];
            if (b.navigationStack.value.length > 0) {
                const previous = b.navigationStack.value.pop();
                if (previous) {
                    b.viewState.value = previous.state || 'channels';
                    b.channels.value = previous.channels || b.channels.value;
                    b.episodes.value = previous.episodes || [];
                    b.seriesDetail.value = previous.detail || null;
                    searchQuery.value = '';
                    return;
                }
            }
            if (b.viewState.value === 'seriesDetail' || b.viewState.value === 'episodes') {
                b.viewState.value = 'channels';
                b.seriesDetail.value = null;
                if (mode === 'series') {
                    selectedSeriesSeason.value = '';
                }
            } else if (b.viewState.value === 'channels') {
                b.viewState.value = 'categories';
            } else if (b.viewState.value === 'categories') {
                b.viewState.value = 'accounts';
            }
            searchQuery.value = '';
        };

        const buildPlayerUrlForChannel = (channel, mode, browserState) => {
            const query = new URLSearchParams();
            query.set('accountId', browserState.accountId.value || '');
            query.set('categoryId', browserState.categoryId.value || '');
            query.set('mode', mode);

            const dbId = channel.dbId || '';
            const channelIdentifier = channel.channelId || channel.id || dbId;

            if (dbId) {
                query.set('channelId', dbId);
                if (mode === 'series') {
                    query.set('seriesId', channelIdentifier || '');
                }
            } else {
                query.set('channelId', channelIdentifier || '');
                query.set('name', channel.name || '');
                query.set('logo', channel.logo || '');
                query.set('cmd', channel.cmd || '');
                query.set('drmType', channel.drmType || '');
                query.set('drmLicenseUrl', channel.drmLicenseUrl || '');
                query.set('clearKeysJson', channel.clearKeysJson || '');
                query.set('inputstreamaddon', channel.inputstreamaddon || '');
                query.set('manifestType', channel.manifestType || '');
                if (mode === 'series') {
                    query.set('seriesId', channelIdentifier || '');
                }
            }

            return `${window.location.origin}/player?${query.toString()}`;
        };

        const playChannel = (channel, mode = 'itv') => {
            const b = browsers[mode];
            if (!(mode === 'series' && b.viewState.value === 'seriesDetail')) {
                scrollToTop();
            }
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
                accountId: b.accountId.value,
                categoryId: b.categoryId.value,
                type: 'channel',
                mode,
                clearKeys: channel.clearKeys
            };
            if (mode === 'series' && b.seriesDetail.value) {
                // Keep user on the dedicated series page while episodes are playing.
                b.viewState.value = 'seriesDetail';
            }
            startPlayback(buildPlayerUrlForChannel(channel, mode, b));
        };

        const handleModeSelection = async (mode, channel) => {
            if (mode === 'series') {
                const b = browsers.series;
                if (b.viewState.value === 'channels') {
                    await openSeriesDetails(channel);
                    return;
                }
                if (b.seriesDetail.value) {
                    b.viewState.value = 'seriesDetail';
                }
            }
            playChannel(channel, mode);
        };

        const loadBookmarks = async () => {
            try {
                const response = await fetch(`${window.location.origin}/bookmarks`);
                bookmarks.value = await response.json();
                enrichFavoritesFromBookmarks();
            } catch (e) {
                console.error('Failed to load bookmarks', e);
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

        const playBookmark = (bookmark) => {
            scrollToTop();
            currentChannel.value = {
                id: bookmark.dbId,
                name: bookmark.channelName,
                logo: bookmark.logo,
                accountName: bookmark.accountName,
                type: 'bookmark',
                mode: 'itv'
            };
            startPlayback(`${window.location.origin}/player?bookmarkId=${bookmark.dbId}`);
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
                    if (modeToUse === 'series') query.set('seriesId', fav.channelId || fav.id || '');
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
                    if (modeToUse === 'series') query.set('seriesId', fav.channelId || fav.id || '');
                }
                startPlayback(`${window.location.origin}/player?${query.toString()}`);
            } else {
                startPlayback(`${window.location.origin}/player?bookmarkId=${fav.id}`);
            }
        };

        const startPlayback = async (url) => {
            await stopPlayback(true);
            playerKey.value++;
            isPlaying.value = true;

            try {
                const response = await fetch(url);
                const channelData = await response.json();
                if (!channelData?.url && currentChannel.value?.cmd) {
                    const fallbackUrl = String(currentChannel.value.cmd).trim().replace(/^ffmpeg\s+/i, '');
                    await initPlayer({ url: fallbackUrl });
                } else {
                    await initPlayer(channelData);
                }
            } catch (e) {
                console.error('Failed to start playback', e);
                isPlaying.value = false;
            }
        };

        const stopPlayback = async (preserveUi = false) => {
            if (playerInstance.value) {
                try {
                    await playerInstance.value.destroy();
                } catch (e) {
                    console.warn('Error destroying Shaka player', e);
                }
                playerInstance.value = null;
            }
            clearVideoElement(videoPlayerHome.value);
            clearVideoElement(videoPlayerSeries.value);
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
                isPlaying.value = false;
                return;
            }

            if (uri.includes('youtube.com') || uri.includes('youtu.be')) {
                isYoutube.value = true;
                const videoId = uri.split('v=')[1] ? uri.split('v=')[1].split('&')[0] : uri.split('/').pop();
                youtubeSrc.value = `https://www.youtube.com/embed/${videoId}?autoplay=1`;
                return;
            }

            isYoutube.value = false;
            const video = await resolveActiveVideoElement();
            if (!video) return;

            const isApple = /iPhone|iPad|iPod|Macintosh/i.test(navigator.userAgent);
            const canNative = video.canPlayType('application/vnd.apple.mpegurl');
            const hasDRM = channel.drm != null;

            if (hasDRM) {
                await loadShaka(channel);
            } else if (isApple && canNative) {
                await loadNative(channel);
            } else if (canNative && uri.endsWith('.m3u8')) {
                await loadNative(channel);
            } else {
                await loadShaka(channel);
            }
        };

        const loadNative = async (channel) => {
            const video = await resolveActiveVideoElement();
            if (video) {
                video.src = channel.url;
                try {
                    await video.play();
                } catch (e) {
                    console.warn('Autoplay was prevented.', e);
                }
            }
        };

        const loadShaka = async (channel) => {
            const video = await resolveActiveVideoElement();
            if (!video) return;

            shaka.polyfill.installAll();
            if (!shaka.Player.isBrowserSupported()) return;

            const player = new shaka.Player(video);
            playerInstance.value = player;

            player.addEventListener('error', (event) => {
                console.error('Shaka Player Error:', event.detail);
            });

            if (channel.drm) {
                const drmConfig = {};
                if (channel.drm.licenseUrl) drmConfig.servers = { [channel.drm.type]: channel.drm.licenseUrl };
                if (channel.drm.clearKeys) drmConfig.clearKeys = channel.drm.clearKeys;
                player.configure({ drm: drmConfig });
            }

            try {
                await player.load(channel.url);
                videoTracks.value = player.getVariantTracks();
                autoSelectBestTrack(player);
            } catch (e) {
                console.error('Shaka: Error loading video:', e);
            }
        };

        const switchVideoTrack = (trackId) => {
            if (!playerInstance.value) return;
            const track = playerInstance.value.getVariantTracks().find(t => t.id === trackId);
            if (track) playerInstance.value.selectVariantTrack(track, true);
        };

        const toggleFavorite = () => {
            if (!currentChannel.value) return;
            const favoriteItem = {
                ...currentChannel.value,
                id: currentChannel.value.id ?? currentChannel.value.dbId,
                name: currentChannel.value.name || currentChannel.value.channelName || '',
                type: currentChannel.value.type || (currentChannel.value.accountId && currentChannel.value.categoryId ? 'channel' : 'bookmark'),
                mode: currentChannel.value.mode || 'itv'
            };

            const currentKey = favoriteKey(favoriteItem);
            const index = favorites.value.findIndex(f => favoriteKey(f) === currentKey);
            if (index === -1) favorites.value.push(favoriteItem);
            else favorites.value.splice(index, 1);
            saveFavorites();
        };

        const mediaImageError = (e) => {
            e.target.onerror = null;
            e.target.removeAttribute('src');
            e.target.style.display = 'none';
            const fallback =
                e.target.parentElement?.querySelector('.nf-media-fallback') ||
                e.target.parentElement?.querySelector('.nf-episode-thumb-fallback');
            if (fallback) fallback.classList.add('show');
        };

        const clearVideoElement = (video) => {
            if (!video) return;
            video.pause();
            video.src = '';
            video.removeAttribute('src');
            video.load();
        };

        const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

        const resolveActiveVideoElement = async () => {
            for (let i = 0; i < 6; i++) {
                await nextTick();
                const prefersSeries = isSeriesDetailOpen.value && currentChannel.value?.mode === 'series';
                const video = prefersSeries
                    ? (videoPlayerSeries.value || videoPlayerHome.value)
                    : (videoPlayerHome.value || videoPlayerSeries.value);
                if (video) return video;
                await sleep(25);
            }
            return null;
        };

        const canDecodeHevc = () => {
            if (!window.MediaSource || typeof window.MediaSource.isTypeSupported !== 'function') return false;
            return window.MediaSource.isTypeSupported('video/mp4; codecs="hvc1.1.6.L93.B0"')
                || window.MediaSource.isTypeSupported('video/mp4; codecs="hev1.1.6.L93.B0"')
                || window.MediaSource.isTypeSupported('video/mp4; codecs="hvc1"');
        };

        const canDecodeAvc = () => {
            if (!window.MediaSource || typeof window.MediaSource.isTypeSupported !== 'function') return true;
            return window.MediaSource.isTypeSupported('video/mp4; codecs="avc1.4d401f"')
                || window.MediaSource.isTypeSupported('video/mp4; codecs="avc1.640028"');
        };

        const autoSelectBestTrack = (player) => {
            if (!player) return;
            const tracks = (player.getVariantTracks() || []).filter(t => !t.audioOnly);
            if (!tracks.length) return;

            const hevcSupported = canDecodeHevc();
            const avcSupported = canDecodeAvc();

            let candidates = tracks;
            if (hevcSupported) {
                const hevcTracks = tracks.filter(t => /hev1|hvc1|hevc/i.test(String(t.codecs || '')));
                if (hevcTracks.length) candidates = hevcTracks;
            } else if (avcSupported) {
                const avcTracks = tracks.filter(t => /avc1|h264|avc/i.test(String(t.codecs || '')));
                if (avcTracks.length) candidates = avcTracks;
            }

            candidates.sort((a, b) => {
                const hDiff = (b.height || 0) - (a.height || 0);
                if (hDiff !== 0) return hDiff;
                return (b.bandwidth || 0) - (a.bandwidth || 0);
            });

            const chosen = candidates[0];
            if (chosen) {
                player.selectVariantTrack(chosen, true);
            }
        };

        const getImdbUrl = () => {
            const detail = getSeriesDetail();
            if (!detail) return '';
            if (detail.imdbUrl) return detail.imdbUrl;
            const imdbId = detail.tmdb || '';
            if (/^tt\d+$/i.test(imdbId)) {
                return `https://www.imdb.com/title/${imdbId}/`;
            }
            return '';
        };

        const scrollToTop = () => window.scrollTo({ top: 0, behavior: 'smooth' });

        const toggleTheme = () => {
            if (theme.value === 'system') theme.value = 'dark';
            else if (theme.value === 'dark') theme.value = 'light';
            else theme.value = 'system';
            localStorage.setItem('uiptv_theme', theme.value);
            applyTheme();
        };

        const applyTheme = () => {
            if (theme.value === 'system') document.documentElement.removeAttribute('data-theme');
            else document.documentElement.setAttribute('data-theme', theme.value);
        };

        const resetApp = () => {
            stopPlayback();
            resetModeState('itv');
            resetModeState('vod');
            resetModeState('series');
            selectedSeriesSeason.value = '';
            searchQuery.value = '';
        };

        onMounted(() => {
            loadAccounts();
            loadBookmarks();
            loadFavorites();

            const storedTheme = localStorage.getItem('uiptv_theme');
            if (storedTheme) theme.value = storedTheme;
            applyTheme();
        });

        return {
            searchQuery,
            filteredSupportedAccounts,
            filteredModeCategories,
            filteredModeChannels,
            filteredModeEpisodes,
            isModeState,
            getSeriesDetail,
            isSeriesDetailOpen,
            seriesSeasonTabs,
            selectedSeriesSeason,
            filteredSeriesDetailEpisodes,
            filteredBookmarks,
            filteredFavorites,
            getEpisodeDisplayTitle,
            currentChannel,
            currentChannelName,
            isCurrentFavorite,
            isPlaying,
            showOverlay,
            showBookmarkModal,
            playerKey,
            isYoutube,
            youtubeSrc,
            videoPlayerHome,
            videoPlayerSeries,
            videoTracks,
            isBusy,
            busyMessage,
            theme,
            themeIcon,

            loadModeCategories,
            loadModeChannels,
            goBackMode,
            handleModeSelection,
            playBookmark,
            playFavorite,
            stopPlayback,
            toggleFavorite,
            mediaImageError,
            selectSeriesSeason,
            getImdbUrl,
            toggleTheme,
            resetApp,
            switchVideoTrack
        };
    }
}).mount('#app');
