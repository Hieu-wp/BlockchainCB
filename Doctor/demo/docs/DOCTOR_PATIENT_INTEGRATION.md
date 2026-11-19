# Doctor ↔ Patient — bàn giao từng giai đoạn, 18/11/2026

> Cập nhật luồng ghi 19/11/2026: [Tạo hồ sơ và kê đơn](../../../docs/LUONG_TAO_HO_SO_VA_KE_DON.md).
> Đã có source/test writer public; cần duyệt SQL metadata phụ trước khi dùng Supabase. Các mục dưới là mốc trước đó.

> Phối hợp cả ba module: xem [Hợp đồng database chung Admin / Doctor / Patient](../../../docs/SHARED_DATABASE_CONTRACT.md).
> Tài liệu chung ghi rõ bảng chuẩn, ý nghĩa từng ID, phần Doctor đã làm và phần Patient/Admin chưa xác nhận.

## 1. Kết quả và ranh giới hiện tại

| Giai đoạn | Kết quả |
|---|---|
| A — ví | Source/local test: PENDING/APPROVED ký được, proof và users.wallet_address cùng transaction; rollback được kiểm thử. Ví đã verified không bị yêu cầu ký lại. Đổi ví vẫn cần review, không có API gán ví không chữ ký. |
| B — xin quyền | Có UI, API và SQL repository ghi PENDING vào **public.access_grants**, chống hai request đồng thời từ Doctor. Mặc định tắt chờ xác nhận schema. |
| C — nhận quyền | Khi bật shared mode, GET patients đọc trực tiếp public.access_grants; ACTIVE + revoked=false + chưa hết hạn mới hiển thị. Đã test trên H2 mô phỏng Patient commit trạng thái. Chưa test API Patient thật. |
| D — bệnh án chung | **Đã có source và test đọc public**: lịch sử, chi tiết, phiên bản, thuốc và sinh hiệu theo quyền hiện hành. **Chưa bật ghi public**: chưa có code Patient và chưa thống nhất ACTIVE cho CREATE/UPDATE. Giữ nguyên hồ sơ/hashes cũ, không copy hay đổi FK thật. |
| E — SHA/anchor | Hồi quy legacy vẫn được test. Không thay canonical v1, contract, ABI hoặc giao dịch. Anchor của dữ liệu public chưa sẵn sàng vì D chưa hoàn tất và proof/grantWrite của Patient chưa kiểm chứng. |

**Không tuyên bố đã hoàn thiện end-to-end trên Supabase/Patient thật.** Không có source Patient/Admin trong checkout.
Tìm các tên listForPatient/updateGrantStatus/findPendingAndActiveByPatientId/findByIdAndPatientId chưa thấy implementation.
Ban đầu Pooler từ chối XX000; sau đó đã đọc pg_catalog thành công bằng transaction READ ONLY, đối chiếu cột/FK/index của patients, access_grants và năm bảng bệnh án chung. Chỉ đọc metadata, không đọc nội dung bệnh án.
Không chạy migration, ghi business data Supabase, cấp role, grantWrite, giao dịch Sepolia hoặc commit/push.

**Cập nhật mới nhất:** xem mục12 về luồng đọc bệnh án chung. Các mục9–11 ghi lại từng mốc kiểm thử trước đó;
nhận xét thiếu metadata tại những mốc cũ đã được giải quyết. API/source Patient và chính sách quyền ghi vẫn cần nhóm phối hợp.

## 2. Các file chính

