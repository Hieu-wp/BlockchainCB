# Bàn giao Java Backend Doctor

> C?p nh?t Doctor?Patient 18/11/2026: xem [DOCTOR_PATIENT_INTEGRATION.md](DOCTOR_PATIENT_INTEGRATION.md) v? feature flag ngu?n quy?n public, API xin quy?n v? gi?i h?n b?nh ?n chung. C?c k?t qu? test b?n d??i c? th? l? k?t qu? l?ch s? c?a phi?n tr??c.

Ngày: 16/11/2026. Phạm vi: Java 17, Spring Boot 4.1.2, JWT, JPA, off-chain records.

**Hoàn thiện về source/local test; chưa hoàn thành tích hợp Supabase và module nhóm.**
Không triển khai Solidity, Hardhat, Web3j, MetaMask, Sepolia hoặc giao dịch Ethereum.
`NOT_SUBMITTED` luôn có nghĩa là chưa gửi hash lên blockchain.

## 1. Chức năng và mức độ kiểm chứng

| Chức năng | Source/local | Tích hợp chung |
|---|---|---|
| Đăng ký User + Doctor, BCrypt, transaction, chuyên khoa | Có test HTTP/JPA, duplicate email và rollback | Không đăng ký lại trên Supabase |
| Login bằng `users.password_hash`, role và enabled | Unit test + HTTP test | Chưa chạy lại với dữ liệu nhóm |
| Bearer JWT, stateless, 401/403 | Test token, HTTP và thay đổi quyền sau cấp token | Chưa tích hợp React/Patient/Admin |
| Hồ sơ cá nhân, cập nhật tên/bệnh viện | Có HTTP test; không cho cập nhật role/approval/email/license/ví | Cần thống nhất dữ liệu trùng |
| Danh sách bệnh nhân có quyền, phân trang/tìm tên | Có test H2 | Chờ mapping Patient và dữ liệu cấp quyền |
| Xem/tạo hồ sơ, quyền READ/CREATE/UPDATE riêng | Có test H2, kiểm tra đoán ID và thu hồi quyền | Chờ schema được nhóm duyệt |
| Phiên bản bất biến, optimistic locking, lịch sử | Có test liên kết/hash và hai writer đồng thời | Chưa kiểm tra locking trên PostgreSQL thật |
| SHA-256, chuẩn hóa, kiểm tra nội dung và liên kết | Có vector chuẩn, Unicode, sửa dữ liệu ngoài app | Chưa có trusted anchor on-chain |
| Tệp private | Interface + adapter local, test tải xuống và thu hồi | **Chưa có storage production** |
| Audit HTTP | ATTEMPT và SUCCESS/DENIED/ERROR; chỉ metadata | Chưa vận hành với DB nhóm |

Kết quả kiểm thử: **39 test, 0 failure, 0 error**, gồm 6 AuthService, 23 HTTP/JPA integration,
3 SHA-256, 3 JWT, 2 private storage và 2 audit filter. Maven Wrapper 3.10.0 đã chạy `clean verify` thành công,
biên dịch Java 17 và đóng gói executable JAR.
Chi tiết xem `target/surefire-reports`; thư mục này không commit.
Không dùng kết quả H2 để khẳng định Supabase đã tương thích.

## 2. Cấu trúc và luồng hoạt động

- `AuthController → AuthService → UserRepository/DoctorRepository/SpecialtyRepository`.
- Đăng ký: validate DTO → kiểm tra chuyên khoa/email → băm BCrypt → lưu User → Doctor trong một transaction.
- `JwtAuthenticationFilter → JwtService → DoctorIdentityService → SecurityContext`.
- Token dùng subject là **users.id**, issuer `blockchaincb-doctor`, audience `doctor-api`, hạn một giờ.
- `doctorId` trong token chỉ là metadata; quyền và Doctor ID thực tế được nạp từ DB bằng User ID.
- Token cũ có subject email không dùng được sau thay đổi này; cần đăng nhập lại.
- `DoctorController → MedicalRecordService → PatientAccessPolicy → Repository`.
- `DatabasePatientAccessPolicy` kiểm tra quyền off-chain, thời điểm bắt đầu/hết hạn/thu hồi.
- `HashService` độc lập với lưu trữ và blockchain.
- `PrivateStorage` độc lập với nghiệp vụ; adapter local không phục vụ thư mục qua static URL.
- `AuditFilter → AuditService` ghi riêng transaction tại ranh giới HTTP sau khi xác thực danh tính.

