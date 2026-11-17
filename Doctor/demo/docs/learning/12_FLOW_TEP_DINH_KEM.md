# 12 — Upload và download tệp riêng tư

[← Mục lục](00_INDEX.md)

> Căn cứ: source đang có ngày 17/11/2026. Đây là phân tích tĩnh, không chạy API/test trong lượt viết tài liệu. Kết quả test ở handoff là bằng chứng lịch sử, không phải kiểm chứng Supabase hiện tại. SQL bên dưới chỉ minh họa tương đương logic, không phải log Hibernate.

## A. Chức năng này dùng để làm gì?

Doctor được duyệt tải PDF/PNG/JPEG cho đúng bệnh nhân có quyền rồi gắn ID tệp vào phiên bản. Tệp không có public URL; tải xuống phải qua record/version và active READ. PrivateStorage hiện chỉ có adapter local opt-in, không phải storage production.

## B. Các file tham gia vào flow

Thứ tự là đường đi logic của request; filter chạy trước Controller. Các hàm phụ và chiều trả response có thể quay lại file đã đi qua.

| Thứ tự | File | Class/Method | Nhiệm vụ |
|---|---|---|---|
| 1 | [App.jsx](../../../frontend/src/App.jsx#L85) | `RecordForm.upload; RecordDetail.download` | Chọn file và Blob tải xuống |
| 2 | [api.js](../../../frontend/src/api.js#L34) | `api.upload; api.download; request` | Multipart hoặc Blob, Bearer |
| 3 | [DoctorController.java](../../src/main/java/com/blockchaincb/demo/controller/DoctorController.java#L129) | `upload; download` | MultipartFile và ResponseEntity bytes |
| 4 | [AttachmentService.java](../../src/main/java/com/blockchaincb/demo/service/AttachmentService.java#L39) | `upload; detect; forVersion; descriptors; download` | Quyền, metadata, digest |
| 5 | [DatabasePatientAccessPolicy.java](../../src/main/java/com/blockchaincb/demo/service/DatabasePatientAccessPolicy.java#L22) | `require` | READ và CREATE/UPDATE |
| 6 | [PrivateStorage.java](../../src/main/java/com/blockchaincb/demo/storage/PrivateStorage.java#L3) | `put; read` | Hợp đồng lưu trữ |
| 7 | [LocalPrivateStorage.java](../../src/main/java/com/blockchaincb/demo/storage/LocalPrivateStorage.java#L31) | `put; read; path` | UUID key, thư mục private |
| 8 | [AttachmentRepository.java](../../src/main/java/com/blockchaincb/demo/repository/AttachmentRepository.java#L8) | `saveAndFlush; findById (kế thừa)` | Metadata tệp |
| 9 | [Attachment.java](../../src/main/java/com/blockchaincb/demo/entity/Attachment.java#L8) | `Attachment` | Storage key, patient, uploader và digest |
| 10 | [MedicalRecordService.java](../../src/main/java/com/blockchaincb/demo/service/MedicalRecordService.java#L217) | `download; allowed; version` | Quyền đọc và membership trước bytes |

## C. Sơ đồ luồng hoạt động

```mermaid
sequenceDiagram
    participant UI as RecordForm
    participant C as DoctorController
    participant A as AttachmentService
    participant P as AccessPolicy
    participant FS as PrivateStorage
    participant DB as AttachmentRepository
    UI->>C: POST multipart file
    C->>A: upload(actor,patientId,bytes)
    A->>P: APPROVED, READ, thử CREATE
    alt CREATE bị 403
        A->>P: Thử UPDATE
        Note over A,P: Rủi ro transaction rollback-only, xem lưu ý
    end
    A->>A: Size, magic bytes, SHA-256
    A->>FS: put(bytes)
    FS-->>A: storageKey
    A->>DB: saveAndFlush metadata
    A-->>UI: 201 UploadResponse
    UI->>UI: files thêm attachment ID
    Note over UI,DB: Tạo hồ sơ là POST riêng, chưa nằm trong upload
    UI->>C: GET record/version/attachment
    C->>A: Sau READ và kiểm tra version chứa file
    A->>FS: read(storageKey), kiểm digest
    A-->>UI: 200 bytes; Blob download
```

## D. Giải thích toàn bộ flow từng bước

### Bước 1: Frontend chọn tệp

RecordForm.upload chuyển FileList thành array, xóa value input để chọn lại cùng file được, kiểm tổng file tối đa10. Nó kiểm MIME theo file.type và5MiB ở client cho UX; giá trị do client cung cấp không phải bằng chứng loại tệp. Upload từng file bằng await trong vòng for, đặt uploading để khóa lưu. Thành công chỉ thêm a.id vào files state, chưa tạo bệnh án.

### Bước 2: Multipart đúng cách

api.upload tạo new FormData, append("file",file). request nhận diện FormData nên KHÔNG set Content-Type JSON; trình duyệt tự tạo multipart/form-data boundary đúng. Vẫn có Authorization Bearer. Nếu tự ép header không kèm boundary, server có thể không parse được file. Upload URL là /api/doctor/patients/{patientId}/attachments.

### Bước 3: Controller và giới hạn

DoctorController.upload có consumes MULTIPART_FORM_DATA_VALUE, @RequestPart("file") MultipartFile, @Positive patientId và @ResponseStatus CREATED. Nó lấy file.getBytes rồi gọi AttachmentService.upload. Cấu hình multipart.max-file-size=5MB và max-request-size=6MB là lớp kiểm tra servlet; vượt giới hạn có handler413. Trong service còn check bytes rỗng hoặc >5*1024*1024 trả400.

### Bước 4: Quyền upload và loại file

upload @Transactional gọi identities.approved, READ, thử CREATE; nếu catch ApiException status403 thì thử UPDATE. Sau đó detect đọc chữ ký đầu: %PDF-, tám byte PNG, ba byte JPEG; không tin đuôi filename. Sai loại400. Đây chỉ là magic-byte sniffing, chưa quét malware hay validate toàn bộ cấu trúc tài liệu. Xem lưu ý về transaction của nhánh fallback.

### Bước 5: Adapter và metadata

PrivateStorage là interface; LocalPrivateStorage @Component được inject qua constructor. path kiểm enabled, key regex36 ký tự, normalize đường dẫn và đảm bảo parent đúng root. put tạo UUID server-side, Files.createDirectories rồi Files.write CREATE_NEW; tên client không dùng làm đường dẫn. Service ghi Attachment patientId,uploadedBy,storageKey,mediaType,sizeBytes,sha256,createdAt. Digest tính trực tiếp bytes, không canonical text.

### Bước 6: Gắn tệp vào version

POST tạo/cập nhật sau đó gửi attachmentIds. forVersion chặn ID trùng, tệp không tồn tại, khác patient hoặc uploader không hợp lệ. Với update, inheritedIds từ phiên bản trước cho phép giữ tệp cũ của người khác; không được lấy tùy ý một file của họ chỉ vì đoán ID. descriptors dùng khi canonical để đóng gói digest/media/size vào contentHash phiên bản.

### Bước 7: Download có hai cổng

DoctorController.download gọi MedicalRecordService.download chứ không gọi thẳng Storage. Service.allowed kiểm approved+READ, version(r,number) ràng buộc đúng record; v.attachmentIds phải contains ID, thiếu404. AttachmentService.download lại kiểm patientId metadata, đọc bytes và so SHA-256 với Attachment.sha256; sai409. Cơ chế này không cho endpoint tải tùy ý bằng attachment ID.

### Bước 8: Response file và React

Controller ResponseEntity.ok contentType từ metadata, Content-Disposition attachment; filename=medical-attachment, Cache-Control no-store, nosniff. api.download chọn response.blob() thay json(). RecordDetail tạo object URL ngắn hạn, tạo anchor download, click, rồi revoke URL sau1s. Nó không lưu Blob vào localStorage; nhưng tệp đã tải là bản sao trên thiết bị người dùng. Khi403 nó yêu cầu reload chi tiết để xóa nội dung cũ.

### Request/response minh họa theo contract source

Upload multipart part tên file; response201 minh họa {"id":7,"mediaType":"application/pdf","sizeBytes":128,"sha256":"<64_HEX>"}. Download không trả JSON mà trả bytes. Không có storageKey trong response.

## E. Giải thích code quan trọng

[api.js](../../../frontend/src/api.js#L34) — trích nguyên văn:

```javascript
  upload: (patient, file) => { const body = new FormData(); body.append('file', file); return request(`/doctor/patients/${patient}/attachments`, { method: 'POST', body }); },
```

FormData phải giữ boundary do browser tạo. Hàm wrapper trả UploadResponse để UI chỉ giữ id và thông tin tệp cần thiết, không có public URL.

[LocalPrivateStorage.java](../../src/main/java/com/blockchaincb/demo/storage/LocalPrivateStorage.java#L31) — trích nguyên văn:

```java
  public String put(byte[] content) {
    String key = UUID.randomUUID().toString();
    Path file = path(key);
    try {
      Files.createDirectories(root);
      Files.write(file, content, StandardOpenOption.CREATE_NEW);
      return key;
    } catch (IOException e) {
      throw new ApiException(503, "Khong the luu tep");
    }
```

Server tự sinh UUID và CREATE_NEW tránh dùng đường dẫn do client điều khiển hoặc ghi đè một key đã có. path(key) trả503 khi adapter tắt; không giả lập lưu thành công.

[MedicalRecordService.java](../../src/main/java/com/blockchaincb/demo/service/MedicalRecordService.java#L217) — trích nguyên văn:

```java
  public AttachmentService.Download download(
      DoctorPrincipal actor, Long id, int number, Long attachmentId) {
    var r = allowed(actor, id, PatientAccessPolicy.Action.READ);
    var v = version(r, number);
    if (!v.getAttachmentIds().contains(attachmentId))
      throw new ApiException(404, "Tep khong thuoc phien ban");
    return attachments.download(r.getPatientId(), attachmentId);
  }
```

allowed kiểm quyền và contains ràng buộc tệp với đúng version. Nếu bỏ contains, người có READ với patient có thể thử tệp chưa được gắn vào record đó.

## F. Các trường hợp thành công và thất bại

| Trường hợp | Điều kiện | Kết quả | Code xử lý |
|---|---|---|---|
| Upload hợp lệ | APPROVED, quyền phù hợp, storage bật | 201 metadata | upload |
| Sai loại/size service | Không magic bytes hợp lệ hoặc bytes rỗng | 400 | detect; upload |
| Vượt multipart limit | Request quá lớn | 413 | MaxUploadSizeExceededException handler |
| Storage chưa bật | local-enabled=false | 503 | LocalPrivateStorage.path |
| Tệp ngoài version | ID không trong attachmentIds | 404 | MedicalRecordService.download |
| Bytes bị đổi | Digest bytes khác metadata | 409 | AttachmentService.download |
| Thu hồi READ | Grant không còn active | 403 trước khi đọc bytes | allowed |

### Lưu ý hoặc rủi ro

Rủi ro suy luận trực tiếp từ code, chưa tái hiện ở lượt này: DatabasePatientAccessPolicy.require là @Transactional REQUIRED và ném ApiException extends RuntimeException. Khi upload bắt lỗi CREATE403 rồi UPDATE thành công, proxy transaction của require có thể đã đánh dấu outer transaction rollback-only; commit upload có thể ném UnexpectedRollbackException (handler chung500). Ý định hỗ trợ UPDATE-only không nên được khẳng định đã chạy ổn; cần test riêng nhánh này. Các test upload hiện có dùng grant cả CREATE/UPDATE.

Local storage chưa production: thiếu mã hóa at-rest, antivirus, quota/retention/backup. File mồ côi có thể tồn tại sau lỗi DB hoặc khi người dùng hủy form. Patient vẫn có thể đổi trong lúc upload đang chạy, frontend có thể giữ ID không khớp patient mới; Backend forVersion phải tiếp tục từ chối, không bỏ check để sửa UX.

### Đọc test để kiểm tra lại lập luận

[DemoApplicationTests.java](../../src/test/java/com/blockchaincb/demo/DemoApplicationTests.java#L474); [PrivateStorageTest.java](../../src/test/java/com/blockchaincb/demo/PrivateStorageTest.java#L22)

## G. Bài học rút ra

Interface cho phép thay adapter sau này mà giữ nghiệp vụ. Bytes file và canonical bệnh án có hai digest khác nhau. Transaction DB không bao luôn Files.write, nên rollback metadata không tự xóa file. Status413 ở lớp servlet khác400 ở kiểm size trong service.

### Câu hỏi tự kiểm tra

1. Vì sao không tự set Content-Type khi gửi FormData?
2. Tại sao download phải qua RecordVersion?
3. SHA của file nằm trong contentHash theo cách nào?
4. File name client có tham gia đường dẫn local không?
5. Rollback DB có tự rollback filesystem không?
