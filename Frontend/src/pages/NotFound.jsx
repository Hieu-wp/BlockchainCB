import { FileQuestion, ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function NotFound() {
  return <section className="panel not-found" aria-labelledby="khong-tim-thay">
    <span className="integration-icon"><FileQuestion size={40}/></span><span className="eyebrow">LỖI 404</span>
    <h1 id="khong-tim-thay">Không tìm thấy trang</h1><p>Đường dẫn bạn truy cập không tồn tại trong cổng quản trị MediCare.</p>
    <Link to="/" className="btn btn-primary"><ArrowLeft size={17}/> Quay lại Dashboard</Link>
  </section>
}
