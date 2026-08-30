
(function () {
  'use strict';

  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ── Number ticker ([data-tick], magicui NumberTicker) ────────────────── */
  var tickerIO = null;
  function tickerObserver() {
    if (!tickerIO && 'IntersectionObserver' in window && !reduce) {
      tickerIO = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) {
          if (e.isIntersecting) { runTicker(e.target); tickerIO.unobserve(e.target); }
        });
      }, { rootMargin: '0px 0px -8% 0px' });
    }
    return tickerIO;
  }

  function runTicker(el) {
    var target = parseInt(el.getAttribute('data-tick'), 10);
    if (isNaN(target)) return;
    if (reduce || target === 0) { el.textContent = String(target); return; }

    var prefix = el.getAttribute('data-tick-prefix') || '';
    var suffix = el.getAttribute('data-tick-suffix') || '';
    var dur = parseInt(el.getAttribute('data-tick-dur') || '900', 10);
    var start = null;

    function frame(ts) {
      if (!start) start = ts;
      var t = Math.min((ts - start) / dur, 1);
      var eased = 1 - Math.pow(2, -10 * t); // easeOutExpo
      el.textContent = prefix + Math.round(target * (t === 1 ? 1 : eased)) + suffix;
      if (t < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  /* ── Scroll reveal (.reveal -> .is-in, staggered via --d) ─────────────── */
  var revealIO = null;
  function revealObserver() {
    if (!revealIO) {
      revealIO = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) {
          if (e.isIntersecting) {
            e.target.classList.add('is-in');
            revealIO.unobserve(e.target);
          }
        });
      }, { threshold: 0.06, rootMargin: '0px 0px -4% 0px' });
    }
    return revealIO;
  }
  function scanReveals(root) {
    var els = (root || document).querySelectorAll('.reveal:not(.is-in)');
    els.forEach(function (el) {
      if (reduce) el.classList.add('is-in');
      else revealObserver().observe(el);
    });
  }

  /* ── Tilt (reactbits TiltCard) - delegation, per-card hover tracking ──── */
  var tiltEl = null;
  function tiltLoop() {
    if (!tiltEl) return;
    var r = tiltEl.getBoundingClientRect();
    var max = 6;
    var px = (tiltEl.__px - r.left) / r.width - 0.5;
    var py = (tiltEl.__py - r.top) / r.height - 0.5;
    tiltEl.style.setProperty('--ry', (px * max * 2).toFixed(2) + 'deg');
    tiltEl.style.setProperty('--rx', (-py * max * 2).toFixed(2) + 'deg');
  }
  document.addEventListener('pointermove', function (ev) {
    var el = ev.target.closest ? ev.target.closest('.tilt') : null;
    if (!el) return;
    el.__px = ev.clientX; el.__py = ev.clientY;
    if (tiltEl !== el) { tiltEl = el; tiltLoop(); }
  }, { passive: true });
  document.addEventListener('pointerout', function (ev) {
    var el = ev.target.closest ? ev.target.closest('.tilt') : null;
    if (el && tiltEl === el) {
      tiltEl = null;
      el.style.setProperty('--rx', '0deg');
      el.style.setProperty('--ry', '0deg');
    }
  }, { passive: true });

  /* ── Magnetic buttons (reactbits Magnet) - direct bind, few in DOM ────── */
  var MAGNETIC = new WeakSet();
  function scanMagnetic(root) {
    (root || document).querySelectorAll('.magnetic').forEach(function (el) {
      if (MAGNETIC.has(el)) return;
      MAGNETIC.add(el);
      var strength = 6, raf = null;
      function move(e) {
        if (raf) return;
        raf = requestAnimationFrame(function () {
          raf = null;
          var r = el.getBoundingClientRect();
          var dx = e.clientX - (r.left + r.width / 2);
          var dy = e.clientY - (r.top + r.height / 2);
          el.style.transform = 'translate(' + (dx / r.width) * strength + 'px,' + (dy / r.height) * strength + 'px)';
        });
      }
      function reset() {
        el.style.transform = 'translate(0,0)';
      }
      el.addEventListener('pointermove', move, { passive: true });
      el.addEventListener('pointerleave', reset, { passive: true });
    });
  }

  /* ── Segmented nav thumb (reactbits Glass Pill Nav) ───────────────────── */
  function refreshThumbs(root) {
    (root || document).querySelectorAll('.segnav').forEach(function (nav) {
      var thumb = nav.querySelector('.seg-thumb');
      var active = nav.querySelector('.tab-btn.active');
      if (!thumb || !active) { thumb && thumb.style.opacity && (thumb.style.opacity = '0'); return; }
      thumb.style.opacity = '1';
      var navRect = nav.getBoundingClientRect();
      thumb.style.left = (active.offsetLeft - nav.scrollLeft) + 'px';
      thumb.style.width = active.offsetWidth + 'px';
    });
  }

  /* ── Observe dynamic content (app.js re-renders via innerHTML) ────────── */
  var pending = false;
  function rescan() {
    pending = false;
    scanReveals(document);
    scanMagnetic(document);
    refreshThumbs(document);
    var io = tickerObserver();
    if (io) {
      document.querySelectorAll('[data-tick]').forEach(function (el) {
        if (!el.getAttribute('data-ticked')) {
          el.setAttribute('data-ticked', '1');
          io.observe(el);
        }
      });
    }
  }

  function watch() {
    var mo = new MutationObserver(function () {
      if (!pending) { pending = true; requestAnimationFrame(rescan); }
    });
    mo.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'style'] });
  }

  /* ── TextType (reactbits) - vanilla port for the footer ────────────────── */
  function textType(el, opts) {
    if (!el) return null;
    opts = opts || {};
    var text = Array.isArray(opts.text) ? opts.text.slice() : [opts.text].filter(Boolean);
    if (!text.length) return null;

    var typingSpeed = opts.typingSpeed || 55;
    var deletingSpeed = opts.deletingSpeed || 30;
    var pauseDuration = opts.pauseDuration || 1500;
    var initialDelay = opts.initialDelay || 400;
    var loop = opts.loop !== false;
    var variableSpeed = opts.variableSpeed || null;

    // Cursor assumed to be the sibling `.type-caret` span (see base.html footer).
    var caret = el.nextElementSibling && el.nextElementSibling.classList.contains('type-caret')
      ? el.nextElementSibling : null;
    if (caret && opts.showCursor === false) caret.style.display = 'none';

    var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce) {
      el.textContent = text[0];
      if (caret) caret.style.display = 'none';
      return { stop: function () { }, done: function () { el.textContent = text[0]; } };
    }

    function speed() {
      if (!variableSpeed) return typingSpeed;
      return Math.random() * (variableSpeed.max - variableSpeed.min) + variableSpeed.min;
    }

    var idx = 0, charIdx = 0, deleting = false, timer = null;

    function done() { el.textContent = text[idx]; }

    function tick() {
      var current = text[idx];
      if (deleting) {
        if (charIdx === 0) {
          deleting = false;
          if (opts.onSentenceComplete) opts.onSentenceComplete(current, idx);
          idx = (idx + 1) % text.length;
          if (idx === 0 && !loop) return done();
          timer = setTimeout(tick, pauseDuration);
        } else {
          charIdx--;
          el.textContent = current.slice(0, charIdx);
          timer = setTimeout(tick, deletingSpeed);
        }
      } else {
        if (charIdx < current.length) {
          charIdx++;
          el.textContent = current.slice(0, charIdx);
          timer = setTimeout(tick, speed());
        } else {
          deleting = true;
          if (idx === text.length - 1 && !loop) return done();
          timer = setTimeout(tick, pauseDuration);
        }
      }
    }

    timer = setTimeout(tick, initialDelay);
    return { stop: function () { clearTimeout(timer); }, done: done };
  }

  /* ── init ─────────────────────────────────────────────────────────────── */
  function init() {
    rescan();
    if (!reduce) watch();
    window.addEventListener('resize', function () { refreshThumbs(document); }, { passive: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.DSSEffects = { rescan: rescan, refreshThumbs: refreshThumbs, textType: textType };
})();