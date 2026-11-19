# Sửa domain challenge và lỗi chờ connection — 19/11/2026

> Tối ưu mới nhất: [tải dữ liệu Doctor](../../../docs/TOI_UU_TAI_DU_LIEU.md) cho2 API song song, cache metadata60 giây và ngăn polling chồng.

## Bằng chứng và nguyên nhân

Ảnh UI báo domain ký không khớp. React cũ so sánh duy nhất config.domain với window.location.host;
vì vậy localhost:5173 khác127.0.0.1:5173, một domain có scheme hoặc port khác cũng không khớp.
WalletLinkService cũ còn ghép URI=https:// cố định dù React development đang dùng http://localhost.
Không có URL thanh địa chỉ trong ảnh nên không khẳng định người dùng đang dùng alias cụ thể nào.

Log kèm theo có SQLTransientConnectionException, pool total=2/active=2/idle=0, waiting5–8, timeout khoảng10 giây.
Một lỗi xuất phát khi JwtAuthenticationFilter gọi DoctorIdentityService.load để mở transaction.
Đây là lỗi lấy connection, không phải bằng chứng JWT sai, sai mật khẩu DB hay thiếu DOCTOR_ROLE.
Đoạn log chưa đủ xác định hai connection đang chờ SQL/lock/network nào; không kết luận đã chứng minh connection leak.
Không tìm được phiên Backend đang lắng nghe8080 ở thời điểm kiểm tra local, nên chưa quan sát lại pool của phiên cũ.

## Thay đổi mã nguồn

- WalletOriginPolicy: Backend quản lý allowlist exact origin (scheme + host + port), không tin Host header.
  Khi domain cấu hình là loopback, mặc định cho localhost/127.0.0.1/[::1] trên cùng scheme/port.
  Domain production mặc định chỉ cho HTTPS chính nó; explicit allowlist không tự thêm alias khác.
  Không nhận wildcard, path, userinfo, query, fragment, control characters hoặc HTTP non-loopback.
- BlockchainController config trả walletLinkPolicy=PENDING_APPROVED_V2_ORIGIN và walletOrigins.
  POST wallet/challenges nhận thêm origin bắt buộc. Nếu có Origin header thì phải khớp body.
  Origin ngoài allowlist bị chặn trước tạo challenge.
- WalletLinkService lưu nguyên message chứa Domain/URI đúng origin đã cho phép. Không thêm cột database.
  Verify tiếp tục kiểm tài khoản, chữ ký personal_sign, ownership challenge, expiry, nonce one-use, ví trùng và rollback.
- React kiểm allowlist và challenge.origin/Domain/URI/chain/address/purpose/expiry trước signMessage.
  Gửi nguyên message Backend tạo; không sửa message phía client, không bỏ kiểm tra domain và không ký giao dịch on-chain.
- apiQueue tuần tự hóa API của tab Doctor, có hủy request đang chờ, không tự retry request ghi.
- Shared-grants profile giới hạn2 HTTP request Doctor đang xử lý, chờ slot tối đa10 giây, giữ pool2 connections.
  Request quá tải trả503 DATABASE_BUSY/Retry-After2 trước khi truy cập DB. Không áp dụng cho route Patient/Admin.
  Giữ audit fail-closed và kiểm JWT/quyền. Không cache hoặc tái sử dụng quyền bệnh nhân để tránh thu hồi.
- Bổ sung timeout JDBC query5 giây, default JPA query5000ms, default transaction10 giây trong shared profile.
  Đây là giới hạn ở ứng dụng/driver; không thay đổi cấu hình global của Supabase.
- JWT filter/exception handler trả503 DATABASE_UNAVAILABLE an toàn khi lấy connection hoặc query timeout,
  không echo SQL/credentials/stack trace cho client và không báo nhầm thành401/403.

Các thay đổi về request/timeout giảm dồn tải và lỗi không kiểm soát. Không bảo đảm chữa được tình trạng
Supabase mất mạng, pooler hết session hoặc connection bị khóa ở database. Không tự tăng pool hoặc terminate session của nhóm.

