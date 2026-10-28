const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const html = fs.readFileSync('api-server/src/main/resources/web/index.html', 'utf8');
const spa = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
const css = fs.readFileSync('api-server/src/main/resources/web/css/spa.css', 'utf8');

test('account browsing keeps each back action beside the section it returns from', () => {
    const accountHeadingStart = html.indexOf('<div class="drilldown-actions" v-else>');
    const accountHeadingEnd = html.indexOf('</div>', accountHeadingStart);
    const categoryHeaderStart = html.indexOf('<header class="panel-heading">', html.indexOf('aria-label="Categories"'));
    const categoryHeaderEnd = html.indexOf('</header>', categoryHeaderStart);
    const contentHeaderStart = html.indexOf('<header class="panel-heading">', html.indexOf('aria-label="Content"'));
    const contentHeaderEnd = html.indexOf('</header>', contentHeaderStart);
    const episodesHeaderStart = html.indexOf('<header class="panel-heading episodes-panel-heading">');
    const episodesHeaderEnd = html.indexOf('</header>', episodesHeaderStart);
    const accountHeading = html.slice(accountHeadingStart, accountHeadingEnd);
    const categoryHeader = html.slice(categoryHeaderStart, categoryHeaderEnd);
    const contentHeader = html.slice(contentHeaderStart, contentHeaderEnd);
    const episodesHeader = html.slice(episodesHeaderStart, episodesHeaderEnd);

    assert.doesNotMatch(accountHeading, /goBackToAccounts|goBackToCategories|goBackToChannels/,
        'back controls should not be detached from their destination panels');
    assert.match(categoryHeader, /@click="goBackToAccounts"/,
        'the category panel should return to the account list');
    assert.match(categoryHeader, /aria-label="Back to accounts"/);
    assert.match(categoryHeader, /title="Back to accounts"/,
        'the account arrow should explain its destination on hover');
    assert.match(contentHeader, /viewState === 'channels' \? goBackToCategories\(\) : goBackToChannels\(\)/,
        'the content panel should return to categories or the parent media list');
    assert.match(contentHeader, /:title="viewState === 'channels' \? 'Back to categories' : 'Back to ' \+ contentModeLabels\[contentMode\]\.toLowerCase\(\)"/);
    assert.match(contentHeader, /viewState === 'channels' && isCompactDrilldown/,
        'hide the categories back arrow when the category panel is already visible');
    assert.match(contentHeader, /viewState === 'vodDetail'/,
        'movie detail should expose a return to its media list');
    assert.match(episodesHeader, /@click="goBackToChannels"/,
        'the dedicated episodes panel should return to the series list');
    assert.match(episodesHeader, /aria-label="Back to series"/);
    assert.match(episodesHeader, /title="Back to series"/);
});

test('section back arrows have a visible, accessible icon-button target', () => {
    assert.match(html, /class="panel-back-button"[\s\S]*?bi bi-arrow-left/);
    assert.match(css, /\.panel-back-button\s*\{[\s\S]*?width:\s*34px[\s\S]*?height:\s*34px[\s\S]*?border-radius:\s*50%/);
    assert.match(css, /\.panel-back-button:hover\s*\{/);
});

test('pinned web accounts use the desktop two-tone pin artwork', () => {
    assert.match(html, /<svg aria-hidden="true" class="account-pin-icon" :viewBox="resolvePinSvg\(account\)\.viewBox" v-if="isPinnedAccount\(account\)">/);
    assert.match(html, /:d="resolvePinSvg\(account\)\.stemPath" :fill="resolvePinSvg\(account\)\.stemFill"/);
    assert.match(html, /:d="resolvePinSvg\(account\)\.headPath" :fill="resolvePinSvg\(account\)\.headFill"/);
    assert.match(spa, /const DEFAULT_PIN_SVG = \{[\s\S]*?stemFill: '#cad2d2',[\s\S]*?headFill: '#e30000'/);
    assert.match(css, /\.account-pin-icon\s*\{[\s\S]*?width:\s*18px[\s\S]*?height:\s*18px/);
    assert.doesNotMatch(html, /bi-pin-angle-fill/);
});
