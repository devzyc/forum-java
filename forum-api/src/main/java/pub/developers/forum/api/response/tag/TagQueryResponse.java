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
     * 是否为热门标签（refCount 排名前10），动态计算不入库
     */
    private Boolean isHot;

}
