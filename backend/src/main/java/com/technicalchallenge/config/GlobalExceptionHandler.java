package com.technicalchallenge.config; 

import com.technicalchallenge.dto.ErrorResponse; 
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(cz.jirutka.rsql.parser.RSQLParserException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleRsqlParseException(cz.jirutka.rsql.parser.RSQLParserException ex) {
        
        String causeMessage = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();

        return new ErrorResponse(
            "Invalid RSQL Query Syntax", 
            "The query could not be parsed. Details: " + causeMessage
        );
    }
}