package pub.developers.forum.api.response.tag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TagQueryResponse implements Serializable {

    private Long id;

    /**
     * 标签名称
     */
    private String name;

    private Long refCount;

    /**
     * 名称是否已存在（仅新建标签接口的"已有他人同名标签、直接复用"场景为 true，其余场景恒为 null）
     */
    private Boolean existed;

}
