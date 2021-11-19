package org.stellar.sdk;

import org.stellar.sdk.xdr.AccountID;

/**
 * For more information on Stellar Key formats see: https://developers.stellar.org/docs/tutorials/create-account/
 */
public class StellarKeyUtils {

    /**
     * @param inputKey Any Stellar private key
     * @return true if it's a private key, false if it's a public key
     * @throws IllegalArgumentException neither a public key nor a private key
     */
    public static boolean isPrivateKey(String inputKey) {
        if (inputKey.startsWith(("S"))) {
            return true;
        } else if (inputKey.startsWith("G")) {
            return false;
        } else {
            throw new IllegalArgumentException("Invalid Key supplied");
        }
    }

    /**
     * @param inputKey Any Stellar public key
     * @return true if it's a public key, false if it's a private key
     * @throws IllegalArgumentException neither a public key nor a private key
     */
    public static boolean isPublicKey(String inputKey) {
        return !isPrivateKey(inputKey);
    }

    /**
     * Note - do not use this method in production, using a String for the input string is insecure, see
     * https://docs.oracle.com/javase/1.5.0/docs/guide/security/jce/JCERefGuide.html#PBEEx
     * @param secretKey Secret Key from https://laboratory.stellar.org/#account-creator?network=test
     * @return Keypair object
     */
    public static KeyPair createKeyPair(String secretKey) {
        return KeyPair.fromSecretSeed(secretKey);
    }

    /**
     *
     * @param publicKey
     * @return
     */
    public static KeyPair createPublicKeyFromString(String publicKey) {
        if (!isPublicKey(publicKey)) throw new IllegalArgumentException("Invalid public key supplied");
        AccountID accountID = StrKey.encodeToXDRAccountId(publicKey);
        byte[] publicKeyBytes = accountID.getAccountID().getEd25519().getUint256();
        return KeyPair.fromPublicKey(publicKeyBytes);
    }

}
