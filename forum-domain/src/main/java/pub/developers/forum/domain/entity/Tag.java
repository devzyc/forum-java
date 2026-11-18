package pub.developers.forum.domain.entity;

import lombok.*;
import pub.developers.forum.common.enums.AuditStateEn;

/**
 * @author Qiangqiang.Bian
 * @create 2020/7/30
 * @desc 帖子标签
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Tag extends BaseEntity {

    private String groupName;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 引用统计
     */
    private Long refCount;

    /**
     * 创建人
     */
    private Long creatorId;

    /**
     * 审核状态
     */
    private AuditStateEn auditState;

    /**
     * 名称是否已存在（仅新建标签的"已有同名标签、直接复用"场景为 true，其余场景恒为 null）
     */
    @Builder.Default
    private Boolean existed = null;
}
