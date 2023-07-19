package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(description = "修改密码时传递的数据模型")
public class PasswordEditDTO implements Serializable {

    // 员工id
    @ApiModelProperty("员工id")
    private Long empId;

    // 旧密码
    @ApiModelProperty("旧密码")
    private String oldPassword;

    // 新密码
    @ApiModelProperty("新密码")
    private String newPassword;

}
