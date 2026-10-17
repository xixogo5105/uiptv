const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
const functionSource = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
function harness() {
  const calls = [], tasks = [];
  const ref = value => ({value});
  const context = {
    window: {location: {origin: 'http://fixture'}}, navigator: {language: 'en'},
    URLSearchParams, AbortController, Date, WeakMap, Map, clearTimeout(){}, setTimeout(){return 1;},
    nextTick(fn){if(fn) tasks.push(Promise.resolve().then(fn));},
    IntersectionObserver: class {constructor(callback){this.callback=callback;} observe(element){this.callback([{target:element,isIntersecting:true}]);} disconnect(){} unobserve(){}},
    epgEnabled: ref(false), epgPlaybackOnly: ref(false), thumbnailsEnabled: ref(false), webPlaybackProxyEnabled: ref(true), epgLabels: ref({}), playerEpg: ref({old: true}),
    currentChannel: ref({accountId: 'account', dbId: 'local-row', categoryId: 'category', mode: 'itv'}),
    liveGuideLoading: ref(false), liveGuideError: ref(''), liveGuideAccountId: ref('account'), selectedGuideProgramme:ref(null),
    liveGuideStart: ref(0), liveGuideRows: ref([]), epgGuideVisible: ref(false),
    activeTab:ref('accounts'),viewState:ref('channels'),contentMode:ref('itv'),selectedAccountId:ref('account'),channels:ref([]),bookmarks:ref([]),
    syncEpgEvents(){},
    closeGuide(){context.epgGuideVisible.value=false;},
    fetch: async (url, options={}) => {
      calls.push({url:String(url),options});
      if (String(url).endsWith('/config')) return {ok:true,json: async () => ({epgEnabled: context.nextEnabled, enableThumbnails: true})};
      if (String(url).endsWith('/epg-labels.json')) return {ok:true,json: async () => ({en: {guide: 'View Guide'}})};
      return {ok: true, json: async () => String(url).includes('/playerEpg') ? {now: {title: 'Current'}, next:{title:'Next'}} : []};
    }, nextEnabled: false
  };
  vm.createContext(context);
  vm.runInContext(`let playerEpgRequest = 0, playerEpgController = null, liveGuideRequest = 0, liveGuideController = null, nativeGuideController = null;
    ${functionSource('const refreshPlayerEpg =', 'watch(currentChannel,')}
    ${functionSource('let cardEpgObserver =', 'watch([epgEnabled, epgPlaybackOnly, activeTab, selectedAccountId, viewState, contentMode]')}
    ${functionSource('const applyDesktopEpgSetting =', 'const findBookmarkForChannel =')}
    Object.assign(this,{loadConfig,refreshPlayerEpg,applyDesktopEpgSetting,observeChannelEpg,observeBookmarkEpg,refreshVisibleCardEpg});`, context);
  return {context, calls, async flush(){await Promise.all(tasks.splice(0));}};
}
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

test('card summaries show programme times, bounded progress and display-only placeholders',()=>{
 const context={Vue:{}, createApp(){}, ref:value=>({value}), epgTime:{value:new Date('2026-10-16T12:30:00Z').getTime()}, epgLabels:{value:{epgNoProgrammeDetails:'No details'}}};
 vm.createContext(context);
 vm.runInContext(functionSource('const cardProgramme =','const isLiveBookmark =')+'this.cardProgramme=cardProgramme;this.cardProgrammeProgress=cardProgrammeProgress;this.cardProgrammeTime=cardProgrammeTime;',context);
 const item={epgNow:{title:'Current',start:context.epgTime.value-1800000,stop:context.epgTime.value+1800000}};
 assert.equal(context.cardProgrammeProgress(item),0.5);
 assert.equal(context.cardProgramme({},'epgNow').title,'No details');assert.equal(Object.keys(item).length,1);
 assert.ok(context.cardProgrammeTime(item.epgNow).includes(' – '));
 assert.equal(context.cardProgramme(item,'epgNext').start,item.epgNow.stop);
});
