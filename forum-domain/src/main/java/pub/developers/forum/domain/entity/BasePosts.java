package pub.developers.forum.domain.entity;

import lombok.Data;
import pub.developers.forum.common.enums.AuditStateEn;

import java.util.Set;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
@Data
public class BasePosts extends BaseEntity {

    private AuditStateEn auditState;

    private User author;

    private String title;

    private String htmlContent;

    private Set<Tag> tags;

    private Long views;

    private Long approvals;

    private Long comments;

    private Long authorId;

    private Boolean official;

    private Boolean top;

    private Long sort;

    private Boolean marrow;
}
