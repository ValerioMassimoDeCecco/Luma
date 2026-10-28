import { handleApi } from './static/service.js';

// Only pages belonging to this extension may invoke the device service.
chrome.runtime.onMessage.addListener((message, sender, reply) => {
  if (sender.id !== chrome.runtime.id || !sender.url?.startsWith(chrome.runtime.getURL('')) || message?.kind !== 'luma-api') return false;
  Promise.resolve(handleApi(message.path, message.body, message.demo)).then(
    result => reply({ok: true, ...result}),
    error => reply({ok: false, error: error?.message || 'Operazione non riuscita.', code: error?.code || 'error'})
  );
  return true;
});
