let token = null;
let generation = 0;
export function setToken(value) { token = value; generation++; }
export class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
export async function request(path, { method = 'GET', body, signal, blob = false } = {}) {
  const current = generation;
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  let response;
  try { response = await fetch(`/api${path}`, { method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined, signal, cache: 'no-store', credentials: 'omit' }); }
  catch (e) { if (e.name === 'AbortError') throw e; throw new ApiError(0, 'Không kết nối được máy chủ. Kiểm tra Backend và proxy local.'); }
  if (current !== generation) throw new DOMException('Phiên đã thay đổi', 'AbortError');
  if (!response.ok) {
    if (response.status === 401 && token) { setToken(null); window.dispatchEvent(new Event('doctor:expired')); }
    const messages = { 400: 'Dữ liệu không hợp lệ. Kiểm tra các trường và chuyên khoa.', 401: 'Phiên hết hạn hoặc email / mật khẩu không đúng. Vui lòng đăng nhập lại.', 403: 'Bạn không có quyền hiện hành cho thao tác này, hoặc tài khoản đã bị hạn chế.', 404: 'Không tìm thấy hồ sơ hoặc phiên bản.', 409: 'Dữ liệu trùng, đã thay đổi hoặc không còn toàn vẹn. Hãy tải lại trước khi lưu.', 413: 'Tệp vượt quá giới hạn 5 MiB.', 415: 'Định dạng gửi không được hỗ trợ.', 503: 'Dịch vụ lưu tệp hoặc nhật ký chưa sẵn sàng. Kiểm tra lịch sử trước khi thử gửi lại.' };
    throw new ApiError(response.status, messages[response.status] || 'Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại sau.');
  }
  return blob ? response.blob() : response.json();
}
export const api = {
  login: body => request('/auth/doctor/login', { method: 'POST', body }),
  register: body => request('/auth/doctor/register', { method: 'POST', body }),
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