| File | Hàm và trách nhiệm |
|---|---|
| blockchain/WalletLinkService.java | challenge: WALLET_ALREADY_VERIFIED khi proof đúng ví hiện tại; verify giữ transaction chữ ký/nonce/binding/User |
| frontend/src/blockchain/DoctorWalletPanel.jsx | Hiển thị địa chỉ MetaMask / proof Backend / DOCTOR_ROLE độc lập; không ký lại khi đã xác minh đúng ví |
| shared/SharedIntegrationSettings.java | Feature flag; chặn dùng ID public với record store legacy |
| shared/SharedGrantRepository.java | contract, lockPatient, open, insertPending, requests, visible; chỉ SQL public, metadata xác định FK Doctor |
| shared/SharedGrantService.java | request: principal → approved → metadata → khóa Patient → kiểm trùng → PENDING; đọc danh sách theo chính Doctor |
| shared/SharedGrantController.java | integration config, POST request, GET danh sách yêu cầu |
| service/PatientService.java | Shared mode: đọc bệnh nhân từ repository public, trả patientCode; legacy mode giữ API cũ |
| service/MedicalRecordService.java, AttachmentService.java | Guard SHARED_RECORDS_NOT_READY để không đọc/ghi nhầm ID giữa hai nguồn |
| frontend/src/AccessGrantRequests.jsx | Nhập patient_code, gửi yêu cầu, phân trang, kiểm tra trạng thái và refetch |
| frontend/src/App.jsx | Patients gắn component xin quyền; Dashboard/History chọn nguồn public khi bật shared mode; link riêng cho ID public |
| shared/SharedRecordController.java, SharedRecordService.java, SharedRecordRepository.java | GET lịch sử/chi tiết/phiên bản public, kiểm quyền hiện hành, chỉ SELECT và khóa consent trong transaction |
| dto/SharedRecordResponse.java, frontend/src/SharedRecordViews.jsx | DTO nội dung chung và UI chỉ đọc; không trả file_url hoặc coi stored hash là đã xác minh |
| frontend/src/api.js | integration(), requestAccess(), accessRequests(); thông báo guard nguồn dữ liệu rõ ràng |

Test mới: SharedGrantIntegrationTests, SharedGrantContractTest, AccessGrantRequests.test.jsx.
Test bổ sung: DemoApplicationTests (atomic rollback), WalletLinkServiceTest, DoctorWalletPanel.test.jsx,
api.test.js, App.test.jsx. FrontendDemoApplication được pin flag=false để fixture legacy không bị môi trường bên ngoài bật shared mode.

## 3. Hai chế độ nguồn dữ liệu

`doctor.integration.shared-grants-enabled=${DOCTOR_SHARED_GRANTS_ENABLED:false}`

- **false mặc định:** giữ Doctor legacy trong doctor_backend. API xin quyền mới trả503 SHARED_GRANTS_DISABLED;
  UI giải thích đang chờ tích hợp. Không tự thêm grant/public Patient fixture ở runtime.
- **true sau review:** quyền và danh sách bệnh nhân dùng public hoàn toàn; không sao chép sang doctor_backend.access_grants.
  API bệnh án/attachment/anchor đi qua MedicalRecordService bị chặn503 SHARED_RECORDS_NOT_READY.
  Không fallback sang legacy khi lỗi/thiếu quyền vì patient IDs có thể trùng số nhưng khác người.
- Cả hai mode vẫn validate các Entity legacy khi startup: không bỏ các bảng D hiện tại trong giai đoạn chuyển tiếp.
- Giữ ddl-auto=validate, sql.init.mode=never ở profile dùng Supabase. Flag không tạo/sửa bảng.

Không bật shared flag trên Supabase chỉ để thử. Cần review hợp đồng dưới đây và module Patient trước.
Sau khi đổi flag phải restart Java; không trộn dữ liệu ID của hai mode trong cùng phiên trình duyệt.

Profile opt-in `shared-grants` đã được thêm sau khi nhận ảnh hai bảng. Xem mục10 để chạy rõ chế độ xin quyền;
profile này không bật ghi hồ sơ public hoặc Blockchain.

## 4. Hợp đồng public tối thiểu đang hỗ trợ

Đây là contract được kiểm thử, **không phải DDL xác nhận từ Supabase**:

- public.patients: id BIGINT, user_id BIGINT FK users.id, patient_code kiểu chuỗi và unique toàn cục.
- public.access_grants: id BIGINT có generator/default, patient_id BIGINT FK patients.id,
  doctor_id BIGINT FK **doctors.id hoặc users.id**, status, revoked BOOLEAN, expires_at timestamp nullable.
- public.users: tài khoản chung hiện có, role PATIENT và enabled=true để Patient khả dụng.
- Metadata chọn actor.doctorId nếu FK tới doctors.id, actor.userId nếu tới users.id. Không suy từ số ID trùng nhau.
- FK thiếu/không hỗ trợ/composite, kiểu ID khác, mã không unique, extra NOT NULL không có default,
  hoặc timestamp bắt buộc cho PENDING sẽ bị chặn SHARED_SCHEMA_UNVERIFIED.
- Không hỗ trợ tự suy luận patient_code không unique, Patient không có user_id hoặc schema cần cột bắt buộc khác.
- Việc đọc metadata mới kiểm tra cấu trúc tối thiểu. CHECK enum/status, trigger, RLS, column privileges,
  chính sách expiry và hợp đồng Patient API vẫn phải được nhóm review trên PostgreSQL thật.

## 5. API Doctor

