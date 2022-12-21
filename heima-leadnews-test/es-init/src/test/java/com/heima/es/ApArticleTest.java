package com.heima.es;

import com.alibaba.fastjson.JSON;
import com.heima.es.mapper.ApArticleMapper;
import com.heima.es.pojo.SearchArticleVo;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ApArticleTest {

    /**
     * 注意：数据量的导入，如果数据量过大，需要分页导入
     * @throws Exception
     */
    @Autowired
    private ApArticleMapper apArticleMapper;
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Test
    public void init() throws Exception {
        //查询所有符合条件的文章数据
        List<SearchArticleVo> searchArticleVoList=apArticleMapper.loadArticleList();
        //批量导入到es索引库  批量请求
        BulkRequest bulkRequest=new BulkRequest("app_info_article");
        for (SearchArticleVo searchArticleVo : searchArticleVoList) {
            //索引请求
            IndexRequest indexRequest=new IndexRequest().id(searchArticleVo.getId().toString())
                    .source(JSON.toJSONString(searchArticleVo), XContentType.JSON);
            //加入
            bulkRequest.add(indexRequest);
        }
        restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);

    }

}