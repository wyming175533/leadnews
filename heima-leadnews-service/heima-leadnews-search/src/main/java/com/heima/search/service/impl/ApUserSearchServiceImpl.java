package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.HistorySearchDto;
import com.heima.model.search.pojos.ApUserSearch;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.service.ApUserSearchService;
import com.heima.utils.thread.AppThreadLocalUtil;
import com.mongodb.client.result.DeleteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class ApUserSearchServiceImpl implements ApUserSearchService {

    @Autowired
    private MongoTemplate  mongoTemplate;

    /**
     * 保存用户搜索历史记录
     *
     * @param keyword
     * @param userId
     */
    @Override
    @Async
    public void insert(String keyword, Integer userId) {
        //查询当前用户搜索的关键词，判断是否存在
        Query query=Query.query(Criteria.where("userId").is(userId).and("keyword").is(keyword));
        ApUserSearch apUserSearch = mongoTemplate.findOne(query, ApUserSearch.class);
        //如果存在，更新创建时间
        if(apUserSearch!=null){
           apUserSearch.setCreatedTime(new Date());
           mongoTemplate.save(apUserSearch);
           return;
        }
        //如果不存在，判断当前历史记录总数是否超过10
        apUserSearch=new ApUserSearch();
        apUserSearch.setUserId(userId);
        apUserSearch.setKeyword(keyword);
        apUserSearch.setCreatedTime(new Date());
        Query query1 = Query.query(Criteria.where("userId").is(userId));
        query1.with(Sort.by(Sort.Direction.DESC,"createdTime"));
        List<ApUserSearch> apUserSearches = mongoTemplate.find(query1, ApUserSearch.class);
        if(apUserSearches==null|| apUserSearches.size()<10){
            mongoTemplate.save(apUserSearch);
        }else {
            //获取10条中最早的一条记录，用新的将它替换
            ApUserSearch apUserSearch1 = apUserSearches.get(apUserSearches.size() - 1);

            mongoTemplate.findAndReplace(Query.query(Criteria.where("id").is(apUserSearch1.getUserId())),apUserSearch);

        }

    }

    /**
     * 查询搜索历史
     *
     * @return
     */
    @Override
    public ResponseResult findUserSearch() {
        ApUser apUser= AppThreadLocalUtil.get();
        if(apUser==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        List<ApUserSearch> apUserSearches = mongoTemplate.find(Query.query(Criteria.where("userId").is(apUser.getId()))
                .with(Sort.by(Sort.Direction.DESC, "createdTime")), ApUserSearch.class);
        return ResponseResult.okResult(apUserSearches);
    }

    /**
     * 删除搜索历史
     *
     * @param historySearchDto
     * @return
     */
    @Override
    public ResponseResult delUserSearch(HistorySearchDto historySearchDto) {
        if(historySearchDto.getId()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApUser apUser=AppThreadLocalUtil.get();
        if(apUser==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        DeleteResult remove = mongoTemplate.remove(Query.query(Criteria.where("userId").is(apUser.getId()).and("id").is(historySearchDto.getId())), ApUserSearch.class);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);

    }
}
