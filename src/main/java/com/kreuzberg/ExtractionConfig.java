package com.kreuzberg;

public class ExtractionConfig {
    private String outputFormat;

    public String getOutputFormat() {
        return this.outputFormat;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String outputFormat;

        public Builder outputFormat(String format) {
            this.outputFormat = format;
            return this;
        }

        public ExtractionConfig build() {
            ExtractionConfig c = new ExtractionConfig();
            c.outputFormat = this.outputFormat;
            return c;
        }
    }
}
