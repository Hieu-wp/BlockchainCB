// @vitest-environment node
import { webcrypto } from 'node:crypto';
import { beforeAll, expect, it } from 'vitest';
import { encryptCredentials } from './authEncryption';

let pair, challenge;
const credentials = {email:'fixture@example.test',password:'Synthetic-only!123',fullName:'Bác sĩ kiểm thử'};
beforeAll(async () => {
  pair = await webcrypto.subtle.generateKey({name:'RSA-OAEP',modulusLength:3072,
    publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['encrypt','decrypt']);
  challenge = {keyId:'test-key',nonce:'x'.repeat(43),purpose:'login',
    expiresAt:new Date(Date.now()+120000).toISOString(),
    publicKey:await webcrypto.subtle.exportKey('jwk',pair.publicKey)};
});
const decode = value => Buffer.from(value,'base64url');
async function decrypt(envelope, key=pair.privateKey) {
  const [header,wrapped,iv,cipher,tag] = envelope.payload.split('.');
  const raw = await webcrypto.subtle.decrypt({name:'RSA-OAEP'},key,decode(wrapped));
  const aes = await webcrypto.subtle.importKey('raw',raw,'AES-GCM',false,['decrypt']);
  const clear = await webcrypto.subtle.decrypt({name:'AES-GCM',iv:decode(iv),
    additionalData:Buffer.from(header),tagLength:128},aes,Buffer.concat([decode(cipher),decode(tag)]));
  return JSON.parse(new TextDecoder().decode(clear));
}
it('encrypts all credentials as authenticated JWE and preserves Unicode after decryption',async()=>{
  const envelope = await encryptCredentials(credentials,challenge,'login',webcrypto);
  expect(Object.keys(envelope)).toEqual(['payload']);
  expect(envelope.payload.split('.')).toHaveLength(5);
  expect(envelope.payload).not.toContain(credentials.email);
  expect(envelope.payload).not.toContain(credentials.password);
  expect(JSON.parse(decode(envelope.payload.split('.')[0]).toString())).toMatchObject({alg:'RSA-OAEP-256',enc:'A256GCM'});
  expect(await decrypt(envelope)).toMatchObject({credentials,purpose:'login',jti:challenge.nonce});
});
it('uses a fresh AES key and IV so the same credentials produce different ciphertext',async()=>{
  const a = await encryptCredentials(credentials,challenge,'login',webcrypto);
  const b = await encryptCredentials(credentials,challenge,'login',webcrypto);
  expect(a.payload).not.toEqual(b.payload);
  expect(a.payload.split('.')[2]).not.toEqual(b.payload.split('.')[2]);
});
it('rejects a modified authenticated ciphertext',async()=>{
  const envelope = await encryptCredentials(credentials,challenge,'login',webcrypto);
  const parts = envelope.payload.split('.');
  const cipher=decode(parts[3]);cipher[0]^=1;parts[3]=cipher.toString('base64url');
  await expect(decrypt({payload:parts.join('.')})).rejects.toThrow();
});
it('fails closed without WebCrypto and never returns a plaintext envelope',async()=>{
  await expect(encryptCredentials(credentials,challenge,'login',{})).rejects.toThrow(/mã hóa/);
});
it('rejects the wrong purpose or unsupported key algorithm',async()=>{
  await expect(encryptCredentials(credentials,challenge,'register',webcrypto)).rejects.toThrow(/mã hóa/);
  await expect(encryptCredentials(credentials,{...challenge,publicKey:{...challenge.publicKey,alg:'RSA1_5'}},'login',webcrypto)).rejects.toThrow(/mã hóa/);
});
