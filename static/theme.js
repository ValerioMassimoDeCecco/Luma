/* Applica la preferenza prima del rendering, senza cambiare la light mode predefinita. */
try {
  const saved = localStorage.getItem('luma-theme');
  document.documentElement.dataset.theme = saved === 'dark' ? 'dark' : 'light';
} catch (_) {
  document.documentElement.dataset.theme = 'light';
}
if (new URLSearchParams(location.search).has('popup')) document.documentElement.classList.add('is-popup');
