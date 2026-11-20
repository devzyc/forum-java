package pub.developers.forum.api.service;

import pub.developers.forum.api.model.PageRequestModel;
import pub.developers.forum.api.model.PageResponseModel;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.request.AdminBooleanRequest;
import pub.developers.forum.api.request.article.*;
import pub.developers.forum.api.response.article.ArticleInfoResponse;
import pub.developers.forum.api.response.article.ArticleUserPageResponse;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
public interface ArticleApiService {

    ResultModel<PageResponseModel<ArticleUserPageResponse>> adminPage(PageRequestModel<ArticleAdminPageRequest> pageRequestModel);

    ResultModel<Long> saveArticle(ArticleSaveArticleRequest request);

    ResultModel<PageResponseModel<ArticleUserPageResponse>> authorPage(PageRequestModel<ArticleAuthorPageRequest> pageRequestModel);

    ResultModel<ArticleInfoResponse> info(Long id);

    ResultModel adminTop(AdminBooleanRequest booleanRequest);

    ResultModel adminOfficial(AdminBooleanRequest booleanRequest);

    ResultModel adminMarrow(AdminBooleanRequest booleanRequest);
}
