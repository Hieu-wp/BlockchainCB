if (!Auth.requireLogin()) throw new Error("not logged in");

const $ = (id) => document.getElementById(id);

const STATUS_MAP = {
  COMPLETED:      { cls: "bg-success-subtle text-success border-success-subtle", text: "Đã hoàn thành" },
  PENDING_RESULT: { cls: "bg-warning-subtle text-warning border-warning-subtle", text: "Chờ kết quả" },
  CANCELLED:      { cls: "bg-danger-subtle text-danger border-danger-subtle",   text: "Đã hủy" },
};

const GENDER_MAP = { MALE: "Nam", FEMALE: "Nữ", OTHER: "Khác" };

function renderLines(text) {
  if (!text) return '<li class="text-muted">Không có thông tin</li>';
  return text.split("\n")
    .filter(line => line.trim() !== "")
    .map(line => `<li>${escapeHtml(line)}</li>`)
    .join("");
}

async function loadRecordDetail() {
  const params = new URLSearchParams(location.search);
  const id = params.get("id");
  if (!id) {
    alert("Không tìm thấy mã hồ sơ!");
    location.href = "records.html";
    return;
  }

  try {
    const r = await apiFetch(`/patient/records/${id}`);

    // Tiêu đề & Banner
    document.title = `Chi tiết hồ sơ khám - ${r.recordCode} | MediCare Profile`;
    $("bcRecordCode").textContent = r.recordCode;
    $("docRecordCode").textContent = r.recordCode;
    $("docVisitAt").textContent = `${formatDate(r.visitAt)} · ${formatTime(r.visitAt)}`;

    // Thông tin hành chính
    $("ptFullName").textContent = r.patientFullName;
    $("ptCode").textContent = r.patientCode;
    $("ptDob").textContent = `${formatDate(r.patientDob)} (${r.patientAge} tuổi)`;
    $("ptGenderBlood").textContent = `${GENDER_MAP[r.patientGender] || "Khác"} · Nhóm ${r.patientBloodType || "—"}`;
    $("docSpecialty").textContent = r.specialtyName;
    $("docDoctor").textContent = `BS. ${r.doctorName}`;

    // Lý do khám & Chẩn đoán
    $("docReasonList").innerHTML = renderLines(r.reason);
    $("docDiagnosis").textContent = r.diagnosis || "Chưa có kết luận chẩn đoán";
    $("docNote").textContent = r.note || "Không có ghi chú thêm.";

    // Đơn thuốc điều trị (cột phải cố định)
    const rxBody = $("rxTableBody");
    if (!r.prescriptions || r.prescriptions.length === 0) {
      rxBody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4">Không có đơn thuốc cho lần khám này</td></tr>';
    } else {
      rxBody.innerHTML = r.prescriptions.map((item, idx) => `
        <tr>
          <td class="align-top pt-3"><span class="rx-step">${idx + 1}</span></td>
          <td>
            <div class="fw-bold text-dark fs-6">${escapeHtml(item.medicineName)}</div>
            ${item.activeIngredient ? `<div class="small text-muted mb-1">Hoạt chất: ${escapeHtml(item.activeIngredient)}</div>` : ""}
            <div class="small text-primary fw-medium">
              <i class="bi bi-clock me-1"></i>${escapeHtml(item.dosageInstruction || "")}
            </div>
          </td>
          <td class="text-end fw-bold text-dark align-top pt-3">${escapeHtml(item.quantityText)}</td>
        </tr>
      `).join("");
    }
  } catch (err) {
    alert("Lỗi tải chi tiết hồ sơ: " + err.message);
    location.href = "records.html";
  }
}

loadRecordDetail();