# Bàn giao React Frontend Doctor

> C?p nh?t Doctor?Patient 18/11/2026: xem [DOCTOR_PATIENT_INTEGRATION.md](DOCTOR_PATIENT_INTEGRATION.md) v? feature flag ngu?n quy?n public, API xin quy?n v? gi?i h?n b?nh ?n chung. C?c k?t qu? test b?n d??i c? th? l? k?t qu? l?ch s? c?a phi?n tr??c.

Ngày 16/11/2026. Source: `Doctor/frontend`. React + Vite + React Router + Fetch, giao diện tiếng Việt.
Hoàn thành source và kiểm thử với Java/H2 cách ly; **chưa xác nhận tích hợp Supabase/Patient/Admin production**.
Không đổi Controller, DTO hay Security Java production. Không áp dụng migration, không ghi Supabase, không commit/push.

## Cấu trúc

```text
Doctor/frontend/
  src/main.jsx          # Router và khởi tạo React
  src/App.jsx           # Auth context, route guard, màn hình và trạng thái tải/lỗi
  src/api.js            # Fetch, Bearer, contract API, download Blob
  src/styles.css        # Medical dashboard responsive
  src/App.test.jsx      # Route guard, dashboard, mất quyền
  src/api.test.js       # HTTP contract, token, multipart
  scripts/api-smoke.mjs # HTTP qua proxy tới fixture H2, từ chối máy chủ không có marker
  vite.config.js        # Loopback + proxy /api
  .env.example          # Target Backend, không chứa secret
```

Java chỉ bổ sung `src/test/java/com/blockchaincb/demo/FrontendDemoApplication.java`.
Launcher này không nằm trong production JAR. DataSource được hardcode H2 memory, không lấy URL database từ môi trường.
Secret JWT sinh ngẫu nhiên trong bộ nhớ; mật khẩu demo lấy từ `DOCTOR_DEMO_PASSWORD`.
`X-Doctor-Fixture: isolated-h2` chỉ được thêm trong launcher test này.

## Màn hình và chức năng

1. `/register`: đăng ký đủ thông tin, chuyên khoa, validation, trạng thái PENDING.
2. `/login`: xác thực, báo lỗi sai mật khẩu/tài khoản khóa.
3. `/approval`: chờ duyệt/từ chối, kiểm tra lại trạng thái.
4. `/`: dashboard với tổng bệnh nhân/hồ sơ lấy từ `totalElements` API, không dùng thống kê giả.
5. `/patients`: tìm tên, phân trang, bệnh nhân được cấp quyền.
6. `/patients/:patientId`: danh sách hồ sơ của bệnh nhân.
7. `/records/new?patientId=...`: tạo khám, kiểm tra quyền đọc, tải tệp, Backend kiểm tra CREATE khi lưu.
8. `/records/:id/edit`: toàn bộ nội dung phiên bản mới, `expectedVersion`, không gửi Doctor ID.
9. `/records` và `/records/:id`: lịch sử, chi tiết, các phiên bản, tải tệp, hash.
10. `/profile`: xem và sửa tên/bệnh viện; chuyên khoa, chứng chỉ, email, ví chỉ hiển thị.

Các bảng có loading, empty/error, phân trang; form có trạng thái đang gửi và thông báo thành công/lỗi.
Sidebar đổi thành menu trên màn hình nhỏ. Có nhãn input, focus state, reduced-motion.
Font Be Vietnam Pro tải từ Google Fonts, có font hệ thống dự phòng khi offline.

## API tích hợp

| Method | Endpoint |
|---|---|
| POST | `/api/auth/doctor/register`, `/api/auth/doctor/login` |
| GET / PATCH | `/api/doctor/me` |
| GET | `/api/doctor/patients?q=&page=&size=10` |
| GET | `/api/doctor/patients/{patientId}/records` |
| GET / POST | `/api/doctor/records` |
| GET | `/api/doctor/records/{id}` |
| GET / POST | `/api/doctor/records/{id}/versions` |
| GET | `/api/doctor/records/{id}/versions/{number}` |
| GET | `/api/doctor/records/{id}/versions/{number}/hash` |
| POST multipart `file` | `/api/doctor/patients/{patientId}/attachments` |
| GET Blob | `/api/doctor/records/{id}/versions/{number}/attachments/{attachmentId}` |

