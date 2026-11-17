import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { network } from "hardhat";
import { zeroAddress, keccak256, sha256, stringToHex } from "viem";

// The node-test-runner-viem template already registers the needed plugins.
const { viem } = await network.create();

async function deployFixture() {
  const [admin, doctor, patient, otherPatient, outsider] =
    await viem.getWalletClients();

  // Deploys a fresh instance for each test (isolated contract state).
  const anchor = await viem.deployContract("MedicalRecordAnchor");
  const adminRole = await anchor.read.DEFAULT_ADMIN_ROLE();
  const doctorRole = await anchor.read.DOCTOR_ROLE();

  return {
    anchor,
    adminRole,
    doctorRole,
    admin,
    doctor,
    patient,
    otherPatient,
    outsider,
  };
}

describe("MedicalRecordAnchor - roles and write consent", () => {
  it("gives DEFAULT_ADMIN_ROLE to the deployer", async () => {
    const { anchor, adminRole, admin } = await deployFixture();

    assert.equal(
      await anchor.read.hasRole([adminRole, admin.account.address]),
      true,
    );
  });

  it("lets the admin approve a Doctor wallet", async () => {
    const { anchor, admin, doctor, doctorRole } = await deployFixture();

    await anchor.write.grantRole([doctorRole, doctor.account.address], {
      account: admin.account,
    });

    assert.equal(
      await anchor.read.hasRole([doctorRole, doctor.account.address]),
      true,
    );
  });

  it("prevents a stranger from approving a Doctor", async () => {
    const { anchor, outsider, doctor, doctorRole } = await deployFixture();

    await assert.rejects(
      anchor.write.grantRole([doctorRole, doctor.account.address], {
        account: outsider.account,
      }),
    );

    assert.equal(
      await anchor.read.hasRole([doctorRole, doctor.account.address]),
      false,
    );
  });

  it("allows a Patient to grant consent to an approved Doctor", async () => {
    const { anchor, admin, doctor, patient, doctorRole } =
      await deployFixture();

    await anchor.write.grantRole([doctorRole, doctor.account.address], {
      account: admin.account,
    });
    await anchor.write.grantWrite([doctor.account.address], {
      account: patient.account,
    });

    assert.equal(
      await anchor.read.writePermission([
        patient.account.address,
        doctor.account.address,
      ]),
      true,
    );
  });

  it("rejects consent for a wallet without DOCTOR_ROLE", async () => {
    const { anchor, doctor, patient } = await deployFixture();

    await viem.assertions.revertWith(
      anchor.write.grantWrite([doctor.account.address], {
        account: patient.account,
      }),
      "Doctor is not approved",
    );
  });

  it("rejects the zero address as Doctor", async () => {
    const { anchor, patient } = await deployFixture();

    await viem.assertions.revertWith(
      anchor.write.grantWrite([zeroAddress], {
        account: patient.account,
      }),
      "Invalid doctor",
    );
  });

  it("allows a Patient to revoke an existing consent", async () => {
    const { anchor, admin, doctor, patient, doctorRole } =
      await deployFixture();

    await anchor.write.grantRole([doctorRole, doctor.account.address], {
      account: admin.account,
    });
    await anchor.write.grantWrite([doctor.account.address], {
      account: patient.account,
    });
    await anchor.write.revokeWrite([doctor.account.address], {
      account: patient.account,
    });

    assert.equal(
      await anchor.read.writePermission([
        patient.account.address,
        doctor.account.address,
      ]),
      false,
    );
  });

  it("keeps each Patient's consent independent", async () => {
    const { anchor, admin, doctor, patient, otherPatient, doctorRole } =
      await deployFixture();

    await anchor.write.grantRole([doctorRole, doctor.account.address], {
      account: admin.account,
    });
    await anchor.write.grantWrite([doctor.account.address], {
      account: patient.account,
    });
    await anchor.write.revokeWrite([doctor.account.address], {
      account: otherPatient.account,
    });

    assert.equal(
      await anchor.read.writePermission([
        patient.account.address,
        doctor.account.address,
      ]),
      true,
    );
    assert.equal(
      await anchor.read.writePermission([
        otherPatient.account.address,
        doctor.account.address,
      ]),
      false,
    );
  });

  it("does not let a Doctor grant DOCTOR_ROLE to someone else", async () => {
    const { anchor, admin, doctor, outsider, doctorRole } =
      await deployFixture();

    await anchor.write.grantRole([doctorRole, doctor.account.address], {
      account: admin.account,
    });

    await assert.rejects(
      anchor.write.grantRole([doctorRole, outsider.account.address], {
        account: doctor.account,
      }),
    );
    assert.equal(
      await anchor.read.hasRole([doctorRole, outsider.account.address]),
      false,
    );
  });
});

