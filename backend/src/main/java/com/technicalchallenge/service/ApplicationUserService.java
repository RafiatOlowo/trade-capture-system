package com.technicalchallenge.service;

import com.technicalchallenge.model.ApplicationUser;
import com.technicalchallenge.repository.ApplicationUserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ApplicationUserService implements UserDetailsService { 
    private static final Logger logger = LoggerFactory.getLogger(ApplicationUserService.class);
    private final ApplicationUserRepository applicationUserRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        logger.debug("Attempting to load user by loginId for authentication: {}", loginId);

        ApplicationUser applicationUser = applicationUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with loginId: " + loginId));

        // Spring Security's BasicAuthenticationFilter will use this object.
        // DelegatingPasswordEncoder handles the password comparison.
        return new User(
                applicationUser.getLoginId(),
                applicationUser.getPassword(),
                Collections.emptyList() 
        );
    }

    public List<ApplicationUser> getAllUsers() {
        logger.info("Retrieving all users");
        return applicationUserRepository.findAll();
    }

    public Optional<ApplicationUser> getUserById(Long id) {
        logger.debug("Retrieving user by id: {}", id);
        return applicationUserRepository.findById(id);
    }

    public Optional<ApplicationUser> getUserByLoginId(String loginId) {
        logger.debug("Retrieving user by login id: {}", loginId);
        return applicationUserRepository.findByLoginId(loginId);
    }

    public ApplicationUser saveUser(ApplicationUser user) {
        logger.info("Saving user: {}", user);
        return applicationUserRepository.save(user);
    }

    public void deleteUser(Long id) {
        logger.warn("Deleting user with id: {}", id);
        applicationUserRepository.deleteById(id);
    }

    public ApplicationUser updateUser(Long id, ApplicationUser user) {
        logger.info("Updating user with id: {}", id);
        ApplicationUser existingUser = applicationUserRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        // Update fields
        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());
        existingUser.setLoginId(user.getLoginId());
        existingUser.setActive(user.isActive());
        existingUser.setUserProfile(user.getUserProfile());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(user.getPassword());
        }
        // version and lastModifiedTimestamp handled by entity listeners
        return applicationUserRepository.save(existingUser);
    }
}
