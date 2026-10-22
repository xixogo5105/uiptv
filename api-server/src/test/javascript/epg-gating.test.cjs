const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
const functionSource = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
function harness() {
  const calls = [], tasks = [];
  const ref = value => ({value});
  const englishEpgLabels = JSON.parse(fs.readFileSync('api-server/src/main/resources/web/epg-labels.json', 'utf8')).en;
  const context = {
    ref,
    window: {location: {origin: 'http://fixture'}}, navigator: {language: 'en'},
    URLSearchParams, AbortController, Date, WeakMap, Map, clearTimeout(){}, setTimeout(){return 1;},
    nextTick(fn){if(fn) tasks.push(Promise.resolve().then(fn));},
    IntersectionObserver: class {constructor(callback){this.callback=callback;} observe(element){this.callback([{target:element,isIntersecting:true}]);} disconnect(){} unobserve(){}},
    epgEnabled: ref(false), epgPlaybackOnly: ref(false), thumbnailsEnabled: ref(false), webPlaybackProxyEnabled: ref(true), epgLabels: ref(englishEpgLabels), playerEpg: ref({old: true}),
    currentChannel: ref({accountId: 'account', dbId: 'local-row', categoryId: 'category', mode: 'itv'}),
    liveGuideLoading: ref(false), liveGuideError: ref(''), liveGuideAccountId: ref('account'), selectedGuideProgramme:ref(null),
    liveGuideStart: ref(0), liveGuideRows: ref([]), epgGuideVisible: ref(false),
    activeTab:ref('accounts'),viewState:ref('channels'),contentMode:ref('itv'),selectedAccountId:ref('account'),channels:ref([]),bookmarks:ref([]),
    syncEpgEvents(){},
    closeGuide(){context.epgGuideVisible.value=false;},
    fetch: async (url, options={}) => {
      calls.push({url:String(url),options});
      if (String(url).endsWith('/config')) return {ok:true,json: async () => ({epgEnabled: context.nextEnabled, enableThumbnails: true})};
      if (String(url).endsWith('/epg-labels.json')) return {ok:true,json: async () => ({en: englishEpgLabels})};
      return {ok: true, json: async () => String(url).includes('/playerEpg') ? {now: {title: 'Current'}, next:{title:'Next'}} : []};
    }, nextEnabled: false
  };
  vm.createContext(context);
  vm.runInContext(`let playerEpgRequest = 0, playerEpgController = null, liveGuideRequest = 0, liveGuideController = null, nativeGuideController = null;
    const epgLabels = this.epgLabels, epgLabel = key => epgLabels.value?.[key] || '';
    ${functionSource('const cardProgramme =', 'const isLiveBookmark =')}
    ${functionSource('const refreshPlayerEpg =', 'watch(currentChannel,')}
    ${functionSource('let cardEpgObserver =', 'watch([epgEnabled, epgPlaybackOnly, activeTab, selectedAccountId, viewState, contentMode]')}
    ${functionSource('const applyDesktopEpgSetting =', 'const findBookmarkForChannel =')}
    Object.assign(this,{cardEpgDetails,isCardEpgExpanded,toggleCardEpgDetails,loadConfig,refreshPlayerEpg,applyDesktopEpgSetting,observeChannelEpg,observeBookmarkEpg,refreshVisibleCardEpg,cardEpgStatus,cardEpgIsStale,cardNowStatus});`, context);
  return {context, calls, async flush(){await Promise.all(tasks.splice(0));}};
}
test('web EPG labels are translated for every shipped non-English locale', () => {
  const labels = JSON.parse(fs.readFileSync('api-server/src/main/resources/web/epg-labels.json', 'utf8'));
  const english = labels.en;
  const keys = [
    'epgRefreshGuide', 'epgSourceTooLarge', 'epgSourceProgrammeLimit', 'epgSourceInvalid',
    'epgSourceUnavailable', 'epgSourceXmlLimit', 'epgNow', 'epgNext', 'epgNoProgrammeDetails',
    'epgWebChannel', 'epgWebRowLoading', 'epgWebShowDetails', 'epgWebHideDetails',
    'epgWebNoUpcoming', 'epgWebScheduleFailed', 'epgWebPlayerNowNext'
  ];
  for (const [locale, bundle] of Object.entries(labels)) {
    if (locale.startsWith('en')) continue;
    for (const key of keys) {
      assert.equal(typeof bundle[key], 'string', `${locale} is missing ${key}`);
      assert.notEqual(bundle[key], english[key], `${locale} contains an English placeholder for ${key}`);
    }
  }
});
test('disabled desktop setting suppresses guide requests and clears stale EPG everywhere', async () => {
  const {context, calls} = harness();
  context.channels.value=[{epgNow:{title:'old'}}];context.bookmarks.value=[{epgNext:{title:'old'}}];
  await context.loadConfig(); await context.refreshPlayerEpg();
  assert.equal(context.epgEnabled.value, false); assert.equal(context.playerEpg.value, null);
  assert.equal(context.channels.value[0].epgNow,null);assert.equal(context.bookmarks.value[0].epgNext,null);
  assert.equal(calls.filter(call => /\/(liveGuide|playerEpg)/.test(call.url)).length, 0);
});
test('enabled API setting permits guide and player requests with local lookup identities', async () => {
  const {context, calls,flush} = harness(); context.nextEnabled = true;
  await context.loadConfig();await flush(); context.epgGuideVisible.value = true;
  await context.refreshPlayerEpg();
  assert.equal(context.playerEpg.value.now.title, 'Current');
  assert.ok(calls.some(call => call.url.includes('/playerEpg?') && call.url.includes('channelId=local-row')));
  assert.ok(!calls.some(call => call.url.includes('/liveGuide?')));
  assert.equal(calls.find(call=>call.url.endsWith('/config')).options.cache,'no-store');
});
test('web EPG is read-only from desktop settings with no header status or toggle controls', () => {
  const html = fs.readFileSync('api-server/src/main/resources/web/index.html', 'utf8');
  for(const removed of ['toggleEpgMode','epgModeEnabled','epgSettingsOpen','epg-settings-menu','epgGuideVisible','live-guide-panel','openGuide','/liveGuide']) assert.ok(!source.includes(removed)&&!html.includes(removed));
  for(const removed of ['saveEpgSettings','toggleEpgPlaybackOnly','EPG enabled','EPG disabled','Playback-only EPG','Use full EPG']) {
    assert.ok(!source.includes(removed), `SPA must not expose ${removed}`);
    assert.ok(!html.includes(removed), `header must not expose ${removed}`);
  }
  assert.match(source, /fetch\(window\.location\.origin \+ '\/config', \{cache: 'no-store'\}\)/);
  assert.ok(html.includes("cardProgramme(bookmark, 'epgNow')"));assert.ok(html.includes("cardProgramme(channel, 'epgNext')"));assert.ok(html.includes('cardProgrammeProgress'));
});
test('full-EPG web headers and rows use matching fractional tracks that leave room for gaps', () => {
  const css = fs.readFileSync('api-server/src/main/resources/web/css/spa.css', 'utf8');
  const header = css.match(/\.epg-column-header\s*\{[^}]*grid-template-columns:\s*([^;]+);/s)?.[1];
  const row = css.match(/\.epg-row-card\s*\{[^}]*grid-template-columns:\s*([^;]+);/s)?.[1];
  assert.ok(header); assert.ok(row);
  assert.equal(header, row, 'the header labels must align with every EPG row');
  assert.match(header, /minmax\(0,\s*1\.1fr\)\s+minmax\(0,\s*1fr\)\s+minmax\(0,\s*1fr\)/);
  assert.doesNotMatch(header, /%/, 'percentage tracks plus grid gaps can exceed the content width');
  const narrow = css.match(/@media\s*\(max-width:\s*760px\)\s*\{[^}]*\.epg-column-header,[^}]*\.epg-row-card\s*\{[^}]*grid-template-columns:\s*([^;]+);/s)?.[1];
  assert.ok(narrow);
  assert.match(narrow, /minmax\(0,\s*\.8fr\)\s+minmax\(0,\s*1fr\)/);
  assert.doesNotMatch(narrow, /%/);
  assert.match(css, /\.epg-column-header,\s*\.epg-row-card\s*\{\s*gap:\s*8px;/,
    'the narrowest breakpoint must keep header and row gaps aligned');
  const html = fs.readFileSync('api-server/src/main/resources/web/index.html', 'utf8');
  assert.match(html, /class="epg-column-header"/);
  assert.match(html, /class="epg-now-column"/);
  assert.match(html, /class="epg-next-column"/);
  assert.match(html, /class="epg-info-button"/);
});
test('switching EPG off invalidates an already completed but unread player response',async()=>{
  const {context}=harness();context.applyDesktopEpgSetting(true);
  let finish;context.fetch=()=>new Promise(resolve=>{finish=resolve;});
  const loading=context.refreshPlayerEpg(); context.applyDesktopEpgSetting(false);
  finish({ok:true,json:async()=>({now:{title:'Stale'}})});await loading;
  assert.equal(context.playerEpg.value,null);
});
test('all visible channel cards including M3U hydrate through JSON without a 32-card cutoff',async()=>{
  const {context,calls}=harness();context.epgEnabled.value=true;
  const channels=Array.from({length:33},(_,i)=>({dbId:`local-${i}`,channelId:`provider-${i}`,categoryId:'local-category'}));
  channels.forEach(channel=>context.observeChannelEpg({},channel)); await context.refreshVisibleCardEpg();
  assert.ok(channels.every(channel=>channel.epgNow?.title==='Current'));
  assert.equal(calls.length,33);assert.ok(calls.every(call=>call.url.includes('categoryId=local-category')&&!call.url.includes('provider-')));
});
test('bookmark cards use bookmark database IDs and skip movies and series',async()=>{
  const {context,calls}=harness();context.epgEnabled.value=true;context.activeTab.value='bookmarks';
  const live={dbId:'bookmark-row',channelId:'provider-channel',accountAction:'itv'};
  context.observeBookmarkEpg({},live);context.observeBookmarkEpg({},{dbId:'movie',accountAction:'vod'});context.observeBookmarkEpg({},{dbId:'series',accountAction:'series'});
  await context.refreshVisibleCardEpg();assert.equal(calls.length,1);assert.ok(calls[0].url.endsWith('bookmarkId=bookmark-row'));assert.equal(live.epgNext.title,'Next');
});
test('web schedule detail is single-open, cancels prior lookups and requests a bounded result',async()=>{
  const {context,calls}=harness();
  const pending=[];
  context.fetch=(url,options={})=>{
    calls.push({url:String(url),options});
    return new Promise(resolve=>pending.push({resolve,options}));
  };
  const first={dbId:'first-row',categoryId:'cat'};
  const second={dbId:'second-row',categoryId:'cat'};
  const firstLookup=context.toggleCardEpgDetails(first);
  assert.equal(context.isCardEpgExpanded(first),true);
  const secondLookup=context.toggleCardEpgDetails(second);
  assert.equal(pending[0].options.signal.aborted,true);
  assert.equal(context.isCardEpgExpanded(first),false);
  assert.equal(context.isCardEpgExpanded(second),true);
  assert.ok(calls[0].url.includes('limit=24'));
  pending[0].resolve({ok:true,json:async()=>({programmes:[{title:'Stale'}]})});
  pending[1].resolve({ok:true,json:async()=>({programmes:[{title:'Selected'}]})});
  await Promise.all([firstLookup,secondLookup]);
  assert.deepEqual(Array.from(context.cardEpgDetails.value.programmes,programme=>programme.title),['Selected']);
  await context.toggleCardEpgDetails(second);
  assert.equal(context.isCardEpgExpanded(second),false);
  assert.deepEqual(Array.from(context.cardEpgDetails.value.programmes),[]);
});
test('visible EPG failures back off instead of retrying on every settings poll',async()=>{
  const {context,calls}=harness();context.epgEnabled.value=true;
  context.fetch=async(url,options={})=>{calls.push({url:String(url),options});return {ok:false};};
  const channel={dbId:'unavailable',categoryId:'cat'};
  context.observeChannelEpg({},channel);
  await context.refreshVisibleCardEpg();
  await context.refreshVisibleCardEpg();
  assert.equal(calls.length,1);
  assert.equal(channel.epgSourceStatus,'UNAVAILABLE');
});
test('turning EPG off discards in-flight card JSON',async()=>{
  const {context}=harness();context.epgEnabled.value=true;
  const channel={dbId:'row',categoryId:'cat'};context.channels.value=[channel];context.observeChannelEpg({},channel);
  let finishCard;context.fetch=()=>new Promise(resolve=>{finishCard=resolve;});
  const card=context.refreshVisibleCardEpg();context.applyDesktopEpgSetting(false);
  finishCard({ok:true,json:async()=>({now:{title:'Stale'}})});await card;assert.equal(channel.epgNow,null);

});
test('HTTP configuration failures keep the last desktop setting',async()=>{
  const {context}=harness();context.epgEnabled.value=true;context.fetch=async()=>({ok:false,json:async()=>({})});
  await context.loadConfig();assert.equal(context.epgEnabled.value,true);
});

