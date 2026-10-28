'use strict';

const $ = id => document.getElementById(id);
const state = {
  connected: false, demo: false, linkError: false, on: false,
  deviceId: '', name: 'Lampadina Tapo', model: '', ip: '', session: '',
  bri: 72, ct: 3200, minCt: 2500, maxCt: 6500, hue: 30, sat: 85,
  mode: 'white', editMode: 'white', caps: {brightness: false, ct: false, color: false},
};
let house = {rooms: [], devices: [], accounts: [], legacy_password_present: false};
let selectedRoomId = '', selectedDeviceId = '';
let formDeviceId = '', roomFormId = '';
let connecting = false, refreshing = false, dragging = false;
let revision = 0, navigationGeneration = 0, sending = false, sendTimer = null, popupInFlight = 0;
const pending = new Map();
let toastTimer;
const scenes = {
  relax: {bri: 35, mode: 'white', ct: 2700}, focus: {bri: 85, mode: 'white', ct: 5000},
  sunset: {bri: 65, mode: 'color', hue: 28, sat: 90}, dream: {bri: 30, mode: 'color', hue: 275, sat: 75},
};

function storageGet(key) { try { return localStorage.getItem(key) || ''; } catch (_) { return ''; } }
function storageSet(key, value) { try { localStorage.setItem(key, value); } catch (_) {} }
function currentDevice() { return house.devices.find(d => d.id === selectedDeviceId) || null; }
function currentRoom() { return house.rooms.find(r => r.id === selectedRoomId) || null; }
function accountFor(email) { return house.accounts.find(a => a.email.toLocaleLowerCase() === email.trim().toLocaleLowerCase()) || null; }
function roomDevices(id) { return house.devices.filter(d => d.room_id === id); }

function applyTheme(theme, remember = true) {
  const dark = theme === 'dark';
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  $('themeIcon').setAttribute('href', dark ? '#i-sun' : '#i-moon');
  $('themeLabel').textContent = dark ? 'Tema chiaro' : 'Tema scuro';
  $('themeToggle').setAttribute('aria-pressed', String(dark));
  const label = dark ? 'Passa al tema chiaro' : 'Passa al tema scuro';
  $('themeToggle').setAttribute('aria-label', label);
  $('themeToggle').setAttribute('title', label);
  document.querySelector('meta[name="theme-color"]').content = dark ? '#181622' : '#ebe9f4';
  if (remember) storageSet('luma-theme', dark ? 'dark' : 'light');
}

function toast(message, error = false, duration = 4000) {
  const el = $('toast');
  el.textContent = message; el.classList.toggle('error', error); el.classList.add('visible');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('visible'), duration);
}
async function api(path, body) {
  try {
    const response = await chrome.runtime.sendMessage({kind:'luma-api', path, body, demo:state.demo});
    if (!response?.ok) { const error = new Error(response?.error || 'L’estensione non risponde. Ricaricala e riprova.'); error.code = response?.code; throw error; }
    return response;
  } catch (error) {
    if (error?.code) throw error;
    throw new Error(error?.message || 'L’estensione non risponde. Ricaricala e riprova.');
  }
}
function svgIcon(id) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg'); svg.classList.add('icon'); svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(ns, 'use'); use.setAttribute('href', `#${id}`); svg.append(use); return svg;
}
function saveSelection() {
  const scope = state.demo ? 'demo' : 'real';
  storageSet(`luma-room-${scope}`, selectedRoomId);
  storageSet(`luma-device-${scope}`, selectedDeviceId);
  if (selectedRoomId && selectedDeviceId) storageSet(`luma-device-${selectedRoomId}`, selectedDeviceId);
}
function renderHouse() {
  const rooms = $('roomList'); rooms.replaceChildren();
  house.rooms.forEach(room => {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'room-pill';
    button.setAttribute('aria-pressed', String(room.id === selectedRoomId));
    const name = document.createElement('span'); name.className = 'room-name'; name.textContent = room.name;
    const count = document.createElement('span'); count.className = 'room-count'; count.textContent = String(roomDevices(room.id).length);
    const total = roomDevices(room.id).length;
    count.setAttribute('aria-label', `${total} ${total === 1 ? 'lampadina' : 'lampadine'}`);
    button.append(name, count); button.addEventListener('click', () => selectRoom(room.id)); rooms.append(button);
  });
  const devices = $('deviceList'); devices.replaceChildren();
  const list = roomDevices(selectedRoomId);
  if (!list.length) {
    const empty = document.createElement('span'); empty.className = 'empty-lights'; empty.textContent = 'Ancora nessuna luce qui.'; devices.append(empty);
  }
  list.forEach(device => {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'device-pill';
    button.setAttribute('aria-pressed', String(device.id === selectedDeviceId));
    const name = document.createElement('span'); name.className = 'device-name'; name.textContent = device.name;
    button.append(svgIcon('i-bulb'), name); button.addEventListener('click', () => selectDevice(device.id)); devices.append(button);
  });
  $('editRoomBtn').disabled = !currentRoom();
  $('editDeviceBtn').disabled = !currentDevice();
  $('addDeviceBtn').disabled = !currentRoom();
}
function setHouse(newHouse) {
  house = newHouse || house;
  renderHouse();
}