Tất cả cần JWT; xin quyền/list requests/patients cần Doctor APPROVED, enabled và không BLOCKED.

| Method | URL | Dữ liệu |
|---|---|---|
| GET | /api/doctor/integration | sharedGrantsEnabled, schemaReady, sharedRecordsReady=false, activeScope |
| POST | /api/doctor/access-grants/request | {"patientCode":"PC-001"} |
| GET | /api/doctor/access-grants?page=0&size=10 | Page các yêu cầu của chính Doctor, có patientCode/status/revoked/expiresAt/expired; expired do DB tính |
| GET | /api/doctor/patients?q=PC-001&page=0&size=10 | Shared mode: chỉ Patient có ACTIVE hợp lệ; DTO id/fullName/patientCode |
| GET | /api/doctor/wallet | Proof ví của chính Doctor (PENDING cũng được xem) |
| POST | /api/doctor/wallet/challenges | Tạo challenge cho ví chưa verified; không cần DOCTOR_ROLE |
| POST | /api/doctor/wallet/verify | Xác minh và lưu proof + users.wallet_address trong một transaction |

Ví dụ POST request trả200:

```json
{"id":12,"patientCode":"PC-001","status":"PENDING","existing":false}
```

Gửi trùng pending: cùng ID, existing=true. ACTIVE còn hiệu lực: trả cùng quyền ACTIVE, không tạo hàng mới.
Không nhận doctor_id/status từ client để quyết định quyền; các giá trị thừa không thay principal hoặc status server.
Không trả tên/địa chỉ/email/nội dung khám của Patient khi xin quyền, chỉ xác nhận mã người gửi đã nhập.
Chưa có rate limit tra mã/yêu cầu trong bản này; nhóm cần bổ sung trước vận hành nhiều người dùng.

Các lỗi chính:401 JWT;403 account/approval;404 PATIENT_CODE_UNAVAILABLE;409 DUPLICATE_GRANTS_REVIEW_REQUIRED,
ACCESS_REQUEST_SCHEMA_CONFLICT hoặc WALLET_ALREADY_VERIFIED;503 disabled/schema unverified/record store chưa sẵn sàng.
Nếu UNIQUE(patient_id,doctor_id) hiện có chặn xin lại quyền REVOKED/expired, trả409 và giữ hàng cũ;
không tự đổi status cũ về PENDING hoặc ACTIVE và làm mất lịch sử.

## 6. Giải thích luồng trước giảng viên

1. Doctor mở **Bệnh nhân** trong App.jsx. Trang gắn AccessGrantRequests và tải config để biết integration có sẵn sàng.
2. Bấm **Gửi yêu cầu cấp quyền khám** → AccessGrantRequests.submit() → api.requestAccess(patientCode)
   → POST /api/doctor/access-grants/request.
3. SharedGrantController.request() nhận DTO chỉ có patientCode, lấy DoctorPrincipal từ JWT filter.
4. SharedGrantService.request() yêu cầu APPROVED, đọc metadata để xác định loại Doctor FK,
   tìm/khóa Patient bằng patient_code và kiểm yêu cầu đang mở trong cùng transaction.
5. SharedGrantRepository.insertPending() ghi vào public.access_grants với PENDING, revoked=false, expires_at=NULL.
6. Response trả về UI: chờ bệnh nhân duyệt. Double-click chặn ở React; khóa hàng Patient giữ tới commit để hai JVM Doctor
   không cùng tạo hai yêu cầu. Parent lock không thay thế UNIQUE cho mọi writer khác: module có thể INSERT grant khác cần cùng quy tắc/constraint được nhóm duyệt.
7. Patient dùng **API của module Patient** để list/duyệt hàng này. Chưa biết path/method/DTO chính xác vì source không có;
   không tạo API Patient giả trong Doctor. Team Patient cần xác nhận list đọc cùng patient_id/doctor_id và scope Doctor FK.
8. Khi Patient commit ACTIVE, lần GET patients tiếp theo dùng cùng public.access_grants và thấy quyền ngay, không copy bảng.
   UI cập nhật mỗi30s/focus hoặc bấm **Kiểm tra Patient đã duyệt**. Backend lọc ACTIVE, revoked=false, expiry server.
9. ACTIVE không được tự diễn giải thành CREATE/UPDATE. Hiện chỉ mở danh sách Patient; bệnh án public bị chặn chờ quyết định và DDL.

