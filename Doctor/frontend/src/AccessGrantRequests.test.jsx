import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
vi.mock('./api', () => ({ api: { integration: vi.fn(), requestAccess: vi.fn(), accessRequests: vi.fn() } }));
import { api } from './api';
import AccessGrantRequests, { sharedDisabledMessage } from './AccessGrantRequests';
beforeEach(() => {
  vi.clearAllMocks();api.integration.mockResolvedValue({sharedGrantsEnabled:true,schemaReady:true,sharedRecordsReady:false});
  api.accessRequests.mockResolvedValue({content:[],totalPages:0});api.requestAccess.mockResolvedValue({id:1,status:'PENDING',existing:false});
});
async function ready(){render(<AccessGrantRequests/>);await screen.findByText('Chưa có yêu cầu cấp quyền.');}
it('sends only trimmed patientCode and displays PENDING without pretending approval',async()=>{
  await ready();await userEvent.type(screen.getByLabelText('Mã bệnh nhân để xin quyền'),' PC-001 ');
  await userEvent.click(screen.getByRole('button',{name:'Gửi yêu cầu cấp quyền khám'}));
  expect(api.requestAccess).toHaveBeenCalledWith('PC-001');await screen.findByText('Đã gửi yêu cầu. Đang chờ bệnh nhân duyệt.');
});
it('keeps request disabled until shared schema integration is enabled',async()=>{
  api.integration.mockResolvedValue({sharedGrantsEnabled:false,schemaReady:false});await readyDisabled();
  expect(screen.getByRole('button',{name:'Gửi yêu cầu cấp quyền khám'})).toBeDisabled();expect(api.accessRequests).not.toHaveBeenCalled();
});
async function readyDisabled(){render(<AccessGrantRequests/>);await screen.findByText(sharedDisabledMessage);}
it('does not create two requests on double click',async()=>{
  let finish;api.requestAccess.mockReturnValue(new Promise(resolve=>{finish=resolve;}));await ready();
  await userEvent.type(screen.getByLabelText('Mã bệnh nhân để xin quyền'),'PC-001');await userEvent.dblClick(screen.getByRole('button',{name:'Gửi yêu cầu cấp quyền khám'}));
  expect(api.requestAccess).toHaveBeenCalledOnce();finish({status:'PENDING',existing:false});await screen.findByText('Đã gửi yêu cầu. Đang chờ bệnh nhân duyệt.');
});
it('reloads request status after Patient decision and notifies patient list to refresh',async()=>{
  const changed=vi.fn();render(<AccessGrantRequests onChanged={changed}/>);await screen.findByText('Chưa có yêu cầu cấp quyền.');
  api.accessRequests.mockResolvedValue({content:[{id:1,patientCode:'PC-001',status:'ACTIVE',revoked:false,expired:false}],totalPages:1});
  await userEvent.click(screen.getByRole('button',{name:'Kiểm tra Patient đã duyệt'}));await screen.findByText('Patient đã cấp quyền');expect(changed).toHaveBeenCalledOnce();
});
it('shows a safe missing-patient error without inventing patient information',async()=>{
  api.requestAccess.mockRejectedValue({code:'PATIENT_CODE_UNAVAILABLE'});await ready();await userEvent.type(screen.getByLabelText('Mã bệnh nhân để xin quyền'),'missing');
  await userEvent.click(screen.getByRole('button',{name:'Gửi yêu cầu cấp quyền khám'}));expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy mã bệnh nhân khả dụng');
});
it('does not represent revoked or expired ACTIVE rows as current permission',async()=>{
  api.accessRequests.mockResolvedValue({content:[{id:1,patientCode:'PC-001',status:'ACTIVE',revoked:true},{id:2,patientCode:'PC-002',status:'ACTIVE',revoked:false,expired:true,expiresAt:'2000-01-01T00:00'}],totalPages:1});
  render(<AccessGrantRequests/>);await screen.findByText('Đã thu hồi');expect(screen.getByText('Đã hết hạn')).toBeInTheDocument();expect(screen.queryByText('Patient đã cấp quyền')).not.toBeInTheDocument();
});
it('uses backend expiry rather than interpreting a timezone-less date in the browser',async()=>{
  api.accessRequests.mockResolvedValue({content:[{id:1,patientCode:'PC-TIME',status:'ACTIVE',revoked:false,expired:false,expiresAt:'2000-01-01T00:00'}],totalPages:1});
  render(<AccessGrantRequests/>);await screen.findByText('Patient đã cấp quyền');expect(screen.queryByText('Đã hết hạn')).not.toBeInTheDocument();
});
