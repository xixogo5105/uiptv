const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');

test('NATIVE_PROXY strategy uses native playback before adaptive-engine fallbacks', () => {
    const hint = source.indexOf("strategyHint === 'NATIVE_PROXY'");
    const native = source.indexOf("await loadNative({...channel, url: proxyUrl}, requestId);", hint);
    const override = source.indexOf("if (override === 'proxy')", hint);

    assert.ok(hint >= 0, 'SPA must recognize the server proxy strategy hint');
    assert.ok(native > hint && native < override, 'proxy strategy must select native playback first');
});

