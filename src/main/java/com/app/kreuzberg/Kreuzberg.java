package com.app.kreuzberg;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Document text extraction utility.
 * Uses Apache PDFBox for PDF files and falls back to a basic read for other
 * file types.
 */
public class Kreuzberg {

    private static final Logger logger = LoggerFactory.getLogger(Kreuzberg.class);

    /**
     * Extracts text from a file (PDF or image).
     * For PDFs, uses Apache PDFBox to extract all page text.
     * For non-PDF files, returns a placeholder indicating OCR is needed.
     *
     * @param filePath path to the file on disk (e.g., /tmp/document.pdf)
     * @param config   extraction configuration (currently unused but reserved for
     *                 future options)
     * @return ExtractionResult containing the extracted text
     */
    public static ExtractionResult extractFile(String filePath, ExtractionConfig config) {
        logger.info("Extracting text from: {}", filePath);

        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".pdf")) {
            return extractPdf(filePath);
        } else if (lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg")
                || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".tiff")
                || lowerPath.endsWith(".bmp")) {
            // Image files require OCR — PDFBox cannot handle these.
            // In production, integrate AWS Textract for image-based text extraction.
            logger.warn("Image file detected: {}. OCR not yet implemented — returning placeholder.", filePath);
            return new ExtractionResult(
                    "[OCR not available] Image file detected: " + filePath
                            + ". Integrate AWS Textract for image-based text extraction.");
        } else {
            logger.warn("Unsupported file type: {}", filePath);
            return new ExtractionResult("[Unsupported file type] Cannot extract text from: " + filePath);
        }
    }

    /**
     * Extracts text from a PDF file using Apache PDFBox.
     */
    private static ExtractionResult extractPdf(String filePath) {
        try {
            File file = new File(filePath);
            try (PDDocument document = Loader.loadPDF(file)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                if (text == null || text.isBlank()) {
                    logger.warn("PDF contains no extractable text (may be scanned/image-based): {}", filePath);
                    return new ExtractionResult(
                            "[No text layer found] PDF appears to be scanned/image-based: " + filePath
                                    + ". Integrate AWS Textract for OCR.");
                }

                logger.info("Successfully extracted {} characters from PDF: {}", text.length(), filePath);
                return new ExtractionResult(text);
            }
        } catch (Exception e) {
            logger.error("Failed to extract text from PDF {}: {}", filePath, e.getMessage(), e);
            return new ExtractionResult("[Extraction error] Failed to extract text from: " + filePath
                    + ". Error: " + e.getMessage());
        }
    }
}
