package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.file.service.FileStorageService;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.utils.thread.WmThreadLocal;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.service.WmMaterialService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.util.UuidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class VmMaterialServiceImpl extends ServiceImpl<WmMaterialMapper, WmMaterial>implements WmMaterialService {

    @Autowired
    private FileStorageService fileStorageService;
    private String url;

    /**
     * 图片上传
     *
     * @param multipartFile
     * @return
     */
    @Override
    public ResponseResult uploadPicture(MultipartFile multipartFile) {
        //参数校验
        if(multipartFile==null || multipartFile.getSize()==0){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        Integer userId = WmThreadLocal.get().getId();
        //上传minIo
        String fileName=UUID.randomUUID().toString().replace("-","");
        String originalFilename = multipartFile.getOriginalFilename();
        String layout= originalFilename.substring(originalFilename.lastIndexOf("."));
        String url =null;
        try {
            url = fileStorageService.uploadImgFile("", fileName + layout, multipartFile.getInputStream());
            log.info("文件上传成功");
        } catch (IOException e) {
            log.error("文件上传失败，userId={},文件={}",userId,originalFilename);
            throw new RuntimeException(e);
        }
        //保存到数据库
        WmMaterial wm=new WmMaterial();
        wm.setType((short)0);
        wm.setUrl(url);
        wm.setCreatedTime(new Date());
        wm.setIsCollection((short)0);
        wm.setUserId(userId);
        save(wm);
        return ResponseResult.okResult(wm);
    }

    @Override
    public ResponseResult findList(WmMaterialDto dto) {
        dto.checkParam();//检查分页参数
        IPage ipage=new Page(dto.getPage(),dto.getSize());
        LambdaQueryWrapper<WmMaterial> queryWrapper=new LambdaQueryWrapper<>();
        //添加查询条件 是否收藏
        if(dto.getIsCollection()!=null && dto.getIsCollection()==1){
            queryWrapper.eq(WmMaterial::getIsCollection,dto.getIsCollection());
        }
        //添加用户参数
        if(WmThreadLocal.get()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        log.error("用户id={}",WmThreadLocal.get().getId());
        queryWrapper.eq(WmMaterial::getUserId,WmThreadLocal.get().getId());
        //分页查询
        ipage=page(ipage,queryWrapper);
        ResponseResult responseResult = new PageResponseResult(dto.getPage(),dto.getSize(),(int)ipage.getTotal());
        responseResult.setData(ipage.getRecords());
        return responseResult;
    }
}
