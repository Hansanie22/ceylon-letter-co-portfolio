package com.auracraft.controller;

import com.auracraft.entity.HeroSlide;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequestMapping("/api/public/hero-slides")
public class PublicHeroSlideController {
    @Autowired
    private com.auracraft.service.HeroSlideService heroSlideService;

    @GetMapping
    public ResponseEntity<List<HeroSlide>> getPublicSlides() {
        List<HeroSlide> slides = heroSlideService.getActiveSlides();
        return ResponseEntity.ok(slides);
    }
}
