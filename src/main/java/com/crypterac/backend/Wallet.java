package com.crypterac.backend;

/**
 * Created by skassam on 2018-03-10.
 */
public class Wallet
{
    // Credentials of the merchant
    // TODO: this should be stored in the database and encrypted
    // TODO: put the right PRIVATE KEY
    private static final String PRIVATE_KEY = "2f68bbcfed2df3b6a6b786a07494b820ad813805ad6666ee46be20bf8b690111";
    private static final String PUBLIC_ADDRESS = "0x01BDAd4c19E2E4FaEc994FBE4F0398eBC83503F1";

    public static String getPrivateKey()
    {
        return PRIVATE_KEY;
    }

    public static String getPublicAddress()
    {
        return PUBLIC_ADDRESS;
    }
}
