package notreddit.domain.models.responses;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CommentListWithChildren extends CommentListModel {

    private List<CommentListWithChildren> children = new ArrayList<>();
}
