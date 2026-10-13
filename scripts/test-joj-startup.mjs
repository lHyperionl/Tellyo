import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const adapter = readFileSync(new URL('../app/src/main/assets/players/joj.js', import.meta.url), 'utf8');

// Run the production adapter against a deterministic page and clock. The fake
// player ignores early clicks, as Tivio does during asynchronous source setup.
function page({ readyAt = Infinity, embedded = false } = {}) {
    let now = 0;
    let mutation;
    const clicks = [];
    const intervals = new Map();
    const listeners = new Map();
    const state = { consent: false, account: false, mounted: true };
    const window = { scrollTo() {} };
    const video = { paused: true, readyState: 0 };
    function element(tagName, extra = {}) {
        return {
            tagName, disabled: false, hidden: false,
            getBoundingClientRect() { return { width: this.hidden ? 0 : 100, height: 50 }; },
            focus() { document.activeElement = this; },
            matches() { return false; },
            hasAttribute() { return false; },
            closest() { return null; },
            ...extra,
        };
    }
    const icon = element('svg', {
        dispatchEvent() {
            clicks.push(now);
            if (now >= readyAt) video.paused = false;
        },
    });
    const play = element('DIV', {
        matches(selector) { return selector === '[title]'; },
        querySelector() { return icon; },
    });
    const account = element('A', { innerText: 'Prihlásiť sa' });
    const consent = element('BUTTON', {
        id: 'didomi-notice-disagree-button',
        closest() { return {}; },
        dispatchEvent() {},
    });
    const root = {
        querySelector(selector) { return selector.includes('[title=') && video.paused ? play : null; },
        querySelectorAll(selector) {
            if (selector === 'video') return [video];
            if (selector === 'a') return state.account ? [account] : [];
            return [play];
        },
        contains(target) { return [play, icon, video, account].includes(target); },
    };
    const document = {
        activeElement: null, body: {}, head: { appendChild() {} },
        createElement() { return {}; },
        getElementById(id) { return state.consent && id === consent.id ? consent : null; },
        querySelector(selector) {
            return ['.joj-sticky-player-wrapper', '#player-1'].includes(selector) && state.mounted ? root : null;
        },
        querySelectorAll(selector) { return selector === 'video' ? [video] : []; },
        addEventListener(name, callback) { listeners.set(name, callback); },
        removeEventListener(name) { listeners.delete(name); },
    };
    runInNewContext(adapter, {
        window, document, location: { host: embedded ? 'media.joj.sk' : 'www.joj.sk' },
        Date: { now: () => now },
        getComputedStyle: () => ({ visibility: 'visible', display: 'block' }),
        MouseEvent: class {},
        MutationObserver: class {
            constructor(callback) { mutation = callback; }
            observe() {}
        },
        setInterval(callback, period) {
            const id = intervals.size + 1;
            intervals.set(id, { callback, period, next: now + period });
            return id;
        },
        clearInterval(id) { intervals.delete(id); },
    });
    return {
        clicks, video, play, state, window, document,
        mutate() { mutation(); },
        pointer(target, isTrusted = true) { listeners.get('pointerdown')({ target, isTrusted }); },
        advance(milliseconds) {
            const until = now + milliseconds;
            while (intervals.size) {
                const next = Math.min(...Array.from(intervals.values(), timer => timer.next));
                if (next > until) break;
                now = next;
                for (const timer of Array.from(intervals.values())) {
                    if (timer.next === now) {
                        timer.next += timer.period;
                        timer.callback();
                    }
                }
            }
            now = until;
        },
    };
}

test('starts promptly after asynchronous setup instead of waiting five seconds', () => {
    const p = page({ readyAt: 2100 });
    p.advance(3100);
    assert.equal(p.video.paused, false);
    assert.deepEqual(p.clicks, [0, 1000, 2000, 3000]);
});

test('does not click again while an accepted play request buffers or plays an ad', () => {
    const p = page({ readyAt: 0 });
    p.advance(10000);
    assert.equal(p.video.readyState, 0); // No frame has arrived yet.
    assert.deepEqual(p.clicks, [0]);
    p.video.paused = true; // A later pause must stay paused.
    p.mutate();
    p.advance(10000);
    assert.deepEqual(p.clicks, [0]);
});

test('page mutations cannot flood the player with repeated Play clicks', () => {
    const p = page();
    for (let i = 0; i < 100; i++) p.mutate();
    assert.deepEqual(p.clicks, [0]);
    p.advance(1000);
    for (let i = 0; i < 100; i++) p.mutate();
    assert.deepEqual(p.clicks, [0, 1000]);
});

test('retries stop after thirty seconds even if the player never starts', () => {
    const p = page();
    p.advance(60000);
    assert.equal(p.clicks.length, 30);
    assert.equal(p.clicks.at(-1), 29000);
    p.mutate();
    assert.equal(p.clicks.length, 30);
});

test('consent blocks playback attempts without making a choice', () => {
    const p = page();
    p.state.consent = true;
    p.advance(5000);
    assert.deepEqual(p.clicks, [0]);
    assert.equal(p.document.activeElement.id, 'didomi-notice-disagree-button');
    p.state.consent = false;
    p.mutate();
    assert.deepEqual(p.clicks, [0, 5000]);
});

test('account gate stops retries and focuses the official sign-in link', () => {
    const p = page();
    p.state.account = true;
    p.mutate();
    p.advance(5000);
    assert.deepEqual(p.clicks, [0]);
    assert.equal(p.document.activeElement.innerText, 'Prihlásiť sa');
});

test('remote activation of a focused player control cancels automatic retries', () => {
    const p = page();
    p.document.activeElement = p.play;
    p.window.__tellyoJojRemoteKey('Enter');
    const userClicks = p.clicks.length;
    p.advance(10000);
    p.mutate();
    assert.equal(p.clicks.length, userClicks);
});

test('trusted pointer interaction cancels startup retries', () => {
    const p = page();
    p.pointer(p.play);
    p.advance(5000);
    p.mutate();
    assert.deepEqual(p.clicks, [0]);
});

test('hidden or disabled Play controls are left alone', () => {
    const p = page();
    p.play.hidden = true;
    p.advance(2000);
    p.play.hidden = false;
    p.play.disabled = true;
    p.advance(2000);
    assert.deepEqual(p.clicks, [0]);
    p.play.disabled = false;
    p.mutate();
    assert.deepEqual(p.clicks, [0, 4000]);
});

test('a background page cannot issue automatic Play requests', () => {
    const p = page();
    p.window.__tellyoMediaLifecycle = { active: false };
    p.advance(2000);
    p.mutate();
    assert.deepEqual(p.clicks, [0]);
    p.window.__tellyoMediaLifecycle.active = true;
    p.mutate();
    assert.deepEqual(p.clicks, [0, 2000]);
});

test('JOJ 24 keeps its broadcaster-managed embed autoplay', () => {
    const p = page({ embedded: true });
    p.advance(10000);
    p.mutate();
    assert.deepEqual(p.clicks, []);
});