POST version gửi `{ expectedVersion, content }`; content chứa patientId, examDate, diagnosis,
prescription, testResults, notes, attachmentIds. Không tự ghi đè khi 409; quay lại tải hồ sơ mới.
503 có thể xảy ra sau DB commit nếu audit lỗi: kiểm tra lịch sử trước khi thử gửi lại.
Danh mục sáu chuyên khoa theo bối cảnh nhóm; chưa có API danh mục để nạp động.
Backend không trả quyền CREATE/UPDATE trong PatientResponse: giao diện cho phép thử thao tác,
Backend quyết định và trả 403 khi thiếu quyền hành động. Ẩn nút không được xem là kiểm soát quyền.

## Chạy demo cách ly

Yêu cầu Node 22.12+ (đã dùng Node 26.13), Java 17, Maven Wrapper.
PowerShell có thể chặn npm.ps1; dùng `npm.cmd`, không đổi execution policy.

Terminal 1:

```powershell
cd C:\Users\ADMIN\BlockchainCB\Doctor\demo
$env:DOCTOR_DEMO_PASSWORD='LocalOnlyDemo@123'
.\mvnw.cmd -B -ntp test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=com.blockchaincb.demo.FrontendDemoApplication" "-Dexec.classpathScope=test"
```

Mật khẩu trên chỉ là ví dụ cho dữ liệu local dùng một lần, không dùng lại mật khẩu thật.
Chờ dòng `ISOLATED H2 FRONTEND DEMO READY`. Backend chỉ bind `127.0.0.1:8081`.
Các tài khoản `approved@doctor.local`, `pending@doctor.local`, `rejected@doctor.local`,
`blocked@doctor.local` dùng cùng mật khẩu bạn đặt trong biến môi trường.
Fixture có hai bệnh nhân được đọc: bệnh nhân thứ nhất có CREATE/UPDATE, thứ hai chỉ READ;
bệnh nhân thứ ba không có grant. Có sẵn hồ sơ và hai phiên bản minh họa.

Terminal 2:

```powershell
cd C:\Users\ADMIN\BlockchainCB\Doctor\frontend
npm.cmd ci
$env:DOCTOR_API_TARGET='http://127.0.0.1:8081'
npm.cmd run dev
```

Mở **http://127.0.0.1:5173/**. Đăng nhập `approved@doctor.local` để demo nghiệp vụ.
Tài khoản PENDING/REJECTED dùng để kiểm tra trang trạng thái, BLOCKED bị chặn đăng nhập.
Tắt Java sẽ mất database demo; tệp demo nằm trong `Doctor/demo/target/frontend-demo-files`.
Không chạy `clean` khi Java demo đang phục vụ tệp. Dừng cả hai bằng Ctrl+C sau demo.

Muốn dùng Backend local khác: đổi DOCTOR_API_TARGET, hoặc `.env.local` (gitignore).
Mặc định target là `http://127.0.0.1:8080`.
Đừng thực hiện các form ghi khi target là database Supabase chung chưa được nhóm cho phép.

### Proxy và CORS

Trình duyệt gửi `/api` cùng origin Vite, Vite chuyển tới Java. Vì vậy không cần nới CORS/Security Java.
Không dùng wildcard origin. Build tĩnh production cần reverse proxy `/api` tới Java và SPA fallback về index.html.
`vite preview` cũng có proxy để kiểm tra local; không phải server production.
Không chứa DB_PASSWORD hay JWT_SECRET trong biến VITE_ hoặc bundle trình duyệt.

## Kiểm thử và kết quả

