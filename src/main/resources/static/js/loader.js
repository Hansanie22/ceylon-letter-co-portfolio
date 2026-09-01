/**
 * CEYLON LETTER CO. — Page Loader Animation
 * Runs on first visit per session. Shows branded intro with logo flyout to navbar.
 */

(function () {
  'use strict';

  // ── Create overlay DOM (Played on Every Page Load) ──
  const overlay = document.createElement('div');
  overlay.id = 'clc-loader';
  overlay.setAttribute('aria-hidden', 'true');

  overlay.innerHTML = `
    <style>
      #clc-loader {
        position: fixed;
        inset: 0;
        z-index: 999999;
        background: #2A2622;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: column;
        overflow: hidden;
        pointer-events: all;
      }

      /* Paper noise texture overlay */
      #clc-loader::before {
        content: '';
        position: absolute;
        inset: 0;
        background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.75' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E");
        pointer-events: none;
        z-index: 0;
      }

      /* Floating stationery items */
      .clc-float {
        position: absolute;
        top: 50%;
        left: 50%;
        opacity: 0;
        transform: translate(-50%, -50%) scale(0);
        will-change: transform, opacity;
        pointer-events: none;
      }

      /* Diamond */
      .clc-diamond {
        width: 36px;
        height: 36px;
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .clc-diamond svg {
        width: 100%;
        height: 100%;
        stroke: #C9A96E;
        fill: none;
        stroke-width: 1.2;
        filter: drop-shadow(0 4px 6px rgba(0,0,0,0.1));
      }

      /* Ring */
      .clc-ring {
        width: 32px;
        height: 32px;
        border: 2px solid #C9A96E;
        border-radius: 50%;
        position: relative;
        box-shadow: inset 0 0 8px rgba(201,169,110,0.4), 0 4px 10px rgba(0,0,0,0.15);
      }
      .clc-ring::before {
        content: '';
        position: absolute;
        top: -4px;
        left: 50%;
        transform: translateX(-50%) rotate(45deg);
        width: 8px;
        height: 8px;
        background: #FBF4EA;
        border: 1px solid #C9A96E;
        box-shadow: 0 0 6px rgba(201,169,110,0.6);
      }

      /* Sparkle/Star */
      .clc-star {
        width: 24px;
        height: 24px;
        position: relative;
      }
      .clc-star svg {
        width: 100%;
        height: 100%;
        fill: #C9A96E;
        filter: drop-shadow(0 2px 4px rgba(201,169,110,0.3));
      }

      /* SVG Logo */
      #clc-logo-svg {
        position: relative;
        z-index: 10;
        width: min(90vw, 1200px);
      }

      #clc-logo-text {
        fill: transparent;
        stroke: #F5E6D0;
        stroke-width: 1.2px;
        stroke-dasharray: 2000;
        stroke-dashoffset: 2000;
        font-family: 'Cormorant Garamond', Georgia, serif;
        font-weight: 600;
        letter-spacing: 0.30em;
        text-transform: uppercase;
        font-size: 80px; /* slightly smaller to fit letter spacing */
      }

      /* Progress Bar */
      #clc-progress-wrap {
        position: relative;
        z-index: 10;
        margin-top: 32px;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;
      }

      #clc-progress-pct {
        font-family: 'Cormorant Garamond', Georgia, serif;
        font-size: 28px;
        letter-spacing: 0.18em;
        color: #F5E6D0;
        opacity: 0.7;
      }

      #clc-progress-track {
        width: 180px;
        height: 1px;
        background: rgba(245, 230, 208, 0.18);
        position: relative;
        overflow: hidden;
      }

      #clc-progress-fill {
        position: absolute;
        top: 0; left: 0;
        height: 100%;
        width: 0%;
        background: linear-gradient(90deg, #C9A96E, #F5E6D0);
        transition: none;
      }

      /* Gold rule accent */
      #clc-rule {
        position: relative;
        z-index: 10;
        margin-top: 24px;
        display: flex;
        align-items: center;
        gap: 12px;
        opacity: 0;
      }

      #clc-rule span {
        font-family: 'Cormorant Garamond', Georgia, serif;
        font-size: 11px;
        letter-spacing: 0.38em;
        text-transform: uppercase;
        color: #C9A96E;
      }

      #clc-rule::before, #clc-rule::after {
        content: '';
        width: 60px;
        height: 1px;
        background: linear-gradient(90deg, transparent, #C9A96E);
      }

      #clc-rule::after {
        background: linear-gradient(90deg, #C9A96E, transparent);
      }



      /* Flying logo clone */
      #clc-logo-fly {
        position: fixed;
        z-index: 999998;
        pointer-events: none;
        white-space: nowrap;
        font-family: 'Cormorant Garamond', Georgia, serif;
        font-weight: 600;
        letter-spacing: 0.30em;
        text-transform: uppercase;
        color: #F5E6D0;
        transform-origin: center center;
        will-change: transform, opacity, color;
        display: flex;
        flex-direction: column;
        align-items: center;
        line-height: 1;
        gap: 2px;
      }

      #clc-logo-fly .fly-name {
        font-size: 20px;
        letter-spacing: 0.30em;
        margin-right: -0.30em;
      }
      #clc-logo-fly .fly-sub {
        font-size: 8px;
        letter-spacing: 0.35em;
        color: #C9A96E;
        font-family: 'Inter', sans-serif;
        font-weight: 500;
        text-transform: uppercase;
        margin-right: -0.35em;
      }
      
      @media (max-width: 768px) {
        #clc-logo-fly .fly-name {
          font-size: 13px;
          letter-spacing: 0.16em;
          margin-right: -0.16em;
        }
        #clc-logo-fly .fly-sub {
          font-size: 7px;
          letter-spacing: 0.20em;
          margin-right: -0.20em;
        }
      }
    </style>

    <!-- Floating stationery items -->
    <div id="clc-float-layer" style="position:absolute;inset:0;pointer-events:none;z-index:5;"></div>

    <!-- Logo SVG drawing -->
    <svg id="clc-logo-svg" viewBox="0 0 1200 140" aria-label="Ceylon Letter Co.">
      <text id="clc-logo-text" x="50%" y="70%" text-anchor="middle" dominant-baseline="middle">
        Ceylon Letter Co.
      </text>
    </svg>

    <!-- Gold rule below logo -->
    <div id="clc-rule">
      <span>Fine Jewellery</span>
    </div>

    <!-- Progress -->
    <div id="clc-progress-wrap">
      <div id="clc-progress-pct">0 %</div>
      <div id="clc-progress-track">
        <div id="clc-progress-fill"></div>
      </div>
    </div>


  `;

  document.body.insertBefore(overlay, document.body.firstChild);
  
  // Remove CSS blackout
  document.body.classList.add('loader-ready');

  // Remove initial synchronous blackout div if it exists (for index.html)
  const blackout = document.getElementById('clc-initial-blackout');
  if (blackout) blackout.remove();

  // ── Floating stationery elements ──
  const floatLayer = overlay.querySelector('#clc-float-layer');
  const floatItems = [];
  const ITEM_TYPES = ['diamond', 'ring', 'star'];

  for (let i = 0; i < 16; i++) {
    const type = ITEM_TYPES[Math.floor(Math.random() * ITEM_TYPES.length)];
    const el = document.createElement('div');
    el.className = 'clc-float';

    const depth = Math.random();
    el.style.filter = depth > 0.8 ? 'blur(5px)' : depth < 0.3 ? 'blur(2.5px)' : 'blur(0px)';
    el.style.zIndex = depth > 0.8 ? '9' : depth < 0.3 ? '2' : '6';

    if (type === 'diamond') {
      el.innerHTML = `<div class="clc-diamond"><svg viewBox="0 0 24 24"><path d="M12 2L2 9l10 13L22 9l-10-7z"/><path d="M2 9h20M12 2v20M7 6l5 16M17 6L12 22"/></svg></div>`;
    } else if (type === 'ring') {
      el.innerHTML = `<div class="clc-ring"></div>`;
    } else {
      el.innerHTML = `<div class="clc-star"><svg viewBox="0 0 24 24"><path d="M12 2L14.5 9.5L22 12L14.5 14.5L12 22L9.5 14.5L2 12L9.5 9.5Z"/></svg></div>`;
    }

    floatLayer.appendChild(el);
    floatItems.push(el);
  }

  // ── Animate using requestAnimationFrame (no external dependency) ──
  const START = performance.now();
  const LOAD_DURATION  = 2200; // ms drawing phase
  const SETTLE_DELAY   = 2500; // when explosion starts
  const EXPLODE_DUR    = 800;  // explosion phase
  const CURTAIN_DELAY  = 3000; // curtain sweeps up
  const CURTAIN_DUR    = 700;
  const FLY_DELAY      = 3100; // logo flies to navbar

  function ease(t) {
    // easeInOutCubic
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  function easeOut(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  function lerp(a, b, t) { return a + (b - a) * t; }

  // ── Drawing Phase ──
  const logoText = overlay.querySelector('#clc-logo-text');
  const progressPct = overlay.querySelector('#clc-progress-pct');
  const progressFill = overlay.querySelector('#clc-progress-fill');
  const rule = overlay.querySelector('#clc-rule');

  let drawDone = false;
  let explodeDone = false;
  let curtainDone = false;

  function tick(now) {
    const elapsed = now - START;

    // ── Draw Phase (0 → LOAD_DURATION) ──
    if (elapsed < LOAD_DURATION) {
      const t = ease(Math.min(elapsed / LOAD_DURATION, 1));
      const dashOffset = 2000 * (1 - t);
      logoText.style.strokeDashoffset = dashOffset;
      const pct = Math.floor(t * 100);
      progressPct.textContent = pct + ' %';
      progressFill.style.width = (t * 100) + '%';
    } else if (!drawDone) {
      drawDone = true;
      logoText.style.strokeDashoffset = 0;
      progressPct.textContent = '100 %';
      progressFill.style.width = '100%';
      // Fill logo text solid cream
      logoText.style.fill = '#F5E6D0';
      // Fade in rule
      rule.style.transition = 'opacity 0.5s ease';
      rule.style.opacity = '1';
    }

    // ── Explosion Phase (SETTLE_DELAY → SETTLE_DELAY + EXPLODE_DUR) ──
    if (elapsed >= SETTLE_DELAY && elapsed < SETTLE_DELAY + EXPLODE_DUR) {
      const t = easeOut(Math.min((elapsed - SETTLE_DELAY) / EXPLODE_DUR, 1));
      const W = window.innerWidth;
      const H = window.innerHeight;
      floatItems.forEach((item, i) => {
        const angle = (i / floatItems.length) * Math.PI * 2 + Math.random() * 0.3;
        const radius = (Math.random() * 0.6 + 0.4);
        const tx = Math.cos(angle) * W * radius;
        const ty = Math.sin(angle) * H * radius;
        const rot = (Math.random() - 0.5) * 720 * t;
        const scale = lerp(0, Math.random() * 0.8 + 0.5, t);
        const opacity = t;
        item.style.transform = `translate(calc(-50% + ${tx * t}px), calc(-50% + ${ty * t}px)) rotate(${rot}deg) scale(${scale})`;
        item.style.opacity = opacity;
      });
    } else if (!explodeDone && elapsed >= SETTLE_DELAY + EXPLODE_DUR) {
      explodeDone = true;
    }

    // ── Transition Phase ──
    if (elapsed >= CURTAIN_DELAY && !curtainDone) {
      curtainDone = true;
      startLogoFly();
      return;
    }

    requestAnimationFrame(tick);
  }

  requestAnimationFrame(tick);

  // ── Ambient floating after explosion ──
  setTimeout(() => {
    floatItems.forEach(item => {
      const baseTransform = item.style.transform;
      let phase = Math.random() * Math.PI * 2;
      const ampY = Math.random() * 18 + 10;
      const ampX = Math.random() * 12 + 6;
      const speed = Math.random() * 0.0004 + 0.0003;

      function floatTick(now) {
        if (curtainDone) return;
        phase += speed;
        const dy = Math.sin(phase) * ampY;
        const dx = Math.cos(phase * 0.7) * ampX;
        // Keep existing translation offset but add float
        item.style.transform = baseTransform.replace(')', '') + ` translate(${dx}px, ${dy}px)`;
        requestAnimationFrame(floatTick);
      }
      requestAnimationFrame(floatTick);
    });
  }, SETTLE_DELAY + EXPLODE_DUR + 100);

  // ── Logo Fly Animation ──
  function startLogoFly() {
    // Create the flying logo element
    const fly = document.createElement('div');
    fly.id = 'clc-logo-fly';
    fly.innerHTML = `<span class="fly-name">Ceylon Letter Co.</span><span class="fly-sub">Fine Jewellery</span>`;
    document.body.appendChild(fly);

    // Position: center of screen initially
    const W = window.innerWidth;
    const H = window.innerHeight;
    fly.style.position = 'fixed';
    fly.style.left = '50%';
    fly.style.top = '50%';
    fly.style.transform = 'translate(-50%, -50%) scale(3)';
    fly.style.color = '#F5E6D0';
    fly.style.opacity = '1';
    fly.style.zIndex = '999998';
    fly.style.transition = 'none';

    // Hide the loader overlay
    overlay.style.opacity = '0';
    overlay.style.pointerEvents = 'none';
    overlay.style.transition = 'opacity 0.8s ease';

    // Poll until .nav-logo is in the DOM (navbar is injected async)
    let attempts = 0;
    function tryFly() {
      const navLogo = document.querySelector('.nav-logo');
      if (!navLogo && attempts < 30) {
        attempts++;
        setTimeout(tryFly, 100);
        return;
      }

      if (!navLogo) {
        // Give up — just fade the fly element out
        fly.style.transition = 'opacity 0.5s ease';
        fly.style.opacity = '0';
        setTimeout(() => fly.remove(), 600);
        return;
      }

      const rect = navLogo.getBoundingClientRect();
      const targetX = rect.left + rect.width / 2;
      const targetY = rect.top + rect.height / 2;

      // Hide real navbar logo during fly
      navLogo.style.opacity = '0';
      navLogo.style.transition = 'opacity 0.4s ease';

      // Fly from center to navbar logo position
      animateFlyTo(fly, W * 0.5, H * 0.5, targetX, targetY, 3, 1, '#2A2622', () => {
        navLogo.style.opacity = '1';
        fly.style.transition = 'opacity 0.3s ease';
        fly.style.opacity = '0';
        setTimeout(() => {
          fly.remove();
          overlay.remove();
        }, 400);
      });
    }

    // Give a brief moment before polling (navbar may already be there)
    setTimeout(tryFly, 50);
  }


  function animateFlyTo(el, startX, startY, endX, endY, startScale, endScale, endColor, onDone) {
    const DURATION = 900;
    const start = performance.now();

    function easeInOut(t) {
      return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    function colorLerp(fromHex, toHex, t) {
      const from = hexToRgb(fromHex);
      const to = hexToRgb(toHex);
      if (!from || !to) return toHex;
      const r = Math.round(lerp(from.r, to.r, t));
      const g = Math.round(lerp(from.g, to.g, t));
      const b = Math.round(lerp(from.b, to.b, t));
      return `rgb(${r},${g},${b})`;
    }

    function hexToRgb(hex) {
      const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
      return result ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
      } : null;
    }

    function step(now) {
      const elapsed = now - start;
      const rawT = Math.min(elapsed / DURATION, 1);
      const t = easeInOut(rawT);

      // Recalculate target to handle any layout shifts during flight
      let currentEndX = endX;
      let currentEndY = endY;
      const navLogo = document.querySelector('.nav-logo');
      if (navLogo) {
        const rect = navLogo.getBoundingClientRect();
        currentEndX = rect.left + rect.width / 2;
        currentEndY = rect.top + rect.height / 2;
      }

      const x = lerp(startX, currentEndX, t);
      const y = lerp(startY, currentEndY, t);
      const scale = lerp(startScale, endScale, t);
      const color = colorLerp('#F5E6D0', endColor, t);

      el.style.left = x + 'px';
      el.style.top = y + 'px';
      el.style.transform = `translate(-50%, -50%) scale(${scale})`;
      el.style.color = color;

      if (rawT < 1) {
        requestAnimationFrame(step);
      } else {
        if (onDone) onDone();
      }
    }

    requestAnimationFrame(step);
  }

})();
