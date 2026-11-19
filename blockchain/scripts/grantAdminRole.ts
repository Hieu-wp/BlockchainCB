import { network } from "hardhat";

const CONTRACT_ADDRESS =
  "0xCd49fa3EE662E0bcfADa0FeA9D69f9383D6f7735";

const NEW_ADMIN =
  "0x602712D43b901DeBe68D2609F3A4529c40E16B8D";

async function main() {
  const { viem } = await network.connect();

  const [wallet] = await viem.getWalletClients();
  const publicClient = await viem.getPublicClient();

const contract = await viem.getContractAt(
  "MedicalRecordAnchor",
  CONTRACT_ADDRESS,
  {
    client: {
      wallet: wallet
    }
  }
);
  // Lấy quyền Admin mặc định.
  const adminRole = await contract.read.DEFAULT_ADMIN_ROLE();

  // Kiểm tra ví thực hiện có quyền cấp Admin không.
  const canGrant = await contract.read.hasRole([
    adminRole,
    wallet.account.address,
  ]);

  if (!canGrant) {
    throw new Error("Ví hiện tại không có DEFAULT_ADMIN_ROLE");
  }

  // Tránh gửi giao dịch trùng.
  const alreadyAdmin = await contract.read.hasRole([
    adminRole,
    NEW_ADMIN,
  ]);

  if (alreadyAdmin) {
    console.log("Ví bạn ấy đã có quyền Admin.");
    return;
  }

  // Cấp quyền Admin cho ví mới.
  const txHash = await contract.write.grantRole([
    adminRole,
    NEW_ADMIN,
  ]);

  console.log("Transaction:", txHash);

  const receipt = await publicClient.waitForTransactionReceipt({
    hash: txHash,
  });

  if (receipt.status !== "success") {
    throw new Error("Giao dịch cấp quyền thất bại.");
  }

  // Kiểm tra lại quyền sau giao dịch.
  const granted = await contract.read.hasRole([
    adminRole,
    NEW_ADMIN,
  ]);

  console.log("Admin role granted:", granted);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});