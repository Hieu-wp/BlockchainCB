
 // SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MedicalRecordAnchor} from "./MedicalRecordAnchor.sol";

contract MedicalRecordAnchorTest is Test {

    MedicalRecordAnchor internal anchor;

    address internal patient = address(0xA11CE);
    address internal anotherPatient = address(0xB0B);
    address internal doctor = address(0xD0C7);
    address internal stranger = address(0xCAFE);

    // Chay truoc moi bai test
    function setUp() public {
        anchor = new MedicalRecordAnchor();
    }

    // 1. Nguoi deploy co quyen Admin
    function testDeployerIsAdmin() public view {
        assertTrue(
            anchor.hasRole(
                anchor.DEFAULT_ADMIN_ROLE(),
                address(this)
            )
        );
    }

    // 2. Admin cap vai tro Doctor
    function testAdminCanApproveDoctor() public {
        anchor.grantRole(anchor.DOCTOR_ROLE(), doctor);

        assertTrue(
            anchor.hasRole(anchor.DOCTOR_ROLE(), doctor)
        );
    }

    // 3. Nguoi la khong duoc cap vai tro Doctor
    function testNonAdminCannotApproveDoctor() public {
        vm.expectRevert();
        vm.prank(stranger);

        anchor.grantRole(anchor.DOCTOR_ROLE(), doctor);
    }

    // 4. Patient cap quyen cho Doctor da duoc duyet
    function testApprovedDoctorCanReceiveConsent() public {
        anchor.grantRole(anchor.DOCTOR_ROLE(), doctor);

        vm.prank(patient);
        anchor.grantWrite(doctor);

        assertTrue(anchor.writePermission(patient, doctor));
        assertFalse(anchor.writePermission(anotherPatient, doctor));
    }

    // 5. Khong cap quyen cho Doctor chua duoc duyet
    function testUnapprovedDoctorCannotReceiveConsent() public {
        vm.expectRevert(
            abi.encodeWithSignature(
                "Error(string)",
                "Doctor is not approved"
            )
        );

        vm.prank(patient);
        anchor.grantWrite(doctor);
    }

    // 6. Khong chap nhan dia chi vi 0
    function testCannotGrantZeroAddress() public {
        vm.expectRevert(
            abi.encodeWithSignature(
                "Error(string)",
                "Invalid doctor"
            )
        );

        vm.prank(patient);
        anchor.grantWrite(address(0));
    }

    // 7. Patient thu hoi quyen cua Doctor
    function testPatientCanRevokeConsent() public {
        anchor.grantRole(anchor.DOCTOR_ROLE(), doctor);

        vm.startPrank(patient);
        anchor.grantWrite(doctor);
        anchor.revokeWrite(doctor);
        vm.stopPrank();

        assertFalse(anchor.writePermission(patient, doctor));
    }

    // 8. Patient khac khong the xoa quyen cua nguoi nay
    function testDifferentPatientCannotRevokeOtherGrant() public {
        anchor.grantRole(anchor.DOCTOR_ROLE(), doctor);

        vm.prank(patient);
        anchor.grantWrite(doctor);

        vm.prank(anotherPatient);
        anchor.revokeWrite(doctor);

        assertTrue(anchor.writePermission(patient, doctor));
        assertFalse(anchor.writePermission(anotherPatient, doctor));
    }

    // 9. Doctor khong duoc tu cap vai tro cho nguoi khac
    function testDoctorCannotApproveAnotherDoctor() public {
        anchor.grantRole(anchor.DOCTOR_ROLE(), doctor);

        vm.expectRevert();
        vm.prank(doctor);

        anchor.grantRole(anchor.DOCTOR_ROLE(), stranger);
    }
}
