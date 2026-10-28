// Local extension service. No Python, cloud API, telemetry or remotely hosted code.
import { connectTapo, LumaError, b64, from64, concat } from './protocol.js';

const MODELS = new Set(['l510','l520','l530','l535','l610','l630','l900','l920','l930']);
const WHITE_ONLY = new Set(['l510','l520','l610']);
const enc = new TextEncoder();
const dec = new TextDecoder();
const records = new Map(), deviceLocks = new Map(), backgroundQueues = new Map();
let houseQueue = Promise.resolve(), sessionQueue = Promise.resolve();
const initializations = new Map();
const norm = value => String(value || '').trim().toLowerCase();
const uid = () => crypto.randomUUID();
const clone = obj => structuredClone(obj);
const idKey = (demo, id) => `${demo ? 'demo' : 'real'}:${id}`;
const houseKey = demo => demo ? 'luma-house-demo' : 'luma-house-real';
const error = (message, code = 'error') => { throw new LumaError(message, code); };

function serial(previous, set, task) {
  const current = previous.catch(() => {}).then(task);
  set(current); return current;
}
function withHouse(task) { return serial(houseQueue, p => { houseQueue = p; }, task); }
function withSession(task) { return serial(sessionQueue, p => { sessionQueue = p; }, task); }
async function withDevice(key, task) {
  const run = (deviceLocks.get(key) || Promise.resolve()).catch(() => {}).then(task);
  deviceLocks.set(key, run);
  try { return await run; } finally { if (deviceLocks.get(key) === run) deviceLocks.delete(key); }
}
function demoHouse() {
  return {schema:1, rooms:[{id:'demo-soggiorno',name:'Soggiorno'},{id:'demo-camera',name:'Camera'}], devices:[
    {id:'demo-lampada',room_id:'demo-soggiorno',name:'Lampadina Tapo',email:'',ip:'',model:'l530'},
    {id:'demo-ambiente',room_id:'demo-soggiorno',name:'Luce ambiente',email:'',ip:'',model:'l530'},
    {id:'demo-camera-luce',room_id:'demo-camera',name:'Luce camera',email:'',ip:'',model:'l530'},
  ], accounts:[]};
}
function emptyHouse() { return {schema:1, rooms:[{id:uid(),name:'Casa'}], devices:[], accounts:[]}; }
async function loadHouse(demo) {
  const key = houseKey(demo);
  if (!initializations.has(key)) {
    initializations.set(key, (async () => {
      const item = await chrome.storage.local.get(key);
      if (!item[key]) await chrome.storage.local.set({[key]: demo ? demoHouse() : emptyHouse()});
    })().catch(err => { initializations.delete(key); throw err; }));
  }
  await initializations.get(key);
  const item = await chrome.storage.local.get(key);
  if (!item[key] || !Array.isArray(item[key].rooms) || !Array.isArray(item[key].devices)) error('Dati della casa non validi. Ripristina un backup.', 'storage');
  return item[key];
}
async function saveHouse(demo, house) { await chrome.storage.local.set({[houseKey(demo)]: house}); }
async function readSession() {
  const {lumaRuntime = {}} = await chrome.storage.session.get('lumaRuntime');
  return {passwords:lumaRuntime.passwords || {}, tokens:lumaRuntime.tokens || {}, demoStates:lumaRuntime.demoStates || {}};
}
function updateSession(task) {
  return withSession(async () => { const bag = await readSession(); const value = await task(bag); await chrome.storage.session.set({lumaRuntime: bag}); return value; });
}
async function getToken(demo, id, rotate = false) {
  return updateSession(bag => {
    const key = idKey(demo,id);
    if (!bag.tokens[key] || rotate) bag.tokens[key] = uid();
    return bag.tokens[key];
  });
}
async function currentToken(demo,id) { return (await readSession()).tokens[idKey(demo,id)] || ''; }
function roomFor(house,id) { return house.rooms.find(r => r.id === id); }
function deviceFor(house,id) { return house.devices.find(d => d.id === id); }
function accountFor(house,email) { return house.accounts.find(a => norm(a.email) === norm(email)); }
function cleanLabel(value, what, max) {
  if (typeof value !== 'string') error(`Inserisci un nome valido per ${what}.`, 'validation');
  const s = value.trim();
  if (!s || [...s].length > max || /[\u0000-\u001f\u007f]/.test(s)) error(`Il nome di ${what} deve contenere da 1 a ${max} caratteri.`, 'validation');
  return s;
}
function validEmail(value) {
  if (typeof value !== 'string' || !value.trim() || value.length > 254 || !value.includes('@') || /\s/.test(value)) error('Inserisci l’email del tuo account Tapo.', 'validation');
  return value.trim();
}
export function isPrivateIPv4(value) {
  if (typeof value !== 'string' || !/^(?:0|[1-9]\d{0,2})(?:\.(?:0|[1-9]\d{0,2})){3}$/.test(value)) return false;
  const v = value.split('.').map(Number); if (v.some(n => n > 255)) return false;
  if (v[0] === 10 || (v[0] === 172 && v[1] >= 16 && v[1] <= 31) || (v[0] === 192 && v[1] === 168) || (v[0] === 169 && v[1] === 254)) {
    return v[3] !== 0 && v[3] !== 255;
  }
  return false;
}
function validIp(value) {
  const ip = typeof value === 'string' ? value.trim() : '';
  if (!isPrivateIPv4(ip)) error('Inserisci l’IPv4 privato della lampadina, ad esempio 192.168.1.20.', 'validation');
  return ip;
}
function validModel(value) { const m = String(value || '').toLowerCase(); if (!MODELS.has(m)) error('Seleziona un modello Tapo supportato.', 'validation'); return m; }
function requireRoom(house,id) { if (!roomFor(house,id)) error('La stanza non esiste più.', 'not_found'); }
function requireDevice(house,id) { const d = deviceFor(house,id); if (!d) error('La lampadina non esiste più.', 'not_found'); return d; }
async function ensurePermission(ip) {
  const has = await chrome.permissions.contains({origins:[`http://${ip}/*`]});
  if (!has) error('Consenti a Chrome di accedere all’IP della lampadina e premi Salva.', 'permission_required');
}
function caps(model, demo) { return {brightness:true,ct:demo || !WHITE_ONLY.has(model),color:demo || !WHITE_ONLY.has(model)}; }
function clampInt(v,min,max,def) { const n = Number(v); return Number.isFinite(n) ? Math.max(min,Math.min(max,Math.round(n))) : def; }
function normalizeInfo(raw, profile) {
  if (!raw || typeof raw !== 'object') error('Risposta della lampadina non valida.', 'protocol');
  const range = Array.isArray(raw.color_temp_range) && raw.color_temp_range.length === 2 &&
      Number(raw.color_temp_range[0]) >= 1000 && Number(raw.color_temp_range[1]) <= 10000 && Number(raw.color_temp_range[0]) < Number(raw.color_temp_range[1])
      ? [Number(raw.color_temp_range[0]),Number(raw.color_temp_range[1])] : [2500,6500];
  return {name:profile.name, model:String(raw.model || profile.model).slice(0,50), device_on: Boolean(raw.device_on),
    brightness:clampInt(raw.brightness,1,100,100), color_temp:clampInt(raw.color_temp,0,10000,0),
    color_temp_range:range, hue:clampInt(raw.hue,0,360,0), saturation:clampInt(raw.saturation,0,100,100)};
}
function baseDemo(profile) {
  const value = {name:profile.name,model:'L530',device_on:true,brightness:72,color_temp:3200,color_temp_range:[2500,6500],hue:30,saturation:85};
  if (profile.id === 'demo-ambiente') Object.assign(value,{brightness:56,color_temp:0,hue:220,saturation:65});
  if (profile.id === 'demo-camera-luce') Object.assign(value,{device_on:false,brightness:40,color_temp:2700});
  return value;
}
async function publicHouse(house, demo) {
  const bag = await readSession();
  return {schema:1, rooms:clone(house.rooms), devices:clone(house.devices), legacy_password_present:false,
    accounts: demo ? [] : house.accounts.map(a => ({email:a.email,remember_password:Boolean(a.vault),session_available:Boolean(bag.passwords[norm(a.email)])}))};
}
function snapshot(profile, demo, info, token) {
  return {connected:true,demo,device_id:profile.id,info:clone(info),caps:caps(profile.model,demo),model:profile.model,ip:profile.ip,session:token};
}
function fingerprint(profile) { return `${norm(profile.email)}|${profile.ip}|${profile.model}`; }
async function readReal(record, profile) {
  const raw = await record.protocol.request('get_device_info', null);
  record.info = normalizeInfo(raw,profile); return record.info;
}
async function newReal(profile, password) {
  await ensurePermission(profile.ip);
  if (!password) error('Inserisci la password Tapo o sblocca il tuo archivio cifrato.', 'password_required');
  const protocol = await connectTapo(profile.ip, profile.email, password);
  const record = {protocol,fingerprint:fingerprint(profile),info:null};
  await readReal(record, profile); return record;
}
async function ensureReal(profile) {
  const key = idKey(false,profile.id), current = records.get(key);
  if (current && current.fingerprint === fingerprint(profile)) return current;
  const bag = await readSession(); const secret = bag.passwords[norm(profile.email)];
  const next = await newReal(profile, secret); records.set(key,next); return next;
}
async function liveSnapshot(profile, demo) {
  const token = await getToken(demo,profile.id);
  if (demo) {
    const info = await updateSession(bag => {
      const existing = bag.demoStates[profile.id] || baseDemo(profile); existing.name = profile.name;
      bag.demoStates[profile.id] = existing; return clone(existing);
    });
    return snapshot(profile,true,info,token);
  }
  const record = await ensureReal(profile);
  try { await readReal(record,profile); }
  catch (err) {
    if (err.code !== 'session_expired') throw err;
    records.delete(idKey(false,profile.id));
    const replacement = await ensureReal(profile); return snapshot(profile,false,replacement.info,token);
  }
  return snapshot(profile,false,record.info,token);
}

