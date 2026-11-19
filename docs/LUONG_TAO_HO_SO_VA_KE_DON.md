# Luồng tạo hồ sơ khám và kê đơn — MedLedger Doctor

Ngày19/11/2026. Source/local test đã triển khai. **Hai bảng phụ đã được tạo trên DB cấu hình Doctor theo yêu cầu bổ sung của người dùng**;
xem [biên bản áp dụng và kiểm chứng](../Doctor/demo/docs/sql/APPLIED_shared_clinical_v1_2026-11-19.md).
Chưa chạy API ghi bệnh án thật hoặc API Patient. Không chạy lại [script CREATE TABLE](../Doctor/demo/docs/sql/PROPOSED_shared_clinical_v1.sql).
Không chuyển/copy/xóa bệnh án cũ, không ghi hai nguồn, không tự gửi giao dịch Blockchain.

## 1. Trình bày trước giảng viên

“Khi tôi bấm **Chọn bệnh nhân để tạo hồ sơ khám** trên Dashboard, React chuyển đến `/patients`.
Trang chỉ trả bệnh nhân có quyền ACTIVE còn hiệu lực dành cho Doctor đang đăng nhập.
Nếu chưa có quyền, tôi nhập patient_code, gửi yêu cầu và chờ Patient duyệt.
Tôi bấm Xem hồ sơ của bệnh nhân, rồi Tạo hồ sơ khám để đến `/records/new?patientId=...`.
Form kiểm tra quyền qua Backend trước khi cho lưu. Tôi nhập thông tin khám, thuốc theo từng dòng và sinh hiệu.
Khi lưu, React gọi api.clinicalCreate(), gửi POST `/api/doctor/shared-records`.
ClinicalController.create() nhận DTO đã validation, gọi ClinicalService.create().
Service lấy Doctor/User từ principal, kiểm APPROVED/enabled và grant hiện hành, rồi dùng JdbcTemplate ghi
medical_records, record_versions, prescription_items, vital_signs, record_attachments trong một transaction.
Service tạo hash từ dữ liệu thực đã lưu, giữ canonical snapshot và hash liên kết trong bảng phụ Doctor.
Nếu bất kỳ bước nào lỗi, transaction rollback. Backend trả ID để React mở chi tiết hồ sơ chung.
Patient có thể JOIN các bảng public theo patient_id của chính mình để xem cùng bệnh án và đơn thuốc;
API Patient thực tế cần thành viên Patient đối chiếu, không coi test query SQL là đã test module Patient.”

## 2. File, hàm và dòng của source bàn giao

| File | Hàm / dòng | Trách nhiệm |
|---|---|---|
| Doctor/frontend/src/App.jsx | Dashboard:472, CTA:473 | Hướng dẫn chọn bệnh nhân trước, điều hướng /patients |
| Doctor/frontend/src/App.jsx | CreateRecordRoute:649 | Shared mode dùng form public; mode legacy giữ RecordForm cũ tại650 |
| Doctor/frontend/src/SharedRecordViews.jsx | SharedRecordHistory, CTA:47 | Bệnh án public theo bệnh nhân, nút tạo với patientId |
| Doctor/frontend/src/SharedClinicalForm.jsx | component:8, upload:21, submit:24 | Form phần A–E, thuốc nhiều dòng, validation, upload và lưu/chuyển trang |
| Doctor/frontend/src/api.js | clinicalAccess:70, clinicalCreate:71, clinicalUpdate:72, clinicalUpload:74 | Request API đúng namespace public, không gửi doctorId để tự xác định quyền |
| Doctor/demo/src/main/java/com/blockchaincb/demo/shared/ClinicalController.java | access:15, create:17, update:19, hash:21 | Endpoint, DTO validation và principal |
| Doctor/demo/src/main/java/com/blockchaincb/demo/shared/ClinicalRequest.java | record DTO | Thuốc và sinh hiệu typed, giới hạn theo cột đã đối chiếu |
| Doctor/demo/src/main/java/com/blockchaincb/demo/shared/ClinicalService.java | capability:71, create:80, update:88, append:103 | Authorization, transaction, root lock, ghi public/children và metadata |
| Doctor/demo/src/main/java/com/blockchaincb/demo/shared/ClinicalService.java | canonical:142, proof:150 | Tính/đối chiếu snapshot và nội dung thực, hash phiên bản trước |
| Doctor/demo/src/main/java/com/blockchaincb/demo/shared/ClinicalService.java | upload:167, download:179 | Tệp private, magic bytes, SHA và kiểm quyền khi tải |
| Doctor/demo/src/main/java/com/blockchaincb/demo/shared/SharedRecordRepository.java | history/detail/version reader | Đọc chung hồ sơ/phiên bản/thuốc/sinh hiệu/tên tệp |

