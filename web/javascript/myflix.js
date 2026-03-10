const {createApp, ref, computed, onMounted, onUnmounted, nextTick} = Vue;

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
        const watchingNowRows = ref([]);
        const selectedWatchingNowKey = ref('');
        const watchingNowVodRows = ref([]);
        const selectedWatchingNowVodKey = ref('');

        const createBrowserState = () => ({
            viewState: ref('categories'), // categories, channels, episodes, seriesDetail, vodDetail
            accountId: ref(null),
            accountType: ref(null),
            categoryId: ref(null),
            selectedSeriesId: ref(''),
            selectedSeriesCategoryId: ref(''),
            selectedSeason: ref(''),
            selectedEpisodeId: ref(''),
            selectedEpisodeNum: ref(''),
            selectedEpisodeName: ref(''),
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
        const playbackError = ref('');
        const playerLoading = ref(false);
        const showOverlay = ref(false);
        const showBookmarkModal = ref(false);
        const repeatEnabled = ref(false);
        const lastPlaybackUrl = ref('');
        const repeatInFlight = ref(false);
        const thumbnailsEnabled = ref(true);

        const playerKey = ref(0);
        const isYoutube = ref(false);
        const youtubeSrc = ref('');
        const playerInstance = ref(null);
        const mpegtsPlayer = ref(null);
        const videoPlayerModal = ref(null);
        const playerModalFrame = ref(null);
        const playerInlineRef = ref(null);
        const videoTracks = ref([]);
        const audioTracks = ref([]);
        const textTracks = ref([]);
        const selectedTextTrackId = ref('off');
        const playbackLifecycleId = ref(0);
        const isBusy = ref(false);
        const busyMessage = ref('Loading...');
        const playbackMode = ref('');
        const modeLoadingCount = ref({itv: 0, vod: 0, series: 0, bookmarks: 0, watchingNow: 0, watchingNowVod: 0});

        const playbackUtils = window.UIPTVPlaybackUtils;
        const isTsLikeUrl = (url, manifestType = '') => playbackUtils.isTsLikeUrl(url, manifestType);
        const canUseMpegts = () => playbackUtils.canUseMpegts();
        const normalizeDisplayText = (value) => playbackUtils.normalizeDisplayText(value);

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
        const normalizeWebPlaybackUrl = (rawUrl) => playbackUtils.normalizeWebPlaybackUrl(rawUrl);
        const downgradeHttpsToHttpForKnownPaths = (url) => playbackUtils.downgradeHttpsToHttpForKnownPaths(url);
        const buildForcedHlsPlaybackRequestUrl = (rawUrl) => playbackUtils.buildForcedHlsPlaybackRequestUrl(rawUrl);

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

        const filteredWatchingNowRows = computed(() => {
            const q = String(searchQuery.value || '').trim().toLowerCase();
            if (!q) return watchingNowRows.value;
            return (watchingNowRows.value || []).filter(row => {
                if ((row?.seriesTitle || '').toLowerCase().includes(q)) return true;
                if ((row?.accountName || '').toLowerCase().includes(q)) return true;
                return Array.isArray(row?.episodes) && row.episodes.some(ep => (ep?.name || '').toLowerCase().includes(q));
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

        const selectedWatchingNowRow = computed(() => {
            const key = String(selectedWatchingNowKey.value || '');
            if (!key) return null;
            return filteredWatchingNowRows.value.find(row => String(row?.key || '') === key)
                || watchingNowRows.value.find(row => String(row?.key || '') === key)
                || null;
        });

        const isAllCategory = (category) => {
            const title = String(category?.title || '').trim().toLowerCase();
            const dbId = String(category?.dbId || '').trim().toLowerCase();
            const categoryId = String(category?.categoryId || '').trim().toLowerCase();
            return title === 'all' || dbId === 'all' || categoryId === 'all';
        };

        const bookmarkCategoryTabs = computed(() => {
            const tabs = [{id: '', name: 'All'}];
            for (const category of (bookmarkCategories.value || [])) {
                const id = String(category?.id || '').trim();
                const name = String(normalizeDisplayText(category?.name || '') || '').trim();
                if (!id || !name) continue;
                tabs.push({id, name});
            }
            return tabs;
        });

        const filterBySearch = (arr, field) => {
            if (!searchQuery.value) return arr;
            const q = searchQuery.value.toLowerCase();
            return arr.filter(i => (i?.[field] || '').toLowerCase().includes(q));
        };

        const normalizeWatchedFlag = (item = {}) => ({
            ...item,
            watched: item.watched === true
                || item.watched === 1
                || String(item.watched || '').toLowerCase() === 'true'
                || String(item.watched || '') === '1'
        });

        const normalizeChannel = (item = {}) => ({
            ...normalizeWatchedFlag(item),
            name: resolveDisplayName(item) || normalizeDisplayText(item.name) || item.name || '',
            channelName: normalizeDisplayText(item.channelName) || item.channelName || '',
            title: normalizeDisplayText(item.title) || item.title || '',
            logo: resolveLogoUrl(item.logo)
        });

        const normalizeChannelList = (items) => (Array.isArray(items) ? items.map(normalizeChannel) : []);

        const filteredModeCategories = (mode) => filterBySearch(browsers[mode].categories.value, 'title');
        const filteredModeChannels = (mode) => filterBySearch(browsers[mode].channels.value, 'name');
        const filteredModeEpisodes = (mode) => filterBySearch(browsers[mode].episodes.value, 'name');
        const visibleChannelsByMode = ref({
            itv: CHANNEL_BATCH_SIZE,
            vod: CHANNEL_BATCH_SIZE,
            series: CHANNEL_BATCH_SIZE
        });
        const visibleEpisodesByMode = ref({
            itv: EPISODE_BATCH_SIZE,
            vod: EPISODE_BATCH_SIZE,
            series: EPISODE_BATCH_SIZE
        });
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
                .map(s => ({value: s, label: `Season ${s}`}));
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

        const getSeriesDetailEpisodeAnchorId = (episode) => {
            const idPart = String(episode?.channelId || episode?.id || episode?.dbId || '').trim();
            const season = resolveEpisodeSeason(episode) || '0';
            const epNum = resolveEpisodeNumber(episode) || '0';
            return `myflix-series-ep-${idPart || `${season}-${epNum}`}-${season}-${epNum}`;
        };

        const scrollToWatchedSeriesDetailEpisode = async () => {
            if (!isSeriesDetailOpen.value) return;
            const b = browsers.series;
            const targetEpisode = findTargetSeriesEpisode(filteredSeriesDetailEpisodes.value, {
                episodeId: b.selectedEpisodeId.value,
                season: b.selectedSeason.value || selectedSeriesSeason.value,
                episodeNum: b.selectedEpisodeNum.value,
                episodeName: b.selectedEpisodeName.value
            });
            if (!targetEpisode) return;
            const targetIndex = filteredSeriesDetailEpisodes.value.findIndex(ep =>
                getSeriesDetailEpisodeAnchorId(ep) === getSeriesDetailEpisodeAnchorId(targetEpisode)
            );
            if (targetIndex >= 0 && visibleSeriesDetailEpisodesCount.value <= targetIndex) {
                visibleSeriesDetailEpisodesCount.value = targetIndex + 5;
            }
            const anchorId = getSeriesDetailEpisodeAnchorId(targetEpisode);
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

        const setDefaultSeriesSeason = (preferredSeason = '') => {
            const b = browsers.series;
            const tabs = seriesSeasonTabs.value;
            if (!tabs.length) {
                selectedSeriesSeason.value = '';
                b.selectedSeason.value = '';
                visibleSeriesDetailEpisodesCount.value = DETAIL_EPISODE_BATCH_SIZE;
                return;
            }
            const preferred = String(preferredSeason || '');
            if (preferred && tabs.some(tab => tab.value === preferred)) {
                selectedSeriesSeason.value = preferred;
            } else {
                selectedSeriesSeason.value = tabs[0].value;
            }
            b.selectedSeason.value = selectedSeriesSeason.value;
            visibleSeriesDetailEpisodesCount.value = DETAIL_EPISODE_BATCH_SIZE;
        };

        const selectSeriesSeason = (season) => {
            selectedSeriesSeason.value = season;
            browsers.series.selectedSeason.value = String(season || '');
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
                    logo: resolveLogoUrl(meta.logo || episode.logo),
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
        const currentChannelDebugTitle = computed(() => {
            const name = String(currentChannelName.value || '').trim();
            const mode = String(playbackMode.value || '').trim();
            if (!name) return '';
            return mode ? `${name} [${mode}]` : name;
        });
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

        const resolveLogoUrl = (logo) => {
            if (!thumbnailsEnabled.value) return '';
            const raw = String(logo || '').trim();
            if (!raw) return '';
            if (/^(data:|blob:|https?:\/\/|file:)/i.test(raw)) return raw;
            if (raw.startsWith('//')) return `${window.location.protocol}${raw}`;
            if (raw.startsWith('/')) return `${window.location.origin}${raw}`;
            return `${window.location.origin}/${raw.replace(/^\.?\//, '')}`;
        };

        const normalizePlaybackMode = (value, fallback = '') => String(value || fallback || '').trim().toLowerCase();

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
                || browsers.series?.selectedSeriesId?.value
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
            const mode = normalizePlaybackMode(currentChannel.value?.mode || '');
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

        const withSyntheticAllCategory = (items, accountType) => {
            const list = Array.isArray(items) ? [...items] : [];
            const normalizedType = String(accountType || '').toUpperCase();
            const isStalkerOrXtreme = normalizedType === 'STALKER_PORTAL' || normalizedType === 'XTREME_API';
            const hasAll = list.some(isAllCategory);
            if (hasAll) return list;
            if (isStalkerOrXtreme && list.length < 2) return list;
            return [{dbId: 'all', categoryId: 'all', title: 'All'}, ...list];
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
                const incoming = normalizeChannelList(data);
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
            b.selectedSeriesId.value = '';
            b.selectedSeriesCategoryId.value = '';
            b.selectedSeason.value = '';
            b.selectedEpisodeId.value = '';
            b.selectedEpisodeNum.value = '';
            b.selectedEpisodeName.value = '';
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
            b.selectedSeriesId.value = '';
            b.selectedSeriesCategoryId.value = '';
            b.selectedSeason.value = '';
            b.selectedEpisodeId.value = '';
            b.selectedEpisodeNum.value = '';
            b.selectedEpisodeName.value = '';
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
                b.categories.value = withSyntheticAllCategory(await response.json(), b.accountType.value);
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
            b.selectedSeriesId.value = '';
            b.selectedSeriesCategoryId.value = '';
            b.selectedSeason.value = '';
            b.selectedEpisodeId.value = '';
            b.selectedEpisodeNum.value = '';
            b.selectedEpisodeName.value = '';
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

        const loadModeEpisodes = async (mode, seriesId, seriesCategoryId = '') => {
            const b = browsers[mode];
            b.selectedSeriesId.value = String(seriesId || '');
            if (mode === 'series') {
                b.selectedSeriesCategoryId.value = String(seriesCategoryId || b.selectedSeriesCategoryId.value || b.categoryId.value || '');
            }
            const effectiveCategoryId = mode === 'series'
                ? String(b.selectedSeriesCategoryId.value || b.categoryId.value || '')
                : String(b.categoryId.value || '');
            try {
                startModeLoading(mode);
                isBusy.value = true;
                busyMessage.value = 'Loading episodes...';
                const response = await fetch(
                    `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}&categoryId=${encodeURIComponent(effectiveCategoryId)}`
                );
                b.episodes.value = normalizeChannelList(await response.json());
                visibleEpisodesByMode.value[mode] = EPISODE_BATCH_SIZE;
                b.viewState.value = 'episodes';
                searchQuery.value = '';
                if (mode === 'series') {
                    setDefaultSeriesSeason(resolvePreferredSeriesSeason(b.episodes.value, selectedSeriesSeason.value, {
                        season: b.selectedSeason.value,
                        episodeId: b.selectedEpisodeId.value,
                        episodeNum: b.selectedEpisodeNum.value,
                        episodeName: b.selectedEpisodeName.value
                    }));
                    await scrollToWatchedSeriesDetailEpisode();
                }
            } catch (e) {
                console.error('Failed to load episodes', e);
            } finally {
                stopModeLoading(mode);
                isBusy.value = false;
            }
        };

        const loadModeSeriesChildren = async (mode, movieId) => {
            const b = browsers[mode];
            b.selectedSeriesId.value = String(movieId || '');
            try {
                startModeLoading(mode);
                isBusy.value = true;
                busyMessage.value = 'Loading series...';
                const response = await fetch(
                    `${window.location.origin}/channels?categoryId=${b.categoryId.value}&accountId=${b.accountId.value}&mode=${mode}&movieId=${encodeURIComponent(movieId)}`
                );
                b.episodes.value = normalizeChannelList(await response.json());
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
            const seriesCategoryId = String(seriesItem?.categoryId || b.categoryId.value || '');
            const previousSeriesId = String(b.selectedSeriesId.value || '');

            b.navigationStack.value.push({
                state: b.viewState.value,
                channels: b.channels.value,
                episodes: b.episodes.value,
                detail: b.detail.value,
                selectedSeriesId: b.selectedSeriesId.value,
                selectedSeriesCategoryId: b.selectedSeriesCategoryId.value
            });

            const detail = {
                seriesId: String(seriesId || ''),
                name: seriesItem.name || 'Series',
                cover: resolveLogoUrl(seriesItem.logo || ''),
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

            b.selectedSeriesId.value = String(seriesId || '');
            b.selectedSeriesCategoryId.value = seriesCategoryId;
            if (previousSeriesId !== String(seriesId || '')) {
                b.selectedSeason.value = '';
                b.selectedEpisodeId.value = '';
                b.selectedEpisodeNum.value = '';
                b.selectedEpisodeName.value = '';
            }
            b.detail.value = detail;
            b.episodes.value = [];
            b.viewState.value = 'seriesDetail';
            searchQuery.value = '';
            startModeLoading('series');

            try {
                if (b.accountType.value === 'XTREME_API') {
                    const response = await fetch(
                        `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}&categoryId=${encodeURIComponent(seriesCategoryId)}`
                    );
                    b.episodes.value = normalizeChannelList(await response.json());
                } else {
                    const response = await fetch(
                        `${window.location.origin}/channels?categoryId=${b.categoryId.value}&accountId=${b.accountId.value}&mode=series&movieId=${encodeURIComponent(seriesId)}`
                    );
                    b.episodes.value = normalizeChannelList(await response.json());
                }
            } catch (e) {
                console.error('Failed to load series episodes', e);
                b.episodes.value = [];
            } finally {
                stopModeLoading('series');
            }
            setDefaultSeriesSeason(resolvePreferredSeriesSeason(b.episodes.value, selectedSeriesSeason.value, {
                season: b.selectedSeason.value,
                episodeId: b.selectedEpisodeId.value,
                episodeNum: b.selectedEpisodeNum.value,
                episodeName: b.selectedEpisodeName.value
            }));
            await scrollToWatchedSeriesDetailEpisode();

            // Fetch heavy metadata in background so episodes become visible ASAP.
            (async () => {
                startModeLoading('series');
                try {
                    const response = await fetch(
                        `${window.location.origin}/seriesDetails?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}&categoryId=${encodeURIComponent(seriesCategoryId)}&seriesName=${encodeURIComponent(seriesItem.name || '')}`
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
                        b.episodes.value = normalizeChannelList(data.episodes);
                    }
                    b.episodes.value = enrichEpisodesFromMeta(b.episodes.value, detail);
                    b.detail.value = {...detail};
                    setDefaultSeriesSeason(resolvePreferredSeriesSeason(b.episodes.value, selectedSeriesSeason.value, {
                        season: b.selectedSeason.value,
                        episodeId: b.selectedEpisodeId.value,
                        episodeNum: b.selectedEpisodeNum.value,
                        episodeName: b.selectedEpisodeName.value
                    }));
                    await scrollToWatchedSeriesDetailEpisode();
                } catch (e) {
                    console.error('Failed to load series details', e);
                } finally {
                    stopModeLoading('series');
                }
            })();
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
                    b.selectedSeriesId.value = previous.selectedSeriesId || '';
                    b.selectedSeriesCategoryId.value = previous.selectedSeriesCategoryId || '';
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
                    b.selectedSeriesCategoryId.value = '';
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

        const resolvePlaybackCategoryIdForChannel = (channel, mode, browserState) => {
            if (mode === 'series') {
                return String(browserState?.selectedSeriesCategoryId?.value || channel?.categoryId || browserState.categoryId.value || '');
            }
            const scopedCategoryId = String(browserState?.categoryId?.value || '');
            const channelCategoryId = String(channel?.categoryId || '');
            if (scopedCategoryId.toLowerCase() === 'all' && channelCategoryId) {
                return channelCategoryId;
            }
            return String(channelCategoryId || scopedCategoryId || '');
        };

        const buildPlayerUrlForChannel = (channel, mode, browserState) => {
            const query = new URLSearchParams();
            query.set('accountId', browserState.accountId.value || '');
            const scopedCategoryId = resolvePlaybackCategoryIdForChannel(channel, mode, browserState);
            query.set('categoryId', scopedCategoryId);
            query.set('mode', mode);

            const dbId = channel.dbId || '';
            const channelIdentifier = channel.channelId || channel.id || dbId;
            const seriesEpisodeIdentifier = channel.channelId || channel.id || '';
            const seriesParentId = mode === 'series'
                ? String(browserState?.detail?.value?.seriesId || browserState?.selectedSeriesId?.value || '')
                : '';

            if (mode === 'series') {
                const resolvedSeason = resolvePlaybackSeason(channel);
                const resolvedEpisodeNum = resolvePlaybackEpisodeNumber(channel);
                // Series watch pointer must use real episode channelId, not cached DB row id.
                query.set('channelId', channelIdentifier || '');
                query.set('seriesId', seriesEpisodeIdentifier || '');
                query.set('seriesParentId', seriesParentId);
                query.set('name', channel.name || '');
                query.set('logo', resolveLogoUrl(channel.logo || ''));
                query.set('cmd', channel.cmd || '');
                query.set('season', resolvedSeason);
                query.set('episodeNum', resolvedEpisodeNum);
                query.set('cmd_1', channel.cmd_1 || '');
                query.set('cmd_2', channel.cmd_2 || '');
                query.set('cmd_3', channel.cmd_3 || '');
                query.set('drmType', channel.drmType || '');
                query.set('drmLicenseUrl', channel.drmLicenseUrl || '');
                query.set('clearKeysJson', channel.clearKeysJson || '');
                query.set('inputstreamaddon', channel.inputstreamaddon || '');
                query.set('manifestType', channel.manifestType || '');
                appendPlaybackCompatParams(query, mode);
                return `${window.location.origin}/player?${query.toString()}`;
            }

            if (dbId) {
                query.set('channelId', dbId);
            } else {
                query.set('channelId', channelIdentifier || '');
                query.set('name', channel.name || '');
                query.set('logo', resolveLogoUrl(channel.logo || ''));
                query.set('cmd', channel.cmd || '');
                query.set('season', channel.season || '');
                query.set('episodeNum', channel.episodeNum || '');
                query.set('cmd_1', channel.cmd_1 || '');
                query.set('cmd_2', channel.cmd_2 || '');
                query.set('cmd_3', channel.cmd_3 || '');
                query.set('drmType', channel.drmType || '');
                query.set('drmLicenseUrl', channel.drmLicenseUrl || '');
                query.set('clearKeysJson', channel.clearKeysJson || '');
                query.set('inputstreamaddon', channel.inputstreamaddon || '');
                query.set('manifestType', channel.manifestType || '');
            }

            appendPlaybackCompatParams(query, mode);
            return `${window.location.origin}/player?${query.toString()}`;
        };

        const playChannel = (channel, mode = 'itv') => {
            const b = browsers[mode];
            const playbackCategoryId = resolvePlaybackCategoryIdForChannel(channel, mode, b);
            const channelIdentifier = channel.dbId || channel.channelId || channel.id;
            const seriesEpisodeIdentifier = channel.channelId || channel.id || '';
            currentChannel.value = {
                id: channelIdentifier,
                dbId: channel.dbId || '',
                channelId: mode === 'series'
                    ? seriesEpisodeIdentifier
                    : (channel.channelId || channelIdentifier),
                name: channel.name,
                logo: resolveLogoUrl(channel.logo),
                cmd: channel.cmd,
                drmType: channel.drmType,
                drmLicenseUrl: channel.drmLicenseUrl,
                clearKeysJson: channel.clearKeysJson,
                inputstreamaddon: channel.inputstreamaddon,
                manifestType: channel.manifestType,
                season: mode === 'series' ? resolvePlaybackSeason(channel) : '',
                episodeNum: mode === 'series' ? resolvePlaybackEpisodeNumber(channel) : '',
                seriesParentId: mode === 'series' ? String(b.selectedSeriesId.value || '') : '',
                accountId: b.accountId.value,
                accountName: resolveAccountName(b.accountId.value),
                categoryId: playbackCategoryId,
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
            scrollToInlinePlayer();
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

        const BOOKMARK_PAGE_SIZE = 25;
        const bookmarkWatchUtils = window.UIPTVBookmarkWatchUtils;

        const loadBookmarks = async () => {
            startModeLoading('bookmarks');
            await bookmarkWatchUtils.loadBookmarksPaged({
                origin: window.location.origin,
                pageSize: BOOKMARK_PAGE_SIZE,
                normalize: (item) => bookmarkWatchUtils.normalizeBookmark(item, resolveLogoUrl),
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
                    stopModeLoading('bookmarks');
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
            key: String(row.key || `${row.accountId || ''}|${row.categoryId || ''}|${row.seriesId || ''}`),
            categoryDbId: String(row.categoryDbId || ''),
            episodeId: String(row.episodeId || ''),
            episodeName: String(row.episodeName || ''),
            season: String(row.season || ''),
            episodeNum: String(row.episodeNum || ''),
            seriesPoster: resolveLogoUrl(row.seriesPoster),
            episodes: normalizeChannelList(row.episodes)
        });

        const normalizeWatchingNowVodRow = (row = {}) => bookmarkWatchUtils.normalizeWatchingNowVodRow(
            row,
            resolveLogoUrl,
            normalizeChannel
        );

        const loadWatchingNow = async () => {
            try {
                startModeLoading('watchingNow');
                const response = await fetch(`${window.location.origin}/watchingNow`);
                watchingNowRows.value = (await response.json()).map(normalizeWatchingNowRow);
                const selectedKey = String(selectedWatchingNowKey.value || '');
                const exists = watchingNowRows.value.some(row => String(row?.key || '') === selectedKey);
                selectedWatchingNowKey.value = exists ? selectedKey : '';
            } catch (e) {
                console.error('Failed to load watching now', e);
                watchingNowRows.value = [];
                selectedWatchingNowKey.value = '';
            } finally {
                stopModeLoading('watchingNow');
            }
        };

        const loadWatchingNowVod = async () => {
            try {
                startModeLoading('watchingNowVod');
                const rows = await bookmarkWatchUtils.fetchWatchingNowVod(window.location.origin);
                watchingNowVodRows.value = rows.map(normalizeWatchingNowVodRow);
                const selectedKey = String(selectedWatchingNowVodKey.value || '');
                const exists = watchingNowVodRows.value.some(row => String(row?.key || '') === selectedKey);
                selectedWatchingNowVodKey.value = exists ? selectedKey : '';
            } catch (e) {
                console.error('Failed to load watching now vod', e);
                watchingNowVodRows.value = [];
                selectedWatchingNowVodKey.value = '';
            } finally {
                stopModeLoading('watchingNowVod');
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

        const scrollToInlinePlayer = () => {
            nextTick(() => {
                const target = playerInlineRef.value;
                if (!target) return;
                const rect = target.getBoundingClientRect();
                const absoluteTop = rect.top + window.pageYOffset;
                window.scrollTo({top: Math.max(absoluteTop - 8, 0), behavior: 'smooth'});
            });
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
            startPlayback(playbackUrl);
            scrollToInlinePlayer();
        };

        const openWatchingNowSeriesDetail = async (row) => {
            if (!row) return;
            selectedWatchingNowKey.value = String(row.key || '');
            const b = browsers.series;
            b.accountId.value = row.accountId || null;
            b.accountType.value = row.accountType || null;
            b.categoryId.value = row.categoryDbId || row.categoryId || '';
            b.selectedSeriesId.value = String(row.seriesId || '');
            b.selectedSeriesCategoryId.value = String(row.categoryDbId || row.categoryId || '');
            b.selectedSeason.value = String(row.season || '');
            b.selectedEpisodeId.value = String(row.episodeId || '');
            b.selectedEpisodeNum.value = String(row.episodeNum || '');
            b.selectedEpisodeName.value = String(row.episodeName || '');
            selectedAccountId.value = String(row.accountId || '');
            await openSeriesDetails({
                channelId: row.seriesId || '',
                id: row.seriesId || '',
                dbId: row.seriesId || '',
                categoryId: row.categoryId || '',
                name: row.seriesTitle || 'Series',
                logo: resolveLogoUrl(row.seriesPoster || '')
            });
        };

        const playWatchingNowVodRow = (row) => {
            if (!row) return;
            selectedWatchingNowVodKey.value = String(row.key || '');
            const b = browsers.vod;
            b.accountId.value = row.accountId || null;
            b.accountType.value = row.accountType || null;
            b.categoryId.value = row.categoryId || '';
            selectedAccountId.value = String(row.accountId || '');
            const base = normalizeChannel(row.playItem || {});
            const channelIdentifier = base.dbId || base.channelId || base.id || row.vodId || '';
            const playbackUrl = buildPlayerUrlForChannel({
                ...base,
                channelId: base.channelId || channelIdentifier,
                categoryId: row.categoryId || base.categoryId || '',
                name: base.name || row.vodName || 'VOD',
                logo: base.logo || row.vodLogo || ''
            }, 'vod', b);
            currentChannel.value = {
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
                accountName: resolveAccountName(row.accountId || ''),
                categoryId: row.categoryId || '',
                type: 'channel',
                mode: 'vod',
                playRequestUrl: playbackUrl
            };
            startPlayback(playbackUrl);
            scrollToInlinePlayer();
        };

        const isPlaybackRequestActive = (lifecycleId) => lifecycleId === playbackLifecycleId.value;

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

        const markCurrentSeriesEpisodeWatchedLocally = (lifecycleId) => {
            if (!isPlaybackRequestActive(lifecycleId)) return;
            const b = browsers.series;
            if (!currentChannel.value || currentChannel.value.mode !== 'series' || !Array.isArray(b.episodes.value) || b.episodes.value.length === 0) {
                return;
            }
            const watched = {
                channelId: currentChannel.value.channelId || currentChannel.value.id || '',
                season: currentChannel.value.season || '',
                episodeNum: currentChannel.value.episodeNum || ''
            };
            b.episodes.value = b.episodes.value.map(ep => ({...ep, watched: isEpisodeMatch(watched, ep)}));
        };

        const refreshSeriesEpisodeWatchState = async (lifecycleId) => {
            if (!isPlaybackRequestActive(lifecycleId)) return;
            const b = browsers.series;
            const seriesId = String(b?.selectedSeriesId?.value || b?.detail?.value?.seriesId || '');
            if (!seriesId || !b.accountId.value) {
                return;
            }
            const categoryId = String(b?.selectedSeriesCategoryId?.value || b.categoryId.value || '');
            try {
                let response;
                if (String(b.accountType.value || '').toUpperCase() === 'XTREME_API') {
                    response = await fetch(
                        `${window.location.origin}/seriesEpisodes?seriesId=${encodeURIComponent(seriesId)}&accountId=${b.accountId.value}&categoryId=${encodeURIComponent(categoryId)}`
                    );
                } else {
                    response = await fetch(
                        `${window.location.origin}/channels?categoryId=${encodeURIComponent(categoryId)}&accountId=${b.accountId.value}&mode=series&movieId=${encodeURIComponent(seriesId)}`
                    );
                }
                if (!isPlaybackRequestActive(lifecycleId)) return;
                b.episodes.value = enrichEpisodesFromMeta(normalizeChannelList(await response.json()), b.detail.value || null);
                await loadWatchingNow();
            } catch (e) {
                console.warn('Failed to refresh series watch state', e);
            }
        };

        const startPlayback = async (url) => {
            playbackLifecycleId.value++;
            const lifecycleId = playbackLifecycleId.value;
            await stopPlayback(true, false);
            if (!isPlaybackRequestActive(lifecycleId)) return;
            playerKey.value++;
            isPlaying.value = true;
            playbackError.value = '';
            isBusy.value = false;
            playerLoading.value = true;
            playbackMode.value = 'loading';
            lastPlaybackUrl.value = url || '';
            repeatInFlight.value = false;
            window.scrollTo({top: 0, behavior: 'smooth'});

            try {
                const response = await fetch(url);
                if (!isPlaybackRequestActive(lifecycleId)) return;
                const channelData = await response.json();
                if (!isPlaybackRequestActive(lifecycleId)) return;
                if (!channelData?.url && currentChannel.value?.cmd) {
                    const fallbackUrl = normalizeWebPlaybackUrl(String(currentChannel.value.cmd).trim().replace(/^ffmpeg\s+/i, ''));
                    await initPlayer({url: fallbackUrl}, lifecycleId);
                } else {
                    await initPlayer({
                        ...(channelData || {}),
                        url: normalizeWebPlaybackUrl(channelData?.url)
                    }, lifecycleId);
                }
                if (currentChannel.value?.mode === 'series') {
                    await refreshSeriesEpisodeWatchState(lifecycleId);
                    await ensureSeriesWatchingNow();
                }
            } catch (e) {
                if (!isPlaybackRequestActive(lifecycleId)) return;
                console.error('Failed to start playback', e);
                playbackError.value = `Playback failed: ${e?.message || 'Unknown error'}`;
                isPlaying.value = false;
            } finally {
                if (!isPlaybackRequestActive(lifecycleId)) return;
                playerLoading.value = false;
            }
        };

        const stopPlayback = async (preserveUi = false, invalidateLifecycle = true) => {
            if (invalidateLifecycle) {
                playbackLifecycleId.value++;
            }
            const lifecycleId = playbackLifecycleId.value;
            repeatInFlight.value = false;
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
            if (!isPlaybackRequestActive(lifecycleId)) return;
            clearVideoElement(videoPlayerModal.value);
            if (!preserveUi) {
                isPlaying.value = false;
                currentChannel.value = null;
                lastPlaybackUrl.value = '';
                playbackError.value = '';
                playbackMode.value = '';
            }
            playerLoading.value = false;
            isYoutube.value = false;
            youtubeSrc.value = '';
            videoTracks.value = [];
            audioTracks.value = [];
            textTracks.value = [];
            selectedTextTrackId.value = 'off';
        };

        const stopPlaybackAndHide = async () => {
            // Ensure topbar controls/title hide immediately.
            isPlaying.value = false;
            playbackMode.value = '';
            currentChannel.value = null;
            await stopPlayback(false, true);
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

        const initPlayer = async (channel, lifecycleId) => {
            if (!isPlaybackRequestActive(lifecycleId)) return;
            const uri = normalizeWebPlaybackUrl(channel.url);
            if (!uri) {
                isPlaying.value = false;
                playbackError.value = 'Playback failed: no stream URL returned.';
                return;
            }

            if (uri.includes('youtube.com') || uri.includes('youtu.be')) {
                isYoutube.value = true;
                const videoId = uri.split('v=')[1] ? uri.split('v=')[1].split('&')[0] : uri.split('/').pop();
                youtubeSrc.value = `https://www.youtube.com/embed/${videoId}?autoplay=1`;
                playbackMode.value = playbackUtils.resolvePlaybackModeLabel(uri, 'youtube');
                return;
            }

            isYoutube.value = false;
            const video = await resolveActiveVideoElement();
            if (!video || !isPlaybackRequestActive(lifecycleId)) return;
            bindPlaybackEvents(video);

            const isApple = /iPhone|iPad|iPod|Macintosh/i.test(navigator.userAgent);
            const canNative = Boolean(video.canPlayType('application/vnd.apple.mpegurl'));
            const hasDRM = channel.drm != null;
            const normalizedUri = String(uri || '').toLowerCase();
            const manifestType = String(channel?.drm?.manifestType || channel?.manifestType || '').toLowerCase();
            const isTs = isTsLikeUrl(normalizedUri, manifestType);

            if (hasDRM) {
                await loadShaka({...channel, url: uri}, lifecycleId);
            } else if (isTs) {
                await loadMpegTs({...channel, url: uri}, lifecycleId);
            } else if (isApple && canNative) {
                await loadNative({...channel, url: uri}, lifecycleId);
            } else if (canNative && uri.endsWith('.m3u8')) {
                await loadNative({...channel, url: uri}, lifecycleId);
            } else {
                await loadShaka({...channel, url: uri}, lifecycleId);
            }
        };

        const loadMpegTs = async (channel, lifecycleId) => {
            if (!isPlaybackRequestActive(lifecycleId)) return;
            const video = await resolveActiveVideoElement();
            if (!video || !isPlaybackRequestActive(lifecycleId)) return;
            bindPlaybackEvents(video);

            const sourceUrl = normalizeWebPlaybackUrl(channel.url);
            const engine = window.mpegts;
            if (!canUseMpegts()) {
                await loadNative({...channel, url: sourceUrl}, lifecycleId);
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
                    if (await tryMpegTsHlsFallback(channel, sourceUrl, lifecycleId)) {
                        return;
                    }
                    playbackError.value = `Playback error: ${message}`;
                });
                player.attachMediaElement(video);
                if (!isPlaybackRequestActive(lifecycleId)) {
                    player.destroy();
                    if (mpegtsPlayer.value === player) mpegtsPlayer.value = null;
                    return;
                }
                player.load();
                if (!isPlaybackRequestActive(lifecycleId)) {
                    player.destroy();
                    if (mpegtsPlayer.value === player) mpegtsPlayer.value = null;
                    return;
                }
                await player.play();
                playbackMode.value = playbackUtils.resolvePlaybackModeLabel(sourceUrl, 'mpegts');
            } catch (e) {
                console.warn('MPEGTS playback failed, trying HLS fallback.', e);
                if (await tryMpegTsHlsFallback(channel, sourceUrl, lifecycleId)) {
                    return;
                }
                if (mpegtsPlayer.value) {
                    try {
                        mpegtsPlayer.value.destroy();
                    } catch (_) {
                    }
                    mpegtsPlayer.value = null;
                }
                await loadNative({...channel, url: sourceUrl}, lifecycleId);
            }
        };

        const tryMpegTsHlsFallback = async (channel, sourceUrl, lifecycleId) => {
            if (!isPlaybackRequestActive(lifecycleId)) {
                return false;
            }
            if (mpegtsPlayer.value) {
                try {
                    mpegtsPlayer.value.destroy();
                } catch (_) {
                }
                mpegtsPlayer.value = null;
            }
            return tryForcedHlsFallback({...channel, url: sourceUrl}, lifecycleId);
        };

        const tryForcedHlsFallback = async (channel, lifecycleId) => {
            const sourceUrl = String(channel?.url || '').trim().toLowerCase();
            const manifestType = String(channel?.drm?.manifestType || channel?.manifestType || '').trim().toLowerCase();
            const isMpegTsLike = isTsLikeUrl(sourceUrl, manifestType);
            const isAdaptive = sourceUrl.includes('.m3u8');
            if (!sourceUrl || sourceUrl.includes('/hls/stream.m3u8') || (!isAdaptive && !isMpegTsLike)) {
                return false;
            }
            if (channel?.drm) {
                return false;
            }
            const fallbackRequestUrl = buildForcedHlsPlaybackRequestUrl(currentChannel.value?.playRequestUrl || '');
            if (!fallbackRequestUrl) {
                return false;
            }
            await startPlayback(fallbackRequestUrl);
            return isPlaybackRequestActive(lifecycleId);
        };

        const loadNative = async (channel, lifecycleId) => {
            if (!isPlaybackRequestActive(lifecycleId)) return;
            const video = await resolveActiveVideoElement();
            if (video && isPlaybackRequestActive(lifecycleId)) {
                let sourceUrl = normalizeWebPlaybackUrl(channel.url);
                video.src = sourceUrl;
                try {
                    if (!isPlaybackRequestActive(lifecycleId)) return;
                    await video.play();
                    playbackMode.value = playbackUtils.resolvePlaybackModeLabel(sourceUrl, 'native');
                } catch (e) {
                    // If backend proxy fails transiently, retry once with cache-busting query.
                    const isProxyUrl = String(sourceUrl || '').includes('/proxy-stream');
                    if (isProxyUrl) {
                        try {
                            const parsed = new URL(sourceUrl, window.location.origin);
                            parsed.searchParams.set('_retry', String(Date.now()));
                            sourceUrl = parsed.toString();
                            video.src = sourceUrl;
                            if (!isPlaybackRequestActive(lifecycleId)) return;
                            await video.play();
                            return;
                        } catch (retryProxyErr) {
                            console.warn('Proxy retry failed.', retryProxyErr);
                        }
                    }

                    // Retry with HTTP downgrade for known Stalker playback paths.
                    const downgraded = downgradeHttpsToHttpForKnownPaths(sourceUrl);
                    if (downgraded !== sourceUrl) {
                        try {
                            sourceUrl = downgraded;
                            video.src = sourceUrl;
                            if (!isPlaybackRequestActive(lifecycleId)) return;
                            await video.play();
                            return;
                        } catch (retryErr) {
                            console.warn('Native playback retry failed.', retryErr);
                        }
                    }

                    if (await tryForcedHlsFallback(channel, lifecycleId)) {
                        return;
                    }

                    playbackError.value = `Playback failed: ${e?.message || 'No supported source found'}`;
                    console.warn('Autoplay/source failed.', e);
                }
            }
        };

        const loadShaka = async (channel, lifecycleId) => {
            if (!isPlaybackRequestActive(lifecycleId)) return;
            const video = await resolveActiveVideoElement();
            if (!video || !isPlaybackRequestActive(lifecycleId)) return;
            bindPlaybackEvents(video);

            shaka.polyfill.installAll();
            if (!shaka.Player.isBrowserSupported()) return;

            const player = new shaka.Player();
            const destroyTransientPlayer = async () => {
                if (playerInstance.value === player) {
                    playerInstance.value = null;
                }
                try {
                    await player.destroy();
                } catch (_) {
                }
            };
            playerInstance.value = player;
            if (!isPlaybackRequestActive(lifecycleId)) {
                await destroyTransientPlayer();
                return;
            }

            player.addEventListener('error', (event) => {
                console.error('Shaka Player Error:', event.detail);
                playbackError.value = `Playback error: ${event?.detail?.message || 'Shaka error'}`;
            });

            if (channel.drm) {
                const drmConfig = {};
                if (channel.drm.licenseUrl) drmConfig.servers = {[channel.drm.type]: channel.drm.licenseUrl};
                if (channel.drm.clearKeys) drmConfig.clearKeys = channel.drm.clearKeys;
                player.configure({drm: drmConfig});
            }

            try {
                if (!isPlaybackRequestActive(lifecycleId)) {
                    await destroyTransientPlayer();
                    return;
                }
                await player.attach(video);
                if (!isPlaybackRequestActive(lifecycleId)) {
                    await destroyTransientPlayer();
                    return;
                }
                await player.load(channel.url);
                if (!isPlaybackRequestActive(lifecycleId)) {
                    await destroyTransientPlayer();
                    return;
                }
                playbackMode.value = playbackUtils.resolvePlaybackModeLabel(channel.url, 'shaka');
                videoTracks.value = player.getVariantTracks();
                audioTracks.value = player.getVariantTracks()
                    .filter(track => !track.audioOnly)
                    .filter((track, idx, arr) => arr.findIndex(other =>
                        String(other.language || '') === String(track.language || '')
                        && String(other.label || '') === String(track.label || '')
                        && String(other.audioCodec || '') === String(track.audioCodec || '')
                    ) === idx);
                textTracks.value = player.getTextTracks ? (player.getTextTracks() || []) : [];
                selectedTextTrackId.value = 'off';
                autoSelectBestTrack(player);
            } catch (e) {
                console.error('Shaka: Error loading video:', e);
                playbackError.value = `Playback failed: ${e?.message || 'Unable to load stream'}`;
            }
        };

        const switchVideoTrack = (trackId) => {
            if (!playerInstance.value) return;
            const track = playerInstance.value.getVariantTracks().find(t => t.id === trackId);
            if (track) playerInstance.value.selectVariantTrack(track, true);
        };

        const switchAudioTrack = (trackId) => {
            if (!playerInstance.value) return;
            const track = playerInstance.value.getVariantTracks().find(t => t.id === trackId);
            if (track) playerInstance.value.selectVariantTrack(track, true);
        };

        const switchTextTrack = async (trackId) => {
            if (!playerInstance.value) return;
            if (trackId === 'off') {
                selectedTextTrackId.value = 'off';
                await playerInstance.value.setTextTrackVisibility(false);
                return;
            }
            const track = (playerInstance.value.getTextTracks ? playerInstance.value.getTextTracks() : [])
                .find(t => String(t.id) === String(trackId));
            if (!track) return;
            playerInstance.value.selectTextTrack(track);
            await playerInstance.value.setTextTrackVisibility(true);
            selectedTextTrackId.value = String(track.id);
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
                categoryId: String(current.categoryId || ''),
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
                await loadWatchingNow();
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
                categoryId: String(current.categoryId || ''),
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
                await loadWatchingNow();
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
                categoryId: String(current.categoryId || ''),
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
                await loadWatchingNowVod();
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
            const mode = normalizePlaybackMode(currentChannel.value?.mode || '');
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

        const mediaImageError = (e) => {
            e.target.onerror = null;
            e.target.removeAttribute('src');
            e.target.style.display = 'none';
            const fallback =
                e.target.parentElement?.querySelector('.nf-media-fallback') ||
                e.target.parentElement?.querySelector('.nf-episode-thumb-fallback');
            if (fallback) fallback.classList.add('show');
        };

        const mediaImageLoad = (e) => {
            e.target.style.display = '';
            const fallback =
                e.target.parentElement?.querySelector('.nf-media-fallback') ||
                e.target.parentElement?.querySelector('.nf-episode-thumb-fallback');
            if (fallback) fallback.classList.remove('show');
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
            await window.UIPTVControls.onControlClick(event, action, ensurePlaybackNotPaused, ...args);
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
            } catch (_) {
            }
            try {
                sessionStorage.clear();
            } catch (_) {
            }

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

        const resetApp = async () => {
            stopPlayback();
            selectedAccountId.value = '';
            resetModeState('itv');
            resetModeState('vod');
            resetModeState('series');
            selectedSeriesSeason.value = '';
            searchQuery.value = '';
            selectedBookmarkCategoryId.value = '';
            watchingNowRows.value = [];
            selectedWatchingNowKey.value = '';
            watchingNowVodRows.value = [];
            selectedWatchingNowVodKey.value = '';
            await loadWatchingNow();
            await loadWatchingNowVod();
        };

        onMounted(async () => {
            await loadConfig();
            loadAccounts();
            loadBookmarkCategories();
            loadBookmarks();
            loadWatchingNow();
            loadWatchingNowVod();

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
            isPinnedAccount,
            resolvePinSvg,
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
            filteredWatchingNowRows,
            filteredWatchingNowVodRows,
            selectedWatchingNowRow,
            selectedWatchingNowKey,
            selectedWatchingNowVodKey,
            bookmarkCategoryTabs,
            selectedBookmarkCategoryId,
            getEpisodeDisplayTitle,
            currentChannel,
            currentChannelName,
            currentChannelDebugTitle,
            playbackMode,
            isCurrentFavorite,
            isPlaying,
            playbackError,
            playerLoading,
            showOverlay,
            showBookmarkModal,
            playerKey,
            isYoutube,
            youtubeSrc,
            videoPlayerModal,
            playerInlineRef,
            playerModalFrame,
            videoTracks,
            audioTracks,
            textTracks,
            selectedTextTrackId,
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
            openWatchingNowSeriesDetail,
            playWatchingNowVodRow,
            selectBookmarkCategory,
            stopPlayback,
            stopPlaybackAndHide,
            closePlayerPopup,
            reloadPlayback,
            toggleRepeat,
            togglePictureInPicture,
            requestFullscreenPlayer,
            onPlayerControlClick,
            toggleFavorite,
            mediaImageError,
            mediaImageLoad,
            selectSeriesSeason,
            loadMoreSeriesDetailEpisodes,
            getSeriesDetailEpisodeAnchorId,
            getImdbUrl,
            toggleTheme,
            clearWebCacheAndReload,
            resetApp,
            switchVideoTrack,
            switchAudioTrack,
            switchTextTrack
        };
    }
}).mount('#app');
