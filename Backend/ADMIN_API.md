# Backend Admin – API và kiểm thử

## Phạm vi và chạy ứng dụng

Java 17, Spring Boot 4.0.9, Spring Security Form Login/session/CSRF, JPA và NamedParameterJdbcTemplate. Giữ nguyên endpoint và DTO đang dùng bởi Frontend. Không sửa Frontend trong giai đoạn này.

Tên biến môi trường hiện có, không ghi giá trị bí mật vào tài liệu hoặc Git:

- `SUPABASE_DB_URL`
- `SUPABASE_DB_USERNAME`
- `SUPABASE_DB_PASSWORD`

Thiết lập các biến qua môi trường cục bộ của nhóm, sau đó:

```powershell
cd D:\Tai_Lieu\Nam_4\HK1\BlockChain\BlockchainCB\Backend
.\mvnw.cmd spring-boot:run
```

Backend dùng 8080; Frontend phát triển dùng 5173 và proxy `/api`, `/login`, `/logout`. Cấu hình chính giữ `ddl-auto=validate`, session cookie HttpOnly/SameSite và CSRF. Không chạy migration, seed hay cập nhật thử trên database dùng chung.

## Endpoint đã triển khai

| Endpoint | Contract và ghi chú |
|---|---|
| GET `/api/admin/me` | `tenDangNhap`, `vaiTro: ADMIN`, `danhSachQuyen`, `daXacThuc`; giữ nguyên JSON |
| GET `/api/admin/trang-thai` | API kiểm tra phiên hiện có, giữ nguyên |
| GET `/api/admin/health` | Chỉ trạng thái Admin API, không giám sát database/chain |
| GET `/api/admin/dashboard` | Giữ sáu trường thống kê hiện có |
| GET `/api/admin/users` | `tuKhoa`, `vaiTro`, `trangThai`, `trang`, `kichThuoc`; DTO phân trang tiếng Việt |
| GET `/api/admin/users/{id}` | Chi tiết không có mật khẩu |
| POST `/api/admin/users/{id}/lock` | Chỉ PATIENT, body `{"lyDo":"..."}`, audit USER_LOCK |
| POST `/api/admin/users/{id}/unlock` | Chỉ PATIENT, không cần body, audit USER_UNLOCK |
| GET `/api/admin/doctors` | Mảng DoctorResponse |
| GET `/api/admin/doctors/pending` | Mảng PENDING |
| GET `/api/admin/doctors/search` | Spring Page; giữ `tuKhoa`, `trangThaiPheDuyet`, `trangThaiTaiKhoan`, `trang`, `kichThuoc` |
| GET `/api/admin/doctors/{id}` | Giữ `maBacSi`, `hoTen`, `email`, `soGiayPhep`, `noiCongTac`, `chuyenKhoa`, `diaChiVi`, `trangThai`, `trangThaiPheDuyet` |
| POST `/api/admin/doctors/{id}/reject` | **Mới hoàn thiện nghiệp vụ**: chỉ PENDING → REJECTED, lý do bắt buộc tối đa 350 ký tự, audit DOCTOR_REJECT |
| GET `/api/admin/audit-logs` | Giữ `danhSachNhatKy`, `trangHienTai`, `kichThuocTrang`, `tongPhanTu`, `tongSoTrang` |

Các endpoint trên có code thực; kiểm thử database trong dự án dùng PostgreSQL riêng, chưa xác nhận lại trên Supabase thật. Không gọi endpoint “đã triển khai” là đã được nghiệm thu production.

## Từ chối bác sĩ

1. Kiểm tra ID/lý do; xác thực Admin đang hoạt động từ SecurityContext và bảng users, không nhận maAdmin do client gửi.
2. `PESSIMISTIC_WRITE` khóa dòng bác sĩ, kiểm tra đúng PENDING.
3. Khóa tài khoản liên kết và xác nhận vai trò DOCTOR. Tài khoản không có hoặc sai vai trò trả 409; không tác động PATIENT.
4. JPQL update có điều kiện PENDING, chỉ sửa `approval_status`; bắt buộc đúng một dòng.
5. Audit JDBC ghi DOCTOR_REJECT với Admin, lý do đã trim và timestamp mặc định của bảng.
6. Cùng transaction commit hoặc rollback toàn bộ; trả DoctorResponse hiện có.

