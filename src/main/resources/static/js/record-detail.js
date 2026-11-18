if (!Auth.requireLogin()) throw new Error("not logged in");

const $ = (id) => document.getElementById(id);

const STATUS_MAP = {
  COMPLETED:      { cls: "bg-success-subtle text-success border-success-subtle", text: "Đã hoàn thành" },
  PENDING_RESULT: { cls: "bg-warning-subtle text-warning border-warning-subtle", text: "Chờ kết quả" },
  CANCELLED:      { cls: "bg-danger-subtle text-danger border-danger-subtle",   text: "Đã hủy" },
};

const GENDER_MAP = { MALE: "Nam", FEMALE: "Nữ", OTHER: "Khác" };

function setText(id, val) {
  const el = $(id);
  if (el) el.textContent = val ?? "—";
}

function renderLines(text) {
  if (!text) return '<li class="text-muted">Không có thông tin</li>';
  return text.split("\n")
    .filter(line => line.trim() !== "")
    .map(line => `<li>${escapeHtml(line)}</li>`)
    .join("");
}

async function loadRecordDetail() {
  if ($("navUserName") && Auth.fullName) {
    $("navUserName").textContent = Auth.fullName;
  }
  if ($("btnLogout")) {
    $("btnLogout").addEventListener("click", () => {
      Auth.clear();
      location.href = "/login.html";
    });
  }

  const params = new URLSearchParams(location.search);
  const id = params.get("id");
  if (!id) {
    alert("Không tìm thấy mã hồ sơ!");
    location.href = "records.html";
    return;
  }

  try {
    const r = await apiFetch(`/patient/records/${id}`);

    // 1. Tiêu đề & Banner
    document.title = `Chi tiết hồ sơ khám - ${r.recordCode} | MediCare Profile`;
    setText("bcRecordCode", r.recordCode);
    setText("docRecordCode", r.recordCode);
    setText("docVisitAt", `${formatDate(r.visitAt)} · ${formatTime(r.visitAt)}`);
    setText("docVersionBadge", `v${r.currentVersion || 1}`);
    setText("docHeaderSpecialty", `Khoa ${r.specialtyName}`);

    const st = STATUS_MAP[r.status] || { cls: "bg-light text-dark", text: r.status };
    const statusBadge = $("docStatusBadge");
    if (statusBadge) {
      statusBadge.className = `badge border ${st.cls}`;
      statusBadge.textContent = st.text;
    }

    // 2. Thông tin hành chính
    setText("ptFullName", r.patientFullName);
    setText("ptCode", r.patientCode);
    setText("ptDob", `${formatDate(r.patientDob)} (${r.patientAge} tuổi)`);
    setText("ptGenderBlood", `${GENDER_MAP[r.patientGender] || "Khác"} · Nhóm ${r.patientBloodType || "—"}`);
    setText("docSpecialty", r.specialtyName);
    setText("docDoctor", `BS. ${r.doctorName}`);
    setText("docRoom", r.roomName || "—");
    setText("docVisitType", r.visitType || "—");

    // 3. Chỉ số sinh hiệu
        if (r.vitals) {
          setText("vsBp", r.vitals.bloodPressure || "—");
          setText("vsHr", r.vitals.heartRate || "—");
          setText("vsTemp", r.vitals.temperature || "—");
          setText("vsSpo2", r.vitals.spo2 || "—");
          setText("vsWeight", r.vitals.weight || "—");
          setText("vsHeight", r.vitals.height || "—");
          setText("vsBmi", r.vitals.bmi || "—");
        }

    // 4. Lý do khám & Chẩn đoán
    if ($("docReasonList")) $("docReasonList").innerHTML = renderLines(r.reason);
    setText("docDiagnosis", r.diagnosis || "Chưa có kết luận chẩn đoán");
    setText("docSecondaryDiagnosis", r.secondaryDiagnosis || "Không có");
    setText("docTreatmentDirection", r.treatmentDirection || "—");

    // 5. Lời dặn & Tái khám
    setText("docNote", r.note || "Không có ghi chú thêm.");
    if (r.nextVisitDate) {
      setText("docNextVisitDate", formatDate(r.nextVisitDate));
      setText("docNextVisitNote", r.nextVisitNote ? ` (${r.nextVisitNote})` : "");
    }

    // 6. Tệp đính kèm & Blockchain
    const attBox = $("docAttachmentsBox");
    if (attBox && r.attachments && r.attachments.length > 0) {
      attBox.innerHTML = r.attachments.map(f => `
        <a href="${escapeHtml(f.fileUrl)}" target="_blank" class="btn btn-sm btn-outline-success me-2 mb-2">
          <i class="bi bi-paperclip me-1"></i>${escapeHtml(f.fileName)} (${escapeHtml(f.fileSizeLabel || "")})
        </a>
      `).join("");
    }
    if (r.dataHash) setText("docDataHash", r.dataHash);
    if (r.chainStatus) {
      setText("docChainStatus", r.chainStatus);
      if (r.chainStatus === "CONFIRMED") {
        $("docChainStatus").className = "badge bg-success-subtle text-success border border-success-subtle";
      }
    }

    // 7. Header & Chữ ký Toa thuốc điện tử
    setText("rxRecordCode", r.recordCode);
    setText("rxDate", formatDate(r.visitAt));
    setText("rxPatientInfo", `${r.patientFullName} (${r.patientCode})`);
    setText("rxDoctorName", `BS. ${r.doctorName}`);

   // 8. Đơn thuốc điều trị
       const rxBody = $("rxTableBody");
       const items = r.prescriptions || [];

       if (items.length > 0) {
         rxBody.innerHTML = items.map((item, idx) => `
           <tr>
             <td class="fw-bold text-muted">${item.sortOrder || idx + 1}</td>
             <td>
               <div class="fw-bold text-dark">${escapeHtml(item.medicineName)}</div>
               ${item.activeIngredient ? `<div class="small text-muted">${escapeHtml(item.activeIngredient)}</div>` : ""}
               <div class="small text-success mt-1">${escapeHtml(item.dosageInstruction || "")}</div>
             </td>
             <td class="text-end fw-semibold text-nowrap">${escapeHtml(item.quantityText || "—")}</td>
           </tr>
         `).join("");
       } else if (r.prescriptionNote && r.prescriptionNote.trim() !== "") {
         const lines = r.prescriptionNote.split(/\r?\n/).filter(line => line.trim() !== "");
         rxBody.innerHTML = lines.map((line, idx) => `
           <tr>
             <td class="fw-bold text-muted">${idx + 1}</td>
             <td colspan="2" class="text-dark fw-medium">${escapeHtml(line.replace(/^\d+[\.\)]\s*/, ""))}</td>
           </tr>
         `).join("");
       } else {
         rxBody.innerHTML = `
           <tr>
             <td colspan="3" class="text-center py-4 text-muted">Không có đơn thuốc cho lần khám này</td>
           </tr>
         `;
       }
  } catch (err) {
    alert("Lỗi tải chi tiết hồ sơ: " + err.message);
    location.href = "records.html";
  }
}

loadRecordDetail();