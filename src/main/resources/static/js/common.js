const API_BASE = "/api";

const Auth = {
  get token() { return localStorage.getItem("token"); },
  get fullName() { return localStorage.getItem("fullName") || ""; },
  get role() { return localStorage.getItem("role"); },
  save(data) {
    localStorage.setItem("token", data.token);
    localStorage.setItem("fullName", data.fullName);
    localStorage.setItem("role", data.role);
  },
  clear() {
    localStorage.removeItem("token");
    localStorage.removeItem("fullName");
    localStorage.removeItem("role");
  },
  logout() {
    this.clear();
    location.href = "login.html";
  },
  // Gọi ở đầu mỗi trang cần đăng nhập
  requireLogin() {
    if (!this.token || this.role !== "PATIENT") {
      this.clear();
      location.href = "login.html";
      return false;
    }
    return true;
  },
};

/**
 * Gọi API, tự gắn token, tự xử lý lỗi.
 * - 401: xóa token và quay về trang đăng nhập
 * - Lỗi khác: ném Error với thông báo từ backend ({ "error": "..." })
 */
async function apiFetch(path, options = {}) {
  const { skipAuthRedirect, ...fetchOptions } = options;
  const headers = { "Content-Type": "application/json", ...(fetchOptions.headers || {}) };
  if (Auth.token) headers.Authorization = "Bearer " + Auth.token;

  let res;
  try {
    res = await fetch(API_BASE + path, { ...fetchOptions, headers });
  } catch (e) {
    throw new Error("Không kết nối được máy chủ. Hãy kiểm tra backend đã chạy chưa.");
  }

  if (res.status === 401 && !skipAuthRedirect) {
    Auth.clear();
    location.href = "login.html";
    throw new Error("Phiên đăng nhập đã hết hạn");
  }

  let data = null;
  try { data = await res.json(); } catch (e) { /* phản hồi không có body */ }

  if (!res.ok) {
    throw new Error((data && data.error) || `Lỗi ${res.status}`);
  }
  return data;
}

// Chống XSS: luôn escape dữ liệu từ server trước khi chèn vào innerHTML
function escapeHtml(value) {
  if (value === null || value === undefined) return "";
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

// "2026-10-28T08:30:00" -> "28/10/2026" và "08:30" (cắt chuỗi, tránh lệch múi giờ)
function formatDate(iso) {
  if (!iso) return "";
  const [y, m, d] = iso.slice(0, 10).split("-");
  return `${d}/${m}/${y}`;
}
function formatTime(iso) {
  return iso && iso.length >= 16 ? iso.slice(11, 16) : "";
}