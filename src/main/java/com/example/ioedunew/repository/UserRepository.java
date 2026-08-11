package com.example.ioedunew.repository;

import com.example.ioedunew.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 用户仓库 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(String role);

    List<User> findByRole(String role);
}
