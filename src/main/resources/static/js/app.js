/**
 * TECHNO – App Core JavaScript
 * Component loader, cart management, UI interactions
 */

'use strict';

/* ── SECURITY FIX VULN-11: Production-safe logger ─────────────────────────────
 * On production hosts (non-localhost) all console output is suppressed.
 * This prevents leaking error details, stack traces, and internal paths
 * to attackers via browser DevTools.
 * ───────────────────────────────────────────────────────────── */
(function suppressProdConsole() {
  const isLocalhost = ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname)
    || window.location.hostname.startsWith('192.168.');
  if (!isLocalhost) {
    const noop = () => {};
    ['log', 'warn', 'error', 'info', 'debug', 'trace'].forEach(m => {
      try { console[m] = noop; } catch (_) {}
    });
  }
})();

/* ── SECURITY FIX VULN-08: HTML escape helper ─────────────────────────────────
 * Escapes user/server-supplied strings before inserting into innerHTML.
 * Prevents XSS via product names, brands, categories, etc.
 * ───────────────────────────────────────────────────────────── */
function _escHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/* ── Cloudinary Auto-Format & Dimension Optimizer for Fast Load Speed ── */
function _optimizeImageUrl(url, width = 600) {
  if (!url || typeof url !== 'string') return url || '';
  if (url.includes('cloudinary.com') && url.includes('/upload/')) {
    if (!url.includes('/f_auto,q_auto')) {
      return url.replace('/upload/', `/upload/f_auto,q_auto,w_${width}/`);
    }
  }
  return url;
}
window.optimizeImageUrl = _optimizeImageUrl;

/* ── INJECT INTRO LOADER (runs on every page) ── */
(function () {
  if (document.body && document.body.classList.contains('no-loader')) return;
  const s = document.createElement('script');
  s.src = 'js/loader.js';
  s.async = false;
  (document.head || document.documentElement).appendChild(s);
})();

class TechnoApp {
  constructor() {
    this.cart = this._load('cart');
    this.wishlist = this._load('wishlist');
    this._init();
  }

  /* ── INIT ── */
  async _init() {
    this._injectFavicon();
    this._detectHomePage();
    await this._injectNavbar();
    await this._injectFooter();
    this._injectFloatingChat();
    this._bindNavEvents();
    this._bindNewsletterForm();
    this._updateBadges();
    this._initScrollReveal();
    this._initNavScroll();
    this._setActiveNavLink();
    this._updateNavAuthState(); // check session & update profile link
    this._initPasswordToggles();
  }

  _detectHomePage() {
    const page = window.location.pathname.split('/').pop() || 'index.html';
    if (page === 'index.html') {
      document.body.classList.add('home-page');
    }
  }

