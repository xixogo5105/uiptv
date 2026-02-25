const { createApp, ref, computed, onMounted, nextTick } = Vue;

createApp({
    setup() {
        const activeTab = ref('bookmarks');
        const viewState = ref('accounts'); // accounts, categories, channels, episodes
        const searchQuery = ref('');
        const contentMode = ref('itv'); // itv, vod, series

        const accounts = ref([]);
        const categories = ref([]);
        const channels = ref([]);
        const episodes = ref([]);
        const bookmarks = ref([]);
        const bookmarkCategories = ref([]);
        const selectedBookmarkCategoryId = ref('');
        const selectedAccountId = ref(null);

        const currentContext = ref({ accountId: null, categoryId: null, accountType: null });
        const currentChannel = ref(null);
        const isPlaying = ref(false);
        const showOverlay = ref(false);
        const showBookmarkModal = ref(false);
        const listLoading = ref(false);
        const listLoadingMessage = ref('Loading...');
        const draggedBookmarkId = ref('');
        const dragOverBookmarkId = ref('');
        const suppressNextBookmarkClick = ref(false);
        const bookmarkOverflowToggleRef = ref(null);

        const playerKey = ref(0);
        const isYoutube = ref(false);
        const youtubeSrc = ref('');
        const playerInstance = ref(null);
        const videoPlayer = ref(null);
        const videoTracks = ref([]);
        const repeatEnabled = ref(false);

        const theme = ref('system');

        const contentModeLabels = {
            itv: 'Channels',
            vod: 'Movies',
            series: 'Series'
        };
        const SUPPORTED_MULTI_MODE_TYPES = new Set(['STALKER_PORTAL', 'XTREME_API']);

        const createModeState = () => ({
            viewState: 'accounts',
            categoryId: null,
            categories: [],
            channels: [],
            episodes: [],
            selectedSeason: '',
            selectedSeriesId: '',
            detail: null
        });

        const createStickyStates = () => ({
            itv: createModeState(),
            vod: createModeState(),
            series: createModeState()
        });

        const stickyStates = ref(createStickyStates());

        const getModeState = (mode = contentMode.value) => stickyStates.value[mode];
        const selectedSeriesSeason = ref('');
        const seriesDetail = ref(null);
        const vodDetail = ref(null);
        const seriesDetailLoading = ref(false);
        const vodDetailLoading = ref(false);

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
            const sxe = name.match(/[sS]\s*([0-9]{1,2})\s*[eE]\s*[0-9]{1,3}/);
            if (sxe?.[1]) return String(parseInt(sxe[1], 10));
            const xMatch = name.match(/([0-9]{1,2})\s*x\s*[0-9]{1,3}/i);
            if (xMatch?.[1]) return String(parseInt(xMatch[1], 10));
            return '';
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
                .replace(/^\s*s\d+\s*e\d+\s*[-:]\s*/i, '')
                .replace(/^\s*\d+\s*x\s*\d+\s*[-:]\s*/i, '')
                .trim();
        };

        const getEpisodeDisplayTitle = (episode, idx = 0) => {
            const cleaned = cleanEpisodeTitle(String(episode?.name || ''));
            return cleaned || `Episode ${idx + 1}`;
        };

        const seriesSeasonTabs = computed(() => {
            if (contentMode.value !== 'series' || viewState.value !== 'episodes') return [];
            const unique = new Set();
            for (const episode of (episodes.value || [])) {
                const season = resolveEpisodeSeason(episode);
                if (season) unique.add(season);
            }
            return Array.from(unique)
                .sort((a, b) => Number(a) - Number(b))
                .map(s => ({ value: s, label: `Season ${s}` }));
        });

        const ensureSeriesSeasonSelected = () => {
            if (contentMode.value !== 'series' || viewState.value !== 'episodes') return;
            const tabs = seriesSeasonTabs.value;
            if (!tabs.length) {
                selectedSeriesSeason.value = '';
                const modeState = getModeState('series');
                modeState.selectedSeason = '';
                return;
            }
            const exists = tabs.some(t => t.value === selectedSeriesSeason.value);
            if (!exists) {
                selectedSeriesSeason.value = tabs[0].value;
            }
            const modeState = getModeState('series');
            modeState.selectedSeason = selectedSeriesSeason.value;
        };

        const selectSeriesSeason = (season) => {
            selectedSeriesSeason.value = String(season || '');
            const modeState = getModeState('series');
            modeState.selectedSeason = selectedSeriesSeason.value;
        };

        const getEpisodeSubtitle = (episode) => {
            const parts = [];
            const season = resolveEpisodeSeason(episode);
            const epNum = resolveEpisodeNumber(episode);
            if (season || epNum) {
                const s = season ? `S${String(season).padStart(2, '0')}` : '';
                const e = epNum ? `E${String(epNum).padStart(2, '0')}` : '';
                parts.push([s, e].filter(Boolean).join(' · '));
            }
            if (episode?.releaseDate) parts.push(`Release: ${formatShortDate(episode.releaseDate)}`);
            if (episode?.duration) parts.push(`Duration: ${episode.duration}`);
            if (episode?.description) parts.push(episode.description);
            return parts.join('\n');
        };

        const formatShortDate = (value) => {
            const raw = String(value || '').trim();
            if (!raw) return '';
            const d = new Date(raw);
            if (!Number.isNaN(d.getTime())) {
                const day = new Intl.DateTimeFormat('en', { day: 'numeric', timeZone: 'UTC' }).format(d);
                const month = new Intl.DateTimeFormat('en', { month: 'short', timeZone: 'UTC' }).format(d);
                const year = new Intl.DateTimeFormat('en', { year: 'numeric', timeZone: 'UTC' }).format(d);
                return `${day} ${month} ${year}`;
            }
            const m = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
            if (m) {
                const tmp = new Date(`${m[1]}-${m[2]}-${m[3]}T00:00:00Z`);
                if (!Number.isNaN(tmp.getTime())) {
                    const day = new Intl.DateTimeFormat('en', { day: 'numeric', timeZone: 'UTC' }).format(tmp);
                    const month = new Intl.DateTimeFormat('en', { month: 'short', timeZone: 'UTC' }).format(tmp);
                    return `${day} ${month} ${m[1]}`;
                }
            }
            return raw;
        };

        const enrichEpisodesFromMeta = (items, detail) => {
            const episodesList = Array.isArray(items) ? items : [];
            const metaList = Array.isArray(detail?.episodesMeta) ? detail.episodesMeta : [];
            if (!episodesList.length || !metaList.length) return episodesList;

            const bySeasonEpisode = new Map();
            const byTitle = new Map();
            for (const row of metaList) {
                const season = String(row?.season || '').replace(/[^0-9]/g, '');
                const episodeNum = String(row?.episodeNum || '').replace(/[^0-9]/g, '');
                if (season && episodeNum) {
                    bySeasonEpisode.set(`${parseInt(season, 10)}:${parseInt(episodeNum, 10)}`, row);
                }
                const titleKey = normalizeTitle(row?.title || '');
                if (titleKey) byTitle.set(titleKey, row);
            }

            return episodesList.map((episode) => {
                const season = resolveEpisodeSeason(episode);
                const epNum = resolveEpisodeNumber(episode);
                let meta = null;
                if (season && epNum) {
                    meta = bySeasonEpisode.get(`${season}:${epNum}`) || null;
                }
                if (!meta) {
                    meta = byTitle.get(normalizeTitle(cleanEpisodeTitle(episode?.name || ''))) || null;
                }
                if (!meta) return episode;
                return {
                    ...episode,
                    logo: resolveLogoUrl(meta.logo || episode.logo),
                    description: episode.description || meta.plot || '',
                    releaseDate: episode.releaseDate || meta.releaseDate || '',
                    season: episode.season || meta.season || '',
                    episodeNum: episode.episodeNum || meta.episodeNum || ''
                };
            });
        };

        const mergeDetailIfBlank = (target, source, key) => {
            const current = String(target?.[key] || '').trim();
            const incoming = String(source?.[key] || '').trim();
            if (!current && incoming) target[key] = incoming;
        };

        const getImdbUrl = () => {
            const detail = contentMode.value === 'vod' ? vodDetail.value : seriesDetail.value;
            if (!detail) return '';
            if (detail.imdbUrl) return detail.imdbUrl;
            const imdbId = detail.tmdb || '';
            if (/^tt\d+$/i.test(imdbId)) return `https://www.imdb.com/title/${imdbId}/`;
            return '';
        };

        const applyModeState = (mode = contentMode.value) => {
            const state = getModeState(mode);
            categories.value = [...(state.categories || [])];
            channels.value = [...(state.channels || [])];
            episodes.value = [...(state.episodes || [])];
            currentContext.value.categoryId = state.categoryId || null;
            viewState.value = state.viewState === 'accounts' ? 'categories' : state.viewState;
            if (mode === 'series') {
                selectedSeriesSeason.value = state.selectedSeason || '';
                seriesDetail.value = state.detail || null;
                ensureSeriesSeasonSelected();
            } else if (mode === 'vod') {
                vodDetail.value = state.detail || null;
            }
        };

        const searchPlaceholder = computed(() => {
            if (activeTab.value === 'accounts') {
                if (viewState.value === 'accounts') return 'Search accounts...';
                if (viewState.value === 'categories') return 'Search categories...';
                if (viewState.value === 'channels') return `Search ${contentModeLabels[contentMode.value].toLowerCase()}...`;
                if (viewState.value === 'episodes') return 'Search episodes...';
            }
            if (activeTab.value === 'bookmarks') return 'Search bookmarks...';
            return 'Search...';
        });

        const supportsVodSeriesForSelectedAccount = computed(() => {
            const accountType = String(currentContext.value.accountType || '').toUpperCase();
            return SUPPORTED_MULTI_MODE_TYPES.has(accountType);
        });

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

        const filteredAccounts = computed(() => {
            if (!searchQuery.value) return sortedAccounts.value;
            return sortedAccounts.value.filter(a => (a.accountName || '').toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredCategories = computed(() => {
            if (!searchQuery.value) return categories.value;
            return categories.value.filter(c => (c.title || '').toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredChannels = computed(() => {
            if (!searchQuery.value) return channels.value;
            return channels.value.filter(c => (c.name || '').toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredEpisodes = computed(() => {
            if (!searchQuery.value) return episodes.value;
            return episodes.value.filter(c => (c.name || '').toLowerCase().includes(searchQuery.value.toLowerCase()));
        });

        const filteredSeriesEpisodes = computed(() => {
            if (contentMode.value !== 'series' || viewState.value !== 'episodes') return filteredEpisodes.value;
            const list = filteredEpisodes.value || [];
            if (!selectedSeriesSeason.value) return list;
            return list.filter(ep => resolveEpisodeSeason(ep) === selectedSeriesSeason.value);
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
        const bookmarkPrimaryTabs = computed(() => bookmarkCategoryTabs.value.slice(0, 7));
        const bookmarkOverflowTabs = computed(() => bookmarkCategoryTabs.value.slice(7));
        const isSelectedBookmarkInOverflow = computed(() => {
            const selected = String(selectedBookmarkCategoryId.value || '');
            return bookmarkOverflowTabs.value.some(tab => String(tab?.id || '') === selected);
        });

        const canReorderBookmarks = computed(() => {
            return activeTab.value === 'bookmarks' && !String(searchQuery.value || '').trim();
        });

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

        const currentChannelName = computed(() => currentChannel.value ? currentChannel.value.name : '');

        const resolveAccountName = (accountId) => {
            const account = accounts.value.find(a => String(a.dbId) === String(accountId));
            return account?.accountName || '';
        };

        const resolveAccountId = (accountName) => {
            const account = accounts.value.find(a => String(a.accountName || '') === String(accountName || ''));
            return account?.dbId || '';
        };

        const resolveLogoUrl = (logo) => {
            const raw = String(logo || '').trim();
            if (!raw) return '';
            if (/^(data:|blob:|https?:\/\/|file:)/i.test(raw)) return raw;
            if (raw.startsWith('//')) return `${window.location.protocol}${raw}`;
            if (raw.startsWith('/')) return `${window.location.origin}${raw}`;
            return `${window.location.origin}/${raw.replace(/^\.?\//, '')}`;
        };

        const normalizeChannel = (channel = {}) => ({
            ...channel,
            logo: resolveLogoUrl(channel.logo)
        });

        const normalizeBookmark = (bookmark = {}) => ({
            ...bookmark,
            logo: resolveLogoUrl(bookmark.logo)
        });

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

        const withSyntheticAllCategory = (items, accountType) => {
            const list = Array.isArray(items) ? [...items] : [];
            const normalizedType = String(accountType || '').toUpperCase();
            const isStalkerOrXtreme = normalizedType === 'STALKER_PORTAL' || normalizedType === 'XTREME_API';
            const hasAll = list.some(c => String(c?.title || '').toLowerCase() === 'all');
            if (hasAll) return list;
            if (isStalkerOrXtreme && list.length < 2) return list;
            return [{ dbId: 'All', categoryId: 'All', title: 'All' }, ...list];
        };

        const loadCategories = async (accountIdOrObj, forceReload = false) => {
            const selected = typeof accountIdOrObj === 'object'
                ? accountIdOrObj
                : accounts.value.find(a => String(a.dbId) === String(accountIdOrObj));

            currentContext.value.accountId = selected?.dbId || accountIdOrObj;
            currentContext.value.accountType = selected?.type || null;
            const modeState = getModeState(contentMode.value);

            if (!forceReload && modeState.categories.length > 0) {
                applyModeState(contentMode.value);
                searchQuery.value = '';
                return;
            }

            try {
                listLoading.value = true;
                listLoadingMessage.value = 'Loading categories...';
                const response = await fetch(
                    `${window.location.origin}/categories?accountId=${currentContext.value.accountId}&mode=${contentMode.value}`
                );
                categories.value = withSyntheticAllCategory(await response.json(), currentContext.value.accountType);
                channels.value = [];
                episodes.value = [];
                viewState.value = 'categories';
                modeState.categories = [...categories.value];
                modeState.channels = [];
                modeState.episodes = [];
                modeState.categoryId = null;
                modeState.viewState = 'categories';
                modeState.selectedSeason = '';
                modeState.selectedSeriesId = '';
                modeState.detail = null;
                if (contentMode.value === 'series') {
                    selectedSeriesSeason.value = '';
                    seriesDetail.value = null;
                    seriesDetailLoading.value = false;
                } else if (contentMode.value === 'vod') {
                    vodDetail.value = null;
                    vodDetailLoading.value = false;
                }
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load categories', e);
            } finally {
                listLoading.value = false;
            }
        };

        const loadChannels = async (categoryId, forceReload = false) => {
            currentContext.value.categoryId = categoryId;
            const modeState = getModeState(contentMode.value);
            if (!forceReload &&
                String(modeState.categoryId || '') === String(categoryId || '') &&
                modeState.channels.length > 0) {
                channels.value = [...modeState.channels];
                episodes.value = [];
                viewState.value = 'channels';
                modeState.viewState = 'channels';
                searchQuery.value = '';
                return;
            }
            try {
                listLoading.value = true;
                listLoadingMessage.value = 'Loading channels...';
                const response = await fetch(
                    `${window.location.origin}/channels?categoryId=${categoryId}&accountId=${currentContext.value.accountId}&mode=${contentMode.value}`
                );
                channels.value = (await response.json()).map(normalizeChannel);
                episodes.value = [];
                viewState.value = 'channels';
                modeState.categoryId = categoryId;
                modeState.channels = [...channels.value];
                modeState.episodes = [];
                modeState.viewState = 'channels';
                modeState.selectedSeason = '';
                modeState.selectedSeriesId = '';
                modeState.detail = null;
                if (contentMode.value === 'series') {
                    selectedSeriesSeason.value = '';
                    seriesDetail.value = null;
                    seriesDetailLoading.value = false;
                } else if (contentMode.value === 'vod') {
                    vodDetail.value = null;
                    vodDetailLoading.value = false;
                }
                searchQuery.value = '';
            } catch (e) {
                console.error('Failed to load channels', e);
            } finally {
                listLoading.value = false;
            }
        };

        const loadSeriesEpisodes = async (seriesId) => {
            viewState.value = 'episodes';
            episodes.value = [];
            const modeState = getModeState('series');
            try {
                listLoading.value = true;
                listLoadingMessage.value = 'Loading episodes...';
                const response = await fetch(
                    `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${currentContext.value.accountId}`
                );
                episodes.value = (await response.json()).map(normalizeChannel);
                viewState.value = 'episodes';
                episodes.value = enrichEpisodesFromMeta(episodes.value, modeState.detail || null);
                modeState.episodes = [...episodes.value];
                modeState.viewState = 'episodes';
                searchQuery.value = '';
                selectedSeriesSeason.value = modeState.selectedSeason || '';
                ensureSeriesSeasonSelected();
            } catch (e) {
                console.error('Failed to load series episodes', e);
            } finally {
                listLoading.value = false;
            }
        };

        const loadSeriesChildren = async (movieId) => {
            viewState.value = 'episodes';
            episodes.value = [];
            const modeState = getModeState('series');
            try {
                listLoading.value = true;
                listLoadingMessage.value = 'Loading episodes...';
                const response = await fetch(
                    `${window.location.origin}/channels?categoryId=${currentContext.value.categoryId}&accountId=${currentContext.value.accountId}&mode=series&movieId=${encodeURIComponent(movieId)}`
                );
                episodes.value = (await response.json()).map(normalizeChannel);
                viewState.value = 'episodes';
                episodes.value = enrichEpisodesFromMeta(episodes.value, modeState.detail || null);
                modeState.episodes = [...episodes.value];
                modeState.viewState = 'episodes';
                searchQuery.value = '';
                selectedSeriesSeason.value = modeState.selectedSeason || '';
                ensureSeriesSeasonSelected();
            } catch (e) {
                console.error('Failed to load series children', e);
            } finally {
                listLoading.value = false;
            }
        };

        const openVodDetails = async (vodItem) => {
            const modeState = getModeState('vod');
            const channelIdentifier = vodItem.channelId || vodItem.id || vodItem.dbId;
            const detail = {
                name: vodItem.name || 'VOD',
                cover: resolveLogoUrl(vodItem.logo || ''),
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

            modeState.detail = detail;
            modeState.selectedSeriesId = String(channelIdentifier || '');
            modeState.viewState = 'vodDetail';
            vodDetail.value = detail;
            viewState.value = 'vodDetail';
            searchQuery.value = '';
            vodDetailLoading.value = true;

            // Lazy-load heavy metadata in background.
            (async () => {
                try {
                    const response = await fetch(
                        `${window.location.origin}/vodDetails?accountId=${currentContext.value.accountId}&categoryId=${encodeURIComponent(currentContext.value.categoryId || '')}&channelId=${encodeURIComponent(channelIdentifier || '')}&vodName=${encodeURIComponent(vodItem.name || '')}`
                    );
                    const data = await response.json();
                    if (String(modeState.selectedSeriesId || '') !== String(channelIdentifier || '')) return;
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
                    modeState.detail = { ...detail };
                    vodDetail.value = modeState.detail;
                } catch (e) {
                    console.error('Failed to load VOD details', e);
                } finally {
                    vodDetailLoading.value = false;
                }
            })();
        };

        const playVodFromDetail = () => {
            const detail = vodDetail.value;
            if (!detail?.playItem) return;
            playChannel(detail.playItem);
        };

        const loadBookmarks = async () => {
            try {
                const response = await fetch(`${window.location.origin}/bookmarks`);
                bookmarks.value = (await response.json()).map(normalizeBookmark);
                ensureSelectedBookmarkCategory();
            } catch (e) {
                console.error('Failed to load bookmarks', e);
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
            searchQuery.value = '';
        };

        const hideBookmarkOverflowDropdown = () => {
            const toggle = bookmarkOverflowToggleRef.value;
            if (!toggle || typeof bootstrap === 'undefined' || !bootstrap?.Dropdown) return;
            const instance = bootstrap.Dropdown.getOrCreateInstance(toggle);
            instance.hide();
        };

        const onBookmarkOverflowSelect = (categoryId) => {
            selectBookmarkCategory(categoryId);
            hideBookmarkOverflowDropdown();
        };

        const onBookmarkCardClick = (bookmark) => {
            if (suppressNextBookmarkClick.value) {
                suppressNextBookmarkClick.value = false;
                return;
            }
            playBookmark(bookmark);
        };

        const onBookmarkDragStart = (event, bookmark) => {
            if (!canReorderBookmarks.value) {
                if (event?.preventDefault) event.preventDefault();
                return;
            }
            draggedBookmarkId.value = String(bookmark?.dbId || '');
            dragOverBookmarkId.value = '';
            if (event?.dataTransfer) {
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', draggedBookmarkId.value);
            }
        };

        const onBookmarkDragOver = (event, bookmark) => {
            if (!canReorderBookmarks.value) return;
            if (event?.dataTransfer) event.dataTransfer.dropEffect = 'move';
            dragOverBookmarkId.value = String(bookmark?.dbId || '');
        };

        const persistBookmarkOrder = async () => {
            try {
                const categoryId = String(selectedBookmarkCategoryId.value || '');
                const orderedBookmarkDbIds = filteredBookmarks.value
                    .map(b => String(b?.dbId || '').trim())
                    .filter(Boolean);
                await fetch(`${window.location.origin}/bookmarks`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        categoryId,
                        orderedBookmarkDbIds
                    })
                });
            } catch (e) {
                console.error('Failed to persist bookmark order', e);
            }
        };

        const onBookmarkDrop = async (event, targetBookmark) => {
            if (!canReorderBookmarks.value) return;
            const sourceId = String(draggedBookmarkId.value || '');
            const targetId = String(targetBookmark?.dbId || '');
            if (!sourceId || !targetId || sourceId === targetId) {
                draggedBookmarkId.value = '';
                dragOverBookmarkId.value = '';
                return;
            }

            const visible = [...filteredBookmarks.value];
            const fromIndex = visible.findIndex(b => String(b?.dbId || '') === sourceId);
            const toIndex = visible.findIndex(b => String(b?.dbId || '') === targetId);
            if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) {
                draggedBookmarkId.value = '';
                dragOverBookmarkId.value = '';
                return;
            }

            const [moved] = visible.splice(fromIndex, 1);
            visible.splice(toIndex, 0, moved);

            const selectedCategoryId = String(selectedBookmarkCategoryId.value || '');
            if (!selectedCategoryId) {
                bookmarks.value = visible;
            } else {
                const full = [...bookmarks.value];
                const categoryIndexes = [];
                for (let i = 0; i < full.length; i++) {
                    if (String(full[i]?.categoryId || '') === selectedCategoryId) {
                        categoryIndexes.push(i);
                    }
                }
                for (let i = 0; i < categoryIndexes.length && i < visible.length; i++) {
                    full[categoryIndexes[i]] = visible[i];
                }
                bookmarks.value = full;
            }

            suppressNextBookmarkClick.value = true;
            draggedBookmarkId.value = '';
            dragOverBookmarkId.value = '';
            await persistBookmarkOrder();
        };

        const onBookmarkDragEnd = () => {
            draggedBookmarkId.value = '';
            dragOverBookmarkId.value = '';
            setTimeout(() => {
                suppressNextBookmarkClick.value = false;
            }, 0);
        };

        const switchTab = (tab) => {
            if (activeTab.value === tab && tab === 'accounts') {
                viewState.value = 'accounts';
            } else {
                activeTab.value = tab;
            }
            searchQuery.value = '';
        };

        const selectAccount = async (account) => {
            const nextAccountId = String(account?.dbId || '');
            const accountChanged = String(selectedAccountId.value || '') !== nextAccountId;
            if (accountChanged) {
                stickyStates.value = createStickyStates();
                seriesDetailLoading.value = false;
                vodDetailLoading.value = false;
            }
            selectedAccountId.value = nextAccountId;
            contentMode.value = 'itv';
            if (!accountChanged && getModeState('itv').categories.length > 0) {
                currentContext.value.accountId = account.dbId;
                currentContext.value.accountType = account.type;
                applyModeState('itv');
                searchQuery.value = '';
                return;
            }
            await loadCategories(account, true);
        };

        const setContentMode = async (mode) => {
            if (!['itv', 'vod', 'series'].includes(mode)) return;
            if (mode !== 'itv' && !supportsVodSeriesForSelectedAccount.value) return;
            if (contentMode.value === mode) return;
            contentMode.value = mode;
            searchQuery.value = '';

            if (currentContext.value.accountId && viewState.value !== 'accounts') {
                const modeState = getModeState(mode);
                if (modeState.categories.length > 0) {
                    applyModeState(mode);
                    return;
                }
                await loadCategories(currentContext.value.accountId, true);
            }
        };

        const goBackToAccounts = () => {
            viewState.value = 'accounts';
            searchQuery.value = '';
        };

        const goBackToCategories = () => {
            const modeState = getModeState(contentMode.value);
            if (viewState.value === 'episodes' || viewState.value === 'vodDetail') {
                viewState.value = 'channels';
            } else {
                viewState.value = 'categories';
            }
            modeState.viewState = viewState.value;
            if (viewState.value === 'categories') {
                episodes.value = [];
                modeState.episodes = [];
                modeState.selectedSeason = '';
                modeState.selectedSeriesId = '';
                modeState.detail = null;
                selectedSeriesSeason.value = '';
                seriesDetail.value = null;
                seriesDetailLoading.value = false;
                vodDetail.value = null;
                vodDetailLoading.value = false;
            }
            searchQuery.value = '';
        };

        const appendPlaybackCompatParams = (query, mode) => {
            const resolvedMode = String(mode || '').toLowerCase();
            if (resolvedMode !== 'itv' && resolvedMode !== 'vod' && resolvedMode !== 'series') return;
            query.set('mode', resolvedMode);
            query.set('streamType', resolvedMode === 'itv' ? 'live' : 'video');
            query.set('action', resolvedMode);
        };

        const buildPlayerUrlForChannel = (channel, modeOverride = null) => {
            const modeToUse = String(modeOverride || contentMode.value || 'itv').toLowerCase();
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

            appendPlaybackCompatParams(query, modeToUse);
            return `${window.location.origin}/player?${query.toString()}`;
        };

        const decodeBase64Url = (value) => {
            const raw = String(value || '').trim();
            if (!raw) return '';
            let normalized = raw.replace(/-/g, '+').replace(/_/g, '/');
            while (normalized.length % 4 !== 0) {
                normalized += '=';
            }
            try {
                return atob(normalized);
            } catch (_) {
                return '';
            }
        };

        const cleanLaunchValue = (value) => {
            const normalized = String(value ?? '').trim();
            if (!normalized || normalized.toLowerCase() === 'null' || normalized.toLowerCase() === 'undefined') {
                return '';
            }
            return normalized;
        };

        const parseDrmLaunchPayload = () => {
            try {
                const params = new URLSearchParams(window.location.search);
                const encoded = params.get('drmLaunch');
                if (!encoded) return null;
                const decoded = decodeBase64Url(encoded);
                if (!decoded) return null;
                return JSON.parse(decoded);
            } catch (_) {
                return null;
            }
        };

        const buildPlayerUrlFromLaunchPayload = (payload) => {
            const mode = String(payload?.mode || 'itv').toLowerCase();
            const channel = payload?.channel || {};
            const query = new URLSearchParams();
            query.set('accountId', cleanLaunchValue(payload?.accountId));
            query.set('categoryId', cleanLaunchValue(payload?.categoryId));
            query.set('mode', mode);

            const channelDbId = cleanLaunchValue(channel.dbId);
            const channelIdentifier = cleanLaunchValue(channel.channelId || channel.id);
            if (channelDbId) {
                query.set('channelId', channelDbId);
            } else {
                query.set('channelId', channelIdentifier);
            }
            query.set('name', cleanLaunchValue(channel.name));
            query.set('logo', cleanLaunchValue(channel.logo));
            query.set('cmd', cleanLaunchValue(channel.cmd));
            query.set('cmd_1', cleanLaunchValue(channel.cmd_1));
            query.set('cmd_2', cleanLaunchValue(channel.cmd_2));
            query.set('cmd_3', cleanLaunchValue(channel.cmd_3));
            query.set('drmType', cleanLaunchValue(channel.drmType));
            query.set('drmLicenseUrl', cleanLaunchValue(channel.drmLicenseUrl));
            query.set('clearKeysJson', cleanLaunchValue(channel.clearKeysJson));
            query.set('inputstreamaddon', cleanLaunchValue(channel.inputstreamaddon));
            query.set('manifestType', cleanLaunchValue(channel.manifestType));
            if (mode === 'series') {
                query.set('seriesId', channelIdentifier);
            }

            appendPlaybackCompatParams(query, mode);
            return `${window.location.origin}/player?${query.toString()}`;
        };

        const launchDrmPlaybackFromPayload = async (payload) => {
            if (!payload?.channel) return;

            const mode = String(payload.mode || 'itv').toLowerCase();
            contentMode.value = ['itv', 'vod', 'series'].includes(mode) ? mode : 'itv';
            activeTab.value = 'accounts';
            viewState.value = 'channels';
            currentContext.value.accountId = payload.accountId || '';
            currentContext.value.categoryId = payload.categoryId || '';

            const selectedAccount = accounts.value.find(a => String(a.dbId) === String(currentContext.value.accountId || ''));
            currentContext.value.accountType = selectedAccount?.type || null;

            const channel = normalizeChannel(payload.channel || {});
            const playbackUrl = buildPlayerUrlFromLaunchPayload(payload);
            const channelIdentifier = channel.dbId || channel.channelId || channel.id || '';

            currentChannel.value = {
                id: channelIdentifier,
                dbId: channel.dbId || '',
                channelId: channel.channelId || channelIdentifier,
                name: channel.name || 'DRM Channel',
                logo: resolveLogoUrl(channel.logo),
                cmd: channel.cmd || '',
                drmType: channel.drmType || '',
                drmLicenseUrl: channel.drmLicenseUrl || '',
                clearKeysJson: channel.clearKeysJson || '',
                inputstreamaddon: channel.inputstreamaddon || '',
                manifestType: channel.manifestType || '',
                accountId: currentContext.value.accountId,
                categoryId: currentContext.value.categoryId,
                type: 'channel',
                mode: contentMode.value,
                playRequestUrl: playbackUrl
            };
            await startPlayback(playbackUrl);

            const cleanUrl = `${window.location.origin}${window.location.pathname}`;
            window.history.replaceState({}, document.title, cleanUrl);
        };

        const playChannel = (channel) => {
            scrollToTop();
            const modeToUse = String(contentMode.value || 'itv').toLowerCase();
            const channelIdentifier = channel.dbId || channel.channelId || channel.id;
            const playbackUrl = buildPlayerUrlForChannel(channel, modeToUse);
            currentChannel.value = {
                id: channelIdentifier,
                dbId: channel.dbId || '',
                channelId: channel.channelId || channelIdentifier,
                name: channel.name,
                logo: resolveLogoUrl(channel.logo),
                cmd: channel.cmd,
                drmType: channel.drmType,
                drmLicenseUrl: channel.drmLicenseUrl,
                clearKeysJson: channel.clearKeysJson,
                inputstreamaddon: channel.inputstreamaddon,
                manifestType: channel.manifestType,
                accountId: currentContext.value.accountId,
                categoryId: currentContext.value.categoryId,
                type: 'channel',
                mode: modeToUse,
                playRequestUrl: playbackUrl
            };
            startPlayback(playbackUrl);
        };

        const playBookmark = (bookmark) => {
            scrollToTop();
            const bookmarkMode = String(bookmark.accountAction || bookmark.mode || 'itv').toLowerCase();
            const query = new URLSearchParams();
            query.set('bookmarkId', bookmark.dbId);
            appendPlaybackCompatParams(query, bookmarkMode);
            const playbackUrl = `${window.location.origin}/player?${query.toString()}`;

            currentChannel.value = {
                id: bookmark.dbId,
                name: bookmark.channelName,
                logo: resolveLogoUrl(bookmark.logo),
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

        const handleChannelSelection = async (channel) => {
            if (contentMode.value === 'series' && viewState.value === 'channels') {
                const seriesId = channel.channelId || channel.id || channel.dbId;
                const modeState = getModeState('series');
                modeState.selectedSeriesId = String(seriesId || '');
                modeState.detail = {
                    name: channel.name || 'Series',
                    cover: resolveLogoUrl(channel.logo),
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
                seriesDetail.value = modeState.detail;
                seriesDetailLoading.value = true;
                if (String(currentContext.value.accountType || '').toUpperCase() === 'XTREME_API') {
                    await loadSeriesEpisodes(seriesId);
                } else {
                    await loadSeriesChildren(seriesId);
                }
                (async () => {
                    try {
                        const response = await fetch(
                            `${window.location.origin}/seriesDetails?seriesId=${encodeURIComponent(seriesId)}&accountId=${currentContext.value.accountId}&seriesName=${encodeURIComponent(channel.name || '')}`
                        );
                        const data = await response.json();
                        if (String(modeState.selectedSeriesId || '') !== String(seriesId || '')) return;

                        const detail = { ...(modeState.detail || {}) };
                        if (data?.seasonInfo) {
                            const info = data.seasonInfo;
                            mergeDetailIfBlank(detail, info, 'name');
                            mergeDetailIfBlank(detail, info, 'cover');
                            mergeDetailIfBlank(detail, info, 'plot');
                            mergeDetailIfBlank(detail, info, 'cast');
                            mergeDetailIfBlank(detail, info, 'director');
                            mergeDetailIfBlank(detail, info, 'genre');
                            mergeDetailIfBlank(detail, info, 'releaseDate');
                            mergeDetailIfBlank(detail, info, 'rating');
                            mergeDetailIfBlank(detail, info, 'tmdb');
                            mergeDetailIfBlank(detail, info, 'imdbUrl');
                        }
                        if (Array.isArray(data?.episodesMeta)) {
                            detail.episodesMeta = data.episodesMeta;
                        }
                        if (Array.isArray(data?.episodes) && data.episodes.length > 0 && (!episodes.value || episodes.value.length === 0)) {
                            episodes.value = data.episodes.map(normalizeChannel);
                        }
                        episodes.value = enrichEpisodesFromMeta(episodes.value, detail);
                        modeState.episodes = [...episodes.value];
                        modeState.detail = detail;
                        seriesDetail.value = detail;
                        ensureSeriesSeasonSelected();
                    } catch (e) {
                        console.error('Failed to load series details', e);
                    } finally {
                        seriesDetailLoading.value = false;
                    }
                })();
                return;
            }
            if (contentMode.value === 'vod' && viewState.value === 'channels') {
                await openVodDetails(channel);
                return;
            }
            playChannel(channel);
        };

        const bindPlaybackEvents = (video) => {
            if (!video) return;
            video.onended = async () => {
                if (!repeatEnabled.value || !currentChannel.value?.playRequestUrl) return;
                await startPlayback(currentChannel.value.playRequestUrl);
            };
        };

        const clearVideoElement = (video) => {
            if (!video) return;
            video.onended = null;
            video.pause();
            video.src = '';
            video.removeAttribute('src');
            video.load();
        };

        const startPlayback = async (url) => {
            await stopPlayback(true);

            playerKey.value++;
            isPlaying.value = true;

            try {
                const response = await fetch(url);
                const channelData = await response.json();
                await initPlayer(channelData);
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

            clearVideoElement(videoPlayer.value);

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
                console.error('No URL provided for playback.');
                isPlaying.value = false;
                return;
            }

            const youtubeId = extractYoutubeIdFromAny([
                uri,
                currentChannel.value?.cmd || '',
                currentChannel.value?.playRequestUrl || ''
            ]);

            if (youtubeId) {
                isYoutube.value = true;
                youtubeSrc.value = `https://www.youtube.com/embed/${youtubeId}?autoplay=1`;
                return;
            }

            isYoutube.value = false;
            await nextTick();

            const video = videoPlayer.value;
            if (!video) {
                console.error('Video element not found.');
                return;
            }

            bindPlaybackEvents(video);

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
            await nextTick();
            const video = videoPlayer.value;
            if (!video) return;

            bindPlaybackEvents(video);
            let sourceUrl = normalizeWebPlaybackUrl(channel.url);
            video.src = sourceUrl;
            try {
                await video.play();
            } catch (e) {
                // If backend proxy fails (e.g. 502), retry direct source URL from src query param.
                const proxiedSource = extractProxySourceUrl(sourceUrl);
                if (proxiedSource) {
                    try {
                        sourceUrl = normalizeWebPlaybackUrl(proxiedSource);
                        video.src = sourceUrl;
                        await video.play();
                        return;
                    } catch (proxyErr) {
                        console.warn('Proxy source fallback failed.', proxyErr);
                    }
                }

                // Retry with explicit HTTP downgrade for known Stalker playback paths.
                const downgraded = downgradeHttpsToHttpForKnownPaths(sourceUrl);
                if (downgraded !== sourceUrl) {
                    try {
                        sourceUrl = downgraded;
                        video.src = sourceUrl;
                        await video.play();
                        return;
                    } catch (retryErr) {
                        console.warn('HTTP fallback failed.', retryErr);
                    }
                }

                // Last fallback: try Shaka for browsers that reject native source MIME sniffing.
                try {
                    await loadShaka({ ...(channel || {}), url: sourceUrl });
                    return;
                } catch (shakaErr) {
                    console.warn('Shaka fallback failed.', shakaErr);
                }

                console.warn('Native playback failed.', e);
            }
        };

        const normalizeWebPlaybackUrl = (rawUrl) => {
            const value = String(rawUrl || '').trim();
            if (!value) return value;
            return downgradeHttpsToHttpForKnownPaths(value);
        };

        const downgradeHttpsToHttpForKnownPaths = (url) => {
            const value = String(url || '').trim();
            const lower = value.toLowerCase();
            if (lower.startsWith('https://') && (lower.includes('/live/play/') || lower.includes('/play/movie.php'))) {
                return `http://${value.slice('https://'.length)}`;
            }
            return value;
        };

        const extractProxySourceUrl = (url) => {
            const value = String(url || '').trim();
            if (!value) return '';
            try {
                const parsed = new URL(value, window.location.origin);
                if (!parsed.pathname.includes('/proxy-stream')) return '';
                const src = parsed.searchParams.get('src');
                return src ? decodeURIComponent(src) : '';
            } catch (_) {
                return '';
            }
        };

        const extractYoutubeId = (value) => {
            const raw = String(value || '').trim();
            if (!raw) return '';

            const decoded = (() => {
                try { return decodeURIComponent(raw); } catch (_) { return raw; }
            })();

            // Handle ffmpeg prefix if present.
            const cleaned = decoded.replace(/^ffmpeg\s+/i, '').trim();

            const directPatterns = [
                /(?:youtube\.com\/watch\?[^#\s]*v=)([A-Za-z0-9_-]{11})/i,
                /(?:youtube\.com\/embed\/)([A-Za-z0-9_-]{11})/i,
                /(?:youtu\.be\/)([A-Za-z0-9_-]{11})/i,
                /(?:youtube\.com\/shorts\/)([A-Za-z0-9_-]{11})/i,
                /(?:youtube-nocookie\.com\/embed\/)([A-Za-z0-9_-]{11})/i
            ];

            for (const re of directPatterns) {
                const m = cleaned.match(re);
                if (m?.[1]) return m[1];
            }

            // Some sources pass nested URL values like url=https://youtube...
            const nested = cleaned.match(/(?:^|[?&])url=([^&]+)/i);
            if (nested?.[1]) {
                const nestedDecoded = (() => {
                    try { return decodeURIComponent(nested[1]); } catch (_) { return nested[1]; }
                })();
                for (const re of directPatterns) {
                    const m = nestedDecoded.match(re);
                    if (m?.[1]) return m[1];
                }
            }

            return '';
        };

        const extractYoutubeIdFromAny = (values) => {
            const list = Array.isArray(values) ? values : [values];
            for (const value of list) {
                const id = extractYoutubeId(value);
                if (id) return id;
            }
            return '';
        };

        const loadShaka = async (channel) => {
            await nextTick();
            const video = videoPlayer.value;
            if (!video) return;

            bindPlaybackEvents(video);
            shaka.polyfill.installAll();

            if (!shaka.Player.isBrowserSupported()) {
                console.error('Shaka Player is not supported by this browser.');
                return;
            }

            const player = new shaka.Player();
            playerInstance.value = player;

            player.addEventListener('error', (event) => {
                console.error('Shaka Player Error:', event.detail);
            });

            if (channel.drm) {
                const drmConfig = {};
                if (channel.drm.licenseUrl) {
                    drmConfig.servers = { [channel.drm.type]: channel.drm.licenseUrl };
                }
                if (channel.drm.clearKeys) {
                    drmConfig.clearKeys = channel.drm.clearKeys;
                }
                player.configure({ drm: drmConfig });
            }

            try {
                await player.attach(video);
                await player.load(channel.url);
                videoTracks.value = player.getVariantTracks();
            } catch (e) {
                console.error('Shaka: Error loading video:', e);
            }
        };

        const switchVideoTrack = (trackId) => {
            if (!playerInstance.value) return;
            const track = playerInstance.value.getVariantTracks().find(t => t.id === trackId);
            if (track) {
                playerInstance.value.selectVariantTrack(track, true);
            }
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

        const reloadPlayback = async () => {
            if (!currentChannel.value?.playRequestUrl) return;
            await startPlayback(currentChannel.value.playRequestUrl);
        };

        const toggleRepeat = () => {
            repeatEnabled.value = !repeatEnabled.value;
        };

        const togglePictureInPicture = async () => {
            const video = videoPlayer.value;
            if (!video || isYoutube.value || !document.pictureInPictureEnabled) return;
            try {
                if (document.pictureInPictureElement === video) {
                    await document.exitPictureInPicture();
                } else {
                    await video.requestPictureInPicture();
                }
            } catch (e) {
                console.warn('Picture in Picture unavailable', e);
            }
        };

        const ensurePlaybackNotPaused = async () => {
            if (!isPlaying.value || isYoutube.value) return;
            const video = videoPlayer.value;
            if (!video) return;
            if (video.paused && !video.ended) {
                try {
                    await video.play();
                } catch (_) {
                    // Ignore autoplay/focus related resume issues.
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
            activeTab.value = 'bookmarks';
            viewState.value = 'accounts';
            contentMode.value = 'itv';
            categories.value = [];
            channels.value = [];
            episodes.value = [];
            stickyStates.value = createStickyStates();
            selectedAccountId.value = null;
            currentContext.value = { accountId: null, categoryId: null, accountType: null };
            searchQuery.value = '';
            selectedBookmarkCategoryId.value = '';
            repeatEnabled.value = false;
            listLoading.value = false;
            selectedSeriesSeason.value = '';
            seriesDetail.value = null;
            vodDetail.value = null;
            seriesDetailLoading.value = false;
            vodDetailLoading.value = false;
            draggedBookmarkId.value = '';
            dragOverBookmarkId.value = '';
            suppressNextBookmarkClick.value = false;
        };

        onMounted(async () => {
            await loadAccounts();
            await loadBookmarkCategories();
            await loadBookmarks();

            const storedTheme = localStorage.getItem('uiptv_theme');
            if (storedTheme) {
                theme.value = storedTheme;
            }
            applyTheme();

            const launchPayload = parseDrmLaunchPayload();
            if (launchPayload?.channel) {
                await launchDrmPlaybackFromPayload(launchPayload);
            }
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
            filteredSeriesEpisodes,
            filteredBookmarks,
            bookmarkCategoryTabs,
            bookmarkPrimaryTabs,
            bookmarkOverflowTabs,
            isSelectedBookmarkInOverflow,
            bookmarkOverflowToggleRef,
            seriesSeasonTabs,
            selectedSeriesSeason,
            selectedBookmarkCategoryId,
            seriesDetail,
            vodDetail,
            seriesDetailLoading,
            vodDetailLoading,
            currentChannelName,
            isCurrentFavorite,
            isPlaying,
            showOverlay,
            showBookmarkModal,
            listLoading,
            listLoadingMessage,
            canReorderBookmarks,
            draggedBookmarkId,
            dragOverBookmarkId,
            playerKey,
            isYoutube,
            youtubeSrc,
            videoPlayer,
            videoTracks,
            repeatEnabled,
            theme,
            themeIcon,
            contentMode,
            contentModeLabels,
            supportsVodSeriesForSelectedAccount,

            switchTab,
            setContentMode,
            selectAccount,
            loadCategories,
            loadChannels,
            loadSeriesEpisodes,
            goBackToAccounts,
            goBackToCategories,
            selectSeriesSeason,
            getEpisodeDisplayTitle,
            getEpisodeSubtitle,
            getImdbUrl,
            formatShortDate,
            playVodFromDetail,
            selectBookmarkCategory,
            onBookmarkOverflowSelect,
            playChannel,
            handleChannelSelection,
            playBookmark,
            onBookmarkCardClick,
            onBookmarkDragStart,
            onBookmarkDragOver,
            onBookmarkDrop,
            onBookmarkDragEnd,
            stopPlayback,
            toggleFavorite,
            reloadPlayback,
            toggleRepeat,
            togglePictureInPicture,
            onPlayerControlClick,
            imageError,
            mediaImageError,
            toggleTheme,
            clearWebCacheAndReload,
            resetApp,
            switchVideoTrack
        };
    }
}).mount('#app');
