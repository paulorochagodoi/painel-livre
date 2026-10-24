/* @ds-bundle: {"format":4,"namespace":"Aura","components":[{"name":"Launcher"},{"name":"VehicleStatus"},{"name":"ShortcutTiles"},{"name":"Dock"},{"name":"StatusBar"},{"name":"ClimatePanel"},{"name":"MediaPanel"},{"name":"NavPanel"},{"name":"NavSearch"},{"name":"BatteryPanel"},{"name":"Icons"}]} */
(function () {
  'use strict';
  var A = {};

  /* ================= Icons (24px grid, 1.8 stroke) ================= */
  var P = {
    home: 'M3 11.5 12 4l9 7.5M5.5 10v9.5h13V10M10 19.5v-5h4v5',
    nav: 'M12 3 19.5 20 12 16 4.5 20Z',
    battery: 'M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2ZM22 10.5v3M11 9.2 8.6 12.4h3L9.4 15',
    fan: 'M12 12c0-4 1-8 4-8 2.5 0 3 3 0 5l-4 3Zm0 0c4 0 8 1 8 4 0 2.5-3 3-5 0l-3-4Zm0 0c0 4-1 8-4 8-2.5 0-3-3 0-5l4-3Zm0 0c-4 0-8-1-8-4 0-2.5 3-3 5 0l3 4Z',
    music: 'M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm11-2a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
    play: 'M8 5.2v13.6L19 12Z',
    pause: 'M7 5h3.4v14H7zM13.6 5H17v14h-3.4z',
    prev: 'M5 5h2.6v14H5zM19.5 5.4v13.2L9 12Z',
    next: 'M16.4 5H19v14h-2.6zM4.5 5.4v13.2L15 12Z',
    search: 'M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Zm5.5-2 5 5',
    work: 'M4 8h16v11H4zM9 8V5.5h6V8M4 13h16',
    pin: 'M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11Zm0-8.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
    clock: 'M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18Zm0-13v4.5l3 2',
    star: 'M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 17l-5.2 2.7 1-5.9-4.3-4.1 5.9-.8Z',
    plug: 'M9 3v5M15 3v5M6.5 8h11v3a5.5 5.5 0 0 1-11 0ZM12 16.5V21',
    bolt: 'M13 3 5 13.5h6L10 21l8-10.5h-6Z',
    fuel: 'M5 20V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v15M4 20h11M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3M7.5 8h4',
    leaf: 'M5 19c0-8 5-14 15-14 0 10-6 15-14 15M5 19l7-7',
    gauge: 'M4 17a8 8 0 1 1 16 0M12 17l4-5',
    seatHeat: 'M7.5 4h3a1 1 0 0 1 1 1.1L10.6 13H16a1 1 0 0 1 1 1v2.5H7.6A1.6 1.6 0 0 1 6 14.9L6.5 5a1 1 0 0 1 1-1ZM7 20h10M15 3c-.8.9.8 1.8 0 2.7s.8 1.8 0 2.7M18.5 3c-.8.9.8 1.8 0 2.7s.8 1.8 0 2.7',
    seatVent: 'M7.5 4h3a1 1 0 0 1 1 1.1L10.6 13H16a1 1 0 0 1 1 1v2.5H7.6A1.6 1.6 0 0 1 6 14.9L6.5 5a1 1 0 0 1 1-1ZM7 20h10M17 2.5v6M14.4 4l5.2 3M19.6 4l-5.2 3',
    snow: 'M12 3v18M4.2 7.5l15.6 9M19.8 7.5l-15.6 9M9.5 4.5 12 6l2.5-1.5M9.5 19.5 12 18l2.5 1.5',
    recirc: 'M3.5 16.5 5 11.5A2 2 0 0 1 6.9 10h10.2a2 2 0 0 1 1.9 1.5l1.5 5M3.5 16.5h17M9 6.5a4 4 0 0 1 6.5 0M15.5 3.8v2.7h-2.7',
    defF: 'M3 7c6-3 12-3 18 0l-2 9H5ZM9 10c-.8.9.8 1.8 0 2.7M12 10c-.8.9.8 1.8 0 2.7M15 10c-.8.9.8 1.8 0 2.7M10 20h4',
    defR: 'M4 6h16v12H4zM9 9c-.8.9.8 1.8 0 2.7s.8 1.8 0 2.7M12 9c-.8.9.8 1.8 0 2.7s.8 1.8 0 2.7M15 9c-.8.9.8 1.8 0 2.7s.8 1.8 0 2.7',
    auto: 'M6 18 12 5l6 13M8.3 13h7.4',
    sync: 'M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3M18 3v4h-4M6 21v-4h4',
    power: 'M12 3v8M6.3 6.6a8 8 0 1 0 11.4 0',
    minus: 'M5 12h14',
    plus: 'M12 5v14M5 12h14',
    chevL: 'M14.5 5.5 8 12l6.5 6.5',
    chevR: 'M9.5 5.5 16 12l-6.5 6.5',
    chevD: 'M5.5 9.5 12 16l6.5-6.5',
    back: 'M19 12H5M11 5.5 4.5 12l6.5 6.5',
    close: 'M6 6l12 12M18 6 6 18',
    mic: 'M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3Zm-6-3a6 6 0 0 0 12 0M12 18v3',
    bt: 'M7 7l10 10-5 4V3l5 4L7 17',
    wifi: 'M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.7 16a5 5 0 0 1 6.6 0M12 19.5h.01',
    signal: 'M4 19v-3M9 19v-6M14 19v-9M19 19V5',
    sun: 'M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9ZM12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
    moon: 'M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z',
    cloud: 'M7 18a4 4 0 0 1-.6-8A6 6 0 0 1 18 9.5a4.3 4.3 0 0 1-.5 8.5Z',
    phone: 'M5 4h3.5l1.5 4-2 1.5a11 11 0 0 0 6.5 6.5l1.5-2 4 1.5V19a1 1 0 0 1-1 1A16 16 0 0 1 4 5a1 1 0 0 1 1-1Z',
    apps: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
    settings: 'M4 7h10M18 7h2M4 17h4M12 17h8M16 5v4M10 15v4',
    lock: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3M12 14.5v2',
    unlock: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 6.8-1.2M12 14.5v2',
    door: 'M5 20V10l6-6h8v16ZM11 4v6H5M14 13h2',
    window: 'M5 19V11l5-6h9v14ZM5 11h14',
    hood: 'M4 15l1.5-5A2 2 0 0 1 7.4 8.5h9.2a2 2 0 0 1 1.9 1.5L20 15v4H4ZM4 15h16M8 6l4-3 4 3',
    trunk: 'M4 15l1.5-5A2 2 0 0 1 7.4 8.5h9.2a2 2 0 0 1 1.9 1.5L20 15v4H4ZM4 15h16M9 17h6',
    check: 'M5 12.5 10 17.5 19.5 7',
    alert: 'M12 4 2.5 20h19ZM12 10v4.5M12 17.5h.01',
    volume: 'M4 9.5h3.5L12 6v12l-4.5-3.5H4ZM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11',
    radio: 'M4 9h16v11H4zM7 9l11-5M8 14.5a1.5 1.5 0 1 0 0 .01M13 13h4M13 16h4',
    usb: 'M12 3v14M12 21a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM12 13l-4-2.5V8M12 15l4-2.5V9M7 6h2v2H7zM15 7l1-2 1 2Z',
    parking: 'M5 4h14v16H5zM10 16V8h3a2.5 2.5 0 0 1 0 5h-3',
    food: 'M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M16 21V3c-2 1.5-3 4-3 8h3',
    coffee: 'M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5ZM16 10.5h1.5a2.5 2.5 0 0 1 0 5H16M8 3.5c-.7.7.7 1.5 0 2.5M11 3.5c-.7.7.7 1.5 0 2.5',
    hospital: 'M5 4h14v16H5zM12 8v8M8 12h8',
    turn: 'M6 20v-7a4 4 0 0 1 4-4h8M14 5l4 4-4 4',
    route: 'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm12-10a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM6 15V9a3 3 0 0 1 3-3h4M18 9v6a3 3 0 0 1-3 3h-4',
    v2l: 'M12 3v4M8 7h8v4a4 4 0 0 1-8 0ZM12 15v6M15.5 18H21M19 16l2 2-2 2',
    calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
    layers: 'M12 4 3 9l9 5 9-5ZM3 14l9 5 9-5',
    locate: 'M12 19a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM12 2v3M12 19v3M2 12h3M19 12h3M12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z'
  };
  var FILLED = { play: 1, pause: 1, prev: 1, next: 1 };
  function icon(n, cls) {
    return '<svg class="au-ico' + (FILLED[n] ? ' f' : '') + (cls ? ' ' + cls : '') + '" viewBox="0 0 24 24" aria-hidden="true"><path d="' + (P[n] || '') + '"/></svg>';
  }
  A.icon = icon;
  A.iconNames = Object.keys(P);

  /* ================= Helpers ================= */
  function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function dec(n, d) { return Number(n).toFixed(d == null ? 1 : d).replace('.', ','); }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function norm(s) { return String(s).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, ''); }
  function delegate(el, type, fn) {
    el.addEventListener(type, function (e) {
      var b = e.target.closest('[data-act]');
      if (!b || !el.contains(b)) return;
      fn(b.getAttribute('data-act'), b, e);
    });
  }
  A.fmt = { dec: dec };

  /* ================= Theme ================= */
  A.getTheme = function () { return document.documentElement.getAttribute('data-theme') || 'light'; };
  A.setTheme = function (t) { document.documentElement.setAttribute('data-theme', t); };

  /* ================= Store ================= */
  A.defaults = function () {
    return {
      model: 'Song Plus DM-i',
      outside: 24,
      ui: { open: null, search: false, searchSel: null, searchCat: null },
      vehicle: { locked: true, doors: { fl: false, fr: false, rl: false, rr: false }, windows: { fl: 0, fr: 0, rl: 40, rr: 0 }, hood: false, trunk: false, port: 'charging', portSide: 'rear-right' },
      climate: { on: true, auto: true, ac: true, recirc: false, defF: false, defR: false, sync: false, tL: 22, tR: 23.5, fan: 3, seats: { d: { heat: 0, vent: 2 }, p: { heat: 1, vent: 0 } } },
      media: { source: 'bt', playing: true, idx: 0, pos: 0.38, vol: 14 },
      battery: { soc: 62, fuelKm: 604, mode: 'ev', drive: 'normal', hold: true, holdTarget: 40, limit: 90, sched: false, v2l: false, v2lMin: 30 },
      nav: { active: null }
    };
  };
  A.createStore = function (init) {
    var st = init || A.defaults(), subs = [];
    return {
      get: function () { return st; },
      update: function (fn) { fn(st); subs.slice().forEach(function (f) { f(st); }); },
      sub: function (f) { subs.push(f); return function () { var i = subs.indexOf(f); if (i >= 0) subs.splice(i, 1); }; }
    };
  };
  function mount(el, store, cls, tpl, handle) {
    store = store || A.createStore();
    function render() { el.innerHTML = tpl(store.get()); }
    if (cls) el.classList.add.apply(el.classList, cls.split(' '));
    var unsub = store.sub(render); render();
    if (handle) delegate(el, 'click', function (act, b, e) { handle(act, b, store, e); });
    return { store: store, render: render, destroy: unsub };
  }

  /* ================= Data ================= */
  var MEDIA = {
    fm: { label: 'Rádio', icon: 'radio', items: [{ t: 'FM 97,7', a: 'Rádio Horizonte' }, { t: 'FM 102,1', a: 'Estação Central' }, { t: 'FM 89,1', a: 'Rock & Estrada' }] },
    bt: { label: 'Bluetooth', icon: 'bt', items: [{ t: 'Noite em Movimento', a: 'Lumen Trio', d: 214 }, { t: 'Linha do Horizonte', a: 'Casa Azul', d: 187 }, { t: 'Faróis Acesos', a: 'Marina Sol', d: 241 }] },
    usb: { label: 'USB', icon: 'usb', items: [{ t: 'Estrada Aberta', a: 'Pendrive · Viagem', d: 201 }, { t: 'Céu de Agosto', a: 'Pendrive · Viagem', d: 233 }] }
  };
  A.media = MEDIA;
  var ME = { x: 420, y: 420 };
  var CATS = [
    { id: 'recarga', n: 'Recarga', i: 'plug' }, { id: 'combustivel', n: 'Combustível', i: 'fuel' }, { id: 'estac', n: 'Estacionamento', i: 'parking' },
    { id: 'rest', n: 'Restaurantes', i: 'food' }, { id: 'cafe', n: 'Cafés', i: 'coffee' }, { id: 'saude', n: 'Hospitais', i: 'hospital' }
  ];
  var POIS = [
    { id: 'home', n: 'Casa', a: 'Rua das Acácias, 210', c: 'fav', i: 'home', x: 630, y: 210 },
    { id: 'work', n: 'Trabalho', a: 'Av. Central, 1480 · 9º andar', c: 'fav', i: 'work', x: 140, y: 140 },
    { id: 'e1', n: 'Eletroposto Shopping Norte', a: 'Piso G2 · 4 vagas AC 7 kW', c: 'recarga', i: 'plug', x: 560, y: 140 },
    { id: 'e2', n: 'Carregador rápido Posto Avenida', a: 'DC 60 kW · 2 vagas', c: 'recarga', i: 'plug', x: 280, y: 350 },
    { id: 'e3', n: 'Eletroposto Praça Central', a: 'AC 22 kW · 2 vagas livres', c: 'recarga', i: 'plug', x: 700, y: 420 },
    { id: 'f1', n: 'Posto Avenida', a: 'Av. das Indústrias, 900', c: 'combustivel', i: 'fuel', x: 280, y: 350 },
    { id: 'f2', n: 'Posto Bandeira', a: 'Rua do Comércio, 45', c: 'combustivel', i: 'fuel', x: 490, y: 280 },
    { id: 'p1', n: 'Estacionamento Centro', a: 'Rua XV, 330 · 24h', c: 'estac', i: 'parking', x: 350, y: 210 },
    { id: 'r1', n: 'Cantina Bella Rua', a: 'Massas · aberto até 23h', c: 'rest', i: 'food', x: 210, y: 420 },
    { id: 'r2', n: 'Sabor da Vila', a: 'Brasileira · aberto até 22h', c: 'rest', i: 'food', x: 630, y: 350 },
    { id: 'c1', n: 'Café da Esquina', a: 'Rua das Flores, 12', c: 'cafe', i: 'coffee', x: 420, y: 280 },
    { id: 'h1', n: 'Hospital Municipal', a: 'Pronto-socorro 24h', c: 'saude', i: 'hospital', x: 770, y: 140 }
  ];
  POIS.forEach(function (p) {
    var dpx = Math.abs(p.x - ME.x) + Math.abs(p.y - ME.y);
    p.km = Math.max(0.4, dpx / 70 * 0.9); p.min = Math.max(2, Math.round(p.km * 2.6));
  });
  function poi(id) { for (var i = 0; i < POIS.length; i++) if (POIS[i].id === id) return POIS[i]; return null; }
  A.pois = POIS; A.categories = CATS;
  function arrival(min) { var d = new Date(Date.now() + min * 60000); return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }); }

  /* ================= Vehicle helpers ================= */
  var DOOR_N = { fl: 'Dianteira esq.', fr: 'Dianteira dir.', rl: 'Traseira esq.', rr: 'Traseira dir.' };
  function vSummary(v) {
    var doors = Object.keys(v.doors).filter(function (k) { return v.doors[k]; });
    var wins = Object.keys(v.windows).filter(function (k) { return v.windows[k] > 0; });
    var open = doors.length + wins.length + (v.hood ? 1 : 0) + (v.trunk ? 1 : 0) + (v.port === 'open' ? 1 : 0);
    return { doors: doors, wins: wins, open: open };
  }
  A.vehicleSummary = vSummary;

  /* ================= Car (top view, front to the right) ================= */
  // viewBox -30 -30 580 307 · car body x 46–476, y 46–194 · left side of the car (driver) is the TOP edge
  A.carSVG = function (v, opts) {
    opts = opts || {};
    var d = v.doors, w = v.windows;
    function door(id, x1, x2, top) {
      var o = d[id], y = top ? (o ? 44 : 46) : (o ? 186 : 187), hh = o ? 10 : 7, hy = top ? 49.5 : 190.5, ang = o ? (top ? 38 : -38) : 0;
      return '<rect class="door' + (d[id] ? ' open' : '') + '" x="' + x1 + '" y="' + y + '" width="' + (x2 - x1) + '" height="' + hh + '" rx="4" style="transform-origin:' + x2 + 'px ' + hy + 'px;transform:rotate(' + ang + 'deg)"/>';
    }
    function win(id, x1, x2, top) {
      var y = top ? 57 : 176, len = x2 - x1, p = (w[id] || 0) / 100, gl = len * (1 - p);
      return (p > 0 ? '<rect class="win-gap" x="' + x1 + '" y="' + y + '" width="' + len + '" height="7" rx="3"/>' : '') +
        (gl > 0.5 ? '<rect class="win-glass" x="' + x1 + '" y="' + y + '" width="' + gl.toFixed(1) + '" height="7" rx="3"/>' : '');
    }
    function hit(act, x, y, wd, h) { return opts.interactive ? '<rect class="hit" data-act="' + act + '" x="' + x + '" y="' + y + '" width="' + wd + '" height="' + h + '"/>' : ''; }
    var port = v.port, py = v.portSide === 'rear-left' ? 42 : 188;
    var s = '<svg class="car" viewBox="-30 -30 580 307" role="img" aria-label="Estado do veículo visto de cima">' +
      '<defs><radialGradient id="au-plat" cx="50%" cy="50%" r="50%"><stop offset="0" class="glowstop"/><stop offset="1" class="glowstop" stop-opacity="0"/></radialGradient></defs>' +
      '<ellipse class="plat" cx="261" cy="122" rx="300" ry="150"/>' +
      '<ellipse class="platring" cx="261" cy="122" rx="270" ry="122"/>' +
      '<rect class="tire" x="98" y="38" width="52" height="12" rx="5"/><rect class="tire" x="370" y="38" width="52" height="12" rx="5"/>' +
      '<rect class="tire" x="98" y="190" width="52" height="12" rx="5"/><rect class="tire" x="370" y="190" width="52" height="12" rx="5"/>' +
      '<ellipse class="body" cx="350" cy="40" rx="11" ry="6"/><ellipse class="body" cx="350" cy="200" rx="11" ry="6"/>' +
      '<path class="body" d="M80 46C58 46 46 62 46 88V152C46 178 58 194 80 194H404C448 194 474 176 476 150V90C474 64 448 46 404 46Z"/>' +
      '<path class="panel' + (v.trunk ? ' open' : '') + '" d="M114 62Q72 56 56 78Q50 120 56 162Q72 184 114 178Q106 120 114 62Z"/>' +
      '<path class="panel' + (v.hood ? ' open' : '') + '" d="M388 60Q410 50 440 56Q470 66 472 120Q470 174 440 184Q410 190 388 180Q398 120 388 60Z"/>' +
      '<path class="glass" d="M160 70 122 80Q114 120 122 160L160 170Q154 120 160 70Z"/>' +
      '<rect class="glass" x="166" y="72" width="168" height="96" rx="16"/>' +
      '<path class="glass" d="M340 68 382 80Q392 120 382 160L340 172Q346 120 340 68Z"/>' +
      '<path class="seam" d="M162 46V68M246 46V68M338 46V66M162 194V172M246 194V172M338 194V174"/>' +
      win('rl', 166, 240, true) + win('fl', 252, 332, true) + win('rr', 166, 240, false) + win('fr', 252, 332, false) +
      '<path class="drl" d="M448 58Q468 70 472 92M448 182Q468 170 472 148"/>' +
      '<path class="tail" d="M54 70Q48 84 47 100M54 170Q48 156 47 140"/>' +
      (port === 'charging' ? '<path class="cable" d="M97 ' + (py + 10) + 'C97 228 104 240 100 262"/>' : '') +
      '<rect class="port ' + port + '" x="86" y="' + py + '" width="22" height="12" rx="4"/>' +
      (port === 'charging' ? '<path class="portbolt" d="M98.5 ' + (py + 2) + 'l-4 5h3l-1 4 4-5h-3Z"/>' : '') +
      door('rl', 162, 244, true) + door('fl', 248, 336, true) + door('rr', 162, 244, false) + door('fr', 248, 336, false) +
      hit('door:rl', 162, 22, 82, 46) + hit('door:fl', 248, 22, 88, 46) + hit('door:rr', 162, 172, 82, 46) + hit('door:fr', 248, 172, 88, 46) +
      hit('trunk', 46, 70, 70, 100) + hit('hood', 388, 70, 88, 100) + hit('port', 76, py - 10, 42, 32) +
      '</svg>';
    return s;
  };

  function chip(x, y, cls, ic, k, val, act) {
    var px = ((x + 30) / 580 * 100).toFixed(2), py = ((y + 30) / 307 * 100).toFixed(2);
    return '<button class="au-chip ' + (cls || '') + '" style="left:' + px + '%;top:' + py + '%"' + (act ? ' data-act="' + act + '"' : ' tabindex="-1"') + '>' +
      '<span class="ic">' + icon(ic) + '</span><span><span class="k">' + k + '</span><span class="v" style="display:block">' + val + '</span></span></button>';
  }
  function vehicleTpl(st, opts) {
    var v = st.vehicle, sm = vSummary(v), b = st.battery;
    var dv = sm.doors.length === 0 ? 'Fechadas' : sm.doors.length === 1 ? DOOR_N[sm.doors[0]] + ' aberta' : sm.doors.length + ' abertas';
    var wv = sm.wins.length === 0 ? 'Fechados' : sm.wins.length === 1 ? DOOR_N[sm.wins[0]].replace('Dianteira', 'Diant.').replace('Traseira', 'Tras.') + ' ' + v.windows[sm.wins[0]] + '%' : sm.wins.length + ' abertos';
    var pv = v.port === 'charging' ? 'Carregando · ' + b.soc + '%' : v.port === 'open' ? 'Tampa aberta' : 'Tampa fechada';
    return A.carSVG(v, opts) +
      chip(420, -14, sm.doors.length ? 'warn' : '', 'door', 'Portas', dv) +
      chip(420, 258, sm.wins.length ? 'warn' : '', 'window', 'Vidros', wv + (sm.wins.length ? ' · Fechar' : ''), sm.wins.length && opts.interactive ? 'closewins' : '') +
      chip(548, 120, v.hood ? 'warn' : '', 'hood', 'Capô', v.hood ? 'Aberto' : 'Fechado') +
      chip(-26, 120, v.trunk ? 'warn' : '', 'trunk', 'Porta-malas', v.trunk ? 'Aberto · Fechar' : 'Fechado', opts.interactive ? 'trunk' : '') +
      chip(100, 266, v.port === 'charging' ? 'acc' : v.port === 'open' ? 'warn' : '', 'plug', 'Carregamento', pv);
  }
  function vehicleHandle(act, b, store) {
    store.update(function (s) {
      var v = s.vehicle;
      if (act.indexOf('door:') === 0) { var k = act.slice(5); v.doors[k] = !v.doors[k]; if (v.doors[k]) v.locked = false; }
      else if (act === 'trunk') v.trunk = !v.trunk;
      else if (act === 'hood') v.hood = !v.hood;
      else if (act === 'port') v.port = v.port === 'closed' ? 'open' : v.port === 'open' ? 'charging' : 'closed';
      else if (act === 'closewins') Object.keys(v.windows).forEach(function (k) { v.windows[k] = 0; });
    });
  }
  A.mountVehicleStatus = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-vs', function (st) { return vehicleTpl(st, { interactive: opts.interactive !== false }); }, vehicleHandle);
  };

  /* ================= Status bar ================= */
  function statusTpl(st) {
    var t = A.getTheme();
    return '<div class="grp">' + icon('signal') + '<span>4G</span>' + icon('wifi') + icon('bt') + '</div><span class="au-sp"></span>' +
      '<div class="grp">' + icon('fan') + '<span>' + (st.climate.on ? dec(st.climate.tL) + '°' : 'Desl.') + '</span><span style="width:6px"></span>' + icon('battery') + '<span>' + st.battery.soc + '%</span></div>' +
      '<button class="au-themebtn" data-act="theme" aria-label="Alternar tema">' + icon(t === 'dark' ? 'moon' : 'sun') + (t === 'dark' ? 'Escuro' : 'Claro') + '</button>';
  }
  A.mountStatusBar = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-status', statusTpl, function (act, b, store) {
      if (act === 'theme') { A.setTheme(A.getTheme() === 'dark' ? 'light' : 'dark'); store.update(function () {}); }
    });
  };

  /* ================= Shortcut tiles ================= */
  var TILE_IDS = ['nav', 'battery', 'climate', 'media'];
  function tilesTpl(st) {
    var o = st.ui.open, b = st.battery, c = st.climate, m = st.media, it = MEDIA[m.source].items[m.idx % MEDIA[m.source].items.length];
    var ev = Math.round(b.soc * 0.95), home = poi('home'), work = poi('work'), act = st.nav.active && poi(st.nav.active);
    function hd(id, ic, name) { return '<div class="hd"><span class="bdg">' + icon(ic) + '</span>' + name + icon('chevD', 'chev') + '</div>'; }
    return '<button class="au-tile' + (o === 'nav' ? ' on' : '') + '" data-act="open:nav">' + hd('nav', 'nav', 'Navegação') +
      (act ? '<div><div class="big">' + act.min + '<small>min</small></div><div class="sub">Em rota para ' + esc(act.n) + ' · ' + dec(act.km) + ' km</div></div>'
        : '<div class="favs"><span>' + icon('home') + 'Casa<em>' + home.min + ' min</em></span><span>' + icon('work') + 'Trabalho<em>' + work.min + ' min</em></span></div>') + '</button>' +
      '<button class="au-tile' + (o === 'battery' ? ' on' : '') + '" data-act="open:battery">' + hd('battery', 'battery', 'Bateria e autonomia') +
      '<div><div class="big">' + (ev + b.fuelKm) + '<small>km · ' + b.soc + '%</small></div><div class="au-bar" style="margin:8px 0 6px"><i style="width:' + b.soc + '%"></i></div><div class="sub">Elétrica ' + ev + ' km · Modo ' + b.mode.toUpperCase() + '</div></div></button>' +
      '<button class="au-tile' + (o === 'climate' ? ' on' : '') + '" data-act="open:climate">' + hd('climate', 'fan', 'Climatização') +
      '<div><div class="big">' + (c.on ? dec(c.tL) + '°' : 'Desligado') + (c.on ? '<small>' + dec(c.tR) + '° pass.</small>' : '') + '</div><div class="sub">' + (c.on ? (c.auto ? 'Automático' : 'Manual') + ' · Ventilação ' + c.fan + (c.ac ? ' · A/C' : '') : 'Toque para ligar') + '</div></div></button>' +
      '<div class="au-tile' + (o === 'media' ? ' on' : '') + '" data-act="open:media" role="button" tabindex="0">' + hd('media', 'music', 'Mídia') +
      '<div class="mini"><div class="tt"><b>' + esc(it.t) + '</b><span>' + esc(it.a) + '</span></div>' +
      '<button class="mbtn pri" data-act="m:play" aria-label="Reproduzir/pausar">' + icon(m.playing ? 'pause' : 'play') + '</button>' +
      '<button class="mbtn" data-act="m:next" aria-label="Próxima">' + icon('next') + '</button></div></div>';
  }
  function mediaAct(act, s) {
    var m = s.media, n = MEDIA[m.source].items.length;
    if (act === 'm:play') m.playing = !m.playing;
    if (act === 'm:next') { m.idx = (m.idx + 1) % n; m.pos = 0.02; }
    if (act === 'm:prev') { if (m.pos > 0.1 && m.source !== 'fm') m.pos = 0; else m.idx = (m.idx + n - 1) % n; }
  }
  A.mountShortcutTiles = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-tiles', tilesTpl, function (act, b, store) {
      if (act.indexOf('m:') === 0) { store.update(function (s) { mediaAct(act, s); }); return; }
      if (act.indexOf('open:') === 0) { var id = act.slice(5); store.update(function (s) { s.ui.open = s.ui.open === id ? null : id; }); }
    });
  };

  /* ================= Dock ================= */
  function dockTpl(st) {
    var c = st.climate, o = st.ui.open;
    function temp(side, v, label) {
      return '<div class="au-temp"><button data-act="t:' + side + ':-" aria-label="Diminuir ' + label + '">' + icon('chevL') + '</button>' +
        '<div class="v au-num">' + (c.on ? dec(v) + '°' : '—') + '<small>' + label + '</small></div>' +
        '<button data-act="t:' + side + ':+" aria-label="Aumentar ' + label + '">' + icon('chevR') + '</button></div>';
    }
    var seatOn = c.seats.d.heat || c.seats.d.vent;
    return '<button class="au-db' + (!o && !st.ui.search ? ' on' : '') + '" data-act="home" aria-label="Início">' + icon('home') + '</button>' +
      temp('L', c.tL, 'Motorista') + '<span class="sep"></span>' +
      '<div class="mid">' +
      '<button class="au-db' + (o === 'climate' ? ' on' : '') + '" data-act="open:climate" aria-label="Climatização">' + icon('fan') + '</button>' +
      '<button class="au-db' + (c.seats.d.heat ? ' heat' : c.seats.d.vent ? ' on' : '') + '" data-act="seat" aria-label="Banco do motorista">' + icon(c.seats.d.vent && !c.seats.d.heat ? 'seatVent' : 'seatHeat') + '</button>' +
      '<button class="au-db' + (c.defF ? ' on' : '') + '" data-act="defF" aria-label="Desembaçador dianteiro">' + icon('defF') + '</button>' +
      '<button class="au-db" data-act="noop" aria-label="Telefone">' + icon('phone') + '</button>' +
      '<button class="au-db" data-act="noop" aria-label="Aplicativos">' + icon('apps') + '</button>' +
      '<button class="au-db" data-act="noop" aria-label="Ajustes">' + icon('settings') + '</button>' +
      '<button class="au-db' + (o === 'media' ? ' on' : '') + '" data-act="open:media" aria-label="Volume e mídia">' + icon('volume') + '</button>' +
      '</div><span class="sep"></span>' + temp('R', c.tR, 'Passageiro');
  }
  function climateAct(act, s) {
    var c = s.climate;
    if (act.indexOf('t:') === 0) {
      var p = act.split(':'), d = p[2] === '+' ? 0.5 : -0.5;
      if (!c.on) { c.on = true; return; }
      if (p[1] === 'L') { c.tL = clamp(c.tL + d, 16, 30); if (c.sync) c.tR = c.tL; } else { c.tR = clamp(c.tR + d, 16, 30); c.sync = false; }
    }
    if (act === 'power') c.on = !c.on;
    if (act === 'auto') { c.auto = !c.auto; }
    if (act === 'ac') c.ac = !c.ac;
    if (act === 'recirc') c.recirc = !c.recirc;
    if (act === 'defF') c.defF = !c.defF;
    if (act === 'defR') c.defR = !c.defR;
    if (act === 'sync') { c.sync = !c.sync; if (c.sync) c.tR = c.tL; }
    if (act === 'fan:+') { c.fan = clamp(c.fan + 1, 1, 7); c.auto = false; }
    if (act === 'fan:-') { c.fan = clamp(c.fan - 1, 1, 7); c.auto = false; }
    if (act === 'seat') { var sd = c.seats.d; if (sd.heat) { sd.heat = (sd.heat + 1) % 4; } else { sd.heat = 1; sd.vent = 0; } }
    if (act.indexOf('sh:') === 0 || act.indexOf('sv:') === 0) {
      var seat = c.seats[act.slice(3)], key = act[1] === 'h' ? 'heat' : 'vent', other = key === 'heat' ? 'vent' : 'heat';
      seat[key] = (seat[key] + 1) % 4; if (seat[key]) seat[other] = 0;
    }
  }
  A.mountDock = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-dock', dockTpl, function (act, b, store) {
      store.update(function (s) {
        if (act === 'home') { s.ui.open = null; s.ui.search = false; return; }
        if (act.indexOf('open:') === 0) { var id = act.slice(5); s.ui.search = false; s.ui.open = s.ui.open === id ? null : id; return; }
        climateAct(act, s);
      });
    });
  };

  /* ================= Panels ================= */
  function head(title, extra) { return '<div class="au-ph"><h3>' + title + '</h3>' + (extra || '') + '<button class="x" data-act="close" aria-label="Fechar">' + icon('close') + '</button></div>'; }
  function lv(n) { var s = '<span class="au-lv">'; for (var i = 1; i <= 3; i++) s += '<i class="' + (i <= n ? 'on' : '') + '"></i>'; return s + '</span>'; }

  function climateTpl(st) {
    var c = st.climate, bars = '';
    for (var i = 1; i <= 7; i++) bars += '<i class="' + (i <= c.fan ? 'on' : '') + '" style="height:' + (30 + i * 10) + '%"></i>';
    function tc(side, label, v) {
      return '<div class="au-tcard"><div class="k">' + label + '</div><div class="v">' + dec(v) + '°</div><div class="r">' +
        '<button class="au-step" data-act="t:' + side + ':-" aria-label="Diminuir">' + icon('minus') + '</button>' +
        '<button class="au-step" data-act="t:' + side + ':+" aria-label="Aumentar">' + icon('plus') + '</button></div></div>';
    }
    function mode(id, ic, label, on) { return '<button class="au-mode' + (on ? ' on' : '') + '" data-act="' + id + '">' + icon(ic) + label + '</button>'; }
    function seat(id, label) {
      var s = c.seats[id];
      return '<div class="au-seat"><div class="k">' + label + '</div><div class="r">' +
        '<button class="au-sbtn heat' + (s.heat ? ' on' : '') + '" data-act="sh:' + id + '" aria-label="Aquecer, nível ' + s.heat + '">' + icon('seatHeat') + lv(s.heat) + '</button>' +
        '<button class="au-sbtn cool' + (s.vent ? ' on' : '') + '" data-act="sv:' + id + '" aria-label="Ventilar, nível ' + s.vent + '">' + icon('seatVent') + lv(s.vent) + '</button></div></div>';
    }
    return head('Climatização', '<span class="au-sp"></span><div class="au-swrow">' + (c.on ? 'Ligado' : 'Desligado') + '<button class="au-sw' + (c.on ? ' on' : '') + '" data-act="power" aria-label="Ligar ou desligar"></button></div>') +
      '<div class="cols"><div><div class="au-tgrid">' + tc('L', 'Motorista', c.tL) + tc('R', 'Passageiro', c.tR) + '</div>' +
      '<div class="au-fan"><div class="au-lbl">Ventilação · ' + (c.auto ? 'automática' : 'nível ' + c.fan) + '</div><div class="r">' +
      '<button class="au-step sm" data-act="fan:-" aria-label="Menos ventilação">' + icon('minus') + '</button><div class="au-fanbars">' + bars + '</div>' +
      '<button class="au-step sm" data-act="fan:+" aria-label="Mais ventilação">' + icon('plus') + '</button></div></div></div>' +
      '<div><div class="au-modes">' + mode('auto', 'auto', 'AUTO', c.auto) + mode('ac', 'snow', 'A/C', c.ac) + mode('recirc', 'recirc', 'Recircular', c.recirc) +
      mode('defF', 'defF', 'Desemb. diant.', c.defF) + mode('defR', 'defR', 'Desemb. tras.', c.defR) + mode('sync', 'sync', 'Sincronizar', c.sync) + '</div>' +
      '<div class="au-seats">' + seat('d', 'Banco motorista') + seat('p', 'Banco passageiro') + '</div></div></div>';
  }
  A.mountClimatePanel = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-pop au-clim' + (opts.standalone ? ' standalone' : ''), function (st) { el.classList.toggle('off', !st.climate.on); return climateTpl(st); }, function (act, b, store) {
      store.update(function (s) { if (act === 'close') { s.ui.open = null; return; } climateAct(act, s); });
    });
  };

  function mediaTpl(st) {
    var m = st.media, src = MEDIA[m.source], it = src.items[m.idx % src.items.length], live = m.source === 'fm';
    function t(sec) { sec = Math.round(sec); return Math.floor(sec / 60) + ':' + ('0' + sec % 60).slice(-2); }
    var segs = Object.keys(MEDIA).map(function (k) { return '<button class="' + (k === m.source ? 'on' : '') + '" data-act="src:' + k + '">' + icon(MEDIA[k].icon) + MEDIA[k].label + '</button>'; }).join('');
    return head('Mídia', '<div class="au-seg">' + segs + '</div>') +
      '<div class="top"><div class="au-art"><div class="au-eq' + (m.playing ? ' on' : '') + '"><i></i><i></i><i></i><i></i><i></i></div></div>' +
      '<div class="meta"><b>' + esc(it.t) + '</b><span>' + esc(it.a) + '</span><span class="src">' + icon(src.icon) + src.label + (live ? ' · ao vivo' : '') + '</span></div></div>' +
      (live ? '<div class="au-prog"><div class="au-bar"><i style="width:100%;opacity:.35"></i></div><div class="t"><span>Estação ' + (m.idx + 1) + ' de ' + src.items.length + '</span><span>Ao vivo</span></div></div>'
        : '<div class="au-prog"><div class="au-bar"><i style="width:' + Math.round(m.pos * 100) + '%"></i></div><div class="t"><span>' + t(it.d * m.pos) + '</span><span>' + t(it.d) + '</span></div></div>') +
      '<div class="au-ctrl"><button class="s" data-act="m:prev" aria-label="Anterior">' + icon('prev') + '</button>' +
      '<button class="p" data-act="m:play" aria-label="' + (m.playing ? 'Pausar' : 'Reproduzir') + '">' + icon(m.playing ? 'pause' : 'play') + '</button>' +
      '<button class="s" data-act="m:next" aria-label="Próxima">' + icon('next') + '</button></div>' +
      '<div class="au-vol"><button class="au-step sm" data-act="vol:-" aria-label="Diminuir volume">' + icon('minus') + '</button><div class="au-bar"><i style="width:' + Math.round(m.vol / 30 * 100) + '%"></i></div>' +
      '<button class="au-step sm" data-act="vol:+" aria-label="Aumentar volume">' + icon('plus') + '</button><span class="n">' + m.vol + '</span></div>';
  }
  A.mountMediaPanel = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-pop au-media' + (opts.standalone ? ' standalone' : ''), mediaTpl, function (act, b, store) {
      store.update(function (s) {
        if (act === 'close') { s.ui.open = null; return; }
        if (act.indexOf('src:') === 0) { s.media.source = act.slice(4); s.media.idx = 0; s.media.pos = 0.1; return; }
        if (act === 'vol:+') s.media.vol = clamp(s.media.vol + 1, 0, 30);
        if (act === 'vol:-') s.media.vol = clamp(s.media.vol - 1, 0, 30);
        mediaAct(act, s);
      });
    });
  };

  function navTpl(st) {
    var h = poi('home'), w = poi('work'), act = st.nav.active && poi(st.nav.active);
    function fav(p) {
      return '<button class="au-fav" data-act="go:' + p.id + '"><span class="au-row"><span class="i">' + icon(p.i) + '</span><span class="au-sp"></span><span class="eta">' + p.min + ' min</span></span>' +
        '<span><b style="display:block">' + p.n + '</b><span>' + dec(p.km) + ' km · chegada ' + arrival(p.min) + '</span></span></button>';
    }
    return head('Navegação') +
      (act ? '<div class="au-li on" style="margin-bottom:12px"><span class="i">' + icon('turn') + '</span><span class="tx"><b>Em rota para ' + esc(act.n) + '</b><span>' + act.min + ' min · ' + dec(act.km) + ' km · chegada ' + arrival(act.min) + '</span></span>' +
        '<button class="au-cat" data-act="stop">' + icon('close') + 'Encerrar</button></div>' : '') +
      '<button class="au-search" data-act="search">' + icon('search') + 'Para onde vamos?<span class="mic">' + icon('mic') + '</span></button>' +
      '<div class="au-favs">' + fav(h) + fav(w) + '</div>' +
      '<div class="au-cats">' + CATS.slice(0, 3).map(function (c) { return '<button class="au-cat" data-act="cat:' + c.id + '">' + icon(c.i) + c.n + '</button>'; }).join('') + '</div>';
  }
  A.mountNavPanel = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-pop au-nav' + (opts.standalone ? ' standalone' : ''), navTpl, function (act, b, store) {
      store.update(function (s) {
        if (act === 'close') { s.ui.open = null; return; }
        if (act === 'stop') { s.nav.active = null; return; }
        s.ui.search = true; s.ui.open = null;
        s.ui.searchSel = act.indexOf('go:') === 0 ? act.slice(3) : null;
        s.ui.searchCat = act.indexOf('cat:') === 0 ? act.slice(4) : null;
      });
    });
  };

  function batteryTpl(st) {
    var b = st.battery, v = st.vehicle, ev = Math.round(b.soc * 0.95), C = 2 * Math.PI * 58;
    function seg(key, opts2) { return '<div class="au-seg">' + opts2.map(function (o) { return '<button class="' + (b[key] === o[0] ? 'on' : '') + '" data-act="set:' + key + ':' + o[0] + '">' + (o[2] ? icon(o[2]) : '') + o[1] + '</button>'; }).join('') + '</div>'; }
    function opt(title, sub, right, dis) { return '<div class="au-opt' + (dis ? ' dis' : '') + '"><div class="tx"><b>' + title + '</b><span>' + sub + '</span></div>' + right + '</div>'; }
    function sw(key) { return '<button class="au-sw' + (b[key] ? ' on' : '') + '" data-act="tog:' + key + '" aria-label="Alternar"></button>'; }
    var hrs = Math.max(0, Math.round((b.limit - b.soc) * 0.17 * 10) / 10);
    var chg = v.port === 'charging' ? '<div class="au-chg">' + icon('bolt') + '<span>Carregando · 6,6 kW<br>~' + dec(hrs) + ' h até ' + b.limit + '%</span></div>'
      : '<div class="au-chg off">' + icon('plug') + '<span>Não conectado</span></div>';
    return head('Bateria e autonomia') +
      '<div class="cols"><div>' +
      '<div class="au-btop"><div class="au-ring"><svg viewBox="0 0 132 132"><circle class="tr" cx="66" cy="66" r="58"/><circle class="fg" cx="66" cy="66" r="58" stroke-dasharray="' + (C * b.soc / 100).toFixed(1) + ' ' + C.toFixed(1) + '"/></svg>' +
      '<div class="c"><div><b>' + b.soc + '%</b><span style="display:block">bateria</span></div></div></div>' +
      '<div><div class="k">Autonomia total</div><div class="big">' + (ev + b.fuelKm) + ' <small>km</small></div>' + chg + '</div></div>' +
      '<div class="au-stats"><div><span>' + icon('bolt') + 'Elétrica</span><b>' + ev + ' km</b></div><div><span>' + icon('fuel') + 'Combustível</span><b>' + b.fuelKm + ' km</b></div></div>' +
      '<div class="au-lbl">Modo de energia</div>' + seg('mode', [['ev', 'EV', 'bolt'], ['hev', 'HEV', 'fuel']]) + '</div>' +
      '<div>' +
      opt('Condução', 'Resposta do acelerador', seg('drive', [['eco', 'Eco', 'leaf'], ['normal', 'Normal'], ['sport', 'Sport', 'gauge']])) +
      opt('Manter carga (SOC)', b.hold ? 'Preserva ' + b.holdTarget + '% com o motor a combustão' : 'Desligado', '<button class="au-step sm" data-act="hold:-" aria-label="Menos">' + icon('minus') + '</button><span class="val">' + b.holdTarget + '%</span><button class="au-step sm" data-act="hold:+" aria-label="Mais">' + icon('plus') + '</button>' + sw('hold'), !b.hold) +
      opt('Limite de carga', 'Para de carregar ao atingir', seg('limit', [[80, '80%'], [90, '90%'], [100, '100%']])) +
      opt('Carregamento agendado', b.sched ? '23:00 – 06:00, todos os dias' : 'Carrega assim que conectar', sw('sched')) +
      opt('Descarga externa (V2L)', b.v2l ? 'Ativa · para em ' + b.v2lMin + '% de bateria' : 'Tomada externa desligada', '<span class="val">' + b.v2lMin + '%</span>' + sw('v2l')) +
      '</div></div>';
  }
  A.mountBatteryPanel = function (el, opts) {
    opts = opts || {};
    return mount(el, opts.store, 'au au-pop au-bat' + (opts.standalone ? ' standalone' : ''), batteryTpl, function (act, b, store) {
      store.update(function (s) {
        var bt = s.battery;
        if (act === 'close') { s.ui.open = null; return; }
        if (act.indexOf('set:') === 0) { var p = act.split(':'); bt[p[1]] = p[1] === 'limit' ? Number(p[2]) : p[2]; }
        if (act.indexOf('tog:') === 0) { var k = act.slice(4); bt[k] = !bt[k]; }
        if (act === 'hold:+') { bt.holdTarget = clamp(bt.holdTarget + 5, 25, 70); bt.hold = true; }
        if (act === 'hold:-') { bt.holdTarget = clamp(bt.holdTarget - 5, 25, 70); bt.hold = true; }
      });
    });
  };

  /* ================= Map + full search ================= */
  function mapSVG(sel, list) {
    var s = '<svg viewBox="0 0 840 600" preserveAspectRatio="xMidYMid slice" aria-hidden="true">';
    for (var i = 0; i < 13; i++) for (var j = 0; j < 9; j++) {
      var park = (i === 4 && j === 4) || (i === 5 && j === 4) || (i === 10 && j === 7);
      s += '<rect class="' + (park ? 'park' : 'blk') + '" x="' + (i * 70 + 7) + '" y="' + (j * 70 + 7) + '" width="56" height="56" rx="6"/>';
    }
    s += '<path class="water" d="M-10 40C120 70 160 10 300 30S520 -10 860 20V-10H-10Z"/>';
    var rd = '';
    for (var x = 0; x <= 840; x += 70) rd += 'M' + x + ' 0V600';
    for (var y = 0; y <= 600; y += 70) rd += 'M0 ' + y + 'H840';
    s += '<path class="rd" stroke-width="10" d="' + rd + '"/>';
    s += '<path class="rdm" stroke-width="16" d="M280 0V600M0 280H840M0 560L560 0"/>';
    if (sel) {
      var p = poi(sel), r = 'M' + ME.x + ' ' + ME.y + 'V' + p.y + 'H' + p.x;
      s += '<path class="route-o" d="' + r + '"/><path class="route" d="' + r + '"/>';
    }
    (list || []).forEach(function (p) {
      if (p.id === sel) return;
      s += '<circle class="pin" cx="' + p.x + '" cy="' + p.y + '" r="9"/>';
    });
    if (sel) { var q = poi(sel); s += '<path class="dest" d="M' + q.x + ' ' + q.y + 'c-12-14-14-20-14-25a14 14 0 0 1 28 0c0 5-2 11-14 25Z" transform="translate(0 -4)"/>'; }
    s += '<circle class="mehalo" cx="' + ME.x + '" cy="' + ME.y + '" r="30"/><path class="me" d="M' + ME.x + ' ' + (ME.y - 14) + 'l11 25-11-6-11 6Z"/>';
    return s + '</svg>';
  }
  A.mapSVG = mapSVG;
  function filterPois(q, cat) {
    var nq = norm(q || '');
    return POIS.filter(function (p) {
      if (cat && p.c !== cat) return false;
      if (!nq) return cat ? true : p.c !== 'fav';
      var catName = (CATS.filter(function (c) { return c.id === p.c; })[0] || {}).n || '';
      return norm(p.n + ' ' + p.a + ' ' + catName).indexOf(nq) >= 0;
    }).sort(function (a, b) { return a.km - b.km; });
  }
  A.mountNavSearch = function (el, opts) {
    opts = opts || {};
    var store = opts.store || A.createStore();
    el.classList.add('au', 'au-ns'); if (opts.standalone) el.classList.add('standalone');
    var q = '';
    el.innerHTML = '<div class="side"><div class="sb"><button class="back" data-act="back" aria-label="Voltar">' + icon('back') + '</button>' +
      '<label class="field">' + icon('search') + '<input type="search" placeholder="Buscar destino" aria-label="Buscar destino"><button class="au-step sm" data-act="clear" aria-label="Limpar" style="background:none">' + icon('close') + '</button></label></div>' +
      '<div class="au-cats" data-part="cats"></div><div class="scroll" data-part="list"></div></div>' +
      '<div class="au-map" data-part="map"></div>';
    var input = el.querySelector('input'), cats = el.querySelector('[data-part=cats]'), list = el.querySelector('[data-part=list]'), map = el.querySelector('[data-part=map]');
    function row(p, sel) {
      return '<button class="au-li' + (p.id === sel ? ' on' : '') + '" data-act="sel:' + p.id + '"><span class="i">' + icon(p.i) + '</span><span class="tx"><b>' + esc(p.n) + '</b><span>' + esc(p.a) + '</span></span>' +
        '<span class="d"><b>' + p.min + ' min</b>' + dec(p.km) + ' km</span></button>';
    }
    function render() {
      var st = store.get(), sel = st.ui.searchSel, cat = st.ui.searchCat, res = filterPois(q, cat);
      cats.innerHTML = CATS.map(function (c) { return '<button class="au-cat' + (cat === c.id ? ' on' : '') + '" data-act="cat:' + c.id + '">' + icon(c.i) + c.n + '</button>'; }).join('');
      var h = '';
      if (!q && !cat) {
        h += '<div class="h">Favoritos</div><div class="favrow">' + ['home', 'work'].map(function (id) { var p = poi(id); return '<button data-act="sel:' + id + '">' + icon(p.i) + p.n + '<em>' + p.min + ' min</em></button>'; }).join('') + '</div>';
        h += '<div class="h" style="margin-top:14px">Recentes</div><div class="au-list">' + ['c1', 'e1', 'r2'].map(function (id) { return row(poi(id), sel); }).join('') + '</div>';
      } else {
        h += '<div class="h">' + res.length + ' resultado' + (res.length === 1 ? '' : 's') + ' · por distância</div>';
        h += res.length ? '<div class="au-list">' + res.map(function (p) { return row(p, sel); }).join('') + '</div>' : '<div class="au-empty">Nada encontrado para “' + esc(q) + '”. Tente um endereço ou uma categoria.</div>';
      }
      list.innerHTML = h;
      var p = sel && poi(sel);
      map.innerHTML = mapSVG(sel, (q || cat) ? res : []) +
        '<div class="au-mapctl"><button data-act="noop" aria-label="Camadas">' + icon('layers') + '</button><button data-act="noop" aria-label="Centralizar">' + icon('locate') + '</button></div>' +
        (p ? '<div class="au-sheet"><div class="eta">' + p.min + ' min</div><div class="tx"><b>' + esc(p.n) + '</b><span>' + dec(p.km) + ' km · chegada ' + arrival(p.min) + ' · ' + esc(p.a) + '</span></div>' +
          '<button class="alt" data-act="unsel">' + icon('close') + '</button><button class="go" data-act="go">' + icon('nav') + 'Iniciar</button></div>' : '');
    }
    var unsub = store.sub(render); render();
    input.addEventListener('input', function () { q = input.value; render(); });
    delegate(el, 'click', function (act) {
      if (act === 'clear') { q = ''; input.value = ''; input.focus(); store.update(function (s) { s.ui.searchCat = null; }); return; }
      store.update(function (s) {
        if (act === 'back') { s.ui.search = false; }
        else if (act.indexOf('cat:') === 0) { var c = act.slice(4); s.ui.searchCat = s.ui.searchCat === c ? null : c; }
        else if (act.indexOf('sel:') === 0) s.ui.searchSel = act.slice(4);
        else if (act === 'unsel') s.ui.searchSel = null;
        else if (act === 'go') { s.nav.active = s.ui.searchSel; s.ui.search = false; }
      });
    });
    return { store: store, render: render, destroy: unsub, focus: function () { input.focus(); } };
  };

  /* ================= Launcher (whole screen) ================= */
  var MTN = 'M0 300L70 262 130 280 210 226 280 262 350 236 420 270 500 246 560 268 640 232 720 270 800 240 880 266 960 220 1040 262 1110 238 1190 268 1280 244V720H0Z';
  A.mountLauncher = function (el, opts) {
    opts = opts || {};
    var store = opts.store || A.createStore();
    el.classList.add('au', 'au-screen');
    el.innerHTML = '<svg class="au-backdrop" viewBox="0 0 1280 720" preserveAspectRatio="none" aria-hidden="true"><path class="hz" d="' + MTN + '" opacity=".55"/><path class="hz" d="M0 330L90 300 170 318 260 290 360 312 470 296 600 320 720 300 840 318 960 296 1080 316 1180 300 1280 314V720H0Z"/></svg>' +
      '<div data-part="status"></div>' +
      '<div class="au-clock"><div class="t" data-part="time"></div><div class="d" data-part="date"></div><div class="w">' + icon('cloud') + '<span data-part="out"></span></div></div>' +
      '<div class="au-summary" data-part="summary"></div>' +
      '<div class="au-hero-car" data-part="car" style="width:680px;height:360px"></div>' +
      '<div data-part="tiles"></div><div data-part="layer"></div><div data-part="dock"></div>';
    function $(n) { return el.querySelector('[data-part=' + n + ']'); }
    A.mountStatusBar($('status'), { store: store });
    A.mountVehicleStatus($('car'), { store: store });
    A.mountShortcutTiles($('tiles'), { store: store });
    A.mountDock($('dock'), { store: store });
    var sum = $('summary');
    delegate(sum, 'click', function (act) { if (act === 'lock') store.update(function (s) { s.vehicle.locked = !s.vehicle.locked; }); });
    function clock() {
      var d = new Date();
      $('time').textContent = d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      var ds = d.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }); $('date').textContent = ds.charAt(0).toUpperCase() + ds.slice(1);
    }
    clock(); setInterval(clock, 15000);

    var layer = $('layer'), lastKey = '', cur = null;
    function renderLayer(st) {
      var key = (st.ui.search ? 'S' : '') + (st.ui.open || '');
      var sm = vSummary(st.vehicle);
      $('out').textContent = 'Externa ' + st.outside + '° · Interna ' + dec(st.climate.tL) + '°';
      sum.innerHTML = '<div class="au-model">' + esc(st.model) + '</div>' +
        '<button class="au-lockbtn' + (st.vehicle.locked ? '' : ' off') + '" data-act="lock">' + icon(st.vehicle.locked ? 'lock' : 'unlock') + (st.vehicle.locked ? 'Travado' : 'Destravado') + '</button>' +
        '<div class="au-sumline' + (sm.open ? ' warn' : '') + '">' + icon(sm.open ? 'alert' : 'check') + (sm.open ? sm.open + (sm.open === 1 ? ' abertura' : ' aberturas') : 'Tudo fechado') + '</div>';
      if (key === lastKey) return;
      lastKey = key; if (cur && cur.destroy) cur.destroy(); cur = null; layer.innerHTML = '';
      if (st.ui.search) {
        var ns = document.createElement('div'); layer.appendChild(ns);
        var api = cur = A.mountNavSearch(ns, { store: store }); if (!st.ui.searchSel && !st.ui.searchCat) setTimeout(api.focus, 50);
        return;
      }
      if (!st.ui.open) return;
      var scrim = document.createElement('div'); scrim.className = 'au-scrim';
      scrim.addEventListener('click', function () { store.update(function (s) { s.ui.open = null; }); });
      layer.appendChild(scrim);
      var pop = document.createElement('div'); layer.appendChild(pop);
      var fn = { nav: A.mountNavPanel, battery: A.mountBatteryPanel, climate: A.mountClimatePanel, media: A.mountMediaPanel }[st.ui.open];
      cur = fn(pop, { store: store });
      var i = TILE_IDS.indexOf(st.ui.open), tileW = (1216 - 48) / 4, cx = 32 + i * (tileW + 16) + tileW / 2;
      var w = pop.offsetWidth || 480, left = clamp(cx - w / 2, 24, 1280 - 24 - w);
      pop.style.left = left + 'px'; pop.style.setProperty('--caret', (cx - left) + 'px');
    }
    store.sub(renderLayer); renderLayer(store.get());
    return { store: store };
  };

  /* ================= Icon sheet ================= */
  A.mountIcons = function (el) {
    el.classList.add('au');
    el.innerHTML = '<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(92px,1fr));gap:8px">' + A.iconNames.map(function (n) {
      return '<div style="display:flex;flex-direction:column;align-items:center;gap:6px;padding:12px 4px;border-radius:12px;background:var(--surface-sunken)">' + icon(n) + '<span style="font-size:11px;color:var(--ink-muted)">' + n + '</span></div>';
    }).join('') + '</div>';
  };

  window.Aura = A;
})();
