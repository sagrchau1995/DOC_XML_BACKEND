package com.app.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PresignedUrlHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BUCKET_NAME = System.getenv("S3_UPLOAD_BUCKET");
    // Ensure you assign an AWS Region that matches your deployment, e.g.,
    // Region.US_EAST_1
    private static final Region REGION = Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1"));

    // Use default credentials provider chain for Lambda execution role
    private static final S3Presigner presigner = S3Presigner.builder()
            .region(REGION)
            .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // CORS Headers -> Ensure they match your API Gateway / frontend configuration
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers",
                "Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,GET");
        response.setHeaders(headers);

        try {
            // Extract the user identity from Cognito authorizer if it exists
            Map<String, Object> authorizer = request.getRequestContext() != null
                    ? request.getRequestContext().getAuthorizer()
                    : null;
            String userId = "anonymous";
            if (authorizer != null && authorizer.containsKey("claims")) {
                @SuppressWarnings("unchecked")
                Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
                if (claims.containsKey("sub")) {
                    userId = claims.get("sub");
                }
            }

            // Allow client to request a specific file name, otherwise generate one
            String fileName = UUID.randomUUID().toString() + ".pdf";
            if (request.getQueryStringParameters() != null
                    && request.getQueryStringParameters().containsKey("fileName")) {
                fileName = request.getQueryStringParameters().get("fileName");
            }

            // Prepend a UUID to avoid collisions
            String objectKey = "uploads/" + userId + "/" + UUID.randomUUID().toString() + "-" + fileName;

            if (BUCKET_NAME == null || BUCKET_NAME.isEmpty()) {
                throw new IllegalStateException("S3_UPLOAD_BUCKET environment variable is not defined");
            }

            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(objectKey)
                    // .contentType("application/pdf") // can be enforced if strictly PDF
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15)) // URL is valid for 15 minutes
                    .putObjectRequest(objectRequest)
                    .build();

            // Generate Pre-Signed URL
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);

            // Format response payload
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("url", presignedRequest.url().toString());
            responseBody.put("key", objectKey);
            responseBody.put("method", "PUT");

            response.setStatusCode(200);
            response.setBody(mapper.writeValueAsString(responseBody));

        } catch (Exception e) {
            context.getLogger().log("Error generating presigned URL: " + e.getMessage());
            response.setStatusCode(500);
            try {
                response.setBody(mapper.writeValueAsString(Map.of("error", e.getMessage())));
            } catch (Exception ex) {
                response.setBody("{\"error\":\"Internal Server Error\"}");
            }
        }

        return response;
    }
}
