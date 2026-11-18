package pub.developers.forum.app.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import pub.developers.forum.api.model.PageRequestModel;
import pub.developers.forum.api.model.PageResponseModel;
import pub.developers.forum.api.request.AdminBooleanRequest;
import pub.developers.forum.api.request.article.*;
import pub.developers.forum.api.response.article.ArticleInfoResponse;
import pub.developers.forum.api.response.article.ArticleQueryTypesResponse;
import pub.developers.forum.api.response.article.ArticleUserPageResponse;
import pub.developers.forum.app.support.IsLogin;
import pub.developers.forum.app.support.LoginUserContext;
import pub.developers.forum.app.support.PageUtil;
import pub.developers.forum.app.support.Pair;
import pub.developers.forum.app.transfer.ArticleTransfer;
import pub.developers.forum.common.enums.*;
import pub.developers.forum.common.model.PageResult;
import pub.developers.forum.common.support.CheckUtil;
import pub.developers.forum.common.support.EventBus;
import pub.developers.forum.common.support.SafesUtil;
import pub.developers.forum.domain.entity.*;
import pub.developers.forum.domain.entity.value.PostsPageQueryValue;
import pub.developers.forum.domain.repository.ArticleRepository;
import pub.developers.forum.domain.repository.ArticleTypeRepository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
@Slf4j
@Component
public class ArticleManager extends AbstractPostsManager {

    @Resource
    private ArticleTypeRepository articleTypeRepository;

    @Resource
    private ArticleRepository articleRepository;

    private static final Long INIT_SORT = 1000L;

    /**
     * 单篇文章标签上限（tagIds + tagNames 合并后），防止批量制造空标签
     */
    private static final int MAX_TAG_COUNT = 20;

    @Resource
    private TagManager tagManager;

    @IsLogin
    @Transactional
    public Long saveArticle(ArticleSaveArticleRequest request) {
        // 创建
        if (ObjectUtils.isEmpty(request.getId())) {
            // 校验类型
            ArticleType articleType = checkArticleType(request.getTypeId());

            // 校验标签（已有 tagIds + 用户新敲文字标签 tagNames 合并解析）
            Set<Tag> selectTags = resolveTags(request.getTagIds(), request.getTagNames());

            Article article = ArticleTransfer.toArticle(request, articleType, selectTags, false, INIT_SORT);
            articleRepository.save(article);

            // 触发文章创建事件
            EventBus.emit(EventBus.Topic.ARTICLE_CREATE, article);

            return article.getId();
        }

        // 更新
        // 校验文章
        Article article = articleRepository.get(request.getId());
        CheckUtil.isEmpty(article, ErrorCodeEn.ARTICLE_NOT_EXIST);
        CheckUtil.isFalse(LoginUserContext.getUser().getId().equals(article.getAuthor().getId()), ErrorCodeEn.ARTICLE_NOT_EXIST);

        // 校验类型
        ArticleType articleType = checkArticleType(request.getTypeId());

        // 校验标签（已有 tagIds + 用户新敲文字标签 tagNames 合并解析）
        Set<Tag> selectTags = resolveTags(request.getTagIds(), request.getTagNames());

        // 删除旧标签关联关系
        tagRepository.deletePostsMapping(request.getId());

        Article oldArticle = article.copy();
        Article newArticle = ArticleTransfer.toArticle(request, articleType, selectTags, true, INIT_SORT);

        articleRepository.update(newArticle);

        // 触发文章更新事件
        EventBus.emit(EventBus.Topic.ARTICLE_UPDATE, Pair.build(oldArticle, newArticle));

        return request.getId();
    }