Patient phải tự bảo vệ state transition PENDING→ACTIVE/REJECTED và ACTIVE→REVOKED, chỉ cho chủ Patient.
Các method được người dùng nêu chưa thể kiểm chứng. Doctor không có endpoint đổi status grant.
Test cập nhật H2 mô phỏng commit bên Patient chỉ chứng minh **Doctor đọc đúng thay đổi**, không chứng minh API Patient an toàn.

## 7. Phương án giai đoạn D cần nhóm thống nhất trước khi code

1. DBA xử lý giới hạn pooler, export columns/FK/check/index/trigger/RLS của public tables;
   dùng [READ_ONLY_doctor_patient_contract.sql](sql/READ_ONLY_doctor_patient_contract.sql), không gửi secret hoặc bệnh án thật.
2. Chốt ACTIVE có READ/CREATE/UPDATE nào; nếu có cột scope dùng đúng cột. Không thêm quyền bằng suy đoán từ status.
3. Inventory/backup hồ sơ D, version, attachment, hash, pending/confirmed anchor; không xóa hoặc renumber để ghép bảng.
4. Mapping Patient dựa User FK được xác minh; lập mapping old record/version/attachment ID sang ID public,
   xử lý collision, author Doctor-vs-User, khóa ngoại và quan hệ đơn thuốc/sinh hiệu theo version thật.
5. Thiết kế adapter public cho hồ sơ mới theo cột bắt buộc thực tế; không chỉ đổi @Table(schema).
   Tạo record + version + prescription_items + vital_signs + record_attachments trong một transaction,
   dùng principal, kiểm grant, version conflict và unique(record,version number) theo DDL đã duyệt.
6. Giữ record_versions append-only; Patient/Doctor không UPDATE snapshot cũ. Giữ canonical doctor-record-v1 và dữ liệu legacy
   như cũ; v2 có structured prescriptions/vitals/files với chuẩn hóa/order/units cố định. Không parse chuỗi thuốc cũ thành thuốc giả.
7. Không ghi lại hash cũ chỉ vì ID/schema thay đổi. Canonical v1 chứa IDs; adapter xác minh legacy cần metadata/mapping cũ.
   Bản mới tạo version mới, previousHash nối đúng phiên bản trước. Chọn route/namespace hoặc migration alias để numeric ID không trỏ nhầm record giữa nguồn.
8. RecordAnchor.recordId và AnchorAttempt.recordId/versionId hiện FK tới D. Đề xuất migration chuyển liên kết sau kiểm mapping,
   giữ recordKey, pinned Patient wallet, tx hash, block proof và frozen intent. Không tạo lại key hoặc anchor đè phiên bản cũ.
9. Team Patient xác nhận API xem record/versions/thuốc/tệp theo Patient principal và RLS; proof ví Patient và grantWrite còn là dependency.
10. Kiểm thử PostgreSQL cách ly + hai backend trước Supabase chung. Migration chỉ là đề xuất cần duyệt;
    rollback bằng tắt feature/quay app tương thích, giữ bảng và dữ liệu mới, không DROP/reset dữ liệu.

Hiện medical_records/record_versions/prescription_items/vital_signs/record_attachments của public **chưa được Doctor ghi**.
Legacy mode vẫn ghi D.medical_records/D.record_versions với prescription dạng text, D.attachments/D.version_attachments;
điều đó không được báo là Patient đã xem được bệnh án chung.

## 8. Chạy và kiểm thử an toàn

```powershell
cd C:\Users\ADMIN\BlockchainCB\Doctor\demo
.\mvnw.cmd -B -ntp verify
# Riêng flow public contract (H2, không Supabase):
.\mvnw.cmd -B -ntp '-Dtest=SharedGrantIntegrationTests,SharedGrantContractTest' test
cd ..\frontend
npm.cmd test -- --run
npm.cmd run build
```

SharedGrantIntegrationTests dùng DataSource H2 cố định + dynamic properties, không kế thừa DB_PASSWORD/RPC thật.
shared-grants-contract.sql nằm **src/test/resources**, chỉ tạo contract mẫu cho test, không phải migration Supabase.
Không tự thêm nó vào application-local hay profile mặc định. Fixture legacy frontend vẫn chạy như handoff trước.

Kiểm thử manual cùng Patient sau khi có source/DDL và group review:
Doctor APPROVED → request PC → kiểm public grant PENDING đúng FK → Patient thật list/accept bằng API đã kiểm
→ Doctor bấm kiểm tra → thấy mã Patient → revoke/expiry → Doctor không còn thấy Patient.
Chỉ chuyển sang tạo/xem hồ sơ sau khi giai đoạn D được triển khai và test; không sửa trực tiếp DB chung để giả kết quả.

