# Doctor Blockchain integration — bàn giao 17/11/2026

> Cập nhật bệnh án chung 19/11/2026: [luồng Sepolia ngay sau lưu](SHARED_SEPOLIA_AFTER_SAVE.md).
> Có API riêng cho public.record_versions và migration đề xuất; chưa áp dụng Supabase hoặc gửi giao dịch thật.

> Cập nhật 19/11/2026: [sửa origin challenge và DB timeout](WALLET_DOMAIN_AND_DB_TIMEOUT.md).
> Wallet challenge API V2 nhận thêm origin; Frontend và Backend cần cập nhật cùng phiên bản.

> C?p nh?t Doctor?Patient 18/11/2026: xem [DOCTOR_PATIENT_INTEGRATION.md](DOCTOR_PATIENT_INTEGRATION.md) v? feature flag ngu?n quy?n public, API xin quy?n v? gi?i h?n b?nh ?n chung. C?c k?t qu? test b?n d??i c? th? l? k?t qu? l?ch s? c?a phi?n tr??c.

## 1. Phạm vi và kết luận

Hoàn thành source Java/React và kiểm thử local với H2/RPC giả lập. **Chưa hoàn thành xác minh live Sepolia,
chưa áp dụng migration Supabase, chưa xác nhận an toàn ví DEFAULT_ADMIN_ROLE và chưa tích hợp proof ví Patient thật.**
Blockchain mặc định tắt. Không có transaction signer/private key trong Java; người dùng tự ký giao dịch bằng MetaMask.
Không deploy mới, không cấp role/consent, không commit/push, không thay đổi module Admin/Patient.

Audit source trước sửa cho thấy chỉ có requestAddresses + hasRole; chưa có nonce/signature/verify ở Backend.
Phần kết nối hiện có được tái sử dụng/refactor, bổ sung chữ ký thay vì coi requestAddresses là bằng chứng sở hữu.
Mật khẩu DB literal trong application.properties đã được thay bằng `${DB_PASSWORD}`; không sao chép giá trị ra tài liệu.
Nếu từng chia sẻ hoặc commit mật khẩu/khóa trước đây, chủ tài khoản cần xử lý rotation qua quy trình của nhóm.

## 2. Contract và ABI chuẩn

- Ethereum Sepolia, chain ID **11155111**.
- Contract **MedicalRecordAnchor**, địa chỉ **0xCd49fa3EE662E0bcfADa0FeA9D69f9383D6f7735**.
- ABI: [MedicalRecordAnchor.abi.json](../../frontend/src/blockchain/MedicalRecordAnchor.abi.json).
- Source: [MedicalRecordAnchor.sol](../../../blockchain/contracts/MedicalRecordAnchor.sol).
- Artifact deployment: blockchain/ignition/deployments/chain-11155111/artifacts/MedicalRecordAnchorModule#MedicalRecordAnchor.json.
- ABI frontend bằng ABI deployment artifact và build hiện tại.
- Runtime bytecode profile production khớp artifact deployment; default profile không tối ưu có bytecode khác.
- Java pin Keccak-256 runtime từ artifact tại [anchor-runtime-hash.txt](../src/main/resources/anchor-runtime-hash.txt).
  Mỗi RPC operation xác nhận chain ID và runtime code hash trước khi sử dụng.
  Đây là đối chiếu artifact local, **không phải đã đọc/verify code tại địa chỉ live**.

Hàm ghi thật:

```text
anchorVersion(bytes32 recordKey,address patient,uint64 version,bytes32 contentHash,bytes32 previousHash)
```

Event thật:

```text
VersionAnchored(bytes32 indexed recordKey,uint64 version,bytes32 contentHash,bytes32 previousHash,address indexed doctor)
```

Event không có Patient wallet. Backend kiểm Patient wallet qua calldata, records(recordKey) và intent đã lưu.
Các hàm đọc: DOCTOR_ROLE, hasRole, writePermission, records, versionHashes, verifyVersion.
Contract không enumerable admin members; cần biết/kiểm ví ứng viên và lịch sử RoleGranted/RoleRevoked.

## 3. Cấu trúc code mới

Backend package `com.blockchaincb.demo.blockchain`:

- ChainSettings: chain/address cố định, gate enabled + deployment-reviewed, validation hex/address.
- WalletLinkService: challenge EIP-191, verify/recover signer, nonce một lần và lưu ví.
- WalletBinding/WalletChallenge + repositories: registry proof và challenge persistent.
- PatientWalletResolver/DatabasePatientWalletResolver: ranh giới module Patient, fail-closed khi thiếu proof.
- AnchorService: kiểm principal, approved, consent, author, hash; prepare/submit/status/reconcile.
- RecordAnchor: opaque random key32 byte được lưu một lần mỗi hồ sơ và pin Patient wallet.
- AnchorAttempt: từng intent/attempt, payload server, transaction hash, trạng thái, block và scan cursor.
- AnchorGateway: interface read-only cho mock/test; RpcAnchorGateway triển khai Web3j đọc JSON-RPC.
- BlockchainController: các API Doctor; AnchorReconciler: worker đọc RPC có retry giới hạn.

MedicalRecordService giữ nguyên canonical/SHA-256 và nội dung bất biến. Response được overlay trạng thái/tx từ
AnchorAttempt, nên API record/version/hash hiển thị effective status, không báo NOT_SUBMITTED sau khi đã xác minh.
Các cột legacy trong RecordVersion không bị update.

Frontend:

- medicalRecordContract.js: client MetaMask/viem, role/consent/sequence, signMessage, simulate rồi write.
- DoctorWalletPanel.jsx: kết nối và ký challenge; reset khi accountsChanged/chainChanged.
- AnchorVersionButton.jsx: trạng thái server, gửi một lần, poll tối đa24 lần, kiểm tra/phục hồi thủ công.
- anchorApi.js: endpoint wrappers và thông báo lỗi; App.jsx tích hợp vào Profile/RecordDetail, giữ MedLedger.

