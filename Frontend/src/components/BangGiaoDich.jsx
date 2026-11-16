import { ChevronLeft, ChevronRight } from 'lucide-react'
import { DangTai, ThongBaoLoi, PhanTrang } from './Shared'

// Khung hiển thị độc lập API. Chỉ gắn dòng dữ liệu sau khi nhóm xác nhận DTO thật.
export default function BangGiaoDich({ dangTai = false, loi = null, thuLai, children, phanTrang, nhanNguon = 'Chưa có nguồn dữ liệu' }) {
  return <section className="panel" aria-label="Danh sách giao dịch">
    <div className="panel-head"><h2>Giao dịch Blockchain</h2><span className="pill neutral">{nhanNguon}</span></div>
    {dangTai ? <DangTai/> : loi ? <div className="panel-body"><ThongBaoLoi loi={loi} onRetry={thuLai}/></div> :
      <><div className="table-wrap" tabIndex={0} role="region" aria-label="Bảng giao dịch, cuộn ngang để xem đầy đủ">
        <table className="medical-table"><thead><tr>
          <th scope="col">Transaction hash</th><th scope="col">Loại giao dịch</th>
          <th scope="col">Thời gian gửi</th><th scope="col">Block number</th>
          <th scope="col">Trạng thái</th><th scope="col">Explorer</th>
        </tr></thead><tbody>{children}</tbody></table>
      </div>{!children && <div className="empty transaction-empty">
        <p>Dữ liệu giao dịch chưa được cung cấp.</p><small>Bảng sẽ hiển thị giao dịch từ API khi tích hợp hoàn tất.</small>
      </div>}</>}
    {phanTrang && !dangTai && !loi ? <PhanTrang {...phanTrang}/> : <div className="pagination"><span>Chưa có dữ liệu phân trang</span><div className="pagination-buttons">
      <button type="button" disabled aria-label="Trang giao dịch trước"><ChevronLeft size={17}/></button>
      <span>Chưa khả dụng</span><button type="button" disabled aria-label="Trang giao dịch tiếp"><ChevronRight size={17}/></button>
    </div></div>}
  </section>
}
