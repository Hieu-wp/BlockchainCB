// Thay thế bằng địa chỉ Contract đầy đủ 0x48A...7a5aE bạn vừa copy ở Remix
const CONTRACT_ADDRESS = "0x48A394b338e8BD3b60CdD822A8926D0199a7a5aE";

const CONTRACT_ABI = [
    "function grantAccess(address doctorWallet) external",
    "function revokeAccess(address doctorWallet) external",
    "function hasAccess(address patientWallet, address doctorWallet) external view returns (bool)",
    "function verifyRecordVersion(string recordCode, uint256 versionNo, bytes32 currentDbHash) external view returns (bool isValid, bytes32 onChainHash, address doctor, uint256 anchoredAt)"
];