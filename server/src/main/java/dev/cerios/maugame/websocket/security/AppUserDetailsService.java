package dev.cerios.maugame.websocket.security;

import dev.cerios.maugame.websocket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public @NonNull AppUserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
            .map(user -> new AppUserDetails(user.getUsername(), user.getEmail(), user.getId(), user.getPassword()))
            .orElseThrow(() -> new UsernameNotFoundException("MauUser not found: " + username));
    }
}
