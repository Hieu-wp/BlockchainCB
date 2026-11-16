import React from 'react';
import { expect,it,vi,afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App,AuthProvider,useLoad } from './App';
import { setToken } from './api';
afterEach(()=>{setToken(null);vi.unstubAllGlobals();});
const json=data=>({ok:true,json:async()=>data});
function mount(path='/login'){return render(<MemoryRouter initialEntries={[path]}><AuthProvider><App/></AuthProvider></MemoryRouter>);}
it('redirects unauthenticated visitors to login',()=>{mount('/records/1');expect(screen.getByRole('heading',{name:'Đăng nhập'})).toBeInTheDocument();});
it('pending doctor cannot reach patient routes and sees approval state',async()=>{
  const fetch=vi.fn((url)=>Promise.resolve(url.includes('/login')?json({token:'local.token',id:1}):json({id:1,fullName:'Bác sĩ Demo',approvalStatus:'PENDING',specialtyId:2})));vi.stubGlobal('fetch',fetch);mount();
  await userEvent.type(screen.getByLabelText('Email'),'pending@doctor.local');await userEvent.type(screen.getByLabelText('Mật khẩu'),'LocalTest@123');await userEvent.click(screen.getByRole('button',{name:/Đăng nhập vào/}));
  await screen.findByRole('heading',{name:'Hồ sơ của bạn đang được xem xét'});expect(screen.queryByRole('link',{name:'Bệnh nhân'})).not.toBeInTheDocument();
  expect(fetch.mock.calls.every(([url])=>!url.includes('/patients'))).toBe(true);
});
it('approved dashboard displays actual API totals',async()=>{
  vi.stubGlobal('fetch',vi.fn(url=>Promise.resolve(url.includes('/login')?json({token:'local.token'}):url.endsWith('/me')?json({id:1,fullName:'Demo',approvalStatus:'APPROVED',specialtyId:2}):json({totalElements:url.includes('/patients')?7:3,content:[],totalPages:1}))));mount();
  await userEvent.type(screen.getByLabelText('Email'),'approved@doctor.local');await userEvent.type(screen.getByLabelText('Mật khẩu'),'LocalTest@123');await userEvent.click(screen.getByRole('button',{name:/Đăng nhập vào/}));
  await screen.findByText('7');expect(screen.getByText('3')).toBeInTheDocument();expect(screen.getByRole('heading',{name:'Hồ sơ gần đây'})).toBeInTheDocument();
});
it('resource erases previously loaded medical content when access is revoked',async()=>{
  const loader=vi.fn().mockResolvedValueOnce('PRIVATE_RECORD').mockRejectedValueOnce(new Error('Quyền đã thu hồi'));
  function Resource(){const s=useLoad(loader,[]);return <><button onClick={s.reload}>Tải lại</button><div>{s.data}</div><div>{s.error}</div></>;}
  render(<Resource/>);await screen.findByText('PRIVATE_RECORD');await userEvent.click(screen.getByText('Tải lại'));await screen.findByText('Quyền đã thu hồi');expect(screen.queryByText('PRIVATE_RECORD')).not.toBeInTheDocument();
});
