package com.auracraft.repository;

import com.auracraft.entity.HeroSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HeroSlideRepository extends JpaRepository<HeroSlide, Long> {
    List<HeroSlide> findAllByOrderByDisplayOrderAsc();
    List<HeroSlide> findByIsActiveTrueOrderByDisplayOrderAsc();
}
