# BlockchainCB

Hệ thống quản lý bệnh án điện tử của nhóm: Doctor, Patient và Admin.

**Tài liệu để cả ba thành viên đối chiếu bảng, khóa ngoại và quyền sở hữu dữ liệu:**
[Hợp đồng database chung](docs/SHARED_DATABASE_CONTRACT.md).

Tài liệu phân biệt phần Doctor đã sử dụng bảng chung và phần còn chờ tích hợp.
Patient/Admin cần đối chiếu implementation của mình trước khi xác nhận workflow end-to-end;
source của hai module đó chưa có trong checkout được kiểm tra ngày19/11/2026.

- [Doctor ↔ Patient: trạng thái triển khai và API](Doctor/demo/docs/DOCTOR_PATIENT_INTEGRATION.md)
- [Luồng tạo hồ sơ khám và kê đơn thuốc](docs/LUONG_TAO_HO_SO_VA_KE_DON.md)
- [Tối ưu tải dữ liệu Doctor](docs/TOI_UU_TAI_DU_LIEU.md)
- [Doctor Backend](Doctor/demo/docs/DOCTOR_BACKEND_HANDOFF.md)
- [Doctor Frontend](Doctor/demo/docs/DOCTOR_FRONTEND_HANDOFF.md)
- [Doctor Blockchain](Doctor/demo/docs/DOCTOR_BLOCKCHAIN_INTEGRATION.md)
- [SQL kiểm tra metadata chỉ đọc](docs/sql/READ_ONLY_shared_database_contract.sql)

Các file SQL `PROPOSED_*` là đề xuất cần review, không được mặc định áp dụng lên Supabase chung.
Không chia sẻ password, JWT secret, private key, ảnh đăng nhập hoặc dữ liệu bệnh án thật trong repository.
