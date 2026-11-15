
package com.blockchaincb.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.blockchaincb.dto.DoctorResponse;
import com.blockchaincb.entity.Doctor;
import com.blockchaincb.entity.User;
import com.blockchaincb.repository.AdminDoctorRepository;
import com.blockchaincb.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dịch vụ quản lý bác sĩ dành cho Admin.
 *
 * Chức năng:
 * - Lấy danh sách bác sĩ.
 * - Tìm kiếm bác sĩ.
 * - Lọc theo trạng thái phê duyệt.
 * - Lọc theo trạng thái tài khoản.
 * - Phân trang dữ liệu.
 * - Xem thông tin chi tiết.
 *
 * Chưa kích hoạt:
 * - Duyệt và từ chối bác sĩ.
 * - Khóa và mở khóa bác sĩ.
 *
 * Các thao tác thay đổi quyền phải được tích hợp
 * với Blockchain và cơ chế nhật ký quản trị.
 */
@Service
public class AdminDoctorService {

    // Các trạng thái đã được xác nhận từ Supabase.
    private static final String CHO_DUYET = "PENDING";
    private static final String DA_DUYET = "APPROVED";
    private static final String TU_CHOI = "REJECTED";

    private final AdminDoctorRepository khoQuanLyBacSi;
    private final UserRepository khoNguoiDung;

    /**
     * Khởi tạo dịch vụ quản lý bác sĩ.
     */
    public AdminDoctorService(
            AdminDoctorRepository khoQuanLyBacSi,
            UserRepository khoNguoiDung) {

        this.khoQuanLyBacSi = khoQuanLyBacSi;
        this.khoNguoiDung = khoNguoiDung;
    }

    /**
     * Lấy toàn bộ danh sách bác sĩ.
     *
     * Giữ nguyên để các API cũ tiếp tục hoạt động.
     */
    @Transactional(readOnly = true)
    public List<DoctorResponse> layDanhSachBacSi() {

        List<Doctor> danhSachBacSi =
                khoQuanLyBacSi.findAll(
                        Sort.by(
                                Sort.Direction.DESC,
                                "maBacSi"
                        )
                );

        Map<Long, User> nguoiDungTheoMa =
                layNguoiDungLienQuan(danhSachBacSi);

        return danhSachBacSi.stream()
                .map(bacSi -> chuyenThanhPhanHoi(
                        bacSi,
                        nguoiDungTheoMa
                ))
                .toList();
    }

    /**
     * Lấy bác sĩ đang chờ Admin phê duyệt.
     */
    @Transactional(readOnly = true)
    public List<DoctorResponse> layBacSiChoDuyet() {

        List<Doctor> danhSachBacSi =
                khoQuanLyBacSi
                        .findByTrangThaiPheDuyetIgnoreCase(
                                CHO_DUYET
                        );

        Map<Long, User> nguoiDungTheoMa =
                layNguoiDungLienQuan(danhSachBacSi);

        return danhSachBacSi.stream()
                .map(bacSi -> chuyenThanhPhanHoi(
                        bacSi,
                        nguoiDungTheoMa
                ))
                .toList();
    }

