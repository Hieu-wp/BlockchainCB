# Hợp đồng database chung — Admin / Doctor / Patient

> Cập nhật Doctor writer: xem [luồng tạo hồ sơ/kê đơn](LUONG_TAO_HO_SO_VA_KE_DON.md).
> Source đã có ghi public, ACTIVE cho CREATE và tác giả root có quyền hiện hành cho UPDATE;
> chưa áp dụng SQL metadata mới lên Supabase. Các nhận xét “chưa triển khai ghi” bên dưới là mốc trước bản cập nhật này.

Bản đối chiếu ngày19/11/2026. Nguồn: DDL public và ảnh11 bảng doctor_backend do người dùng cung cấp,
cùng source Doctor hiện tại. Đây là tài liệu phối hợp của nhóm; những quyết định ghi là **chờ thống nhất**
chưa được coi là chính sách đã được cả ba thành viên chấp thuận.

## 1. Trạng thái thực tế

**Dùng cùng một Supabase project chưa đủ: ba module phải dùng đúng schema, bảng và ý nghĩa của ID.**

- Nguồn tài khoản/hồ sơ và đích dữ liệu liên module là các bảng `public` dưới đây.
- Doctor shared mode đã đọc/ghi yêu cầu trong `public.access_grants`, đọc bệnh nhân và bệnh án public.
- **Doctor chưa ghi bệnh án/phiên bản/thuốc/sinh hiệu/tệp vào public.** Ghi bệnh án chung và anchor của bản ghi public chưa bật.
- Kho cũ và bảng kỹ thuật Doctor trong `doctor_backend` vẫn được giữ nguyên. Không tự xóa, copy hoặc đổi ID.
- Source Patient/Admin chưa có trong checkout được kiểm tra. Không tuyên bố hai module đã dùng đúng bảng hoặc API đã chạy thành công.
- Agent không gửi tài liệu tới người khác, không commit/push, không chạy migration hoặc ghi dữ liệu Supabase.
  Người phụ trách Doctor cần chia sẻ tài liệu này với hai thành viên còn lại qua kênh nhóm.

## 2. Danh mục bảng và trách nhiệm

Quyền dưới đây là ranh giới tích hợp cần đối chiếu ở Backend từng module, không phải GRANT SQL đã áp dụng.
Quyền Admin quản lý tài khoản không tự động đồng nghĩa được đọc toàn bộ nội dung bệnh án.

| Bảng chuẩn | Ý nghĩa | Doctor hiện tại | Patient cần đối chiếu | Admin cần đối chiếu |
|---|---|---|---|---|
| public.users | Tài khoản chung, mật khẩu băm, role, enabled, wallet_address | Đăng ký DOCTOR; đăng nhập bằng password_hash; sửa thông tin được phép của chính mình; đồng bộ ví đã xác minh | PATIENT dùng đúng User, quản lý thông tin chính mình theo quyền | Quản lý role/enabled theo nghiệp vụ Admin, không tạo nguồn xác thực khác |
| public.doctors | Hồ sơ hành nghề liên kết User | Tạo PENDING, đọc/sửa trường được phép; không tự duyệt | JOIN để hiển thị danh tính Doctor trong yêu cầu cấp quyền, chỉ trả thông tin cần thiết | Duyệt approval_status, approved_by, approved_at; thống nhất trạng thái khóa |
| public.specialties | Danh mục chuyên khoa | Tra cứu và kiểm tra specialty_id | Dùng cùng specialty ID khi hiển thị hồ sơ | Thống nhất module quản lý danh mục |
| public.patients | Hồ sơ Patient và patient_code duy nhất | Tìm theo mã khi xin quyền; danh sách/nội dung chỉ theo consent hiện hành | Liên kết đúng patients.user_id với principal, quản lý hồ sơ của chính Patient | Chỉ theo nghiệp vụ tài khoản được nhóm cho phép |
| public.access_grants | Yêu cầu và quyền Doctor–Patient | INSERT PENDING; đọc yêu cầu của mình; đọc ACTIVE còn hiệu lực; không tự duyệt | Duyệt/từ chối/thu hồi yêu cầu thuộc chính Patient | Phê duyệt Doctor không được tự biến thành consent của Patient |
| public.medical_records | Một lần khám | Đã đọc theo quyền; **ghi chung chưa triển khai** | Truy vấn bằng patient_id của chính Patient | Không mặc định được đọc nội dung y tế |
| public.record_versions | Nội dung từng phiên bản lần khám | Đã đọc; **append phiên bản chung chưa triển khai** | Đọc các phiên bản thuộc hồ sơ của chính mình | Không sửa/xóa lịch sử qua quản lý tài khoản |
| public.prescription_items | Từng thuốc của một phiên bản | Đã đọc danh sách thuốc; **ghi chung chưa triển khai** | JOIN theo record_version_id, không lấy thuốc nhầm phiên bản | Không mặc định can thiệp nội dung kê đơn |
| public.vital_signs | Sinh hiệu của một phiên bản | Đã đọc; **ghi chung chưa triển khai** | JOIN theo record_version_id | Không mặc định can thiệp dữ liệu khám |
| public.record_attachments | Metadata và vị trí tệp của một phiên bản | Đọc tên/loại/kích thước hiển thị; **chưa trả file_url hoặc tải tệp chung** | Thống nhất private storage, kiểm tra quyền khi tải | Không công khai tệp nhạy cảm |
| public.admin_audit_logs | Nhật ký thao tác Admin | Không ghi bảng này | Không dùng thay nhật ký Patient | Module Admin quản lý theo thiết kế của mình |

