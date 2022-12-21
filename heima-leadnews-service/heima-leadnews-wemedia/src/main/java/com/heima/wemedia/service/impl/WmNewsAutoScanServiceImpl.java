package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.apis.article.IArticleClient;
import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.common.tess4j.Tess4jClient;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.common.SensitiveWordUtil;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmChannelService;
import com.heima.wemedia.service.WmNewsAutoScanService;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.imageio.ImageIO;
import javax.jws.Oneway;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsAutoScanServiceImpl implements WmNewsAutoScanService {

    @Autowired
    private WmNewsMapper wmNewsMapper;
    @Autowired
    private WmChannelMapper wmChannelMapper;
    @Autowired
    private WmUserMapper wmUserMapper;
    /**
     * 自媒体文章审核
     *
     * @param id 自媒体文章id
     */

    @Override
    @Async
    public void autoScanWmNews(Integer id) {
        //根据id 查询出自媒体文章
        WmNews wmnews = wmNewsMapper.selectById(id);
        if(wmnews==null){
            log.debug("没有查询到文章信息，或许已经被删除,id={}",id);
            return;
        }

        //只有状态为发布状态 才进行审核
        if(wmnews.getStatus().equals(WmNews.Status.SUBMIT.getCode())){
            //提取出内容中的文本和图片
            Map<String, Object> textAndImages=handleTextAndImages(wmnews);
            //自管理的敏感词过滤
            boolean isSensitive=handleSensitiveScan((String)textAndImages.get("content"),wmnews);
            if(!isSensitive){
                return;
            }
            //文本审核
            boolean textScan=handleTextScan((String)textAndImages.get("content"),wmnews);
            if(!textScan){
                return;
            }
            //审核图片内容
            boolean imageScan=handlerImageScan((Set<String>)textAndImages.get("images"),wmnews);
            if(!imageScan){
                return;
            }
            //审核都通过，保存app端文章数据
            ResponseResult responseResult=saveAppArticle(wmnews);
            if(!responseResult.getCode().equals(200)){
                //定时任务重新审核  或者 加入到人工审核处理  重新审核后发布article
                throw new RuntimeException("wmNewsAutoScanServiceImpl-文章审核保存app文章失败");
            }
            wmnews.setArticleId((Long) responseResult.getData());
            //回填id
            updateWmNews(wmnews, WmNews.Status.PUBLISHED,"审核成功");

        }

    }

    @Autowired
    private WmSensitiveMapper wmSensitiveMapper;
    /**
     * 自管理敏感词审核
     * @param content
     * @param wmnews
     * @return
     */
    private boolean handleSensitiveScan(String content, WmNews wmnews) {
        List<WmSensitive> wmSensitives = wmSensitiveMapper.
                selectList(Wrappers.<WmSensitive>lambdaQuery()
                .select(WmSensitive::getSensitives));
       List<String>  sensitiveList=wmSensitives.stream().map(WmSensitive::getSensitives).collect(Collectors.toList());
        //应该在项目启动时初始化，这里用锁让它在调用时只初始化一次，不如前者性能好
       if(CollectionUtils.isEmpty(sensitiveList)){
            if(CollectionUtils.isEmpty(SensitiveWordUtil.dictionaryMap)){
                synchronized (this){
                    if(CollectionUtils.isEmpty(SensitiveWordUtil.dictionaryMap)){
                        SensitiveWordUtil.initMap(sensitiveList);
                    }
                }
            }
        }
        Map<String, Integer> map=SensitiveWordUtil.matchWords(content+"-"+wmnews.getTitle());
        if(map.size()>0){
            updateWmNews(wmnews, WmNews.Status.FAIL,"当前文章存在违规内容"+map);
            return false;
        }
        return true;

    }


    @Autowired
    private  IArticleClient  iArticleClient;
    private ResponseResult saveAppArticle(WmNews wmnews) {
        ArticleDto dto = new ArticleDto();
        //属性的拷贝
        BeanUtils.copyProperties(wmnews,dto);
        //文章的布局
        dto.setLayout(wmnews.getType());
        //频道
        WmChannel wmChannel=wmChannelMapper.selectById(wmnews.getChannelId());
        if(wmChannel != null){
            dto.setChannelName(wmChannel.getName());
        }
        //作者
        dto.setAuthorId(wmnews.getUserId().longValue());
        WmUser wmUser = wmUserMapper.selectById(wmnews.getUserId());
        if(wmUser != null){
            dto.setAuthorName(wmUser.getName());
        }
        //设置文章id  更新用
        if(wmnews.getArticleId() != null){
            dto.setId(wmnews.getArticleId());
        }
        dto.setCreatedTime(new Date());
        System.out.println(iArticleClient.getClass());
        ResponseResult responseResult=iArticleClient.saveArticle(dto);
        return responseResult;
    }

    @Autowired
    private GreenTextScan greenTextScan;
    @Autowired
    private GreenImageScan greenImageScan;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private Tess4jClient tess4jClient;

    /**
     * 图片审核
     * @param images
     * @param wmnews
     * @return
     */
    private boolean handlerImageScan(Set<String> images, WmNews wmnews) {
        if(CollectionUtils.isEmpty(images)){
            return true;
        }
        List<byte[]> imageBytes=new ArrayList<>();
        for (String image : images) {
            byte[] bytes = fileStorageService.downLoadFile(image);
            //对每一个图片做文字识别处理
            ByteArrayInputStream in=new ByteArrayInputStream(bytes);
            //识别文字
            String result= null;
            try {
                BufferedImage imageFile= ImageIO.read(in);
                result = tess4jClient.doOCR(imageFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            //敏感词处理
            boolean isSensitive=handleSensitiveScan(result,wmnews);
            if(!isSensitive){
                return false;
            }

            //
            imageBytes.add(bytes);
        }
        //图片审核
        try {
            Map map = greenImageScan.imageScan(imageBytes);
            if(map!=null){
                if(map.get("suggestion").equals("block")){
                    updateWmNews(wmnews, WmNews.Status.FAIL,"当前文章存在违规内容");
                    return false;
                }
                if(map.get("suggestion").equals("review")){
                    updateWmNews(wmnews, WmNews.Status.ADMIN_AUTH,"当前文章存在不确定内容");
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }


        return true;
    }

    /**
     * 文章审核
     * @param content
     * @param wmnews
     * @return
     */
    private boolean handleTextScan(String content, WmNews wmnews) {
        if((wmnews.getTitle()+content).length()==0){
            return true;
        }
        try{
            Map map = greenTextScan.greeTextScan(wmnews.getTitle() + content);
            if(map!=null){
                if(map.get("suggestion").equals("block")){
                    updateWmNews(wmnews,WmNews.Status.FAIL,"当前文章中存在违规内容");
                    return false;
                }
                if(map.get("suggestion").equals("review")){
                    //不确定 需要人工审核
                    updateWmNews(wmnews,WmNews.Status.ADMIN_AUTH,"当前文章中存在不确定内容");
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * 更新文章内容
     * @param wmnews
     * @param
     * @param
     */
    private void updateWmNews(WmNews wmnews, WmNews.Status status, String message) {
        wmnews.setStatus(status.getCode());
        wmnews.setReason(message);
        wmNewsMapper.updateById(wmnews);
    }

    private Map<String, Object> handleTextAndImages(WmNews wmnews) {
        String content = wmnews.getContent();
        //拼接文本
        StringBuilder text=new StringBuilder();
        //添加图片路径
        Set<String> images=new HashSet<>();
        List<Map> maps = JSONArray.parseArray(content, Map.class);
        for (Map map : maps) {
            if(map.get("type").equals("text")){
                text.append(map.get("value"));
            }
            if(map.get("type").equals("image")){
                images.add((String) map.get("value"));
            }
        }
        //封面
        if(StringUtils.isNotBlank(wmnews.getImages())){
            String[]split=wmnews.getImages().split(",");
            images.addAll(Arrays.asList(split));
        }
        Map<String,Object> resultMap=new HashMap<>();
        resultMap.put("content",text.toString());
        resultMap.put("images",images);
        return resultMap;
    }
}
