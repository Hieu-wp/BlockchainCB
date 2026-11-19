# Mật khẩu và DevTools — Doctor

## Giới hạn cần hiểu

Network → Payload của trình duyệt đang đăng nhập hiển thị dữ liệu chính trình duyệt đó gửi.
React không thể ngăn chủ trình duyệt hoặc extension có quyền truy cập đọc dữ liệu này.
`type=password` chỉ che ký tự trong ô nhập. HTTPS mã hóa đường truyền, không che request khỏi DevTools.
BCrypt dùng bảo vệ mật khẩu lưu trong database, không phải cơ chế che payload.
Không đổi thành Base64 hoặc hash tĩnh ở Frontend để tạo cảm giác an toàn: giá trị đó có thể trở thành thông tin đăng nhập dùng lại.

Nếu ảnh/HAR/Copy as cURL chứa mật khẩu hoặc token đã được chia sẻ, cần đổi mật khẩu qua quy trình quản lý tài khoản
được nhóm cho phép; đổi cả nơi dùng chung mật khẩu. Agent chưa thay đổi mật khẩu hoặc dữ liệu Supabase.
Xóa capture bằng nút Clear trong Network chỉ xóa bản hiển thị local, không thu hồi ảnh/file đã chia sẻ hay vô hiệu JWT đang tồn tại.
Không gửi ảnh payload, Authorization header, HAR hoặc cURL đăng nhập cho người khác khi chưa loại bỏ thông tin xác thực.

## Thay đổi trong source

- `frontend/src/App.jsx`, AuthPage.submit: xóa ô password ngay khi lấy dữ liệu gửi; bỏ tham chiếu password khỏi
  object form trong finally. Không khẳng định đã xóa được các bản sao chuỗi trong bộ nhớ trình duyệt hoặc capture Network.
- `frontend/src/api.js`, requireAuthTransport: chặn gửi đăng nhập/đăng ký từ trang không dùng HTTPS, ngoại trừ HTTP
  trên localhost/127.0.0.1/IPv6 loopback phục vụ phát triển trên chính máy. Địa chỉ IP LAN hoặc domain HTTP không được ngoại lệ.
  Đây là lớp chặn phía UI, không thay thế yêu cầu TLS ở reverse proxy/Backend.
- `LoginRequest.java`, `RegisterRequest.java`: password có JsonProperty WRITE_ONLY, vẫn đọc được request để kiểm tra
  BCrypt nhưng bị loại khi serialize DTO ra JSON. Controller tiếp tục dùng Response DTO không có password.
- Qua rà soát source Doctor: không thấy console/request-body logger lưu password; JWT hiện giữ trong bộ nhớ,
  không có code lưu credentials vào localStorage/sessionStorage. Audit chỉ lưu metadata thao tác.

## Khi triển khai

Trang đăng nhập và toàn bộ trang/API đã xác thực phải dùng HTTPS, certificate hợp lệ.
Nếu có reverse proxy, cấu hình TLS tại proxy, chỉ tin forwarded headers từ proxy được kiểm soát và bảo vệ cả đường
proxy → Java nếu đi qua mạng. Không công khai Vite dev server hoặc cổng Java HTTP làm cổng đăng nhập production.
Chưa có domain/certificate/deployment cụ thể trong nhiệm vụ này nên agent không tuyên bố đã cấu hình TLS production.
Không bật logging request body hoặc gửi password/JWT tới công cụ analytics/error reporting.

