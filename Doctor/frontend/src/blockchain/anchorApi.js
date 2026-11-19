import { request } from '../api.js';
export const anchorApi = {
  config: () => request('/doctor/blockchain/config'),
  wallet: () => request('/doctor/wallet'),
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
  WALLET_ALREADY_VERIFIED: 'Ví này đã được Backend xác minh cho tài khoản của bạn. Tải lại hồ sơ, không cần ký lại.',
  WALLET_CHANGE_REQUIRES_REVIEW: 'Tài khoản đã có ví xác minh. Đổi ví cần quy trình review riêng.',
  CHALLENGE_USED_OR_EXPIRED: 'Challenge đã dùng hoặc hết hạn. Hãy tạo yêu cầu mới.',
  INVALID_SIGNATURE: 'Chữ ký không hợp lệ hoặc không thuộc ví đã chọn.',
  WALLET_LINK_NOT_ALLOWED: 'Trạng thái hồ sơ không cho phép liên kết ví. Chỉ Doctor PENDING hoặc APPROVED được thực hiện; hãy liên hệ Admin.',
  CHALLENGE_NOT_OWNED: 'Challenge không thuộc phiên đăng nhập này. Hãy đăng nhập đúng tài khoản và ký yêu cầu mới.',
  ACCOUNT_DISABLED: 'Tài khoản đã bị vô hiệu hóa. Liên hệ Admin.',
  DOCTOR_BLOCKED: 'Tài khoản Doctor đã bị khóa. Liên hệ Admin.',
  ACCOUNT_ROLE_INVALID: 'Tài khoản đăng nhập không có role DOCTOR. Hãy đăng nhập đúng tài khoản.',
  DOCTOR_PROFILE_MISSING: 'Tài khoản chưa có hồ sơ Doctor hợp lệ. Liên hệ Admin kiểm tra liên kết User/Doctor.',
  DOCTOR_PROFILE_INVALID: 'Hồ sơ Doctor hoặc liên kết tài khoản không hợp lệ.',
  DOCTOR_IDENTITY_MISMATCH: 'Danh tính User/Doctor không khớp. Hãy đăng nhập lại.',
  DOCTOR_APPROVAL_INVALID: 'Trạng thái xét duyệt không hợp lệ. Liên hệ Admin kiểm tra hồ sơ.',
  DOCTOR_APPROVAL_REQUIRED: 'Thao tác này yêu cầu Doctor APPROVED. Bước liên kết ví phải được phục vụ bằng Backend đã hỗ trợ PENDING; hãy kiểm tra và khởi động lại đúng Backend.',
  ACCOUNT_RESTRICTED: 'Backend từ chối tài khoản nhưng bản đang chạy chưa nêu rõ nguyên nhân. Kiểm tra Backend đã khởi động lại; không thể kết luận tài khoản bị khóa.',
  ACCESS_DENIED: 'Backend từ chối quyền truy cập endpoint này. Kiểm tra phiên đăng nhập, route và phiên bản Backend.',
  WALLET_BACKEND_OUTDATED: 'Backend đang chạy bản chưa hỗ trợ liên kết ví PENDING. Dừng và khởi động lại đúng Doctor Backend đã biên dịch, rồi tải lại trang và đăng nhập lại.',
  AUTHENTICATION_REQUIRED: 'Yêu cầu đăng nhập Doctor trước khi liên kết ví.',
  TOKEN_INVALID: 'JWT không hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại.',
};
export function walletError(error) {
  const code = error?.code ?? error?.cause?.code;
  const name = error?.name || '';
  if (code === 4001 || /UserRejected/i.test(name)) return 'Bạn đã từ chối yêu cầu trong MetaMask. Chưa ghi Blockchain.';
  if (/InsufficientFunds/i.test(name) || /insufficient funds/i.test(error?.shortMessage || '')) return 'Không đủ Sepolia ETH trả gas.';
  const message = error?.status === 403 && !chainMessages[code]
    ? 'Backend trả 403 nhưng chưa cung cấp nguyên nhân cụ thể. Kiểm tra phiên bản Backend và đăng nhập; chưa thể kết luận tài khoản bị khóa.'
    : chainMessages[code] || error?.shortMessage || error?.message || 'Không thể thực hiện yêu cầu Blockchain.';
  const steps = { '/doctor/blockchain/config': 'Đọc cấu hình ví', '/doctor/wallet': 'Đọc ví đã liên kết', '/doctor/wallet/challenges': 'Tạo challenge', '/doctor/wallet/verify': 'Xác minh chữ ký' };
  const step = steps[error?.path];
  return step ? `${step} · HTTP ${error.status}${chainMessages[code] ? ` · ${code}` : ''}: ${message}` : message;
}
