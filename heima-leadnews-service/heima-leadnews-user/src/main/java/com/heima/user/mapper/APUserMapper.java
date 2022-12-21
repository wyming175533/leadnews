package com.heima.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author wangyiming
 */
@Mapper
public interface APUserMapper extends BaseMapper<ApUser> {

}