## 9. Kết quả kiểm thử

| Kiểm tra | Kết quả thực tế |
|---|---|
| Maven verify | PASS,115 tests,0 failures/errors/skipped; compile và đóng gói thành công |
| SharedGrantIntegrationTests | PASS,10 HTTP/H2 tests: request, duplicate, race, hai Doctor, approve/revoke/expiry, FK tới User/Doctor, conflict xin lại, bảo vệ ID |
| SharedGrantContractTest | PASS,5 tests: hai kiểu FK, FK không hỗ trợ, cột bắt buộc thêm, feature gate |
| Wallet | PASS trong toàn bộ suite, thêm test không ký lại và rollback cả proof/User/nonce khi final User flush thất bại |
| SHA/JWT/Auth/Anchor legacy | Các test hiện có vẫn PASS trong suite115; không coi đó là kiểm thử public record store |
| React | PASS,59 tests trong6 test files; có test request DTO, double click, refetch trạng thái, ví và shared Dashboard |
| React build | PASS; cảnh báo JS bundle646,54 kB chưa chia chunk |
| git diff --check | PASS; Git có thông báo LF/CRLF |
| Supabase metadata | Chưa đọc được, Pooler XX000 giới hạn kết nối |
| Patient API thật / public bệnh án / MetaMask-Sepolia thật | CHƯA KIỂM THỬ; giai đoạn D và tích hợp E chưa hoàn thành |

So với baseline98 Java/50 React của bản wallet trước, bản này thêm17 Java và9 React: tổng115 Java/59 React.
Các test chỉnh constraint/UPDATE status chỉ chạy trên DataSource H2 đã pin; không có thao tác đó trên Supabase.
Không tạo API giả để khẳng định đã kiểm soát state transition bên Patient hoặc đã ghi Blockchain.

Hai thông tin còn chờ nhóm: source/API Patient thực tế + DDL đầy đủ của public clinical tables;
chính sách ACTIVE cho READ/CREATE/UPDATE. Không bật WRITE chỉ vì chưa nhận được câu trả lời.

## 10. Đối chiếu hai bảng từ ảnh Supabase và bước chạy tiếp theo

Nguồn: các ảnh kết quả information_schema.columns và pg_constraint do người dùng gửi 18/11/2026.
Không lấy dữ liệu bệnh nhân hoặc secret từ ảnh.

| Bảng | Cột đã thấy trong ảnh |
|---|---|
| patients | id BIGINT identity; user_id BIGINT NOT NULL; patient_code VARCHAR NOT NULL; date_of_birth DATE NOT NULL; gender VARCHAR NOT NULL; blood_type/phone/address VARCHAR nullable; follow_status VARCHAR NOT NULL default FOLLOWING |
| access_grants | id BIGINT identity; patient_id/doctor_id BIGINT NOT NULL; expires_at TIMESTAMP WITHOUT TIME ZONE nullable; revoked BOOLEAN NOT NULL default false; tx_hash VARCHAR nullable; created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL default now(); status VARCHAR NOT NULL default PENDING; responded_at TIMESTAMP WITHOUT TIME ZONE nullable |

Đã thấy FK access_grants.doctor_id→doctors.id, patient_id→patients.id, patients.user_id→users.id;
UNIQUE patients.patient_code và patients.user_id. Adapter vì vậy dùng actor.doctorId cho DB này.
Không thấy UNIQUE(patient_id,doctor_id) hoặc CHECK status trong kết quả constraint đã gửi;
chưa có kết quả standalone indexes để kết luận toàn bộ index của bảng.
CHECK gender/follow_status trong ảnh bị cắt nên không tự viết lại các biểu thức CHECK đó.
Không dùng follow_status thay consent ACTIVE và không tạo Patient từ Doctor API.

Fixture shared-grants-contract.sql đã cập nhật đủ các cột này (bổ sung DOB/gender cho Patient fixture),
thay TIMESTAMP WITH TIME ZONE bằng WITHOUT, bỏ CHECK status giả định, giữ default Pending/Following/created_at.
Độ dài VARCHAR và identity ALWAYS/BY DEFAULT chưa có trong ảnh nên fixture không được coi là full DDL production.

`expires_at` giữ dạng wall time khi trả DTO, không tự thêm Z/UTC. Backend trả thêm expired bằng cùng biểu thức
SQL dùng kiểm quyền. React dùng expired từ server, không quyết định hết hạn bằng múi giờ/đồng hồ trình duyệt.
Nhóm vẫn cần thống nhất cách ghi/đọc timestamp không có múi giờ giữa hai backend; không tự chuyển dữ liệu cũ sang UTC.

