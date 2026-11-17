
 // SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {AccessControl}
    from "@openzeppelin/contracts/access/AccessControl.sol";

contract MedicalRecordAnchor is AccessControl {

    bytes32 public constant DOCTOR_ROLE =
        keccak256("DOCTOR_ROLE");

    // Thong tin moi nhat cua mot ho so
    struct RecordState {
        address patient;
        uint64 latestVersion;
        bytes32 latestHash;
    }

    // Patient cap quyen ghi cho Doctor
    mapping(address => mapping(address => bool))
        public writePermission;

    // Ma ho so => trang thai moi nhat
    mapping(bytes32 => RecordState) public records;

    // Ma ho so => so phien ban => SHA-256
    mapping(bytes32 => mapping(uint64 => bytes32))
        public versionHashes;

    event PermissionChanged(
        address indexed patient,
        address indexed doctor,
        bool allowed
    );

    event VersionAnchored(
        bytes32 indexed recordKey,
        uint64 version,
        bytes32 contentHash,
        bytes32 previousHash,
        address indexed doctor
    );

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
    }

    // Patient cap quyen ghi
    function grantWrite(address doctor) external {
        require(doctor != address(0), "Invalid doctor");

        require(
            hasRole(DOCTOR_ROLE, doctor),
            "Doctor is not approved"
        );

        writePermission[msg.sender][doctor] = true;

        emit PermissionChanged(msg.sender, doctor, true);
    }

    // Patient thu hoi quyen
    function revokeWrite(address doctor) external {
        writePermission[msg.sender][doctor] = false;

        emit PermissionChanged(msg.sender, doctor, false);
    }

    // Doctor ghi SHA-256 cua phien ban len Blockchain
    function anchorVersion(
        bytes32 recordKey,
        address patient,
        uint64 version,
        bytes32 contentHash,
        bytes32 previousHash
    ) external onlyRole(DOCTOR_ROLE) {

        require(recordKey != bytes32(0), "Invalid record");
        require(patient != address(0), "Invalid patient");
        require(contentHash != bytes32(0), "Invalid hash");

        require(
            writePermission[patient][msg.sender],
            "No patient permission"
        );

        RecordState storage state = records[recordKey];

        if (state.latestVersion == 0) {

            // Ho so chua co phien ban nao
            require(version == 1, "First version must be 1");

            require(
                previousHash == bytes32(0),
                "Invalid previous hash"
            );

            state.patient = patient;

        } else {

            // Ho so da co phien ban
            require(
                state.patient == patient,
                "Patient mismatch"
            );

            require(
                version == state.latestVersion + 1,
                "Invalid version"
            );

            require(
                previousHash == state.latestHash,
                "Previous hash mismatch"
            );
        }

        // Cap nhat thong tin phien ban moi nhat
        state.latestVersion = version;
        state.latestHash = contentHash;

        // Luu hash cua tung phien ban
        versionHashes[recordKey][version] = contentHash;

        emit VersionAnchored(
            recordKey,
            version,
            contentHash,
            previousHash,
            msg.sender
        );
    }

    // Kiem tra hash mot phien ban
    function verifyVersion(
        bytes32 recordKey,
        uint64 version,
        bytes32 contentHash
    ) external view returns (bool) {

        return contentHash != bytes32(0)
            && versionHashes[recordKey][version] == contentHash;
    }
}
