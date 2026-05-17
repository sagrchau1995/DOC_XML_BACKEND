# DOC_XML_BACKEND

A backend service for processing, converting, and extracting data from XML documents. This project is part of the AntiGravity suite and provides core functionality for XML handling, text extraction, and integration with external services.

## Features

- XML to text extraction
- XML merging and conversion
- Presigned URL generation for secure file access
- Configurable extraction logic

## Project Structure

```
pom.xml                # Maven project configuration
src/
  main/
    java/
      com/
        antigravity/
          converter/   # XML conversion and handler classes
        kreuzberg/     # Extraction configuration and result classes
```

## Getting Started

### Prerequisites

- Java 17 or later
- Maven 3.6+

### Build

```
mvn clean install
```

### Run

This project is a library/module. Integrate it into your application or use the provided handlers in your own service.

## Key Classes

- `ConverterFunction` - Core XML conversion logic
- `MergeXmlHandler` - Handles merging of XML files
- `PresignedUrlHandler` - Generates presigned URLs
- `TextExtractorHandler` - Extracts text from XML
- `XmlConverterHandler` - Main entry for XML conversion
- `ExtractionConfig`, `ExtractionResult` - Configuration and result models

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License

[MIT](LICENSE) (or specify your license here)
