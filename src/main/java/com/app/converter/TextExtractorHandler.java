package com.app.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.app.kreuzberg.ExtractionConfig;
import com.app.kreuzberg.ExtractionResult;
import com.app.kreuzberg.Kreuzberg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles text extraction from uploaded documents (PDF/images).
 * Downloads the file from S3, extracts text using Kreuzberg (stub),
 * and uploads the extracted text back to S3.
 *
 * Input map keys:
 * - "bucket" : S3 bucket name
 * - "key" : S3 object key of the uploaded file
 * - "jobId" : (optional) processing job ID
 *
 * Output map keys (added to input):
 * - "extractedTextKey" : S3 key of the extracted text file
 * - "extractedText" : the extracted text content
 */
public class TextExtractorHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(TextExtractorHandler.class);
    private static final String BUCKET_NAME = System.getenv("S3_UPLOAD_BUCKET");
    private final S3Client s3Client = S3Client.create();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String bucket = (String) input.getOrDefault("bucket", BUCKET_NAME);
        String key = (String) input.get("key");
        String jobId = (String) input.getOrDefault("jobId", UUID.randomUUID().toString());

        logger.info("TextExtractorHandler invoked for bucket: {}, key: {}, jobId: {}", bucket, key, jobId);

        try {
            // Download file from S3 to /tmp
            String fileName = key.substring(key.lastIndexOf("/") + 1);
            Path tempFile = Path.of("/tmp", fileName);

            try (InputStream is = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build())) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("Downloaded file to: {}", tempFile);

            // Extract text using Kreuzberg (stub — replace with PDFBox/Textract in
            // production)
            ExtractionConfig config = ExtractionConfig.builder()
                    .outputFormat("text")
                    .build();
            ExtractionResult result = Kreuzberg.extractFile(tempFile.toString(), config);
            String extractedText = result.getContent();
            logger.info("Extracted text length: {}", extractedText.length());

            // Upload extracted text to S3
            String textKey = "extracted_text/" + jobId + "/" + fileName + ".txt";
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(textKey)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromString(extractedText));
            logger.info("Uploaded extracted text to: {}", textKey);

            // Clean up temp file
            Files.deleteIfExists(tempFile);

            // Build output
            Map<String, Object> output = new HashMap<>(input);
            output.put("extractedTextKey", textKey);
            output.put("extractedText", extractedText);
            output.put("jobId", jobId);
            return output;

        } catch (Exception e) {
            logger.error("Error extracting text from {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
        }
    }
}