Không có endpoint Admin hoặc Patient mới. Doctor không có API tự cấp quyền hoặc tự duyệt.

## 3. Quy tắc trạng thái và quyền

| Trạng thái | Login và `/me` | API bệnh nhân/bệnh án/tệp |
|---|---|---|
| PENDING | Cho phép | 403 |
| APPROVED | Cho phép | Cần quyền bệnh nhân tương ứng |
| REJECTED | Cho phép để xem trạng thái | 403 |
| BLOCKED ở status hoặc approval_status | 403 | 403 |
| users.enabled=false / role khác DOCTOR | 403 | 403 |
| approval_status không xác định | 403 | 403 |

`approval_status` là nguồn phê duyệt. `status` cũ chỉ được dùng như cờ BLOCKED bổ sung.
Đăng ký tiếp tục lưu cả hai là PENDING để tương thích.

Mọi API nghiệp vụ đều kiểm tra APPROVED trong service, ngoài việc Security yêu cầu role DOCTOR.
CREATE/UPDATE hiện yêu cầu cả READ và quyền hành động tương ứng để trả được DTO nội dung.
UPDATE được phép cho bác sĩ được cấp quyền UPDATE, không chỉ người tạo ban đầu.
Lịch sử `/records` chỉ lấy hồ sơ do mình tạo **và còn quyền READ hiện hành**.
Sau thu hồi, không thể đọc nội dung/hash/phiên bản/tệp, kể cả là người tạo.

Grant đang dùng được khóa PESSIMISTIC_READ đến hết transaction bệnh án. Việc thu hồi bằng UPDATE cùng hàng
sẽ tuần tự với thao tác đang chạy; request đã được cấp quyền và đang thực thi có thể hoàn tất trước thu hồi.
Request tiếp theo phải thấy quyền mới. Danh sách bệnh nhân dùng snapshot quyền tại thời điểm truy vấn.
Không cache quyền trong JWT.

## 4. Danh sách API

Mọi route `/api/doctor/**` dùng `Authorization: Bearer <token>`.
Phân trang: `page` từ 0, `size` 1–100, mặc định 20. Kết quả Page có `content`, `totalElements` và thông tin trang.

| Method | URL | Mô tả |
|---|---|---|
| POST | `/api/auth/doctor/register` | Đăng ký; giữ HTTP 200 và trường cũ để tương thích |
| POST | `/api/auth/doctor/login` | Login; token và approvalStatus |
| GET | `/api/doctor/me` | Hồ sơ/trạng thái bản thân |
| PATCH | `/api/doctor/me` | Cập nhật fullName, hospital |
| GET | `/api/doctor/patients?q=&page=0&size=20` | Tìm theo tên trong danh sách được cấp quyền |
| GET | `/api/doctor/patients/{patientId}/records` | Hồ sơ của bệnh nhân được READ |
| POST | `/api/doctor/records` | Tạo hồ sơ và version 1; HTTP 201 |
| GET | `/api/doctor/records?patientId=&page=0&size=20` | Lịch sử do mình tạo; patientId tùy chọn |
| GET | `/api/doctor/records/{id}` | Hồ sơ và phiên bản hiện tại |
| POST | `/api/doctor/records/{id}/versions` | Thêm phiên bản; HTTP 201 |
| GET | `/api/doctor/records/{id}/versions` | Phiên bản theo số giảm dần, phân trang |
| GET | `/api/doctor/records/{id}/versions/{number}` | Chi tiết phiên bản |
| GET | `/api/doctor/records/{id}/versions/{number}/hash` | Hash và kiểm tra off-chain, vẫn yêu cầu READ |
| POST | `/api/doctor/patients/{patientId}/attachments` | Multipart `file`; HTTP 201 |
| GET | `/api/doctor/records/{id}/versions/{number}/attachments/{attachmentId}` | Download sau kiểm tra quyền và liên kết phiên bản |

401: thiếu/sai/hết hạn token hoặc sai mật khẩu.
403: tài khoản không được phép, chưa APPROVED hoặc thiếu quyền bệnh nhân.
400: dữ liệu/JSON/chuyên khoa không hợp lệ. 404: không có tài nguyên.
409: email trùng, version cũ, xung đột optimistic lock hoặc nội dung mất toàn vẹn.
413: upload quá lớn. 415: Content-Type sai. 503: storage/audit chưa sẵn sàng.
Lỗi không trả SQL, stack trace, password/hash mật khẩu hoặc JWT.

## 5. Ví dụ request/response

