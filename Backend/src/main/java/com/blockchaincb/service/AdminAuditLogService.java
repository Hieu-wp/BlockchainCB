
package com.blockchaincb.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dịch vụ nhật ký quản trị của BlockchainCB.
 *
 * Chức năng:
 * - Xác định Admin đang đăng nhập.
 * - Kiểm tra quyền quản trị.
 * - Ghi nhật ký thao tác Admin.
 * - Xem và phân trang nhật ký.
 *
 * Các thao tác ghi nhật ký phải thực hiện
 * trong cùng giao dịch với Service nghiệp vụ.
 *
 * Không lưu mật khẩu, token, khóa bí mật
 * hoặc nội dung hồ sơ bệnh án.
 */
@Service
public class AdminAuditLogService {

    // =====================================================
    // 1. HẰNG SỐ
    // =====================================================

    private static final int KICH_THUOC_TOI_DA = 100;

    private static final int DO_DAI_MO_TA_TOI_DA = 500;

    private static final String QUYEN_ADMIN = "ROLE_ADMIN";

    private static final String VAI_TRO_ADMIN = "ADMIN";

    // =====================================================
    // 2. DEPENDENCY
    // =====================================================

    private final NamedParameterJdbcTemplate truyVanDuLieu;

    /**
     * Khởi tạo dịch vụ nhật ký.
     */
    public AdminAuditLogService(
            NamedParameterJdbcTemplate truyVanDuLieu) {

        this.truyVanDuLieu = truyVanDuLieu;
    }

    // =====================================================
    // 3. DTO NHẬT KÝ
    // =====================================================

    /**
     * Thông tin một nhật ký quản trị.
     */
    public record NhatKyQuanTri(
            Long maNhatKy,
            Long maAdmin,
            String hanhDong,
            String loaiDoiTuong,
            Long maDoiTuong,
            String moTa,
            Instant thoiGian
    ) {}

    /**
     * Kết quả phân trang danh sách nhật ký.
     */
    public record TrangNhatKyQuanTri(
            List<NhatKyQuanTri> danhSachNhatKy,
            int trangHienTai,
            int kichThuocTrang,
            long tongPhanTu,
            long tongSoTrang
    ) {}

    // =====================================================
    // 4. CÁC HÀNH ĐỘNG QUẢN TRỊ
    // =====================================================

    /**
     * Các hành động quản trị được hỗ trợ.
     *
     * Không ghi nhận thành công khi nghiệp vụ
     * chưa thực sự hoàn tất.
     */
    public enum HanhDongQuanTri {

        USER_LOCK,
        USER_UNLOCK,

        DOCTOR_APPROVE,
        DOCTOR_REJECT,
        DOCTOR_SUSPEND,
        DOCTOR_REACTIVATE
    }

    /**
     * Loại đối tượng bị tác động.
     */
    public enum LoaiDoiTuong {

        USER,
        DOCTOR
    }

    // =====================================================
    // 5. ÁNH XẠ DỮ LIỆU NHẬT KÝ
    // =====================================================

    /**
     * Chuyển bản ghi PostgreSQL thành DTO.
     */
    private final RowMapper<NhatKyQuanTri> anhXaNhatKy =
            (ketQua, soDong) -> {

                Timestamp thoiGianSql =
                        ketQua.getTimestamp("created_at");

                Instant thoiGian =
                        thoiGianSql == null
                                ? null
                                : thoiGianSql.toInstant();

                return new NhatKyQuanTri(
                        ketQua.getLong("id"),
                        ketQua.getLong("admin_id"),
                        ketQua.getString("action"),
                        ketQua.getString("target_type"),
                        ketQua.getLong("target_id"),
                        ketQua.getString("description"),
                        thoiGian
                );
            };

    // =====================================================
    // 6. XÁC ĐỊNH ADMIN ĐANG ĐĂNG NHẬP
    // =====================================================

