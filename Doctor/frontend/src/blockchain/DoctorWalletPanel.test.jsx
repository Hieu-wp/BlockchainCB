import React from 'react';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
vi.mock('./medicalRecordContract', () => ({ connectDoctorWallet: vi.fn(), linkDoctorWallet: vi.fn(), watchWallet: vi.fn() }));
vi.mock('./anchorApi', async importOriginal => ({ ...await importOriginal(), anchorApi: { wallet: vi.fn() } }));
import { connectDoctorWallet, linkDoctorWallet, watchWallet } from './medicalRecordContract';
import { anchorApi } from './anchorApi';
import DoctorWalletPanel from './DoctorWalletPanel';
const address = '0x' + '1'.repeat(40);
const verified = { walletAddress: address, verified: true, chainId: 11155111, verifiedAt: '2026-11-18T00:00:00Z' };
beforeEach(() => {
  vi.clearAllMocks();
  watchWallet.mockReturnValue(() => {});
  anchorApi.wallet.mockResolvedValue({ walletAddress: null, verified: false });
  connectDoctorWallet.mockResolvedValue({ address, hasDoctorRole: false, chainId: 11155111 });
  linkDoctorWallet.mockResolvedValue({ walletAddress: address, chainId: 11155111 });
});
async function connect() {
  await userEvent.click(screen.getByRole('button', { name: 'Kết nối MetaMask' }));
  await screen.findByText(/Vai trò Blockchain của ví đang kết nối: Chưa có/);
}
it('lets PENDING connect and sign without role, reloads backend profile, and shows waiting for Admin', async () => {
  const onLinked = vi.fn(async () => { anchorApi.wallet.mockResolvedValue(verified); });
  render(<DoctorWalletPanel approvalStatus="PENDING" onLinked={onLinked}/>);
  await screen.findByText('Bạn cần liên kết ví MetaMask để Admin có thể xét duyệt và cấp quyền Blockchain.');
  await connect();
  expect(screen.queryByText('Backend đã xác minh quyền sở hữu ví.')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Ký challenge và liên kết ví' }));
  await screen.findByText('Ví đã liên kết thành công. Đang chờ Admin cấp quyền Blockchain.');
  expect(onLinked).toHaveBeenCalledOnce();
  expect(anchorApi.wallet).toHaveBeenCalledTimes(2);
  expect(linkDoctorWallet).toHaveBeenCalledWith(address, expect.any(Function));
  expect(screen.queryByText(/Có DOCTOR_ROLE$/)).not.toBeInTheDocument();
});
it('does not label an unproved saved address as verified', async () => {
  anchorApi.wallet.mockResolvedValue({ walletAddress: address, verified: false });
  render(<DoctorWalletPanel approvalStatus="PENDING" linkedWallet={address}/>);
  await connect();
  expect(screen.queryByText('Backend đã xác minh quyền sở hữu ví.')).not.toBeInTheDocument();
  expect(screen.queryByText('Ví đã liên kết thành công. Đang chờ Admin cấp quyền Blockchain.')).not.toBeInTheDocument();
});
it.each(['REJECTED', 'BLOCKED'])('does not enable linking for %s', async approvalStatus => {
  render(<DoctorWalletPanel approvalStatus={approvalStatus}/>);
  expect(screen.getByRole('button', { name: 'Kết nối MetaMask' })).toBeDisabled();
  expect(connectDoctorWallet).not.toHaveBeenCalled();
  expect(linkDoctorWallet).not.toHaveBeenCalled();
});
it.each([
  ['INVALID_SIGNATURE', 'Chữ ký không hợp lệ hoặc không thuộc ví đã chọn.'],
  ['CHALLENGE_USED_OR_EXPIRED', 'Challenge đã dùng hoặc hết hạn. Hãy tạo yêu cầu mới.'],
  ['WALLET_ALREADY_LINKED', 'Ví này đã thuộc tài khoản khác.'],
])('shows actionable %s without claiming link success', async (code, message) => {
  linkDoctorWallet.mockRejectedValue({ code });
  const onLinked = vi.fn();
  render(<DoctorWalletPanel approvalStatus="PENDING" onLinked={onLinked}/>);
  await connect();
  await userEvent.click(screen.getByRole('button', { name: 'Ký challenge và liên kết ví' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(message);
  expect(onLinked).not.toHaveBeenCalled();
  expect(screen.queryByText('Backend đã xác minh quyền sở hữu ví.')).not.toBeInTheDocument();
});
it('shows the backend reason and failing request instead of assuming every 403 means blocked', async () => {
  linkDoctorWallet.mockRejectedValue({ status: 403, code: 'ACCOUNT_DISABLED', path: '/doctor/wallet/challenges' });
  render(<DoctorWalletPanel approvalStatus="PENDING"/>);
  await connect();
  await userEvent.click(screen.getByRole('button', { name: 'Ký challenge và liên kết ví' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Tạo challenge · HTTP 403 · ACCOUNT_DISABLED');
  expect(screen.getByRole('alert')).toHaveTextContent('Tài khoản đã bị vô hiệu hóa');
});
it('shows wrong-network errors before signature', async () => {
  connectDoctorWallet.mockRejectedValue(new Error('Sai mạng. Hãy chọn Ethereum Sepolia.'));
  render(<DoctorWalletPanel approvalStatus="PENDING"/>);
  await userEvent.click(screen.getByRole('button', { name: 'Kết nối MetaMask' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Sai mạng');
  expect(linkDoctorWallet).not.toHaveBeenCalled();
});
it('requires the already verified wallet when MetaMask connects another account', async () => {
  anchorApi.wallet.mockResolvedValue(verified);
  connectDoctorWallet.mockResolvedValue({ address: '0x' + '2'.repeat(40), hasDoctorRole: false });
  render(<DoctorWalletPanel approvalStatus="APPROVED" linkedWallet={address}/>);
  await connect();
  expect(screen.getByRole('button', { name: 'Ký challenge và liên kết ví' })).toBeDisabled();
  expect(screen.getByRole('alert')).toHaveTextContent('Sai ví Doctor');
});
it('reloads connected address and role after provider change without another access prompt', async () => {
  render(<DoctorWalletPanel approvalStatus="PENDING"/>);
  await connect();
  const changed = watchWallet.mock.calls[0][0];
  const next = '0x' + '2'.repeat(40);
  connectDoctorWallet.mockResolvedValue({ address: next, hasDoctorRole: true });
  await act(async () => { await changed({ type: 'accountsChanged', addresses: [next] }); });
  await screen.findByText(next);
  expect(screen.getByText('Vai trò Blockchain của ví đang kết nối: Có DOCTOR_ROLE')).toBeInTheDocument();
  expect(connectDoctorWallet).toHaveBeenLastCalledWith({ requestAccess: false });
  expect(linkDoctorWallet).not.toHaveBeenCalled();
});
it('clears the old account when an automatic refresh detects the wrong chain', async () => {
  render(<DoctorWalletPanel approvalStatus="PENDING"/>);
  await connect();
  connectDoctorWallet.mockRejectedValueOnce(new Error('Sai mạng. Hãy chọn Sepolia.'));
  await act(async () => { await watchWallet.mock.calls[0][0]({ type: 'chainChanged', chainId: '0x1' }); });
  expect(await screen.findByRole('alert')).toHaveTextContent('Sai mạng');
  expect(screen.queryByRole('button', { name: 'Ký challenge và liên kết ví' })).not.toBeInTheDocument();
});
it('does not call a generic 403 a blocked account without evidence', async () => {
  linkDoctorWallet.mockRejectedValue({ status: 403, path: '/doctor/wallet/verify' });
  render(<DoctorWalletPanel approvalStatus="PENDING"/>);
  await connect();
  await userEvent.click(screen.getByRole('button', { name: 'Ký challenge và liên kết ví' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Xác minh chữ ký · HTTP 403');
  expect(screen.getByRole('alert')).toHaveTextContent('chưa cung cấp nguyên nhân cụ thể');
});
it('does not ask the owner of an already verified wallet to sign again', async () => {
  anchorApi.wallet.mockResolvedValue(verified);
  render(<DoctorWalletPanel approvalStatus="PENDING" linkedWallet={address}/>);
  await connect();
  expect(screen.getByRole('button', {name:'Ví này đã được xác minh'})).toBeDisabled();
  expect(linkDoctorWallet).not.toHaveBeenCalled();
});