Đăng ký (dùng email mới trong môi trường local, không tự tạo tài khoản trên Supabase):

```json
{"fullName":"Doctor Demo","email":"doctor.demo@example.test","password":"LocalDemo@123","licenseNumber":"LOCAL-LICENSE","hospital":"Local Hospital","specialtyId":2}
```

```json
{"message":"Dang ky thanh cong","id":1,"email":"doctor.demo@example.test","status":"PENDING","approvalStatus":"PENDING"}
```

Login:

```json
{"email":"doctor.demo@example.test","password":"LocalDemo@123"}
```

```json
{"message":"Dang nhap thanh cong","token":"<JWT>","tokenType":"Bearer","id":1,"fullName":"Doctor Demo","email":"doctor.demo@example.test","status":"PENDING","approvalStatus":"PENDING"}
```

Cập nhật hồ sơ cá nhân:

```json
{"fullName":"Doctor Demo","hospital":"New Hospital"}
```

Tạo hồ sơ (chỉ khi đã APPROVED và có quyền; ID dưới đây minh họa):

```json
{"patientId":1,"examDate":"2026-11-16","diagnosis":"Chan doan demo","prescription":"Don thuoc demo","testResults":"","notes":"","attachmentIds":[]}
```

Response có dạng:

```json
{"id":10,"patientId":1,"createdBy":2,"createdAt":"2026-11-16T10:00:00Z","updatedAt":"2026-11-16T10:00:00Z","currentVersion":1,"version":{"id":20,"versionNumber":1,"doctorId":2,"createdAt":"2026-11-16T10:00:00Z","previousVersionId":null,"previousHash":null,"examDate":"2026-11-16","diagnosis":"Chan doan demo","prescription":"Don thuoc demo","testResults":"","notes":"","attachmentIds":[],"contentHash":"<64 hex characters>","canonicalSchema":"doctor-record-v1","blockchainStatus":"NOT_SUBMITTED","blockchainTransactionHash":null,"blockchainRecordId":null}}
```

Thêm phiên bản:

```json
{"expectedVersion":1,"content":{"patientId":1,"examDate":"2026-11-16","diagnosis":"Chan doan bo sung","prescription":"Don thuoc moi","testResults":"Ket qua moi","notes":"","attachmentIds":[]}}
```

Client phải gửi lại toàn bộ nội dung phiên bản; danh sách attachmentIds là danh sách hoàn chỉnh cho phiên bản mới.
`patientId` không được thay đổi. `doctorId` được lấy từ principal, không nhận làm quyền từ request.
Attachment cũ thuộc phiên bản trước có thể được giữ lại bởi bác sĩ khác có quyền UPDATE.
Attachment mới phải do chính bác sĩ hiện tại tải lên cho đúng bệnh nhân.

Upload multipart trả `id`, `mediaType`, `sizeBytes`, `sha256`; dùng `id` vào `attachmentIds`.
Không trả storage key hoặc public URL. Download dùng `Content-Disposition: attachment`, `no-store`, `nosniff`.

Hash response:

```json
{"recordId":10,"versionNumber":1,"algorithm":"SHA-256","canonicalSchema":"doctor-record-v1","hash":"<64 hex characters>","previousHash":null,"blockchainStatus":"NOT_SUBMITTED","storedSnapshotMatchesHash":true,"currentContentMatchesHash":true,"previousLinkMatches":true}
```

Ba cờ chỉ kiểm tra dữ liệu off-chain hiện tại, không phải xác nhận từ blockchain.

## 6. Chạy và kiểm thử

Java 17 và Maven cần có sẵn; Maven Wrapper đi kèm repository.

```powershell
cd C:\Users\ADMIN\BlockchainCB\Doctor\demo
.\mvnw.cmd -B -ntp clean verify
```

Nếu Wrapper không khởi động được trên máy, dùng Maven đã cài: `mvn -B -ntp clean verify`.
Trong phiên triển khai, Maven 3.9.11 dùng cho các lượt compile/test đầu; lượt build sạch cuối dùng Wrapper 3.10.0.

Test không yêu cầu DB_PASSWORD/JWT_SECRET: có secret chỉ dành cho test và DataSource H2 cố định.
`DemoApplicationTests` cung cấp bean DataSource H2 riêng để tránh dùng URL/credential môi trường.
Test khởi tạo SQL và dùng `ddl-auto=validate`; không dùng Hibernate để sửa schema.
Test cleanup DELETE chỉ chạy trên H2 này. Không chạy test tích hợp vào Supabase.

Chạy local:

