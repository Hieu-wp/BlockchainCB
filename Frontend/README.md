# BlockchainCB – Frontend Admin

React + Vite, JavaScript/JSX, React Router và Lucide. Phạm vi Admin; ứng dụng luôn lấy dữ liệu Backend thật.

## Chạy dự án

Backend cần Java 17, cấu hình PostgreSQL/Supabase hợp lệ theo hướng dẫn của nhóm và tài khoản ADMIN có sẵn. Không đưa thông tin kết nối hoặc mật khẩu vào Git.

Terminal Backend:

```powershell
cd D:\Tai_Lieu\Nam_4\HK1\BlockChain\BlockchainCB\Backend
.\mvnw.cmd spring-boot:run
```

Terminal Frontend:

```powershell
cd D:\Tai_Lieu\Nam_4\HK1\BlockChain\BlockchainCB\Frontend
npm.cmd ci
npm.cmd run dev
```

Mở http://localhost:5173; Backend ở http://localhost:8080. Vite dừng nếu cổng 5173 đã bị chiếm. Khi dependencies đã cài đủ, chỉ cần `npm.cmd run dev`.

## Xác thực và API

- GET `/login` lấy input `_csrf`; POST `/login` gửi form URL encoded với username, password và CSRF.
- POST `/login` hiện trả JSON thông báo thành công cũ. Frontend luôn xác minh tiếp GET `/api/admin/me`: `daXacThuc === true`, `vaiTro === 'ADMIN'`, `danhSachQuyen` chứa `ROLE_ADMIN`.
- Tải lại trang kiểm tra `/me`. Hết phiên đưa về form đăng nhập. Khi API trả 403, kiểm tra `/me` để phân biệt hết phiên với CSRF bị từ chối.
- Mỗi POST lấy CSRF mới. Không tự gửi lại thao tác ghi khi lỗi. Không lưu mật khẩu, session ID hoặc CSRF vào localStorage.
- Proxy phát triển chuyển `/api`, `/login`, `/logout` sang Backend. Bản build production cần reverse proxy cùng nguồn và trả `index.html` cho URL React Router; `dist/` không tự cung cấp Backend.

| Chức năng | API |
|---|---|
| Thống kê 6 chỉ số | GET `/api/admin/dashboard` |
| Tìm kiếm/lọc/phân trang người dùng | GET `/api/admin/users` |
| Chi tiết người dùng | GET `/api/admin/users/{id}` |
| Khóa PATIENT có lý do | POST `/api/admin/users/{id}/lock` |
| Mở khóa PATIENT | POST `/api/admin/users/{id}/unlock` |
| Tìm kiếm/lọc/phân trang bác sĩ | GET `/api/admin/doctors/search` |
| Tất cả bác sĩ chờ duyệt | GET `/api/admin/doctors/pending` |
| Chi tiết bác sĩ | GET `/api/admin/doctors/{id}` |
| Nhật ký có phân trang | GET `/api/admin/audit-logs` |
| Đăng xuất | POST `/logout` |

Danh sách người dùng/nhật ký dùng DTO phân trang tiếng Việt; tìm kiếm bác sĩ dùng Spring Page (`content`, `totalElements`, `totalPages`). PENDING là mảng không phân trang: danh sách toàn bộ hồ sơ chờ duyệt, độc lập bộ lọc tìm kiếm. Giấy phép bác sĩ dùng trường `soGiayPhep`, nơi công tác dùng `noiCongTac`.

Các nút duyệt/từ chối/đình chỉ/kích hoạt lại bác sĩ bị vô hiệu hóa vì Service đang trả 501. Theo dõi giao dịch Blockchain chờ nhóm cung cấp API thật. Không khóa ADMIN/DOCTOR qua màn hình người dùng.

## Các màn hình quản trị

| Route | Trang và phạm vi |
|---|---|
| `/` | Dashboard, 6 số liệu từ Backend |
| `/users` | Tìm kiếm/lọc/phân trang, chi tiết và khóa/mở khóa PATIENT |
| `/doctors` | Danh sách, nhóm trạng thái qua GET search, PENDING, chi tiết; hành động ghi chưa khả dụng |
| `/audit-logs` | Nhật ký quản trị và phân trang |
| `/transactions` | Giao diện chờ tích hợp; không gọi API giao dịch, không có hash/số lượng giả |
| `/profile` | Thông tin chỉ đọc từ `/api/admin/me` |
| `/system-status` | Kiểm tra lại 6 GET hiện có, kết quả từng endpoint và thời điểm kiểm tra |
| Đường dẫn khác | Trang 404, giữ URL và có nút về Dashboard |

System Status gọi GET `/api/admin/health`, `/api/admin/me`, `/api/admin/dashboard`, `/api/admin/users`, `/api/admin/doctors/search`, `/api/admin/audit-logs`. Các danh sách giới hạn một bản ghi; trang chỉ giữ kết quả kiểm tra, không hiển thị hoặc lưu dữ liệu cá nhân từ các request đó. Kết quả là ảnh chụp tại thời điểm kiểm tra, không theo dõi nền liên tục. `/health` hiện chỉ trả trạng thái Admin API, không phải API giám sát hạ tầng chuyên dụng. Supabase/PostgreSQL, Blockchain RPC và Smart Contract luôn ghi chưa thể xác minh.

Transactions có component trình bày `BangGiaoDich`, hỗ trợ loading, lỗi/thử lại, nội dung bảng và thông tin phân trang qua props. Route hiện tại không gắn nguồn API; bộ lọc và phân trang bị vô hiệu hóa. Khi nhóm cung cấp DTO/API thật mới thêm adapter trong `services/`, dữ liệu bảng và xác thực mạng/chain ID/hash trước khi tạo explorer link. Không có giả định mạng hay endpoint giao dịch trong bản hiện tại.

Nhóm nhanh bác sĩ giữ từ khóa đã áp dụng và gọi bộ lọc Backend; chuyển sang danh sách PENDING rồi trở lại không đặt lại trang tìm kiếm trước đó. Bốn nút hành động có giải thích riêng, không gắn request POST.

## Kiểm tra

```powershell
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Test hồi quy bằng Node không cần thư viện bổ sung. Fetch và DOMParser được giả lập để kiểm tra hợp đồng session, Form Login, CSRF, lỗi mạng/HTTP và mã hóa bộ lọc. Kết quả này không thay thế kiểm thử tích hợp Spring Security và database thật.

Checklist tích hợp với môi trường được nhóm cho phép:

1. Đăng nhập đúng/sai, tải lại URL `/users`, đăng xuất và thử truy cập lại.
2. Dashboard, tìm kiếm/lọc/phân trang, chi tiết tài khoản/bác sĩ, PENDING và nhật ký.
3. Chỉ sau khi được cho phép ghi database: khóa/mở khóa PATIENT thử nghiệm và xác minh nhật ký.
4. Kiểm tra hết phiên, CSRF sai, Backend dừng, mạng chậm, màn hình 320/390/768/1440px.

Không tìm thấy `trang1.html`, `trang2.html`, `trang3.html` trong repository ở lần khảo sát này. Giao diện kế thừa CSS hiện có và bảng màu trong yêu cầu; cần cung cấp lại HTML để đối chiếu thiết kế gốc.

## Git

Chỉ làm việc trên `Bra_Admin`. Trước khi commit, chạy `git status`, `git branch --show-current`, `git diff --check`, xem lại diff và chỉ stage file Frontend có chủ đích. Không tự commit, push, chuyển/tạo nhánh, merge, rebase hoặc xóa dữ liệu.
