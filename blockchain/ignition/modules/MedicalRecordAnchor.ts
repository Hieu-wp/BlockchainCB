import { buildModule } from "@nomicfoundation/hardhat-ignition/modules";

export default buildModule("MedicalRecordAnchorModule", (m) => {
  const medicalRecordAnchor = m.contract("MedicalRecordAnchor");

  return { medicalRecordAnchor };
});