Không dùng `approved_by/approved_at` cho việc từ chối, không xóa hồ sơ hoặc mật khẩu legacy, không sửa doctors.status/users.enabled. Chỉ từ chối PENDING để không giả thu hồi quyền của bác sĩ đã APPROVED. Nếu quy trình PENDING của nhóm đã có giao dịch on-chain đang xử lý, cần thống nhất việc hủy/đối soát trước khi đưa vào sử dụng thật.

## Các endpoint còn chờ Blockchain

- POST `/api/admin/doctors/{id}/approve`: kiểm tra Admin, tồn tại, PENDING, tài khoản DOCTOR đang hoạt động, họ tên/email/số giấy phép/nơi công tác không rỗng và mã chuyên khoa dương. Sau đó trả **501**, không cập nhật APPROVED hoặc ghi DOCTOR_APPROVE.
- POST `/api/admin/doctors/{id}/suspend`: yêu cầu hồ sơ APPROVED, tài khoản DOCTOR liên kết, status không rỗng, lý do hợp lệ. Sau đó **501**, không giả cập nhật/thu hồi quyền.
- POST `/api/admin/doctors/{id}/reactivate`: tương tự kiểm tra hồ sơ APPROVED và liên kết; sau đó **501**, không giả khôi phục quyền.

Không tự diễn giải doctors.status thành ACTIVE/SUSPENDED hoặc thêm trạng thái approval mới. Quy tắc chuyên khoa tồn tại, giấy phép hợp lệ/duy nhất, quyền ví và cách cấp/thu hồi trên chain cần hợp đồng kỹ thuật từ nhóm; sanity check hiện tại không thay thế kiểm định các nội dung đó.

## Bảo mật và mã lỗi

- Mọi `/api/admin/**` tiếp tục qua `hasRole(ADMIN)`. PATIENT/DOCTOR bị 403; POST cần CSRF.
- Người chưa đăng nhập hiện được Form Login chuyển **302 về `/login`**. Không thay đổi cấu hình này để ép test. Frontend hiện nhận diện trang login sau redirect.
- Service ghi xác minh lại Admin đang hoạt động; SecurityFilter và service có trách nhiệm khác nhau.
- 400: ID/lý do/bộ lọc/phân trang không hợp lệ. 404: tài nguyên không tồn tại.
- 403: thiếu quyền/CSRF hoặc Admin không hợp lệ. 401 có thể do service không có phiên.
- 409: trạng thái không cho thao tác, liên kết sai hoặc cập nhật không đúng một dòng.
- 501: phần cần Blockchain/quy ước status chưa có. Các lỗi dữ liệu hoặc nguồn hạ tầng khác không được giả thành công.
- Lỗi nghiệp vụ `ResponseStatusException` của năm Admin Controller trả JSON `application/json` gồm `status`, `detail` theo ProblemDetail; không lộ SQL/stack trace/credential. Chọn application/json để tương thích bộ phân tích FE hiện tại.
- Frontend hiện xử lý 5xx bằng thông báo Backend chưa sẵn sàng trước khi đọc detail; giữ nguyên vì giai đoạn này không sửa FE. Các nút ghi bác sĩ của FE vẫn vô hiệu hóa, kể cả reject nay có Backend.
- JSON thành công POST `/login` cũ không thay đổi. Frontend vẫn xác minh quyền bằng GET `/api/admin/me`, không nhầm role với authority.

