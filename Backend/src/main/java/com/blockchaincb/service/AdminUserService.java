
package com.blockchaincb.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.blockchaincb.dto.AdminUserResponse;
import com.blockchaincb.dto.AdminUserPageResponse;

import com.blockchaincb.service.AdminAuditLogService.HanhDongQuanTri;
import com.blockchaincb.service.AdminAuditLogService.LoaiDoiTuong;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dịch vụ quản lý người dùng dành cho Admin.
 *
 * Chức năng:
 * 1. Xem danh sách người dùng.
 * 2. Tìm kiếm theo họ tên và email.
 * 3. Lọc theo vai trò và trạng thái.
 * 4. Phân trang danh sách.
 * 5. Xem chi tiết người dùng.
 * 6. Khóa tài khoản PATIENT.
 * 7. Mở khóa tài khoản PATIENT.
 * 8. Ghi nhật ký thao tác Admin.
 *
 * Quy tắc bảo mật:
 * - Chỉ Admin đã xác thực được thay đổi trạng thái.
 * - Không cho phép Admin tự khóa hoặc mở khóa.
 * - Không cho phép thay đổi tài khoản Admin khác.
 * - Không xử lý Doctor khi chưa tích hợp Blockchain.
 * - Cập nhật tài khoản và nhật ký trong cùng transaction.
 * - Không trả về mật khẩu hoặc thông tin bệnh án.
 */
@Service
public class AdminUserService {

    // =========================================================
    // 1. HẰNG SỐ QUẢN LÝ VAI TRÒ VÀ TRẠNG THÁI
    // =========================================================

    private static final String ADMIN = "ADMIN";
    private static final String DOCTOR = "DOCTOR";
    private static final String PATIENT = "PATIENT";

    private static final String HOAT_DONG = "ACTIVE";
    private static final String KHONG_HOAT_DONG = "INACTIVE";

    // Số ký tự tối đa của lý do khóa tài khoản.
    private static final int DO_DAI_LY_DO_TOI_DA = 350;

    // Số bản ghi tối đa cho mỗi trang.
    private static final int KICH_THUOC_TRANG_TOI_DA = 100;

    // =========================================================
    // 2. KHAI BÁO DEPENDENCY
    // =========================================================

    private final NamedParameterJdbcTemplate truyVanDuLieu;

    private final AdminAuditLogService dichVuNhatKy;

    /**
     * Constructor được Spring tự động Dependency Injection.
     */
    public AdminUserService(
            NamedParameterJdbcTemplate truyVanDuLieu,
            AdminAuditLogService dichVuNhatKy) {

        this.truyVanDuLieu = truyVanDuLieu;
        this.dichVuNhatKy = dichVuNhatKy;
    }

    // =========================================================
    // 3. ÁNH XẠ DỮ LIỆU NGƯỜI DÙNG
    // =========================================================

    /**
     * Chuyển một dòng dữ liệu PostgreSQL thành DTO.
     *
     * Không lấy cột password_hash để bảo mật.
     */
    private final RowMapper<AdminUserResponse> anhXaNguoiDung =
            (ketQua, soDong) -> {

                Timestamp ngayTaoSql =
                        ketQua.getTimestamp("created_at");

                LocalDateTime ngayTao =
                        ngayTaoSql == null
                                ? null
                                : ngayTaoSql.toLocalDateTime();

                String trangThai =
                        ketQua.getBoolean("enabled")
                                ? HOAT_DONG
                                : KHONG_HOAT_DONG;

                return new AdminUserResponse(
                        ketQua.getLong("id"),
                        ketQua.getString("full_name"),
                        ketQua.getString("email"),
                        ketQua.getString("role"),
                        trangThai,
                        ketQua.getString("wallet_address"),
                        ngayTao
                );
            };

    /**
     * Lưu thông tin tối thiểu của tài khoản
     * khi thực hiện khóa hoặc mở khóa.
     */
    private record TaiKhoanCanXuLy(
            Long maNguoiDung,
            String vaiTro,
            boolean dangHoatDong
    ) {}

    // =========================================================
    // 4. XEM DANH SÁCH, TÌM KIẾM, LỌC VÀ PHÂN TRANG
    // =========================================================

