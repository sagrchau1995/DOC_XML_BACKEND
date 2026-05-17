package com.antigravity.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.util.List;
import java.util.Map;

public class MergeXmlHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");
    private final S3Client s3Client = S3Client.create();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            // Note: The input from Step Functions Map is an array of outputs from
            // XmlConverterHandler.
            // If Step Functions wraps it, it will look like {"processedFiles":
            // [{xmlKey="..."}, ...], "jobId": "..."}
            // But we actually only receive the Map array directly if we used ResultPath, so
            // the input
            // might have "processedFiles" as a List.
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> processedFiles = (List<Map<String, Object>>) input.get("processedFiles");
            if (processedFiles == null && input.containsKey("files")) { // Fallback handling
                // Check if the input directly is the array, or inside a key. Let's assume
                // processedFiles key is properly pushed by StepFunc
            }

            StringBuilder mergedXml = new StringBuilder();
            mergedXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\\n");
            mergedXml.append("<MergedDocument>\\n");

            String jobId = null;

            if (processedFiles != null) {
                for (Map<String, Object> fileOutput : processedFiles) {
                    if (jobId == null) {
                        jobId = (String) fileOutput.get("jobId");
                    }
                    String xmlKey = (String) fileOutput.get("xmlKey");
                    if (xmlKey != null) {
                        try {
                            ResponseBytes<GetObjectResponse> s3Object = s3Client
                                    .getObjectAsBytes(GetObjectRequest.builder()
                                            .bucket(BUCKET_NAME)
                                            .key(xmlKey)
                                            .build());
                            String xmlContent = s3Object.asUtf8String();
                            // strip out the xml declaration if any
                            xmlContent = xmlContent.replaceAll("(?i)<\\\\?xml[^>]*\\\\?>", "");
                            mergedXml.append("<DocumentFile key=\\\"").append(xmlKey).append("\\\">\\n");
                            mergedXml.append(xmlContent).append("\\n");
                            mergedXml.append("</DocumentFile>\\n");
                        } catch (Exception e) {
                            System.err.println("Failed to fetch " + xmlKey + ": " + e.getMessage());
                        }
                    }
                }
            }

            mergedXml.append("</MergedDocument>");

            if (jobId == null)
                jobId = "unknown-" + System.currentTimeMillis();

            String finalKey = "final/" + jobId + "/merged.xml";

            s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(finalKey).build(),
                    RequestBody.fromString(mergedXml.toString()));

            return Map.of("finalUrl", "s3://" + BUCKET_NAME + "/" + finalKey, "key", finalKey);
        } catch (Exception e) {
            throw new RuntimeException("Error merging xmls: " + e.getMessage(), e);
        }
    }
}