```powershell
npm.cmd run build
npm.cmd test
# Khi Java fixture + Vite ở trên đang chạy:
$env:DOCTOR_DEMO_PASSWORD='LocalOnlyDemo@123'
npm.cmd run test:api
```

- Build Vite production thành công.
- 8 test Vitest/React Testing Library qua: route guard, PENDING, dashboard, dữ liệu bị xóa khi mất quyền,
  Bearer, query encoding, DTO phiên bản, 401/403, multipart và không lưu localStorage.
- 24 kiểm tra HTTP thật qua Vite → Java → H2 qua: login, trạng thái, profile, phân trang bệnh nhân,
  xem/tạo/cập nhật, conflict 409, các phiên bản/hash, upload/download, READ-only/không có grant 403 và đăng ký.
- `npm install` sau nâng Vitest báo 0 vulnerability; package-lock được giữ để tái lập.
- Launcher test Java đã compile và khởi động với H2. Không sửa Java production nên không chạy lại toàn bộ regression backend.
- Kiểm tra trực quan chưa hoàn tất: browser access host không khả dụng; Windows Computer Use tự dừng
  vì không xác định chắc chắn URL để thực thi chính sách an toàn. Chưa khẳng định đã QA pixel/mobile trong browser.

Smoke script chỉ dùng loopback và kiểm tra marker H2 trước mọi thao tác ghi. Không dùng mock API để
khẳng định production hoạt động. Component test dùng response mock và được phân biệt với HTTP test thật.

## Bảo mật và giới hạn

- Token và dữ liệu y tế chỉ nằm trong bộ nhớ React/module; không localStorage, sessionStorage hay cache bệnh án.
- Tải lại trang cần đăng nhập; logout xóa token/user và unmount nội dung. 401 hủy phiên.
- PENDING/REJECTED chỉ truy cập trạng thái và hồ sơ cá nhân; APPROVED vẫn cần quyền bệnh nhân ở Backend.
- Role/enabled/approval được Backend kiểm tra lại. `/me` được làm mới khi focus và mỗi 30 giây.
- Các trang đọc tải lại khi focus/mỗi 30 giây; lỗi 403 xóa dữ liệu đang hiển thị. Form 403 xóa bản nháp/tệp.
- Form chỉnh sửa không tự remount theo polling làm mất bản nháp; kiểm tra quyền bệnh nhân bên trong vẫn hoạt động.
- Không thể thu hồi thông tin đã đọc ngay tức thì chỉ bằng UI polling. Chưa có WebSocket/SSE từ module Patient.
- File tải xuống là bản sao trên thiết bị; người dùng cần bảo quản như dữ liệu nhạy cảm.
- Bản nháp chỉ trong bộ nhớ, chưa có autosave; file upload chưa gắn version có thể mồ côi theo giới hạn Backend.
- Chưa có test browser end-to-end/visual responsive; đã có component và HTTP integration riêng.
- Chưa thực hiện thao tác consent/duyệt Admin; đây là module của nhóm.
- Supabase còn phụ thuộc migration được duyệt, mapping patient_links, grant và storage production.
- React render text, không dùng dangerouslySetInnerHTML cho nội dung bệnh án.

## Ranh giới Blockchain

Màn hình chi tiết hiển thị SHA-256 và ba cờ kiểm tra off-chain của Backend, trạng thái NOT_SUBMITTED.
Vị trí `future-slot` chỉ là nhãn cho giai đoạn sau, không có nút giao dịch hoặc thông báo thành công giả.
Màn hình Profile hiển thị walletAddress read-only, không kết nối MetaMask.

Bạn tự triển khai Solidity, Hardhat, Sepolia, MetaMask, ghi hash và xác minh contract.
Sau này thay nhãn giai đoạn sau trong RecordDetail bằng luồng giao dịch thật và cập nhật trạng thái
qua API do nhóm thống nhất; giữ nguyên kiểm tra quyền khi xem hash/receipt.
