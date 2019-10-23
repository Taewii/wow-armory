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
import notreddit.repositories.FileRepository;
import notreddit.repositories.PostRepository;
import notreddit.repositories.SubredditRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {

    private final SubredditRepository subredditRepository;
    private final PostRepository postRepository;
    private final FileRepository fileRepository;
    private final CloudStorage cloudStorage;
    private final ThumbnailService thumbnailService;
    private final ModelMapper mapper;

    @Autowired
    public PostServiceImpl(SubredditRepository subredditRepository,
                           PostRepository postRepository,
                           @Qualifier("dropboxService") CloudStorage cloudStorage,
                           FileRepository fileRepository,
                           ThumbnailService thumbnailService,
                           ModelMapper mapper) {
        this.subredditRepository = subredditRepository;
        this.postRepository = postRepository;
        this.cloudStorage = cloudStorage;
        this.fileRepository = fileRepository;
        this.thumbnailService = thumbnailService;
        this.mapper = mapper;
    }

    @Override
    public PostsResponseModel allPosts(Pageable pageable) {
        Page<Post> allPosts = postRepository.findAll(pageable);
        return getPostsResponseModel(allPosts);
    }

    @Override
    public PostsResponseModel findAllByUsername(String username, Pageable pageable) {
        Page<Post> allByUsername = postRepository.findAllByUsername(username, pageable);
        return getPostsResponseModel(allByUsername);
    }

    @Override
    public PostsResponseModel getUpvotedPosts(User user, Pageable pageable) {
        Page<Post> upvotedPostsByUser = postRepository
                .findPostsByUserAndVoteChoice(user, (byte) 1, pageable);
        return getPostsResponseModel(upvotedPostsByUser);
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
        Post post = postRepository.findById(id).orElseThrow();
        return mapper.map(post, PostDetailsResponseModel.class);
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
        if (params.containsKey("id")) {
            file.setFileId(Long.parseLong(params.get("id").toString()));
        } else {
            file.setFileId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        }

        if (params.get("contentType").toString().contains("image")) {
            file.setThumbnailUrl(fileUrl);
        } else {
            file.setThumbnailUrl(thumbnailService.generateThumbnailUrl(fileUrl));
        }

        file.setUrl(fileUrl);
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
}
