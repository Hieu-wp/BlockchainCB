# Biên bản tạo bảng phụ Doctor — 19/11/2026

Theo yêu cầu của người dùng: kiểm tra nơi lưu dữ liệu và tạo riêng các bảng còn thiếu đúng với Backend.
Đích: database cấu hình trong Backend Doctor hiện tại; không tạo database/project Supabase khác.

Trước thao tác, đọc metadata bằng transaction READ ONLY: không có cột của shared_version_proofs/shared_uploads;
schema doctor_backend và các bảng public.record_versions/users/patients dùng làm FK đã tồn tại.

Đã áp dụng nội dung [PROPOSED_shared_clinical_v1.sql](PROPOSED_shared_clinical_v1.sql) sau xác nhận này:

- Tạo doctor_backend.shared_version_proofs: version_id, canonical_content, previous_hash, test_results, content_hash.
- Tạo doctor_backend.shared_uploads: id, user_id, patient_id, storage_key, file_name, media_type, size_bytes, sha256.
- Bật RLS cho cả hai; không thêm policy truy cập công khai.
- Thực hiện trong transaction, giới hạn chờ lock3 giây/statement15 giây, dùng advisory transaction lock.
- Kiểm chứng13 cột,3 FK và RLS cho2 bảng trước commit. Nếu sai thì rollback.
- Sau commit đã mở kết nối khác, READ ONLY, kiểm tra lại đủ13 cột NOT NULL và prerequisite tồn tại.

Không ALTER/DROP bảng public hoặc bảng legacy, không di chuyển dữ liệu, không INSERT bệnh án/thuốc/dữ liệu mẫu,
không gọi API bằng JWT người dùng, không giao dịch Ethereum. Không đọc nội dung bệnh án hoặc ghi credential vào báo cáo.
Hai bảng này chỉ là metadata/snapshot phụ trợ; nội dung khám/thuốc/sinh hiệu/tệp liên phiên bản vẫn dùng public.

Không chạy lại script CREATE TABLE này. Không xóa hai bảng khi đã có snapshot/tệp cần bảo toàn.
Nếu rollback chức năng, dừng ghi/revert code và giữ bảng làm bằng chứng; mọi thay đổi dữ liệu tiếp theo cần review riêng.

Cần người dùng tải lại form/kiểm tra lại capability bằng phiên Doctor. Việc tạo bảng chưa phải test E2E tạo bệnh án
trên Supabase hoặc test Patient API. Adapter storage production vẫn chưa cấu hình; local adapter phải opt-in khi demo.