## 4. Luồng liên kết ví

1. JWT xác định User/Doctor; Doctor enabled và có approval PENDING hoặc APPROVED được liên kết ví.
   REJECTED/BLOCKED, sai role, trạng thái không hợp lệ hoặc disabled bị từ chối. Chứng minh sở hữu ví
   không yêu cầu DOCTOR_ROLE và không thay thế phê duyệt hành nghề.
2. POST challenge với address và origin. Backend kiểm origin theo allowlist, lock User, kiểm tra ví trùng, tạo nonce ngẫu nhiên32 byte và TTL5 phút.
3. Message chứa domain/URI của origin đã được cho phép, chain ID, User/Doctor identity, mục đích LINK_DOCTOR_WALLET_ONLY,
   địa chỉ, nonce, challenge ID và expiry. Không lấy domain từ header client tùy ý.
4. React kiểm origin hiện tại thuộc walletOrigins và khớp Domain/URI trong challenge, chain11155111 và MetaMask account, ký đúng message bằng signMessage.
5. POST signature/challenge ID. Backend lock User và challenge thuộc User đó, kiểm expiry/consumed, recover EIP-191.
6. Signer phải bằng address trong challenge. Registry có UNIQUE address; kiểm public.users để chặn ví legacy thuộc người khác.
7. Consume nonce, lưu proof registry và cập nhật users.wallet_address của chính Doctor trong cùng transaction.

Chữ ký proof không phải giao dịch. Không lưu private key. Signature DTO có toString redacted, audit không ghi body.
Hiện hỗ trợ EOA personal_sign, chưa EIP-1271 contract-wallet. Đổi ví đã xác minh bị409, cần quy trình recovery được nhóm duyệt.
Challenge cũ của cùng ví không dùng lại được; frontend bỏ kết quả ký nếu account/chain thay đổi giữa chừng.

## 5. Ranh giới Patient và Admin

### Admin

Admin team tự tích hợp `grantRole(DOCTOR_ROLE, doctorWallet)` / `revokeRole(...)` theo quy trình duyệt thật.
Doctor không có UI/API tự cấp role. APPROVED trong DB và DOCTOR_ROLE on-chain là hai điều kiện riêng.

Trước giao dịch live phải xác định ví nào hiện giữ DEFAULT_ADMIN_ROLE bằng hasRole(zeroHash,address)
và lịch sử RoleGranted/RoleRevoked/Etherscan; không chỉ nhìn địa chỉ deployer cũ.
Một khóa từng có nguy cơ lộ **không được sử dụng để ký giao dịch**.
Nếu không còn admin an toàn để quản trị deployment hiện tại, đây là blocker để nhóm quyết định; không tự deploy thay thế.

### Patient

Patient team tự xác minh ví và gọi `grantWrite(doctorWallet)` / `revokeWrite(doctorWallet)` bằng ví Patient.
Doctor không ký thay Patient, không dùng ví deployer làm fallback.
Default PatientWalletResolver yêu cầu:

- patient_links trỏ User role PATIENT;
- users.wallet_address khác null;
- wallet_bindings có cùng User/address, chain11155111, verified_at và proof_method PERSONAL_SIGN.

Không có API Doctor nào cho phép tạo proof cho User PATIENT. Patient team cần adapter proof được review hoặc
cùng dùng registry có kiểm soát, sau khi tự verify chữ ký/nonce đúng mục đích Patient.
Chỉ điền users.wallet_address không đủ. Các hàng proof Patient trong test là fixture H2, không phải production đã tích hợp.
Registry/unique address cần được hai module phối hợp. Rotation ví Patient đã pin cho một record cần review; không tự đổi.
Backend giữ chính sách consent DB hiện tại; on-chain writePermission là điều kiện bổ sung, không thay thế DB grant.

## 6. Chuẩn bị và gửi giao dịch

```mermaid
sequenceDiagram
    participant R as React
    participant J as Java
    participant DB as PostgreSQL hoặc H2
    participant M as MetaMask
    participant E as Sepolia RPC
    R->>J: POST anchor-payload, JWT
    J->>DB: Kiểm quyền, hash, proof; lock root; lưu opaque key/intent
    J->>E: Read code/chain/role/consent/latest/hash
    J-->>R: Payload từ server
    R->>E: Read role/consent và simulateContract qua provider
    R->>M: writeContract, người dùng xác nhận gas
    M-->>R: Transaction hash
    R->>J: POST anchor-transactions
    J->>E: Verify tx, receipt, input, event, canonical block, storage
    J->>DB: PENDING, CONFIRMED hoặc FAILED
    J-->>R: Trạng thái đã xác minh bởi Backend
```

Payload chỉ được chuẩn bị bởi tác giả version, đang APPROVED, có READ và CREATE(v1)/UPDATE(v>1).
recordKey dùng SecureRandom32 byte được lưu riêng, không hash trực tiếp patientId/recordId, không tái tạo tùy phiên.
Patient wallet được pin cho record. contentHash lấy từ v.contentHash và tái tạo canonical để đối chiếu.
V1 dùng zero previous hash. V>1 yêu cầu latestVersion=n-1 và DB previousHash khớp cả latestHash lẫn versionHashes(n-1).
Không tin hash tùy ý Frontend gửi. Payload prepare là POST vì nó tạo reservation/intent, không dùng GET có side effect.

Java và React đều kiểm role/consent. React simulateContract trước writeContract nhưng simulation không đảm bảo
giao dịch không thể revert nếu consent/state đổi sau đó. MetaMask là bên duy nhất ký/broadcast.
Không coi tx hash là thành công. Các state hợp lệ: NOT_SUBMITTED (chưa intent), PREPARED, PENDING, CONFIRMED, FAILED.
PREPARED có reservation120s để giảm duplicate; pending/confirmed không cho tạo attempt mới cùng phiên bản.
Các attempt cũ được giữ lại, không xóa hash/lịch sử.