Sau khi review cùng Patient, có thể bật riêng xin quyền và danh sách bệnh nhân:

```powershell
cd C:\Users\ADMIN\BlockchainCB\Doctor\demo
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=shared-grants"
```

Dừng phiên Spring Boot cũ của chính Doctor trước khi chạy để tránh chiếm cổng8080 và giữ nhiều session pooler.
Giữ cấu hình DB/JWT hiện có trong môi trường phát triển, không đưa secret vào command line hoặc gửi cho người khác.
Không kết hợp profile này với local/test. Profile shared-grants chỉ bật nguồn quyền public, validate schema,
sql.init.mode=never, tắt Blockchain và giới hạn pool2 connections/min-idle0. Không tự dừng kết nối của teammate.
Nếu pooler vẫn hết session, cần nhóm giải quyết kết nối; thay feature flag không khắc phục được lỗi pooler.

React Vite: chạy npm.cmd run dev trong Doctor/frontend, tải lại trang và đăng nhập Doctor APPROVED.
GET /api/doctor/integration cần sharedGrantsEnabled=true,schemaReady=true,sharedRecordsReady=false.
Sau đó vào Bệnh nhân, nhập mã thật và tự gửi yêu cầu khi đã phối hợp test với Patient. Agent chưa gửi request ghi Supabase.
Doctor PENDING chỉ dùng hồ sơ/ví, không được xin quyền khám hoặc mở API nghiệp vụ.

Hồ sơ khám/thuốc/sinh hiệu/tệp chung tiếp tục chưa bật. Cần cột+FK của medical_records,record_versions,
prescription_items,vital_signs,record_attachments và source/API Patient trước khi làm tiếp D/E.

Kết quả cuối sau khi cập nhật theo ảnh: Maven verify117/117 PASS (0 failures/errors/skipped),
React60/60 PASS trong6 test files, production build PASS (cảnh báo JS bundle646,49 kB).
Hai test Java bổ sung kiểm default created_at/follow_status, tx_hash/responded_at nullable và timestamp không múi giờ;
test React bổ sung xác nhận UI dùng expired của Backend. Không thay kết quả lịch sử mục9.
Chưa chạy request ghi Supabase hoặc kiểm thử Patient API thật; không có migration mới cần áp dụng cho hai bảng này.

## 11. Sửa lỗi schema guard PostgreSQL bool

Ảnh lỗi sau khi bật shared-grants cho thấy GET integration/patients bị503. Bộ kiểm tra cũ trong
SharedGrantRepository.checkColumns() chỉ nhận revoked với DATA_TYPE=Types.BOOLEAN(16), giá trị H2 trả về.
Driver PostgreSQL42.7.14 đang dùng ánh xạ bool thành Types.BIT(-7). Vì vậy cột đúng schema vẫn bị guard từ chối.

Đã tái hiện bằng regression test: dùng TypeInfoCache của chính pgjdbc lấy mã bool và metadata adapter H2;
test trước sửa thất bại với SHARED_SCHEMA_UNVERIFIED. Sau sửa, guard chấp nhận BIT hoặc BOOLEAN
chỉ khi TYPE_NAME là bool/boolean; PostgreSQL bit string vẫn bị từ chối. Không bỏ kiểm tra FK/UNIQUE/ID hay JWT/quyền.

Đã đọc metadata Supabase thành công trong transaction READ ONLY và rollback: revoked=-7/bool,
id=-5/int8/identity YES, expires_at=93/timestamp, FK patient/user/doctor và unique patient_code đều khớp ảnh.
Chỉ đọc metadata, không đọc bệnh án, không ghi audit/business data. Chưa gọi API bằng JWT người dùng để test end-to-end.

Các sửa liên quan: lỗi lấy metadata/kết nối trả SHARED_METADATA_UNAVAILABLE thay vì báo sai schema;
AuditFilter trả AUDIT_UNAVAILABLE và vẫn fail-closed; api.js hiển thị đúng mã nguyên nhân trên Dashboard/patients,
không gán mọi503 thành lỗi lưu tệp/nhật ký. AccessGrantRequests dùng thông báo tương ứng.

Sau khi build, restart đúng Doctor Backend với profile shared-grants như mục10, tải lại Vite/đăng nhập lại.
GET integration kỳ vọng200 với schemaReady=true; GET patients có thể trả danh sách rỗng hợp lệ nếu chưa ACTIVE.
Nếu vẫn503, lấy code của response mới để phân biệt metadata, audit hoặc guard khác; không thay schema chỉ để vượt guard.

