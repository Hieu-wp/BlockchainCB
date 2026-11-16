# BlockchainCB – Frontend Admin

Giao diện React + Vite được chuyển thể từ 3 mẫu HTML của nhóm:
- `trang1.html`: bảng, bộ lọc, thông tin theo phong cách MediCare
- `trang2.html`: hệ thống thẻ thông tin và bố cục tài liệu
- `trang3.html`: trạng thái bác sĩ, danh sách yêu cầu

## Cài đặt vào dự án hiện tại

**Trước khi chép, sao lưu các file nếu bạn đã sửa `src/App.jsx`, `src/App.css`, `src/index.css`, `src/main.jsx`, `vite.config.js`.**

1. Giải nén ZIP rồi **chép nội dung** vào `BlockchainCB/Frontend/` (không chép cả thư mục bọc `BlockchainCB_Admin_FE`).
2. Trong Terminal mới chạy:

```powershell
cd D:\Tai_Lieu\Nam_4\HK1\BlockChain\BlockchainCB\Frontend
npm.cmd install react-router-dom lucide-react
npm.cmd run dev
```

3. Chạy Backend Spring Boot ở `http://localhost:8080` trong Terminal khác.
4. Mở `http://localhost:5173` và đăng nhập bằng tài khoản ADMIN **của bạn**. Không hardcode thông tin đăng nhập.

## API sử dụng

| Trang | Endpoint |
|---|---|
| Phiên Admin | GET `/api/admin/me` |
| Đăng nhập | POST `/login` (Spring formLogin + CSRF) |
| Dashboard | GET `/api/admin/dashboard` |
| Người dùng | GET `/api/admin/users` |
| Khóa PATIENT | POST `/api/admin/users/{id}/lock` |
| Mở khóa PATIENT | POST `/api/admin/users/{id}/unlock` |
| Danh sách bác sĩ | GET `/api/admin/doctors/search` |
| Nhật ký | GET `/api/admin/audit-logs` |
| Đăng xuất | POST `/logout` |

## Lưu ý an toàn

- Các trang dùng **dữ liệu Backend thật**, không mô phỏng người dùng/hồ sơ bệnh án.
- Chỉ hỗ trợ khóa/mở khóa tài khoản PATIENT theo quy tắc hiện tại. Có hộp xác nhận và lý do khóa.
- Không bật thao tác approve/reject/suspend/reactivate bác sĩ vì Backend còn trả 501 và cần thiết kế đồng bộ Blockchain.
- Nếu POST trả 403, kiểm tra CSRF và session; không tắt CSRF.
- Vite proxy `/api`, `/login`, `/logout` sang Spring Boot. Dùng cookie session HttpOnly; không lưu token vào localStorage.
- Đây là **giao diện Admin**, không chuyển các dữ liệu giả về lịch sử khám/đơn thuốc của trang Patient sang Admin.
- Cấu trúc JSON của `DoctorResponse` cần đối chiếu với DTO thực trong dự án; component có fallback một số tên trường phổ biến.
- Không thay đổi Backend hoặc nhánh Git, chỉ chép file vào `Frontend` và commit có chọn lọc.
