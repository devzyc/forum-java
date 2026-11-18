package pub.developers.forum.app.transfer;

import pub.developers.forum.api.response.tag.TagPageResponse;
import pub.developers.forum.api.response.tag.TagQueryResponse;
import pub.developers.forum.app.support.LoginUserContext;
import pub.developers.forum.common.enums.AuditStateEn;
import pub.developers.forum.common.support.DateUtil;
import pub.developers.forum.common.support.SafesUtil;
import pub.developers.forum.domain.entity.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Qiangqiang.Bian
 * @create 20/9/9
 * @desc
 **/
public class TagTransfer {

    public static Tag toTag(String name) {
        return Tag.builder()
                .auditState(AuditStateEn.PASS)
                .creatorId(LoginUserContext.getUser().getId())
                .name(name)
                .refCount(0L)
                .build();
    }

    /**
     * 热门标签数量：按 refCount 排名前 N
     */
    private static final int HOT_TAG_LIMIT = 10;

    public static List<TagQueryResponse> toTagQueryAllResponses(List<Tag> tags) {
        List<TagQueryResponse> responses = new ArrayList<>();

        // 动态计算热门标签：refCount 最高的前 10 个（不入库，每次请求重新算）
        Set<Long> hotIds = SafesUtil.ofList(tags).stream()
                .filter(tag -> tag.getRefCount() != null && tag.getRefCount() > 0)
                .sorted((a, b) -> Long.compare(b.getRefCount(), a.getRefCount()))
                .limit(HOT_TAG_LIMIT)
                .map(Tag::getId)
                .collect(Collectors.toSet());

        SafesUtil.ofList(tags).forEach(tag -> responses.add(toTagQueryAllResponse(tag, hotIds.contains(tag.getId()))));
        return responses;
    }

    public static TagQueryResponse toTagQueryAllResponse(Tag tag) {
        return toTagQueryAllResponse(tag, false);
    }

    public static TagQueryResponse toTagQueryAllResponse(Tag tag, boolean isHot) {
        return TagQueryResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .refCount(tag.getRefCount())
                .isHot(isHot)
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