1. Cấu hình `JWT_SECRET` trong môi trường riêng: chuỗi Base64 của khóa ngẫu nhiên ít nhất 32 byte.
2. Không đặt secret thật trong source, lệnh được chia sẻ, `.env` được commit hoặc tài liệu.
3. Chạy:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Profile local dùng H2 memory, các bảng local và seed sáu chuyên khoa; mất dữ liệu khi tắt ứng dụng.
Không seed tài khoản, trạng thái APPROVED hoặc consent giả trong runtime production.
Luồng demo đầy đủ được dựng bằng fixture trong `DemoApplicationTests`.
Việc Admin duyệt và Patient cấp quyền trong ứng dụng thật cần module nhóm.
Tệp local lưu tại `.private-doctor-files`, được gitignore; file test nằm trong `target/test-private-storage`.

Profile mặc định dùng cấu hình Supabase hiện có, DB_PASSWORD và JWT_SECRET.
`spring.sql.init.mode=never`, `ddl-auto=validate`; **không chạy profile mặc định để thử schema mới chưa được duyệt**.
Nếu thiếu bảng `doctor_backend`, khởi động sẽ fail validation thay vì tự tạo bảng.

## 7. Database và migration đề xuất

Không có kết nối Supabase hoặc truy vấn schema thật trong phiên triển khai.
Chỉ có schema `users/doctors/specialties` do bạn cung cấp và mapping cũ trong source.
`docs/sql/READ_ONLY_schema_preflight.sql` dành cho nhóm kiểm tra cột, constraint và duplicate.
`docs/sql/PROPOSED_doctor_backend_v1.sql` là đề xuất cần duyệt, không được tự động chạy.
Không cài Flyway/Liquibase và không có migration tự động.

| Bảng | Mục đích |
|---|---|
| public.users | Tài khoản chung, nguồn password_hash/role/enabled |
| public.doctors | Hồ sơ bác sĩ, user_id, specialty_id, trạng thái |
| public.specialties | Kiểm tra chuyên khoa |
| doctor_backend.patient_links | Cầu nối tới users.id của Patient |
| doctor_backend.access_grants | Quyền READ/CREATE/UPDATE, hiệu lực, thu hồi |
| doctor_backend.medical_records | Hồ sơ gốc, creator, current_version, lock_version |
| doctor_backend.record_versions | Snapshot nội dung, hash, author, liên kết trước |
| doctor_backend.attachments | Metadata, digest, private storage key |
| doctor_backend.version_attachments | Attachment theo từng phiên bản, giữ thứ tự |
| doctor_backend.audit_logs | Actor User ID, HTTP method, resource, kết quả và thời gian |

**patient_links.id là ID cầu nối của Doctor module, không mặc định bằng patients.id của teammate.**
Patient/Admin cần duyệt cách mapping này hoặc thay adapter/repository bằng schema chung đã thống nhất.
Chưa tự tạo hay thay đổi `public.patients` hoặc bảng quyền của teammate.

Điều kiện tích hợp:

- `users.email` cần UNIQUE thật để ngăn đăng ký đồng thời trùng email.
- Một User chỉ nên có một Doctor; nhóm cần xác nhận UNIQUE `doctors.user_id`.
- Mapping User trong Doctor dùng ManyToOne để không tự áp đặt UNIQUE chưa xác minh.
  Nếu có nhiều Doctor cho cùng User, truy vấn định danh phải fail, không chọn tùy tiện.
- Xác nhận NULL/độ dài/check constraint của các cột tương thích, license_no/license_number và password legacy.
- Xác nhận `patient_links.user_id` chỉ liên kết tài khoản PATIENT.
- Chỉ Patient/Admin/module consent được cấp quyền ghi grant; Doctor API không cung cấp quyền đó.
- SQL proposal chứa FK và UNIQUE version; bản local cùng cấu trúc được kiểm tra bằng H2.
- H2 không thay thế kiểm thử PostgreSQL, pooler, timezone, isolation và quyền DB thật.

## 8. SHA-256 và quy tắc canonical v1

Không hash `toString()`, JSON tùy thứ tự key hoặc file name do client cung cấp.
Chuẩn hóa text: Unicode NFC, CRLF/CR thành LF; không trim nội dung y tế.
Chuỗi Unicode có surrogate lỗi bị từ chối. Số nguyên dùng biểu diễn thập phân Java,
ngày ISO yyyy-MM-dd, Instant UTC ISO-8601, timestamp được cắt đến microsecond trước khi lưu/hash.