    /**
     * Lấy danh sách người dùng.
     *
     * tuKhoa:
     * - Họ tên.
     * - Email.
     *
     * vaiTro:
     * - ADMIN.
     * - DOCTOR.
     * - PATIENT.
     *
     * trangThai:
     * - ACTIVE.
     * - INACTIVE.
     *
     * trang:
     * - Bắt đầu từ 0.
     *
     * kichThuoc:
     * - Từ 1 đến 100.
     */
    @Transactional(readOnly = true)
    public AdminUserPageResponse layDanhSachNguoiDung(
            String tuKhoa,
            String vaiTro,
            String trangThai,
            int trang,
            int kichThuoc) {

        // Kiểm tra dữ liệu phân trang.
        kiemTraPhanTrang(trang, kichThuoc);

        // Chuẩn hóa thông tin tìm kiếm.
        String tuKhoaChuanHoa =
                chuanHoaChuoi(tuKhoa);

        String vaiTroChuanHoa =
                chuanHoaTrangThai(vaiTro);

        String trangThaiChuanHoa =
                chuanHoaTrangThai(trangThai);

        // Kiểm tra bộ lọc hợp lệ.
        kiemTraVaiTro(vaiTroChuanHoa);

        kiemTraTrangThai(trangThaiChuanHoa);

        // Chứa các điều kiện SQL động.
        List<String> danhSachDieuKien =
                new ArrayList<>();

        // Chứa tham số truy vấn.
        MapSqlParameterSource thamSo =
                new MapSqlParameterSource();

        // -----------------------------------------------------
        // Tìm kiếm theo họ tên hoặc email.
        // -----------------------------------------------------

        if (tuKhoaChuanHoa != null) {

            danhSachDieuKien.add("""
                    (
                        full_name ILIKE :tuKhoa ESCAPE '!'
                        OR email ILIKE :tuKhoa ESCAPE '!'
                    )
                    """);

            String mauTimKiem =
                    "%" + thoatKyTuTimKiem(tuKhoaChuanHoa) + "%";

            thamSo.addValue(
                    "tuKhoa",
                    mauTimKiem
            );
        }

        // -----------------------------------------------------
        // Lọc theo vai trò.
        // -----------------------------------------------------

        if (vaiTroChuanHoa != null) {

            danhSachDieuKien.add(
                    "UPPER(role) = :vaiTro"
            );

            thamSo.addValue(
                    "vaiTro",
                    vaiTroChuanHoa
            );
        }

        // -----------------------------------------------------
        // Lọc theo trạng thái tài khoản.
        // -----------------------------------------------------

        if (trangThaiChuanHoa != null) {

            danhSachDieuKien.add(
                    "enabled = :kichHoat"
            );

            boolean kichHoat =
                    HOAT_DONG.equals(trangThaiChuanHoa);

            thamSo.addValue(
                    "kichHoat",
                    kichHoat
            );
        }

        // -----------------------------------------------------
        // Xây dựng WHERE động.
        // -----------------------------------------------------

        String dieuKienSql =
                danhSachDieuKien.isEmpty()
                        ? ""
                        : " WHERE " + String.join(
                                " AND ",
                                danhSachDieuKien
                        );

        // -----------------------------------------------------
        // Đếm tổng số người dùng phù hợp.
        // -----------------------------------------------------

        String sqlDem = """
                SELECT COUNT(*)
                FROM public.users
                """ + dieuKienSql;

        Long ketQuaDem =
                truyVanDuLieu.queryForObject(
                        sqlDem,
                        thamSo,
                        Long.class
                );

        long tongPhanTu =
                ketQuaDem == null ? 0 : ketQuaDem;

        // -----------------------------------------------------
        // Tính OFFSET cho phân trang.
        // -----------------------------------------------------

        long viTriBatDau =
                (long) trang * kichThuoc;

        thamSo.addValue(
                "kichThuoc",
                kichThuoc
        );

        thamSo.addValue(
                "viTriBatDau",
                viTriBatDau
        );

        // -----------------------------------------------------
        // Truy vấn danh sách người dùng.
        // -----------------------------------------------------

        String sqlDanhSach = """
                SELECT
                    id,
                    full_name,
                    email,
                    role,
                    wallet_address,
                    enabled,
                    created_at
                FROM public.users
                """
                + dieuKienSql
                + """

                ORDER BY id DESC
                LIMIT :kichThuoc
                OFFSET :viTriBatDau
                """;

        List<AdminUserResponse> danhSachNguoiDung =
                truyVanDuLieu.query(
                        sqlDanhSach,
                        thamSo,
                        anhXaNguoiDung
                );

        // -----------------------------------------------------
        // Tính tổng số trang.
        // -----------------------------------------------------

        int tongSoTrang = Math.toIntExact(
                tongPhanTu / kichThuoc
                        + (tongPhanTu % kichThuoc == 0 ? 0 : 1)
        );

        // -----------------------------------------------------
        // Trả về kết quả phân trang.
        // -----------------------------------------------------

        return new AdminUserPageResponse(
                danhSachNguoiDung,
                trang,
                kichThuoc,
                tongPhanTu,
                tongSoTrang
        );
    }

