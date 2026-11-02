package pub.developers.forum.portal.controller.rest;

import org.springframework.web.bind.annotation.*;
import pub.developers.forum.api.model.PageRequestModel;
import pub.developers.forum.api.model.PageResponseModel;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.request.article.ArticleSaveArticleRequest;
import pub.developers.forum.api.response.article.ArticleInfoResponse;
import pub.developers.forum.api.response.article.ArticleQueryTypesResponse;
import pub.developers.forum.api.response.comment.CommentPageResponse;
import pub.developers.forum.api.service.ArticleApiService;
import pub.developers.forum.api.service.CommentApiService;
import pub.developers.forum.portal.support.WebUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
@RestController
@RequestMapping("/article-rest")
public class ArticleRestController {

    @Resource
    private ArticleApiService articleApiService;

    @Resource
    private CommentApiService commentApiService;

    @PostMapping("/save")
    public ResultModel<Long> save(@RequestBody ArticleSaveArticleRequest articleRequest) {
        return articleApiService.saveArticle(articleRequest);
    }

    @PostMapping("/{id}")
    public ResultModel<ArticleInfoResponse> get(@PathVariable("id") Long id) {
        return articleApiService.info(id);
    }

    @PostMapping("/editArticleTypes")
    public ResultModel<List<ArticleQueryTypesResponse>> getAllType() {
        return articleApiService.queryEditArticleTypes();
    }

    /**
     * 获取指定文章的评论列表 REST API
     * 通过可选参数控制是否分页
     * 当 pageNo 和 pageSize 都未提供时，返回所有评论
     * 当提供 pageNo 和 pageSize 时，返回分页结果
     */
    @GetMapping("/comments/{articleId}")
    public ResultModel<?> getArticleComments(
            @PathVariable("articleId") Long articleId,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        PageRequestModel<Long> pageRequestModel = new PageRequestModel<>();
        pageRequestModel.setPageNo(pageNo == null ? 1 : pageNo);
        pageRequestModel.setPageSize(pageSize == null ? 9999 : pageSize);
        pageRequestModel.setFilter(articleId);

        ResultModel<PageResponseModel<CommentPageResponse>> result = commentApiService.page(pageRequestModel);

        // 未分页时只返回列表数据
        if (pageNo == null || pageSize == null) {
            if (result.getSuccess() && result.getData() != null) {
                return WebUtil.success(result.getData().getList());
            }
            return WebUtil.success(new ArrayList<>());
        }

        return result;
    }
}
