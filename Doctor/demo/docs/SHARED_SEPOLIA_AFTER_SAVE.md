# Ghi hash bệnh án chung ngay sau lưu — 19/11/2026

## Trạng thái bàn giao

Source và kiểm thử local đã triển khai. **Hai bảng phụ đã được tạo trên Supabase theo yêu cầu bổ sung của người dùng;
chưa gửi giao dịch Sepolia thật.** Xem [biên bản áp dụng](sql/APPLIED_shared_anchor_v1_2026-11-19.md).
Các đoạn “chưa chạy/cần duyệt migration” phía dưới mô tả thời điểm bàn giao source ban đầu;
không chạy lại CREATE TABLE trên database vừa áp dụng.
Không sửa Admin/Patient hoặc Solidity. Không thay đổi thuật toán hash hay nội dung phiên bản đã lưu.

Kiểm tra RPC công khai chỉ đọc ngày 19/11/2026:

- Chain ID: 11155111.
- Contract: `0xCd49fa3EE662E0bcfADa0FeA9D69f9383D6f7735`.
- Có bytecode; Keccak-256 runtime: `0xd8c55c33133c900ba844b3655c49c7c2e1dacfbcbb2011113f94b1888b284261`.
- Khớp `src/main/resources/anchor-runtime-hash.txt`. Đây không phải nghiệm thu quyền ví quản trị hoặc độ an toàn production.
- Đối chiếu source/ABI: `anchorVersion(bytes32,address,uint64,bytes32,bytes32)`;
  event `VersionAnchored(bytes32 indexed,uint64,bytes32,bytes32,address indexed)`.

## Luồng báo cáo

1. `SharedClinicalForm.submit()` gửi POST `/api/doctor/shared-records` hoặc POST `/shared-records/{id}/versions`.
   `ClinicalController` → `ClinicalService` lưu public medical_records, record_versions, prescription_items,
   vital_signs và record_attachments; snapshot/hash phụ ở doctor_backend.shared_version_proofs. Transaction này
   độc lập với Ethereum. Lưu thành công vẫn có giá trị dù người dùng từ chối MetaMask.
2. React chuyển `/shared-records/{id}?anchor={number}#blockchain-confirmation`. `SharedRecordDetail` mở đúng phiên bản.
   `SharedVersionAnchor` hiển thị hash/bước xác nhận, tải danh tính Doctor từ Backend.
3. `AnchorVersionButton` chỉ tự bắt đầu một lần sau lưu khi Backend bật tích hợp, hash verified và đúng tác giả/ví.
   Xóa dấu hiệu auto-start khỏi URL khi bắt đầu; truy cập chi tiết thông thường không tự gửi.
   React gọi `anchorVersion()` trong medicalRecordContract.js với **sharedAnchorApi**, không dùng ID/API bệnh án legacy.
4. POST anchor-payload → `SharedAnchorController.prepare()` → `SharedAnchorService.prepare()`:
   kiểm tra APPROVED/current consent, tác giả phiên bản, SHA-256 recomputed, bằng chứng ví Doctor/Patient,
   DOCTOR_ROLE, writePermission, latestVersion và hash phiên bản trước thực tế on-chain.
5. recordKey ngẫu nhiên 32 byte lưu cố định theo hồ sơ, không mã hóa ID/email/mã bệnh nhân trong recordKey.
   Phiên bản đầu dùng zero previousHash; phiên bản tiếp theo so khớp versionHashes/latestHash.
6. React kiểm tra Sepolia/account, role/quyền, simulateContract rồi writeContract qua MetaMask.
   Người dùng luôn tự xác nhận. Không có private key ở Backend/Frontend. Không gửi nội dung khám lên chain.
7. Nhận Tx Hash → POST anchor-transactions. Backend tái sử dụng RpcAnchorGateway.verify(): kiểm tra chain/runtime,
   transaction sender/destination/value/calldata, receipt/block canonical/confirmations, event và hash lưu trong contract.
   Có hash nhưng receipt chưa đủ → PENDING; chỉ VERIFIED mới CONFIRMED.
8. Trong cùng transaction, Backend cập nhật public.record_versions.tx_hash/chain_status và intent/audit.
   Không ghi lại canonical/hash khi thay đổi trạng thái Blockchain.

## API mới

JWT Doctor, APPROVED và quyền Patient hiện hành bắt buộc ở cả ba API:

| Method | Endpoint | Ý nghĩa |
|---|---|---|
| POST | `/api/doctor/shared-records/{id}/versions/{number}/anchor-payload` | Chuẩn bị/lưu intent, lấy payload từ DB |
| POST | `/api/doctor/shared-records/{id}/versions/{number}/anchor-transactions` | Body `{ "transactionHash": "0x…64 hex…" }`, xác minh |
| GET | `/api/doctor/shared-records/{id}/versions/{number}/anchor-status` | Xác minh lại/phục hồi bằng event scan |

NONE: chưa gửi. PREPARED là trạng thái nội bộ của intent; public vẫn NONE.
PENDING: đã nhận hash, chưa đủ receipt/confirmations hoặc RPC gián đoạn.
FAILED: giao dịch đã đăng ký bị revert/không khớp khi kiểm tra lại.
CONFIRMED: Backend xác minh thành công. Submit hash không khớp bị từ chối trước khi gắn vào intent.

## Database và migration cần nhóm duyệt

[PROPOSED_shared_anchor_v1.sql](sql/PROPOSED_shared_anchor_v1.sql) **chưa chạy**:

