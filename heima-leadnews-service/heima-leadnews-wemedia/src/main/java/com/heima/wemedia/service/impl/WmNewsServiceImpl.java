package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WmNewsMessageConstants;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.utils.thread.WmThreadLocal;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {
    /**
     * 条件查询文章列表
     *
     * @param dto
     * @return
     */
    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;

    @Autowired
    private KafkaTemplate kafkaTemplate;
    @Override
    public ResponseResult findList(WmNewsPageReqDto dto) {
        //参数校验 默认分页值
        dto.checkParam();
        IPage ipage = new Page(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<WmNews> queryWrapper = new LambdaQueryWrapper<>();

        if (WmThreadLocal.get() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        Integer userId = WmThreadLocal.get().getId();
        queryWrapper.eq(WmNews::getUserId, userId);

        //状态
        if (dto.getStatus() != null) {
            queryWrapper.eq(WmNews::getStatus, dto.getStatus());
        }
        //频道
        if (dto.getChannelId() != null) {
            queryWrapper.eq(WmNews::getChannelId, dto.getChannelId());
        }
        //时间范围
        if (dto.getBeginPubDate() != null) {
            queryWrapper.ge(WmNews::getPublishTime, dto.getBeginPubDate());
        }
        if (dto.getEndPubDate() != null) {
            queryWrapper.le(WmNews::getPublishTime, dto.getEndPubDate());
        }
        //关键字模糊
        if (dto.getKeyword() != null) {
            queryWrapper.like(WmNews::getTitle, dto.getKeyword());
        }
        //发布时间倒序
        queryWrapper.orderByDesc(WmNews::getPublishTime);
        //结果返回
        ipage = page(ipage, queryWrapper);
        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) ipage.getTotal());
        responseResult.setData(ipage.getRecords());
        return responseResult;

    }

    @Autowired
    private WmNewsTaskService wmNewsTaskService;
    /**
     * 发布文章或保存草稿
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        //dto，dto.content 参数校验
        if (dto == null || dto.getContent() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        // 拷贝dto属性->new WmNews  封面 list->String 逗号分割
        WmNews wmNews = new WmNews();
        BeanUtils.copyProperties(dto, wmNews);
        List<String> images = dto.getImages();
        if (!CollectionUtils.isEmpty(images)) {
            String strImages = StringUtils.join(images, ",");
            wmNews.setImages(strImages);
        }
        if (dto.getType() == -1) {
            wmNews.setType(null);
        }
        //saveOrUpdate
        saveOrUpdateWmNews(wmNews);
        //如果是草稿->return success
        if (dto.getStatus() == 0) {
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }
        //不是草稿，保存文章内容图片 与 素材关系
        //根据内容content中的url 得到 素材信息，从而根据素材id 关联和文章的关系，type为内容
        List<String> materialUrls = getUrlsByContext(dto.getContent());
        if(!CollectionUtils.isEmpty(materialUrls)){
            saveRelation(materialUrls, wmNews.getId(), (short) 0);//mybatis 会在保存后回传id
        }
        //保存文章封面与素材关系 考虑匹配规则
        saveRelationCover(dto, wmNews, materialUrls);

//        wmNewsAutoScanService.autoScanWmNews(wmNews.getId());
        wmNewsTaskService.addNewsToTask(wmNews.getId(),wmNews.getPublishTime());
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 文章的上下架
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult downOrUp(WmNewsDto dto) {
        if(dto.getId()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //查询文章
        WmNews wmNews=getById(dto.getId());
        if(wmNews==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST,"文章不存在");
        }
        //判断文章状态
        if(!wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"当前文章不是发布状态，不能上下架");
        }
        //修改文章上下架
        if(dto.getEnable()!=null&&dto.getEnable()>-1&&dto.getEnable()<2){
            update(Wrappers.<WmNews>lambdaUpdate().set(WmNews::getEnable,dto.getEnable()).eq(WmNews::getId,wmNews.getId()));
        }
        if(wmNews.getArticleId()!=null){
            Map<String,Object> map=new HashMap<>();
            map.put("articleId",wmNews.getArticleId());
            map.put("enable",dto.getEnable());
            kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC, JSON.toJSONString(map));

        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);

    }

    /**
     * 处理封面图片 更新 wmNews 保存文章封面和素材关系
     *
     * @param dto
     * @param wmNews
     * @param materialUrls
     */
    private void saveRelationCover(WmNewsDto dto, WmNews wmNews, List<String> materialUrls) {
        List<String> images = dto.getImages();
        if (dto.getType() == -1) {
            //单图 多图 无图
            if (materialUrls.size() >= 3) {
                wmNews.setType((short) 3);
                images = materialUrls.stream().limit(3).collect(Collectors.toList());
            } else if (materialUrls.size() >= 1 && materialUrls.size() < 3) {
                wmNews.setType((short) 1);
                images = materialUrls.stream().limit(1).collect(Collectors.toList());
            } else {
                wmNews.setType((short) 0);
            }
            if (!CollectionUtils.isEmpty(images)) {
                wmNews.setImages(StringUtils.join(images, ","));
            }
            wmNews.setPublishTime(new Date());
            updateById(wmNews);
        }
        if (!CollectionUtils.isEmpty(images)) {
            saveRelation(images, wmNews.getId(), (short) 1);
        }


    }

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    /**
     * 根据类型保存文章和素材的关系
     *
     * @param materialUrls
     * @param newsId
     * @param type
     */
    private void saveRelation(List<String> materialUrls, Integer newsId, short type) {
        LambdaQueryWrapper<WmMaterial> in = new LambdaQueryWrapper<WmMaterial>().in(WmMaterial::getUrl, materialUrls);
        List<WmMaterial> wmMaterials = wmMaterialMapper.selectList(in);
        if (wmMaterials.size() < materialUrls.size()) {
            throw new RuntimeException("请删除失效图片后重试！");
        }
        List<Integer> idList = wmMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());
        wmNewsMaterialMapper.saveRelations(idList, newsId, type);

    }

    /**
     * 根据内容获取 内容中的所有图片
     *
     * @param content
     * @return
     */
    private List<String> getUrlsByContext(String content) {
        List<String> materialUrls = new ArrayList<>();
        List<Map> maps = JSONArray.parseArray(content, Map.class);
        for (Map map : maps) {
            // map:{ key:type,value:url/text}
            if (map.get("type").equals("image")) {
                String imageUrl = (String) map.get("value");
                materialUrls.add(imageUrl);
            }
        }
        return materialUrls;
    }

    /**
     * 保存或更新 wmNews
     *
     * @param wmNews
     */
    private void saveOrUpdateWmNews(WmNews wmNews) {
        wmNews.setUserId(WmThreadLocal.get().getId());
        wmNews.setCreatedTime(new Date());
//        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short) 1);
        if (wmNews.getId() == null) {
            save(wmNews);
        } else {
            //删除文章-素材 的全部对应关系
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId, wmNews.getId()));
            updateById(wmNews);
        }
    }
}