    /**
     * Tìm kiếm, lọc và phân trang bác sĩ.
     *
     * Trang bắt đầu từ 0.
     * Kích thước mỗi trang từ 1 đến 100.
     */
    
/**
 * Tìm kiếm, lọc và phân trang danh sách bác sĩ.
 *
 * Không sử dụng điều kiện :thamSo IS NULL.
 * Chỉ bổ sung điều kiện khi tham số có giá trị.
 */
    @Transactional(readOnly = true)
    public Page<DoctorResponse> timKiemBacSi(
        String tuKhoa,
        String trangThaiPheDuyet,
        String trangThaiTaiKhoan,
        int trang,
        int kichThuoc) {

    // Kiểm tra thông tin phân trang.
    kiemTraPhanTrang(trang, kichThuoc);

    // Chuẩn hóa dữ liệu tìm kiếm.
    String tuKhoaChuanHoa = chuanHoaChuoi(tuKhoa);
    String pheDuyetChuanHoa =
            chuanHoaTrangThai(trangThaiPheDuyet);
    String taiKhoanChuanHoa =
            chuanHoaTrangThai(trangThaiTaiKhoan);

    // Chỉ chấp nhận trạng thái phê duyệt hợp lệ.
    kiemTraTrangThaiPheDuyet(pheDuyetChuanHoa);

    // Tạo điều kiện tìm kiếm động.
    Specification<Doctor> dieuKien = (root, query, cb) -> {

        List<Predicate> danhSachDieuKien = new ArrayList<>();

        // Tìm kiếm theo tên, email, bệnh viện, số giấy phép.
        if (tuKhoaChuanHoa != null) {

            String mauTimKiem =
                    "%" + tuKhoaChuanHoa.toLowerCase(
                            Locale.ROOT
                    ).replace("\\", "\\\\")
                     .replace("%", "\\%")
                     .replace("_", "\\_") + "%";

            Predicate timTheoTen = cb.like(
                    cb.lower(root.get("hoTen")),
                    mauTimKiem,
                    '\\'
            );

            Predicate timTheoEmail = cb.like(
                    cb.lower(root.get("email")),
                    mauTimKiem,
                    '\\'
            );

            Predicate timTheoBenhVien = cb.like(
                    cb.lower(root.get("benhVien")),
                    mauTimKiem,
                    '\\'
            );

            Predicate timTheoGiayPhep = cb.like(
                    cb.lower(root.get("soGiayPhep")),
                    mauTimKiem,
                    '\\'
            );

            danhSachDieuKien.add(
                    cb.or(
                            timTheoTen,
                            timTheoEmail,
                            timTheoBenhVien,
                            timTheoGiayPhep
                    )
            );
        }

        // Lọc theo trạng thái phê duyệt.
        if (pheDuyetChuanHoa != null) {

            danhSachDieuKien.add(
                    cb.equal(
                            cb.upper(root.get("trangThaiPheDuyet")),
                            pheDuyetChuanHoa
                    )
            );
        }

        // Lọc theo trạng thái tài khoản.
        if (taiKhoanChuanHoa != null) {

            danhSachDieuKien.add(
                    cb.equal(
                            cb.upper(root.get("trangThaiTaiKhoan")),
                            taiKhoanChuanHoa
                    )
            );
        }

        // Không có bộ lọc thì lấy toàn bộ bác sĩ.
        return cb.and(
                danhSachDieuKien.toArray(new Predicate[0])
        );
    };

    // Phân trang và sắp xếp ID giảm dần.
    Pageable phanTrang = PageRequest.of(
            trang,
            kichThuoc,
            Sort.by(
                    Sort.Direction.DESC,
                    "maBacSi"
            )
    );

    // Thực thi truy vấn không chứa tham số null.
    Page<Doctor> ketQua =
            khoQuanLyBacSi.findAll(
                    dieuKien,
                    phanTrang
            );

    // Lấy danh sách tài khoản liên kết trong một lần.
    List<Doctor> danhSachBacSi = ketQua.getContent();

    Map<Long, User> nguoiDungTheoMa =
            layNguoiDungLienQuan(danhSachBacSi);

    // Chuyển dữ liệu thành DTO trả về.
    List<DoctorResponse> danhSachPhanHoi =
            danhSachBacSi.stream()
                    .map(bacSi -> chuyenThanhPhanHoi(
                            bacSi,
                            nguoiDungTheoMa
                    ))
                    .toList();

    return new PageImpl<>(
            danhSachPhanHoi,
            phanTrang,
            ketQua.getTotalElements()
    );
}


