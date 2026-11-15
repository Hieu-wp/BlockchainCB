package com.blockchaincb.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tài khoản dùng chung cho ADMIN, DOCTOR và PATIENT trong public.users. */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long maNguoiDung;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String matKhauMaHoa;

    @Column(name = "full_name", nullable = false)
    private String hoTen;

    @Column(name = "role", nullable = false)
    private String vaiTro;

    @Column(name = "wallet_address")
    private String diaChiVi;

    @Column(name = "enabled", nullable = false)
    private boolean dangHoatDong;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime ngayTao;

    public Long getMaNguoiDung() { return maNguoiDung; }
    public void setMaNguoiDung(Long maNguoiDung) { this.maNguoiDung = maNguoiDung; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMatKhauMaHoa() { return matKhauMaHoa; }
    public void setMatKhauMaHoa(String matKhauMaHoa) { this.matKhauMaHoa = matKhauMaHoa; }
    public String getHoTen() { return hoTen; }
    public void setHoTen(String hoTen) { this.hoTen = hoTen; }
    public String getVaiTro() { return vaiTro; }
    public void setVaiTro(String vaiTro) { this.vaiTro = vaiTro; }
    public String getDiaChiVi() { return diaChiVi; }
    public void setDiaChiVi(String diaChiVi) { this.diaChiVi = diaChiVi; }
    public boolean isDangHoatDong() { return dangHoatDong; }
    public void setDangHoatDong(boolean dangHoatDong) { this.dangHoatDong = dangHoatDong; }
    public LocalDateTime getNgayTao() { return ngayTao; }
    public void setNgayTao(LocalDateTime ngayTao) { this.ngayTao = ngayTao; }
}
