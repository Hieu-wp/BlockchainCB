# Biên bản tạo bảng shared anchor — 19/11/2026

Người dùng yêu cầu trực tiếp: “rồi tạo bảng đi”.

Đã áp dụng `PROPOSED_shared_anchor_v1.sql` vào database Supabase cấu hình của Backend Doctor:

- `doctor_backend.shared_record_anchors` — 3 cột; FK `record_id` → `public.medical_records.id`.
- `doctor_backend.shared_anchor_intents` — 11 cột; FK `version_id` → `public.record_versions.id`.
- PK, UNIQUE record_key/transaction_hash, CHECK trạng thái và RLS theo script.
- Không tạo policy truy cập công khai, không thay role hoặc cấu hình Backend.

Trước khi tạo: metadata READ ONLY xác nhận hai bảng chưa tồn tại, schema và bảng đích đã có.
Thao tác dùng transaction, advisory lock, lock_timeout 3 giây, statement_timeout 15 giây.
Đã kiểm chứng 14 cột, 2 FK và RLS cả hai bảng trước COMMIT.
Sau COMMIT đã mở kết nối READ ONLY khác để kiểm chứng hai bảng và prerequisite vẫn tồn tại.

Không ALTER/DROP bảng cũ hoặc bảng public. Không INSERT bệnh án, intent, dữ liệu mẫu;
không chạy API người dùng hoặc gửi giao dịch Ethereum. Không ghi credential vào tài liệu.

Không chạy lại script CREATE TABLE. Khi rollback tính năng, tắt cờ Blockchain và giữ bảng/bằng chứng để đối soát;
không xóa bảng có dữ liệu. Cần kiểm tra API bằng phiên Doctor của người dùng để xác nhận các điều kiện tiếp theo:
deployment/RPC, ví Doctor/Patient đã xác minh, DOCTOR_ROLE và grantWrite.

Việc tạo bảng thành công không có nghĩa giao dịch Sepolia hoặc tích hợp Patient đã thành công.
