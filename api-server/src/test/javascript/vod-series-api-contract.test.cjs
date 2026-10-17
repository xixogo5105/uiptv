const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');

test('series episode loading uses the provider-aware endpoint and validates its response', () => {
    assert.match(source, /const query = new URLSearchParams\(\{seriesId: requestedSeriesId, accountId, categoryId\}\)/);
    assert.match(source, /fetch\(`\$\{window\.location\.origin\}\/seriesEpisodes\?\$\{query\}`\)/);
    assert.match(source, /if \(!response\.ok\) throw new Error\(`Series episodes unavailable/);
    assert.match(source, /if \(!Array\.isArray\(payload\)\) throw new Error\('Invalid series episodes response'\)/);
    assert.doesNotMatch(source, /\/channels\?categoryId=.*mode=series&movieId=/);
});

test('series details send account, provider ID, selected category and name with response validation', () => {
    assert.match(source, /const query = new URLSearchParams\(\{\s*seriesId: String\(seriesId \|\| ''\),\s*accountId: String\(currentContext\.value\.accountId \|\| ''\),\s*categoryId: String\(modeState\.selectedSeriesCategoryId/);
    assert.match(source, /if \(!response\.ok\) throw new Error\(`Series details unavailable/);
});

test('VOD details send selected category and encoded provider ID/name with response validation', () => {
    assert.match(source, /categoryId: String\(vodItem\.categoryId \|\| currentContext\.value\.categoryId \|\| ''\), channelId: String\(channelIdentifier \|\| ''\), vodName: String\(vodItem\.name \|\| ''\)/);
    assert.match(source, /if \(!response\.ok\) throw new Error\(`VOD details unavailable/);
});