    // =========================================================
    // 5. XEM CHI TIẾT NGƯỜI DÙNG
    // =========================================================

    /**
     * Lấy thông tin chi tiết người dùng theo ID.
     *
     * Không trả về password_hash.
     */
    @Transactional(readOnly = true)
    public AdminUserResponse layChiTietNguoiDung(
            Long maNguoiDung) {

        kiemTraMaNguoiDung(maNguoiDung);

        String sql = """
                SELECT
                    id,
                    full_name,
                    email,
                    role,
                    wallet_address,
                    enabled,
                    created_at
                FROM public.users
                WHERE id = :maNguoiDung
                """;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue(
                                "maNguoiDung",
                                maNguoiDung
                        );

        List<AdminUserResponse> ketQua =
                truyVanDuLieu.query(
                        sql,
                        thamSo,
                        anhXaNguoiDung
                );

        if (ketQua.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy người dùng"
            );
        }

        return ketQua.get(0);
    }

    // =========================================================
    // 6. KHÓA TÀI KHOẢN NGƯỜI DÙNG
    // =========================================================

    /**
     * Khóa tài khoản PATIENT.
     *
     * Quy trình:
     * 1. Kiểm tra ID và lý do.
     * 2. Xác thực Admin đang đăng nhập.
     * 3. Khóa dòng người dùng trong PostgreSQL.
     * 4. Kiểm tra vai trò tài khoản.
     * 5. Cập nhật enabled = FALSE.
     * 6. Ghi nhật ký USER_LOCK.
     * 7. Commit hoặc rollback toàn bộ transaction.
     */
    @Transactional
    public AdminUserResponse khoaNguoiDung(
            Long maNguoiDung,
            String lyDo) {

        // -----------------------------------------------------
        // Kiểm tra thông tin yêu cầu.
        // -----------------------------------------------------

        kiemTraMaNguoiDung(maNguoiDung);
        kiemTraLyDo(lyDo);

        String lyDoChuanHoa = lyDo.trim();

        // -----------------------------------------------------
        // Lấy Admin đang đăng nhập.
        // -----------------------------------------------------

        Long maAdmin =
                dichVuNhatKy.layMaAdminDangNhap();

        // Không cho phép Admin tự khóa chính mình.
        if (maAdmin.equals(maNguoiDung)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin không được tự khóa tài khoản của mình"
            );
        }

        // -----------------------------------------------------
        // Lấy thông tin và khóa dòng dữ liệu.
        // -----------------------------------------------------

        TaiKhoanCanXuLy taiKhoan =
                layTaiKhoanDeCapNhat(maNguoiDung);

        // -----------------------------------------------------
        // Kiểm tra tài khoản được phép thay đổi.
        // -----------------------------------------------------

        kiemTraTaiKhoanChoPhepXuLy(taiKhoan);

        // -----------------------------------------------------
        // Kiểm tra tài khoản đã bị khóa chưa.
        // -----------------------------------------------------

        if (!taiKhoan.dangHoatDong()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tài khoản đã bị khóa trước đó"
            );
        }

        // -----------------------------------------------------
        // Cập nhật users.enabled = FALSE.
        // -----------------------------------------------------

        String sql = """
                UPDATE public.users
                SET enabled = FALSE
                WHERE id = :maNguoiDung
                  AND enabled = TRUE
                  AND UPPER(role) = 'PATIENT'
                """;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue(
                                "maNguoiDung",
                                maNguoiDung
                        );

        int soDongCapNhat =
                truyVanDuLieu.update(
                        sql,
                        thamSo
                );

        if (soDongCapNhat != 1) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể khóa tài khoản"
            );
        }

        // -----------------------------------------------------
        // Ghi nhật ký trong cùng transaction PostgreSQL.
        // -----------------------------------------------------

        dichVuNhatKy.ghiNhatKy(
                HanhDongQuanTri.USER_LOCK,
                LoaiDoiTuong.USER,
                maNguoiDung,
                "Khóa tài khoản người dùng #"
                        + maNguoiDung
                        + ". Lý do: "
                        + lyDoChuanHoa
        );

        // -----------------------------------------------------
        // Trả dữ liệu tài khoản sau khi khóa.
        // -----------------------------------------------------

        return layChiTietNguoiDung(
                maNguoiDung
        );
    }

    // =========================================================
    // 7. MỞ KHÓA TÀI KHOẢN NGƯỜI DÙNG
    // =========================================================

    /**
     * Mở khóa tài khoản PATIENT.
     *
     * Quy trình:
     * 1. Xác thực Admin.
     * 2. Khóa dòng người dùng.
     * 3. Kiểm tra vai trò.
     * 4. Kiểm tra tài khoản đang bị khóa.
     * 5. Cập nhật enabled = TRUE.
     * 6. Ghi nhật ký USER_UNLOCK.
     * 7. Commit hoặc rollback.
     */
    @Transactional
    public AdminUserResponse moKhoaNguoiDung(
            Long maNguoiDung) {

        kiemTraMaNguoiDung(maNguoiDung);

        // -----------------------------------------------------
        // Xác định Admin thực hiện thao tác.
        // -----------------------------------------------------

        Long maAdmin =
                dichVuNhatKy.layMaAdminDangNhap();

        if (maAdmin.equals(maNguoiDung)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin không được tự thay đổi trạng thái tài khoản"
            );
        }

        // -----------------------------------------------------
        // Lấy và khóa bản ghi người dùng.
        // -----------------------------------------------------

        TaiKhoanCanXuLy taiKhoan =
                layTaiKhoanDeCapNhat(maNguoiDung);

        kiemTraTaiKhoanChoPhepXuLy(taiKhoan);

        // -----------------------------------------------------
        // Kiểm tra tài khoản đang hoạt động.
        // -----------------------------------------------------

        if (taiKhoan.dangHoatDong()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tài khoản hiện đang hoạt động"
            );
        }

        // -----------------------------------------------------
        // Cập nhật users.enabled = TRUE.
        // -----------------------------------------------------

        String sql = """
                UPDATE public.users
                SET enabled = TRUE
                WHERE id = :maNguoiDung
                  AND enabled = FALSE
                  AND UPPER(role) = 'PATIENT'
                """;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue(
                                "maNguoiDung",
                                maNguoiDung
                        );

        int soDongCapNhat =
                truyVanDuLieu.update(
                        sql,
                        thamSo
                );

        if (soDongCapNhat != 1) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể mở khóa tài khoản"
            );
        }

        // -----------------------------------------------------
        // Ghi nhật ký mở khóa.
        // -----------------------------------------------------

        dichVuNhatKy.ghiNhatKy(
                HanhDongQuanTri.USER_UNLOCK,
                LoaiDoiTuong.USER,
                maNguoiDung,
                "Mở khóa tài khoản người dùng #"
                        + maNguoiDung
        );

        return layChiTietNguoiDung(
                maNguoiDung
        );
    }

    // =========================================================
    // 8. LẤY TÀI KHOẢN ĐỂ CẬP NHẬT
    // =========================================================

    /**
     * Khóa dòng người dùng bằng SELECT FOR UPDATE.
     *
     * Mục đích:
     * - Tránh hai Admin thay đổi đồng thời cùng tài khoản.
     * - Bảo đảm trạng thái được kiểm tra trong transaction.
     * - Giữ khóa đến khi commit hoặc rollback.
     */
    private TaiKhoanCanXuLy layTaiKhoanDeCapNhat(
            Long maNguoiDung) {

        String sql = """
                SELECT
                    id,
                    role,
                    enabled
                FROM public.users
                WHERE id = :maNguoiDung
                FOR UPDATE
                """;

        MapSqlParameterSource thamSo =
                new MapSqlParameterSource()
                        .addValue(
                                "maNguoiDung",
                                maNguoiDung
                        );

        List<TaiKhoanCanXuLy> danhSach =
                truyVanDuLieu.query(
                        sql,
                        thamSo,
                        (ketQua, soDong) ->
                                new TaiKhoanCanXuLy(
                                        ketQua.getLong("id"),
                                        ketQua.getString("role"),
                                        ketQua.getBoolean("enabled")
                                )
                );

        if (danhSach.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy người dùng"
            );
        }

        return danhSach.get(0);
    }

    // =========================================================
    // 9. KIỂM TRA TÀI KHOẢN ĐƯỢC PHÉP XỬ LÝ
    // =========================================================

    /**
     * Chỉ cho phép khóa/mở khóa tài khoản PATIENT.
     *
     * ADMIN:
     * - Không cho phép thay đổi.
     *
     * DOCTOR:
     * - Chưa cho phép vì cần đồng bộ Blockchain.
     *
     * PATIENT:
     * - Cho phép thay đổi sau khi kiểm tra trạng thái.
     */
    private void kiemTraTaiKhoanChoPhepXuLy(
            TaiKhoanCanXuLy taiKhoan) {

        String vaiTro = taiKhoan.vaiTro() == null
                ? ""
                : taiKhoan.vaiTro()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (ADMIN.equals(vaiTro)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Không cho phép khóa hoặc mở khóa tài khoản Admin"
            );
        }

        if (DOCTOR.equals(vaiTro)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chưa hỗ trợ thay đổi trạng thái Doctor khi chưa đồng bộ Blockchain"
            );
        }

        if (!PATIENT.equals(vaiTro)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vai trò tài khoản chưa được hỗ trợ"
            );
        }
    }

    // =========================================================
    // 10. CHUẨN HÓA DỮ LIỆU TÌM KIẾM
    // =========================================================

    /**
     * Loại bỏ khoảng trắng đầu/cuối.
     * Chuỗi rỗng được chuyển thành null.
     */
    private String chuanHoaChuoi(
            String giaTri) {

        if (giaTri == null || giaTri.isBlank()) {
            return null;
        }

        return giaTri.trim();
    }

    /**
     * Chuyển trạng thái thành chữ in hoa.
     */
    private String chuanHoaTrangThai(
            String giaTri) {

        String ketQua = chuanHoaChuoi(giaTri);

        if (ketQua == null) {
            return null;
        }

        return ketQua.toUpperCase(Locale.ROOT);
    }

    /**
     * Thoát ký tự đặc biệt trong truy vấn ILIKE.
     *
     * Tránh việc % và _ được hiểu là wildcard.
     */
    private String thoatKyTuTimKiem(
            String tuKhoa) {

        return tuKhoa
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    // =========================================================
    // 11. KIỂM TRA VAI TRÒ
    // =========================================================

    /**
     * Chỉ chấp nhận ba vai trò của hệ thống.
     */
    private void kiemTraVaiTro(
            String vaiTro) {

        if (vaiTro == null) {
            return;
        }

        if (!ADMIN.equals(vaiTro)
                && !DOCTOR.equals(vaiTro)
                && !PATIENT.equals(vaiTro)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vai trò người dùng không hợp lệ"
            );
        }
    }

    // =========================================================
    // 12. KIỂM TRA TRẠNG THÁI
    // =========================================================

    /**
     * Kiểm tra trạng thái lọc người dùng.
     */
    private void kiemTraTrangThai(
            String trangThai) {

        if (trangThai == null) {
            return;
        }

        if (!HOAT_DONG.equals(trangThai)
                && !KHONG_HOAT_DONG.equals(trangThai)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái tài khoản không hợp lệ"
            );
        }
    }

    // =========================================================
    // 13. KIỂM TRA THAM SỐ PHÂN TRANG
    // =========================================================

    /**
     * Trang bắt đầu từ 0.
     * Số bản ghi mỗi trang từ 1 đến 100.
     */
    private void kiemTraPhanTrang(
            int trang,
            int kichThuoc) {

        if (trang < 0
                || kichThuoc < 1
                || kichThuoc > KICH_THUOC_TRANG_TOI_DA) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang phải >= 0 và kích thước từ 1 đến 100"
            );
        }
    }

    // =========================================================
    // 14. KIỂM TRA MÃ NGƯỜI DÙNG
    // =========================================================

    /**
     * Kiểm tra ID người dùng hợp lệ.
     */
    private void kiemTraMaNguoiDung(
            Long maNguoiDung) {

        if (maNguoiDung == null
                || maNguoiDung <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã người dùng không hợp lệ"
            );
        }
    }

    // =========================================================
    // 15. KIỂM TRA LÝ DO KHÓA
    // =========================================================

    /**
     * Yêu cầu lý do khóa không được rỗng
     * và không vượt quá giới hạn cho phép.
     */
    private void kiemTraLyDo(
            String lyDo) {

        if (lyDo == null || lyDo.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lý do khóa tài khoản không được để trống"
            );
        }

        if (lyDo.trim().length() > DO_DAI_LY_DO_TOI_DA) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lý do khóa không được vượt quá 350 ký tự"
            );
        }
    }
}
