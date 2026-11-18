let allLogs = [];

document.addEventListener("DOMContentLoaded", async () => {
    if (typeof Auth !== "undefined" && (!Auth.token || Auth.role !== "PATIENT")) {
        location.href = "/login.html";
        return;
    }

    initNavbarUser();
    bindFilterEvents();
    await loadAccessLogs();
});

function initNavbarUser() {
    const navUser = document.getElementById("navUserName");
    if (navUser && typeof Auth !== "undefined" && Auth.fullName) {
        navUser.textContent = Auth.fullName;
    }
    const btnLogout = document.getElementById("btnLogout");
    if (btnLogout) {
        btnLogout.addEventListener("click", () => {
            if (typeof Auth !== "undefined") Auth.clear();
            location.href = "/login.html";
        });
    }
}

async function loadAccessLogs() {
    const tbody = document.getElementById("logsTableBody");
    const errorBox = document.getElementById("pageError");
    try {
        allLogs = await apiFetch("/patient/access-logs");
        applyFilters();
    } catch (err) {
        errorBox.textContent = "Không thể tải nhật ký truy cập: " + err.message;
        errorBox.classList.remove("d-none");
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-danger">Lỗi tải dữ liệu</td></tr>`;
    }
}

function bindFilterEvents() {
    document.getElementById("filterCategory").addEventListener("change", applyFilters);
    document.getElementById("filterDate").addEventListener("change", applyFilters);
    document.getElementById("filterKeyword").addEventListener("input", applyFilters);
    document.getElementById("btnResetFilter").addEventListener("click", () => {
        document.getElementById("filterCategory").value = "";
        document.getElementById("filterDate").value = "";
        document.getElementById("filterKeyword").value = "";
        applyFilters();
    });
}

function applyFilters() {
    const category = document.getElementById("filterCategory").value;
    const dateVal = document.getElementById("filterDate").value;
    const keyword = document.getElementById("filterKeyword").value.trim().toLowerCase();

    const filtered = allLogs.filter(item => {
        if (category && item.category !== category) return false;
        if (dateVal && item.timestamp && !item.timestamp.startsWith(dateVal)) return false;
        if (keyword) {
            const haystack = [
                item.doctorName,
                item.specialtyName,
                item.recordCode,
                item.description,
                item.txHash
            ].filter(Boolean).join(" ").toLowerCase();
            if (!haystack.includes(keyword)) return false;
        }
        return true;
    });

    document.getElementById("totalLogsCount").textContent = filtered.length;
    renderLogsTable(filtered);
}

function renderLogsTable(list) {
    const tbody = document.getElementById("logsTableBody");
    if (!list || list.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center py-5 text-muted">
                    <i class="bi bi-inbox fs-3 d-block mb-2"></i>
                    Chưa có dữ liệu nhật ký phù hợp.
                </td>
            </tr>`;
        return;
    }

    tbody.innerHTML = list.map(item => {
        const timeStr = formatDateTime(item.timestamp);
        const badgeHtml = getActionBadge(item.actionType, item.actionLabel);
        const shortWallet = item.doctorWallet
            ? `<div class="mt-1"><span class="wallet-pill" title="${item.doctorWallet}">${item.doctorWallet.slice(0, 6)}...${item.doctorWallet.slice(-4)}</span></div>`
            : "";

        const recordBadge = item.recordCode
            ? `<span class="badge bg-success-subtle text-success border border-success-subtle me-1">${item.recordCode}</span>`
            : "";

        const txHtml = item.txHash
            ? `<a href="https://sepolia.etherscan.io/tx/${item.txHash}" target="_blank" rel="noopener" class="tx-link" title="Xem giao dịch trên Sepolia Etherscan">
                   <i class="bi bi-box-arrow-up-right"></i>${item.txHash.slice(0, 8)}...${item.txHash.slice(-6)}
               </a>`
            : `<span class="text-muted small">—</span>`;

        return `
            <tr>
                <td class="ps-4 text-nowrap">
                    <div class="fw-semibold text-dark">${timeStr.date}</div>
                    <div class="small text-muted">${timeStr.time}</div>
                </td>
                <td>
                    <div class="fw-bold text-dark">${escapeHtml(item.doctorName)}</div>
                    <div class="small text-muted">${escapeHtml(item.specialtyName)}</div>
                    ${shortWallet}
                </td>
                <td>${badgeHtml}</td>
                <td>
                    <div class="mb-1">${recordBadge}<span class="text-dark">${escapeHtml(item.description)}</span></div>
                    ${item.dataHash ? `<div class="small text-muted font-monospace">Hash: ${item.dataHash.slice(0, 14)}...</div>` : ""}
                </td>
                <td class="text-end pe-4">${txHtml}</td>
            </tr>
        `;
    }).join("");
}

function getActionBadge(type, label) {
    const map = {
        GRANT_REQUEST:  "bg-warning-subtle text-warning-emphasis border border-warning-subtle",
        GRANT_ACTIVE:   "bg-success-subtle text-success border border-success-subtle",
        GRANT_REVOKED:  "bg-danger-subtle text-danger border border-danger-subtle",
        GRANT_REJECTED: "bg-secondary-subtle text-secondary border border-secondary-subtle",
        RECORD_CREATED: "bg-primary-subtle text-primary border border-primary-subtle",
        RECORD_UPDATED: "bg-info-subtle text-info-emphasis border border-info-subtle"
    };
    const cls = map[type] || "bg-light text-dark border";
    return `<span class="badge ${cls} px-2 py-1 fw-semibold">${escapeHtml(label)}</span>`;
}

function formatDateTime(isoStr) {
    if (!isoStr) return { date: "—", time: "" };
    const d = new Date(isoStr);
    const date = d.toLocaleDateString("vi-VN");
    const time = d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    return { date, time };
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}