Implementation ghi hiện dùng **JdbcTemplate trong ClinicalService**, không có JpaRepository/Entity public giả.
SharedRecordRepository tiếp tục là reader dùng chung. Không đổi Entity JPA legacy sang public vì hai mô hình khác nhau.

## 3. Các API mới

| Method / API | Request / response |
|---|---|
| GET /api/doctor/shared-patients/{patient}/clinical-access | canCreate, reason, patientCode, fullName sau khi kiểm quyền |
| POST /api/doctor/shared-records | ClinicalRequest → HTTP201 {id} |
| POST /api/doctor/shared-records/{id}/versions | {expectedVersion,content:ClinicalRequest} → HTTP201 {id} |
| POST /api/doctor/shared-patients/{patient}/uploads | multipart file → {id} của upload staging |
| GET /api/doctor/shared-records/{id}/versions/{number}/integrity | verified,hash,previousHash,testResults; không phải xác minh on-chain |
| GET /api/doctor/shared-records/{id}/versions/{number}/attachments/{file} | bytes private, no-store, kiểm quyền hiện hành |

Ví dụ request (fixture minh họa, không phải y lệnh hoặc dữ liệu thật):

```json
{
  "patientId": 23,
  "visitAt": "2026-01-01T10:30",
  "reason": "Lý do do bác sĩ nhập",
  "diagnosis": "Chẩn đoán do bác sĩ nhập",
  "secondaryDiagnosis": "",
  "testResults": "Kết quả do bác sĩ nhập",
  "treatmentDirection": "Hướng điều trị do bác sĩ nhập",
  "note": "",
  "visitType": "Ngoại trú",
  "roomName": "A1",
  "nextVisitDate": null,
  "nextVisitNote": "",
  "medicines": [
    {"medicineName": "Thuốc minh họa", "activeIngredient": "", "dosageInstruction": "Bác sĩ tự nhập", "quantityText": "Số lượng do bác sĩ nhập"}
  ],
  "vitals": {"bloodPressure": "", "heartRate": null, "temperature": null, "spo2": null, "weight": null, "height": null, "bmi": null},
  "uploadIds": [],
  "keepAttachmentIds": []
}
```

Ngày giờ khám, patientId và reason được giữ nguyên khi cập nhật một lần khám; thông tin chuyên môn thay đổi bằng phiên bản mới.
Thứ tự thuốc là thứ tự dòng trong form, Backend gán sort_order=1..N; xóa dòng chỉ ảnh hưởng phiên bản đang soạn.
Số lượng/đơn vị dùng cột quantity_text theo schema hiện có, không tự tạo trường quantity/unit chưa có trong DB.
Backend cũng tạo prescription dạng văn bản từ chính thuốc bác sĩ nhập để client Patient cũ đọc được bản tóm tắt;
nguồn thuốc chi tiết là prescription_items của đúng record_version_id.

## 4. Ghi vào bảng nào và kiểm quyền thế nào

| Bảng | Khi tạo / cập nhật |
|---|---|
| public.medical_records | Tạo root với mã ngẫu nhiên29 ký tự; patient_id của Patient public, doctor_id của Doctor principal, specialty_id từ hồ sơ Doctor; cập nhật chỉ current_version |
| public.record_versions | INSERT version_no mới, created_by=users.id, chain_status=NONE, data_hash=0x+SHA256 |
| public.prescription_items | INSERT từng thuốc theo version ID vừa sinh |
| public.vital_signs | Một hàng theo version khi DTO có vitals |
| public.record_attachments | Metadata theo version, file_url là reference nội bộ doctor-private:UUID, không phải URL public |
| doctor_backend.shared_version_proofs | Snapshot canonical, previous_hash, test_results và hash phiên bản; FK tới public.record_versions |
| doctor_backend.shared_uploads | Metadata private upload, owner User/Patient, storage key và SHA; không phải bệnh án thứ hai |

