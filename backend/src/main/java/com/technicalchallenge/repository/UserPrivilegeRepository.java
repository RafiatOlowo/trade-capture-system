package com.technicalchallenge.repository;

import com.technicalchallenge.model.UserPrivilege;
import com.technicalchallenge.model.UserPrivilegeId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPrivilegeRepository extends JpaRepository<UserPrivilege, UserPrivilegeId> {}
