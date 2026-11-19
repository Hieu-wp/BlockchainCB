
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import { App, AuthProvider, useLoad } from './App';
import { setToken } from './api';
// App tests cover routing/forms. Real WebCrypto and its wire format are tested separately.
vi.mock('./authEncryption', () => ({encryptCredentials: vi.fn(async () => ({payload:'fixture.encrypted.payload'}))}));
vi.mock('./blockchain/medicalRecordContract', () => ({ connectDoctorWallet: vi.fn(), linkDoctorWallet: vi.fn(), watchWallet: () => () => {} }));
import { connectDoctorWallet, linkDoctorWallet } from './blockchain/medicalRecordContract';

/**
 * Dọn trạng thái sau mỗi bài kiểm thử.
 *
 * - Xóa JWT để test sau không kế thừa phiên đăng nhập.
 * - Khôi phục các biến global đã được giả lập.
 */
afterEach(() => {
  setToken(null);
  vi.unstubAllGlobals();
});

/**
 * Tạo Response HTTP giả lập thành công.
 *
 * Không gọi Backend thật.
 * Dữ liệu trả về được cung cấp riêng cho mỗi test.
 */
const json = (data) => ({
  ok: true,
  json: async () => data,
});

/**
 * Khởi tạo ứng dụng React để kiểm thử.
 *
 * MemoryRouter: giả lập điều hướng mà không mở trình duyệt.
 * AuthProvider: cung cấp trạng thái đăng nhập.
 * App: component chính của MedLedger.
 */
function mount(path = '/login') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>
  );
}

it.each(['/login', '/register'])('clears the password during a pending request and after failure on %s', async path => {
  let complete;
  const fetch = vi.fn(url => url.includes('/encryption-challenge')
    ? Promise.resolve(json({})) : new Promise(resolve => { complete = resolve; }));
  vi.stubGlobal('fetch', fetch);
  mount(path);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), 'privacy@example.test');
  await user.type(screen.getByLabelText('Mật khẩu'), 'Test-only!123');
  if (path === '/register') {
    await user.type(screen.getByLabelText('Họ và tên'), 'Fixture Doctor');
    await user.type(screen.getByLabelText('Chứng chỉ hành nghề'), 'FIXTURE-LICENSE');
    await user.type(screen.getByLabelText('Nơi công tác'), 'Fixture Hospital');
    await user.selectOptions(screen.getByLabelText('Chuyên khoa'), '2');
  }
  await user.click(screen.getByRole('button', {name: path === '/register' ? /Tạo tài khoản/ : /Đăng nhập vào/}));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(screen.getByLabelText('Mật khẩu')).toHaveValue('');
  // Only an encrypted envelope reaches the login/register POST.
  expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({payload:'fixture.encrypted.payload'});
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
  complete({ok:false,status:401});
  await screen.findByRole('alert');
  expect(screen.getByLabelText('Mật khẩu')).toHaveValue('');
  expect(fetch).toHaveBeenCalledTimes(2);
});

