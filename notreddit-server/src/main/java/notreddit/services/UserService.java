package notreddit.services;

import notreddit.domain.entities.User;
import notreddit.domain.models.requests.ChangeRoleRequest;
import notreddit.domain.models.requests.SignUpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.UUID;

public interface UserService extends UserDetailsService {

    UserDetails loadUserById(UUID id);

    ResponseEntity<?> register(SignUpRequest model);

    ResponseEntity<?> changeRole(ChangeRoleRequest request, User user);

    ResponseEntity<?> deleteUser(String userId, User user);
}