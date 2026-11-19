// @vitest-environment node
import { webcrypto } from 'node:crypto';
import { afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest';
import { api, setToken } from './api';

let publicKey;
beforeAll(async()=>{
  const pair=await webcrypto.subtle.generateKey({name:'RSA-OAEP',modulusLength:3072,
    publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['encrypt','decrypt']);
  publicKey=await webcrypto.subtle.exportKey('jwk',pair.publicKey);
});
beforeEach(()=>{
  vi.stubGlobal('crypto',webcrypto);
  vi.stubGlobal('window',{location:{protocol:'http:',hostname:'localhost'}});
});
afterEach(()=>{setToken(null);vi.unstubAllGlobals();});
const credentials={email:'fixture@example.test',password:'Synthetic-only!123',fullName:'Bác sĩ kiểm thử',hospital:'H'.repeat(255),specialtyId:2};

it.each(['login','register'])('uses real WebCrypto and posts ciphertext only for %s',async purpose=>{
  const fetch=vi.fn().mockResolvedValueOnce({ok:true,json:async()=>({keyId:'key-fixture',publicKey,
    purpose,nonce:'a'.repeat(43),expiresAt:new Date(Date.now()+120000).toISOString()})})
    .mockResolvedValueOnce({ok:true,json:async()=>({message:'ok'})});
  vi.stubGlobal('fetch',fetch);
  await api[purpose](credentials);
  expect(fetch.mock.calls[0][0]).toBe(`/api/auth/doctor/encryption-challenge?purpose=${purpose}`);
  expect(fetch.mock.calls[0][1].body).toBeUndefined();
  const [path,options]=fetch.mock.calls[1];
  expect(path).toBe(`/api/auth/doctor/${purpose}/encrypted`);
  expect(Object.keys(JSON.parse(options.body))).toEqual(['payload']);
  expect(JSON.parse(options.body).payload.split('.')).toHaveLength(5);
  expect(options.body).not.toContain(credentials.email);
  expect(options.body).not.toContain(credentials.password);
  expect(options).toMatchObject({method:'POST',cache:'no-store',credentials:'omit'});
});
it('never falls back to plaintext when challenge fails',async()=>{
  const fetch=vi.fn().mockResolvedValue({ok:false,status:503});vi.stubGlobal('fetch',fetch);
  await expect(api.login(credentials)).rejects.toMatchObject({status:503});
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0][1].body).toBeUndefined();
});
it('never falls back to plaintext when encryption is unavailable',async()=>{
  vi.stubGlobal('crypto',{});
  const fetch=vi.fn().mockResolvedValue({ok:true,json:async()=>({})});vi.stubGlobal('fetch',fetch);
  await expect(api.login(credentials)).rejects.toThrow(/mã hóa/);
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0][1].body).toBeUndefined();
});
