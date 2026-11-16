import { ChiTiet } from '../components/HopThoai'
import HanhDongBacSi from '../components/HanhDongBacSi'
import { useDuLieu } from '../hooks/useDuLieu'
import { useState } from 'react'
import { Search, Info } from 'lucide-react'
import { apiAdmin } from '../services/api'
import { taiTrangBacSi } from '../services/doctorRejection'
import { TieuDe, TrangThai, DangTai, ThongBaoLoi, Rong, PhanTrang, LamMoi } from '../components/Shared'

const truongBacSi = [['Mã bác sĩ', 'maBacSi'], ['Họ tên', 'hoTen'], ['Email', 'email'], ['Giấy phép hành nghề', 'soGiayPhep'], ['Nơi công tác', 'noiCongTac'], ['Chuyên khoa', 'chuyenKhoa'], ['Địa chỉ ví', 'diaChiVi'], ['Trạng thái tài khoản', 'trangThai'], ['Phê duyệt', 'trangThaiPheDuyet']]

export default function Doctors() {
  const [daTuChoi, setDaTuChoi] = useState(false)
  const [chiTiet, setChiTiet] = useState(null)
  const [tuKhoa, setTuKhoa] = useState('')
  const [duyet, setDuyet] = useState('')
  const [boLoc, setBoLoc] = useState({})
  const [trang, setTrang] = useState(0)
  const { duLieu, loi, dangTai, taiLai } = useDuLieu(taiTrangBacSi, { ...boLoc, trang, kichThuoc: 10 })
  const timKiem = e => { e.preventDefault(); setTrang(0); setBoLoc({ tuKhoa: tuKhoa.trim(), trangThaiPheDuyet: duyet }) }
  const danhSach = duLieu?.content || []
  const chonNhom = trangThai => {
    setDuyet(trangThai)
    setTrang(0)
    setBoLoc(hienTai => ({ ...hienTai, trangThaiPheDuyet: trangThai }))
  }
  return <><TieuDe nhan="QUẢN LÝ NHÂN SỰ Y TẾ" tieuDe="Danh sách bác sĩ" moTa="Theo dõi hồ sơ đăng ký và trạng thái phê duyệt của bác sĩ."><LamMoi onClick={taiLai}/></TieuDe>
    <div className="info-banner"><Info size={19}/><div><strong>Quản lý hồ sơ đăng ký</strong><span>Có thể từ chối hồ sơ PENDING kèm lý do. Phê duyệt, đình chỉ và kích hoạt lại đang chờ tích hợp Blockchain.</span></div></div>
    {daTuChoi && <p role="status" className="info-banner">{loi ? 'Đã từ chối hồ sơ thành công nhưng chưa làm mới được danh sách. Hãy nhấn thử lại; không cần gửi lại yêu cầu từ chối.' : dangTai ? 'Đã từ chối hồ sơ thành công. Đang làm mới danh sách...' : 'Đã từ chối hồ sơ thành công; danh sách đã được tải lại từ Backend.'}</p>}
    {loi && <ThongBaoLoi loi={loi} onRetry={taiLai}/>}
    <div className="doctor-groups" role="group" aria-label="Lọc nhanh trạng thái bác sĩ">
      {[['', 'Tất cả'], ['PENDING', 'Chờ duyệt'], ['APPROVED', 'Đã duyệt'], ['REJECTED', 'Từ chối']].map(([giaTri, nhan]) => <button type="button" key={giaTri} className="doctor-group" aria-pressed={(boLoc.trangThaiPheDuyet || '') === giaTri} onClick={() => chonNhom(giaTri)}>{nhan}</button>)}
    </div>
    <section className="panel"><div className="panel-head"><h2>Tìm kiếm hồ sơ bác sĩ</h2></div><div className="panel-body"><form className="filter-grid doctors-filter" onSubmit={timKiem}><label><span>Tên, email hoặc mã chứng chỉ</span><div className="input-icon"><Search size={16}/><input placeholder="Tìm kiếm bác sĩ..." value={tuKhoa} onChange={e=>setTuKhoa(e.target.value)}/></div></label><label><span>Trạng thái phê duyệt</span><select value={duyet} onChange={e=>setDuyet(e.target.value)}><option value="">Tất cả</option><option value="PENDING">Chờ duyệt</option><option value="APPROVED">Đã duyệt</option><option value="REJECTED">Từ chối</option></select></label><button className="btn btn-primary" type="submit"><Search size={16}/> Tìm kiếm</button></form></div></section>
    <section className="panel"><div className="panel-head"><h2>Hồ sơ bác sĩ</h2><span className="muted small">{duLieu?.totalElements ?? 0} hồ sơ</span></div>{dangTai ? <DangTai noiDung="Đang tải dữ liệu"/> : !loi && <><div className="table-wrap"><table className="medical-table"><thead><tr><th>Mã hồ sơ</th><th>Bác sĩ</th><th>Chuyên khoa</th><th>Nơi công tác</th><th>Giấy phép hành nghề</th><th>Phê duyệt</th><th>Thao tác</th></tr></thead><tbody>{danhSach.map((bs) => <tr key={bs.maBacSi}><td className="id-text">#{bs.maBacSi}</td><td><div className="person-cell"><span className="avatar">{(bs.hoTen || 'B').charAt(0)}</span><div><b>{bs.hoTen || 'Chưa có tên'}</b><small>{bs.email || ''}</small></div></div></td><td>{bs.chuyenKhoa || '—'}</td><td>{bs.noiCongTac || '—'}</td><td>{bs.soGiayPhep || '—'}</td><td><TrangThai giaTri={bs.trangThaiPheDuyet}/></td><td><button className="btn btn-small btn-outline" onClick={() => setChiTiet(bs.maBacSi)}>Chi tiết</button></td></tr>)}</tbody></table>{!danhSach.length && <Rong noiDung="Chưa có hồ sơ bác sĩ khớp với bộ lọc."/>}</div><PhanTrang trang={duLieu?.trangHienTai ?? trang} tongTrang={duLieu?.totalPages} tongPhanTu={duLieu?.totalElements} setTrang={setTrang}/></>}</section>
    {chiTiet !== null && <ChiTiet noiDungDangTai="Đang tải dữ liệu" tieuDe="Chi tiết bác sĩ" taiDuLieu={apiAdmin.chiTietBacSi} ma={chiTiet} truong={truongBacSi} dong={() => setChiTiet(null)}>
      {({ duLieu: bacSi, loi: loiChiTiet, dangTai: dangTaiChiTiet }) => <HanhDongBacSi bacSi={!loiChiTiet && !dangTaiChiTiet ? bacSi : null} thanhCong={() => { setChiTiet(null); setDaTuChoi(true); taiLai() }}/> }
    </ChiTiet>}
  </>
}
