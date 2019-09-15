package notreddit.services;

import notreddit.domain.entities.Subreddit;
import notreddit.domain.models.requests.SubredditCreateRequest;
import notreddit.domain.models.responses.ApiResponse;
import notreddit.repositories.SubredditRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import notreddit.domain.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Service
public class SubredditServiceImpl implements SubredditService {

    private final SubredditRepository subredditRepository;
    private final ModelMapper mapper;

    @Autowired
    public SubredditServiceImpl(SubredditRepository subredditRepository,
                                ModelMapper mapper) {
        this.subredditRepository = subredditRepository;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<?> create(SubredditCreateRequest request, User creator) {
        if (subredditRepository.existsByTitle(request.getTitle())) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "Subreddit name already exists."));
        }

        Subreddit subreddit = mapper.map(request, Subreddit.class);
        subreddit.setCreator(creator);
        subredditRepository.saveAndFlush(subreddit);

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/api/subreddit/create")
                .buildAndExpand().toUri();

        return ResponseEntity
                .created(location)
                .body(new ApiResponse(true, "Subreddit created successfully."));
    }
}