    public List<ArticleQueryTypesResponse> queryAllTypes() {
        List<ArticleType> articleTypes = articleTypeRepository.queryByState(AuditStateEn.PASS);

        return ArticleTransfer.toArticleQueryTypesResponses(articleTypes);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public List<ArticleQueryTypesResponse> queryAdminTypes() {
        List<ArticleType> articleTypes = articleTypeRepository.queryByState(null);

        return ArticleTransfer.toArticleQueryTypesResponses(articleTypes);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public PageResponseModel<ArticleQueryTypesResponse> typePage(PageRequestModel<ArticleAdminTypePageRequest> pageRequestModel) {
        ArticleAdminTypePageRequest typePageRequest = pageRequestModel.getFilter();
        ArticleType articleType = ArticleType.builder()
                .name(typePageRequest.getName())
                .description(typePageRequest.getDescription())
                .build();
        if (!ObjectUtils.isEmpty(typePageRequest.getAuditState())) {
            articleType.setAuditState(AuditStateEn.getEntity(typePageRequest.getAuditState()));
        }
        if (!ObjectUtils.isEmpty(typePageRequest.getScope())) {
            articleType.setScope(ArticleTypeScopeEn.getEntity(typePageRequest.getScope()));
        }
        PageResult<ArticleType> pageResult = articleTypeRepository.page(PageUtil.buildPageRequest(pageRequestModel, articleType));

        return PageResponseModel.build(pageResult.getTotal(), pageResult.getSize(), ArticleTransfer.toArticleQueryTypesResponses(pageResult.getList()));
    }

    @IsLogin
    public List<ArticleQueryTypesResponse> queryEditArticleTypes() {
        List<ArticleTypeScopeEn> scopes = new ArrayList<>();
        scopes.add(ArticleTypeScopeEn.USER);

        User loginUser = LoginUserContext.getUser();
        if (!UserRoleEn.USER.equals(loginUser.getRole() )) {
            scopes.add(ArticleTypeScopeEn.OFFICIAL);
        }

        List<ArticleType> articleTypes = articleTypeRepository.queryByScopesAndState(scopes, AuditStateEn.PASS);

        return ArticleTransfer.toArticleQueryTypesResponses(articleTypes);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public void addType(ArticleAddTypeRequest request) {
        CheckUtil.isNotEmpty(articleTypeRepository.query(ArticleType.builder()
                .name(request.getName())
                .build()), ErrorCodeEn.ARTICLE_TYPE_IS_EXIST);

        articleTypeRepository.save(ArticleTransfer.toArticleType(request));
    }

    public PageResponseModel<ArticleUserPageResponse> userPage(PageRequestModel<ArticleUserPageRequest> pageRequestModel) {
        PostsPageQueryValue pageQueryValue = PostsPageQueryValue.builder()
                .auditStates(Arrays.asList(AuditStateEn.PASS.getValue()))
                .build();

        ArticleUserPageRequest pageRequest = pageRequestModel.getFilter();
        if (!ObjectUtils.isEmpty(pageRequest.getTypeName())) {
            ArticleType articleType = articleTypeRepository.getByNameAndState(pageRequest.getTypeName(), AuditStateEn.PASS);
            CheckUtil.isEmpty(articleType, ErrorCodeEn.ARTICLE_TYPE_IS_EXIST);

            pageQueryValue.setTypeId(articleType.getId());
        }

        return pageQuery(pageRequestModel, pageQueryValue);
    }

    public PageResponseModel<ArticleUserPageResponse> authorPage(PageRequestModel<ArticleAuthorPageRequest> pageRequestModel) {
        ArticleAuthorPageRequest request = pageRequestModel.getFilter();

        List<String> auditStates = new ArrayList<>();
        auditStates.add(AuditStateEn.PASS.getValue());

        User user = LoginUserContext.getUser();
        if (!ObjectUtils.isEmpty(user) && user.getId().equals(request.getUserId())) {
            auditStates.add(AuditStateEn.WAIT.getValue());
            auditStates.add(AuditStateEn.REJECT.getValue());
        }

        PostsPageQueryValue pageQueryValue = PostsPageQueryValue.builder()
                .auditStates(auditStates)
                .authorId(request.getUserId())
                .build();

        return pageQuery(pageRequestModel, pageQueryValue);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public PageResponseModel<ArticleUserPageResponse> adminPage(PageRequestModel<ArticleAdminPageRequest> pageRequestModel) {
        ArticleAdminPageRequest request = pageRequestModel.getFilter();

        PostsPageQueryValue pageQueryValue = PostsPageQueryValue.builder()
                .category(PostsCategoryEn.ARTICLE.getValue())
                .authorId(request.getUserId())
                .typeId(request.getTypeId())
                .title(request.getTitle())
                .official(request.getOfficial())
                .marrow(request.getMarrow())
                .top(request.getTop())
                .build();
        if (!ObjectUtils.isEmpty(request.getAuditState()) && !ObjectUtils.isEmpty(AuditStateEn.getEntity(request.getAuditState()))) {
            List<String> auditStates = new ArrayList<>();
            auditStates.add(AuditStateEn.getEntity(request.getAuditState()).getValue());
            pageQueryValue.setAuditStates(auditStates);
        }

        return pageQuery(pageRequestModel, pageQueryValue);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public void adminTop(AdminBooleanRequest booleanRequest) {
        BasePosts basePosts = postsRepository.get(booleanRequest.getId());
        CheckUtil.isEmpty(basePosts, ErrorCodeEn.ARTICLE_NOT_EXIST);

        basePosts.setSort(booleanRequest.getIs() ? 1L : INIT_SORT);
        basePosts.setTop(booleanRequest.getIs());
        postsRepository.update(basePosts);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public void adminOfficial(AdminBooleanRequest booleanRequest) {
        BasePosts basePosts = postsRepository.get(booleanRequest.getId());
        CheckUtil.isEmpty(basePosts, ErrorCodeEn.ARTICLE_NOT_EXIST);

        basePosts.setOfficial(booleanRequest.getIs());
        postsRepository.update(basePosts);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public void adminMarrow(AdminBooleanRequest booleanRequest) {
        BasePosts basePosts = postsRepository.get(booleanRequest.getId());
        CheckUtil.isEmpty(basePosts, ErrorCodeEn.ARTICLE_NOT_EXIST);

        basePosts.setMarrow(booleanRequest.getIs());
        postsRepository.update(basePosts);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public void typeAuditState(AdminBooleanRequest booleanRequest) {
        ArticleType articleType = articleTypeRepository.get(booleanRequest.getId());
        CheckUtil.isEmpty(articleType, ErrorCodeEn.ARTICLE_TYPE_IS_EXIST);

        articleType.setAuditState(booleanRequest.getIs() ? AuditStateEn.PASS : AuditStateEn.REJECT);
        articleTypeRepository.update(articleType);
    }

    public ArticleInfoResponse info(Long id) {
        Article article = articleRepository.get(id);
        CheckUtil.isEmpty(article, ErrorCodeEn.ARTICLE_NOT_EXIST);

        if (!AuditStateEn.PASS.equals(article.getAuditState())) {
            User user = LoginUserContext.getUser();
            CheckUtil.isEmpty(user, ErrorCodeEn.ARTICLE_IN_AUDIT_PROCESS);
            CheckUtil.isFalse(user.getId().equals(article.getAuthor().getId()), ErrorCodeEn.ARTICLE_IN_AUDIT_PROCESS);
        }

        // 触发文章查看详情事件
        EventBus.emit(EventBus.Topic.POSTS_INFO, article);

        return ArticleTransfer.toArticleInfoResponse(article);
    }

    private PageResponseModel<ArticleUserPageResponse> pageQuery(PageRequestModel pageRequestModel, PostsPageQueryValue pageQueryValue) {
        pageQueryValue.setCategory(PostsCategoryEn.ARTICLE.getValue());

        PageResult<Article> pageResult = articleRepository.page(pageRequestModel.getPageNo(), pageRequestModel.getPageSize(), pageQueryValue);

        return PageResponseModel.build(pageResult.getTotal(), pageResult.getSize(), ArticleTransfer.toArticleUserPageResponses(pageResult.getList()));
    }

    private Set<Tag> resolveTags(Set<Long> tagIds, Set<String> tagNames) {
        // 合并已有标签 ID 与新文字标签解析出的 ID（新名按名复用或自动创建），去重
        Set<Long> mergedIds = new HashSet<>(SafesUtil.ofSet(tagIds));
        for (String name : SafesUtil.ofSet(tagNames)) {
            if (!ObjectUtils.isEmpty(name) && !name.trim().isEmpty()) {
                mergedIds.add(tagManager.resolveOrCreate(name.trim()).getId());
            }
        }

        CheckUtil.isTrue(mergedIds.size() > MAX_TAG_COUNT, ErrorCodeEn.TAG_COUNT_EXCEED_LIMIT);

        return checkTags(mergedIds);
    }

    private ArticleType checkArticleType(Long typeId) {
        ArticleType articleType = articleTypeRepository.get(typeId);
        CheckUtil.isEmpty(articleType, ErrorCodeEn.ARTICLE_TYPE_IS_EXIST);
        CheckUtil.isFalse(AuditStateEn.PASS.equals(articleType.getAuditState()), ErrorCodeEn.ARTICLE_TYPE_IS_EXIST);

        return articleType;
    }

}
