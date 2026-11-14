
package com.blockchaincb.dto;

import java.util.List;

/**
 * Dữ liệu phản hồi danh sách người dùng có phân trang.
 *
 * @param danhSachNguoiDung Danh sách tài khoản trong trang.
 * @param trangHienTai Chỉ số trang hiện tại.
 * @param kichThuocTrang Số tài khoản tối đa trên một trang.
 * @param tongPhanTu Tổng số tài khoản thỏa điều kiện.
 * @param tongSoTrang Tổng số trang.
 */
public record AdminUserPageResponse(

    List<AdminUserResponse> danhSachNguoiDung,

    int trangHienTai,

    int kichThuocTrang,

    long tongPhanTu,

    int tongSoTrang

) {}