Thông tin full_name/email/password đang bị lặp ở doctors và users: Doctor hiện dùng users làm nguồn xác thực,
vẫn lưu BCrypt hash trong doctors.password để đáp ứng NOT NULL của schema. Không tự xóa cột trước khi nhóm thống nhất.

## 3. ID và khóa ngoại phải dùng thống nhất

| Cột | Tham chiếu đúng trong schema đã cung cấp |
|---|---|
| doctors.user_id | public.users.id |
| doctors.specialty_id | public.specialties.id |
| doctors.approved_by | public.users.id của Admin |
| patients.user_id | public.users.id của Patient |
| access_grants.patient_id | public.patients.id |
| access_grants.doctor_id | **public.doctors.id**, không phải users.id |
| medical_records.patient_id | public.patients.id |
| medical_records.doctor_id | public.doctors.id |
| medical_records.specialty_id | public.specialties.id |
| record_versions.record_id | public.medical_records.id |
| record_versions.created_by | **public.users.id** của người tạo phiên bản, không phải doctors.id |
| prescription_items.record_version_id | public.record_versions.id |
| vital_signs.record_version_id | public.record_versions.id |
| record_attachments.record_version_id | public.record_versions.id |
| admin_audit_logs.admin_id | public.users.id của Admin |

Ví dụ số **minh họa, không phải dữ liệu thật hoặc lệnh INSERT**:
Doctor có User ID101 và Doctor ID7; Patient có User ID102 và Patient ID23.
Grant dùng `(patient_id=23,doctor_id=7)`; lần khám dùng doctor_id7;
phiên bản dùng created_by101; thuốc phải trỏ vào ID hàng record_versions, không phải version_no hoặc record_id.

`patient_code`, `record_code` là mã hiển thị/tra cứu; không được dùng thay khóa BIGINT trong FK.
Hai ID ở public và doctor_backend có cùng giá trị số vẫn có thể là hai đối tượng hoàn toàn khác.

## 4. Luồng phối hợp và trạng thái

1. Doctor đăng ký → public.users role DOCTOR và public.doctors approval_status PENDING trong một transaction.
2. Admin xử lý public.doctors, public.users theo API của module Admin. **Chưa kiểm tra API Admin thực tế**.
3. Doctor APPROVED nhập patient_code → POST `/api/doctor/access-grants/request` với `{"patientCode":"<mã bệnh nhân>"}`.
   Backend lấy Doctor từ principal, tra public.patients rồi tạo yêu cầu trong public.access_grants.
4. Patient lấy danh tính từ principal → tìm patients theo user_id → đọc public.access_grants của Patient đó.
   Trạng thái chuyển **cần xác nhận implementation Patient**: PENDING→ACTIVE/REJECTED, ACTIVE→REVOKED.
   Khi thu hồi, phải cập nhật dữ liệu nhất quán, khuyến nghị status REVOKED và revoked=true trong cùng transaction.
   Doctor hiện yêu cầu cả status ACTIVE, revoked=false và expires_at NULL hoặc lớn hơn thời gian DB.
5. Sau khi Patient commit ACTIVE, Doctor query cùng bảng sẽ thấy quyền; không có job copy sang doctor_backend.access_grants.
   Giao diện cập nhật qua reload/polling, chưa có push tức thời.
6. **Luồng đích, chưa bật:** Doctor ghi public.medical_records + record_versions + thuốc/sinh hiệu/tệp cùng transaction;
   lấy doctor_id, created_by từ principal, specialty_id từ danh tính bác sĩ được Backend xác minh.
7. Patient JOIN các bảng public theo FK để đọc hồ sơ của mình; không cần copy bệnh án sang bảng Patient riêng.
8. **Luồng đích, chưa bật:** xác minh hash và anchor public sau khi thống nhất canonical/phiên bản và kiểm chứng ví Patient.

