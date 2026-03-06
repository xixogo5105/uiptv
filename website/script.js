const THEME_STORAGE_KEY = 'uiptv-site-theme';
const systemThemeMedia = window.matchMedia('(prefers-color-scheme: dark)');

function resolveTheme(themeChoice) {
    if (themeChoice === 'dark') return 'dark';
    if (themeChoice === 'light') return 'light';
    return systemThemeMedia.matches ? 'dark' : 'light';
}

function applyTheme(themeChoice) {
    const effectiveTheme = resolveTheme(themeChoice);
    document.documentElement.dataset.theme = effectiveTheme;
    document.documentElement.dataset.themeChoice = themeChoice;
    document.querySelectorAll('.theme-btn').forEach((button) => {
        button.setAttribute('aria-pressed', button.dataset.themeChoice === themeChoice ? 'true' : 'false');
    });
}

// Smooth anchor navigation
document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
    anchor.addEventListener('click', (e) => {
        const id = anchor.getAttribute('href');
        if (!id || id === '#') return;

        const target = document.querySelector(id);
        if (!target) return;

        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
});

// Basic OS detection for CTA copy
function detectOS() {
    const ua = window.navigator.userAgent;
    const platform = window.navigator.platform;

    if (['Macintosh', 'MacIntel', 'MacPPC', 'Mac68K'].includes(platform)) return 'macOS';
    if (['Win32', 'Win64', 'Windows', 'WinCE'].includes(platform)) return 'Windows';
    if (/Linux/.test(platform)) return 'Linux';
    if (/Android/.test(ua)) return 'Android';
    if (/iPhone|iPad|iPod/.test(platform)) return 'iOS';
    return null;
}

// Section reveal on scroll
const revealObserver = new IntersectionObserver(
    (entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                entry.target.classList.add('in-view');
                revealObserver.unobserve(entry.target);
            }
        });
    },
    { threshold: 0.16 }
);

document.addEventListener('DOMContentLoaded', () => {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY) || 'system';
    applyTheme(savedTheme);

    document.querySelectorAll('.theme-btn').forEach((button) => {
        button.addEventListener('click', () => {
            const themeChoice = button.dataset.themeChoice || 'system';
            localStorage.setItem(THEME_STORAGE_KEY, themeChoice);
            applyTheme(themeChoice);
        });
    });

    const os = detectOS();
    const text = document.getElementById('download-text');
    if (text && os && ['Windows', 'Linux', 'macOS'].includes(os)) {
        text.textContent = `Download for ${os}`;
    }

    document.querySelectorAll('.reveal').forEach((el, index) => {
        el.style.transitionDelay = `${Math.min(index * 60, 280)}ms`;
        revealObserver.observe(el);
    });
});

systemThemeMedia.addEventListener('change', () => {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY) || 'system';
    if (savedTheme === 'system') {
        applyTheme('system');
    }
});
