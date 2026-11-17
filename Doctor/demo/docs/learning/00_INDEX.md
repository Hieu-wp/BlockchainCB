# Học BlockchainCB Doctor theo từng flow

Bộ18 tài liệu dành cho bạn đọc lại source Java/React từ điểm người dùng thao tác đến database và quay lại giao diện.
Thay vì học rời từng class, mỗi flow02–14 có mục A–G: mục tiêu, file/method, Mermaid, truy vết từng bước,
code trích thực, thành công/thất bại, rủi ro và câu hỏi tự kiểm tra.

Ngày đối chiếu: **17/11/2026**. Nguồn chính là source đang có trong checkout.
Chỉ tạo Markdown tại thư mục này; không sửa Java/React/SQL/Git, không chạy Supabase/migration/Blockchain.

## Danh mục đầy đủ

| Tài liệu | Chức năng | Độ khó | Cần biết trước | Java/method chính |
|---|---|---|---|---|
| [01_KIEN_TRUC_TONG_THE.md](01_KIEN_TRUC_TONG_THE.md) | Kiến trúc tổng thể | Dễ | Java object, HTTP | DemoApplication, SecurityConfig |
| [02_FLOW_DANG_KY_DOCTOR.md](02_FLOW_DANG_KY_DOCTOR.md) | Đăng ký Doctor | Trung bình | Controller/DTO/JPA | AuthService.register, User, Doctor |
| [03_FLOW_DANG_NHAP_JWT.md](03_FLOW_DANG_NHAP_JWT.md) | Đăng nhập JWT | Trung bình | 02, Optional | AuthService.login, JwtService |
| [04_FLOW_XAC_THUC_PHAN_QUYEN.md](04_FLOW_XAC_THUC_PHAN_QUYEN.md) | Xác thực và phân quyền | Khó | 03, filter | JwtAuthenticationFilter, DoctorIdentityService |
| [05_FLOW_PHE_DUYET_DOCTOR.md](05_FLOW_PHE_DUYET_DOCTOR.md) | Phê duyệt Doctor | Dễ | 03–04 | DoctorPolicy, DoctorService |
| [06_FLOW_HO_SO_CA_NHAN.md](06_FLOW_HO_SO_CA_NHAN.md) | Hồ sơ cá nhân | Trung bình | DTO, managed entity | DoctorService, DoctorResponse |
| [07_FLOW_DANH_SACH_BENH_NHAN.md](07_FLOW_DANH_SACH_BENH_NHAN.md) | Danh sách bệnh nhân/grant | Khó | 04–05, JPQL | PatientService, DatabasePatientAccessPolicy |
| [08_FLOW_XEM_BENH_AN.md](08_FLOW_XEM_BENH_AN.md) | Xem bệnh án | Trung bình | 07, DTO | MedicalRecordService.allowed/get/version |
| [09_FLOW_TAO_HO_SO_KHAM.md](09_FLOW_TAO_HO_SO_KHAM.md) | Tạo hồ sơ khám | Khó | 07–08, transaction | MedicalRecordService.create/append |
| [10_FLOW_CAP_NHAT_PHIEN_BAN.md](10_FLOW_CAP_NHAT_PHIEN_BAN.md) | Cập nhật phiên bản | Khó | 09, JPA locking | MedicalRecordService.update, MedicalRecord |
| [11_FLOW_LICH_SU_VA_SHA256.md](11_FLOW_LICH_SU_VA_SHA256.md) | Lịch sử và SHA-256 | Khó | 09–10, UTF-8 | HashService, MedicalRecordService.canonical/hash |
| [12_FLOW_TEP_DINH_KEM.md](12_FLOW_TEP_DINH_KEM.md) | Tệp đính kèm | Khó | 09, byte[], interface | AttachmentService, PrivateStorage |
| [13_FLOW_AUDIT_LOG.md](13_FLOW_AUDIT_LOG.md) | Audit log | Khó | 04, transaction | AuditFilter, AuditService |
| [14_FLOW_REACT_FRONTEND.md](14_FLOW_REACT_FRONTEND.md) | React Frontend | Trung bình | HTTP, state/Promise | Controller/DTO Java liên quan; App.jsx, api.js |
| [15_DATABASE_VA_JPA.md](15_DATABASE_VA_JPA.md) | Database và JPA | Trung bình | Java class/interface | 9 Entity + 9 Repository |
| [16_CHUAN_BI_TICH_HOP_BLOCKCHAIN.md](16_CHUAN_BI_TICH_HOP_BLOCKCHAIN.md) | Chuẩn bị Blockchain | Khó | 09–13 | HashService, RecordVersion; chưa có on-chain |
| [17_LO_TRINH_HOC_LAI_CODE.md](17_LO_TRINH_HOC_LAI_CODE.md) | Lộ trình học lại | Dễ | 00–01 | Các method theo16 buổi |

