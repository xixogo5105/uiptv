const {createApp, ref, computed, onMounted, nextTick, watch} = Vue;

createApp({
    setup() {
        const activeTab = ref('bookmarks');
        const viewState = ref('accounts'); // accounts, categories, channels, episodes
        const searchQuery = ref('');
        const searchVisible = ref(false);
        const selectedAccountTypeFilter = ref('all');
        const contentMode = ref('itv'); // itv, vod, series

        const accounts = ref([]);
        const categories = ref([]);
        const channels = ref([]);
        const episodes = ref([]);
        const bookmarks = ref([]);
        const bookmarkCategories = ref([]);
        const selectedBookmarkCategoryId = ref('__recently_played_bookmarks__');
        const watchingNowRows = ref([]);
        const selectedWatchingNowKey = ref('');
        const watchingNowTab = ref('series');
        const watchingNowDrilldown = ref(false);
        const watchingNowLoadedAt = ref(0);
        const WATCHING_NOW_CACHE_TTL_MS = 120000;
        let watchingNowFetchPromise = null;
        const watchingNowVodRows = ref([]);
        const selectedWatchingNowVodKey = ref('');
        const watchingNowVodLoadedAt = ref(0);
        const WATCHING_NOW_VOD_CACHE_TTL_MS = 120000;
        let watchingNowVodFetchPromise = null;
        const watchingNowVodLoading = ref(false);
        const selectedAccountId = ref(null);

        const currentContext = ref({accountId: null, categoryId: null, accountType: null});
        const currentChannel = ref(null);
        const playbackMode = ref('');
        const isPlaying = ref(false);
        const playbackError = ref('');
        const showOverlay = ref(false);
        const showBookmarkModal = ref(false);
        const playerExpanded = ref(false);
        const playerManuallyHidden = ref(false);
        const listLoading = ref(false);
        const listLoadingMessage = ref('Loading...');
        const draggedBookmarkId = ref('');
        const dragOverBookmarkId = ref('');
        const suppressNextBookmarkClick = ref(false);
        const bookmarkOverflowToggleRef = ref(null);
        const recentBookmarkHistory = ref([]);
        const bingeWatchLoading = ref(false);
        const activeBingeWatch = ref({token: '', currentEpisodeId: '', items: []});

        const playerInstance = ref(null);
        const mpegtsPlayer = ref(null);
        const videoPlayer = ref(null);
        const videoTracks = ref([]);
        const audioTracks = ref([]);
        const textTracks = ref([]);
        const selectedTextTrackId = ref('off');
        const repeatEnabled = ref(false);
        let repeatReloadInFlight = false;
        const isMuted = ref(false);
        const controlsVisible = ref(false);
        const isFullscreen = ref(false);
        const playbackLoading = ref(false);
        const playbackCurrentTime = ref(0);
        const playbackDuration = ref(0);
        const playbackProgressDragging = ref(false);
        const pendingPlaybackKey = ref('');
        const strategyOverride = ref('auto');
        let strategyOverrideKey = '';
        let sharedHeader = null;
        let playbackRequestId = 0;
        let playbackFetchController = null;
        const languageNames = typeof Intl !== 'undefined' && typeof Intl.DisplayNames === 'function'
            ? new Intl.DisplayNames([navigator.language || 'en'], {type: 'language'})
            : null;

        const thumbnailsEnabled = ref(true);

        const theme = ref('system');

        const contentModeLabels = {
            itv: 'Channels',
            vod: 'Movies',
            series: 'Series'
        };
        const SUPPORTED_MULTI_MODE_TYPES = new Set(['STALKER_PORTAL', 'XTREME_API']);
        const RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID = '__recently_played_bookmarks__';
        const RECENTLY_PLAYED_BOOKMARKS_CATEGORY_NAME = 'Recent';
        const RECENT_BOOKMARK_LIMIT = 15;
        const RECENT_BOOKMARK_STORAGE_KEY = 'uiptv_recently_played_bookmarks_v1';

        const createModeState = () => ({
            viewState: 'accounts',
            categoryId: null,
            categories: [],
            channels: [],
            episodes: [],
            selectedSeason: '',
            selectedEpisodeId: '',
            selectedEpisodeNum: '',
            selectedEpisodeName: '',
            selectedSeriesId: '',
            selectedSeriesCategoryId: '',
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

        const normalizeLanguageCode = (value) => {
            const normalized = String(value || '').trim().toLowerCase();
            if (!normalized || normalized === 'und' || normalized === 'unk' || normalized === 'unknown') {
                return '';
            }
            return normalized;
        };

        const humanizeToken = (value) => {
            const normalized = String(value || '').trim().replace(/[_-]+/g, ' ');
            if (!normalized) {
                return '';
            }
            return normalized.replace(/\b\w/g, (letter) => letter.toUpperCase());
        };

        const displayLanguage = (value) => {
            const normalized = normalizeLanguageCode(value);
            if (!normalized) {
                return 'Unknown';
            }
            if (!languageNames) {
                return humanizeToken(normalized);
            }
            try {
                return languageNames.of(normalized) || humanizeToken(normalized);
            } catch (_) {
                const base = normalized.split('-')[0];
                try {
                    return languageNames.of(base) || humanizeToken(normalized);
                } catch (_) {
                    return humanizeToken(normalized);
                }
            }
        };

        const normalizeTrackLabel = (value) => {
            const normalized = String(value || '').trim();
            const lower = normalized.toLowerCase();
            if (!normalized || lower === 'und' || lower === 'unk' || lower === 'unknown') {
                return '';
            }
            return normalized;
        };

        const formatVideoTrackLabel = (track) => {
            if (!track) return 'Auto';
            const height = Number(track.height || 0);
            const width = Number(track.width || 0);
            const bandwidth = Number(track.bandwidth || 0);
            const fps = Number(track.frameRate || 0);
            const parts = [];
            if (height >= 2160 || width >= 3840) {
                parts.push('4K');
            } else if (height > 0) {
                parts.push(`${height}p`);
            } else if (width > 0) {
                parts.push(`${width}px`);
            } else {
                parts.push('Adaptive');
            }
            if (fps >= 24) {
                parts.push(`${Math.round(fps)}fps`);
            }
            if (bandwidth > 0) {
                parts.push(`${(bandwidth / 1000000).toFixed(1)}Mbps`);
            }
            return parts.join(' ');
        };

        const formatAudioTrackLabel = (track, index = 0) => {
            const parts = [];
            const language = displayLanguage(track?.language);
            if (language) {
                parts.push(language);
            }
            const role = humanizeToken(track?.role || track?.roles?.[0]);
            if (role) {
                parts.push(role);
            }
            const label = normalizeTrackLabel(track?.label);
            if (label && label.toLowerCase() !== String(track?.language || '').trim().toLowerCase()) {
                parts.push(label);
            }
            return parts.join(' • ') || `Audio ${index + 1}`;
        };

        const formatTextTrackLabel = (track, index = 0) => {
            const parts = [];
            const language = displayLanguage(track?.language);
            if (language) {
                parts.push(language);
            }
            const kind = humanizeToken(track?.kind || track?.roles?.[0]);
            if (kind) {
                parts.push(kind);
            }
            const label = normalizeTrackLabel(track?.label);
            if (label && label.toLowerCase() !== String(track?.language || '').trim().toLowerCase()) {
                parts.push(label);
            }
            return parts.join(' • ') || `Subtitle ${index + 1}`;
        };

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

        const resolvePlaybackSeason = (episode) => {
            const resolved = resolveEpisodeSeason(episode);
            return resolved ? String(resolved) : '';
        };

        const resolvePlaybackEpisodeNumber = (episode) => {
            const resolved = resolveEpisodeNumber(episode);
            return resolved ? String(resolved) : '';
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

        const digitsOnly = (value) => String(value || '').replace(/[^0-9]/g, '');

        const findTargetSeriesEpisode = (episodeList, target = {}) => {
            const list = Array.isArray(episodeList) ? episodeList : [];
            if (!list.length) return null;
            const targetId = String(target.episodeId || '').trim();
            const targetSeason = digitsOnly(target.season);
            const targetEpisodeNum = digitsOnly(target.episodeNum);
            const targetName = normalizeTitle(cleanEpisodeTitle(target.episodeName || ''));

            const byId = targetId ? list.filter(ep => String(ep?.channelId || ep?.id || '').trim() === targetId) : [];
            if (byId.length) {
                const byIdSeasonEp = byId.find(ep => {
                    const season = digitsOnly(resolveEpisodeSeason(ep));
                    const epNo = digitsOnly(resolveEpisodeNumber(ep));
                    return (!targetSeason || season === targetSeason) && (!targetEpisodeNum || epNo === targetEpisodeNum);
                });
                if (byIdSeasonEp) return byIdSeasonEp;
                const byIdSeason = targetSeason ? byId.find(ep => digitsOnly(resolveEpisodeSeason(ep)) === targetSeason) : null;
                if (byIdSeason) return byIdSeason;
                return byId[0];
            }

            if (targetSeason && targetEpisodeNum) {
                const bySeasonEpisode = list.find(ep =>
                    digitsOnly(resolveEpisodeSeason(ep)) === targetSeason
                    && digitsOnly(resolveEpisodeNumber(ep)) === targetEpisodeNum
                );
                if (bySeasonEpisode) return bySeasonEpisode;
            }

            if (targetName) {
                const byName = list.find(ep => normalizeTitle(cleanEpisodeTitle(ep?.name || '')) === targetName);
                if (byName) return byName;
            }

            const watchedInSeason = targetSeason
                ? list.find(ep => ep?.watched && digitsOnly(resolveEpisodeSeason(ep)) === targetSeason)
                : null;
            if (watchedInSeason) return watchedInSeason;

            const watchedAny = list.find(ep => ep?.watched);
            if (watchedAny) return watchedAny;

            if (targetSeason) {
                const firstInSeason = list.find(ep => digitsOnly(resolveEpisodeSeason(ep)) === targetSeason);
                if (firstInSeason) return firstInSeason;
            }

            return list[0];
        };

        const resolvePreferredSeriesSeason = (episodeList, fallbackSeason = '', target = {}) => {
            const targetSeason = digitsOnly(target.season);
            if (targetSeason) return targetSeason;
            const watchedEpisode = (Array.isArray(episodeList) ? episodeList : []).find(ep => ep?.watched);
            const watchedSeason = resolveEpisodeSeason(watchedEpisode);
            if (watchedSeason) return watchedSeason;
            return String(fallbackSeason || '');
        };

        const getSeriesEpisodeAnchorId = (episode) => {
            const idPart = String(episode?.channelId || episode?.id || episode?.dbId || '').trim();
            const season = resolveEpisodeSeason(episode) || '0';
            const epNum = resolveEpisodeNumber(episode) || '0';
            return `series-ep-${idPart || `${season}-${epNum}`}-${season}-${epNum}`;
        };

        const scrollToSelectedWatchedEpisode = async () => {
            if (contentMode.value !== 'series' || viewState.value !== 'episodes') return;
            const modeState = getModeState('series');
            const targetEpisode = findTargetSeriesEpisode(filteredSeriesEpisodes.value, {
                episodeId: modeState.selectedEpisodeId,
                season: modeState.selectedSeason || selectedSeriesSeason.value,
                episodeNum: modeState.selectedEpisodeNum,
                episodeName: modeState.selectedEpisodeName
            });
            if (!targetEpisode) return;
            const anchorId = getSeriesEpisodeAnchorId(targetEpisode);
            if (!anchorId) return;
            for (let i = 0; i < 4; i++) {
                await nextTick();
            }
            requestAnimationFrame(() => {
                const node = document.getElementById(anchorId);
                if (!node) return;
                node.scrollIntoView({behavior: 'smooth', block: 'center', inline: 'nearest'});
            });
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
                .map(s => ({value: s, label: `Season ${s}`}));
        });

        const episodeSeasonOrDefault = (episode) => resolveEpisodeSeason(episode) || '1';

        const selectedBingeWatchSeason = computed(() => {
            const selected = String(selectedSeriesSeason.value || '').trim();
            if (selected) return selected;
            const firstTab = seriesSeasonTabs.value[0]?.value;
            if (firstTab) return String(firstTab);
            const firstEpisode = (episodes.value || [])[0];
            return episodeSeasonOrDefault(firstEpisode);
        });

        const bingeWatchSeasonEpisodes = computed(() => {
            const season = String(selectedBingeWatchSeason.value || '1');
            return (episodes.value || []).filter(episode => episodeSeasonOrDefault(episode) === season);
        });

        const bingeWatchButtonLabel = computed(() => {
            const seasonNumber = Number.parseInt(String(selectedBingeWatchSeason.value || '1').replace(/[^0-9]/g, ''), 10);
            const season = Number.isFinite(seasonNumber) ? Math.max(seasonNumber, 1) : 1;
            return `Binge Watch S${String(season).padStart(2, '0')}`;
        });

        const canBingeWatchSeason = computed(() => {
            const modeState = getModeState('series');
            return contentMode.value === 'series'
                && viewState.value === 'episodes'
                && !!currentContext.value.accountId
                && !!modeState.selectedSeriesId
                && bingeWatchSeasonEpisodes.value.length > 0;
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
                const day = new Intl.DateTimeFormat('en', {day: 'numeric', timeZone: 'UTC'}).format(d);
                const month = new Intl.DateTimeFormat('en', {month: 'short', timeZone: 'UTC'}).format(d);
                const year = new Intl.DateTimeFormat('en', {year: 'numeric', timeZone: 'UTC'}).format(d);
                return `${day} ${month} ${year}`;
            }
            const m = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
            if (m) {
                const tmp = new Date(`${m[1]}-${m[2]}-${m[3]}T00:00:00Z`);
                if (!Number.isNaN(tmp.getTime())) {
                    const day = new Intl.DateTimeFormat('en', {day: 'numeric', timeZone: 'UTC'}).format(tmp);
                    const month = new Intl.DateTimeFormat('en', {month: 'short', timeZone: 'UTC'}).format(tmp);
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
            if (activeTab.value === 'watchingNow') return 'Search series or movies...';
            return 'Search...';
        });
        const searchFieldVisible = computed(() =>
            searchVisible.value || !!String(searchQuery.value || '').trim()
        );

        const clearSearch = (hide = true) => {
            searchQuery.value = '';
            if (hide) {
                searchVisible.value = false;
            }
        };

        const formatAccountType = (type) => {
            const normalized = String(type || '').toUpperCase();
            if (normalized === 'STALKER_PORTAL') return 'Stalker account';
            if (normalized === 'XTREME_API') return 'Xtreme account';
            if (normalized === 'M3U8_LOCAL') return 'M3U local account';
            if (normalized === 'M3U8_URL') return 'M3U account';
            return normalized ? normalized.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase()) : 'Account';
        };

        const currentAccount = computed(() => {
            const accountId = String(selectedAccountId.value || currentContext.value.accountId || '');
            return (accounts.value || []).find(account => String(account?.dbId || '') === accountId) || null;
        });

        const currentAccountName = computed(() => currentAccount.value?.accountName || 'Select an account');
        const currentAccountTypeLabel = computed(() => formatAccountType(currentAccount.value?.type || currentContext.value.accountType || ''));

        const currentCategoryTitle = computed(() => {
            const categoryId = String(currentContext.value.categoryId || '');
            if (!categoryId) return contentModeLabels[contentMode.value] || 'Content';
            const category = (categories.value || []).find(item =>
                String(item?.dbId || item?.categoryId || '') === categoryId
            );
            return category?.title || contentModeLabels[contentMode.value] || 'Content';
        });

        const contentPanelTitle = computed(() => {
            if (viewState.value === 'vodDetail') return 'Movie Detail';
            if (viewState.value === 'episodes') return 'Episodes';
            return contentModeLabels[contentMode.value] || 'Content';
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

        const DEFAULT_PIN_SVG = {
            viewBox: '0 0 320 320',
            stemPath: 'm 289.99122,309.99418 c -0.66028,0.58344 -50.08221,-43.19021 -52.50936,-45.29992 -2.42734,-2.10956 -51.06934,-43.57426 -52.83626,-46.26739 -1.76673,-2.69328 13.04928,-12.78624 13.70956,-13.36969 0.66024,-0.58341 12.52054,-14.06148 14.94736,-11.95215 2.42733,2.10957 37.03325,55.97684 38.80018,58.66996 1.76673,2.69328 38.54876,57.6358 37.88852,58.21919 z',
            headPath: 'm 56.34936,106.22036 c 20.30938,0.88278 45.68909,32.12704 73.173,75.95489 18.76942,29.93108 45.31357,11.58173 54.19751,2.7927 8.31501,-8.2259 25.42173,-32.179 -3.72915,-51.99008 -42.68539,-29.00919 -72.93354,-55.50764 -73.173,-75.954905 L 81.58356,81.621661 Z',
            stemFill: '#cad2d2',
            headFill: '#e30000'
        };

        const resolvePinSvg = (account) => ({
            viewBox: account?.pinSvgViewBox || DEFAULT_PIN_SVG.viewBox,
            stemPath: account?.pinSvgStemPath || DEFAULT_PIN_SVG.stemPath,
            headPath: account?.pinSvgHeadPath || DEFAULT_PIN_SVG.headPath,
            stemFill: account?.pinSvgStemFill || DEFAULT_PIN_SVG.stemFill,
            headFill: account?.pinSvgHeadFill || DEFAULT_PIN_SVG.headFill
        });

        const resolvePinColor = () => {
            let activeTheme = theme.value;
            if (activeTheme === 'system') {
                const prefersLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches;
                activeTheme = prefersLight ? 'light' : 'dark';
            }
            return activeTheme === 'dark' ? '#e6edf7' : '#1f2937';
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
                return String(a?.accountName || '').localeCompare(String(b?.accountName || ''), undefined, {sensitivity: 'base'});
            });
            return list;
        });

        const matchesAccountTypeFilter = (account, filter) => {
            const type = String(account?.type || '').toUpperCase();
            if (filter === 'stalker') return type === 'STALKER_PORTAL';
            if (filter === 'xtreme') return type === 'XTREME_API';
            if (filter === 'm3u') return type === 'M3U8_LOCAL' || type === 'M3U8_URL';
            return true;
        };

        const filteredAccounts = computed(() => {
            const q = searchQuery.value.toLowerCase();
            const list = sortedAccounts.value.filter(account => matchesAccountTypeFilter(account, selectedAccountTypeFilter.value));
            if (!q) return list;
            return list.filter(a => (a.accountName || '').toLowerCase().includes(q));
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

        const getRecentBookmarkKey = (bookmark = {}) => {
            const bookmarkId = String(bookmark.dbId || bookmark.bookmarkId || '').trim();
            if (bookmarkId) return bookmarkId;
            return [
                String(bookmark.accountName || '').trim(),
                normalizePlaybackMode(bookmark.accountAction || bookmark.mode || 'itv'),
                String(bookmark.channelId || bookmark.id || '').trim(),
                normalizePlaybackName(bookmark.channelName || bookmark.name || '')
            ].join('|');
        };

        const sanitizeRecentBookmarkEntry = (entry = {}) => {
            const key = String(entry.key || entry.bookmarkId || '').trim();
            if (!key) return null;
            return {
                key,
                playCount: Math.max(1, Number.parseInt(String(entry.playCount || 1), 10) || 1),
                lastPlayedAt: Math.max(0, Number.parseInt(String(entry.lastPlayedAt || 0), 10) || 0),
                channelName: String(entry.channelName || '').trim(),
                accountName: String(entry.accountName || '').trim(),
                mode: normalizePlaybackMode(entry.mode || 'itv')
            };
        };

        const sortRecentBookmarkEntries = (entries) => {
            return [...(entries || [])].sort((a, b) => {
                const byCount = Number(b.playCount || 0) - Number(a.playCount || 0);
                if (byCount !== 0) return byCount;
                const byTime = Number(b.lastPlayedAt || 0) - Number(a.lastPlayedAt || 0);
                if (byTime !== 0) return byTime;
                return String(a.channelName || '').localeCompare(String(b.channelName || ''), undefined, {sensitivity: 'base'});
            });
        };

        const loadRecentBookmarkHistory = () => {
            try {
                const raw = localStorage.getItem(RECENT_BOOKMARK_STORAGE_KEY);
                const parsed = raw ? JSON.parse(raw) : [];
                if (!Array.isArray(parsed)) {
                    recentBookmarkHistory.value = [];
                    return;
                }
                recentBookmarkHistory.value = sortRecentBookmarkEntries(parsed
                    .map(sanitizeRecentBookmarkEntry)
                    .filter(Boolean))
                    .slice(0, RECENT_BOOKMARK_LIMIT);
            } catch (_) {
                recentBookmarkHistory.value = [];
            }
        };

        const saveRecentBookmarkHistory = (items) => {
            const normalized = sortRecentBookmarkEntries((items || [])
                .map(sanitizeRecentBookmarkEntry)
                .filter(Boolean))
                .slice(0, RECENT_BOOKMARK_LIMIT);
            recentBookmarkHistory.value = normalized;
            try {
                localStorage.setItem(RECENT_BOOKMARK_STORAGE_KEY, JSON.stringify(normalized));
            } catch (_) {
                // Browsers can deny storage in private sessions; the in-memory list still works for this tab.
            }
        };

        const recordRecentlyPlayedBookmark = (bookmark) => {
            const key = getRecentBookmarkKey(bookmark);
            if (!key) return;
            const existing = recentBookmarkHistory.value.find(entry => String(entry.key || '') === key);
            const nextEntry = {
                key,
                playCount: (Number(existing?.playCount || 0) || 0) + 1,
                lastPlayedAt: Date.now(),
                channelName: String(bookmark?.channelName || bookmark?.name || '').trim(),
                accountName: String(bookmark?.accountName || '').trim(),
                mode: normalizePlaybackMode(bookmark?.accountAction || bookmark?.mode || 'itv')
            };
            saveRecentBookmarkHistory([
                nextEntry,
                ...recentBookmarkHistory.value.filter(entry => String(entry.key || '') !== key)
            ]);
        };

        const removeRecentlyPlayedBookmark = (bookmark) => {
            const key = getRecentBookmarkKey(bookmark);
            if (!key) return;
            saveRecentBookmarkHistory(recentBookmarkHistory.value.filter(entry => String(entry.key || '') !== key));
        };

        const recentBookmarkEntryMap = computed(() => {
            const map = new Map();
            for (const entry of recentBookmarkHistory.value || []) {
                map.set(String(entry.key || ''), entry);
            }
            return map;
        });

        const recentlyPlayedBookmarks = computed(() => {
            return (bookmarks.value || [])
                .map(bookmark => {
                    const entry = recentBookmarkEntryMap.value.get(getRecentBookmarkKey(bookmark));
                    return entry ? {...bookmark, recentPlayCount: entry.playCount, recentLastPlayedAt: entry.lastPlayedAt} : null;
                })
                .filter(Boolean)
                .sort((a, b) => {
                    const byCount = Number(b.recentPlayCount || 0) - Number(a.recentPlayCount || 0);
                    if (byCount !== 0) return byCount;
                    const byTime = Number(b.recentLastPlayedAt || 0) - Number(a.recentLastPlayedAt || 0);
                    if (byTime !== 0) return byTime;
                    return String(a.channelName || '').localeCompare(String(b.channelName || ''), undefined, {sensitivity: 'base'});
                })
                .slice(0, RECENT_BOOKMARK_LIMIT);
        });

        const matchesBookmarkSearch = (bookmark, query) => {
            if (!query) return true;
            const q = String(query || '').toLowerCase();
            return (bookmark.channelName || '').toLowerCase().includes(q)
                || (bookmark.accountName || '').toLowerCase().includes(q);
        };

        const isRecentlyPlayedBookmarksSelected = computed(() =>
            String(selectedBookmarkCategoryId.value || '') === RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID
        );

        const bookmarkCategoryTabs = computed(() => {
            const tabs = [
                {
                    id: RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID,
                    name: RECENTLY_PLAYED_BOOKMARKS_CATEGORY_NAME,
                    itemCount: recentlyPlayedBookmarks.value.length
                },
                {id: '', name: 'All'}
            ];
            for (const category of (bookmarkCategories.value || [])) {
                const id = String(category?.id || '').trim();
                const name = String(normalizeDisplayText(category?.name || '') || '').trim();
                if (!id || !name) continue;
                tabs.push({id, name});
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
            return activeTab.value === 'bookmarks'
                && !isRecentlyPlayedBookmarksSelected.value
                && !String(searchQuery.value || '').trim();
        });

        const filteredBookmarks = computed(() => {
            const selectedCategoryId = String(selectedBookmarkCategoryId.value || '');
            const byCategory = isRecentlyPlayedBookmarksSelected.value
                ? recentlyPlayedBookmarks.value
                : selectedCategoryId
                ? bookmarks.value.filter(b => String(b?.categoryId || '') === selectedCategoryId)
                : bookmarks.value;
            const q = searchQuery.value.toLowerCase();
            return byCategory.filter(bookmark => matchesBookmarkSearch(bookmark, q));
        });

        const bookmarkCardTitle = computed(() => {
            if (isRecentlyPlayedBookmarksSelected.value) return 'Play recently watched bookmark';
            return canReorderBookmarks.value ? 'Drag to reorder' : 'Clear search to reorder bookmarks';
        });

        const bookmarkEmptyTitle = computed(() => {
            if (isRecentlyPlayedBookmarksSelected.value) return 'No recently played channels';
            return 'No bookmarks';
        });

        const bookmarkEmptyDetail = computed(() => {
            if (isRecentlyPlayedBookmarksSelected.value) return 'Bookmarked channels appear here after playback starts.';
            return 'Save channels from the Channels screen.';
        });

        const bookmarkStatusText = computed(() => {
            if (isRecentlyPlayedBookmarksSelected.value) {
                return `${filteredBookmarks.value.length} recently played shown`;
            }
            return `${filteredBookmarks.value.length} favorites shown`;
        });

        const filteredWatchingNowRows = computed(() => {
            const q = String(searchQuery.value || '').trim().toLowerCase();
            if (!q) return watchingNowRows.value;
            return (watchingNowRows.value || []).filter(row => {
                if ((row?.seriesTitle || '').toLowerCase().includes(q)) return true;
                if ((row?.accountName || '').toLowerCase().includes(q)) return true;
                return false;
            });
        });

        const filteredWatchingNowVodRows = computed(() => {
            const q = String(searchQuery.value || '').trim().toLowerCase();
            if (!q) return watchingNowVodRows.value;
            return (watchingNowVodRows.value || []).filter(row => {
                if ((row?.vodName || '').toLowerCase().includes(q)) return true;
                if ((row?.accountName || '').toLowerCase().includes(q)) return true;
                return false;
            });
        });

        const normalizeDisplayText = (value) =>
            window.UIPTVPlaybackUtils?.normalizeDisplayText
                ? window.UIPTVPlaybackUtils.normalizeDisplayText(value)
                : String(value ?? '');
        const getVodPlot = (item) => {
            if (!item) return '';
            const candidate = item.plot || item.description || item.overview || '';
            const normalized = String(normalizeDisplayText(candidate) || '').trim();
            return normalized || String(candidate || '').trim();
        };
        const APP_TITLE = 'UIPTV';
        const currentChannelName = computed(() => currentChannel.value ? normalizeDisplayText(currentChannel.value.name) : '');
        const currentChannelDebugTitle = computed(() => {
            const name = String(normalizeDisplayText(currentChannelName.value) || '').trim();
            const mode = String(playbackMode.value || '').trim();
            if (!name) return '';
            return mode ? `${name} [${mode}]` : name;
        });
        const hasPlayerContent = computed(() =>
            !!currentChannel.value || !!isPlaying.value || !!playbackLoading.value || !!playbackError.value
        );
        const playerPanelVisible = computed(() => hasPlayerContent.value && !playerManuallyHidden.value);
        const playbackSeekable = computed(() => Number.isFinite(playbackDuration.value) && playbackDuration.value > 0);
        const widePlayerProgressVisible = computed(() => playerExpanded.value && isPlaying.value);

        const setBrowserTitle = () => {
            const channelTitle = String(currentChannelDebugTitle.value || '').trim();
            document.title = channelTitle ? `${channelTitle} | ${APP_TITLE}` : APP_TITLE;
        };

        const playbackUtils = window.UIPTVPlaybackUtils;
        const isTsLikeUrl = (url, manifestType = '') => playbackUtils.isTsLikeUrl(url, manifestType);
        const canUseMpegts = () => playbackUtils.canUseMpegts();
        const resolvePlaybackModeLabel = (url, engine = '') => playbackUtils.resolvePlaybackModeLabel(url, engine);
        const buildProxyStreamUrl = (url) => playbackUtils.buildProxyStreamUrl(url);
        const resolveMpegTsPlaybackUrl = (url) => playbackUtils.resolveMpegTsPlaybackUrl(url);
        const describeMpegTsFailure = (error) => playbackUtils.describeMpegTsFailure(error);
        const isBrowserUnsupportedMediaError = (error) => playbackUtils.isBrowserUnsupportedMediaError(error);
        const describeUnsupportedHlsManifest = (manifest) => playbackUtils?.describeUnsupportedHlsManifest?.(manifest) || '';
        const isMaxQualityEnabled = () => playbackUtils?.getShakaMaxQuality?.() === true;
        const setMaxQualityEnabled = (enabled) => playbackUtils?.setShakaMaxQuality?.(!!enabled);
        const notifyPlayerClosed = (reason = 'stop') => {
            if (!playbackUtils || typeof playbackUtils.notifyPlayerClose !== 'function') {
                return;
            }
            const channel = currentChannel.value || {};
            playbackUtils.notifyPlayerClose({
                reason,
                mode: channel.mode || '',
                channelId: channel.channelId || channel.id || '',
                accountId: channel.accountId || ''
            });
        };
        const resetPlaybackDefaults = () => {
            strategyOverride.value = 'auto';
            strategyOverrideKey = '';
            syncSharedHeader();
            syncSharedMenus();
        };

        const resolveChannelIdentity = (channel) => {
            if (!channel) return '';
            return String(channel.channelId || channel.id || channel.dbId || '').trim();
        };

        const resolveAccountName = (accountId) => {
            const account = accounts.value.find(a => String(a.dbId) === String(accountId));
            return account?.accountName || '';
        };

        const resolveAccountId = (accountName) => {
            const account = accounts.value.find(a => String(a.accountName || '') === String(accountName || ''));
            return account?.dbId || '';
        };

        const normalizePlaybackMode = (value, fallback = '') => String(value || fallback || '').trim().toLowerCase();
        const normalizePlaybackName = (value) => String(normalizeDisplayText(value) || '').trim().toLowerCase();

        const resolveDisplayName = (item = {}) => {
            const id = String(item.channelId || item.id || item.dbId || '').trim();
            const candidates = [
                item.name,
                item.seriesTitle,
                item.seriesName,
                item.title,
                item.channelName,
                item.displayName
            ];
            let name = '';
            for (const candidate of candidates) {
                const normalized = String(normalizeDisplayText(candidate) || '').trim();
                if (normalized) {
                    name = normalized;
                    break;
                }
            }
            if (name && id) {
                const idPair = `${id}:${id}`;
                if (name === id || name === idPair) {
                    for (const candidate of [item.seriesTitle, item.seriesName, item.title, item.channelName]) {
                        const normalized = String(normalizeDisplayText(candidate) || '').trim();
                        if (normalized && normalized !== name) {
                            name = normalized;
                            break;
                        }
                    }
                }
            }
            return name;
        };

        const matchesSeriesProgress = (target = {}, current = {}) => {
            const targetSeason = digitsOnly(target.season || resolvePlaybackSeason(target));
            const currentSeason = digitsOnly(current.season || '');
            if (targetSeason && currentSeason && targetSeason !== currentSeason) return false;

            const targetEpisode = digitsOnly(target.episodeNum || resolvePlaybackEpisodeNumber(target));
            const currentEpisode = digitsOnly(current.episodeNum || '');
            return !(targetEpisode && currentEpisode && targetEpisode !== currentEpisode);
        };

        const matchesCurrentPlayback = ({
                                            id = '',
                                            accountId = '',
                                            accountName = '',
                                            mode = '',
                                            name = '',
                                            season = '',
                                            episodeNum = '',
                                            bookmarkId = ''
                                        } = {}) => {
            const current = currentChannel.value;
            if (!current) return false;

            const normalizedBookmarkId = String(bookmarkId || '').trim();
            if (normalizedBookmarkId && normalizedBookmarkId === String(current.bookmarkId || '').trim()) {
                return true;
            }

            const targetMode = normalizePlaybackMode(mode);
            const currentMode = normalizePlaybackMode(current.mode);
            if (targetMode && currentMode && targetMode !== currentMode) return false;

            const targetAccountId = String(accountId || '').trim();
            const currentAccountId = String(current.accountId || '').trim();
            if (targetAccountId && currentAccountId && targetAccountId !== currentAccountId) return false;

            const targetAccountName = String(accountName || '').trim();
            const currentAccountName = String(current.accountName || resolveAccountName(current.accountId) || '').trim();
            if (targetAccountName && currentAccountName && targetAccountName !== currentAccountName) return false;

            const targetId = String(id || '').trim();
            const currentId = resolveChannelIdentity(current);
            if (targetId && currentId && targetId !== currentId) return false;

            const targetName = normalizePlaybackName(name);
            const currentName = normalizePlaybackName(current.name || current.channelName || '');
            const hasNameMatch = !!(targetName && currentName && targetName === currentName);

            if (currentMode === 'series' && !matchesSeriesProgress({season, episodeNum}, current)) {
                return false;
            }

            const hasIdMatch = !!(targetId && currentId && targetId === currentId);
            return !!(normalizedBookmarkId || hasIdMatch || hasNameMatch);
        };

        const buildPlaybackTargetKey = ({
                                            id = '',
                                            accountId = '',
                                            accountName = '',
                                            mode = '',
                                            season = '',
                                            episodeNum = '',
                                            bookmarkId = ''
                                        } = {}) => {
            return [
                normalizePlaybackMode(mode),
                String(accountId || '').trim(),
                String(accountName || '').trim(),
                String(bookmarkId || '').trim(),
                String(id || '').trim(),
                digitsOnly(season),
                digitsOnly(episodeNum)
            ].join('|');
        };

        const isActiveChannel = (channel) => matchesCurrentPlayback({
            id: resolveChannelIdentity(channel),
            accountId: channel?.accountId || currentContext.value.accountId || '',
            mode: channel?.mode || contentMode.value,
            name: channel?.name || channel?.channelName || '',
            season: resolvePlaybackSeason(channel),
            episodeNum: resolvePlaybackEpisodeNumber(channel)
        });

        const isActiveBookmark = (bookmark) => matchesCurrentPlayback({
            id: bookmark?.channelId || bookmark?.id || '',
            accountId: resolveAccountId(bookmark?.accountName),
            accountName: bookmark?.accountName || '',
            mode: bookmark?.accountAction || bookmark?.mode || 'itv',
            name: bookmark?.channelName || bookmark?.name || '',
            bookmarkId: bookmark?.dbId || ''
        });

        const isActiveWatchingNowRow = (row) => {
            if (!row) return false;
            if (matchesCurrentPlayback({
                id: row.episodeId || '',
                accountId: row.accountId || '',
                accountName: row.accountName || '',
                mode: 'series',
                name: row.episodeName || row.seriesTitle || '',
                season: row.season || '',
                episodeNum: row.episodeNum || ''
            })) {
                return true;
            }

            const current = currentChannel.value;
            if (!current || normalizePlaybackMode(current.mode) !== 'series') return false;
            const targetAccountId = String(row.accountId || '').trim();
            const currentAccountId = String(current.accountId || '').trim();
            if (targetAccountId && currentAccountId && targetAccountId !== currentAccountId) return false;

            const selectedSeriesId = String(getModeState('series').selectedSeriesId || '').trim();
            return !!selectedSeriesId && selectedSeriesId === String(row.seriesId || '').trim();
        };

        const isActiveWatchingNowVodRow = (row) => {
            if (!row) return false;
            return matchesCurrentPlayback({
                id: row.vodId || row?.playItem?.channelId || '',
                accountId: row.accountId || '',
                accountName: row.accountName || '',
                mode: 'vod',
                name: row.vodName || row?.playItem?.name || ''
            });
        };

        const isPendingPlaybackTarget = (target) => {
            if (!playbackLoading.value) return false;
            return pendingPlaybackKey.value === buildPlaybackTargetKey(target);
        };

        const isDisabledChannel = (channel) => isPendingPlaybackTarget({
            id: resolveChannelIdentity(channel),
            accountId: channel?.accountId || currentContext.value.accountId || '',
            mode: channel?.mode || contentMode.value,
            season: resolvePlaybackSeason(channel),
            episodeNum: resolvePlaybackEpisodeNumber(channel)
        });

        const isDisabledBookmark = (bookmark) => isPendingPlaybackTarget({
            id: bookmark?.channelId || bookmark?.id || '',
            accountId: resolveAccountId(bookmark?.accountName),
            accountName: bookmark?.accountName || '',
            mode: bookmark?.accountAction || bookmark?.mode || 'itv',
            bookmarkId: bookmark?.dbId || ''
        });

        const resolveLogoUrl = (logo) => {
            if (!thumbnailsEnabled.value) return '';
            const raw = String(logo || '').trim();
            if (!raw) return '';
            const lower = raw.toLowerCase();
            if (lower === 'null' || lower === 'undefined' || lower === 'none' || lower === 'n/a' || lower === 'na' || lower === '-') {
                return '';
            }
            if (/^(data:|blob:|https?:\/\/|file:)/i.test(raw)) return raw;
            if (raw.startsWith('//')) return `${window.location.protocol}${raw}`;
            if (raw.startsWith('/')) return `${window.location.origin}${raw}`;
            return `${window.location.origin}/${raw.replace(/^\.?\//, '')}`;
        };

        const fallbackInitials = (label, fallback = 'TV') => {
            const clean = normalizeDisplayText(label).replace(/[^\p{L}\p{N}\s]+/gu, ' ').trim();
            const words = clean.split(/\s+/).filter(Boolean);
            if (words.length >= 2) {
                return words.slice(0, 2).map(word => word.charAt(0).toUpperCase()).join('');
            }
            if (words.length === 1) {
                return words[0].slice(0, 2).toUpperCase();
            }
            return String(fallback || 'TV').slice(0, 3).toUpperCase();
        };

        const normalizeChannel = (channel = {}) => ({
            ...channel,
            name: resolveDisplayName(channel) || normalizeDisplayText(channel.name) || channel.name || '',
            channelName: normalizeDisplayText(channel.channelName) || channel.channelName || '',
            logo: resolveLogoUrl(channel.logo),
            watched: channel.watched === true
                || channel.watched === 1
                || String(channel.watched || '').toLowerCase() === 'true'
                || String(channel.watched || '') === '1'
        });

        const normalizeBookmark = (bookmark = {}) => ({
            ...bookmark,
            channelName: normalizeDisplayText(bookmark.channelName) || bookmark.channelName || '',
            accountName: normalizeDisplayText(bookmark.accountName) || bookmark.accountName || '',
            logo: resolveLogoUrl(bookmark.logo)
        });

        const loadConfig = async () => {
            try {
                const response = await fetch(window.location.origin + '/config');
                const config = await response.json();
                thumbnailsEnabled.value = config?.enableThumbnails !== false;
            } catch (e) {
                thumbnailsEnabled.value = true;
            }
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

        const resolveCurrentSeriesId = (channel) => {
            if (!channel) return '';
            return String(
                channel.seriesParentId
                || channel.seriesId
                || channel.parentSeriesId
                || getModeState('series')?.selectedSeriesId
                || ''
            ).trim();
        };

        const resolveCurrentVodId = (channel) => {
            if (!channel) return '';
            return String(channel.vodId || channel.channelId || channel.id || '').trim();
        };

        const isCurrentSeriesInWatchingNow = computed(() => {
            const current = currentChannel.value;
            if (!current || normalizePlaybackMode(current.mode) !== 'series') return false;
            const seriesId = resolveCurrentSeriesId(current);
            if (!seriesId) return false;
            const accountId = String(current.accountId || '').trim();
            return (watchingNowRows.value || []).some(row => {
                if (String(row?.seriesId || '').trim() !== seriesId) return false;
                return !accountId || String(row?.accountId || '').trim() === accountId;
            });
        });

        const isCurrentVodInWatchingNow = computed(() => {
            const current = currentChannel.value;
            if (!current || normalizePlaybackMode(current.mode) !== 'vod') return false;
            const vodId = resolveCurrentVodId(current);
            if (!vodId) return false;
            const accountId = String(current.accountId || '').trim();
            return (watchingNowVodRows.value || []).some(row => {
                if (String(row?.vodId || '').trim() !== vodId) return false;
                return !accountId || String(row?.accountId || '').trim() === accountId;
            });
        });

        const isCurrentFavorite = computed(() => {
            if (!currentChannel.value) return false;
            const mode = normalizePlaybackMode(currentChannel.value?.mode || contentMode.value);
            if (mode === 'series') {
                return isCurrentSeriesInWatchingNow.value;
            }
            if (mode === 'vod') {
                return isCurrentVodInWatchingNow.value;
            }
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

        const isAllCategory = (category) => {
            const title = String(category?.title || '').trim().toLowerCase();
            const dbId = String(category?.dbId || '').trim().toLowerCase();
            const categoryId = String(category?.categoryId || '').trim().toLowerCase();
            return title === 'all' || dbId === 'all' || categoryId === 'all';
        };

        const withSyntheticAllCategory = (items, accountType) => {
            const list = Array.isArray(items) ? [...items] : [];
            const normalizedType = String(accountType || '').toUpperCase();
            const isStalkerOrXtreme = normalizedType === 'STALKER_PORTAL' || normalizedType === 'XTREME_API';
            const hasAll = list.some(isAllCategory);
            if (hasAll) return list;
            if (isStalkerOrXtreme && list.length < 2) return list;
            return [{dbId: 'all', categoryId: 'all', title: 'All'}, ...list];
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
                clearSearch();
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
                modeState.selectedEpisodeId = '';
                modeState.selectedEpisodeNum = '';
                modeState.selectedEpisodeName = '';
                modeState.selectedSeriesId = '';
                modeState.selectedSeriesCategoryId = '';
                modeState.detail = null;
                if (contentMode.value === 'series') {
                    selectedSeriesSeason.value = '';
                    seriesDetail.value = null;
                    seriesDetailLoading.value = false;
                } else if (contentMode.value === 'vod') {
                    vodDetail.value = null;
                    vodDetailLoading.value = false;
                }
                clearSearch();
                if (categories.value.length === 1 && isAllCategory(categories.value[0])) {
                    await loadChannels(categories.value[0].dbId || categories.value[0].categoryId || 'all', true);
                }
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
                clearSearch();
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
                modeState.selectedEpisodeId = '';
                modeState.selectedEpisodeNum = '';
                modeState.selectedEpisodeName = '';
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
                clearSearch();
            } catch (e) {
                console.error('Failed to load channels', e);
            } finally {
                listLoading.value = false;
            }
        };

        const loadSeriesEpisodes = async (seriesId, seriesCategoryId = '') => {
            viewState.value = 'episodes';
            episodes.value = [];
            const modeState = getModeState('series');
            const effectiveCategoryId = seriesCategoryId || modeState.selectedSeriesCategoryId || currentContext.value.categoryId || '';
            try {
                listLoading.value = true;
                listLoadingMessage.value = 'Loading episodes...';
                const response = await fetch(
                    `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${currentContext.value.accountId}&categoryId=${encodeURIComponent(effectiveCategoryId)}`
                );
                episodes.value = (await response.json()).map(normalizeChannel);
                viewState.value = 'episodes';
                episodes.value = enrichEpisodesFromMeta(episodes.value, modeState.detail || null);
                modeState.episodes = [...episodes.value];
                modeState.viewState = 'episodes';
                clearSearch();
                selectedSeriesSeason.value = resolvePreferredSeriesSeason(episodes.value, modeState.selectedSeason || '', {
                    season: modeState.selectedSeason,
                    episodeId: modeState.selectedEpisodeId,
                    episodeNum: modeState.selectedEpisodeNum,
                    episodeName: modeState.selectedEpisodeName
                });
                ensureSeriesSeasonSelected();
                await scrollToSelectedWatchedEpisode();
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
                clearSearch();
                selectedSeriesSeason.value = resolvePreferredSeriesSeason(episodes.value, modeState.selectedSeason || '', {
                    season: modeState.selectedSeason,
                    episodeId: modeState.selectedEpisodeId,
                    episodeNum: modeState.selectedEpisodeNum,
                    episodeName: modeState.selectedEpisodeName
                });
                ensureSeriesSeasonSelected();
                await scrollToSelectedWatchedEpisode();
            } catch (e) {
                console.error('Failed to load series children', e);
            } finally {
                listLoading.value = false;
            }
        };

        const onlyDigits = (value) => String(value || '').replace(/[^0-9]/g, '');

        const isEpisodeMatch = (watched, candidate) => {
            const watchedId = String(watched?.channelId || watched?.id || '');
            const candidateId = String(candidate?.channelId || candidate?.id || '');
            if (!watchedId || !candidateId || watchedId !== candidateId) {
                return false;
            }
            const watchedSeason = onlyDigits(watched?.season);
            const candidateSeason = onlyDigits(resolvePlaybackSeason(candidate));
            if (watchedSeason && (!candidateSeason || watchedSeason !== candidateSeason)) {
                return false;
            }
            const watchedEpNum = onlyDigits(watched?.episodeNum);
            const candidateEpNum = onlyDigits(resolvePlaybackEpisodeNumber(candidate));
            return !(watchedEpNum && (!candidateEpNum || watchedEpNum !== candidateEpNum));
        };

        const markCurrentSeriesEpisodeWatchedLocally = () => {
            const modeState = getModeState('series');
            if (contentMode.value !== 'series' || !currentChannel.value || !Array.isArray(episodes.value) || episodes.value.length === 0) {
                return;
            }
            const watched = {
                channelId: currentChannel.value.channelId || currentChannel.value.id || '',
                season: currentChannel.value.season || '',
                episodeNum: currentChannel.value.episodeNum || ''
            };
            episodes.value = episodes.value.map(ep => ({...ep, watched: isEpisodeMatch(watched, ep)}));
            modeState.episodes = [...episodes.value];
        };

        const refreshSeriesEpisodeWatchState = async () => {
            const modeState = getModeState('series');
            const seriesId = String(modeState?.selectedSeriesId || '');
            if (!seriesId || !currentContext.value.accountId) {
                return;
            }
            const categoryId = String(modeState?.selectedSeriesCategoryId || currentContext.value.categoryId || '');
            try {
                let response;
                if (String(currentContext.value.accountType || '').toUpperCase() === 'XTREME_API') {
                    response = await fetch(
                        `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${currentContext.value.accountId}&categoryId=${encodeURIComponent(categoryId)}`
                    );
                } else {
                    response = await fetch(
                        `${window.location.origin}/channels?categoryId=${encodeURIComponent(categoryId)}&accountId=${currentContext.value.accountId}&mode=series&movieId=${encodeURIComponent(seriesId)}`
                    );
                }
                const refreshedEpisodes = (await response.json()).map(normalizeChannel);
                episodes.value = enrichEpisodesFromMeta(refreshedEpisodes, modeState.detail || null);
                modeState.episodes = [...episodes.value];
                if (activeTab.value === 'watchingNow') {
                    await loadWatchingNow({force: true, background: true});
                }
            } catch (e) {
                console.warn('Failed to refresh series watch state', e);
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
            clearSearch();
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
                    modeState.detail = {...detail};
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

        const BOOKMARK_PAGE_SIZE = 25;
        const bookmarkWatchUtils = window.UIPTVBookmarkWatchUtils;

        const loadBookmarks = async () => {
            listLoading.value = true;
            listLoadingMessage.value = 'Loading bookmarks...';
            await bookmarkWatchUtils.loadBookmarksPaged({
                origin: window.location.origin,
                pageSize: BOOKMARK_PAGE_SIZE,
                normalize: (item) => normalizeBookmark(item),
                onReset: () => {
                    bookmarks.value = [];
                },
                onBatch: (batch) => {
                    bookmarks.value = [...bookmarks.value, ...batch];
                },
                onAfterBatch: () => {
                    ensureSelectedBookmarkCategory();
                },
                nextTick,
                onError: (e) => console.error('Failed to load bookmarks', e),
                onDone: () => {
                    listLoading.value = false;
                }
            });
        };

        const loadBookmarkCategories = async () => {
            try {
                bookmarkCategories.value = await bookmarkWatchUtils.loadBookmarkCategories(window.location.origin);
                ensureSelectedBookmarkCategory();
            } catch (e) {
                console.error('Failed to load bookmark categories', e);
            }
        };

        const normalizeWatchingNowRow = (row = {}) => ({
            ...row,
            key: String(row.key || `${row.accountId || ''}|${row.seriesId || ''}`),
            categoryDbId: String(row.categoryDbId || ''),
            episodeId: String(row.episodeId || ''),
            episodeName: String(row.episodeName || ''),
            season: String(row.season || ''),
            episodeNum: String(row.episodeNum || ''),
            seriesPoster: resolveLogoUrl(row.seriesPoster),
            episodes: Array.isArray(row.episodes) ? row.episodes.map(normalizeChannel) : []
        });

        const normalizeWatchingNowVodRow = (row = {}) => bookmarkWatchUtils.normalizeWatchingNowVodRow(
            row,
            resolveLogoUrl,
            normalizeChannel
        );

        const loadWatchingNow = async ({force = false, background = false} = {}) => {
            const hasRows = Array.isArray(watchingNowRows.value) && watchingNowRows.value.length > 0;
            const cacheAgeMs = Date.now() - Number(watchingNowLoadedAt.value || 0);
            const isFresh = hasRows && cacheAgeMs < WATCHING_NOW_CACHE_TTL_MS;
            if (!force && isFresh) {
                return watchingNowRows.value;
            }
            if (watchingNowFetchPromise) {
                return await watchingNowFetchPromise;
            }

            watchingNowFetchPromise = (async () => {
                try {
                    if (!background) {
                        listLoading.value = true;
                        listLoadingMessage.value = 'Loading Watching Now...';
                    }
                    const response = await fetch(`${window.location.origin}/watchingNow`);
                    watchingNowRows.value = (await response.json()).map(normalizeWatchingNowRow);
                    watchingNowLoadedAt.value = Date.now();
                    const selectedKey = String(selectedWatchingNowKey.value || '');
                    const exists = watchingNowRows.value.some(row => String(row?.key || '') === selectedKey);
                    selectedWatchingNowKey.value = exists ? selectedKey : '';
                } catch (e) {
                    console.error('Failed to load watching now', e);
                    if (!hasRows) {
                        watchingNowRows.value = [];
                        selectedWatchingNowKey.value = '';
                    }
                } finally {
                    if (!background) {
                        listLoading.value = false;
                    }
                }
            })();

            try {
                return await watchingNowFetchPromise;
            } finally {
                watchingNowFetchPromise = null;
            }
        };

        const loadWatchingNowVod = async ({force = false, background = false} = {}) => {
            const hasRows = Array.isArray(watchingNowVodRows.value) && watchingNowVodRows.value.length > 0;
            const cacheAgeMs = Date.now() - Number(watchingNowVodLoadedAt.value || 0);
            const isFresh = hasRows && cacheAgeMs < WATCHING_NOW_VOD_CACHE_TTL_MS;
            if (!force && isFresh) {
                return watchingNowVodRows.value;
            }
            if (watchingNowVodFetchPromise) {
                return await watchingNowVodFetchPromise;
            }

            watchingNowVodFetchPromise = (async () => {
                try {
                    if (!background) {
                        watchingNowVodLoading.value = true;
                    }
                    const rows = await bookmarkWatchUtils.fetchWatchingNowVod(window.location.origin);
                    watchingNowVodRows.value = rows.map(normalizeWatchingNowVodRow);
                    watchingNowVodLoadedAt.value = Date.now();
                    const selectedKey = String(selectedWatchingNowVodKey.value || '');
                    const exists = watchingNowVodRows.value.some(row => String(row?.key || '') === selectedKey);
                    selectedWatchingNowVodKey.value = exists ? selectedKey : '';
                } catch (e) {
                    console.error('Failed to load watching now vod', e);
                    if (!hasRows) {
                        watchingNowVodRows.value = [];
                        selectedWatchingNowVodKey.value = '';
                    }
                } finally {
                    if (!background) {
                        watchingNowVodLoading.value = false;
                    }
                }
            })();

            try {
                return await watchingNowVodFetchPromise;
            } finally {
                watchingNowVodFetchPromise = null;
            }
        };

        const ensureSelectedBookmarkCategory = () => {
            const selectedId = String(selectedBookmarkCategoryId.value || '');
            const exists = bookmarkCategoryTabs.value.some(tab => String(tab.id || '') === selectedId);
            if (!exists) {
                selectedBookmarkCategoryId.value = RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID;
            }
        };

        const selectBookmarkCategory = (categoryId) => {
            selectedBookmarkCategoryId.value = String(categoryId || '');
            clearSearch();
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
                const bookmarkOrders = {};
                bookmarks.value
                    .map(b => String(b?.dbId || '').trim())
                    .filter(Boolean)
                    .forEach((bookmarkId, index) => {
                        bookmarkOrders[bookmarkId] = index + 1;
                    });
                await fetch(`${window.location.origin}/bookmarks`, {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        bookmarkOrders
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
            const wasWatchingNowDrilldown = watchingNowDrilldown.value;
            if (activeTab.value === tab && tab === 'accounts') {
                viewState.value = 'accounts';
            } else {
                activeTab.value = tab;
            }
            if (tab === 'accounts' && wasWatchingNowDrilldown) {
                viewState.value = 'accounts';
            }
            if (tab !== 'watchingNow') {
                watchingNowDrilldown.value = false;
            }
            clearSearch();
            if (tab === 'watchingNow') {
                watchingNowDrilldown.value = false;
                if (!watchingNowTab.value) {
                    watchingNowTab.value = 'series';
                }
                loadWatchingNow({force: false, background: !!watchingNowRows.value.length});
                loadWatchingNowVod({force: false, background: !!watchingNowVodRows.value.length});
            }
        };

        const setWatchingNowTab = (tab) => {
            watchingNowTab.value = tab === 'vod' ? 'vod' : 'series';
        };

        const openWatchingNowSeriesDetail = async (row) => {
            if (!row) return;
            watchingNowTab.value = 'series';
            selectedWatchingNowKey.value = String(row.key || '');
            watchingNowDrilldown.value = true;
            activeTab.value = 'watchingNow';
            contentMode.value = 'series';
            selectedAccountId.value = row.accountId || null;
            currentContext.value.accountId = row.accountId || null;
            currentContext.value.accountType = row.accountType || null;
            currentContext.value.categoryId = row.categoryDbId || row.categoryId || '';
            const seriesState = getModeState('series');
            seriesState.selectedSeason = String(row.season || '');
            seriesState.selectedEpisodeId = String(row.episodeId || '');
            seriesState.selectedEpisodeNum = String(row.episodeNum || '');
            seriesState.selectedEpisodeName = String(row.episodeName || '');
            viewState.value = 'channels';
            await handleChannelSelection({
                channelId: row.seriesId || '',
                id: row.seriesId || '',
                dbId: row.seriesId || '',
                categoryId: row.categoryDbId || row.categoryId || '',
                name: row.seriesTitle || 'Series',
                logo: resolveLogoUrl(row.seriesPoster || '')
            });
        };

        const playWatchingNowVodRow = (row) => {
            if (!row) return;
            watchingNowTab.value = 'vod';
            selectedWatchingNowVodKey.value = String(row.key || '');
            contentMode.value = 'vod';
            currentContext.value.accountId = row.accountId || null;
            currentContext.value.accountType = row.accountType || null;
            currentContext.value.categoryId = row.categoryId || '';
            const base = normalizeChannel(row.playItem || {});
            const channelIdentifier = base.dbId || base.channelId || base.id || row.vodId || '';
            const playbackUrl = buildPlayerUrlForChannel({
                ...base,
                channelId: base.channelId || channelIdentifier,
                categoryId: row.categoryId || base.categoryId || '',
                name: base.name || row.vodName || 'VOD',
                logo: base.logo || row.vodLogo || ''
            }, 'vod');
            const nextChannel = {
                id: channelIdentifier,
                dbId: base.dbId || '',
                channelId: base.channelId || channelIdentifier,
                name: base.name || row.vodName || 'VOD',
                logo: resolveLogoUrl(base.logo || row.vodLogo || ''),
                cmd: base.cmd || '',
                drmType: base.drmType || '',
                drmLicenseUrl: base.drmLicenseUrl || '',
                clearKeysJson: base.clearKeysJson || '',
                inputstreamaddon: base.inputstreamaddon || '',
                manifestType: base.manifestType || '',
                accountId: row.accountId || '',
                categoryId: row.categoryId || '',
                type: 'channel',
                mode: 'vod',
                playRequestUrl: playbackUrl
            };
            startPlayback(playbackUrl, nextChannel);
        };

        const goBackToWatchingNow = () => {
            watchingNowDrilldown.value = false;
            activeTab.value = 'watchingNow';
            clearSearch();
        };

        const focusSearch = () => {
            const search = document.getElementById('contentSearch');
            if (search && typeof search.focus === 'function') {
                search.focus();
                if (typeof search.select === 'function') {
                    search.select();
                }
            }
        };

        const toggleSearch = () => {
            if (searchFieldVisible.value) {
                clearSearch();
                return;
            }
            searchVisible.value = true;
            nextTick(() => focusSearch());
        };

        const hidePlayerPanel = () => {
            if (!hasPlayerContent.value) return;
            playerManuallyHidden.value = true;
            playerExpanded.value = false;
        };

        const togglePlayerPanel = () => {
            if (!hasPlayerContent.value) return;
            playerManuallyHidden.value = !playerManuallyHidden.value;
            if (playerManuallyHidden.value) {
                playerExpanded.value = false;
            }
        };

        const togglePlayerExpanded = () => {
            if (!playerPanelVisible.value) return;
            playerExpanded.value = !playerExpanded.value;
            nextTick(() => {
                const video = videoPlayer.value;
                if (video && typeof video.play === 'function' && isPlaying.value && video.paused && !video.ended) {
                    video.play().catch(() => {});
                }
            });
        };

        const syncPlayerExpandedClass = () => {
            const root = document.getElementById('app');
            if (!root) return;
            root.classList.toggle('player-expanded', !!playerExpanded.value);
        };

        const selectAccount = async (account) => {
            watchingNowDrilldown.value = false;
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
                clearSearch();
                return;
            }
            await loadCategories(account, true);
        };

        const selectAccountTypeFilter = (filter) => {
            selectedAccountTypeFilter.value = String(filter || 'all');
        };

        const setContentMode = async (mode) => {
            if (!['itv', 'vod', 'series'].includes(mode)) return;
            if (mode !== 'itv' && !supportsVodSeriesForSelectedAccount.value) return;
            if (contentMode.value === mode) return;
            contentMode.value = mode;
            clearSearch();

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
            if (watchingNowDrilldown.value) {
                watchingNowDrilldown.value = false;
                activeTab.value = 'watchingNow';
                clearSearch();
                return;
            }
            viewState.value = 'accounts';
            clearSearch();
        };

        const goBackToCategories = () => {
            if (watchingNowDrilldown.value) {
                watchingNowDrilldown.value = false;
                activeTab.value = 'watchingNow';
                clearSearch();
                return;
            }
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
                modeState.selectedSeriesCategoryId = '';
                modeState.detail = null;
                selectedSeriesSeason.value = '';
                seriesDetail.value = null;
                seriesDetailLoading.value = false;
                vodDetail.value = null;
                vodDetailLoading.value = false;
            }
            clearSearch();
        };

        const appendPlaybackCompatParams = (query, mode) => {
            const resolvedMode = String(mode || '').toLowerCase();
            if (resolvedMode !== 'itv' && resolvedMode !== 'vod' && resolvedMode !== 'series') return;
            query.set('mode', resolvedMode);
            query.set('streamType', resolvedMode === 'itv' ? 'live' : 'video');
            query.set('action', resolvedMode);
        };

        const resolvePlaybackCategoryIdForChannel = (channel, modeOverride = null) => {
            const modeToUse = String(modeOverride || contentMode.value || 'itv').toLowerCase();
            if (modeToUse === 'series') {
                const seriesState = getModeState('series');
                return String(seriesState?.selectedSeriesCategoryId || channel?.categoryId || currentContext.value.categoryId || '');
            }
            const scopedCategoryId = String(currentContext.value.categoryId || '');
            const channelCategoryId = String(channel?.categoryId || '');
            if (scopedCategoryId.toLowerCase() === 'all' && channelCategoryId) {
                return channelCategoryId;
            }
            return String(channelCategoryId || scopedCategoryId || '');
        };

        const normalizeSeriesParentId = (value) => {
            const raw = String(value || '').trim();
            if (!raw || !raw.includes(':')) return raw;
            const parts = raw.split(':').filter(Boolean);
            return parts.length ? parts[parts.length - 1] : raw;
        };

        const buildPlayerUrlForChannel = (channel, modeOverride = null) => {
            const modeToUse = String(modeOverride || contentMode.value || 'itv').toLowerCase();
            const channelDbId = channel.dbId || '';
            const channelIdentifier = channel.channelId || channel.id || '';
            const seriesState = getModeState('series');
            const seriesParentId = modeToUse === 'series'
                ? normalizeSeriesParentId(seriesState?.selectedSeriesId || '')
                : '';
            const scopedCategoryId = resolvePlaybackCategoryIdForChannel(channel, modeToUse);
            const query = new URLSearchParams();
            query.set('accountId', currentContext.value.accountId || '');
            query.set('categoryId', scopedCategoryId);
            query.set('mode', modeToUse);

            if (modeToUse === 'series') {
                const resolvedSeason = resolvePlaybackSeason(channel);
                const resolvedEpisodeNum = resolvePlaybackEpisodeNumber(channel);
                // Series watch pointer is keyed by episode channelId, not local DB row id.
                query.set('channelId', channelIdentifier);
                query.set('seriesId', channelIdentifier);
                query.set('seriesParentId', seriesParentId);
                query.set('name', channel.name || '');
                query.set('logo', channel.logo || '');
                query.set('cmd', channel.cmd || '');
                query.set('season', resolvedSeason);
                query.set('episodeNum', resolvedEpisodeNum);
                query.set('drmType', channel.drmType || '');
                query.set('drmLicenseUrl', channel.drmLicenseUrl || '');
                query.set('clearKeysJson', channel.clearKeysJson || '');
                query.set('inputstreamaddon', channel.inputstreamaddon || '');
                query.set('manifestType', channel.manifestType || '');
                appendPlaybackCompatParams(query, modeToUse);
                return `${window.location.origin}/player?${query.toString()}`;
            }

            const useDbId = modeToUse === 'itv' && channelDbId;
            if (useDbId) {
                query.set('channelId', channelDbId);
            } else {
                query.set('channelId', channelIdentifier || channelDbId);
                query.set('name', channel.name || '');
                query.set('logo', channel.logo || '');
                query.set('cmd', channel.cmd || '');
                query.set('season', channel.season || '');
                query.set('episodeNum', channel.episodeNum || '');
                query.set('drmType', channel.drmType || '');
                query.set('drmLicenseUrl', channel.drmLicenseUrl || '');
                query.set('clearKeysJson', channel.clearKeysJson || '');
                query.set('inputstreamaddon', channel.inputstreamaddon || '');
                query.set('manifestType', channel.manifestType || '');
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
                const encoded = params.get('launch');
                if (!encoded) return null;
                const decoded = decodeBase64Url(encoded);
                if (!decoded) return null;
                return JSON.parse(decoded);
            } catch (_) {
                return null;
            }
        };

        const buildPlayerUrlFromQueryParams = (params) => {
            const query = new URLSearchParams();
            const allowed = [
                'accountId', 'categoryId', 'channelId', 'bookmarkId', 'seriesId', 'seriesParentId',
                'mode', 'streamType', 'action', 'name', 'logo', 'cmd', 'cmd_1', 'cmd_2', 'cmd_3',
                'season', 'episodeNum', 'drmType', 'drmLicenseUrl', 'clearKeysJson',
                'inputstreamaddon', 'manifestType', 'bingeWatchToken', 'episodeId'
            ];
            allowed.forEach((key) => {
                const value = cleanLaunchValue(params.get(key));
                if (value) query.set(key, value);
            });
            const directUrl = cleanLaunchValue(params.get('directUrl') || params.get('url'));
            if (directUrl) {
                query.set('url', directUrl);
            }
            appendPlaybackCompatParams(query, cleanLaunchValue(query.get('mode') || params.get('mode') || 'itv'));
            return `${window.location.origin}/player?${query.toString()}`;
        };

        const launchPlaybackFromQueryParams = async () => {
            const params = new URLSearchParams(window.location.search);
            if (params.has('launch')) return false;
            const directUrl = cleanLaunchValue(params.get('directUrl') || params.get('url'));
            const accountId = cleanLaunchValue(params.get('accountId'));
            const channelId = cleanLaunchValue(params.get('channelId'));
            const bookmarkId = cleanLaunchValue(params.get('bookmarkId'));
            const bingeWatchToken = cleanLaunchValue(params.get('bingeWatchToken'));
            if (!directUrl && !bookmarkId && !bingeWatchToken && !(accountId && channelId)) {
                return false;
            }

            const mode = cleanLaunchValue(params.get('mode') || 'itv').toLowerCase();
            contentMode.value = ['itv', 'vod', 'series'].includes(mode) ? mode : 'itv';
            activeTab.value = bookmarkId ? 'bookmarks' : 'accounts';
            viewState.value = 'channels';
            selectedAccountId.value = accountId || null;
            currentContext.value.accountId = accountId || null;
            currentContext.value.categoryId = cleanLaunchValue(params.get('categoryId'));
            const selectedAccount = accounts.value.find(a => String(a.dbId) === String(accountId || ''));
            currentContext.value.accountType = selectedAccount?.type || null;

            const playbackUrl = buildPlayerUrlFromQueryParams(params);
            const nextChannel = {
                id: bookmarkId || channelId || directUrl || bingeWatchToken,
                dbId: '',
                channelId: channelId || '',
                bookmarkId,
                name: cleanLaunchValue(params.get('name')) || (bookmarkId ? 'Bookmark' : 'Web Playback'),
                logo: resolveLogoUrl(cleanLaunchValue(params.get('logo'))),
                cmd: cleanLaunchValue(params.get('cmd') || directUrl),
                drmType: cleanLaunchValue(params.get('drmType')),
                drmLicenseUrl: cleanLaunchValue(params.get('drmLicenseUrl')),
                clearKeysJson: cleanLaunchValue(params.get('clearKeysJson')),
                inputstreamaddon: cleanLaunchValue(params.get('inputstreamaddon')),
                manifestType: cleanLaunchValue(params.get('manifestType')),
                season: cleanLaunchValue(params.get('season')),
                episodeNum: cleanLaunchValue(params.get('episodeNum')),
                accountId,
                categoryId: currentContext.value.categoryId,
                type: bookmarkId ? 'bookmark' : 'channel',
                mode: contentMode.value,
                playRequestUrl: playbackUrl
            };
            await startPlayback(playbackUrl, nextChannel);
            window.history.replaceState({}, document.title, `${window.location.origin}${window.location.pathname}`);
            return true;
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
                query.set('season', cleanLaunchValue(channel.season || resolvePlaybackSeason(channel)));
                query.set('episodeNum', cleanLaunchValue(channel.episodeNum || resolvePlaybackEpisodeNumber(channel)));
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

            const nextChannel = {
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
                season: resolvePlaybackSeason(channel),
                episodeNum: resolvePlaybackEpisodeNumber(channel),
                accountId: currentContext.value.accountId,
                categoryId: currentContext.value.categoryId,
                type: 'channel',
                mode: contentMode.value,
                playRequestUrl: playbackUrl
            };
            await startPlayback(playbackUrl, nextChannel);

            const cleanUrl = `${window.location.origin}${window.location.pathname}`;
            window.history.replaceState({}, document.title, cleanUrl);
        };

        const launchDirectPlaybackFromPayload = async (payload) => {
            const directUrl = cleanLaunchValue(payload?.directUrl || payload?.url);
            const bingeWatchToken = cleanLaunchValue(payload?.bingeWatchToken);
            if (!directUrl && !bingeWatchToken) return false;

            const mode = cleanLaunchValue(payload?.mode || 'itv').toLowerCase();
            const params = new URLSearchParams();
            params.set('mode', ['itv', 'vod', 'series'].includes(mode) ? mode : 'itv');
            params.set('accountId', cleanLaunchValue(payload?.accountId));
            params.set('categoryId', cleanLaunchValue(payload?.categoryId || payload?.seriesCategoryId));
            if (directUrl) params.set('directUrl', directUrl);
            if (bingeWatchToken) params.set('bingeWatchToken', bingeWatchToken);
            if (payload?.episodeId) params.set('episodeId', cleanLaunchValue(payload.episodeId));
            const playbackUrl = buildPlayerUrlFromQueryParams(params);

            contentMode.value = cleanLaunchValue(params.get('mode') || 'itv');
            activeTab.value = 'accounts';
            viewState.value = 'channels';
            selectedAccountId.value = cleanLaunchValue(params.get('accountId')) || null;
            currentContext.value.accountId = selectedAccountId.value;
            currentContext.value.categoryId = cleanLaunchValue(params.get('categoryId'));
            const selectedAccount = accounts.value.find(a => String(a.dbId) === String(selectedAccountId.value || ''));
            currentContext.value.accountType = selectedAccount?.type || null;

            await startPlayback(playbackUrl, {
                id: directUrl || bingeWatchToken,
                channelId: cleanLaunchValue(payload?.channelId || payload?.episodeId),
                name: cleanLaunchValue(payload?.name) || 'Web Playback',
                cmd: directUrl,
                accountId: selectedAccountId.value || '',
                categoryId: currentContext.value.categoryId,
                type: 'channel',
                mode: contentMode.value,
                playRequestUrl: playbackUrl
            });
            window.history.replaceState({}, document.title, `${window.location.origin}${window.location.pathname}`);
            return true;
        };

        const launchPlaybackFromPayload = async (payload) => {
            if (!payload) return false;
            if (payload?.channel) {
                await launchDrmPlaybackFromPayload(payload);
                return true;
            }
            return await launchDirectPlaybackFromPayload(payload);
        };

        const serializeBingeWatchEpisode = (episode = {}) => ({
            dbId: String(episode.dbId || ''),
            channelId: String(episode.channelId || episode.id || ''),
            categoryId: String(episode.categoryId || currentContext.value.categoryId || ''),
            name: String(episode.name || ''),
            cmd: String(episode.cmd || ''),
            cmd_1: String(episode.cmd_1 || ''),
            cmd_2: String(episode.cmd_2 || ''),
            cmd_3: String(episode.cmd_3 || ''),
            logo: String(episode.logo || ''),
            description: String(episode.description || ''),
            season: String(episodeSeasonOrDefault(episode)),
            episodeNum: String(resolvePlaybackEpisodeNumber(episode) || ''),
            releaseDate: String(episode.releaseDate || ''),
            rating: String(episode.rating || ''),
            duration: String(episode.duration || ''),
            drmType: String(episode.drmType || ''),
            drmLicenseUrl: String(episode.drmLicenseUrl || ''),
            clearKeysJson: String(episode.clearKeysJson || ''),
            inputstreamaddon: String(episode.inputstreamaddon || ''),
            manifestType: String(episode.manifestType || '')
        });

        const startBingeWatchSeason = async () => {
            if (!canBingeWatchSeason.value || bingeWatchLoading.value) return;
            const modeState = getModeState('series');
            const season = String(selectedBingeWatchSeason.value || '1');
            const seriesId = String(modeState.selectedSeriesId || '');
            const categoryId = String(modeState.selectedSeriesCategoryId || currentContext.value.categoryId || '');
            bingeWatchLoading.value = true;
            playbackError.value = '';
            try {
                const response = await fetch(`${window.location.origin}/bingeWatchSession`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        accountId: currentContext.value.accountId || '',
                        categoryId,
                        seriesId,
                        season,
                        episodes: bingeWatchSeasonEpisodes.value.map(serializeBingeWatchEpisode)
                    })
                });
                const data = await response.json().catch(() => ({}));
                if (!response.ok || !data?.token) {
                    throw new Error(data?.message || 'Unable to prepare binge watch session.');
                }
                const playbackUrl = buildBingeWatchPlaybackUrl(data.token);
                await startPlayback(playbackUrl, {
                    id: `binge-${data.token}`,
                    channelId: '',
                    name: seriesDetail.value?.name || bingeWatchButtonLabel.value,
                    logo: resolveLogoUrl(seriesDetail.value?.cover || ''),
                    accountId: currentContext.value.accountId || '',
                    categoryId,
                    seriesParentId: seriesId,
                    season,
                    bingeWatchToken: data.token,
                    type: 'channel',
                    mode: 'series',
                    playRequestUrl: playbackUrl
                });
            } catch (e) {
                console.error('Failed to start binge watch', e);
                playbackError.value = e?.message || 'Unable to start binge watch.';
            } finally {
                bingeWatchLoading.value = false;
            }
        };

        const playChannel = (channel) => {
            scrollToTop();
            const modeToUse = String(contentMode.value || 'itv').toLowerCase();
            const playbackCategoryId = resolvePlaybackCategoryIdForChannel(channel, modeToUse);
            const channelIdentifier = channel.dbId || channel.channelId || channel.id;
            const playbackUrl = buildPlayerUrlForChannel(channel, modeToUse);
            const nextChannel = {
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
                season: modeToUse === 'series' ? resolvePlaybackSeason(channel) : '',
                episodeNum: modeToUse === 'series' ? resolvePlaybackEpisodeNumber(channel) : '',
                seriesParentId: modeToUse === 'series' ? String(getModeState('series')?.selectedSeriesId || '') : '',
                accountId: currentContext.value.accountId,
                categoryId: playbackCategoryId,
                type: 'channel',
                mode: modeToUse,
                playRequestUrl: playbackUrl
            };
            if (isPendingPlaybackTarget({
                id: nextChannel.channelId || nextChannel.id || '',
                accountId: nextChannel.accountId || '',
                mode: nextChannel.mode || '',
                season: nextChannel.season || '',
                episodeNum: nextChannel.episodeNum || ''
            }) || matchesCurrentPlayback({
                id: nextChannel.channelId || nextChannel.id || '',
                accountId: nextChannel.accountId || '',
                mode: nextChannel.mode || '',
                season: nextChannel.season || '',
                episodeNum: nextChannel.episodeNum || ''
            })) {
                return;
            }
            startPlayback(playbackUrl, nextChannel);
        };

        const playBookmark = (bookmark) => {
            scrollToTop();
            const bookmarkMode = String(bookmark.accountAction || bookmark.mode || 'itv').toLowerCase();
            const query = new URLSearchParams();
            query.set('bookmarkId', bookmark.dbId);
            appendPlaybackCompatParams(query, bookmarkMode);
            const playbackUrl = `${window.location.origin}/player?${query.toString()}`;

            const nextChannel = {
                id: bookmark.dbId,
                name: bookmark.channelName,
                logo: resolveLogoUrl(bookmark.logo),
                accountName: bookmark.accountName,
                accountId: resolveAccountId(bookmark.accountName),
                categoryId: bookmark.categoryId || '',
                channelId: bookmark.channelId || '',
                cmd: bookmark.cmd || '',
                seriesParentId: bookmark.seriesParentId || bookmark.seriesId || '',
                bookmarkId: bookmark.dbId,
                type: 'bookmark',
                mode: bookmarkMode,
                playRequestUrl: playbackUrl
            };
            if (isPendingPlaybackTarget({
                id: nextChannel.channelId || '',
                accountId: nextChannel.accountId || '',
                accountName: nextChannel.accountName || '',
                mode: nextChannel.mode || '',
                bookmarkId: nextChannel.bookmarkId || ''
            }) || matchesCurrentPlayback({
                id: nextChannel.channelId || '',
                accountId: nextChannel.accountId || '',
                accountName: nextChannel.accountName || '',
                mode: nextChannel.mode || '',
                bookmarkId: nextChannel.bookmarkId || ''
            })) {
                return;
            }
            recordRecentlyPlayedBookmark(bookmark);
            startPlayback(playbackUrl, nextChannel);
        };

        const handleChannelSelection = async (channel) => {
            if (contentMode.value === 'series' && viewState.value === 'channels') {
                const seriesId = channel.channelId || channel.id || channel.dbId;
                const modeState = getModeState('series');
                modeState.selectedSeriesId = String(seriesId || '');
                modeState.selectedSeriesCategoryId = String(channel?.categoryId || currentContext.value.categoryId || '');
                if (!watchingNowDrilldown.value) {
                    modeState.selectedSeason = '';
                    modeState.selectedEpisodeId = '';
                    modeState.selectedEpisodeNum = '';
                    modeState.selectedEpisodeName = '';
                }
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
                    await loadSeriesEpisodes(seriesId, modeState.selectedSeriesCategoryId);
                } else {
                    await loadSeriesChildren(seriesId);
                }
                (async () => {
                    try {
                        const response = await fetch(
                            `${window.location.origin}/seriesDetails?seriesId=${encodeURIComponent(seriesId)}&accountId=${currentContext.value.accountId}&categoryId=${encodeURIComponent(modeState.selectedSeriesCategoryId || currentContext.value.categoryId || '')}&seriesName=${encodeURIComponent(channel.name || '')}`
                        );
                        const data = await response.json();
                        if (String(modeState.selectedSeriesId || '') !== String(seriesId || '')) return;

                        const detail = {...(modeState.detail || {})};
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
                playChannel(channel);
                return;
            }
            playChannel(channel);
        };

        const applyMutePreference = (video) => {
            if (!video) return;
            const shouldMute = !!isMuted.value;
            video.muted = shouldMute;
            if (!shouldMute && video.volume === 0) {
                video.volume = 1;
            }
        };

        const readFiniteDuration = (video) => {
            const duration = Number(video?.duration || 0);
            return Number.isFinite(duration) && duration > 0 ? duration : 0;
        };

        const resetPlaybackProgress = () => {
            playbackCurrentTime.value = 0;
            playbackDuration.value = 0;
            playbackProgressDragging.value = false;
        };

        const syncPlaybackProgress = () => {
            const video = videoPlayer.value;
            if (!video) {
                resetPlaybackProgress();
                return;
            }
            playbackDuration.value = readFiniteDuration(video);
            if (!playbackProgressDragging.value) {
                const currentTime = Number(video.currentTime || 0);
                playbackCurrentTime.value = Number.isFinite(currentTime) && currentTime > 0 ? currentTime : 0;
            }
        };

        const formatPlaybackTime = (seconds) => {
            const totalSeconds = Math.max(0, Math.floor(Number(seconds || 0)));
            const hours = Math.floor(totalSeconds / 3600);
            const minutes = Math.floor((totalSeconds % 3600) / 60);
            const secs = totalSeconds % 60;
            const pad = (value) => String(value).padStart(2, '0');
            return hours > 0
                ? `${hours}:${pad(minutes)}:${pad(secs)}`
                : `${minutes}:${pad(secs)}`;
        };

        const seekPlayback = (event) => {
            const video = videoPlayer.value;
            if (!video || !playbackSeekable.value) return;
            const requestedTime = Number(event?.target?.value || 0);
            if (!Number.isFinite(requestedTime)) return;
            const nextTime = Math.min(Math.max(requestedTime, 0), playbackDuration.value);
            playbackProgressDragging.value = true;
            playbackCurrentTime.value = nextTime;
            try {
                video.currentTime = nextTime;
            } finally {
                playbackProgressDragging.value = false;
            }
        };

        const bindPlaybackEvents = (video) => {
            if (!video) return;
            applyMutePreference(video);
            video.onended = async () => {
                await triggerPlaybackEnded();
            };
            video.onerror = async () => {
                await triggerRepeatReload();
            };
            video.onvolumechange = () => {
                isMuted.value = video.muted || video.volume === 0;
            };
            video.ontimeupdate = syncPlaybackProgress;
            video.ondurationchange = syncPlaybackProgress;
            video.onloadedmetadata = syncPlaybackProgress;
            video.onprogress = syncPlaybackProgress;
            isMuted.value = video.muted || video.volume === 0;
            syncPlaybackProgress();
        };

        const clearActiveBingeWatch = () => {
            activeBingeWatch.value = {token: '', currentEpisodeId: '', items: []};
        };

        const normalizeBingeWatchItem = (item = {}) => ({
            episodeId: String(item.episodeId || '').trim(),
            episodeName: String(item.episodeName || '').trim(),
            season: String(item.season || '').trim(),
            episodeNumber: String(item.episodeNumber || item.episodeNum || '').trim()
        });

        const syncBingeWatchState = (channelData = {}) => {
            const binge = channelData?.bingeWatch;
            const items = Array.isArray(binge?.items)
                ? binge.items.map(normalizeBingeWatchItem).filter(item => item.episodeId)
                : [];
            const token = String(binge?.token || '').trim();
            if (!token || items.length === 0) {
                clearActiveBingeWatch();
                return;
            }

            const currentEpisodeId = String(binge.currentEpisodeId || items[0].episodeId || '').trim();
            activeBingeWatch.value = {token, currentEpisodeId, items};

            const resolvedChannel = channelData?.channel || {};
            if (currentChannel.value) {
                currentChannel.value = {
                    ...currentChannel.value,
                    id: currentEpisodeId || currentChannel.value.id,
                    channelId: currentEpisodeId || resolvedChannel.channelId || currentChannel.value.channelId,
                    name: channelData.title || resolvedChannel.name || currentChannel.value.name,
                    season: resolvedChannel.season || currentChannel.value.season,
                    episodeNum: resolvedChannel.episodeNum || currentChannel.value.episodeNum,
                    bingeWatchToken: token,
                    episodeId: currentEpisodeId
                };
            }
        };

        const buildBingeWatchPlaybackUrl = (token, episodeId = '') => {
            const query = new URLSearchParams();
            query.set('mode', 'series');
            query.set('accountId', currentChannel.value?.accountId || currentContext.value.accountId || '');
            query.set('categoryId', currentChannel.value?.categoryId || currentContext.value.categoryId || '');
            query.set('bingeWatchToken', token);
            if (episodeId) {
                query.set('episodeId', episodeId);
            }
            appendPlaybackCompatParams(query, 'series');
            return `${window.location.origin}/player?${query.toString()}`;
        };

        const playBingeWatchEpisodeAt = async (index) => {
            const state = activeBingeWatch.value;
            const item = state.items?.[index];
            if (!state.token || !item?.episodeId) return false;
            const playbackUrl = buildBingeWatchPlaybackUrl(state.token, item.episodeId);
            await startPlayback(playbackUrl, {
                id: item.episodeId,
                channelId: item.episodeId,
                name: item.episodeName || `Episode ${index + 1}`,
                logo: currentChannel.value?.logo || seriesDetail.value?.cover || '',
                accountId: currentChannel.value?.accountId || currentContext.value.accountId || '',
                categoryId: currentChannel.value?.categoryId || currentContext.value.categoryId || '',
                season: item.season || '',
                episodeNum: item.episodeNumber || '',
                bingeWatchToken: state.token,
                episodeId: item.episodeId,
                type: 'channel',
                mode: 'series',
                playRequestUrl: playbackUrl
            });
            return true;
        };

        const playNextBingeWatchIfAvailable = async () => {
            const state = activeBingeWatch.value;
            if (!state.token || !Array.isArray(state.items) || state.items.length === 0) return false;
            const currentIndex = state.items.findIndex(item => item.episodeId === state.currentEpisodeId);
            const nextIndex = currentIndex < 0 ? 1 : currentIndex + 1;
            if (nextIndex < 0 || nextIndex >= state.items.length) return false;
            return await playBingeWatchEpisodeAt(nextIndex);
        };

        const triggerPlaybackEnded = async () => {
            if (await playNextBingeWatchIfAvailable()) {
                return;
            }
            await triggerRepeatReload();
        };

        const triggerRepeatReload = async () => {
            if (!repeatEnabled.value || !currentChannel.value?.playRequestUrl || repeatReloadInFlight) return;
            repeatReloadInFlight = true;
            try {
                await startPlayback(currentChannel.value.playRequestUrl, currentChannel.value);
            } finally {
                repeatReloadInFlight = false;
            }
        };

        const buildStrategyKey = (channel) => {
            if (!channel) return '';
            const id = String(channel.channelId || channel.id || channel.dbId || '').trim();
            const accountId = String(channel.accountId || '').trim();
            const url = String(channel.playRequestUrl || channel.url || channel.cmd || '').trim();
            return [accountId, id, url].join('|');
        };

        const clearVideoElement = (video) => {
            if (!video) return;
            video.onended = null;
            video.onerror = null;
            video.onvolumechange = null;
            video.ontimeupdate = null;
            video.ondurationchange = null;
            video.onloadedmetadata = null;
            video.onprogress = null;
            video.pause();
            video.src = '';
            video.removeAttribute('src');
            video.load();
            resetPlaybackProgress();
        };

        const isSamePlaybackTarget = (a, b) => {
            if (!a || !b) return false;
            const aId = String(a.channelId || a.id || '').trim();
            const bId = String(b.channelId || b.id || '').trim();
            if (!aId || !bId || aId !== bId) return false;
            if (String(a.accountId || '') !== String(b.accountId || '')) return false;
            if (String(a.mode || '') !== String(b.mode || '')) return false;
            if (String(a.season || '') !== String(b.season || '')) return false;
            if (String(a.episodeNum || '') !== String(b.episodeNum || '')) return false;
            if (String(a.bookmarkId || '') !== String(b.bookmarkId || '')) return false;
            return true;
        };

        const startPlayback = async (url, nextChannel = null, options = {}) => {
            const requestId = ++playbackRequestId;
            controlsVisible.value = true;
            playerManuallyHidden.value = false;
            const targetChannel = nextChannel ? {...nextChannel} : currentChannel.value;
            const switching = targetChannel && currentChannel.value && !isSamePlaybackTarget(currentChannel.value, targetChannel);
            if (switching) {
                await stopPlaybackAndHide({reason: 'switch', notify: true, resetStrategy: true, hideControls: false});
            }
            if (playbackFetchController) {
                try {
                    playbackFetchController.abort();
                } catch (_) {
                }
                playbackFetchController = null;
            }
            const controller = new AbortController();
            playbackFetchController = controller;
            const channelKey = buildStrategyKey(targetChannel);
            if (channelKey && channelKey !== strategyOverrideKey) {
                strategyOverride.value = 'auto';
                strategyOverrideKey = channelKey;
            }
            pendingPlaybackKey.value = buildPlaybackTargetKey({
                id: targetChannel?.channelId || targetChannel?.id || '',
                accountId: targetChannel?.accountId || '',
                accountName: targetChannel?.accountName || '',
                mode: targetChannel?.mode || '',
                season: targetChannel?.season || '',
                episodeNum: targetChannel?.episodeNum || '',
                bookmarkId: targetChannel?.bookmarkId || ''
            });
            playbackLoading.value = true;
            playbackMode.value = 'loading';
            playbackError.value = '';
            const channelDataPromise = fetch(url, {signal: controller.signal}).then(response => response.json());
            if (!switching) {
                await stopPlayback(true);
            }
            if (requestId !== playbackRequestId) return;
            currentChannel.value = targetChannel;
            isPlaying.value = true;

            try {
                const channelData = await channelDataPromise;
                if (requestId !== playbackRequestId) return;
                syncBingeWatchState(channelData);
                await initPlayer(channelData);
                if (currentChannel.value?.mode === 'series') {
                    await refreshSeriesEpisodeWatchState();
                    await ensureSeriesWatchingNow();
                }
            } catch (e) {
                if (e?.name === 'AbortError') return;
                if (isBrowserUnsupportedMediaError(e)) {
                    console.warn(`Failed to start playback: ${e?.message || e}`);
                } else {
                    console.error('Failed to start playback', e);
                }
                playbackError.value = `Playback failed: ${e?.message || 'Unknown error'}`;
                isPlaying.value = false;
            } finally {
                if (requestId === playbackRequestId) {
                    playbackLoading.value = false;
                    if (!isPlaying.value) {
                        playbackMode.value = '';
                    }
                    pendingPlaybackKey.value = '';
                    if (playbackFetchController === controller) {
                        playbackFetchController = null;
                    }
                }
            }
        };

        const stopPlayback = async (preserveUi = false) => {
            if (!preserveUi && playbackFetchController) {
                try {
                    playbackFetchController.abort();
                } catch (_) {
                }
                playbackFetchController = null;
                pendingPlaybackKey.value = '';
            }
            if (playerInstance.value) {
                try {
                    await playerInstance.value.destroy();
                } catch (e) {
                    console.warn('Error destroying Shaka player', e);
                }
                playerInstance.value = null;
            }
            if (mpegtsPlayer.value) {
                try {
                    mpegtsPlayer.value.destroy();
                } catch (e) {
                    console.warn('Error destroying MPEGTS player', e);
                }
                mpegtsPlayer.value = null;
            }

            clearVideoElement(videoPlayer.value);

            if (!preserveUi) {
                playbackLoading.value = false;
                playbackMode.value = '';
                isPlaying.value = false;
                currentChannel.value = null;
                playbackError.value = '';
                playerExpanded.value = false;
                playerManuallyHidden.value = false;
                clearActiveBingeWatch();
            }
            videoTracks.value = [];
            audioTracks.value = [];
            textTracks.value = [];
            selectedTextTrackId.value = 'off';
        };

        const stopPlaybackAndHide = async (options = {}) => {
            const notify = options.notify !== false;
            const reason = options.reason || 'stop';
            const resetStrategy = options.resetStrategy !== false;
            const hideControls = options.hideControls !== false;
            if (notify) {
                notifyPlayerClosed(reason);
            }
            if (resetStrategy) {
                resetPlaybackDefaults();
            }
            // Ensure UI hides immediately on explicit stop.
            playbackLoading.value = false;
            playbackMode.value = '';
            isPlaying.value = false;
            currentChannel.value = null;
            playbackError.value = '';
            if (hideControls) {
                controlsVisible.value = false;
            }
            playerExpanded.value = false;
            playerManuallyHidden.value = false;
            await stopPlayback(false);
        };

        const initPlayer = async (channel) => {
            const uri = channel.url;
            if (!uri) {
                console.error('No URL provided for playback.');
                playbackError.value = 'Playback failed: no stream URL returned.';
                isPlaying.value = false;
                return;
            }

            await nextTick();

            const video = videoPlayer.value;
            if (!video) {
                console.error('Video element not found.');
                playbackError.value = 'Playback failed: video element not available.';
                return;
            }

            bindPlaybackEvents(video);

            const override = String(strategyOverride.value || 'auto').toLowerCase();
            const userAgent = navigator.userAgent || '';
            const isIos = /iPhone|iPad|iPod/i.test(userAgent);
            const isSafari = /Safari/i.test(userAgent) && !/Chrome|CriOS|Chromium|Edg|OPR|Firefox|FxiOS/i.test(userAgent);
            const canNative = Boolean(video.canPlayType('application/vnd.apple.mpegurl'));
            const hasDRM = channel.drm != null;
            const normalizedUri = String(uri || '').toLowerCase();
            const manifestType = String(channel?.drm?.manifestType || channel?.manifestType || '').toLowerCase();
            const isTs = isTsLikeUrl(normalizedUri, manifestType);
            const isHls = manifestType === 'hls'
                || manifestType === 'm3u8'
                || normalizedUri.includes('.m3u8')
                || normalizedUri.includes('.m3u?');
            const prefersNativeHls = canNative && (isIos || isSafari);

            if (override === 'proxy') {
                const proxyUrl = buildProxyStreamUrl(uri) || uri;
                await loadNative({...channel, url: proxyUrl});
                return;
            }
            if (override === 'direct') {
                await loadNative(channel);
                return;
            }
            if (override === 'mpegts') {
                await loadMpegTs({...channel, url: resolveMpegTsPlaybackUrl(uri)});
                return;
            }

            const preferShakaFallback = isHls && !prefersNativeHls;
            try {
                if (hasDRM) {
                    await loadShaka(channel);
                } else if (isTs) {
                    await loadMpegTs({...channel, url: resolveMpegTsPlaybackUrl(uri)});
                } else if (isHls && prefersNativeHls) {
                    await loadNative(channel);
                } else if (isHls) {
                    await loadShaka(channel);
                } else if (canNative) {
                    await loadNative(channel);
                } else {
                    await loadShaka(channel);
                }
            } catch (e) {
                if (!hasDRM && !isTs && await tryProxyPlaybackFallback(channel, preferShakaFallback, e)) {
                    return;
                }
                throw e;
            }
        };

        const tryProxyPlaybackFallback = async (channel, preferShaka, previousError) => {
            const sourceUrl = String(channel?.url || '').trim();
            const proxyUrl = buildProxyStreamUrl(sourceUrl);
            if (!proxyUrl || proxyUrl === sourceUrl) {
                return false;
            }
            console.warn('Playback failed, retrying through local proxy.', previousError);
            const attempts = preferShaka
                ? [loadShaka, loadNative]
                : [loadNative, loadShaka];
            let lastError = previousError;
            for (const attempt of attempts) {
                await stopPlayback(true);
                try {
                    await attempt({...channel, url: proxyUrl});
                    return true;
                } catch (e) {
                    lastError = e;
                }
            }
            console.warn('Proxy playback fallback failed.', lastError);
            const compatibilityIssue = await detectHlsBrowserCompatibilityIssue(sourceUrl);
            if (compatibilityIssue) {
                throw new Error(compatibilityIssue);
            }
            return false;
        };

        const detectHlsBrowserCompatibilityIssue = async (url) => {
            const sourceUrl = String(url || '').trim();
            if (!sourceUrl || /^(blob:|data:|file:)/i.test(sourceUrl)) {
                return '';
            }
            const probeUrl = buildProxyStreamUrl(sourceUrl) || sourceUrl;
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 5000);
            try {
                const response = await fetch(probeUrl, {
                    signal: controller.signal,
                    headers: {Accept: 'application/vnd.apple.mpegurl, application/x-mpegURL, */*'}
                });
                const contentType = String(response.headers.get('content-type') || '').toLowerCase();
                const responseUrl = String(response.url || probeUrl).toLowerCase();
                const lowerSource = sourceUrl.toLowerCase();
                const looksHls = contentType.includes('mpegurl')
                    || responseUrl.includes('.m3u8')
                    || lowerSource.includes('.m3u8');
                if (!response.ok || !looksHls) {
                    return '';
                }
                return describeUnsupportedHlsManifest(await response.text());
            } catch (_) {
                return '';
            } finally {
                clearTimeout(timeoutId);
            }
        };

        const loadMpegTs = async (channel) => {
            await nextTick();
            const video = videoPlayer.value;
            if (!video) return;

            bindPlaybackEvents(video);
            const sourceUrl = normalizeWebPlaybackUrl(channel.url);
            const engine = window.mpegts;
            if (!canUseMpegts()) {
                const fallbackUrl = buildProxyStreamUrl(channel.url) || sourceUrl;
                await loadNative({...channel, url: fallbackUrl});
                return;
            }

            try {
                const player = engine.createPlayer(
                    {
                        type: 'mpegts',
                        isLive: String(currentChannel.value?.mode || 'itv') === 'itv',
                        url: sourceUrl
                    },
                    {
                        enableWorker: true,
                        lazyLoad: false
                    }
                );
                mpegtsPlayer.value = player;
                player.on(engine.Events.ERROR, async (_, detail) => {
                    const message = detail?.msg || detail?.message || 'MPEGTS error';
                    playbackError.value = `Playback error: ${message}`;
                });
                player.attachMediaElement(video);
                player.load();
                await player.play();
                playbackMode.value = resolvePlaybackModeLabel(sourceUrl, 'mpegts');
            } catch (e) {
                const message = describeMpegTsFailure(e);
                if (isBrowserUnsupportedMediaError(e)) {
                    console.warn(message);
                } else {
                    console.warn(message, e);
                }
                throw new Error(message);
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
                playbackMode.value = resolvePlaybackModeLabel(sourceUrl, 'native');
            } catch (e) {
                if (String(strategyOverride.value || 'auto') !== 'auto') {
                    playbackError.value = `Playback failed: ${e?.message || 'No supported source found'}`;
                    console.warn('Native playback failed.', e);
                    throw e;
                }
                // If backend proxy fails transiently, retry once with cache-busting query.
                const isProxyUrl = String(sourceUrl || '').includes('/proxy-stream');
                if (isProxyUrl) {
                    try {
                        const parsed = new URL(sourceUrl, window.location.origin);
                        parsed.searchParams.set('_retry', String(Date.now()));
                        sourceUrl = parsed.toString();
                        video.src = sourceUrl;
                        await video.play();
                        playbackMode.value = resolvePlaybackModeLabel(sourceUrl, 'native');
                        return;
                    } catch (retryProxyErr) {
                        console.warn('Proxy retry failed.', retryProxyErr);
                    }
                }

                // Retry with explicit HTTP downgrade for known Stalker playback paths.
                const downgraded = downgradeHttpsToHttpForKnownPaths(sourceUrl);
                if (downgraded !== sourceUrl) {
                    try {
                        sourceUrl = downgraded;
                        video.src = sourceUrl;
                        await video.play();
                        playbackMode.value = resolvePlaybackModeLabel(sourceUrl, 'native');
                        return;
                    } catch (retryErr) {
                        console.warn('HTTP fallback failed.', retryErr);
                    }
                }

                playbackError.value = `Playback failed: ${e?.message || 'No supported source found'}`;
                console.warn('Native playback failed.', e);
                throw e;
            }
        };

        const normalizeWebPlaybackUrl = (rawUrl) => playbackUtils.normalizeWebPlaybackUrl(rawUrl);
        const downgradeHttpsToHttpForKnownPaths = (url) => playbackUtils.downgradeHttpsToHttpForKnownPaths(url);

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
                playbackError.value = `Playback error: ${event?.detail?.message || 'Shaka error'}`;
            });

            if (channel.drm) {
                const drmConfig = {};
                if (channel.drm.licenseUrl) {
                    drmConfig.servers = {[channel.drm.type]: channel.drm.licenseUrl};
                }
                if (channel.drm.clearKeys) {
                    drmConfig.clearKeys = channel.drm.clearKeys;
                }
                player.configure({drm: drmConfig});
            }

            try {
                await player.attach(video);
                await player.load(channel.url);
                await video.play();
                playbackMode.value = resolvePlaybackModeLabel(channel.url, 'shaka');
                refreshShakaTracks(player);
                player.addEventListener('trackschanged', () => refreshShakaTracks(player));
                player.addEventListener('variantchanged', () => refreshShakaTracks(player));
                player.addEventListener('adaptation', () => refreshShakaTracks(player));
            } catch (e) {
                console.error('Shaka: Error loading video:', e);
                playbackError.value = `Playback failed: ${e?.message || 'Unable to load stream'}`;
                throw e;
            }
        };

        const selectHighestVariantTrack = (player) => {
            if (!player || typeof player.getVariantTracks !== 'function') return null;
            const variants = player.getVariantTracks() || [];
            const candidates = variants.filter(track => track && !track.audioOnly);
            if (candidates.length === 0) return null;
            candidates.sort((a, b) => (Number(b.height || 0) - Number(a.height || 0))
                || (Number(b.bandwidth || 0) - Number(a.bandwidth || 0)));
            const top = candidates[0];
            if (top && typeof player.selectVariantTrack === 'function') {
                player.selectVariantTrack(top, true);
            }
            return top;
        };

        const applyMaxQualityPreference = (player) => {
            if (!player || !isMaxQualityEnabled()) return false;
            if (typeof player.configure === 'function') {
                player.configure('abr.enabled', false);
            }
            return !!selectHighestVariantTrack(player);
        };

        const refreshShakaTracks = (player) => {
            if (!player) return;
            if (isMaxQualityEnabled()) {
                applyMaxQualityPreference(player);
            }
            const allVariants = typeof player.getVariantTracks === 'function' ? (player.getVariantTracks() || []) : [];
            videoTracks.value = allVariants
                .filter(track => track && !track.audioOnly)
                .filter((track, idx, arr) => arr.findIndex(other =>
                    Number(other.height || 0) === Number(track.height || 0)
                    && Number(other.bandwidth || 0) === Number(track.bandwidth || 0)
                ) === idx)
                .sort((a, b) => Number(a.height || 0) - Number(b.height || 0));

            const selectedVariant = allVariants.find(track => track && track.active) || null;
            const selectedAudioKey = selectedVariant
                ? `${String(selectedVariant.language || '')}|${String(selectedVariant.roles?.[0] || '')}|${String(selectedVariant.label || '')}`
                : '';
            const audioByKey = new Map();
            for (const track of allVariants) {
                if (!track || track.audioOnly) continue;
                const key = `${String(track.language || '')}|${String(track.roles?.[0] || '')}|${String(track.label || '')}`;
                if (!audioByKey.has(key)) {
                    audioByKey.set(key, {
                        id: key,
                        language: track.language || '',
                        role: track.roles?.[0] || '',
                        label: track.label || '',
                        active: key === selectedAudioKey
                    });
                } else if (key === selectedAudioKey) {
                    audioByKey.get(key).active = true;
                }
            }
            audioTracks.value = Array.from(audioByKey.values());

            const texts = typeof player.getTextTracks === 'function' ? (player.getTextTracks() || []) : [];
            textTracks.value = texts;
            const selectedText = texts.find(track => track && track.active);
            selectedTextTrackId.value = selectedText ? String(selectedText.id) : 'off';
        };

        const switchVideoTrack = (trackId) => {
            if (!playerInstance.value) return;
            const track = playerInstance.value.getVariantTracks().find(t => t.id === trackId);
            if (track) {
                setMaxQualityEnabled(false);
                playerInstance.value.selectVariantTrack(track, true);
                if (typeof playerInstance.value.configure === 'function') {
                    playerInstance.value.configure('abr.enabled', false);
                }
                refreshShakaTracks(playerInstance.value);
            }
        };

        const switchVideoAuto = () => {
            if (!playerInstance.value) return;
            setMaxQualityEnabled(false);
            if (typeof playerInstance.value.configure === 'function') {
                playerInstance.value.configure('abr.enabled', true);
            }
            refreshShakaTracks(playerInstance.value);
        };

        const switchVideoMax = () => {
            if (!playerInstance.value) return;
            setMaxQualityEnabled(true);
            applyMaxQualityPreference(playerInstance.value);
            refreshShakaTracks(playerInstance.value);
        };

        const switchAudioTrack = (trackId) => {
            if (!playerInstance.value) return;
            const [language, role, label] = String(trackId || '').split('|');
            const target = (audioTracks.value || []).find(track =>
                    String(track.id) === String(trackId)
                    || (
                        String(track.language || '') === String(language || '')
                        && String(track.role || '') === String(role || '')
                        && String(track.label || '') === String(label || '')
                    )
            );
            if (target && typeof playerInstance.value.selectAudioLanguage === 'function' && normalizeLanguageCode(target.language)) {
                playerInstance.value.selectAudioLanguage(target.language || '', target.role || '');
                refreshShakaTracks(playerInstance.value);
                return;
            }
            const fallback = (playerInstance.value.getVariantTracks ? playerInstance.value.getVariantTracks() : [])
                .find(track =>
                    String(track.language || '') === String(language || '')
                    && String(track.roles?.[0] || '') === String(role || '')
                );
            if (fallback) {
                playerInstance.value.selectVariantTrack(fallback, true);
                refreshShakaTracks(playerInstance.value);
            }
        };

        const switchTextTrack = async (trackId) => {
            if (!playerInstance.value) return;
            if (trackId === 'off') {
                selectedTextTrackId.value = 'off';
                if (typeof playerInstance.value.setTextTrackVisibility === 'function') {
                    await playerInstance.value.setTextTrackVisibility(false);
                } else if (typeof playerInstance.value.selectTextLanguage === 'function') {
                    playerInstance.value.selectTextLanguage('');
                }
                refreshShakaTracks(playerInstance.value);
                return;
            }
            const track = (playerInstance.value.getTextTracks ? playerInstance.value.getTextTracks() : [])
                .find(t => String(t.id) === String(trackId));
            if (!track) return;
            if (typeof playerInstance.value.selectTextTrack === 'function') {
                playerInstance.value.selectTextTrack(track);
            }
            if (typeof playerInstance.value.setTextTrackVisibility === 'function') {
                await playerInstance.value.setTextTrackVisibility(true);
            }
            selectedTextTrackId.value = String(track.id);
            refreshShakaTracks(playerInstance.value);
        };

        const toggleSeriesWatchingNow = async () => {
            const current = currentChannel.value;
            if (!current) return;
            const seriesId = resolveCurrentSeriesId(current);
            const episodeId = String(current.channelId || current.id || '').trim();
            const accountId = String(current.accountId || '').trim();
            if (!seriesId || !episodeId || !accountId) return;
            const payload = {
                accountId,
                categoryId: String(current.categoryId || currentContext.value.categoryId || ''),
                seriesId,
                episodeId,
                episodeName: current.name || current.channelName || '',
                season: current.season || '',
                episodeNum: current.episodeNum || ''
            };
            const shouldRemove = isCurrentSeriesInWatchingNow.value;
            try {
                await fetch(`${window.location.origin}/watchingNowSeriesAction`, {
                    method: shouldRemove ? 'DELETE' : 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(payload)
                });
                await loadWatchingNow({force: true, background: true});
            } catch (e) {
                console.error('Failed to update watching now series', e);
            }
        };

        const upsertSeriesWatchingNow = async () => {
            const current = currentChannel.value;
            if (!current) return;
            const seriesId = resolveCurrentSeriesId(current);
            const episodeId = String(current.channelId || current.id || '').trim();
            const accountId = String(current.accountId || '').trim();
            if (!seriesId || !episodeId || !accountId) return;
            const payload = {
                accountId,
                categoryId: String(current.categoryId || currentContext.value.categoryId || ''),
                seriesId,
                episodeId,
                episodeName: current.name || current.channelName || '',
                season: current.season || '',
                episodeNum: current.episodeNum || ''
            };
            try {
                await fetch(`${window.location.origin}/watchingNowSeriesAction`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(payload)
                });
                await loadWatchingNow({force: true, background: true});
            } catch (e) {
                console.error('Failed to update watching now series', e);
            }
        };

        const toggleVodWatchingNow = async () => {
            const current = currentChannel.value;
            if (!current) return;
            const vodId = resolveCurrentVodId(current);
            const accountId = String(current.accountId || '').trim();
            if (!vodId || !accountId) return;
            const payload = {
                accountId,
                categoryId: String(current.categoryId || currentContext.value.categoryId || ''),
                vodId,
                vodName: current.name || current.channelName || '',
                vodCmd: current.cmd || '',
                vodLogo: resolveLogoUrl(current.logo || '')
            };
            const shouldRemove = isCurrentVodInWatchingNow.value;
            try {
                await fetch(`${window.location.origin}/watchingNowVodAction`, {
                    method: shouldRemove ? 'DELETE' : 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(payload)
                });
                await loadWatchingNowVod({force: true, background: true});
            } catch (e) {
                console.error('Failed to update watching now vod', e);
            }
        };

        const ensureSeriesWatchingNow = async () => {
            const current = currentChannel.value;
            if (!current || normalizePlaybackMode(current.mode) !== 'series') return;
            await upsertSeriesWatchingNow();
        };

        const toggleFavorite = async () => {
            if (!currentChannel.value) return;
            const mode = normalizePlaybackMode(currentChannel.value?.mode || contentMode.value);
            if (mode === 'series') {
                await toggleSeriesWatchingNow();
                return;
            }
            if (mode === 'vod') {
                await toggleVodWatchingNow();
                return;
            }
            const existing = findBookmarkForChannel(currentChannel.value);
            try {
                if (existing?.dbId) {
                    await fetch(`${window.location.origin}/bookmarks?bookmarkId=${encodeURIComponent(existing.dbId)}`, {
                        method: 'DELETE'
                    });
                } else {
                    await fetch(`${window.location.origin}/bookmarks`, {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({
                            accountId: currentChannel.value.accountId || '',
                            categoryId: currentChannel.value.categoryId || '',
                            mode: currentChannel.value.mode || 'itv',
                            channelId: currentChannel.value.channelId || currentChannel.value.id || '',
                            name: currentChannel.value.name || currentChannel.value.channelName || '',
                            logo: resolveLogoUrl(currentChannel.value.logo || ''),
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
            await startPlayback(currentChannel.value.playRequestUrl, currentChannel.value);
        };

        const toggleRepeat = () => {
            repeatEnabled.value = !repeatEnabled.value;
        };

        const togglePictureInPicture = async () => {
            const video = videoPlayer.value;
            if (!video || !document.pictureInPictureEnabled) return;
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

        const toggleMute = () => {
            const nextMuted = !isMuted.value;
            isMuted.value = nextMuted;
            const video = videoPlayer.value;
            if (!video) return;
            if (!nextMuted && video.volume === 0) {
                video.volume = 1;
            }
            video.muted = nextMuted;
        };

        const requestFullscreenPlayer = async () => {
            const video = videoPlayer.value;
            if (!video || !document.fullscreenEnabled) return;
            try {
                if (document.fullscreenElement) {
                    await document.exitFullscreen();
                } else {
                    await video.requestFullscreen();
                }
            } catch (e) {
                console.warn('Fullscreen request failed', e);
            }
        };

        const ensurePlaybackNotPaused = async () => {
            if (!isPlaying.value) return;
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
            await window.UIPTVControls.onControlClick(event, action, ensurePlaybackNotPaused, ...args);
        };

        const mountSharedHeader = () => {
            const root = document.querySelector('[data-uiptv-shared-player]');
            if (!root || !window.UIPTVSharedPlayer) return;
            sharedHeader = window.UIPTVSharedPlayer.mount(root, {variant: root.dataset.variant || 'compact'});
            if (!sharedHeader) return;
            sharedHeader.bindActions({
                favorite: (event) => onPlayerControlClick(event, toggleFavorite),
                reload: (event) => onPlayerControlClick(event, reloadPlayback),
                repeat: (event) => onPlayerControlClick(event, toggleRepeat),
                pip: (event) => onPlayerControlClick(event, togglePictureInPicture),
                mute: (event) => onPlayerControlClick(event, toggleMute),
                fullscreen: (event) => onPlayerControlClick(event, requestFullscreenPlayer),
                'hide-panel': hidePlayerPanel,
                'expand-panel': togglePlayerExpanded,
                stop: (event) => onPlayerControlClick(event, stopPlaybackAndHide),
                'quality-menu': () => {},
                'audio-menu': () => {},
                'subtitle-menu': () => {}
            }, {hideMissing: false});
            syncSharedHeader();
            syncSharedMenus();
        };

        const syncSharedHeader = () => {
            if (!sharedHeader) return;
            const headerVisible = controlsVisible.value;
            const baseTitle = headerVisible ? (currentChannelName.value || currentChannelDebugTitle.value || '') : '';
            const modeLabel = currentChannelName.value && headerVisible ? playbackMode.value : '';
            sharedHeader.setState({
                repeatEnabled: repeatEnabled.value,
                isMuted: isMuted.value,
                isFullscreen: isFullscreen.value,
                isFavorite: isCurrentFavorite.value,
                isPlaying: headerVisible,
                isPanelExpanded: playerExpanded.value
            });
            sharedHeader.setTitle({
                title: baseTitle,
                mode: modeLabel,
                loading: playbackMode.value === 'loading' || playbackLoading.value,
                subtitle: ''
            });
        };

        const syncSharedMenus = () => {
            if (!sharedHeader) return;
            const maxEnabled = isMaxQualityEnabled();
            const abrEnabled = (() => {
                try {
                    return !!playerInstance.value?.getConfiguration()?.abr?.enabled;
                } catch (_) {
                    return false;
                }
            })();
            const qualityItems = [];
            qualityItems.push({
                label: 'Max',
                active: maxEnabled,
                onSelect: () => onPlayerControlClick(null, switchVideoMax)
            });
            qualityItems.push({
                label: 'Auto',
                active: abrEnabled && !maxEnabled,
                onSelect: () => onPlayerControlClick(null, switchVideoAuto)
            });
            (videoTracks.value || []).forEach((track) => {
                qualityItems.push({
                    label: formatVideoTrackLabel(track),
                    active: !!track.active && !abrEnabled && !maxEnabled,
                    onSelect: () => onPlayerControlClick(null, switchVideoTrack, track.id)
                });
            });

            const audioItems = [];
            if (!audioTracks.value || audioTracks.value.length === 0) {
                audioItems.push({label: 'No audio tracks', disabled: true, muted: true});
            } else {
                audioTracks.value.forEach((track, index) => {
                    audioItems.push({
                        label: formatAudioTrackLabel(track, index),
                        active: !!track.active,
                        onSelect: () => onPlayerControlClick(null, switchAudioTrack, track.id)
                    });
                });
            }

            const subtitleItems = [];
            subtitleItems.push({
                label: 'Off',
                active: String(selectedTextTrackId.value) === 'off',
                onSelect: () => onPlayerControlClick(null, switchTextTrack, 'off')
            });
            if (!textTracks.value || textTracks.value.length === 0) {
                subtitleItems.push({label: 'No subtitles', disabled: true, muted: true});
            } else {
                textTracks.value.forEach((track, index) => {
                    subtitleItems.push({
                        label: formatTextTrackLabel(track, index),
                        active: String(selectedTextTrackId.value) === String(track.id),
                        onSelect: () => onPlayerControlClick(null, switchTextTrack, track.id)
                    });
                });
            }

            sharedHeader.setMenus({
                quality: qualityItems,
                audio: audioItems,
                subtitle: subtitleItems
            });
        };

        const isImageFallbackElement = (element) => {
            return !!element && (
                element.classList.contains('icon-placeholder')
                || element.classList.contains('logo-box')
                || element.classList.contains('poster-fallback')
            );
        };

        const imageError = (e) => {
            const image = e.target;
            image.classList.add('image-load-failed');
            image.style.display = 'none';
            const fallback = image.nextElementSibling;
            if (isImageFallbackElement(fallback)) {
                fallback.classList.add('image-fallback-visible');
                fallback.style.display = '';
            }
        };

        const imageLoad = (e) => {
            const image = e.target;
            image.classList.remove('image-load-failed');
            image.style.display = '';
            const fallback = image.nextElementSibling;
            if (isImageFallbackElement(fallback)) {
                fallback.classList.remove('image-fallback-visible');
                fallback.style.display = 'none';
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

        const mediaImageLoad = (e) => {
            e.target.style.display = '';
            const fallback = e.target.parentElement?.querySelector('.nf-media-fallback');
            if (fallback) {
                fallback.classList.remove('show');
            }
        };

        const scrollToTop = () => {
            window.scrollTo({top: 0, behavior: 'smooth'});
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
                // Playback cleanup is best-effort before a full reload.
            }

            try {
                if ('serviceWorker' in navigator) {
                    const registrations = await navigator.serviceWorker.getRegistrations();
                    await Promise.all(registrations.map(registration => registration.unregister()));
                }
            } catch (_) {
                // Cache refresh should continue even when service worker cleanup fails.
            }

            try {
                if ('caches' in window) {
                    const keys = await caches.keys();
                    await Promise.all(keys.map(key => caches.delete(key)));
                }
            } catch (_) {
                // Browser cache APIs can be unavailable in restricted contexts.
            }

            window.location.reload();
        };

        const resetApp = () => {
            stopPlayback();
            activeTab.value = 'bookmarks';
            playerExpanded.value = false;
            playerManuallyHidden.value = false;
            viewState.value = 'accounts';
            contentMode.value = 'itv';
            categories.value = [];
            channels.value = [];
            episodes.value = [];
            stickyStates.value = createStickyStates();
            selectedAccountId.value = null;
            currentContext.value = {accountId: null, categoryId: null, accountType: null};
            clearSearch();
            selectedBookmarkCategoryId.value = RECENTLY_PLAYED_BOOKMARKS_CATEGORY_ID;
            watchingNowRows.value = [];
            selectedWatchingNowKey.value = '';
            watchingNowDrilldown.value = false;
            watchingNowLoadedAt.value = 0;
            watchingNowVodRows.value = [];
            selectedWatchingNowVodKey.value = '';
            watchingNowVodLoadedAt.value = 0;
            watchingNowVodLoading.value = false;
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
            loadRecentBookmarkHistory();
            if (document.fullscreenEnabled) {
                document.addEventListener('fullscreenchange', () => {
                    isFullscreen.value = !!document.fullscreenElement;
                });
                isFullscreen.value = !!document.fullscreenElement;
            }
            mountSharedHeader();
            await loadConfig();
            await Promise.all([
                loadAccounts(),
                loadBookmarkCategories(),
                loadBookmarks(),
                loadWatchingNow(),
                loadWatchingNowVod()
            ]);

            const storedTheme = localStorage.getItem('uiptv_theme');
            if (storedTheme) {
                theme.value = storedTheme;
            }
            applyTheme();

            const launchPayload = parseDrmLaunchPayload();
            if (launchPayload) {
                await launchPlaybackFromPayload(launchPayload);
            } else {
                await launchPlaybackFromQueryParams();
            }
        });

        watch(currentChannelDebugTitle, () => {
            setBrowserTitle();
        }, {immediate: true});

        watch([isPlaying, controlsVisible, repeatEnabled, isMuted, isFullscreen, isCurrentFavorite, playbackMode, playbackLoading, currentChannelName, currentChannelDebugTitle, strategyOverride], () => {
            syncSharedHeader();
        }, {immediate: true});

        watch([videoTracks, audioTracks, textTracks, selectedTextTrackId], () => {
            syncSharedMenus();
        }, {deep: true});

        watch(playerExpanded, () => {
            syncPlayerExpandedClass();
        }, {immediate: true});

        return {
            activeTab,
            viewState,
            currentContext,
            searchQuery,
            searchVisible,
            searchPlaceholder,
            searchFieldVisible,
            selectedAccountTypeFilter,
            filteredAccounts,
            filteredCategories,
            filteredChannels,
            filteredEpisodes,
            filteredSeriesEpisodes,
            filteredBookmarks,
            bookmarkCardTitle,
            bookmarkEmptyTitle,
            bookmarkEmptyDetail,
            bookmarkStatusText,
            filteredWatchingNowRows,
            filteredWatchingNowVodRows,
            selectedWatchingNowKey,
            watchingNowTab,
            selectedWatchingNowVodKey,
            watchingNowDrilldown,
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
            currentChannelDebugTitle,
            currentAccountName,
            currentAccountTypeLabel,
            currentCategoryTitle,
            contentPanelTitle,
            playbackMode,
            playbackCurrentTime,
            playbackDuration,
            playbackSeekable,
            widePlayerProgressVisible,
            isActiveChannel,
            isActiveBookmark,
            isActiveWatchingNowRow,
            isActiveWatchingNowVodRow,
            isDisabledChannel,
            isDisabledBookmark,
            isCurrentFavorite,
            isPlaying,
            playbackError,
            showOverlay,
            showBookmarkModal,
            playerExpanded,
            hasPlayerContent,
            playerPanelVisible,
            bingeWatchLoading,
            canBingeWatchSeason,
            bingeWatchButtonLabel,
            listLoading,
            listLoadingMessage,
            watchingNowVodLoading,
            canReorderBookmarks,
            isRecentlyPlayedBookmarksSelected,
            draggedBookmarkId,
            dragOverBookmarkId,
            videoPlayer,
            videoTracks,
            audioTracks,
            textTracks,
            selectedTextTrackId,
            repeatEnabled,
            isMuted,
            isFullscreen,
            playbackLoading,
            theme,
            themeIcon,
            contentMode,
            contentModeLabels,
            supportsVodSeriesForSelectedAccount,
            isPinnedAccount,
            resolvePinSvg,
            resolvePinColor,
            selectedAccountId,
            formatAccountType,
            selectAccountTypeFilter,

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
            getSeriesEpisodeAnchorId,
            getVodPlot,
            getImdbUrl,
            formatShortDate,
            fallbackInitials,
            playVodFromDetail,
            startBingeWatchSeason,
            selectBookmarkCategory,
            onBookmarkOverflowSelect,
            playChannel,
            handleChannelSelection,
            playBookmark,
            playWatchingNowVodRow,
            openWatchingNowSeriesDetail,
            goBackToWatchingNow,
            setWatchingNowTab,
            toggleSearch,
            focusSearch,
            hidePlayerPanel,
            togglePlayerPanel,
            togglePlayerExpanded,
            formatPlaybackTime,
            seekPlayback,
            removeRecentlyPlayedBookmark,
            onBookmarkCardClick,
            onBookmarkDragStart,
            onBookmarkDragOver,
            onBookmarkDrop,
            onBookmarkDragEnd,
            stopPlayback,
            stopPlaybackAndHide,
            toggleFavorite,
            reloadPlayback,
            toggleRepeat,
            togglePictureInPicture,
            toggleMute,
            requestFullscreenPlayer,
            onPlayerControlClick,
            imageError,
            imageLoad,
            mediaImageError,
            mediaImageLoad,
            toggleTheme,
            clearWebCacheAndReload,
            resetApp,
            switchVideoTrack,
            switchVideoAuto,
            switchVideoMax,
            switchAudioTrack,
            switchTextTrack,
            formatVideoTrackLabel,
            formatAudioTrackLabel,
            formatTextTrackLabel
        };
    }
}).mount('#app');