## 7. Backend xác minh gì?

RpcAnchorGateway chỉ có RPC đọc. Không có signer/sendRawTransaction/private key.

- Kiểm chain11155111 và Keccak runtime code bằng artifact đã pin.
- Hash transaction phải hex32 đúng. Transaction.to, from, value=0, input phải khớp payload ABI encode đầy đủ.
- Calldata encode Java đã có test đối chiếu vector encodeFunctionData viem cùng ABI.
- Receipt txHash/to/from đúng; receipt và transaction không phải kết quả success do React khai báo.
- Receipt block phải khớp canonical block lấy theo block number. Mặc định chờ3 confirmations.
- Status revert chỉ được ghi FAILED sau kiểm canonical block/confirmations.
- Event VersionAnchored đúng emitter, indexed key/doctor và data version/contentHash/previousHash.
- versionHashes/verifyVersion và records.patient/latestVersion khớp; version trước được đối chiếu khi n>1.
- Chỉ VERIFIED mới CONFIRMED; RPC error/timeout hoặc receipt thiếu giữ PENDING.
- Mismatch trả422 khi submit, không bind một transaction sai vào version.
- Re-submit cùng hash vào cùng attempt idempotent; hash gắn record/version khác bị409; UNIQUE transaction_hash ở DB.

Nội dung canonical hiện tại cũng được kiểm trước prepare/submit/status; không tự cập nhật hash để che sửa trái phép.
Hash hiện tại còn được đối chiếu với hash đã đóng băng trong intent. Nếu cả snapshot lẫn digest trong DB bị sửa
để tự khớp nhau sau khi chuẩn bị, kiểm tra hash vẫn thất bại; DTO không tiếp tục hiển thị CONFIRMED cho nội dung đó.
HTTP audit ghi metadata đường dẫn/method/outcome; worker ghi ANCHOR_RECONCILE metadata, không ghi nội dung y tế hoặc RPC URL.

## 8. Phục hồi khi đóng trình duyệt

Prepare commit intent trước MetaMask, lưu block bắt đầu scan. Nếu tx được broadcast rồi React đóng trước submit,
GET anchor-status/worker tìm VersionAnchored theo recordKey và payload, lấy tx hash rồi chạy cùng bộ verify.
Mỗi lần scan tối đa2000 block, cursor lưu ở DB, giữ cửa sổ confirmations để re-scan khi reorg ngắn.
Worker mỗi60s, batch10, tối đa100 poll/attempt, chỉ chạy khi enabled+reviewed+RPC cấu hình.
Hết budget hoặc quá24 poll UI thì người dùng có thể kiểm tra trạng thái thủ công; không tự gửi thêm transaction.
Worker dùng danh tính intent và tiếp tục yêu cầu account/READ hiện hành. Nếu Doctor bị khóa/mất quyền, tự reconcile có thể bị chặn;
cần quy trình vận hành riêng của nhóm, không bỏ quyền để vượt qua blocker.

Root lock/unique key/attempt + contract sequential version ngăn double anchor hợp lệ. Không hứa exactly-once broadcast:
người dùng có thể ký giao dịch muộn hoặc thay nonce ngoài app; giao dịch thứ hai cùng version phải revert ở contract.
Không lưu bệnh án hoặc token/khóa vào localStorage; recovery dựa vào intent DB và event, không dựa state browser.

## 9. Endpoint Doctor

Tất cả dùng JWT và Security hiện có; không nới permitAll.

| Method | URL | Input / output |
|---|---|---|
| GET | /api/doctor/blockchain/config | enabled, chainId, contractAddress, domain, walletLinkPolicy (không có RPC URL) |
| GET | /api/doctor/wallet | Ví của Doctor đăng nhập: walletAddress,verified,chainId,verifiedAt; trường users.wallet_address riêng lẻ không đủ để verified=true |
| POST | /api/doctor/wallet/challenges | {address,origin} → challengeId,message,expiresAt,origin |
| POST | /api/doctor/wallet/verify | {challengeId,signature} → walletAddress,chainId,verifiedAt |
| POST | /api/doctor/records/{id}/versions/{number}/anchor-payload | Không body → recordKey,patientWallet,versionNumber,contentHash,previousHash,contractAddress,chainId,doctorWallet,attemptId |
| POST | /api/doctor/records/{id}/versions/{number}/anchor-transactions | {transactionHash} → status,code,txHash/block metadata |
| GET | /api/doctor/records/{id}/versions/{number}/anchor-status | Status thật, có thể reconcile RPC đọc |

HTTP200 của submit có thể mang PENDING/FAILED, không có nghĩa CONFIRMED. 400 invalid input/signature,
401 auth,403 permission,409 proof/order/reservation/conflict,422 transaction mismatch,503 RPC/deployment disabled.
Status DTO gọi trường transactionHash; blockNumber/blockHash nullable trước xác nhận.

## 10. Migration cần nhóm phê duyệt

- [PROPOSED_blockchain_v2.sql](sql/PROPOSED_blockchain_v2.sql): bốn bảng mới wallet_bindings, wallet_challenges,
  record_anchors, anchor_attempts trong doctor_backend, FK tới các ID hiện có, không ALTER bảng public.
- [READ_ONLY_blockchain_v2_preflight.sql](sql/READ_ONLY_blockchain_v2_preflight.sql): kiểm cột/constraint/tồn tại/duplicate count.
- Không đổi CHECK legacy blockchain_status=NOT_SUBMITTED, không bỏ @Immutable và không rewrite version/hash.
  Trạng thái effective là sidecar attempt; DTO record/version/hash overlay từ sidecar.
