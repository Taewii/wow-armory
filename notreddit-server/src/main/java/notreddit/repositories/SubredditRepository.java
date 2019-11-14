package notreddit.repositories;

import notreddit.domain.entities.Subreddit;
import notreddit.domain.models.responses.subreddit.SubredditWithPostCountResponse;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SubredditRepository extends JpaRepository<Subreddit, Long> {

    Boolean existsByTitleIgnoreCase(@Length(min = 4) @NotBlank String title);

    Optional<Subreddit> findByTitleIgnoreCase(@Length(min = 4) @NotBlank String title);

    @Query("SELECT new notreddit.domain.models.responses.subreddit.SubredditWithPostCountResponse(s.title, s.posts.size) " +
            "FROM Subreddit s ORDER BY s.title")
    List<SubredditWithPostCountResponse> findAllWithPostCount();

    @Query("SELECT s FROM Subreddit s WHERE LOWER(s.title) IN :title")
    Set<Subreddit> findByTitleIn(Collection<@NotBlank @Length(min = 3) String> title);
}