test('card summaries use only real programme data and never synthesize guide text or time',()=>{
 const context={Vue:{}, createApp(){}, ref:value=>({value}), epgTime:{value:new Date('2026-10-16T12:30:00Z').getTime()}};
 vm.createContext(context);
 vm.runInContext(functionSource('const cardProgramme =','const isLiveBookmark =')+'this.cardProgramme=cardProgramme;this.cardProgrammeProgress=cardProgrammeProgress;this.cardProgrammeTime=cardProgrammeTime;',context);
 const item={epgNow:{title:'Current',start:context.epgTime.value-1800000,stop:context.epgTime.value+1800000}};
 assert.equal(context.cardProgrammeProgress(item),0.5);
 assert.equal(context.cardProgramme({},'epgNow'),null);assert.equal(context.cardProgrammeTime(null),'');
 assert.ok(context.cardProgrammeTime(item.epgNow).includes(' – '));
 assert.equal(context.cardProgramme(item,'epgNext'),null);assert.equal(context.cardProgrammeProgress({}),0);
});

test('EPG row cells distinguish missing current data from a missing guide and keep Next aligned',()=>{
 const {context}=harness();
 assert.equal(context.cardNowStatus({epgLoaded:true,epgNext:{title:'Upcoming'}}),'—');
 assert.equal(context.cardNowStatus({epgLoaded:true}),context.epgLabels.value.epgNoGuide);
 assert.equal(context.cardNowStatus({epgLoaded:true,epgUnavailable:true}),context.epgLabels.value.epgSourceUnavailable);
 assert.equal(context.cardNowStatus({epgLoaded:true,epgSourceStatus:'STALE',epgNext:{title:'Upcoming'}}),context.epgLabels.value.epgSourceStale);
 const html=fs.readFileSync('api-server/src/main/resources/web/index.html','utf8');
 const css=fs.readFileSync('api-server/src/main/resources/web/css/spa.css','utf8');
 assert.equal((html.match(/epg-next-column epg-next-empty/g)||[]).length,2);
 assert.ok(css.includes('.epg-column-header'));
 assert.ok(css.includes('@media (max-width: 760px)'));
 assert.ok(css.includes('.epg-row-card .epg-next-column { display: none; }'));
});
