package com.crypterac.backend;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class ErrorController
{
    @Data
    @AllArgsConstructor
    private class ErrorResponse
    {

        private final String errorMessage;
    }

    static class TransactionException extends Exception {
        TransactionException(String message)
        {
            super(message);
        }
    }

    @ExceptionHandler(TransactionException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public @ResponseBody
    ErrorResponse handleException(TransactionException e) {
        return new ErrorResponse(e.getMessage());
    }
}
