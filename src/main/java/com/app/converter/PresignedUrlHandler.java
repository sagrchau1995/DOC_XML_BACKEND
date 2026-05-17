package com.app.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
// ...existing code...
import com.fasterxml.jackson.databind.ObjectMapper;
// ...existing code...

// ...existing code...
import java.util.*;

public class PresignedUrlHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper mapper = new ObjectMapper();
    // ...existing code...

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            // Ensure Cognito claims are available
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            if (authorizer == null || !authorizer.containsKey("claims")) {
                response.setStatusCode(401);
                try {
                    response.setBody(mapper.writeValueAsString(Map.of("error", "Unauthorized")));
                } catch (Exception ex) {
                    response.setBody("{\"error\":\"Unauthorized\"}");
                }
                return response;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            String userId = claims.get("sub");

            if (userId == null || userId.isEmpty()) {
                response.setStatusCode(401);
                try {
                    response.setBody(mapper.writeValueAsString(Map.of("error", "User ID not found in token")));
                } catch (Exception ex) {
                    response.setBody("{\"error\":\"User ID not found in token\"}");
                }
            }
            // ...existing code...
        } catch (Exception e) {
            response.setStatusCode(500);
            try {
                response.setBody(mapper.writeValueAsString(Map.of("error", e.getMessage())));
            } catch (Exception ex) {
                response.setBody("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
        return response;
    }
}
