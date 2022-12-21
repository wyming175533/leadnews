package com.heima.common.constants;

import io.swagger.models.auth.In;

/**
 * @author wangyiming
 */
public class ArticleConstants {
    public static final Integer MAX_PAGE_LIMIT=50;
    public static final Integer BASE_PAGE_LIMIT=50;



    public static final Short LOADTYPE_LOAD_MORE = 1;
    public static final Short LOADTYPE_LOAD_NEW = 2;
    public static final String DEFAULT_TAG = "__all__";
    public static final String ARTICLE_ES_SYNC_TOPIC = "article.es.sync.topic";

    public static final Integer HOT_ARTICLE_LIKE_WEIGHT = 3;
    public static final Integer HOT_ARTICLE_COMMENT_WEIGHT = 5;
    public static final Integer HOT_ARTICLE_COLLECTION_WEIGHT = 8;

    public static final String HOT_ARTICLE_FIRST_PAGE = "hot_article_first_page_";
}
