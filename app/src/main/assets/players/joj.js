// Fit JOJ's own players and expose their existing controls to a TV remote.
// Sources, advertisements, consent and account checks remain broadcaster-owned.
(function () {
    if (window.__tellyoJojInstalled) return;
    window.__tellyoJojInstalled = true;
    const embedded = location.host === 'media.joj.sk';
    const style = document.createElement('style');
    style.id = 'tellyo-joj-fit';
    style.textContent = 'html,body{margin:0!important;overflow:hidden!important;background:black!important;}' +
        '.joj-sticky-player-wrapper{position:fixed!important;inset:0!important;width:100vw!important;' +
            'height:100vh!important;margin:0!important;z-index:30!important;background:black!important;}' +
        '.joj-sticky-player-wrapper>div,.joj-sticky-player-wrapper>div>div,' +
        '.joj-sticky-player-wrapper .aspect-video{width:100%!important;height:100%!important;' +
            'aspect-ratio:auto!important;border-radius:0!important;}' +
        'header{display:none!important;}' +
        '.joj-sticky-player-wrapper video,.b-player video{object-fit:contain!important;object-position:50% 50%!important;}' +
        '.didomi-popup-notice :focus,.tellyo-joj-login :focus,.joj-sticky-player-wrapper :focus,#player-1 :focus{' +
            'outline:3px solid #1aa8ff!important;outline-offset:3px!important;}' +
        '.tellyo-joj-login{z-index:40!important;}' +
        '.tellyo-joj-login>div{max-height:100vh!important;overflow:auto!important;}';
    if (embedded) style.textContent += '.b-player,.player-wrap,#player-1{' +
        'width:100vw!important;height:100vh!important;margin:0!important;padding:0!important;}';
    document.head.appendChild(style);
    window.scrollTo(0, 0);

    function visible(element) {
        if (!element || element.disabled) return false;
        const rect = element.getBoundingClientRect();
        const css = getComputedStyle(element);
        return rect.width > 0 && rect.height > 0 && css.visibility !== 'hidden' && css.display !== 'none';
    }
    function root() { return document.querySelector(embedded ? '#player-1' : '.joj-sticky-player-wrapper'); }
    function consentButtons() {
        return ['didomi-notice-disagree-button', 'didomi-notice-learn-more-button', 'didomi-notice-agree-button']
            .map(id => document.getElementById(id)).filter(b => visible(b) && b.closest('.didomi-popup-notice'));
    }
    function login() {
        const input = Array.from(document.querySelectorAll('input[type=password]')).find(visible);
        const modal = input && input.closest('.fixed');
        if (modal && !modal.classList.contains('tellyo-joj-login')) modal.classList.add('tellyo-joj-login');
        return modal;
    }
    function accountButtons() {
        return root() ? Array.from(root().querySelectorAll('a')).filter(e => visible(e) &&
            /^(prihlásiť sa|registrovať sa)$/i.test(e.innerText.trim())) : [];
    }
    let consentSelection = 'didomi-notice-disagree-button';
    let startupFinished = embedded;
    let startupDeadline = null;
    let nextStartAt = 0;
    function activate(element) {
        // Tivio binds its action to the icon inside the titled container.
        // Clicking the container itself never reaches that child handler.
        const target = element.matches('[title]') ? element.querySelector('svg,img') || element :
            element.tagName === 'A' ? element.firstElementChild || element : element;
        target.dispatchEvent(new MouseEvent('click', {bubbles:true,cancelable:true,view:window}));
    }
    function refresh() {
        const buttons = consentButtons();
        if (buttons.length) {
            if (!buttons.includes(document.activeElement)) {
                (buttons.find(b => b.id === consentSelection) || buttons[0]).focus({preventScroll:true});
            }
            return;
        }
        if (visible(document.querySelector('#didomi-popup'))) return;
        const modal = login();
        if (modal) return;
        const account = accountButtons();
        if (account.length) {
            startupFinished = true;
            if (!account.includes(document.activeElement)) {
                account[0].tabIndex = 0;
                account[0].focus({preventScroll:true});
            }
            return;
        }
        if (!startupFinished) {
            const now = Date.now();
            if (startupDeadline !== null && now >= startupDeadline) {
                startupFinished = true;
                return;
            }
            const player = root();
            // A successful play request sets paused=false before frames arrive.
            // Leave buffering and pre-roll alone rather than issuing more clicks.
            if (player && Array.from(player.querySelectorAll('video')).some(v => !v.paused)) {
                startupFinished = true;
                return;
            }
            if (window.__tellyoMediaLifecycle && !window.__tellyoMediaLifecycle.active) return;
            const play = player && player.querySelector('[title="Spustiť"]');
            // The control renders before Tivio finishes its asynchronous source
            // setup. Retry promptly within a 30-second window, but only while
            // its visible Play control remains available and media is paused.
            if (visible(play) && now >= nextStartAt) {
                if (startupDeadline === null) startupDeadline = now + 30000;
                nextStartAt = now + 1000;
                activate(play);
            }
        }
    }
    new MutationObserver(refresh).observe(document.body, {
        childList:true,subtree:true,attributes:true,attributeFilter:['title','disabled']
    });
    const startupTimer = setInterval(function () {
        refresh();
        if (startupFinished) clearInterval(startupTimer);
    }, 250);
    document.addEventListener('pointerdown', function (event) {
        if (event.isTrusted && root() && root().contains(event.target)) startupFinished = true;
    }, true);
    refresh();
    // Clear the Radiant embed's unattended autoplay mute through its volume API
    // once; later viewer mute and pause choices remain intact.
    function audibleStart() {
        if (!embedded || !window.Rmp || !Rmp.player) return;
        if (!Array.from(document.querySelectorAll('video')).some(v => !v.paused && v.readyState >= 2)) return;
        Rmp.player.mute = false;
        document.removeEventListener('playing', audibleStart, true);
    }
    document.addEventListener('playing', audibleStart, true);
    audibleStart();

    function controls(scope) {
        return scope ? Array.from(scope.querySelectorAll('button,a,input,[role=button],[title]'))
            .filter(visible).filter(e => e.tagName !== 'VIDEO') : [];
    }
    function togglePlayback() {
        if (embedded && window.Rmp && Rmp.player) {
            if (Rmp.player.paused) Rmp.player.play(); else Rmp.player.pause();
            return true;
        }
        const control = root() && root().querySelector('[title="Spustiť"],[title="Pozastaviť"],[title="Pauza"]');
        if (control) { activate(control); return true; }
        return false;
    }
    function remoteKey(key) {
        let buttons = consentButtons();
        const isConsent = buttons.length > 0;
        const modal = login();
        const popup = document.querySelector('#didomi-popup');
        const popupVisible = visible(popup);
        const account = accountButtons();
        if (!isConsent && !modal && !popupVisible && !account.length && key === 'Enter' &&
            (!root() || !root().contains(document.activeElement) || !document.activeElement.matches('button,a,input,[role=button],[title]'))) {
            startupFinished = true;
            if (togglePlayback()) return true;
        }
        if (!buttons.length) buttons = account.length && !modal && !popupVisible ? account :
            controls(popupVisible ? popup : (modal || root()));
        if (!buttons.length) return false;
        let index = buttons.indexOf(document.activeElement);
        if (index < 0) index = isConsent ? buttons.findIndex(b => b.id === consentSelection) : 0;
        if (key.startsWith('Arrow')) {
            const step = key === 'ArrowRight' || key === 'ArrowDown' ? 1 : -1;
            const selected = buttons[(Math.max(index, 0) + step + buttons.length) % buttons.length];
            if (isConsent) consentSelection = selected.id;
            if (!selected.hasAttribute('tabindex')) selected.tabIndex = 0;
            selected.focus({preventScroll:!modal});
        } else if (key === 'Enter') {
            const selected = buttons[Math.max(index, 0)];
            if (!isConsent && !modal && !popupVisible && root() && root().contains(selected)) {
                startupFinished = true;
            }
            selected.focus();
            activate(selected);
            if (selected.tagName === 'INPUT' && !['checkbox','radio','submit','button'].includes(selected.type)) return 'input';
        } else return false;
        return true;
    }
    window.__tellyoJojRemoteKey = remoteKey;
    document.addEventListener('keydown', function (event) {
        if (remoteKey(event.key)) { event.preventDefault(); event.stopPropagation(); }
    }, true);
})()
