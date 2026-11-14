
package com.blockchaincb.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity đại diện cho tài khoản quản trị viên.
 *
 * Chức năng:
 * - Lưu thông tin đăng nhập của Admin.
 * - Lưu mật khẩu đã được băm bằng BCrypt.
 * - Quản lý trạng thái hoạt động của tài khoản.
 * - Lưu địa chỉ ví blockchain nếu Admin sử dụng MetaMask.
 * - Theo dõi thời gian tạo và lần đăng nhập gần nhất.
 *
 * Lưu ý:
 * - Không lưu mật khẩu dạng văn bản thuần.
 * - Không lưu private key hoặc seed phrase của MetaMask.
 * - Tài khoản Admin không được đăng ký công khai.
 */
@Entity
@Table(name = "admins")
public class Admin {

    /**
     * Mã định danh tài khoản Admin.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long maAdmin;

    /**
     * Tên đăng nhập Admin, không được trùng lặp.
     */
    @Column(name = "ten_dang_nhap",
            nullable = false,
            unique = true,
            length = 100)
    private String tenDangNhap;

    /**
     * Mật khẩu đã được băm bằng BCrypt.
     */
    @Column(name = "mat_khau",
            nullable = false,
            length = 255)
    private String matKhau;

    /**
     * Họ tên người quản trị.
     */
    @Column(name = "ho_ten", length = 150)
    private String hoTen;

    /**
     * Email của quản trị viên.
     */
    @Column(name = "email", unique = true, length = 150)
    private String email;

    /**
     * Địa chỉ ví MetaMask của quản trị viên.
     */
    @Column(name = "dia_chi_vi", unique = true, length = 42)
    private String diaChiVi;

    /**
     * Vai trò của tài khoản.
     * Hiện tại sử dụng ADMIN.
     */
    @Column(name = "vai_tro",
            nullable = false,
            length = 30)
    private String vaiTro = "ADMIN";

    /**
     * Trạng thái hoạt động của tài khoản.
     * true: Được phép đăng nhập.
     * false: Tài khoản bị vô hiệu hóa.
     */
    @Column(name = "dang_hoat_dong", nullable = false)
    private boolean dangHoatDong = true;

    /**
     * Thời gian tạo tài khoản.
     */
    @Column(name = "ngay_tao",
            nullable = false,
            updatable = false)
    private LocalDateTime ngayTao;

    /**
     * Thời gian đăng nhập gần nhất.
     */
    @Column(name = "lan_dang_nhap_cuoi")
    private LocalDateTime lanDangNhapCuoi;

    /**
     * Constructor mặc định dành cho JPA.
     */
    public Admin() {
    }

    /**
     * Tự động ghi thời gian tạo tài khoản trước khi lưu mới.
     */
    @jakarta.persistence.PrePersist
    public void truocKhiLuu() {
        if (ngayTao == null) {
            ngayTao = LocalDateTime.now();
        }
    }

    // ============================
    // GETTER VÀ SETTER
    // ============================

    public Long getMaAdmin() {
        return maAdmin;
    }

    public void setMaAdmin(Long maAdmin) {
        this.maAdmin = maAdmin;
    }

    public String getTenDangNhap() {
        return tenDangNhap;
    }

    public void setTenDangNhap(String tenDangNhap) {
        this.tenDangNhap = tenDangNhap;
    }

    public String getMatKhau() {
        return matKhau;
    }

    public void setMatKhau(String matKhau) {
        this.matKhau = matKhau;
    }

    public String getHoTen() {
        return hoTen;
    }

    public void setHoTen(String hoTen) {
        this.hoTen = hoTen;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDiaChiVi() {
        return diaChiVi;
    }

    public void setDiaChiVi(String diaChiVi) {
        this.diaChiVi = diaChiVi;
    }

    public String getVaiTro() {
        return vaiTro;
    }

    public void setVaiTro(String vaiTro) {
        this.vaiTro = vaiTro;
    }

    public boolean isDangHoatDong() {
        return dangHoatDong;
    }

    public void setDangHoatDong(boolean dangHoatDong) {
        this.dangHoatDong = dangHoatDong;
    }

    public LocalDateTime getNgayTao() {
        return ngayTao;
    }

    public void setNgayTao(LocalDateTime ngayTao) {
        this.ngayTao = ngayTao;
    }

    public LocalDateTime getLanDangNhapCuoi() {
        return lanDangNhapCuoi;
    }

    public void setLanDangNhapCuoi(LocalDateTime lanDangNhapCuoi) {
        this.lanDangNhapCuoi = lanDangNhapCuoi;
    }
}