- local-blockchain-schema.sql chỉ khởi tạo H2 profile local/test. Default sql.init.mode=never, ddl-auto=validate giữ nguyên.

**Cần duyệt trước khi chạy app bản mới với Supabase:** Entity validation yêu cầu các bảng mới ngay khi startup,
kể cả blockchain.enabled=false. Không tự chạy production để “thử”, không bật ddl-auto update để vượt lỗi.

Trước apply: DB owner xác minh schema thật, quyền DB, uniqueness wallet, backup và thử trên database cách ly.
SQL là additive, không idempotent để tránh che schema đã khác; nếu bảng đã có thì dừng đối chiếu.
DB owner có thể áp dụng trong transaction sau review. Trước COMMIT còn có thể ROLLBACK transaction.
Sau COMMIT rollback an toàn là tắt tính năng, quay lại app cũ tương thích và **giữ nguyên bảng/dữ liệu mới**.
Không cung cấp DROP TABLE tự động; proof/receipt có thể là dữ liệu cần bảo toàn. Mọi cleanup phải backup và review riêng.

## 11. Biến môi trường

| Tên | Mặc định / ý nghĩa |
|---|---|
| DB_PASSWORD | Bí mật DB, không lưu source |
| JWT_SECRET | Base64 HMAC key riêng, không đưa vào React |
| DOCTOR_BLOCKCHAIN_ENABLED | false |
| DOCTOR_DEPLOYMENT_REVIEWED | false; chỉ true sau review admin key/contract/privacy thật |
| SEPOLIA_RPC_URL | Rỗng; URL RPC server có timeout, không log API key |
| ANCHOR_CONFIRMATIONS | 3, phải >=1 |
| DOCTOR_WALLET_DOMAIN | Domain/origin chính; mặc định localhost:5173, các alias loopback cùng port được hỗ trợ |
| DOCTOR_WALLET_ALLOWED_ORIGINS | Allowlist origin chính xác, phân cách bằng dấu phẩy; khi cấu hình sẽ thay danh sách mặc định |
| DOCTOR_API_TARGET | Vite proxy target, mặc định loopback8080; dùng8081 cho fixture demo |

Không có private-key/deployer-key env cần cho Doctor Backend. Không nhét SEPOLIA_RPC_URL có API key vào VITE_*.
Tắt blockchain bằng gate vẫn cho phép xác minh chữ ký off-chain nếu schema đã có và Doctor PENDING/APPROVED hợp lệ.

## 12. Chạy local và kiểm thử

Không cần RPC/khóa thật cho test:

```powershell
cd C:\Users\ADMIN\BlockchainCB\Doctor\demo
.\mvnw.cmd -B -ntp test
cd ..\frontend
npm.cmd test -- --run
npm.cmd run build
cd ..\..\blockchain
npx.cmd hardhat build
npx.cmd hardhat test nodejs test/MedicalRecordAnchor.test.ts
npx.cmd hardhat build --build-profile production
npx.cmd tsc --noEmit
```

Unit AnchorService/WalletLinkService dùng mock repositories hoặc keys ngẫu nhiên chỉ trong memory của test.
RpcAnchorGatewayTest dùng HTTP JSON-RPC server giả lập loopback; kiểm bytecode/chain/calldata/event/storage,
pending, revert, wrong contract/sender/hash, reorg, socket timeout và cross-ABI vector.
DemoApplicationTests dùng H2 DataSource cố định, MockitoBean AnchorGateway; có HTTP signature/replay,
pending doctor, prepare/submit/idempotency/status overlay và grant bị thu hồi.
React mock provider/viem/Backend để test wrong chain/account, consent/role, reject, simulate error,
đổi account, pending/confirmed và double-click. Không có test nào gửi Sepolia thật.

Demo UI local: theo DOCTOR_FRONTEND_HANDOFF.md khởi động FrontendDemoApplication test launcher + Vite.
Blockchain demo mặc định disabled; launcher không seed proof Patient thật hoặc tự cấp role on-chain.
Muốn demo end-to-end mà không ký thật, dùng các test mock ở trên. Chưa có mockchain runtime bật sẵn trong UI.
Không gọi luồng production với proof Patient fixture.

## 13. Checklist kiểm thử Sepolia thủ công — CHƯA THỰC HIỆN

1. Nhóm xử lý sự cố khóa bị lộ trước; xác định admin hiện hành, runtime code và provenance của deployment.
2. Duyệt migration, proof registry Patient, consent DB và privacy; không tạo/cấp quyền giả để vượt blocker.
3. Cấu hình RPC server qua môi trường, cả hai gate và wallet domain đúng origin local, HTTPS khi triển khai thật.
4. Doctor APPROVED, ví EOA an toàn, proof hợp lệ, DOCTOR_ROLE do Admin an toàn cấp.
5. Patient tự ký chứng minh ví và grantWrite bằng ví của họ; còn active DB READ+CREATE/UPDATE tương ứng.
6. Bắt đầu v1, so SHA-256 off-chain đúng; chuẩn bị payload không có patientId/tên/nội dung khám.
7. Người dùng tự bấm và xác nhận MetaMask với Sepolia ETH đủ gas; không giao private key cho agent/server.
8. Quan sát PENDING khi chỉ có hash, chờ Backend xác minh/confirmations. Thử đóng React và kiểm recovery.
9. Trên https://sepolia.etherscan.io/tx/<transactionHash> kiểm status, from, to, input/event/version/hash;
   URL chỉ là mẫu, không phải giao dịch đã tạo. So lại API status và verifyVersion trên contract.
10. Kiểm tra lần lượt v2, stale version, sai ví/mạng, revokeWrite và off-chain revoke; không bypass Backend.

