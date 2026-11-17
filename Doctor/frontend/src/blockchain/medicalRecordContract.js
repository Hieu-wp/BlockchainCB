import { createPublicClient, createWalletClient, custom } from 'viem';
import { sepolia } from 'viem/chains';
import abi from './MedicalRecordAnchor.abi.json';
import { anchorApi } from './anchorApi.js';
export const CONTRACT_ADDRESS = '0xCd49fa3EE662E0bcfADa0FeA9D69f9383D6f7735';
export { abi };
export function getProvider() {
  if (typeof window === 'undefined' || !window.ethereum) throw new Error('Bạn chưa cài MetaMask.');
  return window.ethereum;
}
export function clients() {
  const provider = getProvider();
  return { provider, walletClient: createWalletClient({ chain: sepolia, transport: custom(provider) }),
    publicClient: createPublicClient({ chain: sepolia, transport: custom(provider) }) };
}
export function watchWallet(onChange) {
  const provider = typeof window !== 'undefined' ? window.ethereum : null;
  provider?.on?.('accountsChanged', onChange); provider?.on?.('chainChanged', onChange);
  return () => { provider?.removeListener?.('accountsChanged', onChange); provider?.removeListener?.('chainChanged', onChange); };
}
export async function connectDoctorWallet() {
  const { walletClient, publicClient } = clients();
  if (await walletClient.getChainId() !== sepolia.id) throw new Error('Sai mạng. Hãy chọn Ethereum Sepolia (11155111) trong MetaMask.');
  const [address] = await walletClient.requestAddresses();
  if (!address) throw new Error('Không lấy được tài khoản MetaMask.');
  const doctorRole = await publicClient.readContract({ address: CONTRACT_ADDRESS, abi, functionName: 'DOCTOR_ROLE' });
  const hasDoctorRole = await publicClient.readContract({ address: CONTRACT_ADDRESS, abi, functionName: 'hasRole', args: [doctorRole, address] });
  return { address, chainId: sepolia.id, hasDoctorRole };
}
export async function linkDoctorWallet(address, stillCurrent = () => true) {
  const { walletClient } = clients(); const config = await anchorApi.config();
  if (config.domain !== window.location.host) throw new Error('Domain ký không khớp trang hiện tại. Kiểm tra DOCTOR_WALLET_DOMAIN.');
  if (await walletClient.getChainId() !== sepolia.id) throw new Error('Cần chọn mạng Sepolia trước khi ký.');
  const [account] = await walletClient.getAddresses();
  if (account?.toLowerCase() !== address.toLowerCase()) throw new Error('Tài khoản MetaMask đã thay đổi. Kết nối lại.');
  const challenge = await anchorApi.challenge(address);
  if (!stillCurrent()) throw new Error('Ví hoặc mạng đã đổi. Kết nối lại.');
  const signature = await walletClient.signMessage({ account: address, message: challenge.message });
  if (!stillCurrent()) throw new Error('Ví hoặc mạng đã đổi. Không sử dụng chữ ký của phiên cũ.');
  return anchorApi.verify(challenge.challengeId, signature);
}
export async function anchorVersion({ id, number, linkedWallet, onHash, stillCurrent = () => true }) {
  const { walletClient, publicClient } = clients();
  if (!linkedWallet) throw new Error('Cần liên kết ví bằng chữ ký trong Hồ sơ cá nhân.');
  if (await walletClient.getChainId() !== sepolia.id) throw new Error('Sai mạng. Hãy chọn Ethereum Sepolia (11155111).');
  const [account] = await walletClient.requestAddresses();
  if (account?.toLowerCase() !== linkedWallet.toLowerCase()) throw new Error('Sai ví Doctor. Chọn đúng ví đã xác minh trong Backend.');
  const p = await anchorApi.prepare(id, number);
  if (p.chainId !== sepolia.id || p.contractAddress.toLowerCase() !== CONTRACT_ADDRESS.toLowerCase() || p.doctorWallet.toLowerCase() !== account.toLowerCase()) throw new Error('Cấu hình giao dịch không khớp. Dừng ký.');
  for (const hex of [p.recordKey, p.contentHash, p.previousHash]) if (!/^0x[0-9a-fA-F]{64}$/.test(hex)) throw new Error('Payload bytes32 không hợp lệ.');
  const read = (functionName, args = []) => publicClient.readContract({ address: CONTRACT_ADDRESS, abi, functionName, args });
  const role = await read('DOCTOR_ROLE');
  if (!await read('hasRole', [role, account])) throw new Error('Ví chưa có DOCTOR_ROLE. Liên hệ Admin.');
  if (!await read('writePermission', [p.patientWallet, account])) throw new Error('Patient chưa cấp grantWrite cho ví Doctor này.');
  const [, latest, previous] = await read('records', [p.recordKey]);
  if (latest + 1n !== BigInt(p.versionNumber)) throw new Error('Sai thứ tự phiên bản hoặc đã có giao dịch. Kiểm tra trạng thái.');
  if (latest > 0n && previous.toLowerCase() !== p.previousHash.toLowerCase()) throw new Error('previousHash không khớp Blockchain.');
  const args = [p.recordKey, p.patientWallet, BigInt(p.versionNumber), p.contentHash, p.previousHash];
  const { request } = await publicClient.simulateContract({ address: CONTRACT_ADDRESS, abi, functionName: 'anchorVersion', args, account });
  if (!stillCurrent()) throw new Error('MetaMask account/chain đã thay đổi. Dừng gửi và kết nối lại.');
  // Only MetaMask signs/broadcasts. Java never receives a private key.
  const hash = await walletClient.writeContract(request);
  onHash?.(hash);
  // Submit even if the UI wallet changed after broadcast; backend verifies the frozen intent.
  return anchorApi.submit(id, number, hash);
}