  _initPasswordToggles() {
    // Wait slightly to ensure DOM is fully ready
    setTimeout(() => {
      document.querySelectorAll('input[type="password"]').forEach(input => {
        if (input.parentElement.classList.contains('pw-toggle-wrap')) return;
        
        // Remove '••••••••' placeholder to let the browser handle it cleanly
        if (input.placeholder === '••••••••' || input.placeholder.includes('•') || input.placeholder.includes('•')) {
            input.placeholder = '';
        }
        
        const wrap = document.createElement('div');
        wrap.className = 'pw-toggle-wrap';
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);
        
        const icon = document.createElement('div');
        icon.className = 'pw-toggle-icon';
        icon.innerHTML = `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
        </svg>`;
        
        icon.addEventListener('click', () => {
          const isPass = input.type === 'password';
          input.type = isPass ? 'text' : 'password';
          icon.innerHTML = isPass ? 
            `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
             </svg>` : 
            `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
             </svg>`;
        });
        
        wrap.appendChild(icon);
      });
    }, 100);
  }

  _injectFavicon() {
    if (document.querySelector('link[rel="icon"]')) return;
    const link = document.createElement('link');
    link.rel = 'icon';
    link.type = 'image/png';
    link.href = 'images/favicon.png';
    document.head.appendChild(link);
  }

  _injectFloatingChat() {
    if (document.getElementById('chat-fab-container')) return;
    
    // Remove old btn if it exists
    const oldBtn = document.getElementById('floating-chat-btn');
    if (oldBtn) oldBtn.remove();

    const container = document.createElement('div');
    container.id = 'chat-fab-container';
    container.className = 'chat-fab-container';
    container.innerHTML = `
      <div class="chat-fab-menu" id="chat-fab-menu">
        <a href="messages.html" class="chat-fab-item tooltip-left" data-tooltip="Leave a Message">
          <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
          </svg>
        </a>
        <a href="https://wa.me/94112345678" target="_blank" rel="noopener noreferrer" class="chat-fab-item whatsapp-item tooltip-left" data-tooltip="WhatsApp Support">
          <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
            <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51a12.8 12.8 0 0 0-.57-.01c-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 0 0-3.48-8.413z"/>
          </svg>
        </a>
      </div>
      <button class="chat-fab-toggle" id="chat-fab-toggle" aria-label="Support options">
        <svg class="icon-open" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm13.36-1.814a.75.75 0 10-1.22-.872l-3.236 4.53L9.53 12.22a.75.75 0 00-1.06 1.06l2.25 2.25a.75.75 0 001.14-.094l3.75-5.25z" style="display:none;"/>
          <path stroke-linecap="round" stroke-linejoin="round" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
        </svg>
        <svg class="icon-close" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" style="display: none;">
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/>
        </svg>
      </button>
    `;
    document.body.appendChild(container);

    const toggleBtn = container.querySelector('#chat-fab-toggle');
    const menu = container.querySelector('#chat-fab-menu');
    const iconOpen = container.querySelector('.icon-open');
    const iconClose = container.querySelector('.icon-close');

    toggleBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      menu.classList.toggle('active');
      const isActive = menu.classList.contains('active');
      iconOpen.style.display = isActive ? 'none' : 'block';
      iconClose.style.display = isActive ? 'block' : 'none';
    });

    document.addEventListener('click', (e) => {
      if (!container.contains(e.target)) {
        menu.classList.remove('active');
        iconOpen.style.display = 'block';
        iconClose.style.display = 'none';
      }
    });
  }

  /* ── COMPONENT INJECTION ── */
  async _injectNavbar() {
    if (window.location.pathname.includes('login.html')) return;
    try {
      const res = await fetch('navbar.html');
      if (!res.ok) return;
      const html = await res.text();
      const wrap = document.createElement('div');
      wrap.innerHTML = html.trim();

      // Force hide ticker bar if not home page
      const page = window.location.pathname.split('/').pop() || 'index.html';
      if (page !== 'index.html') {
        const ticker = wrap.querySelector('#ticker-wrap');
        if (ticker) {
          ticker.style.display = 'none';
        }
      }

      // Insert all children at the start of body
      const fragment = document.createDocumentFragment();
      while (wrap.firstChild) fragment.appendChild(wrap.firstChild);
      document.body.insertBefore(fragment, document.body.firstChild);
    } catch (e) {
      console.warn('[TECHNO] Navbar load failed:', e.message);
    }
  }

  async _injectFooter() {
    if (window.location.pathname.includes('login.html')) return;
    try {
      const res = await fetch('footer.html');
      if (!res.ok) return;
      const html = await res.text();
      const wrap = document.createElement('div');
      wrap.innerHTML = html.trim();

      const footerElement = wrap.firstElementChild;
      document.body.appendChild(footerElement);
      this._populateNewsletterEmail();

      this._loadFooterCategories(footerElement);
    } catch (e) {
      console.warn('[TECHNO] Footer load failed:', e.message);
    }
  }

  async _loadFooterCategories(footerElement) {
    const container = footerElement.querySelector('#footer-categories');
    if (!container) return;

    try {
      const res = await fetch('api/products?view=categories');
      if (res.ok) {
        const data = await res.json();
        if (data.success && Array.isArray(data.categories)) {
          container.innerHTML = data.categories.map(c =>
            `<li><a href="products.html?category=${encodeURIComponent(c.name)}">${c.name}</a></li>`
          ).join('');
        }
      }
    } catch (err) {
      console.warn('[TECHNO] Failed to load footer categories:', err);
    }
  }

  _populateNewsletterEmail() {
    const stored = sessionStorage.getItem('AuraCraft Studio_user');
    const newsletterSection = document.querySelector('.footer-newsletter');
    if (stored) {
      try {
        const u = JSON.parse(stored);
        if (u) {
          if (u.email) {
            const emailInput = document.getElementById('newsletter-email');
            if (emailInput) {
              emailInput.value = u.email;
            }
          }
          if (u.isSubscribed) {
            if (newsletterSection) {
              newsletterSection.style.display = 'none';
            }
            return;
          }
        }
      } catch (_) { }
    }
    if (newsletterSection) {
      newsletterSection.style.display = '';
    }
  }

  /* ── NAV EVENTS ── */
  _bindNavEvents() {
    // Populate search input from URL
    const urlParams = new URLSearchParams(window.location.search);
    const qParam = urlParams.get('q');
    if (qParam) {
      const searchInput = document.getElementById('search-input');
      if (searchInput) searchInput.value = qParam;
    }

    // Search modal
    document.addEventListener('click', e => {
      const searchBtn = document.getElementById('search-btn');
      const searchClose = document.getElementById('search-close');
      const searchModal = document.getElementById('search-modal');
      const searchInput = document.getElementById('search-input');

      if (e.target === searchBtn || searchBtn?.contains(e.target)) {
        searchModal?.classList.add('open');
        setTimeout(() => searchInput?.focus(), 100);
      } else if (e.target === searchClose || searchClose?.contains(e.target)) {
        searchModal?.classList.remove('open');
      } else if (e.target === searchModal) {
        searchModal.classList.remove('open');
      }
    });

    // Keyboard: ESC closes search
    document.addEventListener('keydown', e => {
      if (e.key === 'Escape') {
        document.getElementById('search-modal')?.classList.remove('open');
        const drawer = document.getElementById('nav-drawer');
        if (drawer?.classList.contains('open')) {
          drawer.classList.remove('open');
          document.body.style.overflow = '';
        }
      }
    });

    // Mobile drawer toggle
    document.addEventListener('click', e => {
      const toggle = document.getElementById('nav-mobile-toggle');
      const drawer = document.getElementById('nav-drawer');
      const close = document.getElementById('nav-drawer-close');

      if (e.target === toggle || toggle?.contains(e.target)) {
        drawer?.classList.toggle('open');
        const isOpen = drawer?.classList.contains('open');
        toggle?.setAttribute('aria-expanded', isOpen);
        if (isOpen) {
          document.body.style.overflow = 'hidden';
        } else {
          document.body.style.overflow = '';
        }
      } else if (e.target === close || close?.contains(e.target)) {
        drawer?.classList.remove('open');
        toggle?.setAttribute('aria-expanded', 'false');
        document.body.style.overflow = '';
      }
    });

    // Search form submit & Suggestions
    let searchCache = null;
    document.addEventListener('input', async e => {
      if (e.target && e.target.id === 'search-input') {
        const q = e.target.value.trim().toLowerCase();
        const suggBox = document.getElementById('search-suggestions');
        if (!suggBox) return;

        if (q.length < 2) {
          suggBox.style.display = 'none';
          return;
        }

        if (!searchCache) {
          try {
            const res = await fetch('api/products');
            if (res.ok) {
              const data = await res.json();
              searchCache = data.products || [];
            }
          } catch (err) { console.error(err); }
        }

        if (!searchCache) return;

        const matches = searchCache.filter(p => (p.name && p.name.toLowerCase().includes(q)) || (p.brand && p.brand.toLowerCase().includes(q))).slice(0, 5);

        if (matches.length > 0) {
          // SECURITY FIX VULN-08: escape product name/brand/id before inserting into innerHTML
          suggBox.innerHTML = matches.map(m => `
             <a href="product-view.html?id=${encodeURIComponent(m.id)}" style="display:flex; align-items:center; padding:10px 16px; text-decoration:none; color:inherit; border-bottom:1px solid var(--border);">
                <img src="${_escHtml(m.primaryImage || m.img || '')}" alt="${_escHtml(m.name)}"
                     style="width:40px; height:40px; object-fit:contain; margin-right:12px; border-radius:4px; background:var(--gray-50);"
                     onerror="this.style.display='none'">
                <div>
                   <div style="font-size:14px; font-weight:600;">${_escHtml(m.name)}</div>
                   <div style="font-size:12px; color:var(--text-muted);">Rs. ${(m.price || 0).toFixed(2)}</div>
                </div>
             </a>
           `).join('');
          suggBox.style.display = 'block';
        } else {
          suggBox.innerHTML = `<div style="padding:12px 16px; font-size:13px; color:var(--text-muted);">No products found.</div>`;
          suggBox.style.display = 'block';
        }
      }
    });

    document.addEventListener('click', e => {
      const suggBox = document.getElementById('search-suggestions');
      if (suggBox && e.target.id !== 'search-input' && !suggBox.contains(e.target)) {
        suggBox.style.display = 'none';
      }
    });

    document.addEventListener('keydown', e => {
      if (e.target && e.target.id === 'search-input' && e.key === 'Enter') {
        e.preventDefault();
        const q = e.target.value.trim();
        if (q) window.location.href = `products.html?q=${encodeURIComponent(q)}`;
      }
    });

    // Intercept profile/account link clicks for instant routing without login.html flash
    document.addEventListener('click', e => {
      const link = e.target.closest('#nav-profile-link, #nav-mobile-account-link');
      if (link) {
        e.preventDefault();
        const stored = sessionStorage.getItem('AuraCraft Studio_user');
        if (stored) {
          // Already logged in → go to account
          window.location.href = 'account.html';
        } else {
          // Not logged in → go to login, then come back to account.html
          window.location.href = `login.html?returnUrl=${encodeURIComponent('/account.html')}`;
        }
      }
    });
  }

  /* ── ACTIVE LINK ── */
  _setActiveNavLink() {
    const page = window.location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.nav-links a, .nav-drawer-links a').forEach(a => {
      const href = a.getAttribute('href') || '';
      if (href === page || (page === '' && href === 'index.html')) {
        a.classList.add('active');
      } else {
        a.classList.remove('active');
      }
    });
  }

  /* ── SCROLL: nav shadow + ticker ── */
  _initNavScroll() {
    const nav = document.getElementById('main-nav');
    if (!nav) return;

    const onScroll = () => {
      if (window.scrollY > 20) {
        nav.classList.add('scrolled');
      } else {
        nav.classList.remove('scrolled');
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* ── NEWSLETTER ── */
  _bindNewsletterForm() {
    document.addEventListener('submit', async e => {
      const form = e.target.closest('#newsletter-form');
      if (!form) return;
      e.preventDefault();
      const email = form.querySelector('input[type="email"]')?.value.trim();
      if (!email || !this._validEmail(email)) {
        this.showNotification('Please enter a valid email address.', 'error');
        return;
      }

      try {
        const res = await fetch('api/auth/subscribe', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email })
        });
        const data = await res.json();
        if (data.success) {
          this.showNotification(data.message || 'Thank you for subscribing!');
          form.reset();

          // Update local storage if the user is logged in and the email matches
          const stored = sessionStorage.getItem('AuraCraft Studio_user');
          if (stored) {
            try {
              const u = JSON.parse(stored);
              if (u && u.email && u.email.toLowerCase() === email.toLowerCase()) {
                u.isSubscribed = true;
                sessionStorage.setItem('AuraCraft Studio_user', JSON.stringify(u));
              }
            } catch (_) { }
          }

          // Slide up and fade out the newsletter section smoothly
          const newsletterSection = document.closest ? form.closest('.footer-newsletter') : document.querySelector('.footer-newsletter');
          if (newsletterSection) {
            newsletterSection.style.transition = 'all 0.6s cubic-bezier(0.4, 0, 0.2, 1)';
            newsletterSection.style.opacity = '0';
            newsletterSection.style.maxHeight = '0px';
            newsletterSection.style.paddingTop = '0px';
            newsletterSection.style.paddingBottom = '0px';
            newsletterSection.style.marginTop = '0px';
            newsletterSection.style.marginBottom = '0px';
            newsletterSection.style.overflow = 'hidden';
            setTimeout(() => {
              newsletterSection.remove();
            }, 650);
          }
        } else {
          this.showNotification(data.message || 'Failed to subscribe.', 'error');
        }
      } catch (err) {
        console.error('Subscription error:', err);
        this.showNotification('Network error. Please try again.', 'error');
      }
    });
  }

  _validEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  /* ── AUTH-AWARE NAV ── */
  async _updateNavAuthState() {
    const currentPage = window.location.pathname.split('/').pop() || 'index.html';

    const _setNavToAccount = () => {
      const profileLink = document.getElementById('nav-profile-link');
      const mobileAccountLink = document.getElementById('nav-mobile-account-link');
      const mobileNotif = document.getElementById('nav-mobile-notif');
      if (profileLink) profileLink.href = 'account.html';
      if (mobileAccountLink) mobileAccountLink.href = 'account.html';
      if (mobileNotif) mobileNotif.style.display = 'flex';
    };

    const _setNavToLogin = () => {
      const profileLink = document.getElementById('nav-profile-link');
      const mobileAccountLink = document.getElementById('nav-mobile-account-link');
      const mobileNotif = document.getElementById('nav-mobile-notif');
      if (profileLink) profileLink.href = 'login.html';
      if (mobileAccountLink) mobileAccountLink.href = 'login.html';
      if (mobileNotif) mobileNotif.style.display = 'none';
    };

    // ── PHASE 1: Instant sessionStorage check (synchronous, zero delay) ────
    const stored = sessionStorage.getItem('AuraCraft Studio_user');
    
    const _toggleAdminLink = (isStaff) => {
      const adminLi = document.getElementById('nav-admin-li');
      const mobileAdmin = document.getElementById('nav-mobile-admin');
      if (adminLi) adminLi.style.display = isStaff ? '' : 'none';
      if (mobileAdmin) mobileAdmin.style.display = isStaff ? '' : 'none';
    };

    if (stored) {
      let isLocalStaff = false;
      try {
        const u = JSON.parse(stored);
        if (u && u.role !== 'CUSTOMER') {
          isLocalStaff = true;
        }
      } catch (_) { }

      _toggleAdminLink(isLocalStaff);

      if (currentPage === 'login.html') {
        if (isLocalStaff) {
          window.location.replace('admin.html');
        } else {
          const params = new URLSearchParams(window.location.search);
          const returnUrl = params.get('returnUrl');
          let dest = 'account.html';
          if (returnUrl) {
            try {
              const parsed = new URL(decodeURIComponent(returnUrl), window.location.origin);
              if (parsed.origin === window.location.origin) dest = parsed.href;
            } catch (_) {}
          }
          window.location.replace(dest);
        }
        return;
      }
      _setNavToAccount();
    } else {
      _toggleAdminLink(false);
    }

    // ── PHASE 2: Background server verification ──────────────────────────
    // Only call the server when sessionStorage indicates a user might be logged in.
    // If there is no local session entry at all, skip the API call entirely —
    // this prevents the 401 console error on every page load for guests.
    if (!stored) {
      _setNavToLogin();
      return;
    }

    try {
      const res = await fetch('api/auth/me', { credentials: 'same-origin' });

      // 401/403 = session expired or invalid — clear stale data silently (no console error)
      if (res.status === 401 || res.status === 403) {
        sessionStorage.removeItem('AuraCraft Studio_user');
        _setNavToLogin();
        _toggleAdminLink(false);
        this._updateBadges();
        const emailInput = document.getElementById('newsletter-email');
        if (emailInput) emailInput.value = '';
        return;
      }

      // Any other non-OK status (5xx etc.) — leave nav state unchanged
      if (!res.ok) return;

      const data = await res.json().catch(() => ({ success: false }));

      if (data.success) {
        // Session confirmed alive – ensure links point to correct page
        const isStaff = data.role !== 'CUSTOMER';
        _toggleAdminLink(isStaff);

        if (currentPage === 'login.html') {
          if (isStaff) {
            window.location.replace('admin.html');
          } else {
            const params = new URLSearchParams(window.location.search);
            const returnUrl = params.get('returnUrl');
            let dest = 'account.html';
            if (returnUrl) {
              try {
                const parsed = new URL(decodeURIComponent(returnUrl), window.location.origin);
                if (parsed.origin === window.location.origin) dest = parsed.href;
              } catch (_) {}
            }
            window.location.replace(dest);
          }
          return;
        }
        _setNavToAccount();

        // Refresh stored user details
        try {
          const u = stored ? JSON.parse(stored) : {};
          u.id = data.id;
          u.name = data.fullName;
          u.email = data.email;
          u.role = data.role;
          u.isSubscribed = data.isSubscribed;
          sessionStorage.setItem('AuraCraft Studio_user', JSON.stringify(u));
        } catch (_) { }

        // Sync Cart & Wishlist with Backend
        this._syncCartWithBackend();
        this._syncWishlistWithBackend();
        this._populateNewsletterEmail();
      } else {
        // Server says not logged in → clear stale data and revert links
        sessionStorage.removeItem('AuraCraft Studio_user');
        _setNavToLogin();
        _toggleAdminLink(false);
        this._updateBadges();
        const emailInput = document.getElementById('newsletter-email');
        if (emailInput) emailInput.value = '';
      }
    } catch (_) {
      // Network error – trust sessionStorage for now, don't change anything
    }
  }

  /* ── SCROLL REVEAL ── */
  _initScrollReveal() {
    const els = document.querySelectorAll('.reveal');
    if (!els.length) return;

    const observer = new IntersectionObserver(entries => {
      entries.forEach((entry, i) => {
        if (entry.isIntersecting) {
          setTimeout(() => entry.target.classList.add('visible'), i * 60);
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });

    els.forEach(el => observer.observe(el));
  }

  /* ── AUTH HELPERS ── */
  requireLogin(actionDesc) {
    const stored = sessionStorage.getItem('AuraCraft Studio_user');
    if (!stored) {
      const returnUrl = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = `login.html?returnUrl=${returnUrl}`;
      return false;
    }
    return true;
  }

  /* ── CART ── */
  async addToCart(item) {
    // Local cart validation for max 10
    const existingIdx = this.cart.findIndex(c => {
      if (c.variantId && item.variantId) return c.variantId === item.variantId;
      return c.id === item.id;
    });
    const currentQty = existingIdx !== -1 ? (this.cart[existingIdx].quantity || 1) : 0;
    const addingQty = item.quantity || 1;
    if (currentQty + addingQty > 10) {
      this.showNotification('For wholesale orders exceeding 10 items, please contact us at +94 11 234 5678 or email admin@auracraft.com', 'warning');
      return;
    }

    const payload = {
      action: 'add',
      quantity: addingQty
    };
    if (item.variantId) {
      payload.variantId = item.variantId;
    } else {
      payload.id = item.id;
    }
    if (item.engravingText) payload.engravingText = item.engravingText;
    if (item.customResize) payload.customResize = item.customResize;
    
    const isLoggedIn = !!sessionStorage.getItem('AuraCraft Studio_user');

    if (isLoggedIn) {
      try {
        const res = await fetch('api/cart', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!data.success) {
          this.showNotification(data.message || 'Failed to add to cart', 'error');
          return;
        }
      } catch (err) {
        console.error('Add to cart error:', err);
        this.showNotification('Network error. Failed to add to cart.', 'error');
        return;
      }
    }

    // Local storage update (runs for both guests and logged-in users after successful API call)
    const idx = this.cart.findIndex(c => {
      if (c.variantId && item.variantId) {
        return c.variantId === item.variantId;
      }
      return c.id === item.id;
    });

    if (idx !== -1 && 
        this.cart[idx].engravingText === (item.engravingText || '') &&
        this.cart[idx].customResize === (item.customResize || '')) {
      this.cart[idx].quantity = (this.cart[idx].quantity || 1) + (item.quantity || 1);
    } else {
      this.cart.push({ ...item, quantity: item.quantity || 1 });
    }
    this._save('cart', this.cart);
    this._updateBadges();
    this.showNotification(`"${item.name}" added to cart`, 'success');
  }

  removeFromCart(id) {
    // Use || so item is removed if EITHER its id OR variantId matches
    this.cart = this.cart.filter(c => c.id !== id && c.variantId !== id);
    this._save('cart', this.cart);
    this._updateBadges();

    const isLoggedIn = !!sessionStorage.getItem('AuraCraft Studio_user');
    if (isLoggedIn) {
      // API update — send both id and variantId so servlet can match either
      fetch(`api/cart?variantId=${id}&id=${id}`, {
        method: 'DELETE',
        credentials: 'same-origin'
      }).catch(console.error);
    }
  }

  clearCart() {
    this.cart = [];
    this._save('cart', this.cart);
    this._updateBadges();

    const isLoggedIn = !!sessionStorage.getItem('AuraCraft Studio_user');
    if (isLoggedIn) {
      // API update
      fetch('api/cart?clear=true', {
        method: 'DELETE',
        credentials: 'same-origin'
      }).catch(console.error);
    }
  }


  /* ── WISHLIST ── */
  async addToWishlist(item) {
    if (this.wishlist.some(w => w.id === item.id)) {
      this.showNotification('Already in your wishlist!', 'info');
      return;
    }
    
    const isLoggedIn = !!sessionStorage.getItem('AuraCraft Studio_user');

    if (isLoggedIn) {
      try {
        const res = await fetch('api/wishlist', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'add', id: item.id })
        });
        const data = await res.json();
        if (!data.success) {
          this.showNotification(data.message || 'Failed to add to wishlist', 'error');
          return;
        }
      } catch (err) {
        console.error('Add to wishlist error:', err);
        this.showNotification('Network error. Failed to add to wishlist.', 'error');
        return;
      }
    }

    this.wishlist.push(item);
    this._save('wishlist', this.wishlist);
    this._updateBadges();
    this.showNotification(`"${item.name}" added to wishlist`, 'success');
  }

  async removeFromWishlist(id) {
    if (!id || id === 'undefined' || id === 'null') {
      console.warn('[Wishlist] removeFromWishlist called with empty/invalid ID:', id);
      return;
    }
    // Filter out by product id (wishlist items use product id)
    this.wishlist = this.wishlist.filter(w => w.id !== id && w.id !== parseInt(id));
    this._save('wishlist', this.wishlist);
    this._updateBadges();

    const isLoggedIn = !!sessionStorage.getItem('AuraCraft Studio_user');
    if (isLoggedIn) {
      // API update
      await fetch(`api/wishlist?id=${id}`, {
        method: 'DELETE',
        credentials: 'same-origin'
      }).catch(console.error);
    }
  }


  async clearWishlist() {
    this.wishlist = [];
    this._save('wishlist', this.wishlist);
    this._updateBadges();

    const isLoggedIn = !!sessionStorage.getItem('AuraCraft Studio_user');
    if (isLoggedIn) {
      // API update
      await fetch('api/wishlist?clear=true', {
        method: 'DELETE',
        credentials: 'same-origin'
      }).catch(console.error);
    }
  }

  /* ── SYNCING ── */
  async _syncCartWithBackend() {
    // 1. Send local cart to backend
    if (this.cart.length > 0) {
      await fetch('api/cart', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'sync', items: this.cart })
      }).catch(console.error);
    }
    // 2. Fetch merged cart from backend
    try {
      const res = await fetch('api/cart');
      if (res.ok) {
        const data = await res.json();
        if (data.success && Array.isArray(data.cart)) {
          if (data.cart.length > 0) {
            // Backend has items — use them as source of truth
            this.cart = data.cart;
          } else if (this.cart.length === 0) {
            // Both empty — that's fine
            this.cart = [];
          }
          // If backend empty but local has items: keep local until next sync succeeds
          this._save('cart', this.cart);
          this._updateBadges();
        }
      }
    } catch (e) { console.error('Cart sync fail', e); }
  }

  async _syncWishlistWithBackend() {
    // 1. Send local wishlist to backend
    if (this.wishlist.length > 0) {
      await fetch('api/wishlist', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'sync', items: this.wishlist })
      }).catch(console.error);
    }
    // 2. Fetch merged wishlist from backend
    try {
      const res = await fetch('api/wishlist');
      if (res.ok) {
        const data = await res.json();
        if (data.success && Array.isArray(data.wishlist)) {
          if (data.wishlist.length > 0) {
            // Backend has items — merge: prefer backend but keep any local-only items
            const backendIds = new Set(data.wishlist.map(w => w.id));
            const localOnly = this.wishlist.filter(w => !backendIds.has(w.id));
            this.wishlist = [...data.wishlist, ...localOnly];
          } else if (this.wishlist.length === 0) {
            // Both empty — that's fine
            this.wishlist = [];
          }
          // If backend empty but local has items: keep local until sync POST completes
          this._save('wishlist', this.wishlist);
          this._updateBadges();
        }
      }
    } catch (e) { console.error('Wishlist sync fail', e); }
  }

  /* ── BADGE UPDATE ── */
  _updateBadges() {
    const isLoggedIn = !!sessionStorage.getItem('AuraCraft Studio_user');

    const cartCount = document.getElementById('cart-count');
    const total = this.cart.reduce((sum, i) => sum + (i.quantity || 1), 0);
    if (cartCount) {
      cartCount.textContent = total;
      cartCount.style.display = total > 0 ? 'flex' : 'none';
    }
    const wishlistCount = document.getElementById('wishlist-count');
    const totalW = this.wishlist.length;
    if (wishlistCount) {
      wishlistCount.textContent = totalW;
      wishlistCount.style.display = totalW > 0 ? 'flex' : 'none';
    }

    const mobileCartBtn = document.getElementById('nav-mobile-cart-btn');
    if (mobileCartBtn) {
      mobileCartBtn.textContent = `Cart (${total})`;
    }

    window.dispatchEvent(new CustomEvent('cartUpdated'));
    window.dispatchEvent(new CustomEvent('wishlistUpdated'));
  }

  /* ── TOAST NOTIFICATION ── */
  showNotification(message, type = 'success') {
    // Remove existing toasts with a nice fade-out
    document.querySelectorAll('.toast').forEach(t => {
      t.classList.remove('show');
      setTimeout(() => t.remove(), 400);
    });

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'status');
    toast.setAttribute('aria-live', 'polite');

    let icon = '<svg style="width:14px;height:14px;" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>';
    if (type === 'error') icon = '<svg style="width:14px;height:14px;" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>';
    else if (type === 'warning') icon = '<svg style="width:14px;height:14px;" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>';
    else if (type === 'info') icon = '<svg style="width:14px;height:14px;" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>';

    toast.innerHTML = `
      <span style="
        width:22px; height:22px; background:rgba(0,0,0,0.06);
        border-radius:50%; display:flex; align-items:center;
        justify-content:center; font-size:12px; flex-shrink:0;
        font-weight: bold;
      ">${icon}</span>
      <span style="font-size:13px; font-weight: 500;">${message}</span>
    `;

    document.body.appendChild(toast);

    // Trigger reflow & show toast
    requestAnimationFrame(() => {
      toast.classList.add('show');
    });

    // Automatically dismiss after 4 seconds
    setTimeout(() => {
      toast.classList.remove('show');
      setTimeout(() => toast.remove(), 400);
    }, 4000);
  }

  /* ── LOCAL STORAGE HELPERS ── */
  _load(key) {
    try {
      return JSON.parse(sessionStorage.getItem(`techno_${key}`)) || [];
    } catch {
      return [];
    }
  }

  _save(key, value) {
    try {
      sessionStorage.setItem(`techno_${key}`, JSON.stringify(value));
    } catch (e) {
      console.warn('[TECHNO] Storage error:', e);
    }
  }
}