    /**
     * Lấy thông tin chi tiết bác sĩ theo ID.
     */
    @Transactional(readOnly = true)
    public DoctorResponse layChiTietBacSi(
            Long maBacSi) {

        kiemTraMaBacSi(maBacSi);

        Doctor bacSi = khoQuanLyBacSi
                .findById(maBacSi)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy bác sĩ"
                        )
                );

        User nguoiDung = khoNguoiDung
                .findById(bacSi.getMaNguoiDung())
                .orElse(null);

        String diaChiVi = nguoiDung == null
                ? null
                : nguoiDung.getDiaChiVi();

        return taoPhanHoiBacSi(
                bacSi,
                diaChiVi
        );
    }

    /**
     * Duyệt bác sĩ.
     *
     * Chưa cập nhật database khi chưa có cơ chế
     * xác nhận cấp DOCTOR_ROLE trên Blockchain.
     */
    public DoctorResponse duyetBacSi(
            Long maBacSi) {

        kiemTraMaBacSi(maBacSi);

        throw chuaHoTro(
                "Chưa tích hợp cấp DOCTOR_ROLE trên Blockchain"
        );
    }

    /**
     * Từ chối bác sĩ.
     *
     * Cần lưu lý do và nhật ký trước khi kích hoạt.
     */
    public DoctorResponse tuChoiBacSi(
            Long maBacSi,
            String lyDo) {

        kiemTraMaBacSi(maBacSi);
        kiemTraLyDo(lyDo);

        throw chuaHoTro(
                "Chưa tích hợp nghiệp vụ từ chối và nhật ký quản trị"
        );
    }

    /**
     * Khóa tài khoản bác sĩ.
     *
     * Chưa thực hiện vì trạng thái khóa và cơ chế
     * thu hồi quyền Blockchain chưa được thống nhất.
     */
    public DoctorResponse khoaBacSi(
            Long maBacSi,
            String lyDo) {

        kiemTraMaBacSi(maBacSi);
        kiemTraLyDo(lyDo);

        throw chuaHoTro(
                "Chưa tích hợp khóa tài khoản và thu hồi quyền Blockchain"
        );
    }

    /**
     * Mở khóa tài khoản bác sĩ.
     */
    public DoctorResponse moKhoaBacSi(
            Long maBacSi) {

        kiemTraMaBacSi(maBacSi);

        throw chuaHoTro(
                "Chưa tích hợp mở khóa và đồng bộ quyền Blockchain"
        );
    }

    /**
     * Lấy thông tin tài khoản của các bác sĩ
     * bằng một truy vấn theo danh sách ID.
     *
     * Tránh truy vấn riêng từng người dùng
     * khi trả danh sách bác sĩ.
     */
    private Map<Long, User> layNguoiDungLienQuan(
            List<Doctor> danhSachBacSi) {

        List<Long> danhSachMaNguoiDung =
                danhSachBacSi.stream()
                        .map(Doctor::getMaNguoiDung)
                        .filter(ma -> ma != null)
                        .distinct()
                        .toList();

        if (danhSachMaNguoiDung.isEmpty()) {
            return Map.of();
        }

        List<User> danhSachNguoiDung =
                khoNguoiDung.findAllById(
                        danhSachMaNguoiDung
                );

        return danhSachNguoiDung.stream()
                .collect(Collectors.toMap(
                        User::getMaNguoiDung,
                        Function.identity()
                ));
    }

    /**
     * Chuyển Entity Doctor thành DTO.
     */
    private DoctorResponse chuyenThanhPhanHoi(
            Doctor bacSi,
            Map<Long, User> nguoiDungTheoMa) {

        User nguoiDung = nguoiDungTheoMa.get(
                bacSi.getMaNguoiDung()
        );

        String diaChiVi = nguoiDung == null
                ? null
                : nguoiDung.getDiaChiVi();

        return taoPhanHoiBacSi(
                bacSi,
                diaChiVi
        );
    }

    /**
     * Xây dựng phản hồi bác sĩ.
     *
     * Không trả về mật khẩu hoặc dữ liệu bệnh án.
     */
    private DoctorResponse taoPhanHoiBacSi(
            Doctor bacSi,
            String diaChiVi) {

        // Chưa có Entity chuyên khoa để lấy tên thật.
        String chuyenKhoa =
                "Mã chuyên khoa: "
                        + bacSi.getMaChuyenKhoa();

        return new DoctorResponse(
                bacSi.getMaBacSi(),
                bacSi.getHoTen(),
                bacSi.getEmail(),
                bacSi.getSoGiayPhep(),
                bacSi.getBenhVien(),
                chuyenKhoa,
                diaChiVi,
                bacSi.getTrangThaiTaiKhoan(),
                bacSi.getTrangThaiPheDuyet()
        );
    }

    /**
     * Chuẩn hóa từ khóa tìm kiếm.
     */
    private String chuanHoaChuoi(
            String giaTri) {

        if (giaTri == null || giaTri.isBlank()) {
            return null;
        }

        return giaTri.trim();
    }

    /**
     * Chuẩn hóa trạng thái thành chữ in hoa.
     */
    private String chuanHoaTrangThai(
            String giaTri) {

        String ketQua = chuanHoaChuoi(giaTri);

        return ketQua == null
                ? null
                : ketQua.toUpperCase(Locale.ROOT);
    }

    /**
     * Kiểm tra giá trị trạng thái phê duyệt.
     */
    private void kiemTraTrangThaiPheDuyet(
            String trangThai) {

        if (trangThai == null) {
            return;
        }

        if (!CHO_DUYET.equals(trangThai)
                && !DA_DUYET.equals(trangThai)
                && !TU_CHOI.equals(trangThai)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái phê duyệt không hợp lệ"
            );
        }
    }

    /**
     * Kiểm tra phân trang hợp lệ.
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
     * Kiểm tra mã bác sĩ.
     */
    private void kiemTraMaBacSi(
            Long maBacSi) {

        if (maBacSi == null || maBacSi <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã bác sĩ không hợp lệ"
            );
        }
    }

    /**
     * Kiểm tra lý do xử lý hồ sơ.
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

    /**
     * Thông báo nghiệp vụ chưa được hỗ trợ.
     */
    private ResponseStatusException chuaHoTro(
            String thongBao) {

        return new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                thongBao
        );
    }
}
