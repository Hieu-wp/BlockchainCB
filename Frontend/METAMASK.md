# Kết nối MetaMask cho Admin

Ví chỉ được khởi tạo trong giao diện Admin sau khi Spring Security xác nhận phiên.
Không thêm dependency. Header và /profile dùng cùng một hook/store của lần đăng nhập.

Provider discovery ưu tiên EIP-6963, rdns `io.metamask`; fallback provider legacy có
isMetaMask khi không có thông báo EIP-6963 phù hợp. Metadata/isMetaMask do provider
tự khai báo, không phải chứng nhận danh tính hoặc quyền Admin của ví. Không render
icon HTML/SVG do provider cung cấp. Chỉ các phương thức eth_accounts,
eth_requestAccounts, eth_chainId được phép trong service này.

eth_accounts chỉ đọc quyền đã cấp; eth_requestAccounts chỉ chạy khi nhấn nút kết nối.
Đổi tài khoản/mạng cập nhật state; logout/unmount hủy listeners và bỏ qua phản hồi cũ.
Ngắt liên kết trong MediCare chỉ bỏ trạng thái tại ứng dụng, không thu hồi quyền MetaMask.
Không lưu địa chỉ vào database hoặc storage. Tải lại/đăng nhập lại sẽ đọc quyền từ provider
mới, không coi trạng thái ví cũ là bằng chứng xác thực Admin.

## Mạng yêu cầu

Nhóm có thể cấu hình biến Vite công khai `VITE_BLOCKCHAIN_CHAIN_ID` trong môi trường
Frontend rồi khởi động/build lại Vite. Nhận số nguyên dương dạng decimal hoặc hex.
Ví dụ giá trị `11155111` hoặc `0xaa36a7` chỉ minh họa Sepolia, không phải mạng mặc định.
Không cấu hình thì hiển thị “Chưa cấu hình mạng yêu cầu”, không đánh giá sai mạng.
Giá trị không hợp lệ được thông báo trong Profile; không tự chuyển hoặc thêm mạng.

## Phạm vi tiếp theo

Cần mạng/chain ID đã nghiệm thu, ABI, địa chỉ contract theo mạng và quy tắc owner/role
từ nhóm Blockchain. Kết nối tài khoản không xác minh owner/DOCTOR_ROLE. Không bật
approve/suspend/reactivate, ký message hoặc gửi giao dịch trong giai đoạn này.

Tham chiếu: https://eips.ethereum.org/EIPS/eip-6963 và
https://eips.ethereum.org/EIPS/eip-1193.
