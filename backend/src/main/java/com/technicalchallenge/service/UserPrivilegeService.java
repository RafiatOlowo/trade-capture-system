package com.technicalchallenge.service;

import com.technicalchallenge.model.UserPrivilege;
import com.technicalchallenge.model.UserPrivilegeId;
import com.technicalchallenge.repository.UserPrivilegeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class UserPrivilegeService {
    private static final Logger logger = LoggerFactory.getLogger(UserPrivilegeService.class);

    @Autowired
    private UserPrivilegeRepository userPrivilegeRepository;

    public List<UserPrivilege> getAllUserPrivileges() {
        logger.info("Retrieving all user privileges");
        return userPrivilegeRepository.findAll();
    }

    /**
     * Retrieves a UserPrivilege record using the composite key components (User ID and Privilege ID).
     * This is the preferred public method for lookup via raw key values.
     * * @param userId The ID of the user.
     * @param privilegeId The ID of the privilege.
     * @return Optional containing the UserPrivilege record, if found.
     */
    public Optional<UserPrivilege> getUserPrivilege(Long userId, Long privilegeId) {
        UserPrivilegeId id = new UserPrivilegeId(userId, privilegeId);
        logger.debug("Retrieving user privilege by composite id: {}", id);
        return findById(id);
    }

    /**
     * Retrieves a UserPrivilege record using the full entity object containing the key fields.
     * This is useful when the entity object is already available in the business logic.
     *
     * @param userPrivilege A UserPrivilege object containing the composite key (userId and privilegeId).
     * @return Optional containing the UserPrivilege record, if found.
     */
    public Optional<UserPrivilege> getUserPrivilegeById(UserPrivilege userPrivilege) {
        UserPrivilegeId id = new UserPrivilegeId(userPrivilege.getUserId(), userPrivilege.getPrivilegeId());
        logger.debug("Retrieving user privilege by entity key: {}", id);
        return findById(id);
    }

    public UserPrivilege saveUserPrivilege(UserPrivilege userPrivilege) {
        logger.info("Saving user privilege: {}", userPrivilege);
        return userPrivilegeRepository.save(userPrivilege);
    }

    /**
     * Deletes a UserPrivilege record using the composite key components.
     * * @param userId The ID of the user.
     * @param privilegeId The ID of the privilege.
     */
    public void deleteUserPrivilege(Long userId, Long privilegeId) {
        UserPrivilegeId id = new UserPrivilegeId(userId, privilegeId);
        logger.warn("Deleting user privilege with composite id: {}", id);
        deleteById(id);
    }

    public void deleteUserPrivilege(UserPrivilege userPrivilege) {
    // Extracts the two key parts from the entity and uses the existing helper.
    UserPrivilegeId id = new UserPrivilegeId(userPrivilege.getUserId(), userPrivilege.getPrivilegeId());
    logger.warn("Deleting user privilege by entity key: {}", id);
    deleteById(id);
    }

    // --- Private methods using the required UserPrivilegeId type ---
    
    private Optional<UserPrivilege> findById(UserPrivilegeId id) {
        return userPrivilegeRepository.findById(id);
    }

    private void deleteById(UserPrivilegeId id) {
        userPrivilegeRepository.deleteById(id);
    }
}