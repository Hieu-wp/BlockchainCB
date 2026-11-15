package com.blockchaincb.dto;

/** Thông tin công khai cho Admin; không trả mật khẩu hoặc bệnh án. */
public record DoctorResponse(
    Long maBacSi,
    String hoTen,
    String email,
    String soGiayPhep,
    String noiCongTac,
    String chuyenKhoa,
    String diaChiVi,
    String trangThai,
    String trangThaiPheDuyet
) {}
