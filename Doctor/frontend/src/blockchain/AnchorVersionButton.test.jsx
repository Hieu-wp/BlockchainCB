import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
vi.mock('./medicalRecordContract', () => ({ anchorVersion: vi.fn(), watchWallet: () => () => {} }));
vi.mock('./anchorApi', () => ({ anchorApi: { status: vi.fn() }, walletError: e => e.message }));
import { anchorApi } from './anchorApi';
import { anchorVersion } from './medicalRecordContract';
import AnchorVersionButton from './AnchorVersionButton';
const props = { recordId: 1, versionNumber: 1, linkedWallet: '0x' + '1'.repeat(40), integrity: true, authorAllowed: true };
beforeEach(() => { vi.clearAllMocks(); anchorApi.status.mockResolvedValue({ status: 'NOT_SUBMITTED', enabled: true }); });
it('disables sending without integrity or linked wallet', async () => { render(<AnchorVersionButton {...props} integrity={false} linkedWallet={null}/>); await screen.findByText('Chưa gửi'); expect(screen.getByRole('button', { name: 'Ghi SHA-256 lên Blockchain' })).toBeDisabled(); });
it('respects deployment review gate from backend', async () => { anchorApi.status.mockResolvedValue({ status: 'NOT_SUBMITTED', enabled: false }); render(<AnchorVersionButton {...props}/>); await screen.findByText('Chưa gửi'); expect(screen.getByRole('button', { name: 'Ghi SHA-256 lên Blockchain' })).toBeDisabled(); });
it('prevents repeated clicks and only shows confirmation after backend result', async () => {
  let resolve; const result = new Promise(r => { resolve = r; }); anchorVersion.mockReturnValue(result); render(<AnchorVersionButton {...props}/>); await screen.findByText('Chưa gửi');
  await userEvent.dblClick(screen.getByRole('button', { name: 'Ghi SHA-256 lên Blockchain' })); expect(anchorVersion).toHaveBeenCalledOnce(); expect(screen.queryByText('Đã được Backend xác minh')).not.toBeInTheDocument();
  resolve({ status: 'CONFIRMED', enabled: true, transactionHash: '0x' + '3'.repeat(64) }); await screen.findByText('Đã được Backend xác minh'); expect(screen.getByRole('link', { name: /Etherscan/ })).toHaveAttribute('href', expect.stringContaining('sepolia.etherscan.io/tx/'));
});
it('shows actionable errors and does not claim success', async () => { anchorVersion.mockRejectedValue(new Error('Sai ví Doctor')); render(<AnchorVersionButton {...props}/>); await screen.findByText('Chưa gửi'); await userEvent.click(screen.getByRole('button', { name: 'Ghi SHA-256 lên Blockchain' })); await screen.findByRole('alert'); expect(screen.getByRole('alert')).toHaveTextContent('Sai ví Doctor'); expect(screen.queryByText('Đã được Backend xác minh')).not.toBeInTheDocument(); });
it('pending transaction disables a second send after remount', async () => { anchorApi.status.mockResolvedValue({ status: 'PENDING', enabled: true, transactionHash: '0x' + '3'.repeat(64) }); render(<AnchorVersionButton {...props}/>); await screen.findByText('Đang chờ xác nhận'); expect(screen.getByRole('button', { name: 'Ghi SHA-256 lên Blockchain' })).toBeDisabled(); expect(screen.getByText('Transaction hash chưa phải xác nhận thành công.')).toBeInTheDocument(); });

it('checks shared status without opening MetaMask automatically and displays verified receipt metadata',async()=>{
  const api={status:vi.fn().mockResolvedValue({status:'CONFIRMED',enabled:true,blockNumber:99,contractAddress:'0xcontract',doctorWallet:props.linkedWallet,transactionHash:'0x'+'8'.repeat(64)})};
  render(<AnchorVersionButton {...props} api={api}/>);
  await screen.findByText('Block Number: 99');expect(api.status).toHaveBeenCalledWith(1,1);
  expect(anchorVersion).not.toHaveBeenCalled();expect(anchorApi.status).not.toHaveBeenCalled();
  expect(screen.getByRole('link',{name:/Etherscan/})).toHaveAttribute('href','https://sepolia.etherscan.io/tx/0x'+'8'.repeat(64));
});
it('retains a broadcast hash after submission failure and never enables a second send',async()=>{
  anchorVersion.mockImplementation(async({onHash})=>{onHash('0x'+'8'.repeat(64));throw new Error('RPC unavailable');});
  render(<AnchorVersionButton {...props}/>);await screen.findByText('Chưa gửi');
  await userEvent.click(screen.getByRole('button',{name:'Ghi SHA-256 lên Blockchain'}));
  await screen.findByRole('alert');expect(screen.getByRole('button',{name:'Ghi SHA-256 lên Blockchain'})).toBeDisabled();
  expect(screen.getByRole('link',{name:/Etherscan/})).toBeInTheDocument();
});

it('starts the explicit post-save flow once when eligible and never on a normal detail visit',async()=>{
  anchorVersion.mockResolvedValue({status:'PENDING',enabled:true,transactionHash:'0x'+'7'.repeat(64)});
  render(<AnchorVersionButton {...props} autoStart/>);
  await waitFor(()=>expect(anchorVersion).toHaveBeenCalledOnce());
  expect(await screen.findByRole('link',{name:/Etherscan/})).toBeInTheDocument();
});
