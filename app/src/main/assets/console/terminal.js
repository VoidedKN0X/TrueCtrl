// xterm.js talking to the TrueNAS web shell. The app opens the socket and signs in (the first
// message is a token); once TrueNAS says "connected" the channel opens and carries raw terminal
// bytes both ways. Typing goes out as binary frames; the size is set by the app (core.resize_shell).
(function () {
  'use strict';
  const sock = window.TrueChannel;
  const term = new Terminal({
    cursorBlink: true,
    fontSize: Number(new URLSearchParams(location.search).get('font')) || 13,
    fontFamily: 'monospace',
    scrollback: 5000,
    allowProposedApi: true,
    theme: { background: '#08080a', foreground: '#f4f4f2', cursor: '#b48cff', selectionBackground: '#3d2f5c' },
  });
  const fit = new FitAddon.FitAddon();
  term.loadAddon(fit);
  term.open(document.getElementById('term'));
  fit.fit();

  let connected = false;
  const mods = { ctrl: false, alt: false };
  const encoder = new TextEncoder();

  function send(data) {
    if (!connected) return;
    sock.send(encoder.encode(data));
  }
  function sendSize() {
    if (connected) TrueBridge.resize(term.cols, term.rows);
  }
  function applyMods(data) {
    if (mods.ctrl && data.length === 1) {
      const c = data.toUpperCase().charCodeAt(0);
      if (c >= 64 && c <= 95) data = String.fromCharCode(c - 64);
      else if (data === ' ') data = '\x00';
    }
    if (mods.alt) data = '\x1b' + data;
    if (mods.ctrl || mods.alt) {
      mods.ctrl = mods.alt = false;
      TrueBridge.modsReleased();
    }
    return data;
  }

  sock.onopen = function () {
    connected = true;
    term.reset();
    requestAnimationFrame(() => { fit.fit(); sendSize(); term.focus(); });
    TrueBridge.connected();
  };
  sock.onmessage = function (ev) { term.write(new Uint8Array(ev.data)); };
  sock.onclose = function () { connected = false; };

  term.onData(function (data) { send(applyMods(data)); });
  term.onResize(sendSize);
  let resizeTimer;
  function refit() {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () { fit.fit(); }, 100);
  }
  window.addEventListener('resize', refit);
  if (window.ResizeObserver) new ResizeObserver(refit).observe(document.getElementById('term'));

  // Called by the app's extra-keys bar and keyboard field.
  window.pcSetMods = function (ctrl, alt) { mods.ctrl = ctrl; mods.alt = alt; };
  window.pcKey = function (seq) { send(applyMods(seq)); };
  window.pcInput = function (text) {
    text = text.replace(/\r?\n/g, '\r');
    if (!text) return;
    if (mods.ctrl || mods.alt) {
      const first = String.fromCodePoint(text.codePointAt(0));
      send(applyMods(first));
      text = text.slice(first.length);
    }
    if (text) send(text);
  };
  window.pcBackspace = function (n) { send('\x7f'.repeat(n)); };
  window.pcPaste = function (text) {
    text = text.replace(/\r?\n/g, '\r');
    send(term.modes.bracketedPasteMode ? '\x1b[200~' + text + '\x1b[201~' : text);
  };
  window.pcFontSize = function (size) { term.options.fontSize = size; fit.fit(); };

  TrueBridge.pageReady();
})();
