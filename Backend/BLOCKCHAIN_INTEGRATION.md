# Phụ thuộc Blockchain – Chưa triển khai gửi giao dịch

## Kết quả khảo sát

Không tìm thấy Smart Contract `.sol`, ABI, Hardhat/Truffle, địa chỉ contract, chain ID, RPC adapter, entity/repository lịch sử giao dịch hoặc receipt reader trong repository hiện tại. Không có nguồn giao dịch thật đủ xác nhận để triển khai endpoint đọc transactions.

`DieuKienTichHopBacSi` và `BlockchainBacSiChuaTichHop` chỉ tách thông tin phụ thuộc của ba thao tác chưa sẵn sàng. Đây **không phải** adapter gửi giao dịch, không trả receipt/hash, không có cờ bật để bỏ qua Blockchain. Service vẫn trả 501 sau preflight, kể cả khi lý do được thay thế. Cần bổ sung bộ điều phối thực tế sau khi hợp đồng kỹ thuật được phê duyệt.

## Những gì nhóm cần cung cấp

1. ABI và phiên bản contract, địa chỉ contract, chain ID/network, RPC đáng tin cậy; cách xác minh địa chỉ và chain đúng khi khởi động.
2. Phương thức cấp/thu hồi/khôi phục quyền, đối số, event và điều kiện quyền gửi; định nghĩa bác sĩ trên chain được nhận diện bằng ví hay ID.
3. Cơ chế signer/ủy quyền và quản lý khóa qua môi trường/vault; không gửi private key vào chat hoặc Git.
4. Cách theo dõi hash/receipt, số confirmation, timeout, reorg, retry/idempotency, nonce và phân biệt RPC lỗi với giao dịch revert.
5. Quy ước doctors.status, liên hệ users.enabled, approval_status và quyền on-chain. Không tự thêm SUSPENDED/BLOCKED/WAITING_BLOCKCHAIN vào schema.
6. Nguồn transaction thật: bảng hiện có hoặc truy vấn event/RPC; quyền đọc, thứ tự ổn định, phân trang, dữ liệu được phép hiển thị cho Admin.
7. Cơ chế audit và điều phối thay đổi DB/on-chain khi một bên hoàn thành, bên kia thất bại; trạng thái pending/outbox hoặc phương án đối soát cần schema được duyệt.

## Phân biệt trạng thái giao dịch

Các ý nghĩa dưới đây là yêu cầu thiết kế, chưa phải enum production hoặc giá trị CHECK trong database:

| Tình huống | Cách xử lý cần thống nhất |
|---|---|
| Chưa gửi | Không có hash; không báo đã gửi/thành công |
| Đã gửi, chưa xác nhận | Giữ hash thật, tiếp tục theo dõi; không ghi APPROVED cuối cùng |
| Đã xác nhận | Receipt, chain, contract/event và confirmation phải hợp lệ trước khi hoàn tất nghiệp vụ |
| Thất bại | Chỉ kết luận từ bằng chứng đã thống nhất như receipt revert; ghi nguyên nhân phù hợp |
| Không xác định do RPC lỗi | Không tự đổi thành FAILED; phục hồi truy vấn/đối soát theo hash hoặc idempotency key |

Không gọi RPC trong transaction database đang giữ khóa để chờ confirmation. Không coi Spring rollback có thể hoàn tác giao dịch đã được ghi trên chain. Cần bộ điều phối/đối soát riêng trước khi kích hoạt approve/suspend/reactivate.

## Contract đọc giao dịch – DRAFT, chưa có endpoint

Frontend `/transactions` tiếp tục là giao diện chờ. Có thể thống nhất sau một GET Admin đọc giao dịch với tìm kiếm hash, lọc trạng thái, phân trang và response gồm hash, loại nghiệp vụ, trạng thái xác nhận, thời gian, block number, mạng/chain ID. Tên endpoint/JSON/enum chưa được công bố thành API hoạt động trong thay đổi này.

Hash/block number có thể chưa có tùy trạng thái; không điền giá trị mẫu. Explorer link phải dựa trên cấu hình mạng đáng tin cậy và hash hợp lệ, không ghép URL từ input tùy ý. Không tạo bảng, seed transaction, explorer giả hoặc endpoint trả danh sách mẫu.

## Điều kiện bật thật

- Hợp đồng kỹ thuật và schema cần thiết được nhóm phê duyệt.
- Test contract/RPC trên môi trường riêng, bao gồm chậm confirmation, revert, RPC lỗi, reorg và yêu cầu lặp.
- Test DB/audit và idempotency, cơ chế phục hồi khi chain thành công nhưng DB/audit lỗi.
- Sau khi Backend được nghiệm thu mới giao task riêng mở nút hoặc tích hợp transaction data vào Frontend; giai đoạn này không sửa Frontend.
