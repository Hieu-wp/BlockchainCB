if (!Auth.requireLogin()) throw new Error("not logged in");

const $ = (id) => document.getElementById(id);

const SEPOLIA_CHAIN_ID = "0xaa36a7"; // 11155111
const ETHERSCAN_TX_URL = "https://sepolia.etherscan.io/tx/";

const AVATAR_GRADIENTS = [
  "linear-gradient(135deg, #0d6efd, #20c997)",
  "linear-gradient(135deg, #6f42c1, #0dcaf0)",
  "linear-gradient(135deg, #fd7e14, #ffc107)",
  "linear-gradient(135deg, #20c997, #0d6efd)",
];

let linkedPatientWallet = null;

function shortAddress(addr) {
  if (!addr || addr.length < 10) return addr || "Chưa có ví";
  return `${addr.slice(0, 6)}...${addr.slice(-4)}`;
}

function getInitials(fullName) {
  if (!fullName) return "BS";
  const parts = fullName.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[parts.length - 2][0] + parts[parts.length - 1][0]).toUpperCase();
}

function showAlert(type, htmlMsg) {
  const box = $("pageAlert");
  box.className = `alert alert-${type} shadow-sm`;
  box.innerHTML = htmlMsg;
  box.classList.remove("d-none");
  box.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

// Kiểm tra MetaMask, chuyển sang mạng Sepolia và xác thực đúng ví đã liên kết của bệnh nhân
async function getValidatedContract() {
  if (!window.ethereum) {
    throw new Error("Trình duyệt chưa cài đặt ví MetaMask!");
  }
  if (!linkedPatientWallet) {
    throw new Error("Tài khoản của bạn chưa liên kết ví MetaMask. Vui lòng quay lại trang Hồ sơ BN để liên kết ví trước.");
  }

  const provider = new ethers.BrowserProvider(window.ethereum);
  const accounts = await provider.send("eth_requestAccounts", []);
  const currentWallet = accounts[0];

  if (currentWallet.toLowerCase() !== linkedPatientWallet.toLowerCase()) {
    throw new Error(
      `Sai tài khoản ví MetaMask! Hệ thống yêu cầu ví đã liên kết (${shortAddress(linkedPatientWallet)}), nhưng MetaMask đang chọn ví (${shortAddress(currentWallet)}).`
    );
  }

  // Tự động yêu cầu chuyển sang mạng Sepolia nếu đang ở mạng khác
  const network = await provider.getNetwork();
  if (network.chainId !== 11155111n) {
    try {
      await window.ethereum.request({
        method: "wallet_switchEthereumChain",
        params: [{ chainId: SEPOLIA_CHAIN_ID }],
      });
    } catch (switchErr) {
      throw new Error("Vui lòng chuyển mạng trên MetaMask sang Sepolia Testnet để tiếp tục.");
    }
  }

  const signer = await provider.getSigner();
  return new ethers.Contract(CONTRACT_ADDRESS, CONTRACT_ABI, signer);
}

function renderPending(items) {
  $("pendingCount").textContent = items.length;
  if (items.length === 0) {
    $("pendingList").innerHTML =
      '<div class="text-center py-4 text-muted small">Không có yêu cầu nào đang chờ xác nhận.</div>';
    return;
  }

  $("pendingList").innerHTML = items.map((g, idx) => {
    const bg = AVATAR_GRADIENTS[idx % AVATAR_GRADIENTS.length];
    const timeStr = g.createdAt ? `${formatDate(g.createdAt)} ${formatTime(g.createdAt)}` : "—";
    const walletTag = g.doctorWallet
      ? `<span class="wallet-pill" title="${escapeHtml(g.doctorWallet)}"><i class="bi bi-wallet2 me-1"></i>${shortAddress(g.doctorWallet)}</span>`
      : `<span class="badge bg-danger-subtle text-danger">BS chưa liên kết ví</span>`;

    return `
      <div class="request-item" data-id="${g.id}" data-wallet="${escapeHtml(g.doctorWallet || "")}" data-name="${escapeHtml(g.doctorName)}">
        <div class="d-flex flex-wrap align-items-center gap-3">
          <div class="doctor-avatar" style="background: ${bg};">${getInitials(g.doctorName)}</div>
          <div class="flex-grow-1">
            <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
              <strong>BS. ${escapeHtml(g.doctorName)}</strong>
              <span class="specialty-tag">${escapeHtml(g.specialtyName)}</span>
              ${walletTag}
              <span class="badge badge-pending rounded-pill">Chờ xác nhận</span>
            </div>
            <div class="small text-muted">
              <i class="bi bi-hospital me-1"></i>${escapeHtml(g.hospitalName || "Phòng khám Đa khoa MediCare")}
              <span class="mx-1">·</span>
              <i class="bi bi-clock me-1"></i>Gửi lúc: ${timeStr}
            </div>
          </div>
          <div class="d-flex gap-2 flex-shrink-0">
            <button type="button" class="btn btn-accept btn-sm" data-action="approve">
              <i class="bi bi-check-lg me-1"></i>Chấp nhận
            </button>
            <button type="button" class="btn btn-reject btn-sm" data-action="reject">
              <i class="bi bi-x-lg me-1"></i>Từ chối
            </button>
          </div>
        </div>
      </div>`;
  }).join("");
}

function renderActive(items) {
  $("activeCount").textContent = items.length;
  if (items.length === 0) {
    $("activeList").innerHTML =
      '<div class="text-center py-4 text-muted small">Bạn chưa cấp quyền nhập hồ sơ cho bác sĩ nào.</div>';
    return;
  }

  $("activeList").innerHTML = items.map((g, idx) => {
    const bg = AVATAR_GRADIENTS[idx % AVATAR_GRADIENTS.length];
    const sinceStr = g.respondedAt ? formatDate(g.respondedAt) : formatDate(g.createdAt);
    const txLink = g.txHash
      ? `<a href="${ETHERSCAN_TX_URL}${escapeHtml(g.txHash)}" target="_blank" rel="noopener" class="text-decoration-none ms-1" title="Xem giao dịch trên Etherscan">
           <i class="bi bi-box-arrow-up-right"></i> Tx
         </a>`
      : "";

    return `
      <div class="doctor-row" data-id="${g.id}" data-wallet="${escapeHtml(g.doctorWallet || "")}" data-name="${escapeHtml(g.doctorName)}">
        <div class="d-flex flex-wrap align-items-center gap-3">
          <div class="doctor-avatar sm" style="background: ${bg};">${getInitials(g.doctorName)}</div>
          <div class="flex-grow-1">
            <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
              <strong>BS. ${escapeHtml(g.doctorName)}</strong>
              <span class="specialty-tag">${escapeHtml(g.specialtyName)}</span>
              <span class="wallet-pill" title="${escapeHtml(g.doctorWallet || "")}">${shortAddress(g.doctorWallet)}</span>
              <span class="badge badge-active rounded-pill">Đang hoạt động</span>
            </div>
            <div class="small text-muted">
              <i class="bi bi-hospital me-1"></i>${escapeHtml(g.hospitalName || "Phòng khám Đa khoa MediCare")}
              <span class="mx-1">·</span>
              <i class="bi bi-calendar-check me-1"></i>Cấp từ: ${sinceStr}${txLink}
            </div>
          </div>
          <button type="button" class="btn btn-revoke" data-action="revoke">
            <i class="bi bi-slash-circle me-1"></i>Thu hồi
          </button>
        </div>
      </div>`;
  }).join("");
}

function setRowButtonsDisabled(row, disabled) {
  row.querySelectorAll("button").forEach(b => { b.disabled = disabled; });
}

async function handleAction(e) {
  const btn = e.target.closest("button[data-action]");
  if (!btn) return;

  const row = btn.closest("[data-id]");
  const grantId = row.dataset.id;
  const doctorWallet = row.dataset.wallet;
  const doctorName = row.dataset.name;
  const action = btn.dataset.action;

  const oldHtml = btn.innerHTML;
  setRowButtonsDisabled(row, true);

  try {
    // 1. TỪ CHỐI (Chỉ cập nhật DB, không tốn phí gas)
    if (action === "reject") {
      if (!confirm(`Từ chối yêu cầu xin quyền nhập hồ sơ của BS. ${doctorName}?`)) {
        setRowButtonsDisabled(row, false);
        return;
      }
      btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>Đang xử lý...`;
      await apiFetch(`/patient/access-grants/${grantId}/reject`, { method: "POST" });
      showAlert("secondary", `<i class="bi bi-x-circle me-1"></i>Đã từ chối yêu cầu của <strong>BS. ${escapeHtml(doctorName)}</strong>.`);
      await loadAll();
      return;
    }

    // Kiểm tra ví bác sĩ trước khi gọi Smart Contract
    if (!doctorWallet || !ethers.isAddress(doctorWallet)) {
      throw new Error(`Tài khoản BS. ${doctorName} chưa có địa chỉ ví Ethereum hợp lệ nên không thể ghi lên Smart Contract.`);
    }

    const contract = await getValidatedContract();

    // 2. CHẤP NHẬN -> Gọi Smart Contract grantAccess(doctorWallet)
    if (action === "approve") {
      btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>Xác nhận MetaMask...`;
      const tx = await contract.grantAccess(doctorWallet);

      btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>Đang ghi Block...`;
      showAlert(
        "info",
        `<div class="d-flex align-items-center gap-2">
           <div class="spinner-border spinner-border-sm"></div>
           <span>Đang chờ mạng Sepolia xác nhận cấp quyền cho <strong>BS. ${escapeHtml(doctorName)}</strong>...
             <a href="${ETHERSCAN_TX_URL}${tx.hash}" target="_blank" rel="noopener" class="fw-semibold ms-1">Xem Tx</a>
           </span>
         </div>`
      );

      const receipt = await tx.wait();

      await apiFetch(`/patient/access-grants/${grantId}/approve`, {
        method: "POST",
        body: JSON.stringify({ txHash: receipt.hash }),
      });

      showAlert(
        "success",
        `<i class="bi bi-check-circle-fill me-1"></i>Đã cấp quyền thành công trên Blockchain cho <strong>BS. ${escapeHtml(doctorName)}</strong>!
         <a href="${ETHERSCAN_TX_URL}${receipt.hash}" target="_blank" rel="noopener" class="fw-semibold ms-2">Xem giao dịch</a>`
      );
    }

    // 3. THU HỒI -> Gọi Smart Contract revokeAccess(doctorWallet)
    if (action === "revoke") {
      if (!confirm(`Bạn chắc chắn muốn thu hồi quyền nhập hồ sơ của BS. ${doctorName} trên Blockchain?`)) {
        setRowButtonsDisabled(row, false);
        return;
      }
      btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>Xác nhận MetaMask...`;
      const tx = await contract.revokeAccess(doctorWallet);

      btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>Đang thu hồi...`;
      showAlert(
        "info",
        `<div class="d-flex align-items-center gap-2">
           <div class="spinner-border spinner-border-sm"></div>
           <span>Đang chờ mạng Sepolia xác nhận thu hồi quyền của <strong>BS. ${escapeHtml(doctorName)}</strong>...
             <a href="${ETHERSCAN_TX_URL}${tx.hash}" target="_blank" rel="noopener" class="fw-semibold ms-1">Xem Tx</a>
           </span>
         </div>`
      );

      const receipt = await tx.wait();

      await apiFetch(`/patient/access-grants/${grantId}/revoke`, {
        method: "POST",
        body: JSON.stringify({ txHash: receipt.hash }),
      });

      showAlert(
        "warning",
        `<i class="bi bi-slash-circle-fill me-1"></i>Đã thu hồi quyền truy cập của <strong>BS. ${escapeHtml(doctorName)}</strong> trên Blockchain.
         <a href="${ETHERSCAN_TX_URL}${receipt.hash}" target="_blank" rel="noopener" class="fw-semibold ms-2">Xem giao dịch</a>`
      );
    }

    await loadAll();
  } catch (err) {
    const msg = err.code === "ACTION_REJECTED"
      ? "Bạn đã hủy ký giao dịch trên MetaMask."
      : (err.reason || err.shortMessage || err.message);
    showAlert("danger", `<i class="bi bi-exclamation-triangle-fill me-1"></i>${escapeHtml(msg)}`);
    setRowButtonsDisabled(row, false);
    btn.innerHTML = oldHtml;
  }
}

async function loadAll() {
  const [profile, grants] = await Promise.all([
    apiFetch("/patient/me"),
    apiFetch("/patient/access-grants"),
  ]);

  linkedPatientWallet = profile.walletAddress || null;

  $("ptHeaderInfo").innerHTML =
    `Bệnh nhân: <strong>${escapeHtml(profile.fullName)}</strong> · Mã BN: <strong>${escapeHtml(profile.patientCode)}</strong>`;

  if (linkedPatientWallet) {
    $("walletBadge").innerHTML =
      `<div class="badge bg-success-subtle text-success border border-success-subtle px-3 py-2 fs-6 fw-normal">
         <i class="bi bi-wallet2 me-1"></i>Ví định danh: <strong>${shortAddress(linkedPatientWallet)}</strong>
       </div>`;
  } else {
    $("walletBadge").innerHTML =
      `<a href="/records.html" class="btn btn-sm btn-warning fw-semibold">
         <i class="bi bi-exclamation-triangle me-1"></i>Chưa liên kết ví MetaMask
       </a>`;
  }

  renderPending(grants.filter((g) => g.status === "PENDING"));
  renderActive(grants.filter((g) => g.status === "ACTIVE"));
}

// Gắn sự kiện
$("pendingList").addEventListener("click", handleAction);
$("activeList").addEventListener("click", handleAction);
$("btnLogout").addEventListener("click", () => Auth.logout());
$("navUserName").textContent = Auth.fullName;

loadAll().catch((err) => showAlert("danger", escapeHtml(err.message)));