## 14. Giới hạn và rủi ro cần giải quyết

- Chưa kiểm live DEFAULT_ADMIN_ROLE hoặc Ethereum receipts thật. Gate deployment-reviewed không tự chứng minh admin an toàn.
- Contract nhận Patient wallet công khai và có thể liên kết với Doctor/recordKey/version/timestamp.
  Không có tên/ID y tế trực tiếp nhưng pseudonym không đồng nghĩa ẩn danh; nhóm cần chấp thuận privacy trước thử dữ liệu thật.
- Nội dung/bytes tệp/password/JWT không on-chain. Canonical SHA-256 không mã hóa dữ liệu y tế và không chứng minh chẩn đoán đúng.
- Receipt cần confirmations, không phải finality tuyệt đối; deep reorg cần kiểm tra lại. Status GET re-verify khi feature enabled.
- Các RPC đọc state mới nhất có thể gặp race giữa block: ưu tiên không xác nhận khi mismatch và kiểm tra lại;
  chưa có multiread snapshot block pin thống nhất cho mọi eth_call.
- Worker retry có giới hạn, không outbox broadcast vì Java không ký; pending bị drop/replacement khác hash
  có thể cần manual reconciliation, không tự ký/gửi lại.
- Audit outcome worker có thể lỗi sau transaction status commit, giống khoảng trống audit/commit đã nêu ở backend handoff;
  chưa outbox audit. HTTP audit vẫn fail closed về body.
- EOA personal_sign thôi, chưa contract wallets, chưa đổi/gỡ ví, rate limit nonce và retention challenge cần hoàn thiện khi vận hành.
- Transaction/record metadata sidecar tăng query trên Page DTO; chưa tối ưu batch cho lượng lớn.
- UI build có cảnh báo chunk >500kB do viem; build vẫn thành công, có thể tách chunk sau.
- Chưa QA trình duyệt/MetaMask thật trong phiên này; test UI/provider đều giả lập.

## 15. Nguồn kỹ thuật đối chiếu

- https://docs.web3j.io/4.12.3/advanced/message_signing/
- https://viem.sh/docs/contract/simulateContract
- https://viem.sh/docs/contract/writeContract
- Skill Hardhat/viem trong blockchain/.agents/skills được áp dụng cho build/test local.

## 16. Kết quả phiên triển khai ban đầu 17/11/2026

| Kiểm tra | Kết quả thực tế |
|---|---|
| Maven verify (compile, test, đóng gói) | PASS; 82 tests, 0 failures, 0 errors, 0 skipped |
| React Vitest | PASS; 26 tests trong 4 test files |
| React production build | PASS; cảnh báo chunk JS 636,25 kB, chưa tối ưu chia chunk |
| Hardhat build mặc định | PASS |
| Hardhat test nodejs test/MedicalRecordAnchor.test.ts | PASS; 12 tests |
| Hardhat build --build-profile production | PASS; runtime khớp artifact deployment local |
| TypeScript tsc --noEmit trong blockchain | PASS |
| git diff --check | PASS; chỉ có thông báo chuẩn hóa LF/CRLF của Git |
| Trình duyệt + MetaMask + RPC Sepolia thật | CHƯA KIỂM THỬ |
| Migration trên Supabase chung | CHƯA ÁP DỤNG |

Java gồm 39 test hiện có và 43 test bổ sung: AnchorService 17, RPC gateway 15, WalletLinkService 8,
HTTP/H2 integration 3. React có 8 test hiện có và 18 test bổ sung: contract adapter 13, anchor button 5.
Không có test tự động nào dùng Supabase hoặc Sepolia thật. Báo cáo Java chi tiết nằm trong target/surefire-reports.

Các nhóm file tạo/sửa để review:

- Java mới: toàn bộ package `src/main/java/com/blockchaincb/demo/blockchain/`.
- Java hiện có: `MedicalRecordService`, `MedicalRecordRepository`, `UserRepository`; không sửa Controller/API bệnh án cũ.
- Maven/cấu hình: `pom.xml`, `application.properties`, `application-local.properties`, resource runtime hash và local SQL mới.
- Test Java: ba test class trong package blockchain, `DemoApplicationTests`, `FrontendDemoApplication`,
  `application-test.properties`, hai resource runtime/calldata vector.
- React: package/lock (giữ dependency viem đã có), `App.jsx`, `api.js`, `styles.css`, các file trong `src/blockchain/`.
  ABI được giữ nguyên; dùng lại wallet panel/adapter đang có và bổ sung test/API/button.
- Tài liệu/SQL: tài liệu này, `sql/PROPOSED_blockchain_v2.sql`, `sql/READ_ONLY_blockchain_v2_preflight.sql`.

Không sửa source Solidity, module Ignition, Hardhat tests hoặc source Admin/Patient. Không stage, commit hoặc push.

## 17. Sửa luồng liên kết ví trước phê duyệt — 18/11/2026

Nguyên nhân được tái hiện bằng test HTTP/H2 trước sửa: POST wallet/challenges của Doctor PENDING trả403,
body `message=Bac si chua duoc phe duyet`. Cả challenge() và verify() trong WalletLinkService gọi
DoctorIdentityService.approved(). JWT/SecurityConfig đã cho PENDING xác thực; điểm chặn là policy trong Service.

Đã tách `DoctorPolicy.requireWalletLink()` và `DoctorIdentityService.walletLinkAllowed()` cho PENDING/APPROVED.
`approved()` vẫn giữ kiểm tra APPROVED cho bệnh án, payload, submit và mọi kiểm tra quyền hiện có.
GET `/api/doctor/wallet` chỉ đọc proof của principal hiện tại; không nhận userId/doctorId từ client.
UI gọi endpoint này để phân biệt địa chỉ đã lưu với proof, không suy luận từ địa chỉ MetaMask.
Sau chữ ký thành công, Profile gọi lại `/api/doctor/me`; không tự set approval hoặc DOCTOR_ROLE.
Lỗi đọc role qua RPC không chặn ký challenge off-chain; luồng anchor vẫn kiểm role/consent nghiêm ngặt.

