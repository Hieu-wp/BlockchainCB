import { useEffect, useState } from 'react'
import { Search, Info } from 'lucide-react'
import { apiAdmin } from '../services/api'
import { TieuDe, TrangThai, DangTai, ThongBaoLoi, Rong, PhanTrang, LamMoi } from '../components/Shared'

export default function Doctors() {
  const [tuKhoa, setTuKhoa] = useState('')
  const [duyet, setDuyet] = useState('')
  const [boLoc, setBoLoc] = useState({})
  const [trang, setTrang] = useState(0)
  const [lan, setLan] = useState(0)
  const [duLieu, setDuLieu] = useState(null)
  const [loi, setLoi] = useState(null)
  const [dangTai, setDangTai] = useState(true)
  useEffect(() => {
    let hieuLuc = true
    apiAdmin.bacSi({ ...boLoc, trang, kichThuoc: 10 })
      .then(data => { if(hieuLuc) setDuLieu(data) }).catch(err => { if(hieuLuc) setLoi(err) }).finally(() => { if(hieuLuc) setDangTai(false) })
    return () => { hieuLuc = false }
  }, [boLoc, trang, lan])
  const taiLai = () => { setLoi(null); setDangTai(true); setLan(x => x + 1) }
  const timKiem = e => { e.preventDefault(); setTrang(0); setBoLoc({ tuKhoa: tuKhoa.trim(), trangThaiPheDuyet: duyet }) }
  const danhSach = duLieu?.content || []
  return <><TieuDe nhan="QUẢN LÝ NHÂN SỰ Y TẾ" tieuDe="Danh sách bác sĩ" moTa="Theo dõi hồ sơ đăng ký và trạng thái phê duyệt của bác sĩ."><LamMoi onClick={taiLai}/></TieuDe>
    <div className="info-banner"><Info size={19}/><div><strong>Chế độ xem và tra cứu</strong><span>Backend hiện chưa hoàn thiện phê duyệt/từ chối và đồng bộ Blockchain, vì vậy giao diện chưa bật các nút thay đổi trạng thái bác sĩ.</span></div></div>
    {loi && <ThongBaoLoi loi={loi} onRetry={taiLai}/>}
    <section className="panel"><div className="panel-head"><h2>Tìm kiếm hồ sơ bác sĩ</h2></div><div className="panel-body"><form className="filter-grid doctors-filter" onSubmit={timKiem}><label><span>Tên, email hoặc mã chứng chỉ</span><div className="input-icon"><Search size={16}/><input placeholder="Tìm kiếm bác sĩ..." value={tuKhoa} onChange={e=>setTuKhoa(e.target.value)}/></div></label><label><span>Trạng thái phê duyệt</span><select value={duyet} onChange={e=>setDuyet(e.target.value)}><option value="">Tất cả</option><option value="PENDING">Chờ duyệt</option><option value="APPROVED">Đã duyệt</option><option value="REJECTED">Từ chối</option></select></label><button className="btn btn-primary" type="submit"><Search size={16}/> Tìm kiếm</button></form></div></section>
    <section className="panel"><div className="panel-head"><h2>Hồ sơ bác sĩ</h2><span className="muted small">{duLieu?.totalElements ?? 0} hồ sơ</span></div>{dangTai ? <DangTai/> : <><div className="table-wrap"><table className="medical-table"><thead><tr><th>Mã hồ sơ</th><th>Bác sĩ</th><th>Chuyên khoa</th><th>Nơi công tác</th><th>Giấy phép hành nghề</th><th>Phê duyệt</th></tr></thead><tbody>{danhSach.map((bs, index) => <tr key={bs.maBacSi ?? bs.id ?? index}><td className="id-text">#{bs.maBacSi ?? bs.id ?? '—'}</td><td><div className="person-cell"><span className="avatar">{(bs.hoTen || bs.fullName || 'B').charAt(0)}</span><div><b>{bs.hoTen || bs.fullName || 'Chưa có tên'}</b><small>{bs.email || ''}</small></div></div></td><td>{bs.chuyenKhoa || bs.tenChuyenKhoa || bs.specialtyName || '—'}</td><td>{bs.benhVien || bs.noiCongTac || bs.hospital || '—'}</td><td>{bs.soChungChi || bs.licenseNumber || bs.licenseNo || '—'}</td><td><TrangThai giaTri={bs.trangThaiPheDuyet || bs.approvalStatus}/></td></tr>)}</tbody></table>{!danhSach.length && <Rong noiDung="Chưa có hồ sơ bác sĩ khớp với bộ lọc."/>}</div><PhanTrang trang={trang} tongTrang={duLieu?.totalPages} tongPhanTu={duLieu?.totalElements} setTrang={setTrang}/></>}</section>
  </>
}
