package com.heima.user.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.mapper.APUserMapper;
import com.heima.user.service.APUserService;
import com.heima.utils.common.AppJwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.sql.Wrapper;
import java.util.HashMap;
import java.util.Map;

@Service
public class ApUserServiceImpl extends ServiceImpl<APUserMapper,ApUser> implements APUserService {
    /**
     * @param dto 登陆请求信息phone password
     * @return
     */
    @Override
    public ResponseResult login(LoginDto dto) {
        Map<String,Object> map=new HashMap<>();
        //1.正常登陆
        if(ObjectUtil.isNotNull(dto)&&StrUtil.isNotBlank(dto.getPassword())&&StrUtil.isNotBlank(dto.getPhone())){
            //查询用户 getOne-->Wrappers条件构造器.<实体>lambdaQuery->条件
            ApUser apUser=getOne(Wrappers.<ApUser>lambdaQuery()
                    .eq(ApUser::getPhone, dto.getPhone()));
            //对比密码
            if(apUser==null){
                return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST,"用户信息错误");
            }
            //用户存在 拿到表中盐 和用户传入的密码 生成新的密码
            String salt=apUser.getSalt();
            String pwd= dto.getPassword();
            pwd= DigestUtils.md5DigestAsHex((pwd+salt).getBytes());
            if(!pwd.equals(apUser.getPassword())){
                return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
            }
            //验证成功生成jwt返回

            map.put("token", AppJwtUtil.getToken(apUser.getId().longValue()));
            apUser.setSalt("");
            apUser.setPassword("");
            map.put("user",apUser);
            return ResponseResult.okResult(map);

        }else{
            //游客
            map.put("token",AppJwtUtil.getToken(0L));
           return ResponseResult.okResult(map);
        }

    }
}