Kết quả cuối: Maven verify120/120 PASS (0 failures/errors/skipped), React63/63 PASS trong6 test files,
production build PASS (cảnh báo bundle647,15 kB). Test mới nhận PostgreSQL bool, từ chối bit string,
phân biệt lỗi kết nối metadata và kiểm tra thông báo503 theo code. Không cần migration hoặc giao dịch Blockchain cho bản sửa.

## 12. Đọc lịch sử và nội dung bệnh án chung

Ảnh trang Lịch sử hiển thị SHARED_RECORDS_NOT_READY vì UI cũ còn gọi `/api/doctor/records`,
API dành cho kho `doctor_backend`. Guard cố ý chặn khi shared mode bật để ID từ `public` không bị
dùng nhầm trong kho cũ. Bản này giữ guard đó, bổ sung endpoint chỉ đọc với namespace riêng và nối UI vào đúng nguồn.

### Schema đã kiểm chứng

Đã đọc metadata thực tế bằng JDBC PostgreSQL trong transaction READ ONLY rồi rollback;
không SELECT nội dung bệnh án, không tạo dữ liệu Supabase và không chạy migration.

| Bảng public | Liên kết và nội dung quan trọng |
|---|---|
| medical_records | patient_id → patients.id; doctor_id → doctors.id; specialty_id → specialties.id; record_code UNIQUE; current_version; visit_at/reason/status |
| record_versions | record_id → medical_records.id; UNIQUE(record_id,version_no); **created_by → users.id**; diagnosis/prescription/note và trường khám mở rộng; data_hash/tx_hash/chain_status |
| prescription_items | record_version_id → record_versions.id; sort_order, medicine_name, active_ingredient, dosage_instruction, quantity_text |
| vital_signs | record_version_id UNIQUE → record_versions.id; blood_pressure/heart_rate/temperature/spo2/weight/height/bmi |
| record_attachments | record_version_id → record_versions.id; file_name/file_type/file_size_label/file_url/uploaded_at |

Không đồng nhất `created_by` với Doctor ID: cột này tham chiếu User. Adapter trả `createdByUserId` để tránh hiểu nhầm.
Các timestamp là WITHOUT TIME ZONE, trả dạng wall time; không tự sửa timezone dữ liệu đang có.
RLS đang bật, FORCE RLS=false, không thấy policy hoặc trigger không nội bộ ở các bảng đã kiểm tra.
JDBC role owner có thể bypass RLS, vì vậy việc kiểm tra consent ở service/SQL vẫn bắt buộc. Không sửa RLS.

### API và luồng giao diện

| GET API | Công dụng |
|---|---|
| /api/doctor/shared-records?patientCode=&page=0&size=10 | Lịch sử do Doctor hiện tại tạo, lọc bằng mã bệnh nhân thật |
| /api/doctor/shared-patients/{patientId}/records?page=0&size=10 | Các lần khám của bệnh nhân được cấp quyền đọc hiện hành |
| /api/doctor/shared-records/{id} | Thông tin lần khám và nội dung phiên bản hiện tại |
| /api/doctor/shared-records/{id}/versions?page=0&size=10 | Danh sách phiên bản |
| /api/doctor/shared-records/{id}/versions/{number} | Nội dung phiên bản được chọn, thuốc/sinh hiệu/tên tệp của đúng phiên bản đó |

`GET /api/doctor/integration` bổ sung `sharedHistoryReady`. Khi schema đã khớp:
`sharedGrantsEnabled=true`, `schemaReady=true`, `sharedHistoryReady=true`, `sharedRecordsReady=false`.
Trường cuối giữ false vì **chức năng tạo/cập nhật bệnh án chung chưa bật**; không dùng nó để chặn luồng đọc mới.
Schema/cột/FK không khớp thì fail closed, không tự fallback sang kho cũ.

Khi nhấn **Lịch sử hồ sơ** trong App.jsx:
React kiểm tra integration → SharedRecordHistory → api.sharedRecords() → SharedRecordController.history()
→ SharedRecordService.history() → SharedRecordRepository.history() → SELECT public.medical_records,
patients, users, specialties và kiểm tra EXISTS public.access_grants → DTO phân trang → bảng React.
Nhấn mã hồ sơ mở `/shared-records/{id}`; trang gọi API detail/versions và hiển thị nội dung phiên bản,
đơn thuốc, sinh hiệu. Chọn một phiên bản gọi api.sharedVersion() để lấy nội dung đúng version_no.
Danh sách bệnh nhân đã cấp quyền có link `/shared-patients/{id}`; không đưa ID public vào route/API legacy.

