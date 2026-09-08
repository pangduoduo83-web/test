package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepo extends JpaRepository<HubItem, Long> {
    List<HubItem> findByPublisherTenantIdOrderByUpdatedAtDesc(Long tenantId);

    List<HubItem> findByReviewStatusOrderByUpdatedAtDesc(String status);

    List<HubItem> findAllByOrderByUpdatedAtDesc();

    long countByReviewStatus(String status);

    long countByFeaturedTrue();

    /**
     * 对某租户可见的条目:有已审核通过的版本且未下架(新版本待审/被驳回不影响老版本继续展示),
     * 并且是公开的或被定向分享给它的。推荐置顶的排最前(按置顶时间倒序),其余按更新时间倒序。
     */
    @Query("SELECT i FROM HubItem i WHERE i.currentVersionId IS NOT NULL AND i.reviewStatus <> 'OFFLINE' AND "
            + "(i.visibility = 'PUBLIC' OR i.id IN (SELECT g.itemId FROM HubItemGrant g WHERE g.tenantId = :tenantId)) "
            + "ORDER BY i.featured DESC, i.featuredAt DESC, i.updatedAt DESC")
    List<HubItem> findVisibleTo(@Param("tenantId") Long tenantId);
}