Doctor PENDING hiện được xem trạng thái/hồ sơ và liên kết ví theo điều kiện bảo mật, không được xin quyền hoặc khám bệnh.
CHECK doctors.approval_status chỉ cho PENDING/APPROVED/REJECTED; khóa tài khoản cần thống nhất users.enabled
hoặc doctors.status với Admin, không tự ghi BLOCKED vào approval_status trái constraint.
`follow_status` của Patient không thay thế consent trong access_grants.
Approval của Admin, consent off-chain, proof ví và quyền grantWrite/DOCTOR_ROLE on-chain là các điều kiện riêng biệt.

## 5. Quyền và bất biến còn cần thống nhất

| Quyết định | Hiện trạng | Điều kiện trước khi mở ghi bệnh án chung |
|---|---|---|
| ACTIVE cho READ/CREATE/UPDATE | Shared schema chỉ có status; Doctor mới dùng cho READ | Nhóm phải xác nhận scope ghi hoặc cơ chế thể hiện quyền riêng; không tự suy diễn mọi ACTIVE có toàn quyền |
| Ai được cập nhật một hồ sơ | Chưa thống nhất cho public | Bác sĩ tạo hồ sơ hay bác sĩ khác có quyền UPDATE? Phải được xác minh ở Backend |
| Yêu cầu trùng | Doctor khóa hàng Patient rồi kiểm tra PENDING/ACTIVE trước INSERT; có test race | Hai backend khác cũng phải tuân thủ quy ước; xem index/constraint thật trước đề xuất thay đổi. Không chỉ dựa vào ẩn nút |
| Version number | SQL export chưa thể hiện UNIQUE(record_id,version_no), metadata trước đó có index này | Đối chiếu pg_indexes, không kết luận thiếu hoặc tự tạo lại từ bản export. Giữ số phiên bản duy nhất và tăng theo thứ tự |
| Cập nhật đồng thời | Public root có current_version nhưng chưa có adapter ghi | Khóa root hoặc compare-and-swap current_version; append phiên bản và children atomic, không ghi đè phiên bản cũ |
| Hash chung | Doctor cũ dùng canonical doctor-record-v1, public chỉ có data_hash/tx_hash/chain_status | Chốt trường, thứ tự, Unicode, null, timestamp, thuốc/tệp và hash liên kết; giữ hash/bằng chứng cũ, không tự tính lại rồi thay |
| Tệp | file_url là metadata, chưa đủ chứng minh private access | Thống nhất storage private, kiểm quyền ở mọi download, không trả URL công khai cho dữ liệu nhạy cảm |
| Thời gian | Public dùng timestamp WITHOUT time zone | Thống nhất timezone/session và cách ghi expires_at/visit_at/created_at giữa các backend |
| RLS | Bản export không đủ xác nhận policy/role kết nối của từng module | Kiểm RLS bằng role thực tế; owner/service role có thể bypass. Backend vẫn phải kiểm quyền; không tự tắt RLS |

Patient chỉ duyệt grant thuộc chính mình; Doctor không nhận doctor_id từ client để xác định danh tính.
Ngay cả tác giả hồ sơ cũng phải bị chặn đọc khi consent đã bị thu hồi. Test IDOR phải kiểm cả patient_id/record_id/version_id/tệp.

## 6. Vai trò schema doctor_backend trong giai đoạn chuyển tiếp

- Kho cũ: patient_links, access_grants, medical_records, record_versions, attachments, version_attachments.
- Nhật ký Doctor: audit_logs; không trộn với public.admin_audit_logs.
- Ví: wallet_bindings, wallet_challenges. Sau verify thành công, Doctor đồng bộ public.users.wallet_address trong cùng transaction.
- Anchor cũ: record_anchors, anchor_attempts. FK vẫn trỏ vào kho cũ, không được gán ID public vào.

Trong shared mode không dùng kho quyền cũ cho bệnh nhân public, không fallback nếu public trả lỗi/thiếu quyền.
Legacy Entity vẫn được JPA validate ở startup dù flag của chức năng tắt. Giữ ddl-auto=validate và sql.init.mode=never trên Supabase.
Việc di chuyển dữ liệu cũ cần backup, bảng ánh xạ ID, bảo toàn lịch sử/hash và kế hoạch rollback được nhóm review riêng.

## 7. API Doctor để các thành viên kiểm tra hợp đồng

