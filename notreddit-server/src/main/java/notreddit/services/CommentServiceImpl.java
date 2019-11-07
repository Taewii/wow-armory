package notreddit.services;

import notreddit.domain.entities.Comment;
import notreddit.domain.entities.Mention;
import notreddit.domain.entities.Post;
import notreddit.domain.entities.User;
import notreddit.domain.models.requests.CommentPostRequestModel;
import notreddit.domain.models.responses.api.ApiResponse;
import notreddit.domain.models.responses.comment.CommentListWithChildren;
import notreddit.domain.models.responses.comment.CommentListWithReplyCount;
import notreddit.domain.models.responses.comment.CommentsResponseModel;
import notreddit.repositories.CommentRepository;
import notreddit.repositories.MentionRepository;
import notreddit.repositories.PostRepository;
import notreddit.repositories.VoteRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MentionRepository mentionRepository;
    private final VoteRepository voteRepository;
    private final ModelMapper mapper;

    @Autowired
    public CommentServiceImpl(PostRepository postRepository,
                              CommentRepository commentRepository,
                              MentionRepository mentionRepository,
                              VoteRepository voteRepository,
                              ModelMapper mapper) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.mentionRepository = mentionRepository;
        this.voteRepository = voteRepository;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<?> create(CommentPostRequestModel commentModel, User creator) {
        Post post = postRepository.findById(commentModel.getPostId()).orElse(null);
        Comment parent = null;

        if (post == null) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "Post doesn't exist."));
        }

        Comment comment = mapper.map(commentModel, Comment.class);
        comment.setCreator(creator);
        comment.setPost(post);
        comment.setCreatedOn(LocalDateTime.now());

        if (commentModel.getParentId() != null) {
            parent = commentRepository.findById(commentModel.getParentId()).orElse(null);
            comment.setParent(parent);

            if (parent != null) {
                Mention mention = createMention(comment, parent.getCreator(), creator);
                comment.getMentions().add(mention);
                parent.addChild(comment);
                commentRepository.saveAndFlush(parent);
            }
        }

        if (parent == null) {
            Mention mention = createMention(comment, post.getCreator(), creator);
            comment.getMentions().add(mention);
            commentRepository.saveAndFlush(comment);
        }

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/api/comment/post")
                .buildAndExpand().toUri();

        return ResponseEntity
                .created(location)
                .body(new ApiResponse(true, "Comment created successfully."));
    }

    private Mention createMention(Comment comment, User receiver, User creator) {
        Mention mention = new Mention();
        mention.setComment(comment);
        mention.setCreatedOn(LocalDateTime.now());
        mention.setCreator(creator);
        mention.setReceiver(receiver);

        return mention;
    }

    @Override
    public List<CommentListWithChildren> findAllFromPost(UUID postId) {
        return commentRepository
                .findByPostIdWithChildren(postId)
                .parallelStream()
                .map(c -> mapper.map(c, CommentListWithChildren.class))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public CommentsResponseModel findAllFromUsername(String username, Pageable pageable) {
        Page<Comment> byCreatorUsername = commentRepository.findByCreatorUsername(username.toLowerCase(), pageable);
        List<CommentListWithReplyCount> comments = byCreatorUsername.stream()
                .map(c -> {
                    CommentListWithReplyCount model = mapper.map(c, CommentListWithReplyCount.class);
                    model.setReplies(c.getChildren().size());
                    return model;
                }).collect(Collectors.toUnmodifiableList());

        return new CommentsResponseModel(byCreatorUsername.getTotalElements(), comments);
    }

    @Override
    public ResponseEntity<?> delete(UUID commentId, User user) {
        Comment comment = commentRepository.findById(commentId).orElse(null);

        if (comment == null || !user.getUsername().equals(comment.getCreator().getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "Comment doesn't exist or you are not the creator."));
        }

        mentionRepository.deleteAllByCommentId(commentId);

        if (!comment.getChildren().isEmpty()) {
            comment.setContent("[deleted]");
            commentRepository.saveAndFlush(comment);

            return ResponseEntity
                    .ok(new ApiResponse(true, "Comment deleted successfully."));
        }

        voteRepository.deleteAllByCommentId(commentId);
        commentRepository.delete(comment);

        return ResponseEntity
                .ok(new ApiResponse(true, "Comment deleted successfully."));
    }
}