it('pending profile reloads /me after signature and keeps medical routes restricted', async () => {
  const wallet = '0x' + '1'.repeat(40);
  let linked = false;
  connectDoctorWallet.mockResolvedValue({ address: wallet, hasDoctorRole: false });
  linkDoctorWallet.mockImplementation(async () => { linked = true; return { walletAddress: wallet }; });
  const fetch = vi.fn(url => Promise.resolve(url.includes('/login') ? json({ token: 'local.token' })
    : url.endsWith('/wallet') ? json({ walletAddress: linked ? wallet : null, verified: linked })
    : json({ id: 1, fullName: 'Pending Doctor', approvalStatus: 'PENDING', hospital: 'Demo', licenseNumber: 'L1', specialtyId: 2, walletAddress: linked ? wallet : null })));
  vi.stubGlobal('fetch', fetch);
  mount();
  await userEvent.type(screen.getByLabelText('Email'), 'pending@doctor.local');
  await userEvent.type(screen.getByLabelText('Mật khẩu'), 'LocalTest@123');
  await userEvent.click(screen.getByRole('button', { name: /Đăng nhập vào/ }));
  await screen.findByRole('heading', { name: 'Hồ sơ của bạn đang được xem xét' });
  await userEvent.click(screen.getByRole('link', { name: /Xem hồ sơ cá nhân/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Kết nối MetaMask' }));
  await screen.findByRole('button', { name: 'Ký challenge và liên kết ví' });
  const before = fetch.mock.calls.filter(([url]) => url.endsWith('/me')).length;
  await userEvent.click(screen.getByRole('button', { name: 'Ký challenge và liên kết ví' }));
  await screen.findByText('Ví đã liên kết thành công. Đang chờ Admin cấp quyền Blockchain.');
  expect(fetch.mock.calls.filter(([url]) => url.endsWith('/me')).length).toBeGreaterThan(before);
  expect(screen.queryByRole('link', { name: 'Bệnh nhân' })).not.toBeInTheDocument();
  expect(fetch.mock.calls.every(([url]) => !url.includes('/patients') && !url.includes('/records'))).toBe(true);
});
it('shared grants dashboard and patient page use public patientCode without reading legacy records', async () => {
  const fetch=vi.fn(url=>Promise.resolve(url.includes('/login')?json({token:'local.token'})
    :url.endsWith('/me')?json({id:1,fullName:'Doctor',approvalStatus:'APPROVED',specialtyId:2})
    :url.endsWith('/integration')?json({sharedGrantsEnabled:true,schemaReady:true,sharedRecordsReady:false})
    :url.includes('/access-grants')?json({content:[],totalPages:0,totalElements:0})
    :json({content:[{id:50,fullName:'Granted Patient',patientCode:'PC-REAL'}],totalPages:1,totalElements:1})));
  vi.stubGlobal('fetch',fetch);mount();
  await userEvent.type(screen.getByLabelText('Email'),'shared@doctor.local');await userEvent.type(screen.getByLabelText('Mật khẩu'),'LocalTest@123');
  await userEvent.click(screen.getByRole('button',{name:/Đăng nhập vào/}));
  await screen.findByText('Chờ tích hợp');
  await userEvent.click(screen.getByRole('link',{name:'Bệnh nhân'}));
  await screen.findByText('PC-REAL');await screen.findByRole('heading',{name:'Xin quyền khám bằng mã bệnh nhân'});
  expect(screen.getByText('Bệnh án chung chờ tích hợp')).toBeInTheDocument();
  expect(fetch.mock.calls.every(([url])=>!url.includes('/records'))).toBe(true);
});

/**
 * TEST 1: CHẶN NGƯỜI CHƯA ĐĂNG NHẬP
 *
 * Người dùng truy cập trực tiếp /records/1.
 * Nếu chưa đăng nhập, hệ thống phải chuyển về /login.
 */
it('redirects unauthenticated visitors to login', () => {
  mount('/records/1');

  expect(
    screen.getByRole('heading', { name: 'Đăng nhập' })
  ).toBeInTheDocument();
});

/**
 * TEST 2: KIỂM TRA QUYỀN BÁC SĨ PENDING
 *
 * Backend giả lập:
 * - /login: cấp token đăng nhập.
 * - /me: trả về tài khoản chưa được Admin phê duyệt.
 *
 * Kết quả mong đợi:
 * - Hiển thị trang chờ phê duyệt.
 * - Không hiển thị menu Bệnh nhân.
 * - Không gọi API danh sách bệnh nhân.
 */
it('pending doctor cannot reach patient routes and sees approval state', async () => {
  const fetch = vi.fn((url) =>
    Promise.resolve(
      url.includes('/login')
        ? json({ token: 'local.token', id: 1 })
        : json({
            id: 1,
            fullName: 'Bác sĩ Demo',
            approvalStatus: 'PENDING',
            specialtyId: 2,
          })
    )
  );

  // Thay fetch thật bằng fetch giả lập.
  vi.stubGlobal('fetch', fetch);

  mount();

  // Nhập thông tin đăng nhập thử nghiệm.
  await userEvent.type(
    screen.getByLabelText('Email'),
    'pending@doctor.local'
  );

  await userEvent.type(
    screen.getByLabelText('Mật khẩu'),
    'LocalTest@123'
  );

  // Nhấn nút đăng nhập.
  await userEvent.click(
    screen.getByRole('button', { name: /Đăng nhập vào/ })
  );

  // Phải hiển thị trang chờ Admin phê duyệt.
  await screen.findByRole('heading', {
    name: 'Hồ sơ của bạn đang được xem xét',
  });

  // Không được hiển thị menu Bệnh nhân.
  expect(
    screen.queryByRole('link', { name: 'Bệnh nhân' })
  ).not.toBeInTheDocument();

  // Frontend không được gọi API /patients.
  expect(
    fetch.mock.calls.every(([url]) => !url.includes('/patients'))
  ).toBe(true);
});

/**
 * TEST 3: DASHBOARD CỦA DOCTOR ĐÃ PHÊ DUYỆT
 *
 * Backend giả lập:
 * - /login: đăng nhập thành công.
 * - /me: tài khoản APPROVED.
 * - /patients: trả về 7 bệnh nhân.
 * - /records: trả về 3 hồ sơ khám.
 *
 * Kết quả mong đợi:
 * - Dashboard hiển thị 7 bệnh nhân.
 * - Dashboard hiển thị 3 hồ sơ.
 * - Có mục Hồ sơ gần đây.
 */
it('approved dashboard displays actual API totals', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn((url) =>
      Promise.resolve(
        url.includes('/login')
          ? json({ token: 'local.token' })
          : url.endsWith('/me')
            ? json({
                id: 1,
                fullName: 'Demo',
                approvalStatus: 'APPROVED',
                specialtyId: 2,
              })
            : json({
                totalElements: url.includes('/patients') ? 7 : 3,
                content: [],
                totalPages: 1,
              })
      )
    )
  );

  mount();

  await userEvent.type(
    screen.getByLabelText('Email'),
    'approved@doctor.local'
  );

  await userEvent.type(
    screen.getByLabelText('Mật khẩu'),
    'LocalTest@123'
  );

  await userEvent.click(
    screen.getByRole('button', { name: /Đăng nhập vào/ })
  );

  // Kiểm tra số lượng dữ liệu trên Dashboard.
  await screen.findByText('7');

  expect(screen.getByText('3')).toBeInTheDocument();

  expect(
    screen.getByRole('heading', { name: 'Hồ sơ gần đây' })
  ).toBeInTheDocument();
});

/**
 * TEST 4: XÓA DỮ LIỆU KHI QUYỀN TRUY CẬP BỊ THU HỒI
 *
 * Lần tải đầu:
 * - Backend giả lập trả PRIVATE_RECORD.
 *
 * Lần tải lại:
 * - Backend giả lập trả lỗi "Quyền đã thu hồi".
 *
 * Kết quả mong đợi:
 * - Hiển thị thông báo lỗi.
 * - Không tiếp tục hiển thị dữ liệu bệnh án cũ.
 */
it('resource erases previously loaded medical content when access is revoked', async () => {
  const loader = vi
    .fn()
    .mockResolvedValueOnce('PRIVATE_RECORD')
    .mockRejectedValueOnce(new Error('Quyền đã thu hồi'));

  /**
   * Component thử nghiệm để kiểm tra hook useLoad().
   * useLoad chịu trách nhiệm tải dữ liệu, xử lý lỗi và reload.
   */
  function Resource() {
    const state = useLoad(loader, []);

    return (
      <>
        <button onClick={state.reload}>Tải lại</button>

        <div>{state.data}</div>
        <div>{state.error}</div>
      </>
    );
  }

  render(<Resource />);

  // Lần đầu phải nhìn thấy nội dung bệnh án.
  await screen.findByText('PRIVATE_RECORD');

  // Giả lập người dùng tải lại.
  await userEvent.click(screen.getByText('Tải lại'));

  // Kiểm tra thông báo khi quyền bị thu hồi.
  await screen.findByText('Quyền đã thu hồi');

  // Nội dung bệnh án cũ không được tiếp tục xuất hiện.
  expect(
    screen.queryByText('PRIVATE_RECORD')
  ).not.toBeInTheDocument();
});
