import { encryptCredentials } from './authEncryption';
import { apiSlot } from './apiQueue';
let token = null;
let generation = 0;
export function setToken(value) { token = value; generation++; }
export class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
// Local HTTP is for development on this machine only. HTTPS is required elsewhere.
// TLS protects transport, but cannot hide a request from its own browser's DevTools.
export function requireAuthTransport(location = window.location) {
  const loopback = ['localhost', '127.0.0.1', '[::1]', '::1'].includes(location.hostname);
  if (location.protocol !== 'https:' && !(location.protocol === 'http:' && loopback)) {
    throw new ApiError(0, 'Đăng nhập và đăng ký cần HTTPS. HTTP chỉ được phép khi phát triển trên localhost.');
  }
}
export async function request(path, { method = 'GET', body, signal, blob = false } = {}) {
  if (path.startsWith('/auth/doctor/')) requireAuthTransport();
  const current = generation;
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  let response;
  try { response = await apiSlot(signal, async () => {
    if (current !== generation) throw new DOMException('Phiên đã thay đổi', 'AbortError');
    return fetch(`/api${path}`, { method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined, signal, cache: 'no-store', credentials: 'omit' });
  }); }
  catch (e) { if (e.name === 'AbortError') throw e; throw new ApiError(0, 'Không kết nối được máy chủ. Kiểm tra Backend và proxy local.'); }
  if (current !== generation) throw new DOMException('Phiên đã thay đổi', 'AbortError');
  if (!response.ok) {
    if (response.status === 401 && token) { setToken(null); window.dispatchEvent(new Event('doctor:expired')); }
    const messages = { 400: 'Dữ liệu không hợp lệ. Kiểm tra các trường và chuyên khoa.', 401: 'Phiên hết hạn hoặc email / mật khẩu không đúng. Vui lòng đăng nhập lại.', 403: 'Bạn không có quyền hiện hành cho thao tác này, hoặc tài khoản đã bị hạn chế.', 404: 'Không tìm thấy hồ sơ hoặc phiên bản.', 409: 'Dữ liệu trùng, đã thay đổi hoặc không còn toàn vẹn. Hãy tải lại trước khi lưu.', 413: 'Tệp vượt quá giới hạn 5 MiB.', 415: 'Định dạng gửi không được hỗ trợ.', 429: 'Quá nhiều yêu cầu. Vui lòng đợi một lúc rồi thử lại.', 503: 'Dịch vụ máy chủ tạm thời chưa sẵn sàng. Với thao tác lưu, kiểm tra lịch sử trước khi gửi lại.' };
    const error = new ApiError(response.status, messages[response.status] || 'Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại sau.');
    error.path = path;
    error.method = method;
    if (path.startsWith('/doctor/') || path.startsWith('/auth/doctor/')) {
      try {
        const body = await response.json();
        const legacy = { 'Bac si chua duoc phe duyet': 'DOCTOR_APPROVAL_REQUIRED', 'Tai khoan khong duoc phep truy cap': 'ACCOUNT_RESTRICTED', 'Trang thai phe duyet khong hop le': 'DOCTOR_APPROVAL_INVALID', 'Khong co ho so Doctor': 'DOCTOR_PROFILE_MISSING', 'Danh tinh khong hop le': 'DOCTOR_IDENTITY_MISMATCH', 'Khong du quyen': 'ACCESS_DENIED' };
        const code = body?.code || legacy[body?.message] || body?.message?.split(':')[0];
        if (typeof code === 'string' && /^[A-Z_]{3,80}$/.test(code)) error.code = code;
      } catch { /* Do not display arbitrary server bodies, SQL or HTML. */ }
    }
    const serviceMessages = {
      SHARED_WRITE_MIGRATION_REQUIRED: 'Chưa áp dụng migration metadata Doctor cho bệnh án chung. Cần nhóm duyệt SQL trước khi bật lưu.',
      VERSION_CONFLICT: 'Hồ sơ đã có phiên bản mới. Tải lại trước khi lưu.',
      PUBLIC_INTEGRITY_UNVERIFIED: 'Phiên bản chưa có bằng chứng hash hợp lệ; không thể cập nhật tự động.',
      ONLY_RECORD_AUTHOR_CAN_UPDATE: 'Chỉ bác sĩ lập hồ sơ này được tạo phiên bản cập nhật.',
      DATABASE_BUSY: 'Backend đang xử lý nhiều yêu cầu. Vui lòng đợi rồi thử lại.',
      DATABASE_UNAVAILABLE: 'Kết nối database tạm thời không khả dụng hoặc hết thời gian chờ. Vui lòng đợi rồi thử lại; kiểm tra lịch sử trước khi gửi lại thao tác lưu.',
      AUTH_ENVELOPE_INVALID: 'Yêu cầu đăng nhập mã hóa không hợp lệ hoặc đã hết hạn. Nhập lại mật khẩu và thử lại.',
      AUTH_CHALLENGE_BUSY: 'Máy chủ đang có nhiều yêu cầu đăng nhập. Vui lòng đợi một lúc rồi thử lại.',
      SHARED_RECORDS_NOT_READY: 'Bệnh án chung chưa được tích hợp. Chờ đối chiếu schema và thống nhất quyền ghi với Patient; không dùng ID bệnh nhân chung cho bệnh án cũ.',
      SHARED_SCHEMA_UNVERIFIED: 'Backend chưa xác minh được cấu trúc dữ liệu Patient. Kiểm tra phiên bản Backend và metadata của bảng chung.',
      SHARED_RECORD_READ_NOT_READY: 'Cấu trúc bệnh án chung chưa phù hợp với phiên bản Backend. Cần kiểm tra cột và khóa ngoại.',
      PATIENT_READ_PERMISSION_REQUIRED: 'Bạn không còn quyền đọc hiện hành với bệnh nhân hoặc hồ sơ này.',
      SHARED_VERSION_MISSING: 'Không tìm thấy phiên bản tương ứng trong bệnh án chung. Cần kiểm tra current_version của hồ sơ.',
      SHARED_METADATA_UNAVAILABLE: 'Backend không đọc được metadata database. Kiểm tra kết nối, giới hạn Pooler và quyền truy cập database.',
      AUDIT_UNAVAILABLE: 'Nhật ký hoạt động tạm thời không khả dụng. Với thao tác lưu, kiểm tra lịch sử trước khi gửi lại.',
    };
    if (serviceMessages[error.code]) error.message = serviceMessages[error.code];
    throw error;
  }
  return blob ? response.blob() : response.json();
}
async function encryptedAuth(purpose, credentials) {
  requireAuthTransport();
  const challenge = await request(`/auth/doctor/encryption-challenge?purpose=${purpose}`);
  const body = await encryptCredentials(credentials, challenge, purpose);
  return request(`/auth/doctor/${purpose}/encrypted`, {method:'POST',body});
}
export const api = {
  clinicalAccess: (patient,signal) => request(`/doctor/shared-patients/${patient}/clinical-access`,{signal}),
  clinicalCreate: body => request('/doctor/shared-records',{method:'POST',body}),
  clinicalUpdate: (id,body) => request(`/doctor/shared-records/${id}/versions`,{method:'POST',body}),
  sharedIntegrity: (id,number,signal) => request(`/doctor/shared-records/${id}/versions/${number}/integrity`,{signal}),
  clinicalUpload: (patient,file) => {const body=new FormData();body.append('file',file);return request(`/doctor/shared-patients/${patient}/uploads`,{method:'POST',body});},
  clinicalDownload: (id,number,file) => request(`/doctor/shared-records/${id}/versions/${number}/attachments/${file}`,{blob:true}),
  sharedRecords: (patientCode='',page=0,signal) => request(`/doctor/shared-records?patientCode=${encodeURIComponent(patientCode)}&page=${page}&size=10`,{signal}),
  sharedPatientRecords: (patient,page=0,signal) => request(`/doctor/shared-patients/${patient}/records?page=${page}&size=10`,{signal}),
  sharedRecord: (id,signal) => request(`/doctor/shared-records/${id}`,{signal}),
  sharedVersions: (id,page=0,signal) => request(`/doctor/shared-records/${id}/versions?page=${page}&size=10`,{signal}),
  sharedVersion: (id,number,signal) => request(`/doctor/shared-records/${id}/versions/${number}`,{signal}),
  integration: signal => request('/doctor/integration', { signal }),
  requestAccess: patientCode => request('/doctor/access-grants/request', {method:'POST',body:{patientCode}}),
  accessRequests: (page=0, signal) => request(`/doctor/access-grants?page=${page}&size=10`, {signal}),
  login: body => encryptedAuth('login', body),
  register: body => encryptedAuth('register', body),
  me: signal => request('/doctor/me', { signal }),
  profile: body => request('/doctor/me', { method: 'PATCH', body }),
  patients: (q = '', page = 0, signal) => request(`/doctor/patients?q=${encodeURIComponent(q)}&page=${page}&size=10`, { signal }),
  records: (patient, page = 0, signal) => request(`/doctor/records?page=${page}&size=10${patient ? `&patientId=${encodeURIComponent(patient)}` : ''}`, { signal }),
  patientRecords: (id, page = 0, signal) => request(`/doctor/patients/${id}/records?page=${page}&size=10`, { signal }),
  record: (id, signal) => request(`/doctor/records/${id}`, { signal }),
  create: body => request('/doctor/records', { method: 'POST', body }),
  update: (id, body) => request(`/doctor/records/${id}/versions`, { method: 'POST', body }),
  versions: (id, page = 0, signal) => request(`/doctor/records/${id}/versions?page=${page}&size=10`, { signal }),
  version: (id, number, signal) => request(`/doctor/records/${id}/versions/${number}`, { signal }),
  hash: (id, number, signal) => request(`/doctor/records/${id}/versions/${number}/hash`, { signal }),
  upload: (patient, file) => { const body = new FormData(); body.append('file', file); return request(`/doctor/patients/${patient}/attachments`, { method: 'POST', body }); },
  download: (id, number, file) => request(`/doctor/records/${id}/versions/${number}/attachments/${file}`, { blob: true }),
};
export const specialties = [[1,'Nội khoa'],[2,'Tim mạch'],[3,'Nội tiết'],[4,'Tổng quát'],[5,'Tiêu hóa'],[6,'Thần kinh']];
