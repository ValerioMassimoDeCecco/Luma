// Direct, local-only Tapo light protocol: KLAP v2 and HTTP TPAP (SPAKE2+).
// Protocol behaviour referenced from the MIT-licensed tapo project; see licenses/.
import { p256, sjcl } from './crypto-vendor.js';

const te = new TextEncoder();
const td = new TextDecoder('utf-8', {fatal: true});
const utf8 = x => te.encode(x);
const subtle = globalThis.crypto.subtle;
const ascii = utf8;
const MAX_RESPONSE = 1_048_576;

export class LumaError extends Error {
  constructor(message, code = 'error') { super(message); this.name = 'LumaError'; this.code = code; }
}
export function concat(...arrays) {
  const size = arrays.reduce((n, a) => n + a.length, 0);
  const result = new Uint8Array(size); let offset = 0;
  for (const a of arrays) { result.set(a, offset); offset += a.length; }
  return result;
}
export function hex(bytes) { return Array.from(bytes, n => n.toString(16).padStart(2, '0')).join(''); }
export function unhex(str) {
  if (!/^(?:[0-9a-f]{2})*$/i.test(str)) throw new LumaError('Dati crittografici non validi.', 'protocol');
  return Uint8Array.from(str.match(/../g) || [], x => parseInt(x, 16));
}
export function b64(bytes) { let s = ''; for (const c of bytes) s += String.fromCharCode(c); return btoa(s); }
export function from64(str) {
  try { return Uint8Array.from(atob(str), ch => ch.charCodeAt(0)); }
  catch (_) { throw new LumaError('La lampadina ha inviato dati non validi.', 'protocol'); }
}
const random = n => crypto.getRandomValues(new Uint8Array(n));
const hash = async (name, data) => new Uint8Array(await subtle.digest(name, data));
export const sha256 = data => hash('SHA-256', data);
const sha1 = data => hash('SHA-1', data);
async function hmac(keyBytes, data) {
  const key = await subtle.importKey('raw', keyBytes, {name: 'HMAC', hash: 'SHA-256'}, false, ['sign']);
  return new Uint8Array(await subtle.sign('HMAC', key, data));
}
export async function hkdf(keyBytes, salt, info, length) {
  const key = await subtle.importKey('raw', keyBytes, 'HKDF', false, ['deriveBits']);
  return new Uint8Array(await subtle.deriveBits({name: 'HKDF', hash: 'SHA-256', salt, info}, key, length * 8));
}
async function pbkdf2(pass, salt, iterations, length) {
  const key = await subtle.importKey('raw', pass, 'PBKDF2', false, ['deriveBits']);
  return new Uint8Array(await subtle.deriveBits({name: 'PBKDF2', hash: 'SHA-256', salt, iterations}, key, length * 8));
}
function equal(a, b) {
  if (a.length !== b.length) return false;
  let d = 0; for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
  return d === 0;
}
function signedBytes(seq) { const b = new Uint8Array(4); new DataView(b.buffer).setInt32(0, seq, false); return b; }
function ccmEncrypt(key, nonce, plain) {
  const aes = new sjcl.cipher.aes(sjcl.codec.hex.toBits(hex(key)));
  const result = sjcl.mode.ccm.encrypt(aes, sjcl.codec.hex.toBits(hex(plain)), sjcl.codec.hex.toBits(hex(nonce)), [], 128);
  return unhex(sjcl.codec.hex.fromBits(result));
}
function ccmDecrypt(key, nonce, cipher) {
  try {
    const aes = new sjcl.cipher.aes(sjcl.codec.hex.toBits(hex(key)));
    const result = sjcl.mode.ccm.decrypt(aes, sjcl.codec.hex.toBits(hex(cipher)), sjcl.codec.hex.toBits(hex(nonce)), [], 128);
    return unhex(sjcl.codec.hex.fromBits(result));
  } catch (_) { throw new LumaError('La risposta cifrata della lampadina non è autentica o non è valida.', 'bad_signature'); }
}
function checkBodySize(bytes) {
  if (bytes.length > MAX_RESPONSE) throw new LumaError('Risposta della lampadina troppo grande.', 'protocol');
  return bytes;
}
async function post(url, body, contentType = 'application/octet-stream') {
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(url, {
      method: 'POST', body, signal: controller.signal, redirect: 'error', cache: 'no-store',
      credentials: 'include', headers: {'Content-Type': contentType},
    });
    const length = Number(response.headers.get('content-length'));
    if (length > MAX_RESPONSE) throw new LumaError('Risposta della lampadina troppo grande.', 'protocol');
    return {status: response.status, bytes: checkBodySize(new Uint8Array(await response.arrayBuffer()))};
  } catch (error) {
    if (error instanceof LumaError) throw error;
    if (error.name === 'AbortError') throw new LumaError('La lampadina non risponde. Controlla IP, rete e alimentazione.', 'timeout');
    throw new LumaError('Non riesco a raggiungere la lampadina. Controlla IP, rete e autorizzazione Chrome.', 'network');
  } finally { clearTimeout(timer); }
}
function parseJSON(bytes) {
  try { return JSON.parse(td.decode(bytes)); }
  catch (_) { throw new LumaError('La lampadina ha inviato una risposta non valida.', 'protocol'); }
}
function checkDeviceResult(data, duringLogin = false) {
  if (!data || typeof data.error_code !== 'number') throw new LumaError('La risposta della lampadina non è riconosciuta.', 'protocol');
  if (data.error_code === 0) return data.result;
  if (data.error_code === -2101) throw new LumaError('Troppi tentativi di accesso: attendi che la lampadina si sblocchi prima di riprovare.', 'locked');
  if (data.error_code === -2203 || (duringLogin && data.error_code === -40401)) throw new LumaError('Credenziali Tapo non riconosciute. Verifica la password e riprova senza ripetere troppi tentativi.', 'credentials');
  if (data.error_code === -40401 || data.error_code === 9999) throw new LumaError('La sessione della lampadina è scaduta.', 'session_expired');
  throw new LumaError(`La lampadina ha rifiutato la richiesta (codice ${data.error_code}).`, 'device_error');
}

