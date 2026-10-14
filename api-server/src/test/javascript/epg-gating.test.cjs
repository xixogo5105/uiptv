const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
const functionSource = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
function harness() {
  const calls = [];
  const ref = value => ({value});
  const context = {
    window: {location: {origin: 'http://fixture'}}, navigator: {language: 'en'},
    URLSearchParams, AbortController, Date,
    epgEnabled: ref(false), thumbnailsEnabled: ref(false), epgLabels: ref({}), playerEpg: ref({old: true}),
    currentChannel: ref({accountId: 'account', dbId: 'local-row', categoryId: 'category', mode: 'itv'}),
    liveGuideLoading: ref(false), liveGuideError: ref(''), liveGuideAccountId: ref('account'),
    liveGuideStart: ref(0), liveGuideRows: ref([]), epgGuideVisible: ref(false),
    fetch: async url => {
      calls.push(String(url));
      if (String(url).endsWith('/config')) return {json: async () => ({epgEnabled: context.nextEnabled, enableThumbnails: true})};
      if (String(url).endsWith('/epg-labels.json')) return {json: async () => ({en: {guide: 'View Guide'}})};
      return {ok: true, json: async () => String(url).includes('/playerEpg') ? {now: {title: 'Current'}} : []};
    }, nextEnabled: false
  };
  vm.createContext(context);
  vm.runInContext(`let playerEpgRequest = 0, playerEpgController = null, liveGuideRequest = 0, liveGuideController = null;
    ${functionSource('const refreshPlayerEpg =', 'watch(currentChannel,')}
    ${functionSource('const loadLiveGuide =', 'const playGuideRow =')}
    ${functionSource('const loadConfig =', 'const findBookmarkForChannel =')}
    this.loadConfig = loadConfig; this.refreshPlayerEpg = refreshPlayerEpg; this.loadLiveGuide = loadLiveGuide;`, context);
  return {context, calls};
}
test('disabled API setting suppresses both guide requests and clears player data', async () => {
  const {context, calls} = harness();
  await context.loadConfig(); await context.refreshPlayerEpg(); await context.loadLiveGuide();
  assert.equal(context.epgEnabled.value, false); assert.equal(context.playerEpg.value, null);
  assert.equal(calls.filter(url => /\/(liveGuide|playerEpg)/.test(url)).length, 0);
});
test('enabled API setting permits guide and player requests with local lookup identities', async () => {
  const {context, calls} = harness(); context.nextEnabled = true;
  await context.loadConfig(); context.epgGuideVisible.value = true;
  await context.refreshPlayerEpg(); await context.loadLiveGuide();
  assert.equal(context.playerEpg.value.now.title, 'Current');
  assert.ok(calls.some(url => url.includes('/playerEpg?') && url.includes('channelId=local-row')));
  assert.ok(calls.some(url => url.includes('/liveGuide?')));
});
test('web provides navigation and no independent enable switch', () => {
  const html = fs.readFileSync('api-server/src/main/resources/web/index.html', 'utf8');
  assert.ok(!source.includes('toggleEpgMode'));
  assert.ok(!html.includes('epgModeEnabled'));
  assert.ok(html.includes('@click="openGuide"')); assert.ok(html.includes('@click="closeGuide"'));
});