Cập nhật sau phản hồi DATABASE_BUSY: cấu hình1 request/chờ2 giây ban đầu quá chặt khi nhiều API cùng gọi.
Đã chỉnh thành2 request/chờ10 giây, không tăng pool2 connections. Queue Frontend vẫn giữ request tuần tự trong mỗi tab.
Nếu vẫn timeout với cấu hình mới, cần kiểm tra connection/lock ở DB; không tăng timeout vô hạn hoặc bỏ audit/quyền.

## Khởi động và kiểm tra thủ công

Dừng phiên Doctor Backend cũ của chính mình bằng Ctrl+C, rồi chạy trong Doctor/demo:

```powershell
$env:DOCTOR_WALLET_DOMAIN="localhost:5173"
$env:DOCTOR_WALLET_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=shared-grants"
```

Giữ DB/JWT secret trong môi trường riêng, không dán chúng vào tài liệu/ảnh/log.
Terminal Frontend trong Doctor/frontend chạy npm.cmd run dev. Mở http://localhost:5173 hoặc http://127.0.0.1:5173,
tải lại trang và đăng nhập lại. Nếu dùng port khác (ví dụ preview4173), phải khai báo explicit origin đúng port;
không bỏ check để vượt lỗi. Khi deployment, cấu hình HTTPS domain thật, không dùng HTTP LAN làm ngoại lệ.

Config API phải trả PENDING_APPROVED_V2_ORIGIN và walletOrigins chứa chính window.location.origin.
Challenge API phải trả origin đúng trang, message có Domain tương ứng và URI đúng scheme.
Chỉ sau đó React mới mở yêu cầu ký MetaMask. Backend/Frontend cũ phải restart/reload cùng nhau.
V1/client chỉ gửi address cần cập nhật thêm origin; không fallback sang domain cũ.

DOCTOR_ROLE chưa có không chặn liên kết ví. PENDING vẫn liên kết được; BLOCKED/disabled/REJECTED bị chặn.
Sau khi proof được lưu, không tự đổi approval_status hoặc cấp quyền on-chain.

Nếu còn DATABASE_UNAVAILABLE/AUDIT_UNAVAILABLE sau restart, thành viên có quyền DB có thể kiểm tra metadata tổng hợp:

```sql
SELECT state, wait_event_type, wait_event, count(*) AS connections
FROM pg_stat_activity
WHERE datname = current_database() AND pid <> pg_backend_pid()
GROUP BY state, wait_event_type, wait_event
ORDER BY connections DESC;
```

Query chỉ đọc trạng thái kết nối, không đọc query text hoặc bệnh án. Không tự kill session, đổi schema hoặc sửa thông tin xác thực.

## Kiểm thử và giới hạn

Test local dùng H2/mock: alias hợp lệ ký đúng scope, origin/URI/ví/expiry sai không mở ký, header khác body bị chặn,
PENDING ký được mà vẫn không mở nghiệp vụ, replay/duplicate wallet/rollback giữ nguyên;
API queue hủy được và không retry, request gate giữ giới hạn/release khi exception;
DB timeout ở JWT/Controller trả503 không lộ nội dung exception.

Chưa thực hiện chữ ký trên MetaMask thật hoặc ghi proof/nonce/audit lên Supabase trong lượt sửa này.
Chưa xác nhận đã giải quyết toàn bộ nguyên nhân giữ connection của phiên chạy cũ; cần quan sát sau restart.
Không sửa Patient/Admin, không migration, không cấp role, không deploy hoặc giao dịch Sepolia, không commit/push.

Kết quả thực tế cuối lượt: Maven verify153/153 PASS (0 failures/errors/skipped), React98/98 PASS trong10 test files,
Vite production build PASS; vẫn có cảnh báo bundle lớn660,27 kB. git diff --check không báo lỗi whitespace.

Sau chỉnh giới hạn2 request/chờ10 giây:22 test hồi quy liên quan gate/JWT DB failure/origin/chữ ký PASS,
0 failures/errors/skipped; có thêm test cho hai request chạy đồng thời và request thứ ba bị giới hạn.

Tham khảo: [HikariCP pool sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing),
[Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).
Wallet message đang dùng EIP191 tùy biến cho proof liên kết, không tuyên bố là SIWE hoàn chỉnh.