/** KLAP v2: one session per device; the browser holds its TP_SESSIONID cookie. */
export class Klap {
  constructor(base) { this.base = base; this.seq = 0; }
  async login(email, password) {
    const seed = random(16);
    const auth = await sha256(concat(await sha1(utf8(email)), await sha1(utf8(password))));
    const one = await post(`${this.base}/app/handshake1`, seed);
    if (one.status !== 200) throw new LumaError('La lampadina non accetta KLAP. Verifica firmware e compatibilità con servizi di terze parti nell’app Tapo.', 'unsupported');
    if (one.bytes.length !== 48) throw new LumaError('Risposta KLAP non valida; questo firmware potrebbe richiedere un altro protocollo.', 'protocol');
    const remote = one.bytes.slice(0, 16), serverHash = one.bytes.slice(16);
    if (!equal(await sha256(concat(seed, remote, auth)), serverHash)) throw new LumaError('Credenziali Tapo non riconosciute o lampadina associata a un altro account.', 'credentials');
    const two = await post(`${this.base}/app/handshake2`, await sha256(concat(remote, seed, auth)));
    if (two.status !== 200) throw new LumaError('Accesso KLAP non riuscito. Controlla l’accesso ai cookie per la rete locale.', 'credentials');
    const material = concat(seed, remote, auth);
    this.key = await subtle.importKey('raw', (await sha256(concat(ascii('lsk'), material))).slice(0, 16), 'AES-CBC', false, ['encrypt', 'decrypt']);
    const ivhash = await sha256(concat(ascii('iv'), material));
    this.iv = ivhash.slice(0, 12); this.seq = new DataView(ivhash.buffer, ivhash.byteOffset + 28, 4).getInt32(0, false);
    this.sig = (await sha256(concat(ascii('ldk'), material))).slice(0, 28);
  }
  async request(method, params = null) {
    if (!this.key) throw new LumaError('Sessione non inizializzata.', 'session_expired');
    this.seq = (this.seq + 1) | 0; const seq = this.seq, seqBytes = signedBytes(seq), iv = concat(this.iv, seqBytes);
    const plain = utf8(JSON.stringify({method, params}));
    const encrypted = new Uint8Array(await subtle.encrypt({name: 'AES-CBC', iv}, this.key, plain));
    const sig = await sha256(concat(this.sig, seqBytes, encrypted));
    const response = await post(`${this.base}/app/request?seq=${seq}`, concat(sig, encrypted));
    if (response.status === 403 || response.status === 401) throw new LumaError('Sessione della lampadina scaduta.', 'session_expired');
    if (response.status !== 200) throw new LumaError('La lampadina non ha accettato il comando.', 'device_error');
    if (response.bytes.length < 48 || (response.bytes.length - 32) % 16 !== 0) throw new LumaError('Risposta KLAP incompleta.', 'protocol');
    const receivedSignature = response.bytes.slice(0, 32), ciphertext = response.bytes.slice(32);
    if (!equal(receivedSignature, await sha256(concat(this.sig, seqBytes, ciphertext)))) {
      throw new LumaError('La firma della risposta KLAP non è valida.', 'bad_signature');
    }
    let decoded;
    try { decoded = new Uint8Array(await subtle.decrypt({name: 'AES-CBC', iv}, this.key, ciphertext)); }
    catch (_) { throw new LumaError('La risposta KLAP non può essere decifrata.', 'bad_signature'); }
    return checkDeviceResult(parseJSON(decoded));
  }
}

