package com.crypterac.backend;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SendTransactionController
{

    @Data
    @AllArgsConstructor
    private static class SendTransactionRequest
    {

        private final String fromAddress;
        private final String amount;
    }

    /**
     * Sends crypto from the merchant to the card
     * Takes the public address of the card, and the amount to send
     *
     * @return success or error messages
     */
    @RequestMapping("/transactions/send")
    public @ResponseBody
    String send_transaction() {
        return "success";
    }
}
