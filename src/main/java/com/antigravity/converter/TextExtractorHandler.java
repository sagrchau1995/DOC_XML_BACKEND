package com.antigravity.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.kreuzberg.ExtractionConfig;
import com.kreuzberg.ExtractionResult;
import com.kreuzberg.Kreuzberg;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class TextExtractorHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");
    private final S3Client s3Client = S3Client.create();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String s3Key = (String) input.get("fileKey");
        String jobId = (String) input.get("jobId");

        try {
            // Download from S3 to temp
            Path path = Paths.get("/tmp/" + s3Key.substring(s3Key.lastIndexOf("/") + 1));
            try (InputStream in = s3Client
                    .getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key(s3Key).build())) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }

            // Extract using Kreuzberg
            ExtractionConfig config = ExtractionConfig.builder().outputFormat("plain").build();
            ExtractionResult result = Kreuzberg.extractFile(path.toString(), config);
            String extractedText = result.getContent();

            // Put Extracted Text back to S3
            String targetKey = "extracted/" + jobId + "/" + s3Key.substring(s3Key.lastIndexOf("/") + 1) + ".txt";
            s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(targetKey).build(),
                    RequestBody.fromString(extractedText));

            Map<String, Object> output = new HashMap<>(input);
            output.put("extractedTextKey", targetKey);
            return output;
        } catch (Exception e) {
            throw new RuntimeException("Error extracting text: " + e.getMessage(), e);
        }
    }
}
