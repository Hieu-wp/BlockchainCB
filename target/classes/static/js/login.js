
if (Auth.isLoggedIn && Auth.isLoggedIn() && Auth.role === "PATIENT") {
  window.location.href = "/records";
}

const $ = (id) => document.getElementById(id);

const els = {
  form: $("loginForm"),
  email: $("email"),
  password: $("password"),
  btnSubmit: $("btnLogin"),
  error: $("loginError"),
  togglePwd: $("togglePassword"),
};

function showError(msg) {
  if (!els.error) return alert(msg);
  els.error.textContent = msg;
  els.error.classList.remove("d-none");
}

function clearError() {
  if (!els.error) return;
  els.error.classList.add("d-none");
  els.error.textContent = "";
}

function setLoading(loading) {
  if (!els.btnSubmit) return;
  els.btnSubmit.disabled = loading;
  els.btnSubmit.innerHTML = loading
    ? '<span class="spinner-border spinner-border-sm me-2"></span>Đang đăng nhập...'
    : "Đăng nhập";
}

// Ẩn/hiện mật khẩu (nếu trên giao diện có nút con mắt)
if (els.togglePwd && els.password) {
  els.togglePwd.addEventListener("click", () => {
    const isPwd = els.password.type === "password";
    els.password.type = isPwd ? "text" : "password";
    els.togglePwd.innerHTML = isPwd
      ? '<i class="bi bi-eye-slash"></i>'
      : '<i class="bi bi-eye"></i>';
  });
}

// Xử lý submit form đăng nhập
els.form.addEventListener("submit", async (e) => {
  e.preventDefault();
  clearError();

  const email = els.email.value.trim();
  const password = els.password.value;

  if (!email || !password) {
    showError("Vui lòng nhập đầy đủ email và mật khẩu.");
    return;
  }

  setLoading(true);

  try {
    // Gọi API đăng nhập của Spring Boot (/api/auth/login)
    // Tùy cách cấu hình base URL trong apiFetch: nếu apiFetch tự gắn "/api" thì giữ "/auth/login"
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
      throw new Error(data.message || "Email hoặc mật khẩu không chính xác.");
    }

    // Kiểm tra vai trò từ bảng users: chỉ cho phép PATIENT vào cổng bệnh nhân
    if (data.role !== "PATIENT") {
      throw new Error("Tài khoản này không phải tài khoản Bệnh nhân.");
    }

    // Lưu thông tin đăng nhập (khớp với Auth.fullName trong records.js)
    if (typeof Auth.saveSession === "function") {
      Auth.saveSession(data);
    } else {
      localStorage.setItem("token", data.token || data.accessToken);
      localStorage.setItem("fullName", data.fullName || data.full_name || "");
      localStorage.setItem("role", data.role);
      if (data.walletAddress) {
        localStorage.setItem("walletAddress", data.walletAddress);
      }
    }

    // Chuyển hướng sang trang danh sách hồ sơ bệnh án
    window.location.href = "/records";
  } catch (err) {
    showError(err.message || "Đăng nhập thất bại, vui lòng thử lại.");
  } finally {
    setLoading(false);
  }
});