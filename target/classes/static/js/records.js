if (!Auth.requireLogin()) throw new Error("not logged in");  // dừng script nếu chưa đăng nhập

// ====== Cấu hình hiển thị ======
const PAGE_SIZE = 7;

const SPECIALTY_STYLE = {
  noikhoa:  "bg-primary-subtle text-primary",
  timmach:  "bg-danger-subtle text-danger",
  noitiet:  "bg-info-subtle text-info",
  tongquat: "bg-success-subtle text-success",
  tieuhoa:  "bg-warning-subtle text-warning",
  thankinh: "bg-secondary-subtle text-secondary",
};

const STATUS_VIEW = {
  COMPLETED:      { cls: "status-done",      text: "Đã hoàn thành" },
  PENDING_RESULT: { cls: "status-pending",   text: "Chờ kết quả" },
  CANCELLED:      { cls: "status-cancelled", text: "Đã hủy" },
};

const GENDER_VIEW = {
  MALE:   { text: "Nam",  icon: "bi-gender-male" },
  FEMALE: { text: "Nữ",   icon: "bi-gender-female" },
  OTHER:  { text: "Khác", icon: "bi-gender-ambiguous" },
};

// ====== Phần tử DOM ======
const $ = (id) => document.getElementById(id);
const els = {
  body: $("recordsBody"), pagination: $("pagination"), pageInfo: $("pageInfo"),
  date: $("filterDate"), specialty: $("filterSpecialty"), search: $("filterSearch"),
  error: $("pageError"),
};

let currentPage = 0;
let requestSeq = 0;   // bỏ qua kết quả của request cũ nếu người dùng bấm lọc liên tục

// ====== Hiển thị lỗi ======
function showError(msg) {
  els.error.textContent = msg;
  els.error.classList.remove("d-none");
}
function clearError() { els.error.classList.add("d-none"); }

// ====== 1. Thông tin bệnh nhân ======
async function loadProfile() {
  const p = await apiFetch("/patient/me");
  const g = GENDER_VIEW[p.gender] || GENDER_VIEW.OTHER;

  $("pFullName").textContent = p.fullName;
  $("pCode").textContent = p.patientCode;
  $("pDob").textContent = `${formatDate(p.dateOfBirth)} (${p.age} tuổi)`;
  $("pGender").textContent = g.text;
  $("pGenderIcon").className = `bi ${g.icon} me-1`;
  $("pBlood").textContent = p.bloodType || "—";
  $("pPhone").textContent = p.phone || "—";
  $("pAddress").textContent = p.address || "—";

  const follow = $("pFollow");
  if (p.followStatus === "FOLLOWING") {
    follow.className = "badge bg-success-subtle text-success";
    follow.innerHTML = '<i class="bi bi-check-circle me-1"></i> Đang theo dõi';
  } else {
    follow.className = "badge bg-secondary-subtle text-secondary";
    follow.innerHTML = '<i class="bi bi-pause-circle me-1"></i> Ngừng theo dõi';
  }

  // Hiển thị trạng thái ví Blockchain nếu đã liên kết
  if (p.walletAddress) {
    const shortAddr = p.walletAddress.slice(0, 6) + "..." + p.walletAddress.slice(-4);
    $("walletStatusBadge").innerHTML =
      `<span class="badge bg-success-subtle text-success"><i class="bi bi-check-circle-fill me-1"></i>${escapeHtml(shortAddr)}</span>`;
    const btn = $("btnConnectWallet");
    btn.classList.replace("btn-warning", "btn-outline-success");
    btn.innerHTML = `<i class="bi bi-arrow-repeat me-1"></i>Đổi ví`;
  }
}

// ====== 2. Dropdown chuyên khoa ======
async function loadSpecialties() {
  const list = await apiFetch("/specialties");
  els.specialty.innerHTML =
    '<option value="">Tất cả</option>' +
    list.map(s => `<option value="${escapeHtml(s.code)}">${escapeHtml(s.name)}</option>`).join("");
}

// ====== 3. Bảng danh sách ======
function renderRows(items) {
  if (items.length === 0) {
    els.body.innerHTML =
      '<tr><td colspan="7" class="text-center text-muted py-5">' +
      '<i class="bi bi-inbox fs-3 d-block mb-2"></i>Không có hồ sơ nào phù hợp</td></tr>';
    return;
  }

  els.body.innerHTML = items.map(r => {
    const st = STATUS_VIEW[r.status] || { cls: "", text: r.status };
    const spCls = SPECIALTY_STYLE[r.specialtyCode] || "bg-secondary-subtle text-secondary";
    return `
      <tr data-id="${r.id}" style="cursor: pointer;">
        <td class="ps-3 fw-semibold text-primary">${escapeHtml(r.recordCode)}</td>
        <td>
          <div>${formatDate(r.visitAt)}</div>
          <small class="text-muted">${formatTime(r.visitAt)}</small>
        </td>
        <td><span class="badge badge-specialty ${spCls}">${escapeHtml(r.specialtyName)}</span></td>
        <td>BS. ${escapeHtml(r.doctorName)}</td>
        <td>${escapeHtml(r.reason)}</td>
        <td><span class="badge ${st.cls} rounded-pill">${st.text}</span></td>
        <td class="text-end pe-3">
          <button type="button" class="btn btn-sm btn-outline-medical" title="Xem chi tiết">
            <i class="bi bi-eye"></i> Xem
          </button>
        </td>
      </tr>`;
  }).join("");
}

