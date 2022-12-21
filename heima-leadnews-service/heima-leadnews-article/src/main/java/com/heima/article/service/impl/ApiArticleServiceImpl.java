package com.heima.article.service.impl;

import com.aliyuncs.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
@Service
public class ApiArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {
    /**
     * 根据参数加载文章列表
     *
     * @param loadtype 1为加载更多  2为加载最新
     * @param dto
     * @return
     */
    @Autowired
    private ApArticleMapper apArticleMapper;
    @Autowired
    private ApArticleContentMapper apArticleContentMapper;
    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;
    @Override
    public ResponseResult load(Short loadtype, ArticleHomeDto dto) {
        //校验参数
        Integer size=dto.getSize();
        if(size==null||size==0){
            size= ArticleConstants.BASE_PAGE_LIMIT;
        }
        size=Math.min(size,ArticleConstants.MAX_PAGE_LIMIT);
        dto.setSize(size);

        if(!loadtype.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !loadtype.equals(ArticleConstants.LOADTYPE_LOAD_NEW)){
            loadtype=ArticleConstants.LOADTYPE_LOAD_MORE;
        }
        if(StringUtils.isEmpty(dto.getTag())){
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }
        if(dto.getMaxBehotTime()==null){
            dto.setMaxBehotTime(new Date());
        }
        if(dto.getMinBehotTime()==null){
            dto.setMinBehotTime(new Date());
        }

        List<ApArticle> articles=apArticleMapper.loadArticleList(dto,loadtype);


        ResponseResult responseResult=ResponseResult.okResult(articles);
        return responseResult;

    }

    /**保存app端文章 config content
     * @param articleDto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto articleDto) {
    if(articleDto==null){
        return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
    }
    //new article copy参数
        ApArticle article = new ApArticle();
        BeanUtils.copyProperties(articleDto,article);
        //判断存在id？
        if(article.getId()==null){
            //save article config content
            save(article);

            ApArticleConfig apArticleConfig=new ApArticleConfig(article.getId());
            apArticleConfigMapper.insert(apArticleConfig);

            ApArticleContent apArticleContent=new ApArticleContent();
            apArticleContent.setContent(articleDto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        }else {
            //updateById  不用更新配置
            updateById(article);

            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(new LambdaQueryWrapper<ApArticleContent>().eq(ApArticleContent::getArticleId, articleDto.getId()));
            apArticleContent.setContent(articleDto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }
        //异步调用 生成静态文件上传到minio中
        articleFreemarkerService.buildArticleToMinIO(article, articleDto.getContent());
        //返回插入后的id
        return ResponseResult.okResult(article.getId());
    }
}