Grant phải ACTIVE, revoked=false, chưa hết hạn; Doctor APPROVED/enabled và Patient User enabled/PATIENT.
Nguồn quyền là public.access_grants, không copy sang kho grant legacy. URL ID không phải bằng chứng quyền.
Theo yêu cầu ghi mới, ACTIVE cho phép tạo; cập nhật đang giới hạn thêm **Doctor tác giả của root** và quyền hiện hành.
Patient không có endpoint sửa clinical data trong Doctor Backend. Thành viên Patient phải giữ cùng nguyên tắc ở API của họ.
Root bị khóa FOR UPDATE, expectedVersion sai trả409. Mỗi request append thuốc/sinh hiệu/tệp riêng, không UPDATE phiên bản cũ.
Không tự nhận phiên bản public cũ thiếu snapshot thành phiên bản do writer mới tạo; cập nhật bản đó bị chặn409 chờ review/migration riêng.

## 5. SHA-256 và Blockchain

Canonical mới có tên public-clinical-v1, dùng HashService.canonical length-prefix UTF8/NFC hiện có;
thêm marker NULL/VALUE, số decimal bỏ trailing zeros, timestamp không tự gán UTC.
Bao gồm patient/Doctor/specialty ID và visit_at/reason, nội dung và author/time từng phiên bản,
từng thuốc theo thứ tự, sinh hiệu, metadata tệp kèm SHA của upload và previous_hash.
Hash tính sau khi đọc lại giá trị DB để giá trị numeric/timestamp đã được DB làm tròn được xử lý nhất quán.
Không đưa data_hash/tx_hash/chain_status vào chính nội dung tự hash, không phụ thuộc current_version của root.
Proof API so stored snapshot hash, live content hash, data_hash và liên kết snapshot hash phiên bản trước.
Tải tệp kiểm lại hash bytes. Đây không phải chứng minh đã anchor hay full scan toàn bộ lịch sử; từng phiên bản có API verify riêng.

Giữ nguyên HashService schema doctor-record-v1, legacy MedicalRecordService và anchor sidecar cũ.
Không đổi hash đã có hoặc trỏ anchor_attempts sang public ID. Phiên bản public mới có chain_status NONE,
không tự anchor khi lưu. Tích hợp anchor cho public cần mapping/recordKey và contract permission riêng được review;
chưa có nút gửi giao dịch public trong bản này. Ví Doctor/Patient, DOCTOR_ROLE/grantWrite vẫn là điều kiện độc lập.

## 6. SQL và storage cần nhóm duyệt

SQL đề xuất chỉ thêm shared_version_proofs/shared_uploads vào doctor_backend và bật RLS cho hai bảng mới;
không ALTER/DROP bảng public hoặc copy dữ liệu cũ. Script không tự chạy vì sql.init.mode=never và ddl-auto=validate.
Trước áp dụng: backup, kiểm metadata/index/FK public, xác nhận chưa có hai bảng đó; review quyền JDBC/Patient reader và RLS.
Không chạy lại CREATE TABLE nếu bảng đã tồn tại. Rollback ưu tiên dừng ghi/revert code, giữ snapshot/metadata/tệp;
không DROP bằng chứng khi đã có hồ sơ thật.

Thiếu bảng phụ → capability canCreate=false và thông báo migration; ghi trực tiếp bị503 trước INSERT public.
Storage hiện dùng PrivateStorage và LocalPrivateStorage có sẵn. Local chỉ hoạt động khi cấu hình
`doctor.storage.local-enabled=true` và private root phù hợp; có thể truyền tham số ứng dụng
`--doctor.storage.local-enabled=true --doctor.storage.local-root=./.private-doctor-files` cho phiên demo được phép.
Chỉ bật local adapter cho demo/local được nhóm đồng ý; không giả định đây là Supabase Storage production.
Tệp không được serve static hoặc gửi URL công khai. Upload thất bại/không gắn hồ sơ có thể để lại file staging;
chưa có job dọn orphan, không tự xóa file để tránh mất tệp của phiên bản cũ.

