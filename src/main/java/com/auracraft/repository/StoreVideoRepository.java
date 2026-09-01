package com.auracraft.repository;

import com.auracraft.entity.StoreVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreVideoRepository extends JpaRepository<StoreVideo, Long> {
    List<StoreVideo> findAllByOrderByDisplayOrderAscCreatedAtDesc();
    List<StoreVideo> findByIsActiveTrueOrderByDisplayOrderAscCreatedAtDesc();
    List<StoreVideo> findByVideoCategoryAndIsActiveTrueOrderByDisplayOrderAscCreatedAtDesc(String videoCategory);
    List<StoreVideo> findByProductIdAndIsActiveTrueOrderByDisplayOrderAsc(Integer productId);
}
