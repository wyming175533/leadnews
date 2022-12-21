package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.UserSearchDto;
import com.heima.model.search.pojos.ApAssociateWords;
import com.heima.search.service.ApAssociateWordsService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApAssociateWordsServiceImpl implements ApAssociateWordsService {
    /**
     * 联想词
     *
     * @param userSearchDto
     * @return
     */
    @Autowired
    private MongoTemplate mongoTemplate;
    @Override
    public ResponseResult findAssociate(UserSearchDto userSearchDto) {
        if(userSearchDto==null|| StringUtils.isBlank(userSearchDto.getSearchWords())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        if(userSearchDto.getPageSize()>20){
            userSearchDto.setPageSize(20);
        }
        Query query = Query.query(Criteria.where("associateWords").regex((".*?\\" + userSearchDto.getSearchWords() + ".*")));
        query.limit(userSearchDto.getPageSize());
        List<ApAssociateWords> apAssociateWords =
                mongoTemplate.find(query, ApAssociateWords.class);
        return ResponseResult.okResult(apAssociateWords);
    }
}
