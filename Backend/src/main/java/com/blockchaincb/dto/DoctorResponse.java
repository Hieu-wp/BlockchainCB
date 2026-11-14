
package com.blockchaincb.dto;

/**
 * Dữ liệu thông tin bác sĩ trả về cho Admin.
 *
 * Chỉ chứa thông tin phục vụ việc quản lý,
 * không chứa hồ sơ bệnh án của bệnh nhân.
 */
public record DoctorResponse(
    Long maBacSi,
    String hoTen,
    String email,
    String soGiayPhep,
    String noiCongTac,
    String chuyenKhoa,
    String diaChiVi,
    String trangThai
) {}
