package notreddit.web.controllers;

import com.weddini.throttling.Throttling;
import com.weddini.throttling.ThrottlingType;
import notreddit.domain.entities.User;
import notreddit.domain.models.requests.PostCreateRequest;
import notreddit.domain.models.responses.PostDetailsResponseModel;
import notreddit.domain.models.responses.PostListResponseModel;
import notreddit.domain.models.responses.PostsResponseModel;
import notreddit.services.PostService;
import notreddit.services.VoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/post")
public class PostController {

    private final PostService postService;
    private final VoteService voteService;

    @Autowired
    public PostController(PostService postService,
                          VoteService voteService) {
        this.postService = postService;
        this.voteService = voteService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @ModelAttribute PostCreateRequest request,
                                    @AuthenticationPrincipal User creator) {
        return postService.create(request, creator);
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/all")
    public PostsResponseModel all(Pageable pageable) {
        return postService.allPosts(pageable);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/mine")
    public List<PostListResponseModel> mine(@AuthenticationPrincipal User user) {
        return postService.findAllByUsername(user.getUsername());
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public PostDetailsResponseModel findById(@PathVariable UUID id) {
        return postService.findById(id);
    }

    @Throttling(type = ThrottlingType.PrincipalName)
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/vote")
    public ResponseEntity<?> vote(@RequestParam byte choice,
                                  @RequestParam UUID postId,
                                  @AuthenticationPrincipal User user) {
        return voteService.voteForPostOrComment(choice, postId, null, user);
    }
}
