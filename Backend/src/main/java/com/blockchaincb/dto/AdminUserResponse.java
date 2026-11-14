
package com.blockchaincb.dto;

import java.time.LocalDateTime;

/**
 * Dữ liệu thông tin tài khoản người dùng trả về cho Admin.
 *
 * Chỉ chứa thông tin phục vụ quản lý tài khoản.
 * Không chứa mật khẩu, mã băm mật khẩu hoặc bệnh án.
 */
public record AdminUserResponse(

    Long maNguoiDung,

    String hoTen,

    String email,

    String vaiTro,

    String trangThai,

    String diaChiVi,

    LocalDateTime ngayTao

) {}
