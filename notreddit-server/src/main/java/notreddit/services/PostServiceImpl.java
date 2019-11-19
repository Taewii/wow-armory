package notreddit.services;

import notreddit.domain.entities.File;
import notreddit.domain.entities.Post;
import notreddit.domain.entities.Subreddit;
import notreddit.domain.entities.User;
import notreddit.domain.models.requests.PostCreateRequest;
import notreddit.domain.models.responses.api.ApiResponse;
import notreddit.domain.models.responses.post.PostDetailsResponseModel;
import notreddit.domain.models.responses.post.PostListResponseModel;
import notreddit.domain.models.responses.post.PostsResponseModel;
import notreddit.repositories.*;
import notreddit.web.exceptions.AccessForbiddenException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {

    private final CloudStorage cloudStorage;
    private final ThumbnailService thumbnailService;
    private final SubredditRepository subredditRepository;
    private final PostRepository postRepository;
    private final FileRepository fileRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;
    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final ModelMapper mapper;

    @Autowired
    public PostServiceImpl(SubredditRepository subredditRepository,
                           PostRepository postRepository,
                           @Qualifier("dropboxService") CloudStorage cloudStorage,
                           FileRepository fileRepository,
                           ThumbnailService thumbnailService,
                           VoteRepository voteRepository,
                           CommentRepository commentRepository,
                           MentionRepository mentionRepository,
                           UserRepository userRepository,
                           ModelMapper mapper) {
        this.subredditRepository = subredditRepository;
        this.postRepository = postRepository;
        this.cloudStorage = cloudStorage;
        this.fileRepository = fileRepository;
        this.thumbnailService = thumbnailService;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Override
    public PostsResponseModel allPosts(Pageable pageable) {
        Page<Post> allPosts = postRepository.findAll(pageable);
        return getPostsResponseModel(allPosts);
    }

    @Override
    public PostsResponseModel findAllByUsername(String username, Pageable pageable) {
        Page<Post> allByUsername = postRepository.findAllByUsername(username.toLowerCase(), pageable);
        return getPostsResponseModel(allByUsername);
    }

    @Override
    public PostsResponseModel findAllBySubreddit(String subreddit, Pageable pageable) {
        Page<UUID> allBySubredditTitle = postRepository.getPostIdsBySubredditTitle(subreddit.toLowerCase(), pageable);
        List<Post> postsBySubreddit = new ArrayList<>();

        // if I call the method with an empty array the IN statement in the sql throws an exception
        if (allBySubredditTitle.getTotalElements() > 0) {
            postsBySubreddit = postRepository.getPostsFromIdList(allBySubredditTitle.getContent(), pageable.getSort());
        }

        return getPostsResponseModel(allBySubredditTitle.getTotalElements(), postsBySubreddit);
    }

    @Override
    public ResponseEntity<?> delete(UUID postId, User user) {
        Post post = postRepository.findByIdWithCreatorAndComments(postId).orElse(null);

        if (post == null) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "Post doesn't exist."));
        }

        boolean isCreatorOrModerator = user.getUsername().equals(post.getCreator().getUsername()) ||
                user.getRoles().stream().anyMatch(r -> r.getAuthority().equals("ROLE_MODERATOR"));

        if (!isCreatorOrModerator) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(false, "You don't have the authority to do that."));
        }

        voteRepository.deleteAllByPostId(post.getId());
        post.getComments()
                .parallelStream()
                .forEach(comment -> {
                    mentionRepository.deleteAllByCommentId(comment.getId());
                    voteRepository.deleteAllByCommentId(comment.getId());
                    commentRepository.deleteById(comment.getId());
                });

        String fileUrl = post.getFile().getUrl();
        if (fileUrl.contains("dropbox")) { // if the file is uploaded to the cloud storage -> delete it
            cloudStorage.removeFile(fileUrl);
        }

        postRepository.delete(post);
        return ResponseEntity
                .ok(new ApiResponse(true, "Post deleted successfully."));
    }

    @Override
    public PostsResponseModel subscribedPosts(User user, Pageable pageable) {
        user = userRepository.getWithSubscriptions(user);
        /* Using two queries to avoid
         * Hibernate “HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!”
         * warning message, taken from https://vladmihalcea.com/fix-hibernate-hhh000104-entity-fetch-pagination-warning-message/
         */
        Page<UUID> subscribedPostsIds = postRepository.getSubscribedPostsIds(user.getSubscriptions(), pageable);
        List<Post> subscribedPosts = postRepository.getPostsFromIdList(subscribedPostsIds.getContent(), pageable.getSort());
        return getPostsResponseModel(subscribedPostsIds.getTotalElements(), subscribedPosts);
    }

    @Override
    public PostsResponseModel defaultPosts(Pageable pageable) {
        Set<Subreddit> defaultSubreddits = subredditRepository.findByTitleIn(SubredditServiceImpl.DEFAULT_SUBREDDITS);
        Page<UUID> subscribedPostsIds = postRepository.getSubscribedPostsIds(defaultSubreddits, pageable);
        List<Post> subscribedPosts = postRepository.getPostsFromIdList(subscribedPostsIds.getContent(), pageable.getSort());
        return getPostsResponseModel(subscribedPostsIds.getTotalElements(), subscribedPosts);
    }

    @Override
    public PostsResponseModel getPostsByVoteChoice(User user, String username, int choice, Pageable pageable) {
        if (!user.getUsername().equalsIgnoreCase(username)) {
            throw new AccessForbiddenException("You are not allowed to view this content");
        }

        PageRequest nativeQuerySorting = (PageRequest) pageable;

        // changing the sorting param from createdOn -> created_on, cus its a native query
        if (pageable.getSort().isSorted()) {
            Sort.Order createdOn = pageable.getSort().getOrderFor("createdOn");
            if (createdOn != null) {
                Sort createdOnNativeQuery = Sort.by("created_on");
                if (createdOn.getDirection().isAscending()) {
                    createdOnNativeQuery = createdOnNativeQuery.ascending();
                } else {
                    createdOnNativeQuery = createdOnNativeQuery.descending();
                }
                nativeQuerySorting = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), createdOnNativeQuery);
            }
        }

        Page<String> postIdsByVoteChoice = postRepository.findPostIdsByUserAndVoteChoice(user.getId(), (byte) choice, nativeQuerySorting);
        List<UUID> postIds = postIdsByVoteChoice.getContent().stream().map(UUID::fromString).collect(Collectors.toList());
        List<Post> posts = new ArrayList<>();

        if (!postIds.isEmpty()) {
            posts = postRepository.getPostsFromIdList(postIds, pageable.getSort());
        }

        return getPostsResponseModel(postIdsByVoteChoice.getTotalElements(), posts);
    }

    @Override
    public ResponseEntity<?> create(PostCreateRequest request, User creator) {
        Subreddit subreddit = subredditRepository.findByTitleIgnoreCase(request.getSubreddit()).orElse(null);

        if (subreddit == null) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "Subreddit does't exist."));
        }

        Post post = mapper.map(request, Post.class);
        post.setCreator(creator);
        post.setSubreddit(subreddit);
        post.setCreatedOn(LocalDateTime.now());

        if (request.getFile() == null && request.getUrl().isBlank()) {
            return createPostWithoutFiles(post);
        } else if (request.getFile() != null && request.getUrl().isBlank()) {
            return createPostWithUploadedFile(request, post);
        } else if (request.getFile() == null && !request.getUrl().isBlank()) {
            return createPostWithWebUrl(request, post);
        } else {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "You can't have both url and uploaded image."));
        }
    }

    @Override
    public PostDetailsResponseModel findById(UUID id) {
        Post post = postRepository.findByIdEager(id).orElseThrow();
        return mapper.map(post, PostDetailsResponseModel.class);
    }

    private ResponseEntity<?> createPostWithoutFiles(Post post) {
        postRepository.saveAndFlush(post);
        return getCreatedResponseEntityWithPath();
    }

    private ResponseEntity<?> createPostWithUploadedFile(PostCreateRequest request, Post post) {
        double fileSizeInMb = request.getFile().getSize() / 1024d / 1024d;
        if (fileSizeInMb > 10) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "File size is over the limit of 10MB."));
        }

        File file = uploadFile(request.getFile());
        file.setPost(post);
        post.setFile(file);
        postRepository.saveAndFlush(post);

        return getCreatedResponseEntityWithPath();
    }

    private ResponseEntity<?> createPostWithWebUrl(PostCreateRequest request, Post post) {
        File file = new File();
        file.setFileId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        file.setPost(post);
        file.setUrl(request.getUrl());
        file.setThumbnailUrl(thumbnailService.generateThumbnailUrl(request.getUrl()));
        fileRepository.saveAndFlush(file);

        return getCreatedResponseEntityWithPath();
    }

    private File uploadFile(MultipartFile multipartFile) {
        Map<String, Object> params = cloudStorage.uploadFileAndGetParams(multipartFile);
        String fileUrl = params.get("url").toString();

        File file = new File();
        file.setUrl(fileUrl);
        file.setFileId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);

        if (params.get("contentType").toString().contains("image")) {
            file.setThumbnailUrl(fileUrl);
        } else {
            file.setThumbnailUrl(thumbnailService.generateThumbnailUrl(fileUrl));
        }

        return file;
    }

    private ResponseEntity getCreatedResponseEntityWithPath() {
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/api/post/create")
                .buildAndExpand().toUri();

        return ResponseEntity
                .created(location)
                .body(new ApiResponse(true, "Post created successfully."));
    }

    private PostsResponseModel getPostsResponseModel(Page<Post> allByUsername) {
        List<PostListResponseModel> posts = allByUsername.stream()
                .map(p -> {
                    PostListResponseModel model = mapper.map(p, PostListResponseModel.class);
                    model.setCommentCount(p.getComments().size());
                    return model;
                })
                .collect(Collectors.toUnmodifiableList());

        return new PostsResponseModel(allByUsername.getTotalElements(), posts);
    }

    private PostsResponseModel getPostsResponseModel(long total, List<Post> posts) {
        List<PostListResponseModel> mappedPosts = posts.stream()
                .map(p -> {
                    PostListResponseModel model = mapper.map(p, PostListResponseModel.class);
                    model.setCommentCount(p.getComments().size());
                    return model;
                })
                .collect(Collectors.toUnmodifiableList());

        return new PostsResponseModel(total, mappedPosts);
    }
}