| File sửa/thêm trong bản sửa | Hàm / tác động |
|---|---|
| security/DoctorPolicy.java | requireWalletLink: chỉ PENDING/APPROVED, giữ requireApproved |
| security/DoctorIdentityService.java | account kiểm principal; walletLinkAllowed tách khỏi approved |
| blockchain/WalletLinkService.java | challenge/verify dùng policy mới; status đọc proof hiện hành |
| blockchain/BlockchainController.java | GET wallet đọc proof của Doctor đang đăng nhập |
| frontend/src/App.jsx | Profile truyền approvalStatus; onLinked gọi refresh từ Backend |
| frontend/src/blockchain/DoctorWalletPanel.jsx | Ba trạng thái độc lập, thông báo chờ Admin, chặn sai ví/trạng thái |
| frontend/src/blockchain/anchorApi.js | wallet GET và thông báo lỗi wallet |
| frontend/src/blockchain/medicalRecordContract.js | connectDoctorWallet trả role unknown khi RPC đọc lỗi, vẫn cho chứng minh sở hữu |
| DemoApplicationTests.java, WalletLinkServiceTest.java, DoctorWalletPolicyTest.java (mới) | HTTP/H2 và policy/proof regression tests |
| App.test.jsx, medicalRecordContract.test.js, DoctorWalletPanel.test.jsx (mới) | React integration/component/adapter tests |
| docs/DOCTOR_BLOCKCHAIN_INTEGRATION.md | Bổ sung báo cáo và hướng dẫn này; mục16 giữ kết quả lịch sử |

Workflow: đăng ký PENDING → đăng nhập → Hồ sơ cá nhân → kết nối và ký → Backend xác minh/lưu ví →
Admin xét duyệt/cấp role theo quy trình nhóm → Doctor APPROVED mới dùng nghiệp vụ; grantWrite và DB consent vẫn bắt buộc để anchor.
Liên kết ví không gửi bất kỳ giao dịch nào.

### Điểm tích hợp Admin còn thiếu trong workspace

Workspace được kiểm tra chỉ có Doctor và blockchain; không có source Backend/Frontend Admin hoặc Patient.
Script `blockchain/scripts/grantAdminRole.ts` có sẵn trước bản sửa không phải luồng xét duyệt Doctor/đọc proof từ Backend;
không sửa hay chạy script này. Không có endpoint `/api/admin/...` hiện hành để nối vào an toàn.

DTO proof hiện có qua GET `/api/doctor/wallet` phục vụ **chính Doctor đăng nhập**, không dùng JWT Doctor để gọi thay Admin.
Để làm danh sách xét duyệt, module Admin cần endpoint được bảo vệ bằng danh tính/role ADMIN của họ,
trả `doctorId,userId,walletAddress,walletVerified,verifiedAt,chainId,approvalStatus`.
Nguồn dữ liệu đã sẵn sàng trong `users` + `doctors` + `doctor_backend.wallet_bindings`;
Admin phải lấy địa chỉ từ proof khớp account, không lấy địa chỉ tự nhập làm nguồn cấp DOCTOR_ROLE.

Truy vấn SELECT tham khảo cho Backend Admin (bind `:doctorId`; chưa chạy trên Supabase):

```sql
SELECT d.id AS doctor_id, u.id AS user_id,
       b.address AS wallet_address, b.chain_id, b.verified_at, d.approval_status
FROM public.doctors d
JOIN public.users u ON u.id = d.user_id
JOIN doctor_backend.wallet_bindings b ON b.user_id = u.id
WHERE d.id = :doctorId
  AND u.role = 'DOCTOR' AND u.enabled = TRUE
  AND d.status <> 'BLOCKED' AND d.approval_status IN ('PENDING','APPROVED')
  AND lower(u.wallet_address) = b.address
  AND b.chain_id = 11155111 AND b.proof_method = 'PERSONAL_SIGN'
  AND b.verified_at IS NOT NULL;
```

Endpoint Admin và giao dịch grantRole của Admin **chưa được tích hợp/kiểm thử** vì module không có trong checkout.
Không thêm API Admin vào Doctor với policy JWT Doctor hoặc nới permitAll để vượt ranh giới module.

### Thử trên website

1. Khởi động lại Java bản đã sửa và chạy Vite như hướng dẫn frontend handoff. Dùng đúng Backend/proxy,
   port5173; origin trang phải thuộc walletOrigins cấu hình Backend. V2 hỗ trợ mặc định localhost/127.0.0.1 cùng port local.
2. Đăng nhập tài khoản Doctor PENDING hiện có. Vào **Hồ sơ cá nhân**; không đăng ký lại tài khoản trùng.
3. Chọn Sepolia trong MetaMask, chọn ví EOA của chính Doctor, bấm **Kết nối MetaMask**.
   Chưa có DOCTOR_ROLE vẫn được bấm ký; đây chưa phải bằng chứng sở hữu.
4. Bấm **Ký challenge và liên kết ví**, đọc đúng domain/chain/mục đích rồi ký. Đây là personal_sign,
   không phải giao dịch trả gas, không cần cung cấp private key/seed phrase cho website.
5. DevTools Network kỳ vọng challenge POST200 → verify POST200 → me GET200 → wallet GET200
   (wallet GET có thể lặp do refresh). wallet trả verified=true, chainId11155111, verifiedAt có giá trị;
   me vẫn approvalStatus=PENDING. Ví lưu xuất hiện và khi role=false sẽ báo đang chờ Admin cấp quyền Blockchain.
