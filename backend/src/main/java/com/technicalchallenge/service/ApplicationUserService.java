package com.technicalchallenge.service;

import com.technicalchallenge.model.ApplicationUser;
import com.technicalchallenge.model.Privilege;
import com.technicalchallenge.model.UserPrivilege;
import com.technicalchallenge.repository.ApplicationUserRepository;
import com.technicalchallenge.repository.PrivilegeRepository;
import com.technicalchallenge.repository.UserPrivilegeRepository;

import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ApplicationUserService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationUserService.class);
    private final ApplicationUserRepository applicationUserRepository;
    private final UserPrivilegeRepository userPrivilegeRepository;
    private final PrivilegeRepository privilegeRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean validateCredentials(String loginId, String password) {
            logger.debug("Validating credentials for user: {}", loginId);
            Optional<ApplicationUser> user = applicationUserRepository.findByLoginId(loginId);
            return user.map(applicationUser -> passwordEncoder.matches(password, applicationUser.getPassword())).orElse(false);
    }
    
    private Set<String> fetchPrivilegeNamesForUser(Long applicationUserId) {
        // 1. Get the list of UserPrivilege objects
        List<UserPrivilege> userPrivileges = userPrivilegeRepository.findByUserId(applicationUserId);
        
        // 2. Extract the privilegeId directly from the UserPrivilege object
        Set<Long> privilegeIds = userPrivileges.stream()
            .map(UserPrivilege::getPrivilegeId)
            .collect(Collectors.toSet());

        // 3. Look up the names using the PrivilegeRepository
        List<Privilege> privileges = privilegeRepository.findAllById(privilegeIds);
        
        // 4. Return the names
        return privileges.stream()
            .map(Privilege::getName)
            .collect(Collectors.toSet());
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        logger.debug("Attempting to load user by loginId for authentication: {}", loginId);

        ApplicationUser applicationUser = applicationUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with loginId: " + loginId));

        if (!applicationUser.isActive()) {
            throw new UsernameNotFoundException("User is inactive: " + loginId);
        }

        Long applicationUserId = applicationUser.getId();

        // --- INTEGRATE PRIVILEGES AND DERIVED ROLES ---

        // Step 1: Initialize a mutable list to hold ALL authorities
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        
        // Step 2: Fetch raw privilege names
        Set<String> privilegeNames = fetchPrivilegeNamesForUser(applicationUserId);

        // Step 3: Add all fetched privileges as authorities (fine-grained permissions)
        privilegeNames.stream()
            .map(SimpleGrantedAuthority::new)
            .forEach(grantedAuthorities::add);

        // Step 4: Apply the CRITICAL privilege-to-role translation rule
        if (privilegeNames.contains("AMEND_TRADE")) {
            // Grant the high-level roles required by @PreAuthorize("hasAnyAuthority('ROLE_TRADER', 'ROLE_SALES')")
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_TRADER")); 
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_SALES"));
        }
        
        // Step 5: Add the base user profile role (e.g., ROLE_ADMIN, ROLE_SUPPORT)
        String userType = applicationUser.getUserProfile() != null 
                            ? applicationUser.getUserProfile().getUserType().toUpperCase() 
                            : "UNKNOWN";
        
        // Grant the base role derived from the UserProfile
        grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + userType));
        
        // --- END FIX: INTEGRATE PRIVILEGES AND DERIVED ROLES ---

        logger.debug("User {} loaded with authorities: {}", loginId, grantedAuthorities);

        // Return UserDetails with the combined authorities
        return new User(
                applicationUser.getLoginId(),
                applicationUser.getPassword(),
                grantedAuthorities // Now contains all privileges and roles
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
