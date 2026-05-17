package com.kreuzberg;

// Stubbed Kreuzberg Classes so the project compiles.
// Real library should be installed or compiled via native JNI.
public class Kreuzberg {
    public static ExtractionResult extractFile(String filePath, ExtractionConfig config) {
        return new ExtractionResult("Extracted text from " + filePath);
    }
}
