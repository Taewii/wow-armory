package notreddit.services;

import notreddit.domain.entities.Mention;
import notreddit.domain.entities.User;
import notreddit.domain.models.responses.api.ApiResponse;
import notreddit.domain.models.responses.mention.MentionResponse;
import notreddit.domain.models.responses.mention.MentionResponseModel;
import notreddit.repositories.MentionRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MentionServiceImpl implements MentionService {

    private final MentionRepository mentionRepository;
    private final ModelMapper mapper;

    @Autowired
    public MentionServiceImpl(MentionRepository mentionRepository,
                              ModelMapper mapper) {
        this.mentionRepository = mentionRepository;
        this.mapper = mapper;
    }

    @Override
    public int getUnreadMentionCountByUser(User user) {
        return mentionRepository.getUnreadMentionCountByUser(user);
    }

    @Override
    public MentionResponse getMentionByUser(User user, Pageable pageable) {
        Page<Mention> usersMentions = mentionRepository.getUsersMentions(user, pageable);
        List<MentionResponseModel> mentions = usersMentions.stream()
                .map(m -> mapper.map(m, MentionResponseModel.class))
                .collect(Collectors.toUnmodifiableList());

        return new MentionResponse(usersMentions.getTotalElements(), mentions);
    }

    @Override
    public ResponseEntity<?> mark(boolean read, User user, UUID mentionId) {
        Mention mention = mentionRepository.findByIdWithReceiver(mentionId).orElse(null);

        if (mention == null || !user.getUsername().equals(mention.getReceiver().getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(false, "No such mention, or you are not the receiver of the mention"));
        }

        mention.setRead(read);
        mentionRepository.saveAndFlush(mention);

        String message = String.format("Mention marked as %s.", read ? "read" : "unread");
        return ResponseEntity
                .ok()
                .body(new ApiResponse(true, message));
    }
}
