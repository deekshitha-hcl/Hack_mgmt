package com.hackathon.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.hackathon.exception.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QrCodeService {

    private final FileStorageService fileStorageService;

    public QrCodeService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public String generateQrCode(String content) {
        try {
            Path directory = fileStorageService.getUploadRoot().resolve("qrcodes").normalize();
            Files.createDirectories(directory);
            Path file = directory.resolve(UUID.randomUUID() + "-registration.png");
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);
            MatrixToImageWriter.writeToPath(matrix, "PNG", file);
            return "/uploads/" + fileStorageService.getUploadRoot().relativize(file).toString().replace('\\', '/');
        } catch (WriterException | IOException exception) {
            throw new BadRequestException("Unable to generate QR code");
        }
    }
}