describe("MedicalRecordAnchor - version anchoring", () => {

  it("stores and verifies two consecutive versions", async () => {
    const { anchor, admin, doctor, patient, doctorRole } =
      await deployFixture();

    const recordKey = keccak256(stringToHex("demo-record-001"));
    const hash1 = sha256(stringToHex("medical-record-version-1"));
    const hash2 = sha256(stringToHex("medical-record-version-2"));
    const emptyHash = `0x${"00".repeat(32)}` as const;

    // Admin cong nhan Doctor
    await anchor.write.grantRole(
      [doctorRole, doctor.account.address],
      { account: admin.account }
    );

    // Patient cap quyen
    await anchor.write.grantWrite(
      [doctor.account.address],
      { account: patient.account }
    );

    // Doctor ghi version 1
    await anchor.write.anchorVersion(
      [recordKey, patient.account.address, 1n, hash1, emptyHash],
      { account: doctor.account }
    );

    // Doctor ghi version 2
    await anchor.write.anchorVersion(
      [recordKey, patient.account.address, 2n, hash2, hash1],
      { account: doctor.account }
    );

    assert.equal(
      await anchor.read.versionHashes([recordKey, 1n]),
      hash1
    );

    assert.equal(
      await anchor.read.versionHashes([recordKey, 2n]),
      hash2
    );

    assert.equal(
      await anchor.read.verifyVersion([recordKey, 1n, hash1]),
      true
    );

    assert.equal(
      await anchor.read.verifyVersion([recordKey, 2n, hash2]),
      true
    );

    assert.equal(
      await anchor.read.verifyVersion([recordKey, 2n, hash1]),
      false
    );
  });

  it("rejects anchoring without Patient consent", async () => {
    const { anchor, admin, doctor, patient, doctorRole } =
      await deployFixture();

    const recordKey = keccak256(stringToHex("demo-record-002"));
    const hash1 = sha256(stringToHex("version-1"));
    const emptyHash = `0x${"00".repeat(32)}` as const;

    await anchor.write.grantRole(
      [doctorRole, doctor.account.address],
      { account: admin.account }
    );

    await viem.assertions.revertWith(
      anchor.write.anchorVersion(
        [recordKey, patient.account.address, 1n, hash1, emptyHash],
        { account: doctor.account }
      ),
      "No patient permission"
    );
  });

  it("rejects overwriting an existing version", async () => {
    const { anchor, admin, doctor, patient, doctorRole } =
      await deployFixture();

    const recordKey = keccak256(stringToHex("demo-record-003"));
    const hash1 = sha256(stringToHex("version-1"));
    const hash2 = sha256(stringToHex("version-2"));
    const emptyHash = `0x${"00".repeat(32)}` as const;

    await anchor.write.grantRole(
      [doctorRole, doctor.account.address],
      { account: admin.account }
    );

    await anchor.write.grantWrite(
      [doctor.account.address],
      { account: patient.account }
    );

    await anchor.write.anchorVersion(
      [recordKey, patient.account.address, 1n, hash1, emptyHash],
      { account: doctor.account }
    );

    // Co tinh ghi lai version 1
    await viem.assertions.revertWith(
      anchor.write.anchorVersion(
        [recordKey, patient.account.address, 1n, hash2, hash1],
        { account: doctor.account }
      ),
      "Invalid version"
    );
  });
});
