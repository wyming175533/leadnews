package com.heima.search.service.impl;


import com.alibaba.fastjson.JSON;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.UserSearchDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.service.ApUserSearchService;
import com.heima.search.service.ArticleSearchService;
import com.heima.utils.thread.AppThreadLocalUtil;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author wangyiming
 */
@Service
public class ArticleSearchServiceImpl implements ArticleSearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @Autowired
    private ApUserSearchService apUserSearchService;
    /**
     * ES文章分页搜索
     *
     * @param
     * @return
     */
    @Override
    public ResponseResult search(UserSearchDto dto) throws IOException {
      //检查参数
        if(dto==null|| StringUtils.isBlank(dto.getSearchWords())){
          return   ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApUser  user= AppThreadLocalUtil.get();
        if(user!=null && dto.getFromIndex()==0){
            apUserSearchService.insert(dto.getSearchWords(),user.getId());
        }
        //es查询条件
        //select from xxx 针对 app_info_article 的搜索操作
        SearchRequest searchRequest=new SearchRequest("app_info_article");
        //搜索条件
        SearchSourceBuilder searchSourceBuilder=new SearchSourceBuilder();
       //由多个布尔搜索条件组成
        BoolQueryBuilder bollQuery= QueryBuilders.boolQuery();
        //填写关键词分词查询
        QueryStringQueryBuilder queryStringQueryBuilder=QueryBuilders.queryStringQuery(dto.getSearchWords())
                .field("title").field("content").defaultOperator(Operator.OR);
        bollQuery.must(queryStringQueryBuilder);
//        范围查询
        RangeQueryBuilder range=QueryBuilders.rangeQuery("publishTime").lt(dto.getMinBehotTime().getTime());
//        添加查询条件
        bollQuery.filter(range);

        //分页查询
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(dto.getPageSize());

        //降序
        searchSourceBuilder.sort("publishTime", SortOrder.DESC);

        //高亮
        HighlightBuilder highlightBuilder=new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<font style='color: red; font-size: inherit;'>");
        highlightBuilder.postTags("</font>");

        searchSourceBuilder.highlighter(highlightBuilder);
        searchSourceBuilder.query(bollQuery);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse=restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        //封装结果
        List<Map> list=new ArrayList<>();

        SearchHit[] hits=searchResponse.getHits().getHits();
        for (SearchHit hit : hits) {
            String json=hit.getSourceAsString();
            Map map= JSON.parseObject(json,Map.class);
            if(hit.getHighlightFields()!=null && hit.getHighlightFields().size()>0){
                //高亮标题
                Text[] titles = hit.getHighlightFields().get("title").getFragments();
                String title= StringUtils.join(titles);
                map.put("h_title",title);
            }else {
                //原始标题
                map.put("h_title",map.get("title"));
            }
            list.add(map);
        }


        return ResponseResult.okResult(list);
    }
}
