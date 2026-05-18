package com.app.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges multiple individual XML files into a single combined XML document.
 *
 * Input map keys:
 * - "bucket" : S3 bucket name
 * - "xmlKeys" : List of S3 keys to individual XML files
 * - "jobId" : processing job ID
 *
 * Output map keys:
 * - "mergedXmlKey" : S3 key of the merged XML file
 */
public class MergeXmlHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(MergeXmlHandler.class);
    private static final String BUCKET_NAME = System.getenv("S3_UPLOAD_BUCKET");
    private final S3Client s3Client = S3Client.create();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String bucket = (String) input.getOrDefault("bucket", BUCKET_NAME);
        String jobId = (String) input.get("jobId");
        List<String> xmlKeys = (List<String>) input.get("xmlKeys");

        logger.info("MergeXmlHandler invoked for jobId: {}, merging {} XML files", jobId,
                xmlKeys != null ? xmlKeys.size() : 0);

        try {
            StringBuilder merged = new StringBuilder();
            merged.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            merged.append("<merged-documents jobId=\"").append(jobId).append("\">\n");

            if (xmlKeys != null) {
                for (int i = 0; i < xmlKeys.size(); i++) {
                    String xmlKey = xmlKeys.get(i);
                    ResponseBytes<GetObjectResponse> s3Object = s3Client.getObjectAsBytes(
                            GetObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(xmlKey)
                                    .build());
                    String xmlContent = s3Object.asUtf8String();

                    // Strip XML declaration from individual files before merging
                    xmlContent = xmlContent.replaceFirst("<\\?xml[^?]*\\?>\\s*", "");

                    merged.append("  <!-- Source: ").append(xmlKey).append(" -->\n");
                    merged.append("  <source-document index=\"").append(i + 1).append("\">\n");
                    merged.append("    ").append(xmlContent.trim()).append("\n");
                    merged.append("  </source-document>\n");
                }
            }

            merged.append("</merged-documents>\n");

            // Upload merged XML to S3
            String mergedKey = "merged_xml/" + jobId + "/merged_output.xml";
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(mergedKey)
                            .contentType("application/xml")
                            .build(),
                    RequestBody.fromString(merged.toString()));
            logger.info("Uploaded merged XML to: {}", mergedKey);

            Map<String, Object> output = new HashMap<>(input);
            output.put("mergedXmlKey", mergedKey);
            return output;

        } catch (Exception e) {
            logger.error("Error merging XML files for jobId {}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("XML merge failed: " + e.getMessage(), e);
        }
    }
}
