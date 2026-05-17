package com.antigravity.converter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConverterFunction implements RequestHandler<S3Event, String> {

    private static final Logger logger = LoggerFactory.getLogger(ConverterFunction.class);

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        logger.info("Lambda invoked to process S3 event.");

        // Future: Initialize LangChain4j and Amazon Bedrock integration
        // and parse the incoming PDF/Image to XML

        if (s3Event != null && s3Event.getRecords() != null) {
            s3Event.getRecords().forEach(record -> {
                String bucket = record.getS3().getBucket().getName();
                String key = record.getS3().getObject().getKey();
                logger.info("Processing file from bucket: {} and key: {}", bucket, key);
            });
        }

        return "Successfully processed S3 document.";
    }
}
