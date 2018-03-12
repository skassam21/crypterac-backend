package com.crypterac.backend;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Base64;

@RestController
public class SendTransactionController
{

    private static final Web3j web3j = Web3j.build(new HttpService("https://ropsten.infura.io/uB6E6lwaacbBdi7rVDy7"));


    @Data
    @AllArgsConstructor
    private static class SendTransactionRequest
    {

        private final String toAddress;
        private final String amount;
    }

    /**
     * Sends crypto from the merchant to the card
     * Takes the public address of the card, and the amount to send
     *
     * @return success or error messages
     */
    @RequestMapping(method= RequestMethod.POST, value="/transactions/send")
    public @ResponseBody
    ResponseEntity send_transaction(@RequestBody
            SendTransactionRequest request)
            throws ErrorController.TransactionException {

        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger weiValue = Convert.toWei(request.getAmount(), Convert.Unit.FINNEY).toBigIntegerExact();
            BigInteger nonce = web3j.ethGetTransactionCount(Wallet.getPublicAddress(),
                    DefaultBlockParameterName.LATEST).send().getTransactionCount();
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    Transfer.GAS_LIMIT,
                    request.getToAddress(),
                    weiValue);
            Credentials credentials = Credentials.create(Wallet.getPrivateKey());

            System.out.println("nonce " + nonce);

            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
            String hexValue = "0x" + Hex.toHexString(signedMessage);
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            if (ethSendTransaction.hasError()) {
                System.out.println("Transaction Error: " + ethSendTransaction.getError().getMessage());
                return new ResponseEntity(HttpStatus.BAD_REQUEST);
            } else {
                System.out.println(String.format("Sent Ether to %s, with tx_id = %s",
                        Wallet.getPublicAddress(), hexValue));
                return new ResponseEntity(HttpStatus.OK);
            }
        } catch (Exception e) {
            throw new ErrorController.TransactionException(e.getMessage());
        }
    }
}
