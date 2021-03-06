/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.common.crypto;

import io.bitsquare.common.util.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.*;
import java.util.Arrays;

// TODO: which counter modes and paddings should we use?
// https://security.stackexchange.com/questions/52665/which-is-the-best-cipher-mode-and-padding-mode-for-aes-encryption
public class Encryption {
    private static final Logger log = LoggerFactory.getLogger(Encryption.class);

    public static final String ASYM_KEY_ALGO = "RSA"; // RSA/NONE/OAEPWithSHA256AndMGF1Padding
    public static final String ASYM_CIPHER = "RSA";

    public static final String SYM_KEY_ALGO = "AES"; // AES/CTR/NoPadding
    public static final String SYM_CIPHER = "AES";

    public static final String HMAC = "HmacSHA256";

    public static KeyPair generateKeyPair() {
        long ts = System.currentTimeMillis();
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ASYM_KEY_ALGO, "BC");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.genKeyPair();
            log.trace("Generate msgEncryptionKeyPair needed {} ms", System.currentTimeMillis() - ts);
            return keyPair;
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Could not create key.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Symmetric
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static byte[] encrypt(byte[] payload, SecretKey secretKey) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(SYM_CIPHER, "BC");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(payload);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new CryptoException(e);
        }
    }

    public static byte[] decrypt(byte[] encryptedPayload, SecretKey secretKey) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(SYM_CIPHER, "BC");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(encryptedPayload);
        } catch (Throwable e) {
            throw new CryptoException(e);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Hmac
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static byte[] getPayloadWithHmac(byte[] payload, SecretKey secretKey) {
        byte[] payloadWithHmac;
        try {

            ByteArrayOutputStream outputStream = null;
            try {
                byte[] hmac = getHmac(payload, secretKey);
                outputStream = new ByteArrayOutputStream();
                outputStream.write(payload);
                outputStream.write(hmac);
                outputStream.flush();
                payloadWithHmac = outputStream.toByteArray().clone();
            } catch (IOException | NoSuchProviderException e) {
                e.printStackTrace();
                throw new RuntimeException("Could not create hmac");
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Could not create hmac");
        }
        return payloadWithHmac;
    }


    private static boolean verifyHmac(byte[] message, byte[] hmac, SecretKey secretKey) throws CryptoException {
        try {
            byte[] hmacTest = getHmac(message, secretKey);
            return Arrays.equals(hmacTest, hmac);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Could not create cipher");
        }
    }

    private static byte[] getHmac(byte[] payload, SecretKey secretKey) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        Mac mac = Mac.getInstance(HMAC, "BC");
        mac.init(secretKey);
        return mac.doFinal(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Symmetric with Hmac
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static byte[] encryptPayloadWithHmac(Serializable object, SecretKey secretKey) throws CryptoException {
        return encryptPayloadWithHmac(Utilities.serialize(object), secretKey);
    }

    public static byte[] encryptPayloadWithHmac(byte[] payload, SecretKey secretKey) throws CryptoException {
        return encrypt(getPayloadWithHmac(payload, secretKey), secretKey);
    }

    public static byte[] decryptPayloadWithHmac(byte[] encryptedPayloadWithHmac, SecretKey secretKey) throws CryptoException {
        byte[] payloadWithHmac = decrypt(encryptedPayloadWithHmac, secretKey);
        String payloadWithHmacAsHex = Hex.toHexString(payloadWithHmac);
        // first part is raw message
        int length = payloadWithHmacAsHex.length();
        int sep = length - 64;
        String payloadAsHex = payloadWithHmacAsHex.substring(0, sep);
        // last 64 bytes is hmac
        String hmacAsHex = payloadWithHmacAsHex.substring(sep, length);
        if (verifyHmac(Hex.decode(payloadAsHex), Hex.decode(hmacAsHex), secretKey)) {
            return Hex.decode(payloadAsHex);
        } else {
            throw new CryptoException("Hmac does not match.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Asymmetric
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static byte[] encrypt(byte[] payload, PublicKey publicKey) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(ASYM_CIPHER, "BC");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(payload);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new CryptoException("Couldn't encrypt payload");
        }
    }

    public static byte[] decrypt(byte[] encryptedPayload, PrivateKey privateKey) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(ASYM_CIPHER, "BC");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(encryptedPayload);
        } catch (Throwable e) {
            // errors when trying to decrypt foreign messages are normal
            throw new CryptoException(e);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Hybrid with signature of asymmetric key
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param payload             The data to encrypt.
     * @param signatureKeyPair    The key pair for signing.
     * @param encryptionPublicKey The public key used for encryption.
     * @return A SealedAndSigned object.
     * @throws CryptoException
     */
    public static SealedAndSigned encryptHybridWithSignature(Serializable payload, KeyPair signatureKeyPair,
                                                             PublicKey encryptionPublicKey)
            throws CryptoException {
        // Create a symmetric key
        SecretKey secretKey = generateSecretKey();

        // Encrypt secretKey with receivers publicKey 
        byte[] encryptedSecretKey = encrypt(secretKey.getEncoded(), encryptionPublicKey);

        // Encrypt with sym key payload with appended hmac
        byte[] encryptedPayloadWithHmac = encryptPayloadWithHmac(payload, secretKey);

        // sign hash of encryptedPayloadWithHmac
        byte[] hash = Hash.getHash(encryptedPayloadWithHmac);
        byte[] signature = Sig.sign(signatureKeyPair.getPrivate(), hash);

        // Pack all together
        return new SealedAndSigned(encryptedSecretKey, encryptedPayloadWithHmac, signature, signatureKeyPair.getPublic());
    }

    /**
     * @param sealedAndSigned The sealedAndSigned object.
     * @param privateKey      The private key for decryption
     * @return A DecryptedPayloadWithPubKey object.
     * @throws CryptoException
     */
    public static DecryptedPayloadWithPubKey decryptHybridWithSignature(SealedAndSigned sealedAndSigned, PrivateKey privateKey) throws CryptoException {
        SecretKey secretKey = getSecretKeyFromBytes(decrypt(sealedAndSigned.encryptedSecretKey, privateKey));
        boolean isValid = Sig.verify(sealedAndSigned.sigPublicKey,
                Hash.getHash(sealedAndSigned.encryptedPayloadWithHmac),
                sealedAndSigned.signature);
        if (!isValid)
            throw new CryptoException("Signature verification failed.");

        Serializable decryptedPayload = Utilities.deserialize(decryptPayloadWithHmac(sealedAndSigned.encryptedPayloadWithHmac, secretKey));
        return new DecryptedPayloadWithPubKey(decryptedPayload, sealedAndSigned.sigPublicKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static SecretKey getSecretKeyFromBytes(byte[] encodedKey) {
        return new SecretKeySpec(encodedKey, SYM_KEY_ALGO);
    }

    private static SecretKey generateSecretKey() {
        try {
            KeyGenerator keyPairGenerator = KeyGenerator.getInstance(SYM_CIPHER, "BC");
            keyPairGenerator.init(256);
            return keyPairGenerator.generateKey();
        } catch (Throwable e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new RuntimeException("Couldn't generate key");
        }
    }
}

