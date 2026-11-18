package pub.developers.forum.api.request.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleSaveArticleRequest implements Serializable {

    private Long id;

    private Long typeId;

    private String title;

    private String contentType;

    private String markdownContent;

    private String htmlContent;

    private String headImg;

    private Set<Long> tagIds;

    /**
     * 用户新敲的文字标签（不在已有标签列表里的），后端按名复用或自动创建后与 tagIds 合并
     */
    private Set<String> tagNames;

}
