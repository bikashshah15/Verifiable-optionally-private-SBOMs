package com.sbom.publicationrecord.web;

import com.sbom.publicationrecord.service.SbomPublicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(" "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERROR", message.isBlank() ? "Invalid request." : message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERROR", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            String pathReference = invalidFormat.getPathReference();
            boolean publicationModeField = pathReference != null && pathReference.contains("publicationMode");
            if (publicationModeField) {
                String allowedValues = Arrays.stream(com.sbom.publicationrecord.publication.PublicationMode.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse(
                                "ERROR",
                                "publicationMode must be one of: " + allowedValues + "."
                        ));
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERROR", "Malformed request body."));
    }

    @ExceptionHandler(SbomPublicationException.class)
    public ResponseEntity<ErrorResponse> handlePublicationFailure(SbomPublicationException ex) {
        logger.error("Zenodo publication workflow failed.", ex);
        String stage = ex.getPartialResult() != null && ex.getPartialResult().publicationStage() != null
                ? ex.getPartialResult().publicationStage().name()
                : null;
        String status = ex.getPartialResult() != null && ex.getPartialResult().publicationStatus() != null
                ? ex.getPartialResult().publicationStatus().name()
                : null;
        String progress = ex.getPartialResult() != null ? ex.getPartialResult().progressMessage() : null;
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("ERROR", ex.getMessage(), stage, status, progress));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleState(IllegalStateException ex) {
        logger.error("SBOM generation failed.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("ERROR", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ERROR", "Resource not found."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        logger.error("Unexpected error.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("ERROR", "Unexpected server error."));
    }
}
