# BlockchainCB Backend

Spring Boot 4.0.9 · Java 17 · PostgreSQL Supabase · Spring Security.

Xem [ADMIN_API.md](ADMIN_API.md) để chạy Backend, kiểm tra API, bảo mật và transaction.
Xem [BLOCKCHAIN_INTEGRATION.md](BLOCKCHAIN_INTEGRATION.md) cho các phần còn chờ nhóm Blockchain.

Từ chối hồ sơ PENDING đã có nghiệp vụ và audit cùng transaction. Approve/suspend/reactivate vẫn chưa khả dụng khi thiếu hợp đồng tích hợp. Không có API giao dịch giả. Không thay đổi schema hoặc dữ liệu Supabase trong quá trình triển khai/kiểm thử này.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify -Ppostgres-it
```

Lệnh đầu dùng H2 riêng cho context và mock test. Lệnh thứ hai chạy thêm PostgreSQL tạm độc lập (không cần Docker) để kiểm tra rollback và cạnh tranh. Test không đọc cấu hình database dùng chung. Không chạy script fixture test trên Supabase.
