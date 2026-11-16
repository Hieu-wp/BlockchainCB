const danhSachHanhDong = [
  ['Phê duyệt', 'Chờ tích hợp cấp quyền bác sĩ trên Blockchain.'],
  ['Từ chối', 'Chờ hoàn thiện lưu lý do và nhật ký quản trị.'],
  ['Đình chỉ', 'Chờ thống nhất trạng thái và thu hồi quyền Blockchain.'],
  ['Kích hoạt lại', 'Chờ tích hợp khôi phục và đồng bộ quyền Blockchain.'],
]

export default function HanhDongBacSi() {
  return <section className="doctor-actions" aria-label="Thao tác hồ sơ bác sĩ">
    <h3>Thao tác hồ sơ</h3><span className="pill warning">Chờ Backend/Blockchain</span>
    <p className="field-help">Các thao tác chưa khả dụng. Hồ sơ chỉ được xem, chưa thể thay đổi trạng thái.</p>
    <div className="doctor-action-grid">{danhSachHanhDong.map(([nhan, lyDo]) => <div key={nhan}>
      <button type="button" className="btn btn-small btn-outline" disabled title={lyDo}>{nhan}</button><small>{lyDo}</small>
    </div>)}</div>
  </section>
}
