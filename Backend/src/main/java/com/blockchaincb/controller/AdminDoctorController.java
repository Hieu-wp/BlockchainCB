
package com.blockchaincb.controller;

import java.util.List;

import com.blockchaincb.dto.DoctorActionRequest;
import com.blockchaincb.dto.DoctorResponse;
import com.blockchaincb.service.AdminDoctorService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý bác sĩ dành riêng cho Admin.
 *
 * Chức năng:
 * - Xem danh sách tất cả bác sĩ.
 * - Xem danh sách bác sĩ đang chờ duyệt.
 * - Tìm kiếm bác sĩ theo thông tin cá nhân.
 * - Lọc theo trạng thái phê duyệt và trạng thái tài khoản.
 * - Phân trang danh sách bác sĩ.
 * - Xem thông tin chi tiết bác sĩ.
 * - Chuẩn bị các API duyệt, từ chối, khóa và mở khóa.
 *
 * Các API được bảo vệ bởi Spring Security.
 * Chỉ tài khoản có ROLE_ADMIN mới được truy cập.
 */
@RestController
@RequestMapping("/api/admin/doctors")
public class AdminDoctorController {

    private final AdminDoctorService dichVuQuanLyBacSi;

    /**
     * Khởi tạo Controller quản lý bác sĩ.
     */
    public AdminDoctorController(
            AdminDoctorService dichVuQuanLyBacSi) {

        this.dichVuQuanLyBacSi = dichVuQuanLyBacSi;
    }

    /**
     * Lấy toàn bộ danh sách bác sĩ.
     *
     * Phương thức: GET
     * Đường dẫn: /api/admin/doctors
     *
     * Chỉ trả về thông tin quản trị cần thiết,
     * không trả về mật khẩu hoặc nội dung bệnh án.
     */
    @GetMapping
    public ResponseEntity<List<DoctorResponse>> layDanhSachBacSi() {

        List<DoctorResponse> danhSachBacSi =
                dichVuQuanLyBacSi.layDanhSachBacSi();

        return ResponseEntity.ok(danhSachBacSi);
    }

    /**
     * Lấy danh sách bác sĩ chờ xét duyệt.
     *
     * Phương thức: GET
     * Đường dẫn: /api/admin/doctors/pending
     *
     * Chỉ lấy bác sĩ có approval_status = PENDING.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<DoctorResponse>> layBacSiChoDuyet() {

        List<DoctorResponse> danhSachBacSi =
                dichVuQuanLyBacSi.layBacSiChoDuyet();

        return ResponseEntity.ok(danhSachBacSi);
    }

    /**
     * Tìm kiếm, lọc và phân trang danh sách bác sĩ.
     *
     * Phương thức: GET
     * Đường dẫn: /api/admin/doctors/search
     *
     * Tham số:
     * - tuKhoa: Họ tên, email, bệnh viện, số giấy phép.
     * - trangThaiPheDuyet: PENDING, APPROVED, REJECTED.
     * - trangThaiTaiKhoan: Trạng thái tài khoản bác sĩ.
     * - trang: Trang hiện tại, bắt đầu từ 0.
     * - kichThuoc: Số bác sĩ mỗi trang, từ 1 đến 100.
     *
     * Các tham số tìm kiếm và lọc là không bắt buộc.
     */
    @GetMapping("/search")
    public ResponseEntity<Page<DoctorResponse>> timKiemBacSi(
            @RequestParam(required = false) String tuKhoa,
            @RequestParam(required = false) String trangThaiPheDuyet,
            @RequestParam(required = false) String trangThaiTaiKhoan,
            @RequestParam(defaultValue = "0") int trang,
            @RequestParam(defaultValue = "10") int kichThuoc) {

        Page<DoctorResponse> ketQua =
                dichVuQuanLyBacSi.timKiemBacSi(
                        tuKhoa,
                        trangThaiPheDuyet,
                        trangThaiTaiKhoan,
                        trang,
                        kichThuoc
                );

        return ResponseEntity.ok(ketQua);
    }

    /**
     * Lấy thông tin chi tiết bác sĩ theo ID.
     *
     * Phương thức: GET
     * Đường dẫn: /api/admin/doctors/{id}
     *
     * Ví dụ: /api/admin/doctors/1
     */
    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponse> layChiTietBacSi(
            @PathVariable("id") Long maBacSi) {

        DoctorResponse bacSi =
                dichVuQuanLyBacSi.layChiTietBacSi(maBacSi);

        return ResponseEntity.ok(bacSi);
    }

    /**
     * Phê duyệt hồ sơ bác sĩ.
     *
     * Phương thức: POST
     * Đường dẫn: /api/admin/doctors/{id}/approve
     *
     * Lưu ý:
     * - Chỉ bác sĩ chờ duyệt mới được phê duyệt.
     * - Cần xác nhận cấp DOCTOR_ROLE trên blockchain.
     * - Service hiện chưa kích hoạt nghiệp vụ này.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<DoctorResponse> duyetBacSi(
            @PathVariable("id") Long maBacSi) {

        DoctorResponse ketQua =
                dichVuQuanLyBacSi.duyetBacSi(maBacSi);

        return ResponseEntity.ok(ketQua);
    }

    /**
     * Từ chối hồ sơ bác sĩ.
     *
     * Phương thức: POST
     * Đường dẫn: /api/admin/doctors/{id}/reject
     *
     * Yêu cầu gửi lý do từ chối trong JSON.
     *
     * Ví dụ:
     * {
     *   "lyDo": "Giấy phép hành nghề chưa hợp lệ"
     * }
     *
     * Chỉ PENDING -> REJECTED; trạng thái và nhật ký cùng transaction.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<DoctorResponse> tuChoiBacSi(
            @PathVariable("id") Long maBacSi,
            @Valid @RequestBody DoctorActionRequest yeuCau) {

        DoctorResponse ketQua =
                dichVuQuanLyBacSi.tuChoiBacSi(
                        maBacSi,
                        yeuCau.lyDo()
                );

        return ResponseEntity.ok(ketQua);
    }

    /**
     * Khóa tài khoản bác sĩ.
     *
     * Phương thức: POST
     * Đường dẫn: /api/admin/doctors/{id}/suspend
     *
     * Yêu cầu lý do khóa tài khoản.
     *
     * Cần đồng bộ trạng thái với quyền blockchain.
     * Service hiện chưa kích hoạt nghiệp vụ này.
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<DoctorResponse> khoaBacSi(
            @PathVariable("id") Long maBacSi,
            @Valid @RequestBody DoctorActionRequest yeuCau) {

        DoctorResponse ketQua =
                dichVuQuanLyBacSi.khoaBacSi(
                        maBacSi,
                        yeuCau.lyDo()
                );

        return ResponseEntity.ok(ketQua);
    }

    /**
     * Mở khóa tài khoản bác sĩ.
     *
     * Phương thức: POST
     * Đường dẫn: /api/admin/doctors/{id}/reactivate
     *
     * Cần kiểm tra trạng thái hiện tại và quyền
     * blockchain trước khi kích hoạt lại tài khoản.
     *
     * Service hiện chưa kích hoạt nghiệp vụ này.
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<DoctorResponse> moKhoaBacSi(
            @PathVariable("id") Long maBacSi) {

        DoctorResponse ketQua =
                dichVuQuanLyBacSi.moKhoaBacSi(maBacSi);

        return ResponseEntity.ok(ketQua);
    }
}
