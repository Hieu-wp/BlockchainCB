
package com.blockchaincb.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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
 * - Ghi nhận các thao tác quản trị thành công.
 * - Truy vấn nhật ký theo thời gian.
 * - Phân trang lịch sử thao tác.
 *
 * Yêu cầu bảng public.admin_audit_logs tồn tại.
 *
 * Không ghi dữ liệu nhạy cảm như mật khẩu,
 * token, khóa bí mật hoặc nội dung bệnh án.
 */
@Service
public class AdminAuditLogService {

    private final NamedParameterJdbcTemplate truyVanDuLieu;

    public AdminAuditLogService(
            NamedParameterJdbcTemplate truyVanDuLieu) {

        this.truyVanDuLieu = truyVanDuLieu;
    }

    /**
     * DTO dùng để trả về một bản ghi nhật ký.
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
     * DTO phân trang nhật ký quản trị.
     */
    public record TrangNhatKyQuanTri(
            List<NhatKyQuanTri> danhSachNhatKy,
            int trangHienTai,
            int kichThuocTrang,
            long tongPhanTu,
            long tongSoTrang
    ) {}

    /**
     * Các loại hành động được hỗ trợ.
     *
     * Có thể mở rộng khi hệ thống bổ sung
     * chức năng quản trị mới.
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
     * Các đối tượng có thể bị tác động.
     */
    public enum LoaiDoiTuong {
        USER,
        DOCTOR
    }

    /**
     * Ánh xạ dữ liệu nhật ký từ PostgreSQL.
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

    /**
     * Xác định ID Admin đang đăng nhập.
     *
     * Không cho phép API truyền tùy ý admin_id,
     * tránh giả mạo người thực hiện thao tác.
     */
    @Transactional(readOnly = true)
    public Long layMaAdminDangNhap() {

        Authentication xacThuc =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (xacThuc == null
                || !xacThuc.isAuthenticated()
                || xacThuc instanceof AnonymousAuthenticationToken) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Admin chưa đăng nhập"
            );
        }

        boolean coQuyenAdmin =
                xacThuc.getAuthorities()
                        .stream()
                        .anyMatch(quyen ->
                                "ROLE_ADMIN".equals(
                                        quyen.getAuthority()
                                )
                        );

        if (!coQuyenAdmin) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản không có quyền Admin"
            );
        }

        // Email là username trong cơ chế đăng nhập hiện tại.
        String emailAdmin = xacThuc.getName();

        String sql = """
                SELECT id
                FROM public.users
                WHERE LOWER(email) = LOWER(:email)
                  AND UPPER(role) = 'ADMIN'
                  AND enabled = TRUE
                """;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue("email", emailAdmin);

        List<Long> danhSachMaAdmin =
                truyVanDuLieu.queryForList(
                        sql,
                        thamSo,
                        Long.class
                );

        if (danhSachMaAdmin.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Không tìm thấy tài khoản Admin hợp lệ"
            );
        }

        return danhSachMaAdmin.get(0);
    }

    /**
     * Ghi nhật ký thao tác quản trị.
     *
     * Phương thức này yêu cầu đã có giao dịch
     * database từ Service nghiệp vụ.
     *
     * Nếu thao tác ghi nhật ký thất bại,
     * giao dịch database bên ngoài sẽ rollback.
     *
     * Chỉ gọi sau khi nghiệp vụ được xác nhận
     * thực hiện thành công.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long ghiNhatKy(
            HanhDongQuanTri hanhDong,
            LoaiDoiTuong loaiDoiTuong,
            Long maDoiTuong,
            String moTa) {

        // Kiểm tra dữ liệu đầu vào.
        if (hanhDong == null || loaiDoiTuong == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hành động hoặc loại đối tượng không hợp lệ"
            );
        }

        if (maDoiTuong == null || maDoiTuong <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã đối tượng không hợp lệ"
            );
        }

        if (moTa == null
                || moTa.isBlank()
                || moTa.length() > 500) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mô tả phải có từ 1 đến 500 ký tự"
            );
        }

        // Lấy ID từ phiên đăng nhập hiện tại.
        Long maAdmin = layMaAdminDangNhap();

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
                        .addValue("moTa", moTa.trim());

        return truyVanDuLieu.queryForObject(
                sql,
                thamSo,
                Long.class
        );
    }

    /**
     * Lấy lịch sử nhật ký quản trị.
     *
     * Kết quả được sắp xếp mới nhất trước.
     */
    @Transactional(readOnly = true)
    public TrangNhatKyQuanTri layDanhSachNhatKy(
            int trang,
            int kichThuoc) {

        if (trang < 0
                || kichThuoc < 1
                || kichThuoc > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Thông tin phân trang không hợp lệ"
            );
        }

        // Đảm bảo người truy vấn đang có quyền Admin.
        layMaAdminDangNhap();

        Long ketQuaDem =
                truyVanDuLieu.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM public.admin_audit_logs
                        """,
                        new MapSqlParameterSource(),
                        Long.class
                );

        long tongPhanTu =
                ketQuaDem == null ? 0 : ketQuaDem;

        long viTriBatDau =
                (long) trang * kichThuoc;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue("kichThuoc", kichThuoc)
                        .addValue(
                                "viTriBatDau",
                                viTriBatDau
                        );

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
}