- doctor_backend.shared_record_anchors: public record FK, recordKey ngẫu nhiên, Patient wallet cố định.
- doctor_backend.shared_anchor_intents: public version FK, payload frozen, scan cursor, tx unique, trạng thái, block, thời điểm.
- Bật RLS hai bảng mới; không tạo policy công khai. Role JDBC cần quyền phù hợp theo cơ chế triển khai nhóm.
- Không dùng record_anchors/anchor_attempts legacy: ID public và legacy có thể trùng.
- Không thêm/sửa cột public: tx_hash và chain_status đã có đúng trạng thái NONE/PENDING/CONFIRMED/FAILED.

Trước áp dụng: backup phục hồi được, kiểm tra bảng chưa tồn tại, FK đúng public,
quyền datasource/RLS, constraint UNIQUE(record_id,version_no) thực tế và các duplicate cũ.
Giữ ddl-auto=validate. Cần transaction/maintenance được nhóm duyệt; không chạy SQL context tạo lại public.
Rollback tính năng: tắt cờ Blockchain và giữ nguyên bảng/intent/hash để đối soát; không DROP bảng hoặc xóa bằng chứng.

## Cấu hình và chạy demo

Giữ mặc định OFF. Sau khi nhóm rà soát khóa quản trị và duyệt deployment:

- `DOCTOR_BLOCKCHAIN_ENABLED=true`
- `DOCTOR_DEPLOYMENT_REVIEWED=true`
- `SEPOLIA_RPC_URL`: đặt trong môi trường Backend, không commit/chia sẻ URL có API key.
- `ANCHOR_CONFIRMATIONS`: mặc định 3, không đánh đồng với finalized.

Profile shared-grants nay đọc hai cờ từ biến môi trường thay vì ghi đè false cố định.
Chạy Backend `./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=shared-grants"`, React `npm.cmd run dev`.
Sau migration được duyệt, restart Backend để nạp code và làm mới metadata.

Điều kiện demo: Doctor APPROVED/enabled; quyền off-chain ACTIVE chưa hết hạn; ví Doctor đã xác minh;
Admin đã cấp DOCTOR_ROLE trên đúng contract; Patient đã xác minh ví tương thích và tự grantWrite cho ví Doctor;
MetaMask đúng ví/Sepolia, đủ Sepolia ETH. Tạo hồ sơ → lưu → bước Blockchain → MetaMask → kiểm tra trạng thái/Etherscan.
Nếu chưa đủ điều kiện, vẫn giữ hồ sơ DB, xử lý dependency rồi dùng nút ghi/kiểm tra ở ngay chi tiết.

## Phụ thuộc và hạn chế cần biết

- Patient wallet_address một mình không chứng minh sở hữu. Adapter hiện dùng WalletLinkService.requiredWallet với registry
  doctor_backend.wallet_bindings như cơ chế resolver cũ. Nếu Patient dùng registry khác, cần thống nhất adapter đọc bằng chứng
  thật; **hiện sẽ chặn WALLET_PROOF_MISSING**, không tự tạo proof hoặc dùng ví deployer. Đây là blocker tích hợp Patient.
- Chưa thay luồng chứng minh ví/cấp role bên Admin. Rủi ro private key quản trị cũ phải được nhóm giải quyết trước giao dịch thật.
- Không tự mở lại phiên bản có tx FAILED để gửi hash mới: cần đối soát và thiết kế lịch sử nhiều attempt trước khi cho gửi thay thế.
  Từ chối MetaMask trước broadcast vẫn giữ NONE và có thể dùng lại payload. Không tự đánh FAILED khi user từ chối.
- Sau broadcast, nếu POST hash lỗi, UI giữ hash và nút kiểm tra gửi lại đúng hash, không gửi transaction mới.
  Khi đóng browser, GET status scan event từ scan_from theo batch tối đa 2000 block qua gateway hiện có.
  Chưa có scheduler riêng cho public; cần mở lại trang/bấm kiểm tra để tiếp tục. Không coi PREPARED là bằng chứng đã gửi.
- Intent/canonical được khóa trong transaction khi xác minh; RPC chậm có thể gây timeout/giữ connection.
  Cần đo latency local staging trước bật chung; không tăng pool Supabase hoặc hạ kiểm tra để vượt lỗi.
- Blockchain có thể thay đổi quyền/reorg sau lần đọc; simulate và receipt verification vẫn bắt buộc. Cần kiểm tra lại nếu có nghi vấn.
- Metadata đề xuất chưa chứng minh schema production đã có các bảng mới; source/local tests không có nghĩa đã tích hợp Supabase end-to-end.

## Kiểm thử

Kết quả thực chạy: Java 183 test, 0 failure/error/skipped (full suite 181 trước hai test bổ sung;
chạy lại toàn bộ SharedAnchorServiceTest sau thay đổi: 15 PASS). React 116 PASS; npm run build PASS,
còn cảnh báo bundle lớn hơn 500 kB. Maven compile chạy trong lifecycle test.

Java mock RPC/H2: payload/key ổn định, zero hash, version tiếp theo/previous on-chain hash, mất quyền,
thiếu role/quyền Patient/proof, hash sai, tamper, pending/RPC timeout, revert, giao dịch sai, confirmed/idempotency, phục hồi event.
RPC gateway test hiện có kiểm tra receipt, event/input/contract/sender/content mismatch và network.
React: API shared tách legacy, pending/confirmed, không gửi trùng, giữ hash khi lỗi submit, auto-start sau lưu một lần,
wrong chain/account, simulate/reject/gas qua bộ test wallet hiện có.
Chưa có receipt giao dịch thật được tạo trong phiên làm việc này. Không tuyên bố demo Sepolia end-to-end đã thành công.

SHA-256 là dấu vân tay nội dung snapshot; Tx Hash là định danh giao dịch Ethereum, hai giá trị không thay thế nhau.
