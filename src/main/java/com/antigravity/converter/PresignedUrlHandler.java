package com.antigravity.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.*;

public class PresignedUrlHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            // Ensure Cognito claims are available
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            if (authorizer == null || !authorizer.containsKey("claims")) {
                response.setStatusCode(401);
                response.setBody(mapper.writeValueAsString(Map.of("error", "Unauthorized")));
                return response;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            String userId = claims.get("sub");

            if (userId == null || userId.isEmpty()) {
                response.setStatusCode(401);
                response.setBody(mapper.writeValueAsString(Map.of("error", "User ID not found in token")));
                return response;
            }

            JsonNode bodyParams = mapper.readTree(request.getBody());
            String filename = bodyParams.get("filename").asText();

            String s3Key = "users/" + userId + "/" + filename;

            try (S3Presigner presigner = S3Presigner.create()) {
                PutObjectRequest objectRequest = PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(s3Key)
                        .build();

                PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .putObjectRequest(objectRequest)
                        .build();

                PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
                String url = presignedRequest.url().toString();

                Map<String, String> res = new HashMap<>();
                res.put("url", url);
                res.put("s3Key", s3Key);

                response.setStatusCode(200);
                response.setHeaders(Map.of("Access-Control-Allow-Origin", "*"));
                response.setBody(mapper.writeValueAsString(res));
                return response;
            }
        } catch (Exception e) {
            response.setStatusCode(500);
            response.setBody(e.getMessage());
            return response;
        }
    }
}
