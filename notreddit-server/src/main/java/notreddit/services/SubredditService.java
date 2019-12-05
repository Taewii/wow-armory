package notreddit.services;

import notreddit.domain.entities.User;
import notreddit.domain.models.requests.SubredditCreateRequest;
import notreddit.domain.models.responses.subreddit.SubredditWithPostsAndSubscribersCountResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

public interface SubredditService {

    ResponseEntity<?> create(SubredditCreateRequest request, User creator);

    Boolean existsByTitle(String title);

    List<String> getAllAsStrings();

    List<SubredditWithPostsAndSubscribersCountResponse> getAllWithPostCount();

    ResponseEntity<?> subscribe(String subreddit, User user);

    ResponseEntity<?> unsubscribe(String subreddit, User user);

    Set<String> getUserSubscriptions(User user);

    Boolean isUserSubscribedToSubreddit(String subreddit, User user);
}
