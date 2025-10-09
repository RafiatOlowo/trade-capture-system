package com.technicalchallenge.controller;

import com.technicalchallenge.dto.UserPrivilegeDTO;
import com.technicalchallenge.mapper.UserPrivilegeMapper;
import com.technicalchallenge.model.UserPrivilege;
import com.technicalchallenge.service.UserPrivilegeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/userPrivileges")
public class UserPrivilegeController {
    private static final Logger logger = LoggerFactory.getLogger(UserPrivilegeController.class);

    @Autowired
    private UserPrivilegeService userPrivilegeService;

    @Autowired
    private UserPrivilegeMapper userPrivilegeMapper;

    @GetMapping
    public List<UserPrivilegeDTO> getAllUserPrivileges() {
        logger.info("Fetching all user privileges");
        return userPrivilegeService.getAllUserPrivileges().stream()
                .map(userPrivilegeMapper::toDto)
                .toList();
    }

    /**
     * Retrieves a UserPrivilege record using the composite key components (User ID and Privilege ID).
     * The old endpoint using a single ID is removed.
     * * @param userId The ID of the user.
     * @param privilegeId The ID of the privilege.
     * @return The DTO if found, or 404 Not Found.
     */
    @GetMapping("/{userId}/{privilegeId}")
    public ResponseEntity<UserPrivilegeDTO> getUserPrivilege(
            @PathVariable Long userId, 
            @PathVariable Long privilegeId) {
        
        logger.debug("Fetching user privilege by composite id: User {} / Privilege {}", userId, privilegeId);
        
        // Call the service method designed for composite key lookup
        Optional<UserPrivilege> userPrivilege = userPrivilegeService.getUserPrivilege(userId, privilegeId);
        
        return userPrivilege.map(userPrivilegeMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Creates a new user privilege record.
     * NOTE: The location URI is updated to correctly point to the new composite resource path.
     */
    @PostMapping
    public ResponseEntity<UserPrivilegeDTO> createUserPrivilege(@Valid @RequestBody UserPrivilegeDTO userPrivilegeDTO) {
        logger.info("Creating new user privilege: {}", userPrivilegeDTO);
        UserPrivilege createdUserPrivilege = userPrivilegeService.saveUserPrivilege(userPrivilegeMapper.toEntity(userPrivilegeDTO));
        
        // IMPORTANT: The Location header must now reflect the composite key path
        String resourcePath = String.format("/api/userPrivileges/%d/%d", 
            createdUserPrivilege.getUserId(), // Assuming entity now exposes these getters
            createdUserPrivilege.getPrivilegeId());
            
        return ResponseEntity.created(URI.create(resourcePath))
                .body(userPrivilegeMapper.toDto(createdUserPrivilege));
    }

    /**
     * Deletes a UserPrivilege record using the composite key components (User ID and Privilege ID).
     * The old endpoint using a single ID is removed.
     * * @param userId The ID of the user.
     * @param privilegeId The ID of the privilege.
     * @return 204 No Content.
     */
    @DeleteMapping("/{userId}/{privilegeId}")
    public ResponseEntity<Void> deleteUserPrivilege(
            @PathVariable Long userId, 
            @PathVariable Long privilegeId) {
        
        logger.warn("Deleting user privilege with composite id: User {} / Privilege {}", userId, privilegeId);
        userPrivilegeService.deleteUserPrivilege(userId, privilegeId);
        return ResponseEntity.noContent().build();
    }
}