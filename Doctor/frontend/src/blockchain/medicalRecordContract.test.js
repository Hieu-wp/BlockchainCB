import { beforeEach, afterEach, expect, it, vi } from 'vitest';
const m = vi.hoisted(() => ({ wallet: { getChainId: vi.fn(), requestAddresses: vi.fn(), getAddresses: vi.fn(), signMessage: vi.fn(), writeContract: vi.fn() }, public: { readContract: vi.fn(), simulateContract: vi.fn() }, role: true, permission: true, latest: 0n }));
vi.mock('viem', () => ({ createWalletClient: () => m.wallet, createPublicClient: () => m.public, custom: v => v }));
vi.mock('./anchorApi', () => ({ anchorApi: { prepare: vi.fn(), submit: vi.fn(), config: vi.fn(), challenge: vi.fn(), verify: vi.fn() } }));
import { anchorApi } from './anchorApi';
import { anchorVersion, connectDoctorWallet, linkDoctorWallet, watchWallet, CONTRACT_ADDRESS } from './medicalRecordContract';
const doctor = '0x' + '1'.repeat(40), patient = '0x' + '2'.repeat(40), hash = '0x' + '3'.repeat(64), zero = '0x' + '0'.repeat(64);
const challengeFixture=()=>({challengeId:'nonce-id',origin:window.location.origin,expiresAt:new Date(Date.now()+300000).toISOString(),
  message:`MedLedger wallet ownership verification\nDomain: ${window.location.host}\nURI: ${window.location.origin}\nChain ID: 11155111\nPurpose: LINK_DOCTOR_WALLET_ONLY\nAddress: ${doctor}`});
