package com.crypterac.backend;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;

@RestController
public class ReceiveTransactionController
{

    private static final Web3j web3j = Web3j.build(new HttpService("https://ropsten.infura.io/uB6E6lwaacbBdi7rVDy7"));

    @Data
    @AllArgsConstructor
    private static class ReceiveTransactionRequest
    {

        private final String fromAddress;
        private final String amount;
    }

    @Data
    @AllArgsConstructor
    private static class TransactionDetails {
        private final BigInteger nonce;
        private final BigInteger gasPrice;
        private final BigInteger gasLimit;
        private final String toAddress;
        private final BigInteger value;
    }

    @Data
    @AllArgsConstructor
    private static class ReceiveTransactionResponse
    {

        private final String message;
        private final TransactionDetails transactionDetails;
    }

    /**
     * Starts a transaction to be signed on the card (sending from card to merchant)
     * Takes the address of the card, and the amount to send
     *
     * @return a base 64 encoded version of the message to be signed by the card
     */
    @RequestMapping(method=RequestMethod.POST, value="/transactions/receive")
    public @ResponseBody
    ReceiveTransactionResponse start_receive_transaction(@RequestBody ReceiveTransactionRequest receiveTransaction)
            throws ErrorController.TransactionException
    {
        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger weiValue = Convert.toWei(receiveTransaction.getAmount(), Convert.Unit.FINNEY).toBigIntegerExact();
            String fromAddress = convertECPublicKeyToAddress(Base64.getDecoder().decode(receiveTransaction.getFromAddress()));

            System.out.println("fromAddress " + fromAddress);

            BigInteger nonce = web3j.ethGetTransactionCount(fromAddress,
                    DefaultBlockParameterName.LATEST).send().getTransactionCount();
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    Transfer.GAS_LIMIT,
                    Wallet.getPublicAddress(),
                    weiValue);
            byte[] txBytes = TransactionEncoder.encode(rawTransaction);
            byte[] messageHash = Hash.sha3(txBytes);
            // use base64 encoding to transfer bytes
            String message = new String(Base64.getEncoder().encode(messageHash));
            TransactionDetails details = new TransactionDetails(
                    nonce,
                    gasPrice,
                    Transfer.GAS_LIMIT,
                    Wallet.getPublicAddress(),
                    weiValue
            );
            return new ReceiveTransactionResponse(message, details);
        } catch (Exception e) {
            throw new ErrorController.TransactionException(e.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    private static class CompleteTransactionRequest
    {

        private final String respData;
        private final String message;
        private final String fromAddress;
        private final TransactionDetails transactionDetails;
    }

    @Data
    @AllArgsConstructor
    private static class CompleteTransactionResponse
    {

        private final boolean success;
    }

    /***
     * Completes the transaction by sending the signed transaction on the blockchain
     *
     * Takes the signed data from the card, and sends the transaction to the blockchain
     *
     * @return success or error messages
     */
    @RequestMapping("/transactions/receive/complete")
    public @ResponseBody
    CompleteTransactionResponse complete_receive_transaction(@RequestBody CompleteTransactionRequest request)
            throws ErrorController.TransactionException
    {
        byte[] respData = Base64.getDecoder().decode(request.getRespData());
        byte[] messageHash = Base64.getDecoder().decode(request.getMessage());
        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                request.getTransactionDetails().getNonce(),
                request.getTransactionDetails().getGasPrice(),
                request.getTransactionDetails().getGasLimit(),
                request.getTransactionDetails().getToAddress(),
                request.getTransactionDetails().getValue()
        );


        try {
            Sign.SignatureData signature = createSignature(respData, messageHash,
                    Base64.getDecoder().decode(request.getFromAddress()));
            Method encode = TransactionEncoder.class.getDeclaredMethod("encode", RawTransaction.class,
                    Sign.SignatureData.class);
            encode.setAccessible(true);
            byte[] signedMessage = (byte[]) encode.invoke(null, rawTransaction, signature);
            String hexValue = "0x" + Hex.toHexString(signedMessage);
            System.out.println(hexValue);
//            return new ResponseEntity(HttpStatus.OK);
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            if (ethSendTransaction.hasError()) {
                System.out.println("Transaction Error: " + ethSendTransaction.getError().getMessage());

                return new CompleteTransactionResponse(false);
            } else {
                System.out.println(String.format("Sent Ether to %s, with tx_id = %s",
                        Wallet.getPublicAddress(), hexValue));
                return new CompleteTransactionResponse(true);
            }
        } catch (Exception e) {
            throw new ErrorController.TransactionException(e.getMessage());
        }
    }

    private Sign.SignatureData createSignature(byte[] respData, byte[] messageHash, byte[] pubData)
            throws Exception
    {
        byte[] rawSig = extractSignature(respData);

        int rLen = rawSig[3];
        int sOff = 6 + rLen;
        int sLen = rawSig.length - rLen - 6;

        BigInteger r = new BigInteger(Arrays.copyOfRange(rawSig, 4, 4 + rLen));
        BigInteger s = new BigInteger(Arrays.copyOfRange(rawSig, sOff, sOff + sLen));
        System.out.println(String.format("r: %s", r));
        System.out.println(String.format("s: %s", s));

        Class<?> ecdsaSignature = Class.forName("org.web3j.crypto.Sign$ECDSASignature");
        Constructor ecdsaSignatureConstructor = ecdsaSignature.getDeclaredConstructor(BigInteger.class, BigInteger.class);
        ecdsaSignatureConstructor.setAccessible(true);
        Object sig = ecdsaSignatureConstructor.newInstance(r, s);
        Method m = ecdsaSignature.getMethod("toCanonicalised");
        m.setAccessible(true);
        sig = m.invoke(sig);

        Method recoverFromSignature = Sign.class.getDeclaredMethod("recoverFromSignature", int.class, ecdsaSignature, byte[].class);
        recoverFromSignature.setAccessible(true);

        BigInteger publicKey = convertECPublicKeyToBigInteger(pubData);

        int recId = -1;
        for (int i = 0; i < 4; i++) {
            BigInteger k = (BigInteger) recoverFromSignature.invoke(null, i, sig, messageHash);
            if (k != null && k.equals(publicKey)) {
                recId = i;
                break;
            }
        }
        if (recId == -1) {
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        }

        int headerByte = recId + 27;

        Field rF = ecdsaSignature.getDeclaredField("r");
        rF.setAccessible(true);
        Field sF = ecdsaSignature.getDeclaredField("s");
        sF.setAccessible(true);
        r = (BigInteger) rF.get(sig);
        s = (BigInteger) sF.get(sig);

        // 1 header + 32 bytes for R + 32 bytes for S
        byte v = (byte) headerByte;
        byte[] rB = Numeric.toBytesPadded(r, 32);
        byte[] sB = Numeric.toBytesPadded(s, 32);

        return new Sign.SignatureData(v, rB, sB);
    }

    private byte[] extractSignature(byte[] sig)
    {
        int off = 0;
        return Arrays.copyOfRange(sig, off, off + sig[off + 1] + 2);
    }

    private static String convertECPublicKeyToAddress(byte[] data) {

        return Numeric.prependHexPrefix(Keys.getAddress(convertECPublicKeyToBigInteger(data)));
    }

    private static BigInteger convertECPublicKeyToBigInteger(byte[] data) {
        byte[] newArray = Arrays.copyOfRange(data, 0, data.length);
        newArray[0] = 0x00;
        return new BigInteger(newArray);
    }
}
