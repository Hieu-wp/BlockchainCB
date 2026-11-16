import { useEffect, useState } from 'react'
import { ShieldCheck } from 'lucide-react'
import { apiAdmin } from '../services/api'
import { TieuDe, TrangThai, DangTai, ThongBaoLoi, Rong, PhanTrang, LamMoi, dinhDangNgay } from '../components/Shared'

export default function AuditLogs() {
  const [trang, setTrang] = useState(0)
  const [lan, setLan] = useState(0)
  const [duLieu, setDuLieu] = useState(null)
  const [loi, setLoi] = useState(null)
  const [dangTai, setDangTai] = useState(true)
  useEffect(() => {
    let hieuLuc = true
    apiAdmin.nhatKy({ trang, kichThuoc: 10 })
      .then(data => { if(hieuLuc) setDuLieu(data) }).catch(err => { if(hieuLuc) setLoi(err) }).finally(() => { if(hieuLuc) setDangTai(false) })
    return () => { hieuLuc = false }
  }, [trang, lan])
  const taiLai = () => { setDangTai(true); setLoi(null); setLan(x => x + 1) }
  return <><TieuDe nhan="BẢO MẬT VÀ TRUY VẾT" tieuDe="Nhật ký quản trị" moTa="Theo dõi hoạt động quản lý tài khoản và các thao tác của Admin."><LamMoi onClick={taiLai}/></TieuDe>
    {loi && <ThongBaoLoi loi={loi} onRetry={taiLai}/>}
    <section className="panel"><div className="panel-head"><h2><ShieldCheck size={18}/> Lịch sử thao tác</h2><span className="muted small">{duLieu?.tongPhanTu ?? 0} bản ghi</span></div>{dangTai ? <DangTai/> : <><div className="table-wrap"><table className="medical-table"><thead><tr><th>Mã nhật ký</th><th>Thời gian</th><th>Admin</th><th>Hành động</th><th>Đối tượng</th><th>Mô tả</th></tr></thead><tbody>{(duLieu?.danhSachNhatKy || []).map(log => <tr key={log.maNhatKy}><td className="id-text">#{log.maNhatKy}</td><td className="nowrap">{dinhDangNgay(log.thoiGian)}</td><td>#{log.maAdmin}</td><td><TrangThai giaTri={log.hanhDong}/></td><td>{log.loaiDoiTuong} #{log.maDoiTuong}</td><td className="description-col">{log.moTa}</td></tr>)}</tbody></table>{!duLieu?.danhSachNhatKy?.length && <Rong noiDung="Chưa có nhật ký quản trị."/>}</div><PhanTrang trang={trang} tongTrang={duLieu?.tongSoTrang} tongPhanTu={duLieu?.tongPhanTu} setTrang={setTrang}/></>}</section>
  </>
}