beforeEach(() => {
  vi.clearAllMocks(); m.role = true; m.permission = true; m.latest = 0n;
  vi.stubGlobal('ethereum', { on: vi.fn(), removeListener: vi.fn() });
  m.wallet.getChainId.mockResolvedValue(11155111); m.wallet.requestAddresses.mockResolvedValue([doctor]); m.wallet.getAddresses.mockResolvedValue([doctor]); m.wallet.signMessage.mockResolvedValue('0xsigned'); m.wallet.writeContract.mockResolvedValue(hash);
  m.public.readContract.mockImplementation(async ({ functionName }) => ({ DOCTOR_ROLE: hash, hasRole: m.role, writePermission: m.permission, records: [patient, m.latest, hash] })[functionName]);
  m.public.simulateContract.mockResolvedValue({ request: { functionName: 'anchorVersion', account: doctor } });
  anchorApi.prepare.mockResolvedValue({ recordKey: hash, patientWallet: patient, versionNumber: 1, contentHash: hash, previousHash: zero, doctorWallet: doctor, chainId: 11155111, contractAddress: CONTRACT_ADDRESS });
  anchorApi.submit.mockResolvedValue({ status: 'PENDING', transactionHash: hash });
  anchorApi.config.mockResolvedValue({ domain: window.location.host, walletOrigins:[window.location.origin],walletLinkPolicy: 'PENDING_APPROVED_V2_ORIGIN' });
});
afterEach(() => vi.unstubAllGlobals());
const run = options => anchorVersion({ id: 1, number: 1, linkedWallet: doctor, ...options });
it('connects a wallet and reads role without treating it as ownership proof', async () => { expect((await connectDoctorWallet()).hasDoctorRole).toBe(true); expect(anchorApi.verify).not.toHaveBeenCalled(); });
it('allows connection without DOCTOR_ROLE and does not grant any role', async () => {
  m.role = false;
  expect((await connectDoctorWallet()).hasDoctorRole).toBe(false);
  expect(m.wallet.writeContract).not.toHaveBeenCalled();
});
it('does not make off-chain ownership proof depend on contract read availability', async () => {
  m.public.readContract.mockRejectedValueOnce(new Error('RPC unavailable'));
  expect(await connectDoctorWallet()).toMatchObject({ address: doctor, hasDoctorRole: null });
  expect(m.wallet.writeContract).not.toHaveBeenCalled();
});
it('simulates then broadcasts and sends hash to Java, still pending', async () => { const onHash = vi.fn(); expect((await run({ onHash })).status).toBe('PENDING'); expect(m.public.simulateContract).toHaveBeenCalledOnce(); expect(m.wallet.writeContract).toHaveBeenCalledOnce(); expect(onHash).toHaveBeenCalledWith(hash); expect(anchorApi.submit).toHaveBeenCalledWith(1, 1, hash); });
it('requires MetaMask', async () => { vi.stubGlobal('ethereum', undefined); await expect(run()).rejects.toThrow('MetaMask'); });
it('rejects wrong chain before preparation', async () => { m.wallet.getChainId.mockResolvedValue(1); await expect(run()).rejects.toThrow('Sai mạng'); expect(anchorApi.prepare).not.toHaveBeenCalled(); });
it('rejects an account different from backend linked wallet', async () => { m.wallet.requestAddresses.mockResolvedValue([patient]); await expect(run()).rejects.toThrow('Sai ví Doctor'); expect(m.wallet.writeContract).not.toHaveBeenCalled(); });
it('rejects missing role', async () => { m.role = false; await expect(run()).rejects.toThrow('DOCTOR_ROLE'); expect(m.wallet.writeContract).not.toHaveBeenCalled(); });
it('rejects absent patient consent', async () => { m.permission = false; await expect(run()).rejects.toThrow('grantWrite'); });
it('rejects an already anchored version', async () => { m.latest = 1n; await expect(run()).rejects.toThrow('thứ tự'); });
it('never broadcasts when simulation fails', async () => { m.public.simulateContract.mockRejectedValueOnce(new Error('Contract revert')); await expect(run()).rejects.toThrow('Contract revert'); expect(m.wallet.writeContract).not.toHaveBeenCalled(); });
it('does not submit a hash when user rejects MetaMask', async () => { m.wallet.writeContract.mockRejectedValueOnce(Object.assign(new Error('Rejected'), { code: 4001 })); await expect(run()).rejects.toMatchObject({ code: 4001 }); expect(anchorApi.submit).not.toHaveBeenCalled(); });
it('stops before broadcast when account or chain changed', async () => { await expect(run({ stillCurrent: () => false })).rejects.toThrow('thay đổi'); expect(m.wallet.writeContract).not.toHaveBeenCalled(); });
it('signs exact backend challenge and submits signature for ownership verification', async () => {
  const challenge=challengeFixture();anchorApi.challenge.mockResolvedValue(challenge); anchorApi.verify.mockResolvedValue({ walletAddress: doctor });
  expect((await linkDoctorWallet(doctor)).walletAddress).toBe(doctor); expect(m.wallet.signMessage).toHaveBeenCalledWith({ account: doctor, message: challenge.message }); expect(anchorApi.verify).toHaveBeenCalledWith('nonce-id', '0xsigned');
  expect(anchorApi.challenge).toHaveBeenCalledWith(doctor,window.location.origin);
  expect(m.public.readContract).not.toHaveBeenCalled(); expect(m.wallet.writeContract).not.toHaveBeenCalled();
});
it('detects old backend before challenge or signature instead of mislabeling the account as blocked', async () => {
  anchorApi.config.mockResolvedValue({ domain: window.location.host });
  await expect(linkDoctorWallet(doctor)).rejects.toMatchObject({ code: 'WALLET_BACKEND_OUTDATED' });
  expect(anchorApi.challenge).not.toHaveBeenCalled();
  expect(m.wallet.signMessage).not.toHaveBeenCalled();
});
it('signs the exact allowed loopback alias rather than comparing it with the primary host',async()=>{
  const provider=window.ethereum;
  vi.stubGlobal('window',{location:new URL('http://127.0.0.1:5173'),ethereum:provider});
  anchorApi.config.mockResolvedValue({domain:'localhost:5173',walletOrigins:['http://localhost:5173','http://127.0.0.1:5173'],walletLinkPolicy:'PENDING_APPROVED_V2_ORIGIN'});
  const challenge=challengeFixture();anchorApi.challenge.mockResolvedValue(challenge);anchorApi.verify.mockResolvedValue({walletAddress:doctor});
  await linkDoctorWallet(doctor);
  expect(anchorApi.challenge).toHaveBeenCalledWith(doctor,'http://127.0.0.1:5173');
  expect(m.wallet.signMessage).toHaveBeenCalledWith({account:doctor,message:challenge.message});
});
it('never asks for a signature on an origin outside the server allowlist',async()=>{
  anchorApi.config.mockResolvedValue({walletLinkPolicy:'PENDING_APPROVED_V2_ORIGIN',walletOrigins:['https://doctor.example']});
  await expect(linkDoctorWallet(doctor)).rejects.toThrow('chưa được Backend cho phép');
  expect(anchorApi.challenge).not.toHaveBeenCalled();expect(m.wallet.signMessage).not.toHaveBeenCalled();
});
it.each(['origin','uri','expired','address'])('does not sign a challenge with an invalid %s',async field=>{
  const challenge=challengeFixture();
  if(field==='origin')challenge.origin='https://other.example';
  if(field==='uri')challenge.message=challenge.message.replace(`URI: ${window.location.origin}`,'URI: https://other.example');
  if(field==='expired')challenge.expiresAt=new Date(Date.now()-1000).toISOString();
  if(field==='address')challenge.message=challenge.message.replace(`Address: ${doctor}`,`Address: ${patient}`);
  anchorApi.challenge.mockResolvedValue(challenge);
  await expect(linkDoctorWallet(doctor)).rejects.toThrow('Challenge không khớp');
  expect(m.wallet.signMessage).not.toHaveBeenCalled();expect(anchorApi.verify).not.toHaveBeenCalled();
});
it('refreshes an already authorised account without a new wallet connection prompt', async () => {
  m.wallet.getAddresses.mockResolvedValue([patient]);
  expect(await connectDoctorWallet({ requestAccess: false })).toMatchObject({ address: patient });
  expect(m.wallet.requestAddresses).not.toHaveBeenCalled();
});
it('refuses to verify a signature after an account switch even when the provider event is delayed', async () => {
  anchorApi.challenge.mockResolvedValue(challengeFixture());
  m.wallet.getAddresses.mockResolvedValueOnce([doctor]).mockResolvedValueOnce([patient]);
  await expect(linkDoctorWallet(doctor)).rejects.toThrow('đổi sau khi ký');
  expect(anchorApi.verify).not.toHaveBeenCalled();
});
it('cleans up typed account and chain change listeners', () => {
  const cb = vi.fn(); const stop = watchWallet(cb);
  const accounts = window.ethereum.on.mock.calls.find(([event]) => event === 'accountsChanged')[1];
  const chain = window.ethereum.on.mock.calls.find(([event]) => event === 'chainChanged')[1];
  accounts([patient]); chain('0xaa36a7');
  expect(cb).toHaveBeenCalledWith({ type: 'accountsChanged', addresses: [patient] });
  expect(cb).toHaveBeenCalledWith({ type: 'chainChanged', chainId: '0xaa36a7' });
  stop();
  expect(window.ethereum.removeListener).toHaveBeenCalledWith('accountsChanged', accounts);
  expect(window.ethereum.removeListener).toHaveBeenCalledWith('chainChanged', chain);
});

it('routes a shared public version through its own preparation and verification API',async()=>{
  const shared={prepare:vi.fn().mockImplementation((...args)=>anchorApi.prepare(...args)),submit:vi.fn().mockResolvedValue({status:'CONFIRMED'})};
  const result=await run({api:shared});
  expect(shared.prepare).toHaveBeenCalledWith(1,1);expect(shared.submit).toHaveBeenCalledWith(1,1,expect.any(String));
  expect(anchorApi.submit).not.toHaveBeenCalled();expect(result.status).toBe('CONFIRMED');
});
