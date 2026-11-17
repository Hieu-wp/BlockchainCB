import { request } from '../api.js';
export const anchorApi = {
  config: () => request('/doctor/blockchain/config'),
  challenge: address => request('/doctor/wallet/challenges', { method: 'POST', body: { address } }),
  verify: (challengeId, signature) => request('/doctor/wallet/verify', { method: 'POST', body: { challengeId, signature } }),
  prepare: (id, number) => request(`/doctor/records/${id}/versions/${number}/anchor-payload`, { method: 'POST' }),
  submit: (id, number, transactionHash) => request(`/doctor/records/${id}/versions/${number}/anchor-transactions`, { method: 'POST', body: { transactionHash } }),
  status: (id, number) => request(`/doctor/records/${id}/versions/${number}/anchor-status`),
};
export const chainMessages = {
  BLOCKCHAIN_DISABLED: 'Blockchain đang tắt. Nhóm cần kiểm tra deployment/admin key và cấu hình RPC.',
  WALLET_PROOF_MISSING: 'Ví chưa có bằng chứng sở hữu trong Backend. Cần liên kết bằng chữ ký.',
  WALLET_PROOF_MISMATCH: 'Bằng chứng ví không khớp tài khoản hoặc Sepolia.',
  PATIENT_WALLET_MISSING: 'Patient chưa liên kết ví. Liên hệ thành viên Patient.',
  DOCTOR_WALLET_MISSING: 'Bạn cần liên kết ví bằng chữ ký trong Hồ sơ cá nhân.',
  PATIENT_WRITE_PERMISSION_MISSING: 'Patient chưa cấp grantWrite cho ví Doctor này.',
  DOCTOR_ROLE_MISSING: 'Ví chưa có DOCTOR_ROLE. Liên hệ Admin; Doctor không tự cấp role.',
  ONCHAIN_VERSION_ORDER_MISMATCH: 'Sai thứ tự phiên bản. Cần ghi lần lượt từ v1, hoặc kiểm tra giao dịch đã có.',
  ONCHAIN_PREVIOUS_HASH_MISMATCH: 'Hash phiên bản trước không khớp bằng chứng on-chain.',
  CONTENT_INTEGRITY_FAILED: 'Nội dung off-chain không toàn vẹn. Dừng ghi và liên hệ quản trị.',
  TRANSACTION_DATA_MISMATCH: 'Giao dịch không đúng contract, ví ký hoặc payload bệnh án.',
  ONCHAIN_DATA_MISMATCH: 'Event hoặc dữ liệu contract không khớp; chưa xác nhận thành công.',
  ANCHOR_PREPARED_WAIT_OR_RECOVER: 'Đã có yêu cầu chuẩn bị. Kiểm tra trạng thái; nếu đã hủy ký, chờ 2 phút trước khi thử lại.',
  ANCHOR_EXISTS_CHECK_STATUS: 'Phiên bản đã có giao dịch. Hãy kiểm tra trạng thái.',
  ONLY_VERSION_AUTHOR_CAN_ANCHOR: 'Chỉ bác sĩ tạo phiên bản này được ghi bằng ví đã liên kết của mình.',
  RPC_UNAVAILABLE: 'RPC chưa phản hồi. Trạng thái chưa được xác nhận; có thể kiểm tra lại.',
  WALLET_ALREADY_LINKED: 'Ví này đã thuộc tài khoản khác.',
  WALLET_CHANGE_REQUIRES_REVIEW: 'Tài khoản đã có ví xác minh. Đổi ví cần quy trình review riêng.',
  CHALLENGE_USED_OR_EXPIRED: 'Challenge đã dùng hoặc hết hạn. Hãy tạo yêu cầu mới.',
  INVALID_SIGNATURE: 'Chữ ký không hợp lệ hoặc không thuộc ví đã chọn.',
};
export function walletError(error) {
  const code = error?.code ?? error?.cause?.code;
  const name = error?.name || '';
  if (code === 4001 || /UserRejected/i.test(name)) return 'Bạn đã từ chối yêu cầu trong MetaMask. Chưa ghi Blockchain.';
  if (/InsufficientFunds/i.test(name) || /insufficient funds/i.test(error?.shortMessage || '')) return 'Không đủ Sepolia ETH trả gas.';
  return chainMessages[code] || error?.shortMessage || error?.message || 'Không thể thực hiện yêu cầu Blockchain.';
}
