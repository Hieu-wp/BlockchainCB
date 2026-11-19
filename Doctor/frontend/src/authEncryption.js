// RFC 7516 compact JWE: RSA-OAEP-256 wraps a fresh AES-256-GCM key.
// Never export an RSA private key or fall back to a plaintext login request.
function base64url(bytes) {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function encryptCredentials(credentials, challenge, purpose, cryptoApi = globalThis.crypto) {
  if (!cryptoApi?.subtle || !challenge || challenge.purpose !== purpose
      || !['login', 'register'].includes(purpose)
      || typeof challenge.keyId !== 'string'
      || !/^[A-Za-z0-9_-]{43}$/.test(challenge.nonce || '')
      || challenge.publicKey?.alg !== 'RSA-OAEP-256'
      || !Number.isFinite(Date.parse(challenge.expiresAt))) {
    throw new Error('Không thể mã hóa thông tin đăng nhập. Kiểm tra HTTPS và phiên bản Backend.');
  }
  const encoder = new TextEncoder();
  let plaintext;
  try {
    const publicKey = await cryptoApi.subtle.importKey('jwk', challenge.publicKey,
      {name:'RSA-OAEP', hash:'SHA-256'}, false, ['encrypt']);
    const contentKey = await cryptoApi.subtle.generateKey({name:'AES-GCM',length:256}, true, ['encrypt']);
    const rawKey = new Uint8Array(await cryptoApi.subtle.exportKey('raw', contentKey));
    let encryptedKey;
    try { encryptedKey = await cryptoApi.subtle.encrypt({name:'RSA-OAEP'}, publicKey, rawKey); }
    finally { rawKey.fill(0); }
    const protectedHeader = base64url(encoder.encode(JSON.stringify({
      alg:'RSA-OAEP-256',enc:'A256GCM',kid:challenge.keyId,typ:'doctor-auth+jwe',
    })));
    plaintext = encoder.encode(JSON.stringify({
      iss:'blockchaincb-doctor-ui',aud:'doctor-auth',jti:challenge.nonce,purpose,
      exp:Math.floor(Date.parse(challenge.expiresAt)/1000),credentials,
    }));
    const iv = cryptoApi.getRandomValues(new Uint8Array(12));
    const sealed = new Uint8Array(await cryptoApi.subtle.encrypt({
      name:'AES-GCM',iv,additionalData:encoder.encode(protectedHeader),tagLength:128,
    }, contentKey, plaintext));
    const payload = [protectedHeader,base64url(encryptedKey),base64url(iv),
      base64url(sealed.subarray(0,-16)),base64url(sealed.subarray(-16))].join('.');
    if (payload.length > 16384) throw new Error();
    return {payload};
  } catch {
    throw new Error('Không thể mã hóa thông tin đăng nhập. Vui lòng tải lại trang và thử lại.');
  } finally {
    // Best-effort byte buffer clearing, not a guarantee of wiping JS strings or DevTools history.
    plaintext?.fill(0);
  }
}
