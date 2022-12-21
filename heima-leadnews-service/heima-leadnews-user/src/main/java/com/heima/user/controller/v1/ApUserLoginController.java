package com.heima.user.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.LoginDto;
import com.heima.user.service.APUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author wangyiming
 */
@RestController
@RequestMapping("/api/v1/login")
@Api(value = "app端用户登陆",tags ="ap_user",description = "app端用户登陆api")
public class ApUserLoginController {
    @Autowired
    private APUserService apUserService;
    @ApiOperation("用户登陆")
    @PostMapping("/login_auth")
    public ResponseResult login(@RequestBody LoginDto dto){
        return apUserService.login(dto);
    }

}
