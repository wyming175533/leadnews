package com.heima.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;

public interface APUserService extends IService<ApUser> {

    /**
     * @param dto  登陆请求信息phone password
     * @return
     */
    public ResponseResult login(LoginDto dto);
}
