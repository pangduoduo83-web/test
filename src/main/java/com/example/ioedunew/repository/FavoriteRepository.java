package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 项目收藏仓库 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndProjectId(Long userId, Long projectId);

    List<Favorite> findByUserId(Long userId);
}
