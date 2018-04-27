package com.crypterac.backend;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

@RestController
public class SendTransactionController
{

    private static final Web3j web3j = Web3j.build(new HttpService("https://ropsten.infura.io/uB6E6lwaacbBdi7rVDy7"));
    private static final String CRYPTERAC_CONTRACT = "0x924c5735570a371962051ea15b6d9e37eb6f5af4";

    private static final String CRYPTERAC_TYPE = "CRTC";
    private static final String ETHER_TYPE = "ETH";

    /**
     * Approve Crypto to be sent between to parties
     *
     * @return success or error messages
     */
    @RequestMapping(method= RequestMethod.POST, value="/transactions/approve")
    public @ResponseBody
    SendTransactionResponse approve_transaction()
            throws ErrorController.TransactionException {

        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            System.out.println("gasPrice " + gasPrice);
            BigInteger nonce = web3j.ethGetTransactionCount(Wallet.getPublicAddress(),
                    DefaultBlockParameterName.LATEST).send().getTransactionCount();
            RawTransaction rawTransaction;

            Function function = new Function("approve",
                    Arrays.<Type>asList(new Address("0x041ffaab716df567a31fb9673d0645d08eb7e6c1"),
                            new Uint256(new BigInteger("1000000"))),
                    Collections.<TypeReference<?>>emptyList());
            String encodedFunction = FunctionEncoder.encode(function);

            rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPrice, new BigInteger("50000"),
                    CRYPTERAC_CONTRACT,
                    encodedFunction);

            Credentials credentials = Credentials.create(Wallet.getPrivateKey());

            System.out.println("nonce " + nonce);

            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
            String hexValue = "0x" + Hex.toHexString(signedMessage);
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            if (ethSendTransaction.hasError()) {
                System.out.println("Transaction Error: " + ethSendTransaction.getError().getMessage());
                return new SendTransactionResponse(false, ethSendTransaction.getError().getMessage());
            } else {
                System.out.println(String.format("Approved %s, with tx_id = %s",
                        CRYPTERAC_CONTRACT, hexValue));
                return new SendTransactionResponse(true, "");
            }
        } catch (Exception e) {
            throw new ErrorController.TransactionException(e.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    private static class SendTransactionRequest
    {
        private final String toAddress;
        private final String amount;
        private final String type;
    }

    @Data
    @AllArgsConstructor
    private static class SendTransactionResponse
    {

        private final boolean success;
        private final String reason;
    }

    /**
     * Sends crypto from the merchant to the card
     * Takes the public address of the card, and the amount to send
     *
     * @return success or error messages
     */
    @RequestMapping(method= RequestMethod.POST, value="/transactions/send")
    public @ResponseBody
    SendTransactionResponse send_transaction(@RequestBody
            SendTransactionRequest request)
            throws ErrorController.TransactionException {

        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            System.out.println("gasPrice " + gasPrice);
            BigInteger weiValue = Convert.toWei(request.getAmount(), Convert.Unit.FINNEY).toBigIntegerExact();
            BigInteger nonce = web3j.ethGetTransactionCount(Wallet.getPublicAddress(),
                    DefaultBlockParameterName.LATEST).send().getTransactionCount();
            String toAddress = convertECPublicKeyToAddress(Base64.getDecoder().decode(request.getToAddress()));
            RawTransaction rawTransaction;
            if (request.getType().equals(CRYPTERAC_TYPE)) {
                Function function = new Function("transfer",
                        Arrays.<Type>asList(new Address(toAddress),
                                new Uint256(new BigInteger(request.getAmount()))),
                        Collections.<TypeReference<?>>emptyList());
                String encodedFunction = FunctionEncoder.encode(function);

                rawTransaction = RawTransaction.createTransaction(
                        nonce,
                        gasPrice, new BigInteger("250000"),
                        CRYPTERAC_CONTRACT,
                        encodedFunction);
            } else {
                rawTransaction = RawTransaction.createEtherTransaction(
                        nonce,
                        gasPrice,
                        Transfer.GAS_LIMIT,
                        toAddress,
                        weiValue);
            }
            Credentials credentials = Credentials.create(Wallet.getPrivateKey());

            System.out.println("nonce " + nonce);

            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
            String hexValue = "0x" + Hex.toHexString(signedMessage);
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            if (ethSendTransaction.hasError()) {
                System.out.println("Transaction Error: " + ethSendTransaction.getError().getMessage());
                return new SendTransactionResponse(false, ethSendTransaction.getError().getMessage());
            } else {
                System.out.println(String.format("Sent Token to %s, with tx_id = %s",
                        toAddress, hexValue));

                String value = request.getType().equals(CRYPTERAC_TYPE) ? request.getAmount() : weiValue.toString();
                sendPendingTransaction(toAddress,
                        Wallet.getPublicAddress(), ethSendTransaction.getTransactionHash(), value,
                        request.getType());
                return new SendTransactionResponse(true, "");
            }
        } catch (Exception e) {
            throw new ErrorController.TransactionException(e.getMessage());
        }
    }

    public static String convertECPublicKeyToAddress(byte[] data) {

        return Numeric.prependHexPrefix(Keys.getAddress(convertECPublicKeyToBigInteger(data)));
    }

    public static BigInteger convertECPublicKeyToBigInteger(byte[] data) {
        byte[] newArray = Arrays.copyOfRange(data, 0, data.length);
        newArray[0] = 0x00;
        return new BigInteger(newArray);
    }

    private static void sendPendingTransaction(String to, String from, String hashId, String amount,
                                               String type) {
        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("https://crypterac-frontend.appspot.com/api/transactions/create");

        try {
            JSONObject params = new JSONObject();
            params.put("to", to);
            params.put("from", from);
            params.put("amount", amount);
            params.put("hashId", hashId);
            params.put("type", type);
            StringEntity requestEntity = new StringEntity(
                    params.toString(),
                    ContentType.APPLICATION_JSON);
            httppost.setEntity(requestEntity);
            //Execute and don't worry about the response.
            httpclient.execute(httppost);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }
}
