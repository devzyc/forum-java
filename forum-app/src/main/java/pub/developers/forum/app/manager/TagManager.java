package pub.developers.forum.app.manager;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import pub.developers.forum.api.model.PageRequestModel;
import pub.developers.forum.api.model.PageResponseModel;
import pub.developers.forum.api.request.tag.TagCreateRequest;
import pub.developers.forum.api.request.tag.TagPageRequest;
import pub.developers.forum.api.response.tag.TagPageResponse;
import pub.developers.forum.api.response.tag.TagQueryResponse;
import pub.developers.forum.api.vo.PostsVO;
import pub.developers.forum.app.support.IsLogin;
import pub.developers.forum.app.support.PageUtil;
import pub.developers.forum.app.transfer.TagTransfer;
import pub.developers.forum.common.enums.AuditStateEn;
import pub.developers.forum.common.enums.ErrorCodeEn;
import pub.developers.forum.common.enums.UserRoleEn;
import pub.developers.forum.common.model.PageResult;
import pub.developers.forum.common.support.CheckUtil;
import pub.developers.forum.common.support.SafesUtil;
import pub.developers.forum.domain.entity.Posts;
import pub.developers.forum.domain.entity.Tag;
import pub.developers.forum.domain.repository.TagRepository;
import pub.developers.forum.domain.service.CacheService;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Qiangqiang.Bian
 * @create 2020/7/31
 * @desc
 **/
@Component
public class TagManager extends AbstractPostsManager {

    @Resource
    private TagRepository tagRepository;

    @Resource
    private CacheService cacheService;

    public Tag create(TagCreateRequest request) {
        List<Tag> exist = tagRepository.query(Tag.builder().name(request.getName()).build());
        if (!exist.isEmpty()) {
            // 名称已存在：直接复用旧标签，不报错、不重复插入，标记 existed 供前端区分
            Tag existTag = exist.get(0);
            existTag.setExisted(true);
            return existTag;
        }

        Tag tag = TagTransfer.toTag(request);
        tagRepository.save(tag);
        return tag;
    }

    public List<TagQueryResponse> queryAll() {
        return TagTransfer.toTagQueryAllResponses(tagRepository.queryByState(AuditStateEn.PASS));
    }

    public List<TagQueryResponse> queryInIds(Set<Long> ids) {
        List<Tag> tags = tagRepository.queryByIds(ids);

        return TagTransfer.toTagQueryAllResponses(SafesUtil.ofList(tags).stream()
                .filter(tag -> AuditStateEn.PASS.equals(tag.getAuditState()))
                .collect(Collectors.toList()));
    }

    public TagQueryResponse getByName(String name) {
        Tag tag = tagRepository.getByNameAndState(name, AuditStateEn.PASS);
        CheckUtil.isEmpty(tag, ErrorCodeEn.TAG_NOT_EXIST);

        return TagTransfer.toTagQueryAllResponse(tag);
    }

    public PageResponseModel<PostsVO> pagePosts(PageRequestModel<Long> pageRequestModel) {
        PageResult<Posts> pageResult = tagRepository.pagePosts(PageUtil.buildPageRequest(pageRequestModel, pageRequestModel.getFilter()));

        return pagePostsVO(pageResult);
    }

    public PageResponseModel<PostsVO> pagePostsByTagIds(PageRequestModel<Set<Long>> pageRequestModel) {
        PageResult<Posts> pageResult = tagRepository.pagePostsByTagIds(PageUtil.buildPageRequest(pageRequestModel, pageRequestModel.getFilter()));

        return pagePostsVO(pageResult);
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public PageResponseModel<TagPageResponse> page(PageRequestModel<TagPageRequest> pageRequestModel) {
        TagPageRequest tagPageRequest = pageRequestModel.getFilter();
        Tag tag = Tag.builder()
                .groupName(tagPageRequest.getGroupName())
                .name(tagPageRequest.getName())
                .description(tagPageRequest.getDescription())
                .build();
        if (!ObjectUtils.isEmpty(tagPageRequest.getAuditState())) {
            tag.setAuditState(AuditStateEn.getEntity(tagPageRequest.getAuditState()));
        }

        PageResult<Tag> pageResult = tagRepository.page(PageUtil.buildPageRequest(pageRequestModel, tag));

        return PageResponseModel.build(pageResult.getTotal(), pageResult.getSize(), TagTransfer.toTagPageResponses(pageResult.getList()));
    }

    @IsLogin(role = UserRoleEn.ADMIN)
    public void delete(Long id) {
        Tag tag = tagRepository.get(id);
        CheckUtil.isEmpty(tag, ErrorCodeEn.TAG_NOT_EXIST);

        tagRepository.delete(id);
    }

    public List<TagQueryResponse> queryAllRef() {
        List<Tag> tags = tagRepository.queryByState(AuditStateEn.PASS);
        List<Tag> cacheTags = SafesUtil.ofList(tags).stream().filter(tag -> tag.getRefCount() > 0).collect(Collectors.toList());

        return TagTransfer.toTagQueryAllResponses(cacheTags);
    }
}