/* ── GLOBAL INIT ── */
document.addEventListener('DOMContentLoaded', () => {
  window.AppLoader = new TechnoApp();
});

/* ── GLOBAL HELPER FUNCTIONS (called from HTML onclick) ── */
function addToCart(item) {
  window.AppLoader?.addToCart(item);
}

function addToWishlist(item) {
  window.AppLoader?.addToWishlist(item);
}

/* ── MODULE EXPORT (optional) ── */
if (typeof module !== 'undefined' && module.exports) {
  module.exports = TechnoApp;
}

/* -- NOTIFICATIONS LOGIC -- */
let notifOpen = false;

async function fetchNotifications() {
  // Only fetch notifications if the user is logged in — prevents 401 for guests
  if (!sessionStorage.getItem('AuraCraft Studio_user')) return;

  try {
    const res = await fetch('api/notifications', { credentials: 'same-origin' });

    // Silently ignore 401/403 (session expired) without logging to console
    if (res.status === 401 || res.status === 403) {
      sessionStorage.removeItem('AuraCraft Studio_user');
      const badge = document.getElementById('notif-badge');
      if (badge) badge.style.display = 'none';
      return;
    }

    if (res.ok) {
      const data = await res.json();
      
      // Handle the new graceful 200 OK unauthenticated response
      if (data.success === false && data.message === 'Not authenticated') {
        sessionStorage.removeItem('AuraCraft Studio_user');
        const badge = document.getElementById('notif-badge');
        if (badge) badge.style.display = 'none';
        return;
      }

      let unreadCount = 0;
      // SECURITY FIX VULN-04: build notification list with safe DOM elements
      // instead of injecting server-supplied HTML via innerHTML (prevents Stored XSS).
      const listEl = document.getElementById('notif-list');
      if (!listEl) return; // guard

      // Clear existing content safely
      while (listEl.firstChild) listEl.removeChild(listEl.firstChild);

      if (data.notifications && data.notifications.length > 0) {
        data.notifications.forEach(n => {
          if (!n.isRead) unreadCount++;

          const item = document.createElement('div');
          item.style.cssText = `padding:10px; border-bottom:1px solid #f5f5f5; ${!n.isRead ? 'background:#f0f8ff;' : ''}`;

          const msgDiv = document.createElement('div');
          msgDiv.style.cssText = 'font-size:13px; margin-bottom:4px;';
          msgDiv.textContent = n.message; // safe: textContent, not innerHTML

          const timeDiv = document.createElement('div');
          timeDiv.style.cssText = 'font-size:10px; color:#888;';
          timeDiv.textContent = n.createdAt; // safe: textContent

          item.appendChild(msgDiv);
          item.appendChild(timeDiv);
          listEl.appendChild(item);
        });
      } else {
        const empty = document.createElement('div');
        empty.style.cssText = 'padding:12px; font-size:13px; color:#888; text-align:center;';
        empty.textContent = 'No notifications';
        listEl.appendChild(empty);
      }

      const badge = document.getElementById('notif-badge');
      const mobileBadge = document.getElementById('nav-mobile-notif-badge');
      if (unreadCount > 0) {
        if (badge) {
          badge.textContent = unreadCount;
          badge.style.display = 'flex';
        }
        if (mobileBadge) {
          mobileBadge.textContent = unreadCount;
          mobileBadge.style.display = 'inline-flex';
        }
      } else {
        if (badge) badge.style.display = 'none';
        if (mobileBadge) mobileBadge.style.display = 'none';
      }
    }
  } catch (e) { /* silently ignore network errors for notifications */ }
}

