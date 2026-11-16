
package com.blockchaincb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu yêu cầu thao tác trên tài khoản người dùng.
 */
public record AdminUserActionRequest(

        @NotBlank(message = "Lý do không được để trống")
        @Size(
                max = 350,
                message = "Lý do không được vượt quá 350 ký tự"
        )
        String lyDo
) {}