function renderPageInfo(d) {
  if (d.totalElements === 0) {
    els.pageInfo.textContent = "Không có hồ sơ";
    return;
  }
  const from = d.page * d.size + 1;
  const to = d.page * d.size + d.content.length;
  els.pageInfo.innerHTML =
    `Hiển thị <strong>${from}–${to}</strong> trong tổng số <strong>${d.totalElements}</strong> hồ sơ`;
}

function renderPagination(d) {
  if (d.totalPages <= 1) { els.pagination.innerHTML = ""; return; }

  const cur = d.page, last = d.totalPages - 1;
  let start = Math.max(0, cur - 2);
  let end = Math.min(last, start + 4);
  start = Math.max(0, end - 4);

  const item = (page, label, { active = false, disabled = false } = {}) =>
    `<li class="page-item ${active ? "active" : ""} ${disabled ? "disabled" : ""}">
       <a class="page-link" href="#" data-page="${page}">${label}</a>
     </li>`;

  let html = item(cur - 1, '<i class="bi bi-chevron-left"></i>', { disabled: cur === 0 });
  for (let i = start; i <= end; i++) html += item(i, i + 1, { active: i === cur });
  html += item(cur + 1, '<i class="bi bi-chevron-right"></i>', { disabled: cur === last });
  els.pagination.innerHTML = html;
}

async function loadRecords(page = 0) {
  const seq = ++requestSeq;
  clearError();
  els.body.innerHTML =
    '<tr><td colspan="7" class="text-center py-5">' +
    '<div class="spinner-border text-primary" role="status"></div></td></tr>';

  const params = new URLSearchParams({ page, size: PAGE_SIZE });
  if (els.date.value) params.set("date", els.date.value);
  if (els.specialty.value) params.set("specialty", els.specialty.value);
  if (els.search.value.trim()) params.set("keyword", els.search.value.trim());

  try {
    const d = await apiFetch("/patient/records?" + params);
    if (seq !== requestSeq) return;

    if (d.content.length === 0 && d.totalPages > 0 && page > d.totalPages - 1) {
      return loadRecords(d.totalPages - 1);
    }

    currentPage = d.page;
    renderRows(d.content);
    renderPageInfo(d);
    renderPagination(d);
  } catch (err) {
    if (seq !== requestSeq) return;
    els.body.innerHTML = "";
    els.pageInfo.textContent = "";
    els.pagination.innerHTML = "";
    showError(err.message);
  }
}

// ====== 4. Kết nối ví MetaMask ======
async function connectMetaMask() {
  if (!window.ethereum) {
    alert("Vui lòng cài đặt tiện ích MetaMask trên trình duyệt!");
    return;
  }
  try {
    const provider = new ethers.BrowserProvider(window.ethereum);
    const accounts = await provider.send("eth_requestAccounts", []);
    const walletAddress = accounts[0];

    const signer = await provider.getSigner();
    await signer.signMessage(`Lien ket vi ${walletAddress} voi tai khoan benh nhan`);

    await apiFetch("/patient/wallet", {
      method: "POST",
      body: JSON.stringify({ walletAddress })
    });

    alert("Liên kết ví thành công: " + walletAddress);
    location.reload();
  } catch (err) {
    alert("Lỗi kết nối ví: " + (err.reason || err.message));
  }
}

// ====== 5. Sự kiện ======
els.date.addEventListener("change", () => loadRecords(0));
els.specialty.addEventListener("change", () => loadRecords(0));

let searchTimer;
els.search.addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => loadRecords(0), 400);
});
els.search.addEventListener("keydown", (e) => {
  if (e.key === "Enter") { clearTimeout(searchTimer); loadRecords(0); }
});
$("btnSearch").addEventListener("click", () => { clearTimeout(searchTimer); loadRecords(0); });

$("btnReset").addEventListener("click", () => {
  els.date.value = "";
  els.specialty.value = "";
  els.search.value = "";
  loadRecords(0);
});

els.pagination.addEventListener("click", (e) => {
  e.preventDefault();
  const link = e.target.closest("a[data-page]");
  if (!link || link.parentElement.classList.contains("disabled")) return;
  loadRecords(Number(link.dataset.page));
});

// Bấm vào dòng hoặc nút "Xem" để chuyển sang trang chi tiết hồ sơ
els.body.addEventListener("click", (e) => {
  const tr = e.target.closest("tr[data-id]");
  if (tr) {
    location.href = `/record-detail.html?id=${tr.dataset.id}`;
  }
});

$("btnLogout").addEventListener("click", () => Auth.logout());

// ====== 6. Khởi động ======
$("navUserName").textContent = Auth.fullName;

(async function init() {
  try {
    await Promise.all([loadProfile(), loadSpecialties(), loadRecords(0)]);
  } catch (err) {
    showError(err.message);
  }
})();