package com.auracraft.service;

import com.auracraft.entity.HeroSlide;
import com.auracraft.repository.HeroSlideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class HeroSlideService {

    @Autowired
    private HeroSlideRepository heroSlideRepository;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<HeroSlide> getAllSlides() {
        return heroSlideRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<HeroSlide> getActiveSlides() {
        return heroSlideRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    public HeroSlide createSlide(String mediaUrl, String mediaType, String altText, String tag, String heading, String description) {
        HeroSlide slide = new HeroSlide();
        slide.setMediaUrl(mediaUrl);
        slide.setMediaType(mediaType);
        slide.setAltText(altText);
        slide.setTag(tag);
        slide.setHeading(heading);
        slide.setDescription(description);
        
        // Find max display order
        List<HeroSlide> all = heroSlideRepository.findAllByOrderByDisplayOrderAsc();
        int maxOrder = all.isEmpty() ? 0 : all.get(all.size() - 1).getDisplayOrder();
        slide.setDisplayOrder(maxOrder + 1);
        slide.setActive(true);
        
        return heroSlideRepository.save(slide);
    }

    public void deleteSlide(Long id) {
        heroSlideRepository.findById(id).ifPresent(slide -> {
            try {
                // Try to delete from cloudinary if it's a cloudinary URL
                if (slide.getMediaUrl() != null && slide.getMediaUrl().contains("cloudinary")) {
                    String[] parts = slide.getMediaUrl().split("/");
                    String publicIdWithExt = parts[parts.length - 1];
                    String publicId = publicIdWithExt.contains(".") ? publicIdWithExt.substring(0, publicIdWithExt.lastIndexOf('.')) : publicIdWithExt;
                    cloudinaryService.deleteFile(publicId, "video".equals(slide.getMediaType()) ? "video" : "image");
                }
            } catch (Exception e) {
                // Ignore cloudinary delete errors
            }
            heroSlideRepository.delete(slide);
        });
    }
}