window.toggleNotifications = function () {
  // Guard: only open notifications if user is logged in
  if (!sessionStorage.getItem('AuraCraft Studio_user')) return;

  const dd = document.getElementById('notif-dropdown');
  if (!dd) return;
  notifOpen = !notifOpen;
  dd.style.display = notifOpen ? 'block' : 'none';
  if (notifOpen) fetchNotifications();
};

document.addEventListener('click', function(event) {
  const dd = document.getElementById('notif-dropdown');
  const btn = document.getElementById('notif-btn');
  if (notifOpen && dd && btn && !dd.contains(event.target) && !btn.contains(event.target)) {
    notifOpen = false;
    dd.style.display = 'none';
  }
});

window.markNotificationsRead = async function () {
  try {
    await fetch('api/notifications', { method: 'PUT', credentials: 'same-origin' });
    fetchNotifications();
  } catch (e) { }
};

window.clearNotifications = async function () {
  const proceed = async () => {
    try {
      await fetch('api/notifications', { method: 'DELETE', credentials: 'same-origin' });
      fetchNotifications();
    } catch (e) { }
  };
  if (typeof window.showConfirmDialog === 'function') {
    window.showConfirmDialog("Are you sure you want to clear all your notifications?", proceed);
  } else {
    await proceed();
  }
};