    /**
     * Lấy ID Admin đã xác thực từ Spring Security.
     *
     * Không nhận ID Admin từ request nhằm tránh
     * giả mạo người thực hiện thao tác.
     *
     * Cơ chế xác thực hiện tại sử dụng email
     * làm username đăng nhập.
     */
    public Long layMaAdminDangNhap() {

        Authentication xacThuc =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        // Kiểm tra phiên đăng nhập.
        if (xacThuc == null
                || !xacThuc.isAuthenticated()
                || xacThuc instanceof AnonymousAuthenticationToken) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Admin chưa đăng nhập"
            );
        }

        // Kiểm tra quyền quản trị.
        boolean coQuyenAdmin =
                xacThuc.getAuthorities()
                        .stream()
                        .anyMatch(quyen ->
                                QUYEN_ADMIN.equals(
                                        quyen.getAuthority()
                                )
                        );

        if (!coQuyenAdmin) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản không có quyền Admin"
            );
        }

        // Lấy email đã xác thực từ Spring Security.
        String emailAdmin = xacThuc.getName();

        if (emailAdmin == null || emailAdmin.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không xác định được tài khoản Admin"
            );
        }

        // Chỉ chấp nhận Admin đang hoạt động.
        String sql = """
                SELECT id
                FROM public.users
                WHERE LOWER(email) = :email
                  AND UPPER(role) = :vaiTro
                  AND enabled = TRUE
                """;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue(
                                "email",
                                emailAdmin.trim()
                                        .toLowerCase(Locale.ROOT)
                        )
                        .addValue(
                                "vaiTro",
                                VAI_TRO_ADMIN
                        );

        List<Long> danhSachMaAdmin =
                truyVanDuLieu.queryForList(
                        sql,
                        thamSo,
                        Long.class
                );

        if (danhSachMaAdmin.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản Admin không tồn tại hoặc đã bị vô hiệu hóa"
            );
        }

        return danhSachMaAdmin.get(0);
    }

    // =====================================================
    // 7. GHI NHẬT KÝ QUẢN TRỊ
    // =====================================================

    /**
     * Ghi nhật ký thao tác quản trị.
     *
     * Phải được gọi từ Service nghiệp vụ
     * đang có giao dịch @Transactional.
     *
     * Propagation.MANDATORY:
     * - Không cho phép tự tạo giao dịch mới.
     * - Tham gia giao dịch đang tồn tại.
     * - Nếu ghi nhật ký lỗi, exception được
     *   truyền ra để rollback giao dịch.
     *
     * Lưu ý:
     * Chỉ ghi nhật ký sau khi cập nhật dữ liệu
     * nghiệp vụ thành công.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long ghiNhatKy(
            HanhDongQuanTri hanhDong,
            LoaiDoiTuong loaiDoiTuong,
            Long maDoiTuong,
            String moTa) {

        // Kiểm tra hành động.
        if (hanhDong == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hành động quản trị không hợp lệ"
            );
        }

        // Kiểm tra loại đối tượng.
        if (loaiDoiTuong == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loại đối tượng không hợp lệ"
            );
        }

        // Kiểm tra ID đối tượng.
        if (maDoiTuong == null || maDoiTuong <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã đối tượng không hợp lệ"
            );
        }

        // Chuẩn hóa và kiểm tra mô tả.
        String moTaChuanHoa = kiemTraVaChuanHoaMoTa(moTa);

        // Xác định Admin từ phiên đăng nhập.
        Long maAdmin = layMaAdminDangNhap();

        // Tạo câu lệnh ghi nhật ký.
        String sql = """
                INSERT INTO public.admin_audit_logs (
                    admin_id,
                    action,
                    target_type,
                    target_id,
                    description
                )
                VALUES (
                    :maAdmin,
                    :hanhDong,
                    :loaiDoiTuong,
                    :maDoiTuong,
                    :moTa
                )
                RETURNING id
                """;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue("maAdmin", maAdmin)
                        .addValue("hanhDong", hanhDong.name())
                        .addValue(
                                "loaiDoiTuong",
                                loaiDoiTuong.name()
                        )
                        .addValue("maDoiTuong", maDoiTuong)
                        .addValue("moTa", moTaChuanHoa);

        // Thực hiện INSERT trong giao dịch hiện tại.
        Long maNhatKy =
                truyVanDuLieu.queryForObject(
                        sql,
                        thamSo,
                        Long.class
                );

        if (maNhatKy == null) {

            throw new IllegalStateException(
                    "Ghi nhật ký không trả về mã bản ghi"
            );
        }

        return maNhatKy;
    }

    // =====================================================
    // 8. XEM DANH SÁCH NHẬT KÝ
    // =====================================================

    /**
     * Lấy nhật ký quản trị có phân trang.
     *
     * Trang bắt đầu từ 0.
     * Số bản ghi mỗi trang từ 1 đến 100.
     *
     * Sắp xếp bản ghi mới nhất trước.
     */
    @Transactional(readOnly = true)
    public TrangNhatKyQuanTri layDanhSachNhatKy(
            int trang,
            int kichThuoc) {

        // Kiểm tra thông tin phân trang.
        kiemTraPhanTrang(trang, kichThuoc);

        // Chỉ Admin hợp lệ mới được đọc nhật ký.
        layMaAdminDangNhap();

        // Đếm tổng số bản ghi.
        String sqlDem = """
                SELECT COUNT(*)
                FROM public.admin_audit_logs
                """;

        Long ketQuaDem =
                truyVanDuLieu.queryForObject(
                        sqlDem,
                        new MapSqlParameterSource(),
                        Long.class
                );

        long tongPhanTu =
                ketQuaDem == null ? 0 : ketQuaDem;

        // Tính vị trí bắt đầu của trang.
        long viTriBatDau =
                (long) trang * kichThuoc;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue("kichThuoc", kichThuoc)
                        .addValue(
                                "viTriBatDau",
                                viTriBatDau
                        );

        // Truy vấn lịch sử nhật ký.
        String sql = """
                SELECT
                    id,
                    admin_id,
                    action,
                    target_type,
                    target_id,
                    description,
                    created_at
                FROM public.admin_audit_logs
                ORDER BY created_at DESC, id DESC
                LIMIT :kichThuoc
                OFFSET :viTriBatDau
                """;

        List<NhatKyQuanTri> danhSachNhatKy =
                truyVanDuLieu.query(
                        sql,
                        thamSo,
                        anhXaNhatKy
                );

        // Tính tổng số trang.
        long tongSoTrang =
                tongPhanTu / kichThuoc
                + (tongPhanTu % kichThuoc == 0 ? 0 : 1);

        return new TrangNhatKyQuanTri(
                danhSachNhatKy,
                trang,
                kichThuoc,
                tongPhanTu,
                tongSoTrang
        );
    }

    // =====================================================
    // 9. KIỂM TRA DỮ LIỆU ĐẦU VÀO
    // =====================================================

    /**
     * Kiểm tra và chuẩn hóa mô tả nhật ký.
     */
    private String kiemTraVaChuanHoaMoTa(
            String moTa) {

        if (moTa == null || moTa.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mô tả nhật ký không được để trống"
            );
        }

        String moTaChuanHoa = moTa.trim();

        if (moTaChuanHoa.length() > DO_DAI_MO_TA_TOI_DA) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mô tả nhật ký không được vượt quá 500 ký tự"
            );
        }

        return moTaChuanHoa;
    }

    /**
     * Kiểm tra trang và kích thước trang.
     */
    private void kiemTraPhanTrang(
            int trang,
            int kichThuoc) {

        if (trang < 0
                || kichThuoc < 1
                || kichThuoc > KICH_THUOC_TOI_DA) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang phải >= 0 và kích thước từ 1 đến 100"
            );
        }
    }
}
