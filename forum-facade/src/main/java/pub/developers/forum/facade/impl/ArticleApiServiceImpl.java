package pub.developers.forum.facade.impl;

import org.springframework.stereotype.Service;
import pub.developers.forum.api.model.PageRequestModel;
import pub.developers.forum.api.model.PageResponseModel;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.request.AdminBooleanRequest;
import pub.developers.forum.api.request.article.*;
import pub.developers.forum.api.response.article.ArticleInfoResponse;
import pub.developers.forum.api.response.article.ArticleUserPageResponse;
import pub.developers.forum.api.service.ArticleApiService;
import pub.developers.forum.app.manager.ArticleManager;
import pub.developers.forum.facade.support.ResultModelUtil;
import pub.developers.forum.facade.validator.ArticleValidator;
import pub.developers.forum.facade.validator.PageRequestModelValidator;

import javax.annotation.Resource;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
@Service
public class ArticleApiServiceImpl implements ArticleApiService {

    @Resource
    private ArticleManager articleManager;

    @Override
    public ResultModel<PageResponseModel<ArticleUserPageResponse>> adminPage(PageRequestModel<ArticleAdminPageRequest> pageRequestModel) {
        PageRequestModelValidator.validator(pageRequestModel);

        return ResultModelUtil.success(articleManager.adminPage(pageRequestModel));
    }

    @Override
    public ResultModel<Long> saveArticle(ArticleSaveArticleRequest request) {
        ArticleValidator.saveArticle(request);

        return ResultModelUtil.success(articleManager.saveArticle(request));
    }

    @Override
    public ResultModel<PageResponseModel<ArticleUserPageResponse>> authorPage(PageRequestModel<ArticleAuthorPageRequest> pageRequestModel) {
        PageRequestModelValidator.validator(pageRequestModel);

        return ResultModelUtil.success(articleManager.authorPage(pageRequestModel));
    }

    @Override
    public ResultModel<ArticleInfoResponse> info(Long id) {

        return ResultModelUtil.success(articleManager.info(id));
    }

    @Override
    public ResultModel adminTop(AdminBooleanRequest booleanRequest) {
        ArticleValidator.validatorBooleanRequest(booleanRequest);

        articleManager.adminTop(booleanRequest);

        return ResultModelUtil.success();
    }

    @Override
    public ResultModel adminOfficial(AdminBooleanRequest booleanRequest) {
        ArticleValidator.validatorBooleanRequest(booleanRequest);

        articleManager.adminOfficial(booleanRequest);

        return ResultModelUtil.success();
    }

    @Override
    public ResultModel adminMarrow(AdminBooleanRequest booleanRequest) {
        ArticleValidator.validatorBooleanRequest(booleanRequest);

        articleManager.adminMarrow(booleanRequest);

        return ResultModelUtil.success();
    }
}
