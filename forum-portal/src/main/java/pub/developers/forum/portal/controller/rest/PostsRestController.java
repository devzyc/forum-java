package pub.developers.forum.portal.controller.rest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.service.PostsApiService;

import javax.annotation.Resource;

/**
 * @author Qiangqiang.Bian
 * @create 2020/11/25
 * @desc
 **/
@RestController
@RequestMapping("/posts-rest")
public class PostsRestController {

    @Resource
    private PostsApiService postsApiService;

    @PostMapping("/delete/{id}")
    public ResultModel delete(@PathVariable("id") Long id) {
        return postsApiService.delete(id);
    }

}
