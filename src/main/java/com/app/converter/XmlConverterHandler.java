package com.app.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.util.HashMap;
import java.util.Map;

public class XmlConverterHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");
    private final S3Client s3Client = S3Client.create();

    private final BedrockAnthropicMessageChatModel model = BedrockAnthropicMessageChatModel.builder()
            .model("anthropic.claude-3-haiku-20240307-v1:0")
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String extractedKey = (String) input.get("extractedTextKey");
        String jobId = (String) input.get("jobId");

        try {
            ResponseBytes<GetObjectResponse> s3Object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(extractedKey)
                    .build());
            String text = s3Object.asUtf8String();

            String prompt = "Extract structure from this text and wrap it as a structured XML exactly. Output only the raw XML document. Text:\\n"
                    + text;
            String xmlOut = model.generate(prompt);

            String targetKey = "converted_xml/" + jobId + "/"
                    + extractedKey.substring(extractedKey.lastIndexOf("/") + 1) + ".xml";
            s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(targetKey).build(),
                    RequestBody.fromString(xmlOut));

            Map<String, Object> output = new HashMap<>(input);
            output.put("xmlKey", targetKey);
            return output;
        } catch (Exception e) {
            throw new RuntimeException("Error converting text to xml: " + e.getMessage(), e);
        }
    }
}