| Endpoint | Nguồn dữ liệu / ý nghĩa |
|---|---|
| GET /api/doctor/me | User/Doctor của principal, trạng thái phê duyệt và thông tin được phép |
| GET /api/doctor/integration | Cờ nguồn dữ liệu; sharedGrantsEnabled/schemaReady/sharedHistoryReady; sharedRecordsReady=false nghĩa là ghi public chưa sẵn sàng |
| POST /api/doctor/access-grants/request | Tạo PENDING trong public.access_grants bằng patientCode, không nhận doctorId |
| GET /api/doctor/access-grants | Yêu cầu của Doctor đang đăng nhập |
| GET /api/doctor/patients | Shared mode: public Patient có quyền đọc ACTIVE còn hiệu lực |
| GET /api/doctor/shared-records | Lịch sử public do Doctor tạo và còn quyền đọc; hỗ trợ patientCode/page/size |
| GET /api/doctor/shared-patients/{id}/records | Các lần khám của public Patient có quyền đọc hiện hành |
| GET /api/doctor/shared-records/{id} | Phiên bản hiện tại, kèm metadata lần khám |
| GET /api/doctor/shared-records/{id}/versions | Danh sách phiên bản |
| GET /api/doctor/shared-records/{id}/versions/{number} | Nội dung, thuốc, sinh hiệu và tên tệp của đúng phiên bản |

Không gọi `/records` legacy bằng public record ID. Chưa có public POST/PATCH bệnh án tương ứng với các GET trên.
Hash public trả UNVERIFIED_PUBLIC_CANONICAL; giá trị CONFIRMED trong DB không được coi là Backend đã xác minh blockchain.
Chưa xác nhận endpoint Patient/Admin; không tự đặt tên API của họ trong tài liệu như thể đã tồn tại.

## 8. Cách ba thành viên cùng xác nhận

1. Mỗi thành viên đối chiếu Supabase project ref trong cấu hình local với project nhóm đã thống nhất.
   Không gửi connection string chứa password, file .env hoặc service-role key cho nhau.
2. Chạy [SQL metadata chỉ đọc](sql/READ_ONLY_shared_database_contract.sql), đối chiếu cột/FK/index/RLS.
   Script không đọc bệnh án, không thay schema hoặc dữ liệu.
3. Patient/Admin bổ sung danh sách file, service, API và bảng mình thực sự dùng vào bảng xác nhận dưới đây.
4. Thống nhất các quyết định còn mở ở mục5 trước khi bật ghi chung. Doctor không tự sửa module của người khác.
5. Phối hợp test bằng tài khoản/dữ liệu kiểm thử được nhóm cho phép hoặc DB cách ly:
   Admin duyệt Doctor → Doctor xin quyền → Patient duyệt → Doctor thấy Patient → thu hồi và bị chặn.
   Sau khi adapter ghi hoàn thành mới test Doctor tạo/append → Patient thấy đúng hồ sơ/thuốc/sinh hiệu/tệp.
6. Ghi kết quả, thời điểm, phiên bản code và môi trường test; không đưa thông tin y tế, mật khẩu hoặc JWT vào báo cáo.

| Module | Trạng thái xác nhận | File/API cần cung cấp hoặc kiểm chứng |
|---|---|---|
| Doctor | Source đã đối chiếu; test local, chưa E2E chung | AuthService, DoctorService, SharedGrantService/Repository, SharedRecordService/Repository; xem tài liệu Doctor |
| Patient | **Chưa được thành viên Patient xác nhận** | API danh sách/duyệt/thu hồi grant; query bệnh án/phiên bản/thuốc theo patients.user_id; policy ví/storage |
| Admin | **Chưa được thành viên Admin xác nhận** | API duyệt/khóa Doctor; cách cập nhật users.enabled/doctors.status/approval_status và admin_audit_logs |

Source Doctor đã có test local; không coi việc thêm tài liệu này là đã hoàn thành tích hợp ba module.
Xem [bàn giao Doctor ↔ Patient](../Doctor/demo/docs/DOCTOR_PATIENT_INTEGRATION.md) để biết chi tiết và các mốc test.

## 9. Nội dung ngắn để gửi nhóm

> Nhóm mình thống nhất đối chiếu tài liệu docs/SHARED_DATABASE_CONTRACT.md. Tài khoản/hồ sơ và dữ liệu liên module
> dùng các bảng public; access_grants.doctor_id là doctors.id, còn record_versions.created_by là users.id.
> Doctor đã xin quyền và đọc quyền/bệnh án chung, nhưng chưa ghi bệnh án public.
> Patient xác nhận API duyệt/thu hồi grant và đọc hồ sơ chính mình; Admin xác nhận cách duyệt/khóa tài khoản.
> Các bảng doctor_backend giữ dữ liệu cũ và metadata kỹ thuật, chưa được xóa hoặc đổi FK.
> Cần thống nhất quyền CREATE/UPDATE, canonical hash và storage trước khi mở ghi bệnh án chung.