## Transaction và kiểm tra tự động

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify -Ppostgres-it
```

- Mặc định: context dùng H2 riêng; service và MockMvc test kiểm tra vai trò/CSRF/form login/session/logout, validation, lỗi nghiệp vụ và DTO.
- `postgres-it`: Failsafe chạy `PostgresAdminIT` trên PostgreSQL embedded 18.3, port ngẫu nhiên, chỉ bind 127.0.0.1, dữ liệu/binary dưới `Backend/target/`. Không nhận URL database từ người dùng hoặc Supabase.
- Test chỉ đọc `application-test.properties`, dynamic properties ép URL/driver/tài khoản của PostgreSQL tạm. Fixture SQL chỉ dành cho instance vừa tạo. Không chạy fixture trên database có sẵn.
- H2 chỉ xác nhận context, không được dùng để khẳng định SQL PostgreSQL/rollback nghiệp vụ. Bằng chứng rollback/cạnh tranh là test PostgreSQL riêng.
- Test xác nhận bean JpaTransactionManager dùng cùng DataSource và truy vấn `txid_current()` qua JPA/JDBC cho cùng ID transaction.
- Fault injection CHECK constraint chỉ trên database tạm: audit lỗi làm rollback users.enabled và rejection; update bác sĩ lỗi không tạo audit; audit bắt buộc tham gia transaction.
- Cạnh tranh: hai reject hoặc hai lock đồng thời, lặp ba lần mỗi loại, đúng một thành công, một 409 và một audit.
- Mockito test dùng subclass/reflection extension thay vì self-attach instrumentation do giới hạn môi trường Windows; chỉ áp dụng test classpath.

Dependencies H2/embedded-postgres và binary BOM đều test-only. Lần chạy đầu cần tải Maven artifacts; không cần cài Docker. PostgreSQL 14 binary từng bị Windows DLL relocation trong môi trường này; bộ 18.3 đã được kiểm tra chạy được.

## Schema cần đối chiếu trước khi dùng database nhóm

Không có migration production trong thay đổi này. Fixture test dựa trên entity và SQL hiện có, không phải bản sao được xác nhận của schema Supabase.

Cần nhóm xác nhận `admin_audit_logs.action` cho phép DOCTOR_REJECT, `target_type` cho phép DOCTOR, description đủ dài, timestamp có default, quyền INSERT và các trigger/RLS phù hợp. Nếu constraint/quyền trên môi trường thật không phù hợp, thay đổi nghiệp vụ sẽ rollback và trả lỗi; mọi thay đổi constraint cần được nhóm phê duyệt riêng.

Không cần thêm cột để lưu lý do từ chối: dùng audit hiện có. Nếu nhóm muốn trường quyết định riêng trên doctors, cần thống nhất/schema review trước, không tái dùng cột approved_* một cách ngầm định.

## Kiểm tra bằng Postman trên môi trường được cho phép

1. GET `/login`, giữ cookie jar, lấy input `_csrf` thật. Không lưu credential hoặc token vào file collection đưa lên Git.
2. POST `/login` dạng x-www-form-urlencoded: username/password/_csrf. GET `/api/admin/me` xác nhận ADMIN và ROLE_ADMIN.
3. Kiểm tra GET danh sách/chi tiết, filter và pagination; DTO phải giữ như Frontend đang dùng.
4. Chỉ trên database riêng hoặc sau khi được cho phép rõ tài khoản thử nghiệm: lấy CSRF mới, POST reject với `{"lyDo":"Lý do kiểm tra"}` và header `X-CSRF-TOKEN`; kiểm tra 200/REJECTED cùng DOCTOR_REJECT. Gửi lại phải 409, không thêm audit.
5. Thiếu CSRF hoặc role sai phải bị chặn. Approve/suspend/reactivate đủ điều kiện vẫn 501, không đổi DB/không có audit thành công.
6. POST `/logout` với CSRF; phiên hết hiệu lực. Không fault injection hoặc chạy DDL thử trên Supabase.

Tài liệu tham khảo cơ chế kiểm thử: [Spring JpaTransactionManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html), [Zonky embedded-postgres](https://github.com/zonkyio/embedded-postgres). Kết luận transaction của dự án dựa trên test, không chỉ suy diễn từ tài liệu.