Mọi endpoint nội dung lấy Doctor từ principal, nạp lại tài khoản APPROVED/enabled và yêu cầu grant ACTIVE,
revoked=false, chưa hết hạn; Patient User phải enabled và role PATIENT. Chi tiết không có quyền trả403,
không tiết lộ hồ sơ có tồn tại hay không. Service dùng transaction thường với SELECT FOR UPDATE trên grant
để việc thu hồi không chen giữa kiểm tra consent và đọc nội dung; không UPDATE grant trong các API đọc.
Không dùng @Transactional(readOnly=true) vì PostgreSQL không cho FOR UPDATE trong transaction READ ONLY.
AuditFilter hiện có ghi actor/đường dẫn/kết quả, không ghi nội dung y tế hoặc query chứa patientCode.
Các API đọc không sửa dữ liệu nghiệp vụ; request thực tế vẫn có audit log như thiết kế bảo mật hiện có.

React xóa nội dung cũ khi tải lại hoặc bị từ chối quyền, kiểm tra lại khi focus và mỗi30 giây.
Đây không phải thu hồi tức thời qua push; backend kiểm quyền ở mọi request mới. Không lưu bệnh án vào localStorage.

### Hash, tệp và khả năng tương thích

- Dữ liệu public chưa có canonical snapshot và liên kết previousHash như canonical v1 của kho Doctor cũ.
  API chỉ trả `storedDataHash`, `storedChainStatus`, `storedTransactionHash` kèm
  `hashVerification=UNVERIFIED_PUBLIC_CANONICAL`; kể cả DB ghi CONFIRMED, UI không tuyên bố đã xác minh.
- Không có nút anchor trên chi tiết public, không đổi HashService hay khóa recordKey cũ, không gửi giao dịch.
  Sidecar Blockchain cũ vẫn liên kết phiên bản doctor_backend; chưa được trỏ lại sang public.
- Chỉ trả metadata tệp, **không SELECT/trả file_url**, không cho tải trực tiếp khi chưa thống nhất private-storage contract.
- Không di chuyển/copy hồ sơ cũ. Lịch sử public rỗng là hợp lệ nếu Doctor chưa có hồ sơ trong public hoặc grant hết hiệu lực.
  Danh sách bệnh án của một bệnh nhân có thể gồm lần khám do bác sĩ khác tạo nếu có quyền đọc hiện hành của bệnh nhân.
- Không tạo POST/PATCH public clinical endpoints. Quyền ACTIVE không được tự suy diễn thành CREATE/UPDATE.

### Chạy và kiểm chứng

Restart Doctor Backend bằng profile `shared-grants` theo mục10, tải lại Vite rồi đăng nhập Doctor APPROVED.
Mở Lịch sử hồ sơ hoặc link Xem hồ sơ trên một bệnh nhân đang ACTIVE. Không phải chạy migration cho phần đọc này.
Nếu vẫn thấy thông báo guard cũ, kiểm tra API Network: UI mới gọi `/shared-records`, không gọi `/records` ở shared mode.

Kiểm thử local: Maven verify **125 PASS**, 0 failures/errors/skipped; React **68 PASS** trong7 test files;
Vite production build PASS, cảnh báo bundle655,82 kB. Test mới dùng H2/test fixtures cách ly:
lọc Doctor tác giả, grant pending/active/thu hồi/hết hạn, IDOR, Patient disabled, Doctor PENDING,
nội dung thuốc/sinh hiệu theo phiên bản, phân trang, metadata tệp không URL, hash chưa xác minh,
không mở public write hoặc fallback ID legacy. Test React kiểm tra link/filter, đổi phiên bản và xóa nội dung sau403.
Các fixture chỉ ở src/test/resources; không phải dữ liệu mẫu trả cho giao diện khi kết nối Supabase.

**Chưa kiểm thử end-to-end bằng JWT người dùng trên Supabase hoặc Patient API thật.** Đã kiểm chứng schema thật,
source và test local; cần người dùng kiểm tra kết quả read API sau restart. Bước tiếp theo cho ghi chung vẫn cần:
source/API Patient, quy ước ACTIVE cho CREATE/UPDATE, canonical hash và phương án bảo toàn bản ghi cũ/anchor sidecar,
private storage. Không tự cấp quyền, thay code Patient/Admin hoặc chạy migration để vượt các dependency này.
