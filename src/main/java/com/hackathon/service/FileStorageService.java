package com.hackathon.service;

import com.hackathon.exception.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            Path directory = uploadRoot.resolve(folder).normalize();
            Files.createDirectories(directory);
            String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            String filename = UUID.randomUUID() + "-" + originalName;
            Path target = directory.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + uploadRoot.relativize(target).toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new BadRequestException("Unable to store uploaded file");
        }
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }
}