## Nên bắt đầu ở đâu?

Mở [01 — Kiến trúc tổng thể](01_KIEN_TRUC_TONG_THE.md), sau đó đọc
[17 — Lộ trình học](17_LO_TRINH_HOC_LAI_CODE.md) để chia buổi.
Học thử flow06 (PATCH me) trước nếu còn mới với Controller → Service → Repository;
đọc file15 song song để hiểu DTO, Entity và dirty checking.
Sau đó đi02 →03 →04 →05 →07 →08 →09 →10 →11 →12 →13.
File14 nối các màn hình React với backend; file16 là ranh giới on-chain chưa có.

## Ba flow cần nắm trước Blockchain

1. [09 — Tạo hồ sơ và v1](09_FLOW_TAO_HO_SO_KHAM.md): dữ liệu được phép tạo, transaction và nơi sinh hash.
2. [10 — Phiên bản và concurrency](10_FLOW_CAP_NHAT_PHIEN_BAN.md): giữ lịch sử, khóa lạc quan, previous ID/hash.
3. [11 — Lịch sử và SHA-256](11_FLOW_LICH_SU_VA_SHA256.md): canonical, digest, ba cờ và giới hạn off-chain.

Trước cả ba flow này, phải hiểu04/07: Blockchain không thay việc kiểm tra danh tính và quyền bệnh nhân.

## Quy ước bằng chứng

- **Đã đọc/đối chiếu source**: đường gọi, annotation, DTO và kiểm tra được mô tả tại thời điểm viết.
- **Kết quả lịch sử**: handoff ghi39 Java test,8 frontend test,24 HTTP checks local đã qua; lượt tài liệu không chạy lại.
- **Suy luận tĩnh/rủi ro**: ghi trong mục Lưu ý hoặc rủi ro; không tự sửa code hoặc khẳng định đã tái hiện.
- **Chưa kiểm chứng tích hợp**: schema Supabase hiện tại, Patient/Admin production, private storage production.
- **Dự kiến, chưa triển khai**: mọi Smart Contract/RPC/MetaMask/giao dịch on-chain trong file16.
- JSON/ID/hash minh họa được ghi rõ; SQL chỉ tương đương logic, không phải log thật.
- Link tương đối tới source kèm #L là dòng khai báo/fragment; JSX hiện có dòng dài nên một dòng có thể chứa cả component.
  Link nên mở trong VS Code; số dòng có thể thay khi source sửa về sau.

## Tài liệu gốc và môi trường

- [DOCTOR_BACKEND_HANDOFF.md](../DOCTOR_BACKEND_HANDOFF.md#L1)
- [DOCTOR_FRONTEND_HANDOFF.md](../DOCTOR_FRONTEND_HANDOFF.md#L1)
- [PROPOSED_doctor_backend_v1.sql](../sql/PROPOSED_doctor_backend_v1.sql#L1): chỉ đề xuất; không tự chạy.
- [FrontendDemoApplication.java](../../src/test/java/com/blockchaincb/demo/FrontendDemoApplication.java#L1): fixture H2 cố định,
  account/quyền minh họa, không phải chức năng Admin production.
- [api-smoke.mjs](../../../frontend/scripts/api-smoke.mjs#L1): HTTP checks yêu cầu marker cách ly; không coi mock frontend là production đã hoạt động.

## Những điểm source dễ gây nhầm

Đăng ký kiểm specialty trước email; login lấy password_hash từ User nhưng tìm Doctor trước khi gọi account policy.
Doctor.user là ManyToOne hiện tại, không phải OneToOne. Hồ sơ cá nhân dùng dirty checking, không gọi save.
expectedVersion khác @Version lockVersion. Hash endpoint có thể trả200 với boolean false.
Upload thử CREATE rồi UPDATE có rủi ro rollback-only cần test riêng; audit503 có thể xảy ra sau commit.
Tất cả được giải thích đúng file flow, không thay source bằng “logic lý tưởng”.

## Cách đọc có hiệu quả

Với mỗi flow, mở bảng file theo thứ tự, đọc đoạn code quan trọng rồi tự kể:
“Input kiểu gì → ai xác định actor → điều kiện nào → bảng/cột nào → response nào → React set gì?”.
Trả lời câu hỏi cuối flow trước khi sang bước tiếp theo. Không cần ghi nhớ getter/setter đơn giản.
