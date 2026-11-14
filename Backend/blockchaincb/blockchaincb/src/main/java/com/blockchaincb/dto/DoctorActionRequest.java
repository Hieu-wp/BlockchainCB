
package com.blockchaincb.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Dữ liệu yêu cầu khi Admin từ chối hoặc khóa bác sĩ.
 *
 * @param lyDo Lý do thực hiện thao tác quản trị.
 */
public record DoctorActionRequest(

    @NotBlank(message = "Lý do không được để trống")
    String lyDo

) {}
