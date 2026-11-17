# 13 — Nhật ký truy cập và kết quả thao tác

[← Mục lục](00_INDEX.md)

> Căn cứ: source đang có ngày 17/11/2026. Đây là phân tích tĩnh, không chạy API/test trong lượt viết tài liệu. Kết quả test ở handoff là bằng chứng lịch sử, không phải kiểm chứng Supabase hiện tại. SQL bên dưới chỉ minh họa tương đương logic, không phải log Hibernate.

## A. Chức năng này dùng để làm gì?

Nhật ký cho request Doctor sau khi xác thực account: ai (User ID), HTTP method, đường dẫn tài nguyên, kết quả và thời gian. Không ghi password/JWT/body bệnh án. Một hành động giao diện có thể tạo nhiều request và vì vậy nhiều cặp audit ATTEMPT/kết quả.

## B. Các file tham gia vào flow

Thứ tự là đường đi logic của request; filter chạy trước Controller. Các hàm phụ và chiều trả response có thể quay lại file đã đi qua.

| Thứ tự | File | Class/Method | Nhiệm vụ |
|---|---|---|---|
| 1 | [api.js](../../../frontend/src/api.js#L5) | `request` | Gửi request tới Doctor API |
| 2 | [SecurityConfig.java](../../src/main/java/com/blockchaincb/demo/config/SecurityConfig.java#L44) | `securityFilterChain` | Đặt AuditFilter sau JWT filter |
| 3 | [JwtAuthenticationFilter.java](../../src/main/java/com/blockchaincb/demo/security/JwtAuthenticationFilter.java#L42) | `doFilterInternal` | Tạo principal trước audit |
| 4 | [AuditFilter.java](../../src/main/java/com/blockchaincb/demo/security/AuditFilter.java#L19) | `doFilterInternal` | Ghi trước/sau chain, buffer response |
| 5 | [AuditService.java](../../src/main/java/com/blockchaincb/demo/service/AuditService.java#L18) | `record` | REQUIRES_NEW, tạo AuditLog |
| 6 | [AuditLogRepository.java](../../src/main/java/com/blockchaincb/demo/repository/AuditLogRepository.java#L8) | `saveAndFlush (kế thừa)` | Insert nhật ký |
| 7 | [AuditLog.java](../../src/main/java/com/blockchaincb/demo/entity/AuditLog.java#L8) | `AuditLog` | actorUserId, action, resource, outcome, occurredAt |
| 8 | [SecurityErrors.java](../../src/main/java/com/blockchaincb/demo/security/SecurityErrors.java#L9) | `write` | JSON503 nếu audit không sẵn sàng |

## C. Sơ đồ luồng hoạt động

```mermaid
sequenceDiagram
    participant UI as React
    participant F as JWT filter
    participant A as AuditFilter
    participant L as AuditService REQUIRES_NEW
    participant S as Controller và Service
    F->>A: Principal hợp lệ
    A->>L: record ATTEMPT
    alt Ghi ATTEMPT lỗi
        A-->>UI: 503, không chạy nghiệp vụ
    else Ghi ATTEMPT thành công
        A->>S: chain.doFilter với buffered response
        S-->>A: Kết quả HTTP; transaction nghiệp vụ đã kết thúc
        A->>L: SUCCESS hoặc DENIED hoặc ERROR
        alt Ghi kết quả lỗi
            A->>A: Xóa buffer, thay bằng 503
        end
        A-->>UI: copyBodyToResponse
    end
```

## D. Giải thích toàn bộ flow từng bước

### Bước 1: Audit không chạy cho mọi request

AuditFilter đọc SecurityContextHolder.getContext().getAuthentication. Nếu null hoặc principal không phải DoctorPrincipal thì chain đi tiếp mà không ghi audit Doctor. JWT sai hoặc account bị chặn ngay trong JWT filter return trước AuditFilter, nên không có cặp nhật ký này. Login/register public cũng không có principal Doctor. Không có endpoint UI xem audit ở module này.

### Bước 2: Metadata được giới hạn

resource lấy requestURI bỏ contextPath, không lấy query string. Chỉ path khớp /api/doctor/[a-z0-9/-]+ và <=200 ký tự được giữ; khác dùng /api/doctor/unknown. action chính là HTTP method, ví dụ GET/POST/PATCH, không phải enum tên nghiệp vụ CREATE_RECORD. actor là actor.userId, không phải actor.doctorId.

### Bước 3: ATTEMPT trước tác vụ

audit.record(actorUserId,method,resource,"ATTEMPT") được gọi trước chain.doFilter. Nếu RuntimeException, SecurityErrors.write503 rồi return. Vì vậy trong tình huống này Controller chưa chạy và không có thao tác khám đã commit. AuditService dùng @Transactional(REQUIRES_NEW) để insert một AuditLog qua saveAndFlush.

### Bước 4: Tách transaction

REQUIRES_NEW mở transaction riêng (tạm treo transaction hiện hữu nếu có) cho mỗi lần record. Audit ATTEMPT đã commit không bị rollback theo transaction tạo bệnh án thất bại. Nói “audit nằm trong cùng transaction bệnh án” là sai. @Service proxy phải được gọi từ bean khác; ở đây AuditFilter giữ AuditService Spring cấp qua constructor trong SecurityConfig.

### Bước 5: Buffer response và kết quả

ContentCachingResponseWrapper giữ body trước khi gửi thật. failed mặc định true; chỉ set false khi chain hoàn tất không ném exception. finally chọn ERROR nếu failed hoặc status>=500; DENIED nếu status>=400; còn lại SUCCESS. Do đó validation400, notfound404, conflict409 đều được gộp DENIED, không chỉ quyền403. Redirect3xx sẽ bị xếp SUCCESS bởi quy tắc hiện tại.

### Bước 6: Kết quả audit lỗi sau commit

Nếu ghi outcome thất bại, resetBuffer, set503 và ghi JSON lỗi audit vào wrapper. copyBodyToResponse chạy sau để không lộ body y tế ban đầu chưa audit được. Nhưng transaction nghiệp vụ có thể đã commit trước khi audit outcome: response503 không chứng minh không tạo record. Đây là lý do React không nên tự retry POST.

### Bước 7: Dữ liệu DB và phản hồi UI

AuditLog có actorUserId,action,resource,outcome,occurredAt=Instant.now; SQL đề xuất có FK actor tới public.users. Nó không chứa body, token, fullName hay diagnosis. request.js dịch503 thành thông báo kiểm tra lịch sử trước thử lại. Không có notification tự động hoặc giao diện audit chi tiết được triển khai.



## E. Giải thích code quan trọng

[AuditService.java](../../src/main/java/com/blockchaincb/demo/service/AuditService.java#L17) — trích nguyên văn:

```java
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(Long actor, String action, String resource, String outcome) {
    var log = new AuditLog();
    log.setActorUserId(actor);
    log.setAction(action);
    log.setResource(resource);
    log.setOutcome(outcome);
    log.setOccurredAt(Instant.now());
    logs.saveAndFlush(log);
  }
```

Đây là transaction annotation quan trọng nhất flow. logs.saveAndFlush gửi insert trong transaction mới; proxy commit sau record kết thúc. Không bắt exception trong record để filter còn biết audit lỗi và fail closed.

[AuditFilter.java](../../src/main/java/com/blockchaincb/demo/security/AuditFilter.java#L37) — trích nguyên văn:

```java
    var buffered = new ContentCachingResponseWrapper(res);
    boolean failed = true;
    try {
      chain.doFilter(req, buffered);
      failed = false;
    } finally {
      try {
        audit.record(
            actor.userId(),
            req.getMethod(),
            resource,
            failed || buffered.getStatus() >= 500
```

Wrapper giữ nội dung và finally bảo đảm cố ghi kết quả dù downstream ném lỗi. Nếu bỏ buffer, dữ liệu có thể đã gửi ra trước khi biết audit kết quả thất bại.

[AuditLog.java](../../src/main/java/com/blockchaincb/demo/entity/AuditLog.java#L19) — trích nguyên văn:

```java
  private Long actorUserId;
```

actorUserId là ID tài khoản dùng chung, giúp truy nguồn người thực hiện dù module Doctor có ID riêng. Không nhầm cột này với Doctor ID trong version.

## F. Các trường hợp thành công và thất bại

| Trường hợp | Điều kiện | Kết quả | Code xử lý |
|---|---|---|---|
| Bình thường | ATTEMPT và outcome ghi được | Response gốc; hai audit hàng | AuditFilter + record |
| ATTEMPT không ghi | DB audit lỗi trước chain | 503; nghiệp vụ không chạy | catch đầu |
| Outcome không ghi | Nghiệp vụ đã hoàn tất | 503 thay body; có thể đã commit | catch trong finally |
| Nghiệp vụ 403/409 | Chain trả 4xx | DENIED | Phân loại status |
| JWT hỏng | Bị JWT filter return trước | Không audit theo cơ chế này | JWT filter |

### Lưu ý hoặc rủi ro

Không có outbox, idempotency key hoặc ID liên kết ATTEMPT với kết quả; đồng thời nhiều request cần ghép nhật ký theo metadata/thời gian. Audit không bao phủ lời gọi service nội bộ (fixture/test), request login sai hay account bị khóa trước filter. Các exception chưa được downstream xử lý vẫn có thể bị container xử lý thêm sau finally.

### Đọc test để kiểm tra lại lập luận

[AuditFilterTest.java](../../src/test/java/com/blockchaincb/demo/AuditFilterTest.java#L31); [DemoApplicationTests.java](../../src/test/java/com/blockchaincb/demo/DemoApplicationTests.java#L509)

## G. Bài học rút ra

Học try/finally, nested ternary outcome và transaction propagation. Constructor DI không yêu cầu AuditFilter phải @Component: SecurityConfig tự new filter nhưng truyền AuditService managed bean vào. Ghi audit thành công không đồng nghĩa nghiệp vụ thành công; phải đọc outcome.

### Câu hỏi tự kiểm tra

1. Audit dùng User ID hay Doctor ID?
2. Conflict409 được xếp outcome nào?
3. Nghiệp vụ đã commit có thể nhận503 không?
4. JWT sai có audit ATTEMPT từ filter này không?
5. Tại sao cần ResponseWrapper?