6. Đăng xuất/đăng nhập lại: ví và proof vẫn đọc từ Backend. Menu nghiệp vụ chưa mở cho PENDING.
7. Admin thực hiện phần xét duyệt/cấp role qua module của nhóm bằng ví quản trị an toàn. Doctor bấm kiểm tra trạng thái,
   kết nối lại MetaMask để đọc role mới. Không tự đổi DB hoặc gọi grantRole từ Doctor.

Bản sửa này **không cần migration SQL mới**. Các bảng wallet_bindings/wallet_challenges của migration v2 trước
phải tồn tại sẵn. Nếu thiếu, DB owner vẫn cần review migration v2 như mục10; không tự chạy production.
Không cần bật hai gate Blockchain hoặc RPC Backend chỉ để liên kết ví.

### Kiểm thử bản sửa

- Trước sửa: test H2 tái hiện403 và đúng message tại WalletLinkService (1/1 PASS với kỳ vọng lỗi cũ).
- Sau sửa: Maven verify **97/97 PASS**, 0 failures/errors/skipped, compile và đóng gói thành công.
- React **40/40 PASS** trong5 test files; build PASS, còn cảnh báo bundle638,03 kB.
- Java thêm test policy, proof metadata, HTTP PENDING, chặn BLOCKED/disabled/REJECTED/sai role,
  kiểm quyền lại lúc verify, JWT, challenge chủ khác, chữ ký sai/hết hạn/replay, ví trùng và anchor APPROVED hồi quy.
- React kiểm PENDING không role vẫn ký, tải lại me, dữ liệu proof độc lập, không tự cấp role,
  sai mạng/ví, đổi account, lỗi ký/challenge/tài khoản và không gọi API bệnh án cho PENDING.
- Không chạy lại Hardhat vì không sửa Solidity/ABI/Hardhat. Không gọi Supabase hoặc giao dịch Sepolia thật.
- Các thay đổi AuthController/AuthService/application.properties, phần chú thích/định dạng App.jsx/App.test.jsx
  và script grantAdminRole có sẵn trước phiên sửa được giữ lại; không ghi đè hoặc đưa vào commit.

## 18. Chẩn đoán lỗi 403 còn xuất hiện — 18/11/2026

### Những gì đã kiểm chứng và chưa thể kết luận

- Source hiện tại: `WalletLinkService.challenge()` và `verify()` đều gọi `walletLinkAllowed()`, không gọi approved().
  Policy cho PENDING/APPROVED liên kết ví, không đổi điều kiện APPROVED của nghiệp vụ bệnh án/anchor.
- `walletError()` trước bản bổ sung này quy mọi403 chưa có mã nhận diện sang thông báo tài khoản bị khóa/vô hiệu hóa.
  Vì vậy thông báo trên UI **không phải bằng chứng** rằng users.enabled=false hoặc Doctor BLOCKED.
- Backend Doctor cổng8080 (PID31264 tại lúc kiểm tra) khởi động lúc20:38:36, trước lần compile sửa policy lúc20:47–20:51.
  Main class của process là DemoApplication. Đây là dấu hiệu runtime có thể chưa nạp bản sửa;
  chưa xác minh hot reload hay classpath chính xác, nên không kết luận chắc chắn đó là nguyên nhân403 của tài khoản thật.
- GET không JWT tới `/api/doctor/wallet` trực tiếp8080 và qua proxy5173 đều trả401, body `Can xac thuc`.
  Kết nối Backend/proxy hoạt động lúc kiểm tra; không tái hiện ECONNREFUSED.
- Người dùng cung cấp log Vite cũ ECONNREFUSED và Unexpected token trong App.jsx, chưa có response403 của phiên đăng nhập.
  App.jsx hiện build thành công. Chưa xác định request thật bị từ chối tại config, challenge hay verify;
  không đọc JWT/chữ ký từ trình duyệt và không thử ghi vào Supabase để suy đoán.

### Bổ sung để lần thử tiếp theo xác định đúng lỗi

- ApiException có mã lỗi độc lập; GlobalExceptionHandler giữ status/message và thêm code khi có.
- DoctorPolicy/DoctorIdentityService trả mã cụ thể; điều kiện cho phép/từ chối giữ nguyên.
- JwtAuthenticationFilter giữ nguyên kiểm JWT, truyền mã nguyên nhân thay vì xóa chi tiết thành một lỗi tài khoản chung.
- SecurityErrors serialize JSON đúng escaping; SecurityConfig vẫn bảo vệ `/api/doctor/**` bằng role DOCTOR,
  chỉ bổ sung code cho401/403, không thêm permitAll.
- Mốc trước dùng `walletLinkPolicy=PENDING_APPROVED_V1`; bản hiện tại dùng `PENDING_APPROVED_V2_ORIGIN`, kèm walletOrigins.
  Frontend thiếu marker sẽ dừng trước ký và yêu cầu khởi động lại đúng Backend.
  Marker chỉ là thông tin phiên bản, không phải bằng chứng cấp quyền; server vẫn kiểm danh tính/trạng thái ở từng API.
- api.js đọc code riêng, hỗ trợ một số message legacy được liệt kê sẵn, giữ path/method/status cho UI;
  không đưa body lỗi tùy ý/HTML/SQL ra thông báo.
- anchorApi.walletError() hiển thị đúng bước, HTTP status và mã lỗi được nhận diện.403 không rõ mã được mô tả trung lập.
- DoctorWalletPanel tự đọc lại account/chain/role bằng getAddresses khi accountsChanged/chainChanged,
  không mở popup xin quyền/ký chỉ vì event; xóa địa chỉ cũ trước khi tải lại và không chấp nhận kết quả đã lỗi thời.
- medicalRecordContract.js kiểm lại account/chain sau ký trước khi gửi verify, kể cả khi event provider đến muộn.
  Backend vẫn là bên xác minh chữ ký, nonce, ownership và lưu wallet binding.