// Only run the initial notification fetch if user appears to be logged in
if (sessionStorage.getItem('AuraCraft Studio_user')) {
  setTimeout(fetchNotifications, 1500);
}

// ── Customer Real-Time WebSocket ─────────────────────────────────────────────
// Connects each logged-in customer to their own WebSocket channel so that
// order-status notifications pushed by the admin are received immediately
// (without waiting for the next poll).
(function connectCustomerWebSocket() {
  const raw = sessionStorage.getItem('AuraCraft Studio_user');
  if (!raw) return;

  let userId;
  try {
    const user = JSON.parse(raw);
    userId = user.id;
  } catch (e) {
    return; // Malformed stored user — skip
  }
  if (!userId) return;

  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const contextPath = window.location.pathname.replace(/\/[^/]*$/, ''); // strip filename
  const wsUrl = `${wsProtocol}//${window.location.host}${contextPath}/ws/notifications?userId=${userId}`;

  const ws = new WebSocket(wsUrl);

  ws.onmessage = function (event) {
    try {
      const data = JSON.parse(event.data);
      // Refresh the notification bell/list immediately
      fetchNotifications();
      // Also show a toast so the customer sees the update even if bell is closed
      if (data.message && window.AppLoader && window.AppLoader.showNotification) {
        window.AppLoader.showNotification(data.message, 'info');
      } else if (data.message) {
        // Fallback if AppLoader is not in scope
        showNotification(data.message, 'info');
      }
    } catch (e) { /* ignore parse errors */ }
  };

  ws.onclose = function () {
    // Reconnect after 10 seconds if connection drops
    setTimeout(connectCustomerWebSocket, 10000);
  };

  ws.onerror = function () {
    ws.close(); // triggers onclose → reconnect
  };
})();

