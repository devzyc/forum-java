package pub.developers.forum.facade.validator;

import pub.developers.forum.api.request.AdminBooleanRequest;
import pub.developers.forum.api.request.article.ArticleSaveArticleRequest;
import pub.developers.forum.common.enums.ErrorCodeEn;
import pub.developers.forum.common.support.CheckUtil;
import org.springframework.util.ObjectUtils;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
public class ArticleValidator {

    public static void validatorBooleanRequest(AdminBooleanRequest booleanRequest) {
        CheckUtil.checkParamToast(booleanRequest, "request");
        CheckUtil.checkParamToast(booleanRequest.getId(), "id");
        CheckUtil.checkParamToast(booleanRequest.getIs(), "is");
    }

    public static void saveArticle(ArticleSaveArticleRequest request) {
        CheckUtil.checkParamToast(request, "request");
        CheckUtil.checkParamToast(request.getTitle(), "title");
        CheckUtil.checkParamToast(request.getHtmlContent(), "htmlContent");
        // tagIds 与 tagNames 至少传一个（纯文字标签新建场景允许 tagIds 为空）
        CheckUtil.isFalse(!ObjectUtils.isEmpty(request.getTagIds()) || !ObjectUtils.isEmpty(request.getTagNames()), ErrorCodeEn.TAG_NOT_EMPTY);
    }
}