## 7. Hợp đồng đọc phía Patient

Patient Backend phải lấy patients.id từ user principal, JOIN medical_records.patient_id,
record_versions.record_id, prescription_items/vital_signs/record_attachments.record_version_id.
Chọn đúng version_no/current_version, không gộp thuốc của mọi phiên bản thành một đơn hiện tại.
test_results nằm trong sidecar shared_version_proofs (public schema chưa có cột tương ứng),
cần review quyền đọc để Patient Backend lấy theo version_id sau khi xác minh ownership của Patient.
File reference doctor-private cần adapter download private chung hoặc API Patient có authorization riêng;
không dùng JWT Doctor thay Patient và không mở trực tiếp reference thành URL public.
Không chỉnh sửa source Patient/Admin trong lượt này. Query SQL theo Patient User trên H2 đã được test;
API Patient thật và private storage production chưa được tích hợp/kiểm thử.

## 8. Chạy thử và kiểm thử

Backend Doctor: chạy profile shared-grants như hướng dẫn hiện có. React: npm.cmd run dev, mở localhost:5173.
Luồng thủ công sau khi SQL và storage được nhóm phê duyệt: Dashboard → Chọn bệnh nhân → Xem hồ sơ
→ Tạo hồ sơ khám → thêm thuốc/sinh hiệu/tệp → Lưu → SharedRecordDetail → Kiểm tra SHA → Tạo phiên bản mới.
Không cần MetaMask để lưu bệnh án; không được tạo dữ liệu thật để thử khi chưa có sự đồng ý.

Test cách ly H2/mock: APPROVED/grant, PENDING/thu hồi, nhiều thuốc đúng version, sinh hiệu, rollback lỗi sau INSERT,
hash cũ giữ nguyên, tamper thuốc làm verify=false, stale/concurrent expectedVersion, file private/copy phiên bản,
Patient ownership query và missing migration fail-closed. React test thuốc nhiều dòng/lưu/chuyển trang,
URL không có quyền, migration thiếu và xóa dòng. Test wallet cũ được điều chỉnh matcher theo nhãn UI hiện tại,
không đổi code hoặc logic wallet của người dùng.

Kết quả cuối: Maven verify161/161 PASS, không failures/errors/skipped; React103/103 PASS trong11 test files;
Vite production build PASS (còn cảnh báo bundle669,83 kB). Đã test concurrent update và thiếu migration trên H2.
Chưa test ghi Supabase, chưa test API Patient thật, chưa áp dụng SQL đề xuất, chưa cấu hình private storage production.

## 9. Sửa lỗi mở form khi migration chưa có trên PostgreSQL

Đã tái hiện bằng mock PostgreSQL: SELECT trực tiếp bảng shared_version_proofs chưa tồn tại gây42P01,
transaction bị aborted; dù bắt exception, truy vấn Patient tiếp theo vẫn lỗi25P02. H2 không thể hiện trạng thái này.
ClinicalService.checkSidecar giờ kiểm tra cột qua DatabaseMetaData, không SELECT bảng có thể chưa tồn tại.
Thiếu bảng/cột trả capability200/canCreate=false/reason SHARED_WRITE_MIGRATION_REQUIRED;
gửi POST lưu trực tiếp vẫn503 trước tạo dữ liệu. Lỗi connection riêng trả DATABASE_UNAVAILABLE.
Frontend hiển thị form cùng thông báo thiếu migration và khóa lưu, hoặc HTTP status/nút kiểm tra lại nếu API lỗi.
Không tự chạy SQL để vượt điều kiện. Chưa xác nhận lỗi của phiên chạy thật nếu chưa có Network/log mới.
Tham khảo hành vi transaction aborted: [PostgreSQL Transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html).

Kiểm thử sau sửa:167 Java PASS,106 React PASS, Backend package và Vite build PASS.
Test mock tái hiện42P01→25P02 trước sửa và PASS sau sửa; H2 HTTP test kiểm capability trả200 với canCreate=false
khi thiếu bảng phụ. Chưa dùng JWT người dùng để xác minh phiên Supabase trong ảnh; không chạy migration.
