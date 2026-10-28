(function () {
  var KEY = 'aura-theme', t = null;
  try { t = localStorage.getItem(KEY); } catch (e) {}
  if (t !== 'light' && t !== 'dark') t = (window.matchMedia && matchMedia('(prefers-color-scheme: light)').matches) ? 'light' : 'dark';
  Aura.setTheme(t);
  new MutationObserver(function () { try { localStorage.setItem(KEY, Aura.getTheme()); } catch (e) {} })
    .observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  var stage = document.getElementById('stage');
  function fit() {
    var s = Math.min(window.innerWidth / 1280, window.innerHeight / 720);
    stage.style.transform = 'scale(' + s + ') translate(-50%, -50%)';
  }
  window.addEventListener('resize', fit); fit();
  window.auraStore = Aura.createStore();
  Aura.mountLauncher(stage, { store: window.auraStore });
})();