Nguồn: [OWASP Authentication Cheat Sheet — Transmit Passwords Only Over TLS](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#transmit-passwords-only-over-tls-or-other-strong-transport).

## Kiểm thử local

`AuthRequestPrivacyTest` kiểm tra deserialize password và không serialize password của cả hai DTO; không kết nối DB.
Test React kiểm tra ô password trống trong lúc request còn pending và sau lỗi, không tự gửi lại;
test API kiểm tra HTTPS/loopback và không gọi fetch khi trang HTTP remote.
Các test dùng credentials fixture riêng, không chứa tài khoản trong ảnh của người dùng.

## Cập nhật 19/11/2026: Payload mã hóa của giao diện Doctor

Đã bổ sung JWE compact theo RFC7516, RSA-OAEP-256 và AES-256-GCM. Không thêm dependency:
Frontend dùng WebCrypto, Java giải mã bằng JJWT0.12.6 có sẵn. Mỗi request có AES key và IV mới;
khóa RSA private không bao giờ trả về Frontend, không ghi ra file hay database.

Luồng hiện tại:

1. AuthPage lấy dữ liệu form rồi xóa ô password, gọi api.login()/api.register().
2. api.js gọi GET `/api/auth/doctor/encryption-challenge?purpose=login` hoặc `register`.
   Response chỉ có public JWK, keyId, nonce ngẫu nhiên32 byte và thời hạn120 giây; Cache-Control=no-store.
3. authEncryption.encryptCredentials() mã hóa toàn bộ credentials gồm email/password và các trường đăng ký,
   gửi POST `/api/auth/doctor/login/encrypted` hoặc `/api/auth/doctor/register/encrypted` với **duy nhất** `{"payload":"<JWE>"}`.
4. AuthController → AuthPayloadService: kiểm header/thuật toán, giải mã, kiểm purpose/nonce/hết hạn,
   lấy nonce một lần theo thao tác atomic, Bean Validation rồi gọi lại luồng AuthService đang có.
   Role, enabled, phê duyệt, BCrypt, transaction đăng ký và JWT giữ nguyên.

Không tự fallback sang endpoint plaintext nếu thiếu WebCrypto, challenge lỗi, sai khóa hoặc giải mã thất bại.
Không tự retry một request đăng ký/đăng nhập đã gửi. Request mới cần challenge mới; nonce bị tiêu thụ cả khi sai mật khẩu.
Envelope giới hạn16384 ký tự, pending challenge giới hạn1024, quá giới hạn trả429. Không nhận compression,
không nhận thuật toán khác, không tải khóa từ URL client cung cấp. Lỗi chung không echo credentials hoặc crypto exception.

Trong Network, **request mới** login/encrypted sẽ chỉ hiện payload là chuỗi mã hóa.
Request plaintext đã chụp trước đó không thể sửa bằng source mới: Clear Network và tải lại trang trước khi test.
Khởi động lại Backend Doctor và Frontend theo lệnh chạy thường dùng/profile shared-grants. Không cần migration.

### Ranh giới và khả năng tương thích

- Các endpoint plaintext cũ `/login`, `/register` vẫn được giữ để tránh phá API client có sẵn;
  giao diện Doctor hiện tại không gọi chúng. Postman, smoke script cũ hoặc client cũ vẫn có thể gửi plaintext.
  Việc tắt API cũ phải được nhóm kiểm tra client trước; không khẳng định mọi client đã chuyển sang JWE.
- Response đăng nhập vẫn chứa email hồ sơ và JWT theo API hiện tại, cũng như Bearer header khi gọi API khác.
  Không tuyên bố ẩn toàn bộ danh tính, token hoặc network traffic khỏi DevTools.
- Người kiểm soát trình duyệt có thể đặt breakpoint trước khi mã hóa, đọc DOM hoặc đánh cắp dữ liệu qua XSS/extension.
  Mục tiêu này chỉ giảm hiển thị credentials rõ trong **request payload** và các bản capture request thông thường.
- Vẫn bắt buộc HTTPS ở môi trường triển khai: chống thay public key hoặc sửa JavaScript trên đường truyền.
  Chưa cấu hình certificate/reverse proxy production trong nhiệm vụ này.
- Khóa RSA sinh trong bộ nhớ mỗi lần khởi động; nonce store cũng ở bộ nhớ một JVM. Phù hợp local/demo một instance.
  Restart giữa challenge và submit khiến request bị từ chối; người dùng nhập lại và gửi request mới.
  Nhiều instance cần shared nonce store với consume atomic và quản lý/luân chuyển khóa thống nhất trước khi triển khai.
- Giới hạn pending không thay thế rate limit theo IP/tài khoản. Cần rate limiting tại gateway và rà soát bảo mật trước production.
- Không thay đổi schema Supabase, password đang lưu hoặc chức năng Patient/Admin; không gửi request bằng tài khoản thật trong ảnh.

Kiểm thử bổ sung: WebCrypto round-trip/tamper/IV ngẫu nhiên/Unicode; API thật về wire format trong test mock HTTP;
Java kiểm nonce/expiry/race/thuật toán/compression/size và gọi chính module JS bằng Node để kiểm tương thích.
HTTP/H2 tests kiểm đăng ký, đăng nhập JWT PENDING, disabled, validation, replay sau sai mật khẩu và từ chối plaintext ở endpoint mới.
Node cần có trong PATH để chạy kiểm thử tương thích Java→module JavaScript, không dùng mạng hay credential thật.

Kết quả thực tế bản cập nhật: Maven verify141/141 PASS (0 failures/errors/skipped), React88/88 PASS trong9 test files,
Vite production build PASS (còn cảnh báo chunk lớn). Chưa gửi request đăng nhập bằng tài khoản Supabase thật hoặc kiểm tra trên trình duyệt của người dùng.

Tham khảo: [RFC7516](https://www.rfc-editor.org/rfc/rfc7516.html),
[JJWT0.12.6](https://github.com/jwtk/jjwt/tree/0.12.6),
[WebCrypto](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto).