window.showConfirmDialog = function (message, onConfirm) {
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.width = '100vw';
    overlay.style.height = '100vh';
    overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.5)';
    overlay.style.zIndex = '999999';
    overlay.style.display = 'flex';
    overlay.style.alignItems = 'center';
    overlay.style.justifyContent = 'center';
    overlay.style.backdropFilter = 'blur(4px)';
    overlay.style.opacity = '0';
    overlay.style.transition = 'opacity 0.2s ease';

    const modal = document.createElement('div');
    modal.style.backgroundColor = '#ffffff';
    modal.style.border = '2px solid #000000';
    modal.style.padding = '32px 40px';
    modal.style.borderRadius = '0px';
    modal.style.boxShadow = '8px 8px 0px rgba(0, 0, 0, 1)';
    modal.style.maxWidth = '400px';
    modal.style.width = '90%';
    modal.style.textAlign = 'center';
    modal.style.transform = 'translateY(20px)';
    modal.style.transition = 'transform 0.2s ease';

    const text = document.createElement('p');
    text.textContent = message;
    text.style.fontFamily = 'var(--font-display, Inter, sans-serif)';
    text.style.fontSize = '18px';
    text.style.fontWeight = '600';
    text.style.color = '#000000';
    text.style.margin = '0 0 32px 0';

    const btnContainer = document.createElement('div');
    btnContainer.style.display = 'flex';
    btnContainer.style.gap = '16px';
    btnContainer.style.justifyContent = 'center';

    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    cancelBtn.style.padding = '10px 24px';
    cancelBtn.style.backgroundColor = '#ffffff';
    cancelBtn.style.color = '#000000';
    cancelBtn.style.border = '2px solid #000000';
    cancelBtn.style.fontFamily = 'inherit';
    cancelBtn.style.fontWeight = '600';
    cancelBtn.style.fontSize = '14px';
    cancelBtn.style.cursor = 'pointer';
    cancelBtn.style.transition = 'all 0.2s ease';
    cancelBtn.onmouseover = () => { cancelBtn.style.backgroundColor = '#f0f0f0'; };
    cancelBtn.onmouseout = () => { cancelBtn.style.backgroundColor = '#ffffff'; };

    const confirmBtn = document.createElement('button');
    confirmBtn.textContent = 'Confirm';
    confirmBtn.style.padding = '10px 24px';
    confirmBtn.style.backgroundColor = '#000000';
    confirmBtn.style.color = '#ffffff';
    confirmBtn.style.border = '2px solid #000000';
    confirmBtn.style.fontFamily = 'inherit';
    confirmBtn.style.fontWeight = '600';
    confirmBtn.style.fontSize = '14px';
    confirmBtn.style.cursor = 'pointer';
    confirmBtn.style.transition = 'all 0.2s ease';
    confirmBtn.onmouseover = () => {
      confirmBtn.style.backgroundColor = '#ffffff';
      confirmBtn.style.color = '#000000';
    };
    confirmBtn.onmouseout = () => {
      confirmBtn.style.backgroundColor = '#000000';
      confirmBtn.style.color = '#ffffff';
    };

    const close = () => {
      overlay.style.opacity = '0';
      modal.style.transform = 'translateY(20px)';
      setTimeout(() => overlay.remove(), 200);
    };

    cancelBtn.onclick = () => {
      close();
      resolve(false);
    };
    confirmBtn.onclick = async () => {
      close();
      if (typeof onConfirm === 'function') await onConfirm();
      resolve(true);
    };

    btnContainer.appendChild(cancelBtn);
    btnContainer.appendChild(confirmBtn);

    modal.appendChild(text);
    modal.appendChild(btnContainer);
    overlay.appendChild(modal);
    document.body.appendChild(overlay);

    requestAnimationFrame(() => {
      overlay.style.opacity = '1';
      modal.style.transform = 'translateY(0)';
    });
  });
};