Mỗi field được mã hóa thành `<số byte UTF-8>:<giá trị>`, ghép liên tiếp theo thứ tự cố định.
Null chỉ xuất hiện ở metadata phiên bản đầu và được mã hóa chuỗi rỗng; các trường nội dung bắt buộc khác null.
Ví dụ `canonical("é","x") = "2:é1:x"`.

Thứ tự fields trong `MedicalRecordService.canonical`:

1. canonical schema = doctor-record-v1;
2. record ID;
3. patient bridge ID;
4. version number;
5. author Doctor ID;
6. thời điểm tạo;
7. previous version ID;
8. previous hash;
9. exam date;
10. diagnosis;
11. prescription;
12. testResults;
13. notes;
14. số attachment;
15. với mỗi attachment theo đúng thứ tự: ID, SHA-256 bytes, media type, size bytes.

Hash = SHA-256 của UTF-8 canonical content, trả hex thường 64 ký tự.
Vector test: SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.
Canonical content được lưu như snapshot nhạy cảm trong DB, không trả ở API hash và không ghi log.
API hash kiểm tra snapshot/hash, tái tạo từ các cột nội dung/metadata và liên kết phiên bản liền trước.
Download còn tính lại digest bytes. UPDATE bị chặn nếu nội dung phiên bản trước không khớp hash.

Chưa có nguồn hash đáng tin bên ngoài DB: người có quyền sửa cả nội dung và hash trong DB vẫn có thể
thay đổi bằng chứng. Blockchain sau này giải quyết điểm neo xác minh này.
Nếu thay quy tắc canonical phải có schema v2 và tiếp tục giữ bộ kiểm tra v1; không tính lại hash cũ âm thầm.

## 9. Luồng versioning và concurrency

CREATE: kiểm tra tài khoản/READ/CREATE → tạo record → flush để có ID → tạo version 1 → canonical/hash → commit.
UPDATE: kiểm tra tài khoản/UPDATE/READ → kiểm tra patient và expectedVersion → kiểm tra hash trước →
tăng currentVersion → flush `@Version lockVersion` → INSERT version mới cùng previous ID/hash → commit.

Không có API xóa hồ sơ, xóa version hoặc sửa trực tiếp nội dung version cũ.
`RecordVersion` dùng Hibernate Immutable và các cột updatable=false.
UNIQUE(record_id, version_number) là lớp bảo vệ bổ sung ở DB.
Khi hai writer cùng cập nhật một version, chỉ một transaction được commit, phía còn lại nhận xung đột.
Không tự retry ghi để tránh áp dụng lại nội dung trên phiên bản khác ý định client.

## 10. Private storage và audit

PrivateStorage: `put(bytes)` và `read(serverKey)`. Local adapter phải bật chủ động; mặc định trả 503.
Chỉ hỗ trợ PDF, PNG, JPEG, kiểm tra magic bytes cơ bản và giới hạn 5 MiB/file, tối đa 10 file/version.
Không dùng file name client làm đường dẫn. Không có public static route hoặc public bucket URL.
Hash bytes được lưu khi upload và kiểm tra khi download.

Local adapter phục vụ phát triển, **không phải private storage production**: chưa antivirus,
chưa mã hóa file at-rest, quota, retention, backup hoặc object storage access policy.
Filesystem không nằm trong transaction DB: upload thất bại sau khi lưu file có thể để lại file mồ côi;
file upload chưa gắn version cũng cần quy trình dọn sau khi nhóm chốt retention.
Các file không được tải chỉ bằng attachment ID; phải qua record/version hợp lệ và quyền READ hiện hành.

Audit chỉ lưu User ID, HTTP method, đường dẫn đã giới hạn, kết quả và Instant; không body/query/header/token.
ATTEMPT được ghi trước thao tác; kết quả ghi bằng REQUIRES_NEW sau khi service hoàn tất.
Response được buffer để không trả nội dung nếu audit không ghi được; trả 503 khi audit không sẵn sàng.
Nếu transaction nghiệp vụ đã commit nhưng audit kết quả thất bại, client có thể nhận 503 mặc dù dữ liệu đã lưu:
cần truy vấn kiểm tra trước khi retry POST. Chưa triển khai idempotency key/outbox.
Audit hiện ở ranh giới HTTP đã xác thực; token hỏng/login thất bại và lời gọi service nội bộ không đi qua filter
không có cùng nhật ký HTTP này. Không có API đọc audit dành cho Doctor.

## 11. Điểm tích hợp Blockchain sau này