// Optional persistent secret: PBKDF2-SHA256 + AES-256-GCM, passphrase never stored.
async function vaultKey(passphrase, salt, iterations) {
  const input = await crypto.subtle.importKey('raw', enc.encode(passphrase), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({name:'PBKDF2',hash:'SHA-256',salt,iterations}, input, {name:'AES-GCM',length:256}, false, ['encrypt','decrypt']);
}
async function sealPassword(email, password, passphrase) {
  if (typeof passphrase !== 'string' || passphrase.length < 12 || !passphrase.trim()) error('Per salvare la password, scegli una frase segreta di almeno 12 caratteri.', 'vault');
  const salt = crypto.getRandomValues(new Uint8Array(16)), iv = crypto.getRandomValues(new Uint8Array(12)), iterations = 310000;
  const key = await vaultKey(passphrase,salt,iterations);
  const cipher = await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:enc.encode(`luma-vault-v1|${norm(email)}`)}, key, enc.encode(password));
  return {version:1,iterations,salt:b64(salt),iv:b64(iv),ciphertext:b64(new Uint8Array(cipher))};
}
async function openVault(email, vault, passphrase) {
  if (!passphrase) error('Inserisci la frase segreta per sbloccare la password salvata.', 'password_required');
  try {
    if (vault.version !== 1 || !Number.isInteger(vault.iterations) || vault.iterations < 100000 || vault.iterations > 1000000) throw new Error('vault format');
    const salt = from64(vault.salt), iv = from64(vault.iv), cipher = from64(vault.ciphertext);
    if (salt.length !== 16 || iv.length !== 12 || cipher.length < 16 || cipher.length > 4096) throw new Error('vault size');
    const key = await vaultKey(passphrase,salt,vault.iterations);
    const data = await crypto.subtle.decrypt({name:'AES-GCM',iv,additionalData:enc.encode(`luma-vault-v1|${norm(email)}`)}, key, cipher);
    return dec.decode(data);
  } catch (_) { error('Frase segreta errata o archivio cifrato non valido.', 'vault'); }
}