const ORDER = p256.CURVE.n;
const M = p256.ProjectivePoint.fromHex('02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f');
const N = p256.ProjectivePoint.fromHex('03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49');
const asBig = bytes => BigInt('0x' + (hex(bytes) || '0'));
const scalarBytes = n => unhex(n.toString(16).padStart(64, '0'));
const pointBytes = point => point.toRawBytes(false);
const pointMul = (point, scalar) => scalar === 0n ? p256.ProjectivePoint.ZERO : point.multiply(scalar);
function transcript(items) {
  const result = [];
  for (const item of items) { const len = new Uint8Array(8); new DataView(len.buffer).setBigUint64(0, BigInt(item.length), true); result.push(len, item); }
  return concat(...result);
}
function randomScalar() { let x; do { x = asBig(random(32)); } while (x === 0n || x >= ORDER); return x; }

/** Exported for RFC 9383 vectors and an independent simulated verifier. */
export async function spakeProof(w0, w1, x, context, proverId, verifierId, deviceShare) {
  let Y;
  try { Y = p256.ProjectivePoint.fromHex(deviceShare); Y.assertValidity(); }
  catch (_) { throw new LumaError('La lampadina ha inviato una chiave P-256 non valida.', 'protocol'); }
  const X = pointMul(p256.ProjectivePoint.BASE, x).add(pointMul(M, w0));
  const unmasked = Y.subtract(pointMul(N, w0));
  if (unmasked.equals(p256.ProjectivePoint.ZERO)) throw new LumaError('La chiave della lampadina non è sicura.', 'protocol');
  const Z = pointMul(unmasked, x), V = pointMul(unmasked, w1);
  if (Z.equals(p256.ProjectivePoint.ZERO) || V.equals(p256.ProjectivePoint.ZERO)) throw new LumaError('Scambio di chiavi non valido.', 'protocol');
  const share = pointBytes(X), yBytes = pointBytes(Y);
  const trans = transcript([context, proverId, verifierId, pointBytes(M), pointBytes(N), share, yBytes, pointBytes(Z), pointBytes(V), scalarBytes(w0)]);
  const main = await sha256(trans), confirmation = await hkdf(main, new Uint8Array(0), ascii('ConfirmationKeys'), 64);
  return {share, confirm: await hmac(confirmation.slice(0, 32), yBytes), expectedDevice: await hmac(confirmation.slice(32), share), sharedKey: await hkdf(main, new Uint8Array(0), ascii('SharedKey'), 32)};
}
export async function makeTpapProof(password, registration, userRandom) {
  const crypt = registration.extra_crypt;
  if (crypt?.type !== 'password_shadow' || crypt?.params?.passwd_id !== 2) {
    throw new LumaError('Questo firmware richiede una variante TPAP non ancora supportata. Non verranno effettuati tentativi di accesso ripetuti.', 'unsupported');
  }
  if (registration.cipher_suites !== 1 || registration.encryption !== 'aes_128_ccm' ||
      !Number.isInteger(registration.iterations) || registration.iterations < 1 || registration.iterations > 100000) {
    throw new LumaError('La lampadina richiede parametri TPAP non supportati.', 'unsupported');
  }
  const devRandom = from64(registration.dev_random || ''), salt = from64(registration.dev_salt || ''), devShare = from64(registration.dev_share || '');
  if (devRandom.length !== 32 || salt.length < 1 || salt.length > 256 || devShare.length !== 65) throw new LumaError('Risposta TPAP non valida.', 'protocol');
  const credential = hex(await sha1(utf8(password)));
  const stretch = await pbkdf2(utf8(credential), salt, registration.iterations, 80);
  const w0 = asBig(stretch.slice(0, 40)) % ORDER, w1 = asBig(stretch.slice(40)) % ORDER;
  const context = await sha256(concat(ascii('PAKE V1'), userRandom, devRandom));
  return spakeProof(w0, w1, randomScalar(), context, new Uint8Array(0), new Uint8Array(0), devShare);
}

