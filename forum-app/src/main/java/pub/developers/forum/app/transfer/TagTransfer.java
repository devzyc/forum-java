package pub.developers.forum.app.transfer;

import pub.developers.forum.api.request.tag.TagCreateRequest;
import pub.developers.forum.api.response.tag.TagPageResponse;
import pub.developers.forum.api.response.tag.TagQueryResponse;
import pub.developers.forum.app.support.LoginUserContext;
import pub.developers.forum.common.enums.AuditStateEn;
import pub.developers.forum.common.support.DateUtil;
import pub.developers.forum.common.support.SafesUtil;
import pub.developers.forum.domain.entity.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Qiangqiang.Bian
 * @create 20/9/9
 * @desc
 **/
public class TagTransfer {

    public static Tag toTag(TagCreateRequest request) {
        return Tag.builder()
                .auditState(AuditStateEn.PASS)
                .creatorId(LoginUserContext.getUser().getId())
                .name(request.getName())
                .refCount(0L)
                .build();
    }

    public static List<TagQueryResponse> toTagQueryAllResponses(List<Tag> tags) {
        List<TagQueryResponse> responses = new ArrayList<>();

        SafesUtil.ofList(tags).forEach(tag -> responses.add(toTagQueryAllResponse(tag)));
        return responses;
    }

    public static TagQueryResponse toTagQueryAllResponse(Tag tag) {
        return TagQueryResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .refCount(tag.getRefCount())
                .existed(tag.getExisted())
                .build();
    }

    public static List<TagPageResponse> toTagPageResponses(List<Tag> tags) {
        List<TagPageResponse> responses = new ArrayList<>();

        SafesUtil.ofList(tags).forEach(tag -> responses.add(TagPageResponse.builder()
                .auditState(tag.getAuditState().getDesc())
                .createAt(DateUtil.toyyyyMMddHHmmss(tag.getCreateAt()))
                .creatorId(tag.getCreatorId())
                .description(tag.getDescription())
                .groupName(tag.getGroupName())
                .id(tag.getId())
                .name(tag.getName())
                .refCount(tag.getRefCount())
                .updateAt(DateUtil.toyyyyMMddHHmmss(tag.getUpdateAt()))
                .build()));

        return responses;
    }
}