window.showAlertDialog = function (message) {
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.width = '100vw';
    overlay.style.height = '100vh';
    overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.5)';
    overlay.style.zIndex = '999999';
    overlay.style.display = 'flex';
    overlay.style.alignItems = 'center';
    overlay.style.justifyContent = 'center';
    overlay.style.backdropFilter = 'blur(4px)';
    overlay.style.opacity = '0';
    overlay.style.transition = 'opacity 0.2s ease';

    const modal = document.createElement('div');
    modal.style.backgroundColor = '#ffffff';
    modal.style.border = '2px solid #000000';
    modal.style.padding = '32px 40px';
    modal.style.borderRadius = '0px';
    modal.style.boxShadow = '8px 8px 0px rgba(0, 0, 0, 1)';
    modal.style.maxWidth = '400px';
    modal.style.width = '90%';
    modal.style.textAlign = 'center';
    modal.style.transform = 'translateY(20px)';
    modal.style.transition = 'transform 0.2s ease';

    const text = document.createElement('p');
    text.textContent = message;
    text.style.fontFamily = 'var(--font-display, Inter, sans-serif)';
    text.style.fontSize = '18px';
    text.style.fontWeight = '600';
    text.style.color = '#000000';
    text.style.margin = '0 0 32px 0';

    const btnContainer = document.createElement('div');
    btnContainer.style.display = 'flex';
    btnContainer.style.justifyContent = 'center';

    const okBtn = document.createElement('button');
    okBtn.textContent = 'OK';
    okBtn.style.padding = '10px 32px';
    okBtn.style.backgroundColor = '#000000';
    okBtn.style.color = '#ffffff';
    okBtn.style.border = '2px solid #000000';
    okBtn.style.fontFamily = 'inherit';
    okBtn.style.fontWeight = '600';
    okBtn.style.fontSize = '14px';
    okBtn.style.cursor = 'pointer';

    const close = () => {
      overlay.style.opacity = '0';
      modal.style.transform = 'translateY(20px)';
      setTimeout(() => overlay.remove(), 200);
      resolve(true);
    };

    okBtn.onclick = close;
    btnContainer.appendChild(okBtn);
    modal.appendChild(text);
    modal.appendChild(btnContainer);
    overlay.appendChild(modal);
    document.body.appendChild(overlay);

    requestAnimationFrame(() => {
      overlay.style.opacity = '1';
      modal.style.transform = 'translateY(0)';
    });
  });
};
