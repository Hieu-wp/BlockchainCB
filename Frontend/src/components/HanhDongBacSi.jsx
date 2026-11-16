import { useId, useRef, useState } from 'react'
import { HopThoai } from './HopThoai'
import { apiAdmin } from '../services/api'
import { coTheTuChoi, guiTuChoi, loiLyDo } from '../services/doctorRejection'

const hanhDongCho = [
  ['Phê duyệt', 'Chờ tích hợp cấp quyền bác sĩ trên Blockchain.'],
  ['Đình chỉ', 'Chờ thống nhất trạng thái và thu hồi quyền Blockchain.'],
  ['Kích hoạt lại', 'Chờ tích hợp khôi phục và đồng bộ quyền Blockchain.'],
]

export default function HanhDongBacSi({ bacSi, thanhCong }) {
  const [mo, setMo] = useState(false)
  const [lyDo, setLyDo] = useState('')
  const [loi, setLoi] = useState('')
  const [dangGui, setDangGui] = useState(false)
  const [canKiemTra, setCanKiemTra] = useState(false)
  const [trangThaiMoi, setTrangThaiMoi] = useState(null)
  const khoa = useRef(false)
  const nutHuy = useRef(null)
  const ma = useId()
  const hopLe = coTheTuChoi(bacSi) && (!trangThaiMoi || coTheTuChoi(trangThaiMoi))

  async function kiemTra() {
    try {
      const moi = await apiAdmin.chiTietBacSi({ ma: bacSi.maBacSi })
      if (moi?.maBacSi !== bacSi.maBacSi) throw new Error('Dữ liệu hồ sơ không hợp lệ.')
      setTrangThaiMoi(moi)
      setCanKiemTra(false)
      return coTheTuChoi(moi)
        ? 'Hồ sơ hiện vẫn PENDING. Bạn có thể xác nhận lại để gửi một yêu cầu mới.'
        : `Hồ sơ hiện có trạng thái ${moi.trangThaiPheDuyet || 'không xác định'}. Không thể gửi lại yêu cầu từ chối. Đóng hộp thoại và làm mới danh sách.`
    } catch (err) {
      setCanKiemTra(true)
      return `Chưa kiểm tra được trạng thái: ${err.message} Chỉ kiểm tra lại trạng thái trước khi gửi yêu cầu mới.`
    }
  }

  async function xacNhan(e) {
    e.preventDefault()
    if (khoa.current || !hopLe || canKiemTra) return
    const loiNhap = loiLyDo(lyDo)
    if (loiNhap) { setLoi(loiNhap); return }
    khoa.current = true
    setDangGui(true)
    setLoi('')
    try {
      await guiTuChoi(bacSi, lyDo)
      thanhCong()
    } catch (err) {
      const nhan = { 400: 'Lý do không hợp lệ', 403: 'Quyền truy cập hoặc CSRF không hợp lệ', 404: 'Không tìm thấy hồ sơ', 409: 'Hồ sơ đã thay đổi trạng thái' }[err.status]
      if (!err.status || err.status >= 500 || [404, 409].includes(err.status)) {
        setCanKiemTra(true)
        const ketQua = await kiemTra()
        setLoi(`${nhan || 'Chưa xác nhận thao tác thành công'}: ${err.message} ${ketQua}`)
      } else setLoi(`${nhan || 'Không thể từ chối hồ sơ'}: ${err.message}`)
    } finally {
      khoa.current = false
      setDangGui(false)
    }
  }

  async function kiemTraLai() {
    if (khoa.current) return
    khoa.current = true
    setDangGui(true)
    setLoi(await kiemTra())
    khoa.current = false
    setDangGui(false)
  }

  return <section className="doctor-actions" aria-label="Thao tác hồ sơ bác sĩ">
    <h3>Thao tác hồ sơ</h3>
    <p className="field-help">Chỉ hồ sơ PENDING được từ chối. Phê duyệt, đình chỉ và kích hoạt lại đang chờ tích hợp Blockchain.</p>
    <div className="doctor-action-grid">
      <div><button type="button" className="btn btn-small btn-outline" disabled={!hopLe} onClick={() => setMo(true)}>Từ chối</button><small>Cần lý do và xác nhận; trạng thái được kiểm tra bởi Backend.</small></div>
      {hanhDongCho.map(([nhan, moTa]) => <div key={nhan}><button type="button" className="btn btn-small btn-outline" disabled title={moTa}>{nhan}</button><small>{moTa}</small></div>)}
    </div>
    {mo && <HopThoai tieuDe="Xác nhận từ chối bác sĩ" dong={() => setMo(false)} dangGui={dangGui} tieuDiemBanDau={nutHuy} maMoTa={`${ma}-mo-ta`} lopBoSung="doctor-reject">
      <p id={`${ma}-mo-ta`}>Bạn có chắc chắn muốn từ chối hồ sơ đăng ký của bác sĩ {bacSi.hoTen} không?</p>
      <form onSubmit={xacNhan}>
        <label className="modal-field" htmlFor={ma}>Lý do từ chối</label>
        <textarea id={ma} rows={4} placeholder="Nhập lý do từ chối hồ sơ đăng ký..." value={lyDo} disabled={dangGui} aria-invalid={!!loiLyDo(lyDo)} aria-describedby={`${ma}-dem ${ma}-loi`} onChange={e => { setLyDo(e.target.value); if (!canKiemTra) setLoi('') }}/>
        <small id={`${ma}-dem`}>{lyDo.length}/350 ký tự</small>
        <div id={`${ma}-loi`}>{loi && <p className="login-error" role="alert">{loi}</p>}</div>
        {canKiemTra && <button type="button" className="btn btn-outline" disabled={dangGui} onClick={kiemTraLai}>Kiểm tra lại trạng thái</button>}
        <div className="modal-actions"><button ref={nutHuy} type="button" className="btn btn-outline" disabled={dangGui} onClick={() => setMo(false)}>Hủy</button><button type="submit" className="btn btn-primary" disabled={dangGui || canKiemTra || !hopLe}>{dangGui ? 'Đang xử lý...' : 'Xác nhận từ chối'}</button></div>
      </form>
    </HopThoai>}
  </section>
}
