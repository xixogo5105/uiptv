const { createApp, ref, computed, onMounted, onUnmounted, nextTick } = Vue;

createApp({
    setup() {
        const CHANNEL_BATCH_SIZE = 80;
        const EPISODE_BATCH_SIZE = 60;
        const DETAIL_EPISODE_BATCH_SIZE = 30;

        const searchQuery = ref('');
        const accounts = ref([]);
        const selectedAccountId = ref('');
        const selectedAccountTypeFilter = ref('all');
        const bookmarks = ref([]);
        const bookmarkCategories = ref([]);
        const selectedBookmarkCategoryId = ref('');

        const createBrowserState = () => ({
            viewState: ref('categories'), // categories, channels, episodes, seriesDetail, vodDetail
            accountId: ref(null),
            accountType: ref(null),
            categoryId: ref(null),
            categories: ref([]),
            channels: ref([]),
            episodes: ref([]),
            detail: ref(null),
            navigationStack: ref([]),
            channelsNextPage: ref(0),
            channelsHasMore: ref(false),
            channelsApiOffset: ref(0),
            channelsLoading: ref(false)
        });

        const browsers = {
            itv: createBrowserState(),
            vod: createBrowserState(),
            series: createBrowserState()
        };

        const currentChannel = ref(null);
        const isPlaying = ref(false);
        const playerLoading = ref(false);
        const showOverlay = ref(false);
        const showBookmarkModal = ref(false);
        const repeatEnabled = ref(false);
        const lastPlaybackUrl = ref('');
        const repeatInFlight = ref(false);

        const playerKey = ref(0);
        const isYoutube = ref(false);
        const youtubeSrc = ref('');
        const playerInstance = ref(null);
        const videoPlayerModal = ref(null);
        const playerModalFrame = ref(null);
        const videoTracks = ref([]);
        const isBusy = ref(false);
        const busyMessage = ref('Loading...');
        const modeLoadingCount = ref({ itv: 0, vod: 0, series: 0, bookmarks: 0 });

        const theme = ref('system');
        const selectedSeriesSeason = ref('');
        const systemThemeQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
        let unbindSystemThemeListener = null;

        const isVodSeriesSupportedAccount = (account) => !!account && (account.type === 'STALKER_PORTAL' || account.type === 'XTREME_API');
        const isPinnedAccount = (account) => {
            const raw = account?.pinToTop;
            if (raw === true || raw === 1) return true;
            if (raw === false || raw === 0 || raw == null) return false;
            const s = String(raw).trim().toLowerCase();
            return s === 'true' || s === '1' || s === 'yes' || s === 'y';
        };
        const numericDbId = (account) => {
            const value = Number.parseInt(String(account?.dbId || ''), 10);
            return Number.isFinite(value) ? value : Number.MAX_SAFE_INTEGER;
        };

        const sortedAccounts = computed(() => {
            const list = Array.isArray(accounts.value) ? [...accounts.value] : [];
            list.sort((a, b) => {
                const aPinned = isPinnedAccount(a);
                const bPinned = isPinnedAccount(b);
                if (aPinned !== bPinned) return aPinned ? -1 : 1;
                const byId = numericDbId(a) - numericDbId(b);
                if (byId !== 0) return byId;
                return String(a?.accountName || '').localeCompare(String(b?.accountName || ''), undefined, { sensitivity: 'base' });
            });
            return list;
        });

        const matchesAccountTypeFilter = (account, filter) => {
            const type = String(account?.type || '').toUpperCase();
            if (filter === 'stalker') return type === 'STALKER_PORTAL';
            if (filter === 'xtreme') return type === 'XTREME_API';
            if (filter === 'm3u') return type === 'M3U8_LOCAL' || type === 'M3U8_URL';
            if (filter === 'rss') return type === 'RSS_FEED';
            return true;
        };

        const filteredAccounts = computed(() => {
            const q = searchQuery.value.toLowerCase();
            const list = sortedAccounts.value.filter(account => matchesAccountTypeFilter(account, selectedAccountTypeFilter.value));
            if (!q) return list;
            return list.filter(a => (a.accountName || '').toLowerCase().includes(q));
        });

        const selectedAccount = computed(() => {
            const selectedId = String(selectedAccountId.value || '');
            if (!selectedId) return null;
            return accounts.value.find(a => String(a?.dbId || '') === selectedId) || null;
        });
        const showVodSeriesSections = computed(() => isVodSeriesSupportedAccount(selectedAccount.value));

        const filteredBookmarks = computed(() => {
            const selectedCategoryId = String(selectedBookmarkCategoryId.value || '');
            const byCategory = selectedCategoryId
                ? bookmarks.value.filter(b => String(b?.categoryId || '') === selectedCategoryId)
                : bookmarks.value;
            if (!searchQuery.value) return byCategory;
            const q = searchQuery.value.toLowerCase();
            return byCategory.filter(b =>
                (b.channelName || '').toLowerCase().includes(q) ||
                (b.accountName || '').toLowerCase().includes(q)
            );
        });

        const bookmarkCategoryTabs = computed(() => {
            const tabs = [{ id: '', name: 'All' }];
            for (const category of (bookmarkCategories.value || [])) {
                const id = String(category?.id || '').trim();
                const name = String(category?.name || '').trim();
                if (!id || !name) continue;
                tabs.push({ id, name });
            }
            return tabs;
        });

        const filterBySearch = (arr, field) => {
            if (!searchQuery.value) return arr;
            const q = searchQuery.value.toLowerCase();
            return arr.filter(i => (i?.[field] || '').toLowerCase().includes(q));
        };

        const filteredModeCategories = (mode) => filterBySearch(browsers[mode].categories.value, 'title');
        const filteredModeChannels = (mode) => filterBySearch(browsers[mode].channels.value, 'name');
        const filteredModeEpisodes = (mode) => filterBySearch(browsers[mode].episodes.value, 'name');
        const visibleChannelsByMode = ref({ itv: CHANNEL_BATCH_SIZE, vod: CHANNEL_BATCH_SIZE, series: CHANNEL_BATCH_SIZE });
        const visibleEpisodesByMode = ref({ itv: EPISODE_BATCH_SIZE, vod: EPISODE_BATCH_SIZE, series: EPISODE_BATCH_SIZE });
        const visibleSeriesDetailEpisodesCount = ref(DETAIL_EPISODE_BATCH_SIZE);

        const visibleModeChannels = (mode) => {
            const items = filteredModeChannels(mode);
            const limit = visibleChannelsByMode.value[mode] || CHANNEL_BATCH_SIZE;
            return items.slice(0, limit);
        };

        const visibleModeEpisodes = (mode) => {
            const items = filteredModeEpisodes(mode);
            const limit = visibleEpisodesByMode.value[mode] || EPISODE_BATCH_SIZE;
            return items.slice(0, limit);
        };

        const hasMoreModeChannels = (mode) => {
            const localMore = filteredModeChannels(mode).length > (visibleChannelsByMode.value[mode] || CHANNEL_BATCH_SIZE);
            return localMore || !!browsers[mode]?.channelsHasMore?.value;
        };

        const hasMoreModeEpisodes = (mode) => {
            return filteredModeEpisodes(mode).length > (visibleEpisodesByMode.value[mode] || EPISODE_BATCH_SIZE);
        };

        const loadMoreModeChannels = async (mode) => {
            const visible = visibleChannelsByMode.value[mode] || CHANNEL_BATCH_SIZE;
            const loaded = filteredModeChannels(mode).length;
            if (visible < loaded) {
                visibleChannelsByMode.value[mode] = visible + CHANNEL_BATCH_SIZE;
                return;
            }
            if (browsers[mode]?.channelsHasMore?.value) {
                await fetchModeChannelPage(mode, 1);
                visibleChannelsByMode.value[mode] = visible + CHANNEL_BATCH_SIZE;
            }
        };

        const loadMoreModeEpisodes = (mode) => {
            visibleEpisodesByMode.value[mode] = (visibleEpisodesByMode.value[mode] || EPISODE_BATCH_SIZE) + EPISODE_BATCH_SIZE;
        };

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
        const visibleSeriesDetailEpisodes = computed(() => {
            return filteredSeriesDetailEpisodes.value.slice(0, visibleSeriesDetailEpisodesCount.value);
        });
        const hasMoreSeriesDetailEpisodes = computed(() => {
            return filteredSeriesDetailEpisodes.value.length > visibleSeriesDetailEpisodesCount.value;
        });
        const loadMoreSeriesDetailEpisodes = () => {
            visibleSeriesDetailEpisodesCount.value += DETAIL_EPISODE_BATCH_SIZE;
        };

        const setDefaultSeriesSeason = () => {
            const tabs = seriesSeasonTabs.value;
            selectedSeriesSeason.value = tabs.length > 0 ? tabs[0].value : '';
            visibleSeriesDetailEpisodesCount.value = DETAIL_EPISODE_BATCH_SIZE;
        };

        const selectSeriesSeason = (season) => {
            selectedSeriesSeason.value = season;
            visibleSeriesDetailEpisodesCount.value = DETAIL_EPISODE_BATCH_SIZE;
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
        const getSeriesDetail = () => browsers.series.detail.value || null;
        const getVodDetail = () => browsers.vod.detail.value || null;
        const isSeriesDetailOpen = computed(() => isModeState('series', 'seriesDetail'));
        const isVodDetailOpen = computed(() => isModeState('vod', 'vodDetail'));
        const isDetailPageOpen = computed(() => isSeriesDetailOpen.value || isVodDetailOpen.value);

        const currentChannelName = computed(() => currentChannel.value ? currentChannel.value.name : '');
        const isModeLoading = (mode) => Number(modeLoadingCount.value?.[mode] || 0) > 0;
        const startModeLoading = (mode) => {
            if (!modeLoadingCount.value[mode]) modeLoadingCount.value[mode] = 0;
            modeLoadingCount.value[mode] += 1;
        };
        const stopModeLoading = (mode) => {
            if (!modeLoadingCount.value[mode]) {
                modeLoadingCount.value[mode] = 0;
                return;
            }
            modeLoadingCount.value[mode] = Math.max(0, modeLoadingCount.value[mode] - 1);
        };

        const resolveAccountName = (accountId) => {
            const account = accounts.value.find(a => String(a.dbId) === String(accountId));
            return account?.accountName || '';
        };

        const resolveAccountId = (accountName) => {
            const account = accounts.value.find(a => String(a.accountName || '') === String(accountName || ''));
            return account?.dbId || '';
        };

        const findBookmarkForChannel = (channel) => {
            if (!channel) return null;
            if (channel.bookmarkId || channel.type === 'bookmark') {
                const byId = bookmarks.value.find(b => String(b.dbId) === String(channel.bookmarkId || channel.id));
                if (byId) return byId;
            }
            const accountName = channel.accountName || resolveAccountName(channel.accountId);
            const channelId = String(channel.channelId || channel.id || '');
            const channelName = String(channel.name || channel.channelName || '').trim().toLowerCase();
            return bookmarks.value.find(b =>
                String(b.accountName || '') === String(accountName || '') &&
                String(b.channelId || '') === channelId &&
                String(b.channelName || '').trim().toLowerCase() === channelName
            ) || null;
        };

        const isCurrentFavorite = computed(() => {
            if (!currentChannel.value) return false;
            return !!findBookmarkForChannel(currentChannel.value);
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

        const channelIdentityKey = (channel) => {
            if (!channel) return '';
            return [
                String(channel.channelId || ''),
                String(channel.cmd || ''),
                String(channel.name || '').trim().toLowerCase()
            ].join('|');
        };

        const appendUniqueChannels = (target, incoming) => {
            const list = Array.isArray(target) ? target : [];
            const items = Array.isArray(incoming) ? incoming : [];
            if (!items.length) return list;
            const seen = new Set(list.map(channelIdentityKey));
            for (const item of items) {
                const key = channelIdentityKey(item);
                if (!seen.has(key)) {
                    list.push(item);
                    seen.add(key);
                }
            }
            return list;
        };

        const fetchModeChannelPage = async (mode, prefetchPages = 1) => {
            const b = browsers[mode];
            if (b.channelsLoading.value) return;
            if (!b.categoryId.value || !b.accountId.value) return;
            if ((b.channelsNextPage.value || 0) > 0 && !b.channelsHasMore.value) return;

            b.channelsLoading.value = true;
            try {
                const query = new URLSearchParams();
                query.set('categoryId', b.categoryId.value);
                query.set('accountId', b.accountId.value);
                query.set('mode', mode);
                const response = await fetch(`${window.location.origin}/channels?${query.toString()}`);
                const data = await response.json();
                const incoming = Array.isArray(data) ? data : [];
                b.channels.value = appendUniqueChannels([...(b.channels.value || [])], incoming);
                b.channelsNextPage.value = 1;
                b.channelsHasMore.value = false;
                b.channelsApiOffset.value = 0;
            } catch (e) {
                console.error('Failed to load channels', e);
            } finally {
                b.channelsLoading.value = false;
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
            b.detail.value = null;
            b.navigationStack.value = [];
            b.viewState.value = 'categories';
            b.channelsNextPage.value = 0;
            b.channelsHasMore.value = false;
            b.channelsApiOffset.value = 0;
            b.channelsLoading.value = false;
            visibleChannelsByMode.value[mode] = CHANNEL_BATCH_SIZE;
            visibleEpisodesByMode.value[mode] = EPISODE_BATCH_SIZE;
            if (mode === 'series') {
                selectedSeriesSeason.value = '';
                visibleSeriesDetailEpisodesCount.value = DETAIL_EPISODE_BATCH_SIZE;
            }
        };

        const selectAccount = async (account) => {
            if (!account || !account.dbId) return;
            selectedAccountId.value = String(account.dbId);
            searchQuery.value = '';

            resetModeState('itv');
            resetModeState('vod');
            resetModeState('series');

            const jobs = [loadModeCategories('itv', account)];
            if (isVodSeriesSupportedAccount(account)) {
                jobs.push(loadModeCategories('vod', account));
                jobs.push(loadModeCategories('series', account));
            }
            await Promise.all(jobs);
        };

        const selectAccountTypeFilter = (filter) => {
            selectedAccountTypeFilter.value = String(filter || 'all');
        };

        const loadModeCategories = async (mode, account) => {
            const b = browsers[mode];
            b.accountId.value = account.dbId;
            b.accountType.value = account.type;
            b.categoryId.value = null;
            b.channels.value = [];
            b.episodes.value = [];
            b.detail.value = null;
            b.navigationStack.value = [];

            try {
                startModeLoading(mode);
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
                stopModeLoading(mode);
                isBusy.value = false;
            }
        };

        const loadModeChannels = async (mode, categoryId) => {
            const b = browsers[mode];
            b.categoryId.value = categoryId;
            b.episodes.value = [];
            b.detail.value = null;
            b.navigationStack.value = [];
            b.channelsNextPage.value = 0;
            b.channelsHasMore.value = false;
            b.channelsApiOffset.value = 0;
            b.channelsLoading.value = false;

            try {
                startModeLoading(mode);
                isBusy.value = true;
                busyMessage.value = 'Loading channels...';
                b.channels.value = [];
                await fetchModeChannelPage(mode, 3);
                visibleChannelsByMode.value[mode] = CHANNEL_BATCH_SIZE;
                b.viewState.value = 'channels';
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load channels', e);
            } finally {
                stopModeLoading(mode);
                isBusy.value = false;
            }
        };

        const loadModeEpisodes = async (mode, seriesId) => {
            const b = browsers[mode];
            try {
                startModeLoading(mode);
                isBusy.value = true;
                busyMessage.value = 'Loading episodes...';
                const response = await fetch(
                    `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}`
                );
                b.episodes.value = await response.json();
                visibleEpisodesByMode.value[mode] = EPISODE_BATCH_SIZE;
                b.viewState.value = 'episodes';
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load episodes', e);
            } finally {
                stopModeLoading(mode);
                isBusy.value = false;
            }
        };

        const loadModeSeriesChildren = async (mode, movieId) => {
            const b = browsers[mode];
            try {
                startModeLoading(mode);
                isBusy.value = true;
                busyMessage.value = 'Loading series...';
                const response = await fetch(
                    `${window.location.origin}/channels?categoryId=${b.categoryId.value}&accountId=${b.accountId.value}&mode=${mode}&movieId=${encodeURIComponent(movieId)}`
                );
                b.episodes.value = await response.json();
                visibleEpisodesByMode.value[mode] = EPISODE_BATCH_SIZE;
                b.viewState.value = 'episodes';
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load series children', e);
            } finally {
                stopModeLoading(mode);
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
                detail: b.detail.value
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

            b.detail.value = detail;
            b.episodes.value = [];
            b.viewState.value = 'seriesDetail';
            searchQuery.value = '';
            startModeLoading('series');

            try {
                if (b.accountType.value === 'XTREME_API') {
                    const response = await fetch(
                        `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}`
                    );
                    b.episodes.value = await response.json();
                } else {
                    const response = await fetch(
                        `${window.location.origin}/channels?categoryId=${b.categoryId.value}&accountId=${b.accountId.value}&mode=series&movieId=${encodeURIComponent(seriesId)}`
                    );
                    b.episodes.value = await response.json();
                }
            } catch (e) {
                console.error('Failed to load series episodes', e);
                b.episodes.value = [];
            } finally {
                stopModeLoading('series');
            }

            // Fetch heavy metadata in background so episodes become visible ASAP.
            (async () => {
                startModeLoading('series');
                try {
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
                    if (Array.isArray(data?.episodes) && data.episodes.length > 0 && (!b.episodes.value || b.episodes.value.length === 0)) {
                        b.episodes.value = data.episodes;
                    }
                    b.episodes.value = enrichEpisodesFromMeta(b.episodes.value, detail);
                    b.detail.value = { ...detail };
                    setDefaultSeriesSeason();
                } catch (e) {
                    console.error('Failed to load series details', e);
                } finally {
                    stopModeLoading('series');
                }
            })();

            setDefaultSeriesSeason();
        };

        const openVodDetails = async (vodItem) => {
            const b = browsers.vod;
            const channelIdentifier = vodItem.channelId || vodItem.id || vodItem.dbId;

            b.navigationStack.value.push({
                state: b.viewState.value,
                channels: b.channels.value,
                episodes: b.episodes.value,
                detail: b.detail.value
            });

            const detail = {
                name: vodItem.name || 'VOD',
                cover: vodItem.logo || '',
                plot: vodItem.description || '',
                cast: '',
                director: '',
                genre: '',
                releaseDate: vodItem.releaseDate || '',
                rating: vodItem.rating || '',
                tmdb: '',
                imdbUrl: '',
                duration: vodItem.duration || '',
                playItem: vodItem
            };
            const mergeDetailIfBlank = (target, source, key) => {
                const current = String(target?.[key] || '').trim();
                const incoming = String(source?.[key] || '').trim();
                if (!current && incoming) {
                    target[key] = incoming;
                }
            };

            try {
                isBusy.value = true;
                busyMessage.value = 'Loading VOD details...';
                const response = await fetch(
                    `${window.location.origin}/vodDetails?accountId=${b.accountId.value}&categoryId=${encodeURIComponent(b.categoryId.value || '')}&channelId=${encodeURIComponent(channelIdentifier || '')}&vodName=${encodeURIComponent(vodItem.name || '')}`
                );
                const data = await response.json();
                if (data?.vodInfo) {
                    const serverDetail = data.vodInfo;
                    mergeDetailIfBlank(detail, serverDetail, 'name');
                    mergeDetailIfBlank(detail, serverDetail, 'cover');
                    mergeDetailIfBlank(detail, serverDetail, 'plot');
                    mergeDetailIfBlank(detail, serverDetail, 'cast');
                    mergeDetailIfBlank(detail, serverDetail, 'director');
                    mergeDetailIfBlank(detail, serverDetail, 'genre');
                    mergeDetailIfBlank(detail, serverDetail, 'releaseDate');
                    mergeDetailIfBlank(detail, serverDetail, 'rating');
                    mergeDetailIfBlank(detail, serverDetail, 'tmdb');
                    mergeDetailIfBlank(detail, serverDetail, 'imdbUrl');
                    mergeDetailIfBlank(detail, serverDetail, 'duration');
                }
            } catch (e) {
                console.error('Failed to load VOD details', e);
            } finally {
                isBusy.value = false;
            }

            b.detail.value = detail;
            b.viewState.value = 'vodDetail';
            searchQuery.value = '';
        };

        const goBackMode = (mode) => {
            const b = browsers[mode];
            if (b.navigationStack.value.length > 0) {
                const previous = b.navigationStack.value.pop();
                if (previous) {
                    b.viewState.value = previous.state || 'channels';
                    b.channels.value = previous.channels || b.channels.value;
                    b.episodes.value = previous.episodes || [];
                    b.detail.value = previous.detail || null;
                    visibleChannelsByMode.value[mode] = CHANNEL_BATCH_SIZE;
                    visibleEpisodesByMode.value[mode] = EPISODE_BATCH_SIZE;
                    searchQuery.value = '';
                    return;
                }
            }
            if (b.viewState.value === 'seriesDetail' || b.viewState.value === 'vodDetail' || b.viewState.value === 'episodes') {
                b.viewState.value = 'channels';
                b.detail.value = null;
                if (mode === 'series') {
                    selectedSeriesSeason.value = '';
                }
            } else if (b.viewState.value === 'channels') {
                b.viewState.value = 'categories';
            }
            searchQuery.value = '';
        };

        const appendPlaybackCompatParams = (query, mode) => {
            if (!query) return;
            if (mode !== 'itv' && mode !== 'vod' && mode !== 'series') return;
            query.set('hvec', canDecodeHevc() ? '1' : '0');
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
                query.set('cmd_1', channel.cmd_1 || '');
                query.set('cmd_2', channel.cmd_2 || '');
                query.set('cmd_3', channel.cmd_3 || '');
                query.set('drmType', channel.drmType || '');
                query.set('drmLicenseUrl', channel.drmLicenseUrl || '');
                query.set('clearKeysJson', channel.clearKeysJson || '');
                query.set('inputstreamaddon', channel.inputstreamaddon || '');
                query.set('manifestType', channel.manifestType || '');
                if (mode === 'series') {
                    query.set('seriesId', channelIdentifier || '');
                }
            }

            appendPlaybackCompatParams(query, mode);
            return `${window.location.origin}/player?${query.toString()}`;
        };

        const playChannel = (channel, mode = 'itv') => {
            const b = browsers[mode];
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
                accountName: resolveAccountName(b.accountId.value),
                categoryId: b.categoryId.value,
                type: 'channel',
                mode,
                clearKeys: channel.clearKeys,
                playRequestUrl: ''
            };
            const playbackUrl = buildPlayerUrlForChannel(channel, mode, b);
            currentChannel.value.playRequestUrl = playbackUrl;
            if (mode === 'series' && b.detail.value) {
                // Keep user on the dedicated series page while episodes are playing.
                b.viewState.value = 'seriesDetail';
            }
            if (mode === 'vod' && b.detail.value) {
                b.viewState.value = 'vodDetail';
            }
            startPlayback(playbackUrl);
        };

        const handleModeSelection = async (mode, channel) => {
            if (mode === 'series') {
                const b = browsers.series;
                if (b.viewState.value === 'channels') {
                    await openSeriesDetails(channel);
                    return;
                }
                if (b.detail.value) {
                    b.viewState.value = 'seriesDetail';
                }
            }
            if (mode === 'vod') {
                const b = browsers.vod;
                if (b.viewState.value === 'channels') {
                    await openVodDetails(channel);
                    return;
                }
                if (b.detail.value) {
                    b.viewState.value = 'vodDetail';
                }
            }
            playChannel(channel, mode);
        };

        const playVodFromDetail = () => {
            const b = browsers.vod;
            const detail = b.detail.value;
            if (!detail?.playItem) return;
            playChannel(detail.playItem, 'vod');
        };

        const loadBookmarks = async () => {
            try {
                startModeLoading('bookmarks');
                const response = await fetch(`${window.location.origin}/bookmarks`);
                bookmarks.value = await response.json();
                ensureSelectedBookmarkCategory();
            } catch (e) {
                console.error('Failed to load bookmarks', e);
            } finally {
                stopModeLoading('bookmarks');
            }
        };

        const loadBookmarkCategories = async () => {
            try {
                const response = await fetch(`${window.location.origin}/bookmarks?view=categories`);
                bookmarkCategories.value = await response.json();
                ensureSelectedBookmarkCategory();
            } catch (e) {
                console.error('Failed to load bookmark categories', e);
            }
        };

        const ensureSelectedBookmarkCategory = () => {
            const selectedId = String(selectedBookmarkCategoryId.value || '');
            const exists = bookmarkCategoryTabs.value.some(tab => String(tab.id || '') === selectedId);
            if (!exists) {
                selectedBookmarkCategoryId.value = '';
            }
        };

        const selectBookmarkCategory = (categoryId) => {
            selectedBookmarkCategoryId.value = String(categoryId || '');
        };

        const playBookmark = (bookmark) => {
            const bookmarkMode = String(bookmark.accountAction || 'itv').toLowerCase();
            const query = new URLSearchParams();
            query.set('bookmarkId', bookmark.dbId);
            query.set('mode', bookmarkMode);
            appendPlaybackCompatParams(query, bookmarkMode);
            const playbackUrl = `${window.location.origin}/player?${query.toString()}`;
            currentChannel.value = {
                id: bookmark.dbId,
                name: bookmark.channelName,
                logo: bookmark.logo,
                accountName: bookmark.accountName,
                accountId: resolveAccountId(bookmark.accountName),
                categoryId: bookmark.categoryId || '',
                channelId: bookmark.channelId || '',
                cmd: bookmark.cmd || '',
                bookmarkId: bookmark.dbId,
                type: 'bookmark',
                mode: bookmarkMode,
                playRequestUrl: playbackUrl
            };
            startPlayback(playbackUrl);
        };

        const startPlayback = async (url) => {
            await stopPlayback(true);
            playerKey.value++;
            isPlaying.value = true;
            isBusy.value = false;
            playerLoading.value = true;
            lastPlaybackUrl.value = url || '';
            repeatInFlight.value = false;

            try {
                const response = await fetch(url);
                const channelData = await response.json();
                if (!channelData?.url && currentChannel.value?.cmd) {
                    const fallbackUrl = normalizeWebPlaybackUrl(String(currentChannel.value.cmd).trim().replace(/^ffmpeg\s+/i, ''));
                    await initPlayer({ url: fallbackUrl });
                } else {
                    await initPlayer({ ...(channelData || {}), url: normalizeWebPlaybackUrl(channelData?.url) });
                }
            } catch (e) {
                console.error('Failed to start playback', e);
                isPlaying.value = false;
            } finally {
                playerLoading.value = false;
            }
        };

        const stopPlayback = async (preserveUi = false) => {
            repeatInFlight.value = false;
            if (playerInstance.value) {
                try {
                    await playerInstance.value.destroy();
                } catch (e) {
                    console.warn('Error destroying Shaka player', e);
                }
                playerInstance.value = null;
            }
            clearVideoElement(videoPlayerModal.value);
            if (!preserveUi) {
                isPlaying.value = false;
                currentChannel.value = null;
                lastPlaybackUrl.value = '';
            }
            playerLoading.value = false;
            isYoutube.value = false;
            youtubeSrc.value = '';
            videoTracks.value = [];
        };

        const closePlayerPopup = () => {
            stopPlayback();
        };

        const toggleRepeat = () => {
            repeatEnabled.value = !repeatEnabled.value;
        };

        const reloadPlayback = async () => {
            if (!lastPlaybackUrl.value) return;
            await startPlayback(lastPlaybackUrl.value);
        };

        const requestFullscreenPlayer = async () => {
            const node = playerModalFrame.value;
            if (!node || !document.fullscreenEnabled) return;
            try {
                if (document.fullscreenElement) await document.exitFullscreen();
                else await node.requestFullscreen();
            } catch (e) {
                console.warn('Fullscreen request failed', e);
            }
        };

        const togglePictureInPicture = async () => {
            if (isYoutube.value) return;
            const video = videoPlayerModal.value;
            if (!video || typeof document.pictureInPictureEnabled === 'undefined') return;
            try {
                if (document.pictureInPictureElement) {
                    await document.exitPictureInPicture();
                } else if (document.pictureInPictureEnabled) {
                    await video.requestPictureInPicture();
                }
            } catch (e) {
                console.warn('Picture-in-Picture failed', e);
            }
        };

        const tryAutoRepeat = async () => {
            if (!repeatEnabled.value || !lastPlaybackUrl.value || repeatInFlight.value) return;
            repeatInFlight.value = true;
            await sleep(700);
            try {
                await startPlayback(lastPlaybackUrl.value);
            } finally {
                repeatInFlight.value = false;
            }
        };

        const bindPlaybackEvents = (video) => {
            if (!video || video.dataset.uiptvBound === '1') return;
            video.addEventListener('ended', () => {
                tryAutoRepeat();
            });
            video.addEventListener('error', () => {
                tryAutoRepeat();
            });
            video.dataset.uiptvBound = '1';
        };

        const initPlayer = async (channel) => {
            const uri = normalizeWebPlaybackUrl(channel.url);
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
            bindPlaybackEvents(video);

            const isApple = /iPhone|iPad|iPod|Macintosh/i.test(navigator.userAgent);
            const canNative = video.canPlayType('application/vnd.apple.mpegurl');
            const hasDRM = channel.drm != null;
            const normalizedUri = String(uri || '').toLowerCase();
            const manifestType = String(channel?.drm?.manifestType || channel?.manifestType || '').toLowerCase();
            const isHls = manifestType === 'hls' || normalizedUri.includes('.m3u8');
            const isDash = manifestType === 'mpd' || normalizedUri.includes('.mpd');
            const isLikelyProgressive =
                /\.(mkv|mp4|mov|avi|ts|m2ts|webm)(\?|$)/i.test(uri) ||
                normalizedUri.includes('/live/play/') ||
                normalizedUri.includes('/play/movie.php');

            if (hasDRM) {
                await loadShaka({ ...channel, url: uri });
            } else if (isLikelyProgressive) {
                await loadNative({ ...channel, url: uri });
            } else if (isDash) {
                await loadShaka({ ...channel, url: uri });
            } else if (isHls) {
                if (isApple && canNative) await loadNative({ ...channel, url: uri });
                else await loadShaka({ ...channel, url: uri });
            } else {
                // Unknown URLs (often redirected Stalker series links) work better with native first.
                await loadNative({ ...channel, url: uri });
            }
        };

        const normalizeWebPlaybackUrl = (rawUrl) => {
            const value = String(rawUrl || '').trim();
            if (!value) return value;
            const lower = value.toLowerCase();
            if (lower.startsWith('https://') && (lower.includes('/live/play/') || lower.includes('/play/movie.php'))) {
                return `http://${value.slice('https://'.length)}`;
            }
            return value;
        };

        const loadNative = async (channel) => {
            const video = await resolveActiveVideoElement();
            if (video) {
                let sourceUrl = normalizeWebPlaybackUrl(channel.url);
                video.src = sourceUrl;
                try {
                    await video.play();
                } catch (e) {
                    const lower = String(sourceUrl || '').toLowerCase();
                    const canRetryAsHttp = lower.startsWith('https://') &&
                        (lower.includes('/live/play/') || lower.includes('/play/movie.php'));
                    if (canRetryAsHttp) {
                        try {
                            sourceUrl = `http://${sourceUrl.slice('https://'.length)}`;
                            video.src = sourceUrl;
                            await video.play();
                            return;
                        } catch (retryErr) {
                            console.warn('Native playback retry failed.', retryErr);
                        }
                    }
                    console.warn('Autoplay was prevented.', e);
                }
            }
        };

        const loadShaka = async (channel) => {
            const video = await resolveActiveVideoElement();
            if (!video) return;
            bindPlaybackEvents(video);

            shaka.polyfill.installAll();
            if (!shaka.Player.isBrowserSupported()) return;

            const player = new shaka.Player();
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
                await player.attach(video);
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

        const toggleFavorite = async () => {
            if (!currentChannel.value) return;
            const existing = findBookmarkForChannel(currentChannel.value);
            try {
                if (existing?.dbId) {
                    await fetch(`${window.location.origin}/bookmarks?bookmarkId=${encodeURIComponent(existing.dbId)}`, {
                        method: 'DELETE'
                    });
                } else {
                    await fetch(`${window.location.origin}/bookmarks`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            accountId: currentChannel.value.accountId || '',
                            categoryId: currentChannel.value.categoryId || '',
                            mode: currentChannel.value.mode || 'itv',
                            channelId: currentChannel.value.channelId || currentChannel.value.id || '',
                            name: currentChannel.value.name || currentChannel.value.channelName || '',
                            logo: currentChannel.value.logo || '',
                            cmd: currentChannel.value.cmd || '',
                            drmType: currentChannel.value.drmType || '',
                            drmLicenseUrl: currentChannel.value.drmLicenseUrl || '',
                            clearKeysJson: currentChannel.value.clearKeysJson || '',
                            inputstreamaddon: currentChannel.value.inputstreamaddon || '',
                            manifestType: currentChannel.value.manifestType || ''
                        })
                    });
                }
                await loadBookmarks();
            } catch (e) {
                console.error('Failed to update bookmark', e);
            }
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

        const ensurePlaybackNotPaused = async () => {
            if (!isPlaying.value || isYoutube.value) return;
            const video = videoPlayerModal.value;
            if (!video) return;
            if (video.paused && !video.ended) {
                try {
                    await video.play();
                } catch (e) {
                    // Ignore autoplay/focus-related resume issues.
                }
            }
        };

        const onPlayerControlClick = async (event, action, ...args) => {
            if (event) {
                event.preventDefault();
                event.stopPropagation();
            }
            if (typeof action === 'function') {
                await action(...args);
            }
            await ensurePlaybackNotPaused();
        };

        const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

        const resolveActiveVideoElement = async () => {
            for (let i = 0; i < 6; i++) {
                await nextTick();
                const video = videoPlayerModal.value;
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

        const getImdbUrl = (mode = 'series') => {
            const detail = mode === 'vod' ? getVodDetail() : getSeriesDetail();
            if (!detail) return '';
            if (detail.imdbUrl) return detail.imdbUrl;
            const imdbId = detail.tmdb || '';
            if (/^tt\d+$/i.test(imdbId)) {
                return `https://www.imdb.com/title/${imdbId}/`;
            }
            return '';
        };

        const toggleTheme = () => {
            if (theme.value === 'system') theme.value = 'dark';
            else if (theme.value === 'dark') theme.value = 'light';
            else theme.value = 'system';
            localStorage.setItem('uiptv_theme', theme.value);
            applyTheme();
        };

        const getResolvedTheme = () => {
            if (theme.value === 'system') {
                return systemThemeQuery?.matches ? 'dark' : 'light';
            }
            return theme.value;
        };

        const applyTheme = () => {
            document.documentElement.setAttribute('data-theme', getResolvedTheme());
        };

        const clearWebCacheAndReload = async () => {
            try {
                await stopPlayback(true);
            } catch (_) {
                // Ignore playback cleanup errors.
            }

            try {
                if ('serviceWorker' in navigator) {
                    const registrations = await navigator.serviceWorker.getRegistrations();
                    await Promise.all(registrations.map(registration => registration.unregister()));
                }
            } catch (_) {
                // Ignore service worker cleanup errors.
            }

            try {
                if ('caches' in window) {
                    const keys = await caches.keys();
                    await Promise.all(keys.map(key => caches.delete(key)));
                }
            } catch (_) {
                // Ignore Cache Storage cleanup errors.
            }

            try {
                localStorage.clear();
            } catch (_) {}
            try {
                sessionStorage.clear();
            } catch (_) {}

            try {
                if (window.indexedDB && typeof indexedDB.databases === 'function') {
                    const dbs = await indexedDB.databases();
                    await Promise.all((dbs || []).map(db => new Promise(resolve => {
                        if (!db?.name) {
                            resolve();
                            return;
                        }
                        const request = indexedDB.deleteDatabase(db.name);
                        request.onsuccess = () => resolve();
                        request.onerror = () => resolve();
                        request.onblocked = () => resolve();
                    })));
                }
            } catch (_) {
                // Ignore IndexedDB cleanup errors.
            }

            const target = `${window.location.origin}${window.location.pathname}?cacheReset=${Date.now()}`;
            window.location.replace(target);
        };

        const resetApp = () => {
            stopPlayback();
            selectedAccountId.value = '';
            resetModeState('itv');
            resetModeState('vod');
            resetModeState('series');
            selectedSeriesSeason.value = '';
            searchQuery.value = '';
            selectedBookmarkCategoryId.value = '';
        };

        onMounted(() => {
            loadAccounts();
            loadBookmarkCategories();
            loadBookmarks();

            const storedTheme = localStorage.getItem('uiptv_theme');
            if (storedTheme) theme.value = storedTheme;
            applyTheme();

            if (systemThemeQuery) {
                const onSystemThemeChange = () => {
                    if (theme.value === 'system') {
                        applyTheme();
                    }
                };
                if (typeof systemThemeQuery.addEventListener === 'function') {
                    systemThemeQuery.addEventListener('change', onSystemThemeChange);
                    unbindSystemThemeListener = () => systemThemeQuery.removeEventListener('change', onSystemThemeChange);
                } else if (typeof systemThemeQuery.addListener === 'function') {
                    systemThemeQuery.addListener(onSystemThemeChange);
                    unbindSystemThemeListener = () => systemThemeQuery.removeListener(onSystemThemeChange);
                }
            }
        });

        onUnmounted(() => {
            if (typeof unbindSystemThemeListener === 'function') {
                unbindSystemThemeListener();
                unbindSystemThemeListener = null;
            }
        });

        return {
            searchQuery,
            filteredAccounts,
            selectedAccountTypeFilter,
            selectedAccountId,
            selectedAccount,
            selectAccountTypeFilter,
            showVodSeriesSections,
            filteredModeCategories,
            filteredModeChannels,
            filteredModeEpisodes,
            visibleModeChannels,
            visibleModeEpisodes,
            hasMoreModeChannels,
            hasMoreModeEpisodes,
            loadMoreModeChannels,
            loadMoreModeEpisodes,
            isModeState,
            getSeriesDetail,
            getVodDetail,
            isSeriesDetailOpen,
            isVodDetailOpen,
            isDetailPageOpen,
            seriesSeasonTabs,
            selectedSeriesSeason,
            filteredSeriesDetailEpisodes,
            visibleSeriesDetailEpisodes,
            hasMoreSeriesDetailEpisodes,
            filteredBookmarks,
            bookmarkCategoryTabs,
            selectedBookmarkCategoryId,
            getEpisodeDisplayTitle,
            currentChannel,
            currentChannelName,
            isCurrentFavorite,
            isPlaying,
            playerLoading,
            showOverlay,
            showBookmarkModal,
            playerKey,
            isYoutube,
            youtubeSrc,
            videoPlayerModal,
            playerModalFrame,
            videoTracks,
            isBusy,
            busyMessage,
            isModeLoading,
            theme,
            themeIcon,
            repeatEnabled,

            selectAccount,
            loadModeCategories,
            loadModeChannels,
            goBackMode,
            handleModeSelection,
            playVodFromDetail,
            playBookmark,
            selectBookmarkCategory,
            stopPlayback,
            closePlayerPopup,
            reloadPlayback,
            toggleRepeat,
            togglePictureInPicture,
            requestFullscreenPlayer,
            onPlayerControlClick,
            toggleFavorite,
            mediaImageError,
            selectSeriesSeason,
            loadMoreSeriesDetailEpisodes,
            getImdbUrl,
            toggleTheme,
            clearWebCacheAndReload,
            resetApp,
            switchVideoTrack
        };
    }
}).mount('#app');
