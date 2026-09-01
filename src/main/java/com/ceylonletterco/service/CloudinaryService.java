package com.ceylonletterco.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", "tojccyg8",
            "api_key", "372842354576741",
            "api_secret", "gnb85VmLeC1DFZAZb7IRgjyT6tw",
            "secure", true
    ));

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
