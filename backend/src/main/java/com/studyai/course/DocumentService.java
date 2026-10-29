package com.studyai.course;

import com.studyai.common.exception.InvalidDocumentException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final Path uploadDirectory;

    public DocumentService(@Value("${application.upload.dir:uploads/documents}") String uploadDir) {
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Impossible d'initialiser le répertoire de stockage des documents : " + uploadDir, e);
        }
    }

    public void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new InvalidDocumentException("Le fichier PDF est vide ou absent.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new MaxUploadSizeExceededException(MAX_FILE_SIZE);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new InvalidDocumentException("Le fichier doit avoir l'extension .pdf");
        }

        // Verify PDF magic bytes (%PDF-)
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[5];
            int read = is.read(header);
            if (read < 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F' || header[4] != '-') {
                throw new InvalidDocumentException("Le fichier fourni n'est pas un document PDF valide (signature de fichier incorrecte).");
            }
        } catch (IOException e) {
            throw new InvalidDocumentException("Impossible de lire les données du fichier pour validation : " + e.getMessage());
        }
    }

    public Path storeFile(MultipartFile file) {
        validatePdf(file);

        String generatedFilename = UUID.randomUUID().toString() + ".pdf";
        Path targetPath = this.uploadDirectory.resolve(generatedFilename).normalize();

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath;
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'enregistrement du fichier PDF sur le disque", e);
        }
    }

    public String extractText(Path filePath) {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            if (document.isEncrypted()) {
                throw new InvalidDocumentException("Le fichier PDF est protégé par mot de passe et son texte ne peut pas être extrait.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.trim().isEmpty()) {
                return "[Information] Ce document PDF ne contient aucun texte extractible (il s'agit probablement d'un document numérisé ou composé uniquement d'images). L'analyse OCR n'est pas encore implémentée.";
            }

            return text.trim();
        } catch (InvalidDocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidDocumentException("Erreur lors de l'analyse et de l'extraction du texte PDF : " + e.getMessage());
        }
    }

    public List<PageText> extractPages(Path filePath) {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            if (document.isEncrypted()) {
                throw new InvalidDocumentException("Le fichier PDF est protégé par mot de passe et son texte ne peut pas être extrait.");
            }

            int numberOfPages = document.getNumberOfPages();
            List<PageText> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= numberOfPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                pages.add(new PageText(i, pageText != null ? pageText : ""));
            }

            return pages;
        } catch (InvalidDocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidDocumentException("Erreur lors de l'extraction des pages du PDF : " + e.getMessage());
        }
    }

    public void deletePhysicalFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(storagePath);
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Logged or ignored safely
        }
    }
}
