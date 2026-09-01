package com.ceylonletterco.repository;

import com.ceylonletterco.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:category IS NULL OR :category = '' OR a.category = :category) AND " +
           "(:status IS NULL OR :status = '' OR a.status = :status) AND " +
           "(:start IS NULL OR a.createdAt >= :start) AND " +
           "(:end IS NULL OR a.createdAt <= :end) AND " +
           "(:query IS NULL OR :query = '' OR " +
           " LOWER(a.userEmail) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(a.action) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(a.details) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findWithFilters(
            @Param("category") String category,
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:category IS NULL OR :category = '' OR a.category = :category) AND " +
           "(:status IS NULL OR :status = '' OR a.status = :status) AND " +
           "(:start IS NULL OR a.createdAt >= :start) AND " +
           "(:end IS NULL OR a.createdAt <= :end) " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> findForExport(
            @Param("category") String category,
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.category = :category")
    long countByCategory(@Param("category") String category);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
