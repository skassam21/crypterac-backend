package com.crypterac.backend;

/**
 * Created by skassam on 2018-03-10.
 */
public class Wallet
{
    // Credentials of the merchant
    // TODO: this should be stored in the database and encrypted
    // TODO: put the right PRIVATE KEY
    private static final String PRIVATE_KEY = "0x1";
    private static final String PUBLIC_ADDRESS = "0x64ef12BC968Fd3F5F8B63646Db27be92FF0fEC55";

    public static String getPrivateKey()
    {
        return PRIVATE_KEY;
    }

    public static String getPublicAddress()
    {
        return PUBLIC_ADDRESS;
    }
}
