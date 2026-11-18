package pub.developers.forum.portal.controller.rest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.request.tag.TagCreateRequest;
import pub.developers.forum.api.response.tag.TagQueryResponse;
import pub.developers.forum.api.service.TagApiService;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Qiangqiang.Bian
 * @create 2020/11/13
 * @desc
 **/
@RestController
@RequestMapping("/tag")
public class TagRestController {

    @Resource
    private TagApiService tagApiService;

    @PostMapping("/all")
    public ResultModel<List<TagQueryResponse>> all() {
        return tagApiService.queryAll();
    }

    @PostMapping("/tag/create")
    public ResultModel<TagQueryResponse> create(@RequestBody TagCreateRequest request) {
        return tagApiService.create(request);
    }

    @PostMapping("/tag/delete")
    public ResultModel delete(@RequestBody Long id) {
        return tagApiService.delete(id);
    }
}
