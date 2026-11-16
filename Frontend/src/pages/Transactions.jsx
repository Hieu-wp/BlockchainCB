import { Blocks, Info, Search, ExternalLink } from 'lucide-react'
import { TieuDe, Hop } from '../components/Shared'
import BangGiaoDich from '../components/BangGiaoDich'

export default function Transactions() {
  return <>
    <TieuDe nhan="THEO DÕI BLOCKCHAIN" tieuDe="Quản lý giao dịch Blockchain" moTa="Tra cứu và theo dõi giao dịch khi nguồn dữ liệu Blockchain được tích hợp."/>
    <section className="integration-card" aria-labelledby="tich-hop-giao-dich">
      <span className="integration-icon"><Blocks size={30}/></span>
      <div><span className="pill warning">Chờ tích hợp Blockchain</span>
        <h2 id="tich-hop-giao-dich">Nguồn dữ liệu giao dịch chưa sẵn sàng</h2>
        <p>Chức năng đang chờ tích hợp Blockchain. Dữ liệu giao dịch sẽ được hiển thị khi Backend cung cấp API.</p>
      </div>
    </section>
    <Hop tieuDe="Tra cứu giao dịch" phuDe="Bộ lọc chưa khả dụng">
      <fieldset className="transaction-filters" disabled aria-describedby="huong-dan-giao-dich">
        <label><span>Transaction hash</span><div className="input-icon"><Search size={16}/>
          <input type="search" placeholder="Tìm theo transaction hash"/>
        </div></label>
        <label><span>Trạng thái giao dịch</span><select defaultValue=""><option value="">Tất cả trạng thái</option>
          <option value="PENDING">Chờ xác nhận (PENDING)</option><option value="CONFIRMED">Đã xác nhận (CONFIRMED)</option><option value="FAILED">Thất bại (FAILED)</option>
        </select></label>
        <button type="button" className="btn btn-primary"><Search size={16}/> Tra cứu</button>
      </fieldset>
      <p id="huong-dan-giao-dich" className="field-help">Tìm kiếm và lọc sẽ được bật khi có API giao dịch.</p>
    </Hop>
    <BangGiaoDich/>
    <div className="two-col">
      <Hop tieuDe="Mạng Blockchain" phuDe="Chưa được xác minh"><p className="body-copy"><Info size={17}/> Chưa có thông tin mạng, chain ID hoặc trạng thái kết nối Blockchain từ Backend.</p></Hop>
      <Hop tieuDe="Blockchain explorer" phuDe="Chưa có liên kết"><p className="body-copy"><ExternalLink size={17}/> Liên kết explorer chỉ xuất hiện khi mạng, chain ID và transaction hash đã được xác nhận hợp lệ.</p></Hop>
    </div>
  </>
}
