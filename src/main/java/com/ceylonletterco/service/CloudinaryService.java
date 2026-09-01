package com.auracraft.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class CloudinaryService {

    @Value("${cloudinary.cloud-name:tojccyg8}")
    private String cloudName;

    @Value("${cloudinary.api-key:372842354576741}")
    private String apiKey;

    @Value("${cloudinary.api-secret:gnb85VmLeC1DFZAZb7IRgjyT6tw}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    public String uploadImage(MultipartFile file) throws Exception {
        Map<?, ?> uploadResult = this.cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("resource_type", "auto"));
        return uploadResult.get("secure_url").toString();
    }

    public String uploadFile(byte[] fileBytes, String resourceType, String originalFilename) throws Exception {
        Map<?, ?> uploadResult = this.cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                "resource_type", resourceType != null ? resourceType : "auto",
                "public_id", originalFilename != null ? originalFilename.split("\\.")[0] + "_" + System.currentTimeMillis() : null
        ));
        return uploadResult.get("secure_url").toString();
    }

    public void deleteFile(String publicId, String resourceType) throws Exception {
        this.cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType != null ? resourceType : "image"));
    }
}
