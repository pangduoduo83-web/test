package com.example.ioedunew.repository;

import com.example.ioedunew.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 用户仓库 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPhone(String phone);

    /** 查询范围由 Hibernate 当前租户决定;返回全部匹配项以拒绝历史重复号。 */
    @Query("select u from User u where lower(trim(u.email)) = lower(:account)"
            + " or trim(u.phone) = :account or lower(trim(u.studentNo)) = lower(:account)"
            + " or lower(trim(u.teacherNo)) = lower(:account)")
    List<User> findByLoginAccount(@Param("account") String account);

    boolean existsByPhone(String phone);

    long countByRole(String role);

    List<User> findByRole(String role);
}
