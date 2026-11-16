
package com.blockchaincb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu yêu cầu khi Admin từ chối hoặc khóa bác sĩ.
 *
 * @param lyDo Lý do thực hiện thao tác quản trị.
 */
public record DoctorActionRequest(

    @NotBlank(message = "Lý do không được để trống")
    @Size(max = 350, message = "Lý do không được vượt quá 350 ký tự")
    String lyDo

) {}
