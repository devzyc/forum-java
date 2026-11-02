package pub.developers.forum.portal.controller.rest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.request.comment.CommentCreateRequest;
import pub.developers.forum.api.service.CommentApiService;

import javax.annotation.Resource;

/**
 * @author Qiangqiang.Bian
 * @create 2020/11/17
 * @desc
 **/
@RestController
@RequestMapping("/comment-rest")
public class CommentRestController {

    @Resource
    private CommentApiService commentApiService;

    @PostMapping("/create")
    public ResultModel create(@RequestBody CommentCreateRequest createRequest) {
        return commentApiService.create(createRequest);
    }

}