/** Current TPAP mode for lights that announce plain HTTP and password-shadow id 2. */
export class Tpap {
  constructor(base, info) { this.base = base; this.info = info; }
  async login(_email, password) {
    if (Number(this.info.tls || 0) !== 0 || Number(this.info.dac || 0) !== 0 || !Array.isArray(this.info.pake) || !this.info.pake.includes(2) || this.info.pake.includes(0)) {
      // Metadati pubblici di discovery, senza credenziali, IP o identificativi.
      const tls = Number(this.info.tls || 0), dac = Number(this.info.dac || 0);
      const pake = Array.isArray(this.info.pake) ? this.info.pake.filter(n => Number.isInteger(n) && n >= 0 && n <= 255).slice(0,16).join(',') : '?';
      throw new LumaError(`TPAP non supportato (TLS=${tls}, DAC=${dac}, PAKE=[${pake}]). Se vuoi, attiva Compatibilità con terze parti nell’app Tapo, attendi e riprova. Questa impostazione può ridurre la sicurezza e non garantisce il collegamento.`, 'unsupported');
    }
    const ur = random(32);
    const registration = await this.loginRequest({method:'login', params:{sub_method:'pake_register', username:'21232f297a57a5a743894a0e4a801fc3', user_random:b64(ur), cipher_suites:[1], encryption:['aes_128_ccm'], passcode_type:'userpw'}});
    const proof = await makeTpapProof(password, registration, ur);
    const share = await this.loginRequest({method:'login', params:{sub_method:'pake_share', user_share:b64(proof.share), user_confirm:b64(proof.confirm)}});
    if (!equal(from64(share.dev_confirm || ''), proof.expectedDevice)) throw new LumaError('La lampadina non ha confermato la propria identità; connessione interrotta.', 'bad_signature');
    if (typeof share.stok !== 'string' || !/^[a-zA-Z0-9_-]{1,256}$/.test(share.stok) || !Number.isInteger(share.start_seq) || share.start_seq < -2147483648 || share.start_seq > 2147483647) {
      throw new LumaError('Parametri della sessione TPAP non validi.', 'protocol');
    }
    this.url = `${this.base}/stok=${share.stok}/ds`; this.seq = share.start_seq | 0;
    this.key = await hkdf(proof.sharedKey, ascii('tp-kdf-salt-aes128-key'), ascii('tp-kdf-info-aes128-key'), 16);
    this.nonce = await hkdf(proof.sharedKey, ascii('tp-kdf-salt-aes128-iv'), ascii('tp-kdf-info-aes128-iv'), 12);
  }
  async loginRequest(body) {
    const response = await post(`${this.base}/`, JSON.stringify(body), 'application/json');
    if (response.status !== 200) throw new LumaError('Accesso TPAP non riuscito.', 'device_error');
    return checkDeviceResult(parseJSON(response.bytes), true);
  }
  async request(method, params = null) {
    if (!this.key) throw new LumaError('Sessione non inizializzata.', 'session_expired');
    const seq = this.seq; this.seq = (seq + 1) | 0;
    const seqBytes = signedBytes(seq), nonce = concat(this.nonce.slice(0, 8), seqBytes);
    const request = utf8(JSON.stringify({method, params}));
    const response = await post(this.url, concat(seqBytes, ccmEncrypt(this.key, nonce, request)));
    if (response.status === 403 || response.status === 401) throw new LumaError('Sessione della lampadina scaduta.', 'session_expired');
    if (response.status !== 200) throw new LumaError('La lampadina non ha accettato il comando.', 'device_error');
    if (response.bytes.length < 20 || !equal(response.bytes.slice(0, 4), seqBytes)) {
      try { const obj = parseJSON(response.bytes); if (obj.error_code) throw new LumaError('Sessione della lampadina scaduta.', 'session_expired'); } catch (e) { if (e.code === 'session_expired') throw e; }
      throw new LumaError('Risposta TPAP non valida o fuori sequenza.', 'bad_signature');
    }
    return checkDeviceResult(parseJSON(ccmDecrypt(this.key, nonce, response.bytes.slice(4))));
  }
}

/** Ask an IP which protocol it speaks, then authenticate once (never guess after an auth failure). */
export async function connectTapo(ip, email, password, portOverride = null) {
  const base = `http://${ip}${portOverride ? ':' + portOverride : ''}`;
  const discovery = await post(`${base}/`, JSON.stringify({method:'login', params:{sub_method:'discover'}}), 'application/json');
  let answer = null;
  if (discovery.status === 200) {
    try { answer = parseJSON(discovery.bytes); } catch (_) { /* legacy KLAP response is not JSON */ }
  }
  if (answer?.error_code === -2101) checkDeviceResult(answer);
  let protocol;
  if (answer?.error_code === 0 && answer?.result?.tpap && typeof answer.result.tpap === 'object') {
    const info = answer.result.tpap;
    const port = Number(info.port);
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new LumaError('Porta TPAP non valida.', 'protocol');
    const destination = portOverride ? base : `http://${ip}${port === 80 ? '' : ':' + port}`;
    protocol = new Tpap(destination, info);
  } else protocol = new Klap(base);
  await protocol.login(email, password);
  return protocol;
}

// Test-only helper exposes the CCM implementation for public known-answer vectors.
export const testCrypto = {ccmEncrypt, ccmDecrypt, signedBytes};
