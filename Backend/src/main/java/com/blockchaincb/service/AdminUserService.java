
package com.blockchaincb.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.blockchaincb.dto.AdminUserResponse;
import com.blockchaincb.dto.AdminUserPageResponse;

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
 * - Xem danh sách người dùng.
 * - Tìm kiếm theo họ tên và email.
 * - Lọc theo vai trò và trạng thái.
 * - Phân trang danh sách.
 * - Xem chi tiết người dùng.
 *
 * Chỉ truy vấn các trường cần thiết.
 * Không trả về password_hash.
 *
 * Chưa kích hoạt thao tác khóa/mở khóa vì
 * cần thống nhất quyền quản trị và nhật ký thao tác.
 */
@Service
public class AdminUserService {

    // Các vai trò đang sử dụng trong hệ thống.
    private static final String ADMIN = "ADMIN";
    private static final String DOCTOR = "DOCTOR";
    private static final String PATIENT = "PATIENT";

    // Quy ước phản hồi từ cột users.enabled.
    private static final String HOAT_DONG = "ACTIVE";
    private static final String KHONG_HOAT_DONG = "INACTIVE";

    private final NamedParameterJdbcTemplate truyVanDuLieu;

    /**
     * Khởi tạo dịch vụ quản lý người dùng.
     */
    public AdminUserService(
            NamedParameterJdbcTemplate truyVanDuLieu) {

        this.truyVanDuLieu = truyVanDuLieu;
    }

    /**
     * Ánh xạ kết quả SQL thành DTO.
     *
     * Không đọc hoặc trả về mật khẩu.
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
     * Lấy danh sách người dùng có tìm kiếm,
     * lọc và phân trang.
     *
     * Tham số:
     * - tuKhoa: Họ tên hoặc email.
     * - vaiTro: ADMIN, DOCTOR, PATIENT.
     * - trangThai: ACTIVE, INACTIVE.
     * - trang: Bắt đầu từ 0.
     * - kichThuoc: Từ 1 đến 100.
     */
    @Transactional(readOnly = true)
    public AdminUserPageResponse layDanhSachNguoiDung(
            String tuKhoa,
            String vaiTro,
            String trangThai,
            int trang,
            int kichThuoc) {

        // Kiểm tra điều kiện phân trang.
        kiemTraPhanTrang(trang, kichThuoc);

        // Chuẩn hóa dữ liệu do Admin nhập.
        String tuKhoaChuanHoa = chuanHoaChuoi(tuKhoa);
        String vaiTroChuanHoa = chuanHoaTrangThai(vaiTro);
        String trangThaiChuanHoa =
                chuanHoaTrangThai(trangThai);

        // Kiểm tra giá trị bộ lọc.
        kiemTraVaiTro(vaiTroChuanHoa);
        kiemTraTrangThai(trangThaiChuanHoa);

        // Danh sách điều kiện tìm kiếm.
        List<String> danhSachDieuKien = new ArrayList<>();

        // Chứa tham số SQL, tránh nối trực tiếp đầu vào.
        MapSqlParameterSource thamSo =
                new MapSqlParameterSource();

        // Tìm kiếm họ tên hoặc email.
        if (tuKhoaChuanHoa != null) {

            danhSachDieuKien.add("""
                    (
                        full_name ILIKE :tuKhoa ESCAPE '!'
                        OR email ILIKE :tuKhoa ESCAPE '!'
                    )
                    """);

            thamSo.addValue(
                    "tuKhoa",
                    "%" + thoatKyTuTimKiem(tuKhoaChuanHoa) + "%"
            );
        }

        // Lọc vai trò người dùng.
        if (vaiTroChuanHoa != null) {

            danhSachDieuKien.add(
                    "UPPER(role) = :vaiTro"
            );

            thamSo.addValue(
                    "vaiTro",
                    vaiTroChuanHoa
            );
        }

        // Lọc theo trạng thái enabled.
        if (trangThaiChuanHoa != null) {

            danhSachDieuKien.add(
                    "enabled = :kichHoat"
            );

            thamSo.addValue(
                    "kichHoat",
                    HOAT_DONG.equals(trangThaiChuanHoa)
            );
        }

        // Chỉ tạo WHERE khi có điều kiện.
        String dieuKienSql = danhSachDieuKien.isEmpty()
                ? ""
                : " WHERE " + String.join(
                        " AND ",
                        danhSachDieuKien
                );

        // Đếm tổng số người dùng phù hợp.
        String sqlDem = """
                SELECT COUNT(*)
                FROM public.users
                """ + dieuKienSql;

        Long tongNguoiDung =
                truyVanDuLieu.queryForObject(
                        sqlDem,
                        thamSo,
                        Long.class
                );

        long tongPhanTu =
                tongNguoiDung == null ? 0 : tongNguoiDung;

        // Tính vị trí bắt đầu của trang.
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

        // Truy vấn danh sách người dùng.
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

        // Tính tổng số trang.
        int tongSoTrang = Math.toIntExact(
                tongPhanTu / kichThuoc
                        + (tongPhanTu % kichThuoc == 0 ? 0 : 1)
        );

        // Trả về đúng cấu trúc DTO hiện tại.
        return new AdminUserPageResponse(
                danhSachNguoiDung,
                trang,
                kichThuoc,
                tongPhanTu,
                tongSoTrang
        );
    }

