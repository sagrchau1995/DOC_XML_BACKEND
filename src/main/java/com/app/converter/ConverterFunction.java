package com.app.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.app.kreuzberg.ExtractionConfig;
import com.app.kreuzberg.ExtractionResult;
import com.app.kreuzberg.Kreuzberg;
import dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Main Lambda handler triggered by S3 upload events.
 * Orchestrates the full pipeline:
 * 1. Download uploaded file from S3
 * 2. Extract text from the document (Kreuzberg stub / future: PDFBox or
 * Textract)
 * 3. Upload extracted text to S3
 * 4. Convert extracted text to structured XML via Amazon Bedrock (Claude 3
 * Haiku)
 * 5. Upload resulting XML to S3
 */
public class ConverterFunction implements RequestHandler<S3Event, String> {

    private static final Logger logger = LoggerFactory.getLogger(ConverterFunction.class);
    private static final String BUCKET_NAME = System.getenv("S3_UPLOAD_BUCKET");

    private final S3Client s3Client = S3Client.create();

    private final BedrockAnthropicMessageChatModel model = BedrockAnthropicMessageChatModel.builder()
            .model("anthropic.claude-3-haiku-20240307-v1:0")
            .region(Region.US_EAST_1)
            .build();

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        logger.info("Lambda invoked to process S3 event.");

        if (s3Event == null || s3Event.getRecords() == null || s3Event.getRecords().isEmpty()) {
            logger.warn("Received empty S3 event, nothing to process.");
            return "No records to process.";
        }

        int successCount = 0;
        int failCount = 0;

        for (var record : s3Event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            // S3 event notifications URL-encode the key (spaces → +), so we must decode it
            String key = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8);
            logger.info("Processing file from bucket: {} and key: {}", bucket, key);

            try {
                String jobId = UUID.randomUUID().toString();

                // ── Step 1: Download the uploaded file to /tmp ──
                Path tempFile = downloadFromS3(bucket, key);
                logger.info("Downloaded file to: {}", tempFile);

                // ── Step 2: Extract text from the document ──
                String extractedText = extractText(tempFile);
                logger.info("Extracted text length: {}", extractedText.length());

                // ── Step 3: Upload extracted text to S3 ──
                String textKey = "extracted_text/" + jobId + "/" + getFileName(key) + ".txt";
                uploadToS3(bucket, textKey, extractedText);
                logger.info("Uploaded extracted text to: {}", textKey);

                // ── Step 4: Convert text to XML via Bedrock ──
                String xmlContent = convertToXml(extractedText);
                logger.info("Bedrock XML conversion complete, output length: {}", xmlContent.length());

                // ── Step 5: Upload XML output to S3 ──
                String xmlKey = "converted_xml/" + jobId + "/" + getFileName(key) + ".xml";
                uploadToS3(bucket, xmlKey, xmlContent);
                logger.info("Uploaded converted XML to: {}", xmlKey);

                // Clean up temp file
                Files.deleteIfExists(tempFile);
                successCount++;
                logger.info("Successfully processed file: {} -> {}", key, xmlKey);

            } catch (Exception e) {
                failCount++;
                logger.error("Error processing file {}: {}", key, e.getMessage(), e);
            }
        }

        String result = String.format("Processed %d files successfully, %d failures.", successCount, failCount);
        logger.info(result);
        return result;
    }

    /**
     * Downloads a file from S3 to the Lambda /tmp directory.
     */
    private Path downloadFromS3(String bucket, String key) throws Exception {
        String fileName = getFileName(key);
        Path tempFile = Path.of("/tmp", fileName);

        try (InputStream is = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    /**
     * Extracts text from the downloaded document using Kreuzberg (stub).
     * In production, replace with PDFBox, AWS Textract, or a real Kreuzberg
     * implementation.
     */
    private String extractText(Path filePath) {
        ExtractionConfig config = ExtractionConfig.builder()
                .outputFormat("text")
                .build();
        ExtractionResult result = Kreuzberg.extractFile(filePath.toString(), config);
        return result.getContent();
    }

    /**
     * Converts extracted text to structured XML using Amazon Bedrock (Claude 3
     * Haiku).
     */
    private String convertToXml(String extractedText) {
        String prompt = """
                You are an expert document parser. Given the following extracted text from a document,
                convert it into a well-structured XML format. The XML should:
                1. Have a root element <document>
                2. Organize content into logical sections with <section> elements
                3. Preserve headings with <heading> elements
                4. Wrap paragraphs in <paragraph> elements
                5. Preserve any tables, lists, or structured data appropriately

                Output ONLY the raw XML document, no explanations or markdown.

                Text:
                """ + extractedText;

        return model.generate(prompt);
    }

    /**
     * Uploads a string content to S3.
     */
    private void uploadToS3(String bucket, String key, String content) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(key.endsWith(".xml") ? "application/xml" : "text/plain")
                        .build(),
                RequestBody.fromString(content));
    }

    /**
     * Extracts the file name from an S3 object key.
     */
    private String getFileName(String key) {
        return key.substring(key.lastIndexOf("/") + 1);
    }
}