Chỉ chuẩn bị các cột nullable `blockchain_transaction_hash`, `blockchain_record_id` và trạng thái `NOT_SUBMITTED`.
CHECK constraint hiện chỉ cho NOT_SUBMITTED; nhóm phải duyệt migration trạng thái khi triển khai on-chain.
Các cột chưa có setter nghiệp vụ công khai để client tự xác nhận blockchain.

Các vị trí mở rộng:

- `MedicalRecordService.create/update/append`: sau khi commit version, phát sự kiện/outbox mang version ID và hash.
  Không gọi RPC trong transaction JPA hoặc trước khi hash/version được commit.
- `RecordVersion` và migration trạng thái: thêm hàng đợi PENDING/SUBMITTED/CONFIRMED/FAILED và retry có idempotency.
  Có thể tách trạng thái giao dịch thành bảng receipt riêng để giữ snapshot bất biến.
- `MedicalRecordService.hash`: giữ quyền READ và canonical v1; thêm đối chiếu hash có nguồn tin cậy từ contract.
- `PatientAccessPolicy`: điểm tách nguồn quyền; cần chính sách đồng bộ và thu hồi khi bổ sung quyền on-chain.
- `HashService`: giữ quy tắc ổn định để Java và client có thể kiểm tra cùng một bằng chứng.

Không có BlockchainService giả hoặc lời gọi Ethereum trong source bàn giao.

## 12. MetaMask — đề xuất để bạn tự triển khai

Giữ `users.wallet_address`, không có API tự gán địa chỉ ví ở giai đoạn này.
Sau này dùng challenge một lần gắn User ID, nonce ngẫu nhiên, domain/chain và expiry;
client ký bằng ví, Backend xác minh chữ ký và consume nonce nguyên tử trước khi liên kết.
Không tin địa chỉ ví do React gửi; không dùng JWT như bằng chứng sở hữu ví.
Cần quy tắc đổi/gỡ ví, chống replay và uniqueness theo thiết kế chung. Chưa code phần này.

## 13. Những điểm cần thống nhất với Patient/Admin

- ID và schema Patient thực tế, ánh xạ patient_links và User PATIENT.
- Module nào ghi grant, ý nghĩa từng quyền, người cấp, thời hạn và thời điểm thu hồi.
- Schema `doctor_backend` riêng có phù hợp hay phải chuyển sang bảng chung.
- Quy trình Admin duyệt, cập nhật approval_status/status và metadata approved_by/approved_at.
- Nguồn dữ liệu tên/email/mật khẩu chuẩn; hiện chỉ đồng bộ tên khi sửa hồ sơ Doctor.
- Quy tắc email hiện giữ so khớp chính xác như repository cũ; chưa tự đổi chữ hoa/thường trên dữ liệu nhóm.
- UNIQUE email/user_id/license và quy trình giải quyết dữ liệu trùng có sẵn.
- Attachment production, quyền bucket, mã hóa, backup và retention dữ liệu y tế.
- JWT issuer/audience, secret rotation và API contract nếu nhiều backend dùng chung đăng nhập.
- CORS origin React, deployment reverse proxy/HTTPS và chính sách bảo vệ API login.

## 14. Hạn chế và việc còn lại

- Chưa xác minh schema/constraint hoặc chạy API trên Supabase; không có migration nào được áp dụng.
- Tích hợp Patient/Admin và production storage còn phụ thuộc nhóm.
- Chưa có PostgreSQL integration test; khóa/concurrency đã test trên H2.
- Chưa rate limit login, refresh token, logout/revocation token theo phiên, reset password hoặc email verification.
  Token hết hạn sau 1 giờ; enabled/role/approval được kiểm tra DB mỗi request.
- Chưa cấu hình CORS vì chưa có origin React được nhóm xác nhận.
- Chưa có bộ lọc nâng cao theo ngày/trạng thái blockchain hoặc tối ưu truy vấn batch cho dữ liệu lớn.
- Không đảm bảo bất biến trước tài khoản DB đặc quyền; cần DB role/retention và blockchain sau này.
- Audit transaction và filesystem có các giới hạn đã nêu; cần idempotency/outbox khi triển khai production.
- Dữ liệu đang off-chain trong PostgreSQL; SHA-256 không thay thế mã hóa dữ liệu y tế.

Bạn tự triển khai tiếp: Solidity Smart Contract dùng chung → Hardhat → Sepolia → MetaMask →
ghi hash phiên bản lên Blockchain → xác minh tính toàn vẹn bằng Smart Contract.