const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
function kelvinToRgb(kelvin) {
  const t = kelvin / 100;
  const r = t <= 66 ? 255 : 329.698727446 * Math.pow(t - 60, -0.1332047592);
  const g = t <= 66 ? 99.4708025861 * Math.log(t) - 161.1195681661 : 288.1221695283 * Math.pow(t - 60, -0.0755148492);
  const b = t >= 66 ? 255 : (t <= 19 ? 0 : 138.5177312231 * Math.log(t - 10) - 305.0447927307);
  return [r, g, b].map(v => clamp(Math.round(v), 0, 255));
}
function hsvToRgb(hue, saturation, value = 100) {
  const s = saturation / 100, v = value / 100;
  const f = n => { const k = (n + hue / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
  return [5, 3, 1].map(n => Math.round(f(n) * 255));
}
function hexToHsv(hex) {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255);
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let hue = 0;
  if (d) {
    if (max === r) hue = ((g - b) / d + (g < b ? 6 : 0));
    else if (max === g) hue = (b - r) / d + 2;
    else hue = (r - g) / d + 4;
    hue *= 60;
  }
  return {hue: Math.round(hue), sat: Math.max(1, Math.round(max ? d / max * 100 : 0))};
}
function setConnection(kind, text) {
  $('connectionStatus').className = `connection-pill ${kind}`.trim(); $('connectionText').textContent = text;
}
function healthyStatus() { setConnection(state.demo ? 'demo' : 'online', state.demo ? 'Modalità demo' : 'Connessa'); }
function idleStatus() { setConnection(state.demo ? 'demo' : '', state.demo ? 'Modalità demo' : 'Non connessa'); }
function lightDescription() {
  if (state.mode === 'color' && state.caps.color) return 'Luce colorata';
  if (state.caps.ct) return state.ct < 3500 ? 'Bianco caldo' : state.ct <= 5000 ? 'Bianco naturale' : 'Bianco freddo';
  return 'Luce bianca';
}
function setOutput(id, value, unit) {
  const el = $(id); el.replaceChildren(document.createTextNode(String(value)));
  const suffix = document.createElement('span'); suffix.textContent = unit; el.append(suffix);
}
function sceneMatches(scene) {
  if (!state.on || state.bri !== scene.bri || state.mode !== scene.mode) return false;
  return scene.mode === 'white' ? state.ct === clamp(scene.ct, state.minCt, state.maxCt) : state.hue === scene.hue && state.sat === scene.sat;
}
function render() {
  const profile = currentDevice();
  const color = state.mode === 'color' && state.caps.color ? hsvToRgb(state.hue, state.sat) : kelvinToRgb(state.ct);
  const root = document.documentElement.style;
  root.setProperty('--light-rgb', color.join(' ')); root.setProperty('--hue', state.hue);
  root.setProperty('--light-opacity', String(0.48 + state.bri / 100 * 0.52));
  const card = $('deviceCard');
  card.classList.toggle('is-off', state.connected && !state.on);
  card.classList.toggle('is-disconnected', !state.connected);
  $('deviceName').textContent = profile?.name || 'La tua lampadina';
  $('deviceModel').textContent = state.connected ? `${state.model.toUpperCase()} · ${state.demo ? 'Simulazione' : state.ip}`
    : profile ? 'In attesa di connessione' : 'Aggiungi una luce a questa stanza';
  const deviceState = $('deviceState'); deviceState.className = 'device-state-pill';
  const dot = document.createElement('span'); let label = 'Non connessa';
  if (state.linkError) label = 'Non raggiungibile';
  else if (state.connected && state.demo) { label = state.on ? 'Demo · Accesa' : 'Demo · Spenta'; deviceState.classList.add('demo'); }
  else if (state.connected) { label = state.on ? 'Accesa' : 'Spenta'; if (state.on) deviceState.classList.add('on'); }
  deviceState.replaceChildren(dot, document.createTextNode(' ' + label));
  $('bigBrightness').textContent = state.connected && state.on ? state.bri : '—';
  $('bigPercent').hidden = !(state.connected && state.on);
  $('deviceCaption').textContent = !profile ? 'Aggiungi la tua prima luce' : !state.connected ? 'Tocca per collegare la lampadina'
    : state.linkError ? 'Controlla la connessione' : state.on ? lightDescription() : 'Spenta · pronta quando vuoi';
  $('powerText').textContent = !profile ? 'Aggiungi' : !state.connected ? 'Collega' : state.on ? 'Spegni' : 'Accendi';
  $('powerBtn').setAttribute('aria-pressed', String(state.connected && state.on));
  $('powerBtn').setAttribute('aria-label', !profile ? 'Aggiungi una lampadina' : !state.connected ? 'Collega la lampadina' : state.on ? 'Spegni la lampadina' : 'Accendi la lampadina');
  $('refreshBtn').disabled = !state.connected || connecting || refreshing;

  $('brightnessRange').disabled = !state.connected || !state.caps.brightness;
  $('brightnessRange').value = state.bri; $('brightnessRange').style.setProperty('--fill', `${(state.bri - 1) / 99 * 100}%`);
  setOutput('brightnessValue', state.connected ? state.bri : '—', '%');
  $('temperatureRange').min = state.minCt; $('temperatureRange').max = state.maxCt;
  $('temperatureRange').value = clamp(state.ct, state.minCt, state.maxCt);
  $('temperatureRange').disabled = !state.connected || !state.caps.ct; setOutput('temperatureValue', state.connected ? state.ct : '—', state.connected ? 'K' : '');
  $('hueRange').value = state.hue; $('hueRange').disabled = !state.connected || !state.caps.color; setOutput('hueValue', state.hue, '°');
  $('saturationRange').value = state.sat; $('saturationRange').disabled = !state.connected || !state.caps.color; setOutput('saturationValue', state.sat, '%');
  $('customColor').disabled = !state.connected || !state.caps.color;
  const ctAvailable = state.connected && state.caps.ct, colorAvailable = state.connected && state.caps.color;
  $('modeSwitch').hidden = state.connected && !(state.caps.ct && state.caps.color);
  $('whiteTab').disabled = !ctAvailable; $('colorTab').disabled = !colorAvailable;
  $('whiteTab').setAttribute('aria-pressed', String(state.editMode === 'white'));
  $('colorTab').setAttribute('aria-pressed', String(state.editMode === 'color'));
  $('whitePane').hidden = state.connected && (!state.caps.ct || state.editMode !== 'white');
  $('colorPane').hidden = !state.connected || !state.caps.color || state.editMode !== 'color';
  $('noColorSupport').hidden = !state.connected || state.caps.ct || state.caps.color;
  document.querySelectorAll('.white-preset').forEach(button => {
    button.disabled = !ctAvailable;
    button.classList.toggle('active', state.on && state.mode === 'white' && state.ct === clamp(Number(button.dataset.temp), state.minCt, state.maxCt));
  });
  document.querySelectorAll('.color-swatch').forEach(button => {
    button.disabled = !colorAvailable;
    button.classList.toggle('active', state.on && state.mode === 'color' && state.hue === Number(button.dataset.hue) && state.sat === Number(button.dataset.sat));
  });
  document.querySelectorAll('.scene').forEach(button => {
    const scene = scenes[button.dataset.scene];
    button.disabled = !state.connected || !state.caps.brightness || (scene.mode === 'white' ? !state.caps.ct : !state.caps.color);
    button.setAttribute('aria-pressed', String(state.connected && sceneMatches(scene)));
  });
  $('panelHint').textContent = !state.connected ? (profile ? 'Collega una lampadina per iniziare.' : 'Aggiungi una lampadina per iniziare.') : state.demo ? 'Simulazione: nessun comando reale.'
    : !state.on ? 'Regolando la luce, la lampadina si accende.' : state.editMode !== state.mode ? 'Scegli una tonalità per applicarla.' : 'Regola la luce come preferisci.';
}
function applySnapshot(snapshot, followMode = false) {
  if (!snapshot.connected || !snapshot.info || snapshot.device_id !== selectedDeviceId) return;
  const info = snapshot.info;
  state.connected = true; state.demo = Boolean(snapshot.demo); state.linkError = false; state.deviceId = snapshot.device_id;
  state.name = currentDevice()?.name || info.name || 'Lampadina Tapo'; state.model = snapshot.model || 'l530';
  state.ip = snapshot.ip || ''; state.session = snapshot.session || '';
  state.caps = snapshot.caps || {brightness: true, ct: false, color: false}; state.on = Boolean(info.device_on);
  state.bri = clamp(Number(info.brightness) || 100, 1, 100);
  state.minCt = Number(info.color_temp_range?.[0]) || 2500; state.maxCt = Number(info.color_temp_range?.[1]) || 6500;
  state.hue = clamp(Number(info.hue) || 0, 0, 360); state.sat = clamp(Number(info.saturation) || 1, 1, 100);
  if (Number(info.color_temp) > 0 && state.caps.ct) { state.ct = clamp(Number(info.color_temp), state.minCt, state.maxCt); state.mode = 'white'; }
  else if (state.caps.color) state.mode = 'color'; else state.mode = 'white';
  state.ct = clamp(state.ct, state.minCt, state.maxCt);
  if (followMode || (state.editMode === 'white' && !state.caps.ct) || (state.editMode === 'color' && !state.caps.color)) {
    state.editMode = state.caps.ct || state.caps.color ? state.mode : 'white';
  }
  healthyStatus(); render();
}
function resetSelectedState() {
  state.connected = false; state.linkError = false; state.on = false; state.deviceId = selectedDeviceId;
  state.session = ''; state.model = ''; state.ip = ''; state.bri = 72; state.ct = 3200; state.minCt = 2500; state.maxCt = 6500;
  state.hue = 30; state.sat = 85; state.mode = state.editMode = 'white'; state.caps = {brightness: false, ct: false, color: false};
}
async function selectRoom(roomId) {
  if (!house.rooms.some(r => r.id === roomId)) return;
  selectedRoomId = roomId;
  const devices = roomDevices(roomId);
  const remembered = storageGet(`luma-device-${roomId}`);
  const chosen = devices.find(d => d.id === remembered) || devices[0];
  if (chosen) await selectDevice(chosen.id);
  else {
    navigationGeneration++; revision++; selectedDeviceId = ''; resetSelectedState(); saveSelection(); idleStatus(); renderHouse(); render();
  }
}
async function selectDevice(deviceId) {
  const profile = house.devices.find(d => d.id === deviceId);
  if (!profile) return;
  const generation = ++navigationGeneration; revision++;
  selectedRoomId = profile.room_id; selectedDeviceId = deviceId; resetSelectedState(); saveSelection(); renderHouse(); render();
  setConnection('busy', 'Connessione…');
  try {
    const result = await api(`/api/devices/${encodeURIComponent(deviceId)}/activate`, {});
    if (generation !== navigationGeneration || selectedDeviceId !== deviceId) return;
    setHouse(result.house); applySnapshot(result.snapshot, true);
  } catch (error) {
    if (generation !== navigationGeneration || selectedDeviceId !== deviceId) return;
    state.linkError = error.code !== 'password_required';
    setConnection(state.linkError ? 'error' : '', state.linkError ? 'Non raggiungibile' : 'Da collegare'); render();
    openDeviceDialog('edit', error.message);
  }
}

/* Una coda coalescente: i comandi portano SEMPRE id e sessione della lampadina originale. */
function queueCommand(key, path, body, immediate = false) {
  if (!state.connected || !selectedDeviceId) return;
  const deviceId = selectedDeviceId, queueKey = `${deviceId}:${key}`;
  const command = {key: queueKey, path, body: {...body, device_id: deviceId, session: state.session},
                   revision: ++revision, session: state.session, deviceId};
  if (document.documentElement.classList.contains('is-popup')) {
    // L'invio avviene subito; il worker coalesca e completa anche se il popup si chiude.
    popupInFlight++;
    api('/api/queue',{path,body:command.body}).then(result => {
      if (command.deviceId === selectedDeviceId && command.session === state.session && command.revision === revision) applySnapshot(result);
    }).catch(error => toast(error.message, true, 5500)).finally(() => { popupInFlight--; });
    return;
  }
  pending.delete(queueKey); pending.set(queueKey, command);
  if (!sending) {
    if (immediate && sendTimer) { clearTimeout(sendTimer); sendTimer = null; }
    if (!sendTimer) sendTimer = setTimeout(drainQueue, immediate ? 0 : 140);
  }
}
function flushQueue() {
  if (!sending && pending.size) { clearTimeout(sendTimer); sendTimer = setTimeout(drainQueue, 0); }
}
async function drainQueue() {
  clearTimeout(sendTimer); sendTimer = null; if (sending || !pending.size) return;
  sending = true; let mustReconcile = false;
  while (pending.size) {
    const [key, command] = pending.entries().next().value; pending.delete(key);
    const started = Date.now();
    try {
      const result = await api(command.path, command.body);
      if (command.deviceId === selectedDeviceId && command.session === state.session) {
        if (command.revision === revision && pending.size === 0) { applySnapshot(result); mustReconcile = false; }
        else mustReconcile = true;
      }
    } catch (error) {
      const name = house.devices.find(d => d.id === command.deviceId)?.name;
      if (command.deviceId === selectedDeviceId && command.session === state.session) mustReconcile = true;
      toast(name && command.deviceId !== selectedDeviceId ? `${name}: ${error.message}` : error.message, true, 5500);
    }
    if (pending.size) await new Promise(resolve => setTimeout(resolve, Math.max(0, 160 - (Date.now() - started))));
  }
  sending = false;
  if (mustReconcile) await refreshState(false);
}
function discardPending(deviceId) {
  for (const [key, command] of pending) if (command.deviceId === deviceId) pending.delete(key);
}
function setEditMode(mode) {
  if (!state.connected || (mode === 'white' && !state.caps.ct) || (mode === 'color' && !state.caps.color)) return;
  state.editMode = mode; render();
}
function setWhite(value) {
  state.ct = clamp(Math.round(value), state.minCt, state.maxCt); state.mode = state.editMode = 'white'; state.on = true; render();
  queueCommand('ct', '/api/color_temp', {color_temp: state.ct});
}
function setColor(hue, sat) {
  state.hue = clamp(Math.round(hue), 0, 360); state.sat = clamp(Math.round(sat), 1, 100);
  state.mode = state.editMode = 'color'; state.on = true; render();
  queueCommand('color', '/api/color_hsv', {hue: state.hue, saturation: state.sat});
}
function applyScene(name) {
  const scene = scenes[name]; if (!scene || !state.connected) return;
  state.bri = scene.bri; state.on = true; state.mode = state.editMode = scene.mode;
  if (scene.mode === 'white') state.ct = clamp(scene.ct, state.minCt, state.maxCt); else { state.hue = scene.hue; state.sat = scene.sat; }
  render(); queueCommand('brightness', '/api/brightness', {brightness: state.bri});
  if (scene.mode === 'white') queueCommand('ct', '/api/color_temp', {color_temp: state.ct});
  else queueCommand('color', '/api/color_hsv', {hue: state.hue, saturation: state.sat});
  flushQueue();
}
async function refreshState(force = false) {
  if (!state.connected || connecting || refreshing || sending || popupInFlight || pending.size || dragging) return;
  const oldRevision = revision, oldSession = state.session, deviceId = selectedDeviceId;
  refreshing = true; $('refreshBtn').disabled = true;
  try {
    const result = await api(`/api/state?device_id=${encodeURIComponent(deviceId)}`);
    if (deviceId === selectedDeviceId && oldRevision === revision && oldSession === state.session && result.session === state.session && !sending && !popupInFlight && !pending.size) {
      applySnapshot(result); if (force) toast('Stato aggiornato');
    }
  } catch (error) {
    if (deviceId === selectedDeviceId && oldRevision === revision && oldSession === state.session) {
      state.linkError = true; setConnection('error', 'Connessione persa'); render(); if (force) toast(error.message, true, 5500);
    }
  } finally { refreshing = false; render(); }
}

function showFormError(message) { $('formError').textContent = message || ''; $('formError').hidden = !message; }
function resetPasswordField() {
  $('passwordInput').value = ''; $('vaultPassword').value = ''; $('passwordInput').type = 'password'; $('revealBtn').textContent = 'Mostra'; $('revealBtn').setAttribute('aria-pressed', 'false');
}
function populateRoomSelect(chosen) {
  const select = $('deviceRoomSelect'); select.replaceChildren();
  house.rooms.forEach(room => { const option = document.createElement('option'); option.value = room.id; option.textContent = room.name; select.append(option); });
  select.value = chosen || selectedRoomId;
}
function updateCredentialHint(updateCheckbox = false) {
  const email = $('emailInput').value.trim(), account = accountFor(email);
  if (updateCheckbox) $('rememberInput').checked = Boolean(account?.remember_password);
  $('vaultField').hidden = !$('rememberInput').checked;
  if (account?.session_available) {
    $('passwordInput').placeholder = 'Disponibile nella sessione';
    $('credentialHint').textContent = 'Password disponibile per questa sessione di Chrome e per tutte le luci di questo account.';
  } else if (account?.remember_password) {
    $('passwordInput').placeholder = 'Oppure sblocca l’archivio qui sotto';
    $('credentialHint').textContent = 'Inserisci la frase segreta dell’archivio per riutilizzare la password cifrata.';
  } else {
    $('passwordInput').placeholder = formDeviceId ? 'Vuota per cambiare solo nome o stanza' : 'La tua password Tapo';
    $('credentialHint').textContent = 'La password resta solo in memoria durante la sessione, a meno che tu scelga l’archivio cifrato.';
  }
}
function openDeviceDialog(mode = 'add', message = '') {
  // Chrome chiude il popup quando compare la richiesta di permesso: la configurazione reale
  // avviene in una scheda, dove la password e il salvataggio non vanno persi.
  if (document.documentElement.classList.contains('is-popup') && !state.demo) {
    if (message) { toast(`${message} Apri il pannello completo per collegarla.`, true, 10000); return; }
    const args = new URLSearchParams({settings:mode, room:selectedRoomId || ''});
    if (mode === 'edit' && selectedDeviceId) args.set('device',selectedDeviceId);
    chrome.tabs.create({url:chrome.runtime.getURL('index.html') + '?' + args.toString()})
      .then(() => window.top.close()).catch(() => toast('Apri il pannello completo per configurare la lampadina.', true));
    return;
  }
  const profile = mode === 'edit' ? currentDevice() : null;
  formDeviceId = profile?.id || '';
  $('deviceNameInput').value = profile?.name || '';
  populateRoomSelect(profile?.room_id || selectedRoomId);
  const suggested = profile?.email || house.accounts[0]?.email || '';
  $('emailInput').value = suggested; $('ipInput').value = profile?.ip || ''; $('modelSelect').value = profile?.model || 'l530';
  resetPasswordField(); updateCredentialHint(true);
  $('legacyWarning').hidden = state.demo || !house.legacy_password_present;
  $('demoNotice').hidden = !state.demo; $('realFields').hidden = state.demo; $('discoverBtn').hidden = state.demo; $('securityNote').hidden = state.demo;
  $('dialogTitle').textContent = profile ? 'Gestisci la tua luce.' : 'Aggiungi una lampadina.';
  $('deviceDialogEyebrow').textContent = profile ? 'LA TUA LAMPADINA' : 'UNA LUCE IN PIÙ';
  $('dialogDescription').textContent = state.demo ? 'Scegli un nome e una stanza per la tua luce simulata.'
    : profile ? 'Modifica il nome, la stanza o i dati di connessione.' : 'Dai un nome alla luce e collegala al tuo account Tapo.';
  $('connectBtn').querySelector('span').textContent = profile ? 'Salva modifiche' : state.demo ? 'Aggiungi lampadina' : 'Aggiungi e collega';
  $('deleteDeviceBtn').hidden = !profile; $('deviceDeleteConfirm').hidden = true;
  $('discoveryResults').replaceChildren(); $('discoveryResults').hidden = true; showFormError(message);
  if (!$('settingsDialog').open) {
    $('settingsDialog').showModal();
    if (window.matchMedia('(pointer: fine)').matches) (message && !state.demo ? $('passwordInput') : $('deviceNameInput')).focus();
  }
}
function closeDeviceDialog() {
  if (connecting) return;
  if ($('settingsDialog').open) $('settingsDialog').close(); resetPasswordField();
}
function setDeviceBusy(busy) {
  connecting = busy; $('connectBtn').disabled = busy; $('discoverBtn').disabled = busy;
  $('deleteDeviceBtn').disabled = busy; $('confirmDeleteDevice').disabled = busy;
}
function isPrivateIPv4(value) {
  if (!/^(?:0|[1-9]\d{0,2})(?:\.(?:0|[1-9]\d{0,2})){3}$/.test(value)) return false;
  const v = value.split('.').map(Number);
  return !v.some(n => n > 255) && v[3] !== 0 && v[3] !== 255 &&
    (v[0] === 10 || (v[0] === 172 && v[1] >= 16 && v[1] <= 31) || (v[0] === 192 && v[1] === 168) || (v[0] === 169 && v[1] === 254));
}
async function saveDevice() {
  if (connecting) return;
  const name = $('deviceNameInput').value.trim(), roomId = $('deviceRoomSelect').value;
  const email = $('emailInput').value.trim(), password = $('passwordInput').value, ip = $('ipInput').value.trim();
  const model = $('modelSelect').value, remember = $('rememberInput').checked, vaultPassword = $('vaultPassword').value;
  showFormError('');
  if (!name || !roomId) { showFormError('Inserisci il nome della lampadina e scegli una stanza.'); return; }
  if (!state.demo && (!email || !email.includes('@') || !ip)) { showFormError('Inserisci email, indirizzo IP e modello della lampadina.'); return; }
  if (!state.demo && !isPrivateIPv4(ip)) { showFormError('Inserisci un IPv4 privato, ad esempio 192.168.1.20.'); return; }
  const account = accountFor(email);
  if (!state.demo && !formDeviceId && !password && !account?.session_available && !(account?.remember_password && vaultPassword)) {
    showFormError('Inserisci la password Tapo o sblocca l’archivio cifrato.'); return;
  }
  const oldSelectedId = selectedDeviceId, oldSession = state.session, editingId = formDeviceId;
  setDeviceBusy(true); $('connectBtn').querySelector('span').textContent = 'Salvataggio…';
  try {
    // A per-IP optional permission is requested from this user gesture, not for the whole web.
    if (!state.demo) {
      const granted = await chrome.permissions.request({origins:[`http://${ip}/*`]});
      if (!granted) { const e = new Error('Consenti l’accesso all’IP della lampadina per poterla collegare.'); e.code = 'permission_required'; throw e; }
    }
    const body = {name, room_id: roomId, email, password, ip, model, remember_password: remember, vault_password: vaultPassword};
    const path = editingId ? `/api/devices/${encodeURIComponent(editingId)}/update` : '/api/devices';
    const result = await api(path, body);
    navigationGeneration++; revision++;
    setHouse(result.house); selectedDeviceId = result.snapshot.device_id; selectedRoomId = roomId; saveSelection(); renderHouse();
    if (editingId && oldSelectedId === editingId && result.snapshot.connected && oldSession !== result.snapshot.session) discardPending(editingId);
    if (result.snapshot.connected) applySnapshot(result.snapshot, true);
    else { resetSelectedState(); idleStatus(); render(); }
    setDeviceBusy(false); closeDeviceDialog();
    if (result.warnings?.length) toast(result.warnings.join(' '), true, 10000);
    else toast(editingId ? 'Lampadina aggiornata.' : 'La tua luce è stata aggiunta.');
  } catch (error) { showFormError(error.message); }
  finally {
    setDeviceBusy(false);
    $('connectBtn').querySelector('span').textContent = editingId ? 'Salva modifiche' : state.demo ? 'Aggiungi lampadina' : 'Aggiungi e collega';
    render();
  }
}
async function deleteDevice() {
  if (!formDeviceId || connecting) return;
  const deviceId = formDeviceId; setDeviceBusy(true);
  try {
    const result = await api(`/api/devices/${encodeURIComponent(deviceId)}/delete`, {});
    navigationGeneration++; revision++; discardPending(deviceId); setHouse(result.house);
    const next = roomDevices(selectedRoomId)[0]; selectedDeviceId = ''; resetSelectedState();
    setDeviceBusy(false); closeDeviceDialog();
    if (next) await selectDevice(next.id); else { idleStatus(); saveSelection(); renderHouse(); render(); }
    if (result.warnings?.length) toast(result.warnings.join(' '), true, 9000); else toast('Lampadina rimossa da Luma.');
  } catch (error) { showFormError(error.message); }
  finally { setDeviceBusy(false); render(); }
}
function discoverDevice() {
  const box = $('discoveryResults'); box.replaceChildren();
  const hint = document.createElement('p');
  hint.textContent = 'La ricerca automatica non è disponibile nelle estensioni Chrome. Nell’app Tapo apri la lampadina, poi Impostazioni → Informazioni dispositivo, e copia il suo indirizzo IP.';
  box.append(hint); box.hidden = false;
}

function showRoomError(message) { $('roomFormError').textContent = message || ''; $('roomFormError').hidden = !message; }
function openRoomDialog(mode) {
  const room = mode === 'edit' ? currentRoom() : null; roomFormId = room?.id || '';
  $('roomNameInput').value = room?.name || '';
  $('roomDialogTitle').textContent = room ? 'Gestisci la stanza.' : 'Nuova stanza.';
  $('roomDialogDescription').textContent = room ? 'Rinomina o elimina questa stanza.' : 'Organizza le tue luci nel modo che preferisci.';
  $('saveRoomBtn').querySelector('span').textContent = room ? 'Salva modifiche' : 'Crea stanza';
  const count = room ? roomDevices(room.id).length : 0;
  $('deleteRoomBtn').hidden = !room; $('deleteRoomBtn').disabled = Boolean(room && (count || house.rooms.length <= 1));
  $('roomDeleteHint').hidden = !room || !(count || house.rooms.length <= 1);
  $('roomDeleteHint').textContent = count ? 'Per eliminare la stanza, sposta o rimuovi prima le sue lampadine.' : 'Mantieni almeno una stanza nella tua casa.';
  $('roomDeleteConfirm').hidden = true; showRoomError('');
  if (!$('roomDialog').open) { $('roomDialog').showModal(); if (window.matchMedia('(pointer: fine)').matches) $('roomNameInput').focus(); }
}
async function saveRoom() {
  const name = $('roomNameInput').value.trim(); if (!name) { showRoomError('Inserisci il nome della stanza.'); return; }
  const editing = roomFormId; $('saveRoomBtn').disabled = true; showRoomError('');
  try {
    const result = await api(editing ? `/api/rooms/${encodeURIComponent(editing)}/update` : '/api/rooms', {name});
    setHouse(result.house); $('roomDialog').close();
    if (!editing) await selectRoom(result.room_id); else { renderHouse(); render(); }
    toast(editing ? 'Stanza aggiornata.' : 'Stanza creata. Aggiungi una luce quando vuoi.');
  } catch (error) { showRoomError(error.message); }
  finally { $('saveRoomBtn').disabled = false; }
}
async function deleteRoom() {
  if (!roomFormId) return;
  $('confirmDeleteRoom').disabled = true; showRoomError('');
  try {
    const result = await api(`/api/rooms/${encodeURIComponent(roomFormId)}/delete`, {});
    setHouse(result.house); $('roomDialog').close();
    await selectRoom(house.rooms[0].id); toast('Stanza eliminata.');
  } catch (error) { showRoomError(error.message); }
  finally { $('confirmDeleteRoom').disabled = false; }
}

function renderMode() {
  $('modeDescription').textContent = state.demo ? 'MODALITÀ DEMO · LUCI SIMULATE' : 'ESTENSIONE · SOLO RETE LOCALE';
  $('demoToggle').textContent = state.demo ? 'Esci dalla demo' : 'Prova la demo';
  $('exportBtn').disabled = state.demo; $('exportBtn').title = state.demo ? 'Disponibile nella modalità reale' : 'Esporta senza password';
}
async function switchMode() {
  if (connecting || sending || popupInFlight || pending.size) { toast('Attendi la fine dei comandi prima di cambiare modalità.', true); return; }
  $('demoToggle').disabled = true;
  try {
    const result = await api('/api/mode', {demo:!state.demo});
    navigationGeneration++; revision++; state.demo = Boolean(result.demo); setHouse(result.house); renderMode();
    const scope = state.demo ? 'demo' : 'real';
    const remembered = house.devices.find(d => d.id === storageGet(`luma-device-${scope}`));
    const room = house.rooms.find(r => r.id === storageGet(`luma-room-${scope}`));
    selectedRoomId = remembered?.room_id || room?.id || house.rooms[0]?.id || '';
    selectedDeviceId = remembered?.id || roomDevices(selectedRoomId)[0]?.id || '';
    resetSelectedState(); saveSelection(); renderHouse(); render();
    if (selectedDeviceId) await selectDevice(selectedDeviceId); else idleStatus();
    if (!selectedDeviceId || state.connected) toast(state.demo ? 'Demo attiva: le luci sono simulate.' : 'Modalità reale attiva.');
  } catch (error) { toast(error.message, true); }
  finally { $('demoToggle').disabled = false; }
}
let pendingImport = null;
$('demoToggle').addEventListener('click', switchMode);
$('openFullBtn').hidden = !new URLSearchParams(location.search).has('popup');
$('openFullBtn').addEventListener('click', async () => { await chrome.tabs.create({url:chrome.runtime.getURL('index.html')}); window.top.close(); });
$('importBtn').addEventListener('click', () => {
  if (state.demo) { toast('Esci dalla demo prima di importare la casa reale.', true); return; }
  // Anche la scelta del file può chiudere il popup di Chrome: usa una scheda stabile.
  if (document.documentElement.classList.contains('is-popup')) {
    chrome.tabs.create({url:chrome.runtime.getURL('index.html') + '?backup=import'})
      .then(() => window.top.close()).catch(() => toast('Apri il pannello completo per importare.', true));
    return;
  }
  $('importFile').value = ''; $('importFile').click();
});
$('importFile').addEventListener('change', async event => {
  const file = event.target.files?.[0]; if (!file) return;
  if (file.size > 1024 * 1024) { toast('Il file è troppo grande.', true); return; }
  try { pendingImport = JSON.parse(await file.text()); $('importError').hidden = true; $('importDialog').showModal(); }
  catch (_) { toast('Il file non è un JSON valido.', true); }
});
$('cancelImport').addEventListener('click', () => { pendingImport = null; $('importDialog').close(); });
$('cancelImportAction').addEventListener('click', () => { pendingImport = null; $('importDialog').close(); });
$('importDialog').addEventListener('close', () => { pendingImport = null; $('importFile').value = ''; });
$('confirmImport').addEventListener('click', async () => {
  if (!pendingImport || state.demo) return;
  if (sending || pending.size) { $('importError').hidden = false; $('importError').textContent = 'Attendi la fine dei comandi in corso.'; return; }
  $('confirmImport').disabled = true;
  try {
    const result = await api('/api/import', {house:pendingImport}); pendingImport = null; $('importDialog').close();
    navigationGeneration++; revision++; setHouse(result.house); selectedRoomId = house.rooms[0]?.id || ''; selectedDeviceId = '';
    resetSelectedState(); saveSelection(); idleStatus(); renderHouse(); render();
    toast('Casa importata. Seleziona una lampadina per collegarla.');
  } catch (error) { $('importError').textContent = error.message; $('importError').hidden = false; }
  finally { $('confirmImport').disabled = false; }
});
$('exportBtn').addEventListener('click', async () => {
  try {
    const result = await api('/api/export');
    const url = URL.createObjectURL(new Blob([JSON.stringify(result.data, null, 2)], {type:'application/json'}));
    const a = document.createElement('a'); a.href = url; a.download = state.demo ? 'luma-demo-backup.json' : 'luma-casa-backup.json'; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 15000); toast('Backup esportato senza password.');
  } catch (error) { toast(error.message, true); }
});