async function bootstrap() {
  const {lumaDemoMode = false} = await chrome.storage.local.get('lumaDemoMode');
  const demo = lumaDemoMode === true, house = await loadHouse(demo);
  return {demo,house:await publicHouse(house,demo)};
}
async function saveRoom(body,demo,id) {
  return withHouse(async () => {
    const house = await loadHouse(demo), name = cleanLabel(body?.name,'la stanza',50);
    if (house.rooms.some(r => r.id !== id && norm(r.name) === norm(name))) error('Esiste già una stanza con questo nome.', 'duplicate');
    if (id) { requireRoom(house,id); roomFor(house,id).name = name; }
    else { if (house.rooms.length >= 50) error('Hai raggiunto il limite di 50 stanze.', 'limit'); id = uid(); house.rooms.push({id,name}); }
    await saveHouse(demo,house); return {room_id:id,house:await publicHouse(house,demo)};
  });
}
async function deleteRoom(demo,id) {
  return withHouse(async () => {
    const house = await loadHouse(demo); requireRoom(house,id);
    if (house.devices.some(d => d.room_id === id)) error('Sposta o rimuovi prima le lampadine della stanza.', 'room_not_empty');
    if (house.rooms.length <= 1) error('Mantieni almeno una stanza.', 'last_room');
    house.rooms = house.rooms.filter(r => r.id !== id); await saveHouse(demo,house);
    return {house:await publicHouse(house,demo)};
  });
}
async function saveDevice(body,demo,id) {
  return withHouse(async () => {
    const house = await loadHouse(demo), old = id ? requireDevice(house,id) : null, previousIp = old?.ip || '';
    if (!old && house.devices.length >= 100) error('Hai raggiunto il limite di 100 lampadine.', 'limit');
    const name = cleanLabel(body?.name,'la lampadina',60), room_id = String(body?.room_id || ''); requireRoom(house,room_id);
    const model = demo ? 'l530' : validModel(body?.model), emailInput = demo ? '' : validEmail(body?.email), ip = demo ? '' : validIp(body?.ip);
    if (!demo && house.devices.some(d => d.id !== id && d.ip === ip)) error('Questo IP è già associato a un’altra lampadina.', 'duplicate');
    const account = demo ? null : accountFor(house,emailInput);
    const email = account?.email || emailInput;
    const profile = {id:id || uid(), room_id, name, email, ip, model}; id = profile.id;
    return withDevice(idKey(demo,id), async () => {
      let secret = '', vault = account?.vault || null, fresh = null;
      const given = demo ? '' : String(body?.password || ''), vaultPass = demo ? '' : String(body?.vault_password || '');
      if (!demo && given.length > 1024) error('Password troppo lunga.', 'validation');
      const bag = await readSession();
      if (!demo) {
        secret = given || bag.passwords[norm(email)] || '';
        if (!secret && vault && vaultPass) secret = await openVault(email,vault,vaultPass);
        const changed = !old || fingerprint(old) !== fingerprint(profile);
        const shouldConnect = changed || Boolean(given) || Boolean(!bag.passwords[norm(email)] && vaultPass);
        if (shouldConnect && !secret) error('Inserisci la password Tapo o la frase segreta dell’archivio.', 'password_required');
        if (body?.remember_password === true) {
          if (!vault || given) {
            if (!secret) error('Inserisci la password Tapo per creare l’archivio cifrato.', 'password_required');
            vault = await sealPassword(email,secret,vaultPass);
          }
        } else vault = null;
        if (shouldConnect) fresh = await newReal(profile,secret); // no settings saved if connection fails
      }
      if (old) Object.assign(old,profile); else house.devices.push(profile);
      if (!demo) {
        let target = accountFor(house,email);
        if (!target) { target = {email,vault:null}; house.accounts.push(target); }
        target.vault = vault;
        // old was just updated, so identify an unused account from the complete device list.
        const used = new Set(house.devices.map(d => norm(d.email)));
        const removed = house.accounts.filter(a => !used.has(norm(a.email))).map(a => norm(a.email));
        house.accounts = house.accounts.filter(a => used.has(norm(a.email)));
        await saveHouse(demo,house);
        await updateSession(b => { if (secret) b.passwords[norm(email)] = secret; for (const key of removed) delete b.passwords[key]; });
        if (previousIp && previousIp !== ip) { try { await chrome.permissions.remove({origins:[`http://${previousIp}/*`]}); } catch (_) {} }
      } else await saveHouse(demo,house);
      let snap;
      if (demo) { await getToken(true,id); snap = await liveSnapshot(profile,true); }
      else if (fresh) {
        records.set(idKey(false,id),fresh); const token = await getToken(false,id,true);
        snap = snapshot(profile,false,fresh.info,token);
      } else {
        const current = records.get(idKey(false,id));
        snap = current && current.fingerprint === fingerprint(profile)
          ? snapshot(profile,false,current.info,await getToken(false,id))
          : {connected:false,demo:false,device_id:id};
      }
      return {house:await publicHouse(house,demo),snapshot:snap,warnings:[]};
    });
  });
}
async function activateDevice(demo,id) {
  return withDevice(idKey(demo,id), async () => {
    const house = await loadHouse(demo), profile = requireDevice(house,id);
    return {house:await publicHouse(house,demo),snapshot:await liveSnapshot(profile,demo)};
  });
}
async function deleteDevice(demo,id) {
  return withHouse(async () => {
    const house = await loadHouse(demo), profile = requireDevice(house,id);
    return withDevice(idKey(demo,id), async () => {
      house.devices = house.devices.filter(d => d.id !== id);
      const deleteSecret = !demo && !house.devices.some(d => norm(d.email) === norm(profile.email));
      if (deleteSecret) house.accounts = house.accounts.filter(a => norm(a.email) !== norm(profile.email));
      await saveHouse(demo,house); records.delete(idKey(demo,id));
      await updateSession(bag => { delete bag.tokens[idKey(demo,id)]; if (demo) delete bag.demoStates[id]; if (deleteSecret) delete bag.passwords[norm(profile.email)]; });
      if (!demo) { try { await chrome.permissions.remove({origins:[`http://${profile.ip}/*`]}); } catch (_) {} }
      return {house:await publicHouse(house,demo)};
    });
  });
}
function integer(value,min,max,label) {
  if (!Number.isInteger(value) || value < min || value > max) error(`${label} non valida.`, 'validation'); return value;
}
async function deviceState(demo,id) {
  return withDevice(idKey(demo,id), async () => {
    const house = await loadHouse(demo), profile = requireDevice(house,id);
    return {...await liveSnapshot(profile,demo)};
  });
}
async function command(path, body, demo) {
  if (!body || typeof body !== 'object' || typeof body.device_id !== 'string') error('Seleziona una lampadina.', 'validation');
  const id = body.device_id;
  return withDevice(idKey(demo,id), async () => {
    const house = await loadHouse(demo), profile = requireDevice(house,id), token = await currentToken(demo,id);
    if (!token || body.session !== token) error('La sessione della lampadina è cambiata. Selezionala di nuovo.', 'stale_session');
    const available = caps(profile.model,demo); let params;
    if (path === '/api/power') {
      if (typeof body.state !== 'boolean') error('Comando di accensione non valido.', 'validation');
      params = {device_on:body.state};
    } else if (path === '/api/brightness') params = {device_on:true,brightness:integer(body.brightness,1,100,'Luminosità')};
    else if (path === '/api/color_temp') {
      if (!available.ct) error('Questo modello non supporta la temperatura.', 'unsupported');
      params = {device_on:true,color_temp:integer(body.color_temp,1000,10000,'Temperatura')};
    } else {
      if (!available.color) error('Questo modello non supporta il colore.', 'unsupported');
      params = {device_on:true,hue:integer(body.hue,0,360,'Tonalità'),saturation:integer(body.saturation,1,100,'Saturazione'),color_temp:0};
    }
    if (demo) {
      const info = await updateSession(bag => {
        const value = bag.demoStates[id] || baseDemo(profile); Object.assign(value,params); value.name = profile.name;
        bag.demoStates[id] = value; return clone(value);
      });
      return snapshot(profile,true,info,token);
    }
    const apply = async record => {
      if (params.color_temp > 0) {
        const range = record.info.color_temp_range;
        if (params.color_temp < range[0] || params.color_temp > range[1]) error(`Scegli una temperatura tra ${range[0]} e ${range[1]} K.`, 'validation');
      }
      await record.protocol.request('set_device_info',params);
      await readReal(record,profile);
      return snapshot(profile,false,record.info,token);
    };
    try { return await apply(await ensureReal(profile)); }
    catch (err) {
      if (err.code !== 'session_expired') throw err;
      records.delete(idKey(false,id)); return apply(await ensureReal(profile));
    }
  });
}
// I popup Chrome possono sparire subito: i gesti vengono consegnati al worker
// che coalesca gli slider e mantiene la sequenza per ogni singola lampadina.
function enqueueCommand(path, body, demo) {
  const allowed = ['/api/power','/api/brightness','/api/color_temp','/api/color_hsv'];
  if (!allowed.includes(path) || !body || typeof body.device_id !== 'string') error('Comando non valido.', 'validation');
  const key = idKey(demo,body.device_id);
  let queue = backgroundQueues.get(key);
  if (!queue) { queue = {pending:new Map(), running:false}; backgroundQueues.set(key,queue); }
  const result = new Promise((resolve,reject) => {
    const old = queue.pending.get(path), slot = old || {waiters:[]};
    slot.body = clone(body); slot.waiters.push({resolve,reject});
    queue.pending.delete(path); queue.pending.set(path,slot);
  });
  if (!queue.running) {
    queue.running = true;
    void (async () => {
      await new Promise(resolve => setTimeout(resolve,80));
      while (queue.pending.size) {
        const [nextPath,slot] = queue.pending.entries().next().value; queue.pending.delete(nextPath);
        try { const value = await command(nextPath,slot.body,demo); for (const w of slot.waiters) w.resolve(value); }
        catch (err) { for (const w of slot.waiters) w.reject(err); }
      }
      queue.running = false; if (backgroundQueues.get(key) === queue) backgroundQueues.delete(key);
    })();
  }
  return result;
}
function cleanImport(data) {
  if (!data || data.schema !== 1 || !Array.isArray(data.rooms) || data.rooms.length < 1 || data.rooms.length > 50 || !Array.isArray(data.devices) || data.devices.length > 100) error('Il file non è un home.json valido della versione 2.1.', 'validation');
  const ids = new Set(), names = new Set();
  const validId = x => typeof x === 'string' && /^[a-zA-Z0-9_-]{1,80}$/.test(x);
  const rooms = data.rooms.map(r => {
    if (!validId(r?.id) || ids.has(r.id)) error('Il file contiene stanze non valide.', 'validation');
    const name = cleanLabel(r.name,'la stanza',50); if (names.has(norm(name))) error('Il file contiene stanze duplicate.', 'validation');
    ids.add(r.id); names.add(norm(name)); return {id:r.id,name};
  });
  const deviceIds = new Set(), ips = new Set(), accounts = [];
  const devices = data.devices.map(d => {
    if (!validId(d?.id) || deviceIds.has(d.id) || !ids.has(d.room_id)) error('Il file contiene lampadine non valide.', 'validation');
    const profile = {id:d.id,room_id:d.room_id,name:cleanLabel(d.name,'la lampadina',60),email:validEmail(d.email),ip:validIp(d.ip),model:validModel(d.model)};
    if (ips.has(profile.ip)) error('Il file contiene indirizzi IP duplicati.', 'validation');
    deviceIds.add(d.id); ips.add(profile.ip);
    if (!accounts.some(a => norm(a.email) === norm(profile.email))) accounts.push({email:profile.email,vault:null});
    return profile;
  });
  return {schema:1,rooms,devices,accounts}; // Any legacy passwords or keyring flags are deliberately discarded.
}
async function importHouse(body) {
  return withHouse(async () => {
    const candidate = cleanImport(body?.house), previous = await loadHouse(false);
    await saveHouse(false,candidate);
    for (const d of previous.devices) records.delete(idKey(false,d.id));
    await updateSession(bag => { bag.passwords = {}; for (const k of Object.keys(bag.tokens)) if (k.startsWith('real:')) delete bag.tokens[k]; });
    const retainedIps = new Set(candidate.devices.map(d => d.ip));
    for (const d of previous.devices) if (!retainedIps.has(d.ip)) { try { await chrome.permissions.remove({origins:[`http://${d.ip}/*`]}); } catch (_) {} }
    return {house:await publicHouse(candidate,false)};
  });
}
async function exportHouse(demo) {
  const house = await loadHouse(demo);
  return {data:{schema:1,rooms:clone(house.rooms),devices:clone(house.devices),accounts:house.accounts.map(a => ({email:a.email,remember_password:false}))}};
}

