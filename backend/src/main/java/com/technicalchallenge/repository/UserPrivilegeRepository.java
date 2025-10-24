package com.technicalchallenge.repository;

import com.technicalchallenge.model.UserPrivilege;
import com.technicalchallenge.model.UserPrivilegeId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPrivilegeRepository extends JpaRepository<UserPrivilege, UserPrivilegeId> {
    /**
     * Finds all UserPrivilege records for a specific user ID.
     * UserPrivilege has a field 'userId' that maps to the ApplicationUser.id
     */
    List<UserPrivilege> findByUserId(Long userId);
}
