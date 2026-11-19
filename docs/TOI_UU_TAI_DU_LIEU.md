# Tối ưu tải thông tin Doctor — 19/11/2026

Nguyên nhân xác định từ source: API của một tab bị tuần tự hóa toàn bộ; structural metadata được đọc lặp lại
trong nhiều guard; focus/polling có thể gọi chồng khi request chưa hoàn thành.
Log trước có pool2 connections đều bận và chờ10 giây. Chưa đo được latency hoặc truy vấn giữ connection trên Supabase thật.

## Thay đổi

- apiQueue.js:2 request chạy đồng thời thay vì1; request thứ3 đợi slot trống. Một request chậm không chặn slot thứ2.
  Vẫn hỗ trợ hủy request đang đợi/đang chạy, không retry thao tác ghi.
- useLoad trong App.jsx và useRead trong SharedRecordViews: không bắt đầu lượt focus/poll tiếp theo khi đang tải.
  Khi có lượt đọc mới, vẫn xóa medical content cũ và xử lý403/thu hồi quyền như trước.
- AuthProvider.refresh: dùng chung một promise đang tải /me, không mở nhiều refresh song song.
  Chỉ401/403 làm đăng xuất;503/lỗi mạng tạm thời không làm mất phiên. JWT hết hạn vẫn được xử lý riêng.
- SharedGrantRepository, SharedRecordRepository, ClinicalService: MetadataMemo giữ **kết quả kiểm tra cấu trúc thành công**
  trong60 giây ở profile shared-grants. Tránh đọc lại cột/FK/index khi một request đi qua nhiều guard hoặc trang gọi nhiều API.
  Không giữ lock JVM khi chờ connection. Request đồng thời lúc cache lạnh có thể cùng kiểm tra, không có single-flight khóa DB.
- Metadata lỗi không được cache, hết TTL thì phải kiểm lại thành công; không fallback sang metadata hết hạn nếu DB lỗi.
- Không cache quyền Patient, enabled/role/approval, bệnh án hoặc JWT xác thực. Grant hiện hành vẫn SELECT ở mỗi nghiệp vụ,
  thao tác thu hồi được phản ánh ở request tiếp theo. Có test H2 bật TTL rồi thu hồi, Doctor bị403 và danh sách Patient rỗng.
- Pool vẫn2 connections, request gate vẫn2, không tăng session Supabase hoặc bỏ audit.

## Cấu hình và chạy

`doctor.integration.schema-cache-ms=60000` trong application-shared-grants.properties.
Mặc định các môi trường khác là0 (tắt), có thể đặt0 để chẩn đoán. Giới hạn TTL tối đa300000ms.
Khi nhóm thay schema/FK/migration, dừng Doctor và restart sau thay đổi, không đổi ý nghĩa ID khi service còn chạy
với metadata đã cache. Đây không phải cơ chế hỗ trợ hot schema migration.

Restart Backend bằng profile shared-grants, reload React/Ctrl+Shift+R, đăng nhập lại.
Lượt đầu cache lạnh vẫn cần đọc metadata. Các lượt kế tiếp trong60 giây giảm việc truy vấn catalog.
Không hứa số giây hoặc mức tăng tốc chưa đo. Nếu vẫn chậm, cần xem Duration/Waiting của API và trạng thái DB/lock,
không tiếp tục tăng timeout/pool một cách tùy ý. Agent chưa gửi API thật gây audit/write vào Supabase để benchmark.

## Kiểm thử

MetadataMemoTest đếm50 lần kiểm tra trong TTL chỉ gọi loader một lần, kiểm expire/error/disabled.
SharedGrantIntegrationTests kiểm thu hồi quyền ngay cả khi metadata được cache.
React kiểm2 request tiến hành đồng thời, request thứ3 chờ, hủy request ghi đang đợi và không retry;
focus không tạo lượt tải chồng;503 refresh không đăng xuất. Toàn bộ test dùng H2/mock.

Kết quả: Maven verify165/165 PASS (0 failures/errors/skipped), React105/105 PASS trong11 test files,
Vite build PASS (cảnh báo bundle670,00 kB). Chưa benchmark API/Supabase thật; không chạy migration hoặc ghi dữ liệu nhóm.


## Ki?m ch?ng b? sung sau s?a t?i n?n

- React:112 test PASS; npm run build PASS (c?n c?nh b?o bundle l?n).
- C? test ri?ng c?p nh?t n?n kh?ng m?t n?i dung, l?i503 c? c?nh b?o v? h?t h?n60 gi?y,
  401/403/404 x?a n?i dung, ??i patient kh?ng gi? d? li?u patient c?.
- Java: 168 test, 0 failures, 0 errors, 0 skipped.
  C? test ki?m tra quy?n m?i patient m?t l?n theo th? t? ?n ??nh v? v?n ch?n thu h?i.
- Kh?ng thay schema/ghi Supabase trong ??t s?a n?y. Ki?m th? React d?ng mock, Java d?ng mock/H2.
- JWT ti?p t?c ch? n?m trong b? nh?: F5 v?n c?n ??ng nh?p; token h?t h?n ho?c401/403 v?n k?t th?c phi?n.
  Ch?a b? sung refresh-token/cookie, kh?ng ghi token/b?nh ?n v?o localStorage ?? che v?n ?? n?y.
- Ch?a ?o latency Supabase th?c t? sau thay ??i; kh?ng kh?ng ??nh m?i l?i RPC/DB ?? ???c kh?c ph?c.
- Reload Vite nh?n frontend m?i; restart Backend ??ng profile shared-grants ?? n?p t?i ?u truy v?n Java.