document.querySelector('.brand').addEventListener('click', event => {
  if (document.documentElement.classList.contains('is-popup')) { event.preventDefault(); window.scrollTo({top:0,behavior:'smooth'}); }
});
$('themeToggle').addEventListener('click', () => applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'));
$('settingsBtn').addEventListener('click', () => openDeviceDialog(currentDevice() ? 'edit' : 'add'));
$('editDeviceBtn').addEventListener('click', () => openDeviceDialog('edit'));
$('addDeviceBtn').addEventListener('click', () => openDeviceDialog('add'));
$('closeDialog').addEventListener('click', closeDeviceDialog);
$('settingsDialog').addEventListener('cancel', event => { if (connecting) event.preventDefault(); });
$('settingsDialog').addEventListener('close', resetPasswordField);
$('connectionForm').addEventListener('submit', event => { event.preventDefault(); saveDevice(); });
$('discoverBtn').addEventListener('click', discoverDevice);
$('emailInput').addEventListener('change', () => updateCredentialHint(true));
$('rememberInput').addEventListener('change', () => updateCredentialHint(false));
$('revealBtn').addEventListener('click', () => {
  const input = $('passwordInput'), show = input.type === 'password'; input.type = show ? 'text' : 'password';
  $('revealBtn').textContent = show ? 'Nascondi' : 'Mostra'; $('revealBtn').setAttribute('aria-pressed', String(show));
});
$('deleteDeviceBtn').addEventListener('click', () => { $('deviceDeleteConfirm').hidden = false; });
$('cancelDeleteDevice').addEventListener('click', () => { $('deviceDeleteConfirm').hidden = true; });
$('confirmDeleteDevice').addEventListener('click', deleteDevice);
$('addRoomBtn').addEventListener('click', () => openRoomDialog('add'));
$('editRoomBtn').addEventListener('click', () => openRoomDialog('edit'));
$('closeRoomDialog').addEventListener('click', () => $('roomDialog').close());
$('roomForm').addEventListener('submit', event => { event.preventDefault(); saveRoom(); });
$('deleteRoomBtn').addEventListener('click', () => { $('roomDeleteConfirm').hidden = false; });
$('cancelDeleteRoom').addEventListener('click', () => { $('roomDeleteConfirm').hidden = true; });
$('confirmDeleteRoom').addEventListener('click', deleteRoom);
$('refreshBtn').addEventListener('click', () => refreshState(true));
$('powerBtn').addEventListener('click', () => {
  if (!currentDevice()) { openDeviceDialog('add'); return; }
  if (!state.connected) { selectDevice(selectedDeviceId); return; }
  state.on = !state.on; render(); queueCommand('power', '/api/power', {state: state.on}, true);
});
$('whiteTab').addEventListener('click', () => setEditMode('white'));
$('colorTab').addEventListener('click', () => setEditMode('color'));
$('brightnessRange').addEventListener('input', event => {
  state.bri = Number(event.target.value); state.on = true; render(); queueCommand('brightness', '/api/brightness', {brightness: state.bri});
});
$('temperatureRange').addEventListener('input', event => setWhite(Number(event.target.value)));
$('hueRange').addEventListener('input', event => setColor(Number(event.target.value), state.sat));
$('saturationRange').addEventListener('input', event => setColor(state.hue, Number(event.target.value)));
document.querySelectorAll('.range').forEach(input => {
  input.addEventListener('pointerdown', () => { dragging = true; });
  input.addEventListener('change', () => { dragging = false; flushQueue(); });
  input.addEventListener('blur', () => { dragging = false; flushQueue(); });
});
document.addEventListener('pointerup', () => { if (dragging) { dragging = false; flushQueue(); } });
document.addEventListener('pointercancel', () => { dragging = false; flushQueue(); });
document.querySelectorAll('.white-preset').forEach(button => button.addEventListener('click', () => { setWhite(Number(button.dataset.temp)); flushQueue(); }));
document.querySelectorAll('.color-swatch').forEach(button => button.addEventListener('click', () => { setColor(Number(button.dataset.hue), Number(button.dataset.sat)); flushQueue(); }));
$('customColor').addEventListener('input', event => { const color = hexToHsv(event.target.value); setColor(color.hue, color.sat); });
$('customColor').addEventListener('change', flushQueue);
document.querySelectorAll('.scene').forEach(button => button.addEventListener('click', () => applyScene(button.dataset.scene)));
// Il popup può chiudersi subito dopo un gesto. Consegna al service worker i comandi
// ancora in coda: sarà lui a serializzarli anche se questa pagina scompare.
function dispatchPendingBeforeHide() {
  if (!pending.size) return;
  clearTimeout(sendTimer); sendTimer = null;
  const outgoing = [...pending.values()]; pending.clear();
  for (const command of outgoing) {
    try { chrome.runtime.sendMessage({kind:'luma-api',path:command.path,body:command.body,demo:state.demo}).catch(() => {}); }
    catch (_) { /* La pagina sta venendo chiusa. */ }
  }
}
document.addEventListener('visibilitychange', () => { if (document.hidden) dispatchPendingBeforeHide(); else refreshState(); });
window.addEventListener('pagehide', dispatchPendingBeforeHide);
setInterval(() => { if (!document.hidden) refreshState(); }, 30000);

(async function start() {
  applyTheme(document.documentElement.dataset.theme || 'light', false); render();
  try {
    const result = await api('/api/bootstrap'); state.demo = Boolean(result.demo); setHouse(result.house); renderMode();
    const scope = state.demo ? 'demo' : 'real', args = new URLSearchParams(location.search);
    const requestedDevice = args.get('settings') === 'edit' ? house.devices.find(d => d.id === args.get('device')) : null;
    const requestedRoom = args.get('settings') === 'add' ? house.rooms.find(r => r.id === args.get('room')) : null;
    const savedDevice = house.devices.find(d => d.id === storageGet(`luma-device-${scope}`));
    const savedRoom = house.rooms.find(r => r.id === storageGet(`luma-room-${scope}`));
    selectedRoomId = requestedDevice?.room_id || requestedRoom?.id || savedDevice?.room_id || savedRoom?.id || house.rooms[0]?.id || '';
    selectedDeviceId = args.get('backup') === 'import' ? '' : (requestedDevice?.id || (requestedRoom ? roomDevices(requestedRoom.id)[0]?.id : savedDevice?.id) || roomDevices(selectedRoomId)[0]?.id || '');
    renderHouse();
    if (selectedDeviceId) await selectDevice(selectedDeviceId);
    else { idleStatus(); render(); }
    if (args.get('settings') === 'add') openDeviceDialog('add');
    else if (requestedDevice && !$('settingsDialog').open) openDeviceDialog('edit');
    else if (args.get('backup') === 'import' && !state.demo) { $('importBtn').scrollIntoView({block:'center'}); $('importBtn').focus(); toast('Seleziona Importa casa 2.1 per scegliere il file.'); }
  } catch (error) { setConnection('error', 'Estensione non disponibile'); toast(error.message, true, 7000); }
})();