export async function handleApi(path, body, demoFlag) {
  if (typeof path !== 'string') error('Richiesta non valida.', 'validation');
  if (path === '/api/bootstrap') return bootstrap();
  if (path === '/api/mode') { const demo = body?.demo === true; await chrome.storage.local.set({lumaDemoMode:demo}); const house = await loadHouse(demo); return {demo,house:await publicHouse(house,demo)}; }
  const demo = demoFlag === true;
  if (path === '/api/rooms') return saveRoom(body,demo,null);
  if (path === '/api/devices') return saveDevice(body,demo,null);
  if (path === '/api/queue') return enqueueCommand(body?.path,body?.body,demo);
  if (path === '/api/export') return exportHouse(demo);
  if (path === '/api/import') { if (demo) error('Esci dalla demo prima di importare la casa reale.', 'validation'); return importHouse(body); }
  if (path.startsWith('/api/state?')) { const id = new URLSearchParams(path.split('?')[1]).get('device_id'); return deviceState(demo,id); }
  if (['/api/power','/api/brightness','/api/color_temp','/api/color_hsv'].includes(path)) return command(path,body,demo);
  let match = path.match(/^\/api\/rooms\/([a-zA-Z0-9_-]+)\/(update|delete)$/);
  if (match) return match[2] === 'update' ? saveRoom(body,demo,match[1]) : deleteRoom(demo,match[1]);
  match = path.match(/^\/api\/devices\/([a-zA-Z0-9_-]+)\/(activate|update|delete)$/);
  if (match) {
    if (match[2] === 'activate') return activateDevice(demo,match[1]);
    if (match[2] === 'update') return saveDevice(body,demo,match[1]);
    return deleteDevice(demo,match[1]);
  }
  error('Operazione non disponibile.', 'not_found');
}
