# 17 — Lộ trình học lại code theo request

[← Mục lục](00_INDEX.md)

> Căn cứ: source đang có ngày 17/11/2026. Đây là phân tích tĩnh, không chạy API/test trong lượt viết tài liệu. Kết quả test ở handoff là bằng chứng lịch sử, không phải kiểm chứng Supabase hiện tại. SQL bên dưới chỉ minh họa tương đương logic, không phải log Hibernate.

## 1. Cách dùng lịch

Bạn có thể học16 buổi, mỗi buổi45–75 phút; không phải mốc deadline bắt buộc.
Dành10 phút đọc mục tiêu/sơ đồ,20–30 phút mở source theo link,15 phút tự kể lại flow và10 phút trả lời câu hỏi.
Nếu chưa giải thích được “ai gọi method này và nó gọi ai”, quay lại bước tương ứng, không học thuộc getter/setter.

Tài liệu là bản đồ, source là căn cứ cuối cùng. Các ví dụ ID/hash là minh họa.
Không dùng Supabase chung cho bài tập. Lộ trình này ưu tiên đọc/truy vết và ghi chú ngoài source;
không yêu cầu sửa code, tạo account thật hoặc chạy migration.

## 2. Các bài học

| Buổi | Chủ đề | Tài liệu | Source/method cần tìm | Sau bài hiểu được | Bài tập nhỏ |
|---|---|---|---|---|---|
| 1 | Kiến trúc và khởi động | [01_KIEN_TRUC_TONG_THE.md](01_KIEN_TRUC_TONG_THE.md) | [DemoApplication.java](../../src/main/java/com/blockchaincb/demo/DemoApplication.java#L11) — `main` | Chỉ ra entry point, package scan, vị trí React/Java/DB. | Vẽ bằng giấy đường request qua 5 lớp; ghi dữ liệu đổi kiểu ở mỗi lớp. |
| 2 | Controller, DTO, service, repository | [06_FLOW_HO_SO_CA_NHAN.md](06_FLOW_HO_SO_CA_NHAN.md) | [DoctorController.java](../../src/main/java/com/blockchaincb/demo/controller/DoctorController.java#L46) — `public DoctorResponse update` | Giải thích principal, DTO đầu vào, response và người gọi service. | Truy PATCH me từ React đến dirty checking mà không chạy API. |
| 3 | Entity và database | [15_DATABASE_VA_JPA.md](15_DATABASE_VA_JPA.md) | [Doctor.java](../../src/main/java/com/blockchaincb/demo/entity/Doctor.java#L13) — `@ManyToOne` | Phân biệt User/Doctor ID, lazy relation và scalar FK. | Đánh dấu FK nào thuộc SQL đề xuất, annotation nào không tự tạo constraint. |
| 4 | Đăng ký nguyên tử | [02_FLOW_DANG_KY_DOCTOR.md](02_FLOW_DANG_KY_DOCTOR.md) | [AuthService.java](../../src/main/java/com/blockchaincb/demo/service/AuthService.java#L34) — `public Doctor register` | Hiểu validation, specialty, BCrypt và hai INSERT. | Mô tả rollback nếu save Doctor lỗi; chỉ vào test có tên tương ứng. |
| 5 | Đăng nhập từ User | [03_FLOW_DANG_NHAP_JWT.md](03_FLOW_DANG_NHAP_JWT.md) | [AuthService.java](../../src/main/java/com/blockchaincb/demo/service/AuthService.java#L86) — `public Doctor login` | Theo Optional, matches, findByUserId, policy đúng thứ tự. | Giải thích vì sao encode lại password rồi so hash không đúng. |
| 6 | JWT Claims | [03_FLOW_DANG_NHAP_JWT.md](03_FLOW_DANG_NHAP_JWT.md) | [JwtService.java](../../src/main/java/com/blockchaincb/demo/security/JwtService.java#L25) — `public String generateToken` | Nắm subject/issuer/audience/exp/signature; không tin doctorId claim. | Viết bảng claim và nguồn giá trị, không tạo token hoặc secret thật. |
| 7 | Spring Security | [04_FLOW_XAC_THUC_PHAN_QUYEN.md](04_FLOW_XAC_THUC_PHAN_QUYEN.md) | [JwtAuthenticationFilter.java](../../src/main/java/com/blockchaincb/demo/security/JwtAuthenticationFilter.java#L29) — `protected void doFilterInternal` | Tách authentication, authorization và hai tầng error handler. | Truy request thiếu token, token lỗi và account disabled; xác định 401/403. |
| 8 | Phê duyệt account | [05_FLOW_PHE_DUYET_DOCTOR.md](05_FLOW_PHE_DUYET_DOCTOR.md) | [DoctorPolicy.java](../../src/main/java/com/blockchaincb/demo/security/DoctorPolicy.java#L10) — `public static void requireAccount` | Hiểu PENDING/REJECTED/APPROVED/BLOCKED và enabled. | Tự lập bảng 5 tài khoản giả định, dự đoán /me và /records cho từng tài khoản. |
| 9 | Patient Access Grant | [07_FLOW_DANH_SACH_BENH_NHAN.md](07_FLOW_DANH_SACH_BENH_NHAN.md) | [DatabasePatientAccessPolicy.java](../../src/main/java/com/blockchaincb/demo/service/DatabasePatientAccessPolicy.java#L22) — `public Patient require` | Hiểu interface DI, Stream.anyMatch, READ/CREATE/UPDATE, thời hạn. | Với hai grant khác quyền, giải thích require READ/UPDATE và query visible. |
| 10 | Đọc dữ liệu an toàn | [08_FLOW_XEM_BENH_AN.md](08_FLOW_XEM_BENH_AN.md) | [MedicalRecordService.java](../../src/main/java/com/blockchaincb/demo/service/MedicalRecordService.java#L45) — `private MedicalRecord allowed` | Theo record.patientId, version của record và DTO trả ra. | Nêu vì sao sửa URL không thể đổi principal và không đủ quyền. |
| 11 | Tạo MedicalRecord và v1 | [09_FLOW_TAO_HO_SO_KHAM.md](09_FLOW_TAO_HO_SO_KHAM.md) | [MedicalRecordService.java](../../src/main/java/com/blockchaincb/demo/service/MedicalRecordService.java#L58) — `public MedicalRecordResponse create` | Giải thích root, snapshot, author và saveAndFlush. | Vẽ object r/v trước và sau save, điền id ở thời điểm được DB cấp. |
| 12 | RecordVersion và cạnh tranh | [10_FLOW_CAP_NHAT_PHIEN_BAN.md](10_FLOW_CAP_NHAT_PHIEN_BAN.md) | [MedicalRecordService.java](../../src/main/java/com/blockchaincb/demo/service/MedicalRecordService.java#L73) — `public MedicalRecordResponse update` | Phân biệt expectedVersion, currentVersion và @Version lockVersion. | Mô phỏng A/B cùng đọc v1: chỉ ra hai vị trí có thể sinh409. |
| 13 | Canonical và SHA-256 | [11_FLOW_LICH_SU_VA_SHA256.md](11_FLOW_LICH_SU_VA_SHA256.md) | [HashService.java](../../src/main/java/com/blockchaincb/demo/service/HashService.java#L40) — `public String canonical` | Tự giải thích UTF-8 byte length, NFC, snapshot/column/link. | Tính chuỗi canonical cho hai field đơn giản, chưa cần gọi hash hay viết contract. |
| 14 | Tệp và audit | [12_FLOW_TEP_DINH_KEM.md](12_FLOW_TEP_DINH_KEM.md) | [AttachmentService.java](../../src/main/java/com/blockchaincb/demo/service/AttachmentService.java#L39) — `public UploadResponse upload` | Hiểu multipart, private key storage, transaction DB/filesystem. | Đọc thêm flow13: so kết quả audit ATTEMPT lỗi và outcome lỗi sau commit. |
| 15 | React và HTTP | [14_FLOW_REACT_FRONTEND.md](14_FLOW_REACT_FRONTEND.md) | [App.jsx](../../../frontend/src/App.jsx#L26) — `export function useLoad` | Nắm state/context/router/effect/cleanup và body của api.js. | Theo một click xem bản cũ thành những HTTP request cụ thể; ghi setState nào chạy. |
| 16 | Ranh giới Blockchain | [16_CHUAN_BI_TICH_HOP_BLOCKCHAIN.md](16_CHUAN_BI_TICH_HOP_BLOCKCHAIN.md) | [MedicalRecordService.java](../../src/main/java/com/blockchaincb/demo/service/MedicalRecordService.java#L195) — `public HashResponse hash` | Biết phần hiện hữu, dự kiến và nguồn bằng chứng độc lập. | Thiết kế bằng lời receipt/outbox/consent; không triển khai code hay transaction on-chain. |

## 3. Nhịp học gợi ý trong hai tuần

- Tuần1: buổi1–8 (có thể ghép hai buổi ngắn vào một ngày cuối tuần). Kết thúc bằng việc
  tự giải thích đăng ký → login → JWT request → policy account.
- Tuần2: buổi9–16. Nên dành hai lượt riêng cho phiên bản/concurrency và canonical/hash,
  vì đây là nền trước khi viết Solidity. Chưa hiểu transaction thì chưa chuyển sang RPC on-chain.
- Nếu chỉ có7 ngày: ngày1 kiến trúc/JPA; ngày2 đăng ký/login; ngày3 JWT/security/approval;
  ngày4 grants/đọc; ngày5 tạo/versioning; ngày6 hash/tệp/audit; ngày7 React và ranh giới Blockchain.
  Đây là lịch rút gọn để ôn, không thay các tài liệu chi tiết.

## 4. Bài thực hành trên giấy: request cards

Tạo một thẻ cho mỗi endpoint, với bảy dòng:

1. Màn hình/handler React.
2. URL + method + body.
3. Principal và policy cần qua.
4. Controller + DTO.
5. Service + repository + Entity.
6. Transaction/flush/commit và dữ liệu response.
7. Một lỗi400/401/403/409 và đường hiển thị ở React.

Bắt đầu PATCH me, sau đó POST register, cuối cùng POST versions.
Không copy hash/token/password thật vào thẻ.

## 5. Đọc test mà không nhầm với chạy test

Mở [AuthServiceTest.java](../../src/test/java/com/blockchaincb/demo/AuthServiceTest.java#L1): mock giúp tập trung điều kiện nghiệp vụ.
Mở [DemoApplicationTests.java](../../src/test/java/com/blockchaincb/demo/DemoApplicationTests.java#L1): MockMvc + JPA/H2 chứng minh
luồng HTTP/transaction ở môi trường cách ly, có fixture/cleanup riêng.
Mở [JwtServiceTest.java](../../src/test/java/com/blockchaincb/demo/JwtServiceTest.java#L1) và
[HashServiceTest.java](../../src/test/java/com/blockchaincb/demo/HashServiceTest.java#L1): test thuật toán/token không cần Supabase.
Mở [AuditFilterTest.java](../../src/test/java/com/blockchaincb/demo/AuditFilterTest.java#L1) và
[PrivateStorageTest.java](../../src/test/java/com/blockchaincb/demo/PrivateStorageTest.java#L1): đọc các tình huống hỏng dịch vụ.

Frontend có [api.test.js](../../../frontend/src/api.test.js#L1) và [App.test.jsx](../../../frontend/src/App.test.jsx#L1).
[api-smoke.mjs](../../../frontend/scripts/api-smoke.mjs#L1) dùng HTTP thật nhưng đòi marker H2 trước khi ghi.
[FrontendDemoApplication.java](../../src/test/java/com/blockchaincb/demo/FrontendDemoApplication.java#L1) chỉ dùng test classpath.
Trong lượt tài liệu này không chạy lại bất kỳ test nào và không khởi động ứng dụng.

Với mỗi test, tự ghi: “given dữ liệu gì → when gọi method nào → then assert điều gì”.
Ví dụ concurrentWritersProduceOneNewVersion kiểm một version mới, không đảm bảo mọi DB production đều
có cùng hành vi khóa với H2. Không có test nhánh thì ghi là cần bổ sung test, không gọi là đã được chứng minh.

## 6. Tiêu chí tự đánh giá

Bạn đã sẵn sàng trao đổi kiến trúc với nhóm khi có thể:
- Mở source bất kỳ trong flow và xác định người gọi/người được gọi.
- Giải thích User ID khác Doctor ID, patient bridge khác Patient ID teammate.
- Nêu vì sao @Version không phải lịch sử bệnh án, flush không phải commit.
- Giải thích người tạo record vẫn bị chặn sau thu hồi grant.
- Tái tạo ý tưởng canonical v1 và đọc ba boolean hash mà không gọi là Ethereum confirmation.
- Chỉ rõ một rủi ro thực tế: duplicate race, upload rollback-only, filesystem orphan hoặc audit503 sau commit.

## 7. Câu hỏi cuối khóa

1. Trong create, bước nào cần root.id và vì sao?
2. Nếu tắt JWT filter nhưng giữ JwtService.generateToken, request sau có được nhận diện không?
3. Trong profile update, ai gọi UPDATE SQL khi không có save?
4. Snapshot hash khớp nhưng currentContentMatchesHash=false chứng tỏ gì?
5. Khi thêm on-chain consent, chỉ thay PatientAccessPolicy đã đủ cho query danh sách chưa?
6. RPC thành công nhưng DB rollback sẽ để lại vấn đề gì nếu gọi trước commit?

Đọc lại ba flow09–11 trước khi tự học Smart Contract; file16 giúp phân biệt công việc Java đã có với công việc tương lai.