    /**
     * Xem chi tiết người dùng theo ID.
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

    /**
     * Khóa tài khoản người dùng.
     *
     * Chưa kích hoạt:
     * - Phải ngăn Admin tự khóa chính mình.
     * - Phải bảo vệ tài khoản Admin khác.
     * - Phải ghi nhận lịch sử thao tác.
     * - Tài khoản Doctor cần đồng bộ quyền Blockchain.
     */
    public AdminUserResponse khoaNguoiDung(
            Long maNguoiDung,
            String lyDo) {

        kiemTraMaNguoiDung(maNguoiDung);
        kiemTraLyDo(lyDo);

        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Chưa tích hợp quy trình khóa tài khoản an toàn"
        );
    }

    /**
     * Mở khóa tài khoản người dùng.
     *
     * Chưa thay đổi users.enabled.
     */
    public AdminUserResponse moKhoaNguoiDung(
            Long maNguoiDung) {

        kiemTraMaNguoiDung(maNguoiDung);

        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Chưa tích hợp quy trình mở khóa và nhật ký quản trị"
        );
    }

    /**
     * Chuẩn hóa chuỗi nhập vào.
     */
    private String chuanHoaChuoi(
            String giaTri) {

        if (giaTri == null || giaTri.isBlank()) {
            return null;
        }

        return giaTri.trim();
    }

    /**
     * Chuyển giá trị trạng thái thành chữ in hoa.
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
     * Thoát ký tự đặc biệt của LIKE.
     *
     * Tránh việc % hoặc _ trong từ khóa
     * bị hiểu là ký tự đại diện.
     */
    private String thoatKyTuTimKiem(
            String tuKhoa) {

        return tuKhoa
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * Kiểm tra vai trò hợp lệ.
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

    /**
     * Kiểm tra trạng thái tài khoản.
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

    /**
     * Kiểm tra tham số phân trang.
     */
    private void kiemTraPhanTrang(
            int trang,
            int kichThuoc) {

        if (trang < 0
                || kichThuoc < 1
                || kichThuoc > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang phải >= 0 và kích thước từ 1 đến 100"
            );
        }
    }

    /**
     * Kiểm tra mã người dùng.
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

    /**
     * Kiểm tra lý do khóa tài khoản.
     */
    private void kiemTraLyDo(
            String lyDo) {

        if (lyDo == null || lyDo.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lý do không được để trống"
            );
        }
    }
}