| HTTP / code | Ý nghĩa và cách xử lý |
|---|---|
| WALLET_BACKEND_OUTDATED (client) | Config không có marker mới: dừng Java cũ, chạy lại đúng Doctor/demo, tải lại trang/đăng nhập |
| 401 AUTHENTICATION_REQUIRED / TOKEN_INVALID | Chưa đăng nhập hoặc JWT không còn hợp lệ |
| 403 ACCOUNT_DISABLED | users.enabled=false |
| 403 DOCTOR_BLOCKED | doctors.status hoặc approval_status là BLOCKED |
| 403 ACCOUNT_ROLE_INVALID | User hiện không có role DOCTOR |
| 403 DOCTOR_PROFILE_MISSING / DOCTOR_PROFILE_INVALID / DOCTOR_IDENTITY_MISMATCH | Hồ sơ hoặc quan hệ User/Doctor không hợp lệ |
| 403 DOCTOR_APPROVAL_INVALID | Trạng thái xét duyệt không được hệ thống nhận diện |
| 403 WALLET_LINK_NOT_ALLOWED | Trạng thái không cho liên kết ví, ví dụ REJECTED |
| 403 DOCTOR_APPROVAL_REQUIRED từ wallet (legacy) | Backend vẫn yêu cầu duyệt cho bước wallet; kiểm runtime/khởi động lại đúng bản sửa |
| 403 không có code nhận diện | Chưa xác định nguyên nhân; lấy Network response theo từng bước, không suy ra bị khóa |
| 400 INVALID_SIGNATURE | Chữ ký không đúng ví/challenge |
| 409 CHALLENGE_USED_OR_EXPIRED / WALLET_ALREADY_LINKED | Challenge hết hạn/đã dùng hoặc ví thuộc tài khoản khác |

Thông báo ví sẽ ghi rõ ví dụ `Tạo challenge · HTTP 403 · ACCOUNT_DISABLED: ...` hoặc
`Xác minh chữ ký · HTTP 403: ...`, giúp phân biệt API lỗi mà không log JWT, signature hay payload.

### Khởi động lại và thử đúng bản build

1. Dừng phiên Spring Boot đang chạy trong VS Code bằng nút Stop/terminal của chính phiên đó.
   Không chỉ tải lại website và không dừng hàng loạt process Java.
2. Chạy lại Doctor/demo với cấu hình DB/JWT đang dùng. Maven verify đã biên dịch/đóng gói bản mới;
   nếu chạy JAR, dùng đúng `Doctor/demo/target/demo-0.0.1-SNAPSHOT.jar`.
   Giữ ddl-auto=validate/sql.init.mode=never. Không đổi schema hoặc role để xử lý403.
3. Vite đang chạy nhận HMR; Ctrl+F5 và đăng nhập lại. Nếu Vite đã dừng, chạy `npm.cmd run dev` trong Doctor/frontend.
4. Mở Profile, kết nối Sepolia đúng ví, bấm ký challenge. GET blockchain/config phải có walletLinkPolicy mới.
   Đặt domain chính/allowlist origin đúng website. Không cần bật Blockchain hoặc có DOCTOR_ROLE chỉ để ký.
5. Theo Network: config200 → challenges200 → MetaMask personal_sign → verify200 → me/wallet200.
   Kiểm me vẫn PENDING và wallet trả verified=true. Không chuyển sang nghiệp vụ bệnh án khi còn PENDING.
6. Nếu còn403, cung cấp chỉ method/path/status/code/message của request lỗi, không chia sẻ Authorization,
   JWT, chữ ký hoặc toàn bộ request body. Chưa có kết quả kiểm thử bằng tài khoản thật/MetaMask thật trong phiên này.

Admin tiếp tục lấy ví từ proof registry đã đối chiếu với users.wallet_address (SELECT mục17).
GET `/api/doctor/wallet` và trường `walletAddress,verified,verifiedAt,chainId` chỉ phục vụ Doctor hiện tại;
không chia sẻ JWT Doctor cho Admin. Module Admin cần endpoint ADMIN riêng để đọc DTO proof theo doctorId.
Không có code Admin trong checkout để thực hiện kết nối đó ở phiên này.

### Kiểm thử cuối bản bổ sung

- Maven verify: **98/98 PASS**, compile/test/đóng gói thành công,0 failures/errors/skipped.
  H2 HTTP kiểm mã nguyên nhân ở cả challenge/verify, PENDING ký thành công nhưng vẫn bị chặn bệnh án,
  replay/expiry/signer/ownership, profile và auth/JWT/anchor APPROVED hồi quy.
- React: **50/50 PASS**,5 test files; production build PASS, cảnh báo JS chunk641,03 kB.
  Bổ sung test Backend cũ, mã lỗi mới/legacy và path đúng, không lộ body lỗi máy chủ,
  account/role tự cập nhật, sai mạng sau event, đổi ví sau ký nhưng chưa nhận event.
- Lần chạy test React đầu phát hiện test gọi async act chưa await; đã sửa test và chạy lại toàn bộ thành công.
- `App.jsx` không có lỗi cú pháp trong build hiện tại. Không chạy lại Hardhat do không đổi contract/ABI.
- Không cần migration mới; không áp dụng SQL, không gửi giao dịch, không đổi phê duyệt/role thật hoặc restart process đang dùng Supabase.

File thay đổi thêm so với mục17: ApiException, GlobalExceptionHandler, DoctorPolicy, DoctorIdentityService,
SecurityErrors, JwtAuthenticationFilter, SecurityConfig, BlockchainController; frontend api.js, anchorApi.js,
medicalRecordContract.js, DoctorWalletPanel.jsx và các test liên quan. Giữ nguyên các thay đổi người dùng đang có.
