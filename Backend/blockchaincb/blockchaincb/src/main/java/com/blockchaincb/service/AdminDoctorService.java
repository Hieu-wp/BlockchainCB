
package com.blockchaincb.service;

import com.blockchaincb.dto.DoctorResponse;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service quản lý tài khoản bác sĩ dành cho Admin.
 *
 * Chức năng:
 * - Lấy danh sách bác sĩ.
 * - Lấy danh sách bác sĩ đang chờ duyệt.
 * - Xem thông tin chi tiết bác sĩ.
 * - Phê duyệt hoặc từ chối bác sĩ.
 * - Khóa hoặc mở khóa tài khoản bác sĩ.
 *
 * Lưu ý:
 * - Đây là phiên bản nền tảng, chưa kết nối Database.
 * - Chưa thực hiện các giao dịch Blockchain.
 * - Các chức năng chưa triển khai sẽ trả HTTP 501.
 * - Không giả lập trạng thái ACTIVE khi chưa cấp quyền thành công.
 */
@Service
public class AdminDoctorService {

    /**
     * Lấy danh sách tất cả bác sĩ.
     *
     * @return Danh sách bác sĩ.
     */
    public List<DoctorResponse> layDanhSachBacSi() {

        // Chưa kết nối DoctorRepository.
        // Tạm trả về danh sách rỗng.
        return List.of();
    }

    /**
     * Lấy danh sách bác sĩ đang chờ phê duyệt.
     *
     * @return Danh sách bác sĩ chờ duyệt.
     */
    public List<DoctorResponse> layBacSiChoDuyet() {

        // Chưa có dữ liệu từ cơ sở dữ liệu.
        return List.of();
    }

    /**
     * Lấy thông tin chi tiết của một bác sĩ.
     *
     * @param maBacSi Mã định danh bác sĩ.
     * @return Thông tin chi tiết bác sĩ.
     */
    public DoctorResponse layChiTietBacSi(Long maBacSi) {

        // Chưa thể truy vấn bác sĩ khi chưa có Repository.
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Chưa triển khai truy vấn thông tin bác sĩ"
        );
    }

    /**
     * Phê duyệt tài khoản bác sĩ.
     *
     * Quy trình dự kiến:
     * 1. Kiểm tra bác sĩ tồn tại.
     * 2. Kiểm tra trạng thái PENDING.
     * 3. Xác minh thông tin hành nghề.
     * 4. Gửi giao dịch cấp quyền Doctor lên Blockchain.
     * 5. Xác nhận giao dịch thành công.
     * 6. Cập nhật trạng thái ACTIVE trong Database.
     *
     * @param maBacSi Mã bác sĩ cần phê duyệt.
     * @return Thông tin bác sĩ sau khi phê duyệt.
     */
    public DoctorResponse duyetBacSi(Long maBacSi) {

        // Chưa tích hợp Database và Blockchain.
        // Không được tự chuyển bác sĩ sang ACTIVE.
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Chức năng duyệt bác sĩ chưa được triển khai"
        );
    }

    /**
     * Từ chối yêu cầu đăng ký bác sĩ.
     *
     * Quy trình dự kiến:
     * 1. Kiểm tra bác sĩ tồn tại.
     * 2. Kiểm tra trạng thái PENDING.
     * 3. Kiểm tra lý do từ chối.
     * 4. Cập nhật trạng thái REJECTED.
     * 5. Lưu nhật ký thao tác của Admin.
     *
     * @param maBacSi Mã bác sĩ.
     * @param lyDo Lý do từ chối.
     * @return Thông tin bác sĩ sau khi xử lý.
     */
    public DoctorResponse tuChoiBacSi(
            Long maBacSi,
            String lyDo) {

        // Kiểm tra dữ liệu đầu vào.
        kiemTraLyDo(lyDo);

        // Chưa kết nối cơ sở dữ liệu.
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Chức năng từ chối bác sĩ chưa được triển khai"
        );
    }

    /**
     * Khóa tài khoản bác sĩ.
     *
     * Quy trình dự kiến:
     * 1. Kiểm tra bác sĩ đang hoạt động.
     * 2. Kiểm tra lý do khóa.
     * 3. Vô hiệu hóa quyền Doctor trên Blockchain.
     * 4. Xác nhận giao dịch.
     * 5. Cập nhật trạng thái SUSPENDED.
     * 6. Ghi nhật ký thao tác Admin.
     *
     * @param maBacSi Mã bác sĩ.
     * @param lyDo Lý do khóa tài khoản.
     * @return Thông tin bác sĩ sau khi xử lý.
     */
    public DoctorResponse khoaBacSi(
            Long maBacSi,
            String lyDo) {

        // Kiểm tra lý do khóa tài khoản.
        kiemTraLyDo(lyDo);

        // Chưa tích hợp cơ chế thu hồi quyền.
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Chức năng khóa bác sĩ chưa được triển khai"
        );
    }

    /**
     * Mở khóa tài khoản bác sĩ.
     *
     * Quy trình dự kiến:
     * 1. Kiểm tra bác sĩ đang bị khóa.
     * 2. Kiểm tra điều kiện được hoạt động lại.
     * 3. Cấp lại quyền Doctor trên Blockchain.
     * 4. Xác nhận giao dịch thành công.
     * 5. Cập nhật trạng thái ACTIVE.
     * 6. Lưu nhật ký quản trị.
     *
     * @param maBacSi Mã bác sĩ.
     * @return Thông tin bác sĩ sau khi mở khóa.
     */
    public DoctorResponse moKhoaBacSi(Long maBacSi) {

        // Chưa tích hợp Database và Blockchain.
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Chức năng mở khóa bác sĩ chưa được triển khai"
        );
    }

    /**
     * Kiểm tra lý do từ chối hoặc khóa tài khoản.
     *
     * @param lyDo Nội dung lý do cần kiểm tra.
     */
    private void kiemTraLyDo(String lyDo) {

        if (lyDo == null || lyDo.isBlank()) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Lý do không được để trống"
            );
        }
    }
}
