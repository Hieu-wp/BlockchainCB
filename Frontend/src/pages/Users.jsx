import { useEffect, useState } from 'react'
import { Search, Lock, Unlock, X } from 'lucide-react'
import { apiAdmin } from '../services/api'
import { TieuDe, Hop, TrangThai, DangTai, ThongBaoLoi, Rong, PhanTrang, LamMoi, dinhDangNgay } from '../components/Shared'

export default function Users() {
  const [tuKhoa, setTuKhoa] = useState('')
  const [boLoc, setBoLoc] = useState({ tuKhoa: '', vaiTro: '', trangThai: '' })
  const [vaiTro, setVaiTro] = useState('')
  const [trangThai, setTrangThai] = useState('')
  const [trang, setTrang] = useState(0)
  const [lan, setLan] = useState(0)
  const [duLieu, setDuLieu] = useState(null)
  const [dangTai, setDangTai] = useState(true)
  const [loi, setLoi] = useState(null)
  const [chon, setChon] = useState(null)
  const [lyDo, setLyDo] = useState('')
  const [dangGui, setDangGui] = useState(false)
  const [thongBao, setThongBao] = useState('')
  useEffect(() => {
    let conHieuLuc = true
    apiAdmin.nguoiDung({ ...boLoc, trang, kichThuoc: 10 })
      .then(data => { if(conHieuLuc) setDuLieu(data) }).catch(err => { if(conHieuLuc) setLoi(err) }).finally(() => { if(conHieuLuc) setDangTai(false) })
    return () => { conHieuLuc = false }
  }, [boLoc, trang, lan])
  const taiLai = () => { setLoi(null); setDangTai(true); setLan(x => x + 1) }
  const timKiem = e => { e.preventDefault(); setTrang(0); setBoLoc({ tuKhoa: tuKhoa.trim(), vaiTro, trangThai }) }
  const xuLyTrangThai = async () => {
    if (!chon) return
    setDangGui(true); setLoi(null); setThongBao('')
    try {
      if(chon.trangThai === 'ACTIVE') await apiAdmin.khoa(chon.maNguoiDung, lyDo.trim())
      else await apiAdmin.moKhoa(chon.maNguoiDung)
      setThongBao(`Đã ${chon.trangThai === 'ACTIVE' ? 'khóa' : 'mở khóa'} tài khoản #${chon.maNguoiDung}.`)
      setChon(null); setLyDo(''); taiLai()
    } catch (err) { setLoi(err); setChon(null) } finally { setDangGui(false) }
  }
  return <><TieuDe nhan="QUẢN LÝ TÀI KHOẢN" tieuDe="Danh sách người dùng" moTa="Tra cứu, lọc và quản lý trạng thái tài khoản trong hệ thống."><LamMoi onClick={taiLai}/></TieuDe>
    {thongBao && <div className="success-box" role="status">{thongBao}</div>}
    {loi && <ThongBaoLoi loi={loi} onRetry={taiLai}/>}
    <Hop tieuDe="Bộ lọc người dùng"><form className="filter-grid" onSubmit={timKiem}><label><span>Tìm theo tên hoặc email</span><div className="input-icon"><Search size={16}/><input value={tuKhoa} onChange={e => setTuKhoa(e.target.value)} placeholder="Nhập tên hoặc email..." /></div></label><label><span>Vai trò</span><select value={vaiTro} onChange={e => setVaiTro(e.target.value)}><option value="">Tất cả vai trò</option><option value="ADMIN">Admin</option><option value="DOCTOR">Bác sĩ</option><option value="PATIENT">Bệnh nhân</option></select></label><label><span>Trạng thái</span><select value={trangThai} onChange={e => setTrangThai(e.target.value)}><option value="">Tất cả trạng thái</option><option value="ACTIVE">Hoạt động</option><option value="INACTIVE">Đã khóa</option></select></label><button className="btn btn-primary" type="submit"><Search size={16}/> Tìm kiếm</button></form></Hop>
    <section className="panel"><div className="panel-head"><h2>Danh sách tài khoản</h2><span className="muted small">{duLieu?.tongPhanTu ?? 0} người dùng</span></div>{dangTai ? <DangTai/> : <><div className="table-wrap"><table className="medical-table"><thead><tr><th>Mã ID</th><th>Người dùng</th><th>Vai trò</th><th>Ngày tạo</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{(duLieu?.danhSachNguoiDung || []).map(nguoi => <tr key={nguoi.maNguoiDung}><td className="id-text">#{nguoi.maNguoiDung}</td><td><div className="person-cell"><span className="avatar">{nguoi.hoTen?.slice(0, 1) || '?'}</span><div><b>{nguoi.hoTen}</b><small>{nguoi.email}</small></div></div></td><td><span className="role-tag">{nguoi.vaiTro}</span></td><td>{dinhDangNgay(nguoi.ngayTao)}</td><td><TrangThai giaTri={nguoi.trangThai}/></td><td><button className="btn btn-small btn-outline" disabled={nguoi.vaiTro !== 'PATIENT'} title={nguoi.vaiTro !== 'PATIENT' ? 'Chỉ hỗ trợ khóa/mở khóa PATIENT' : ''} onClick={() => setChon(nguoi)}>{nguoi.trangThai === 'ACTIVE' ? <Lock size={14}/> : <Unlock size={14}/>} {nguoi.trangThai === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</button></td></tr>)}</tbody></table>{!duLieu?.danhSachNguoiDung?.length && <Rong noiDung="Không tìm thấy người dùng phù hợp."/>}</div><PhanTrang trang={trang} tongTrang={duLieu?.tongSoTrang} tongPhanTu={duLieu?.tongPhanTu} setTrang={setTrang}/></>}</section>
    {chon && <div className="modal-backdrop" onMouseDown={e => { if(e.target === e.currentTarget) setChon(null) }}><div className="modal" role="dialog" aria-modal="true" aria-label="Xác nhận thay đổi tài khoản"><div className="modal-header"><h2>{chon.trangThai === 'ACTIVE' ? 'Xác nhận khóa tài khoản' : 'Xác nhận mở khóa'}</h2><button className="icon-button" onClick={() => setChon(null)}><X size={20}/></button></div><p>Bạn sắp {chon.trangThai === 'ACTIVE' ? 'khóa' : 'mở khóa'} <strong>{chon.hoTen}</strong> (#{chon.maNguoiDung}).</p>{chon.trangThai === 'ACTIVE' && <label className="modal-field"><span>Lý do khóa (bắt buộc)</span><textarea value={lyDo} onChange={e=>setLyDo(e.target.value)} rows={3} maxLength={350} placeholder="Nhập lý do khóa tài khoản"/></label>}<div className="modal-actions"><button className="btn btn-outline" onClick={() => setChon(null)}>Hủy</button><button className="btn btn-primary" disabled={dangGui || (chon.trangThai === 'ACTIVE' && !lyDo.trim())} onClick={xuLyTrangThai}>{dangGui ? 'Đang xử lý...' : 'Xác nhận'}</button></div></div></div>}
  </>
}
