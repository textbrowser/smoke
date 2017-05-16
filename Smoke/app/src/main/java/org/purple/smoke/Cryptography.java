/*
** Copyright (c) Alexis Megas.
** All rights reserved.
**
** Redistribution and use in source and binary forms, with or without
** modification, are permitted provided that the following conditions
** are met:
** 1. Redistributions of source code must retain the above copyright
**    notice, this list of conditions and the following disclaimer.
** 2. Redistributions in binary form must reproduce the above copyright
**    notice, this list of conditions and the following disclaimer in the
**    documentation and/or other materials provided with the distribution.
** 3. The name of the author may not be used to endorse or promote products
**    derived from Smoke without specific prior written permission.
**
** SMOKE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
** IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
** OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
** IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
** INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
** NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
** DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
** THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
** (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
** SMOKE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.purple.smoke;

import android.util.Base64;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Cryptography
{
    private KeyPair m_chatEncryptionPublicKeyPair = null;
    private KeyPair m_chatSignaturePublicKeyPair = null;
    private SecretKey m_encryptionKey = null;
    private SecretKey m_macKey = null;
    private String m_sipHashId = "00:00:00:00:00:00:00:00";
    private byte m_identity[] = null;
    private byte m_sipHashEncryptionKey[] = null;
    private byte m_sipHashIdDigest[] = null;
    private byte m_sipHashMacKey[] = null;
    private final Object m_chatEncryptionPublicKeyPairMutex = new Object();
    private final Object m_chatSignaturePublicKeyPairMutex = new Object();
    private final Object m_encryptionKeyMutex = new Object();
    private final Object m_identityMutex = new Object();
    private final Object m_macKeyMutex = new Object();
    private final Object m_sipHashEncryptionKeyMutex = new Object();
    private final Object m_sipHashIdDigestMutex = new Object();
    private final Object m_sipHashIdMutex = new Object();
    private final Object m_sipHashMacKeyMutex = new Object();
    private final static String HASH_ALGORITHM = "SHA-512";
    private final static String HMAC_ALGORITHM = "HmacSHA512";
    private final static String PKI_ECDSA_SIGNATURE_ALGORITHM =
	"SHA512withECDSA";
    private final static String PKI_RSA_ENCRYPTION_ALGORITHM =
	"RSA/NONE/OAEPwithSHA-512andMGF1Padding";
    private final static String PKI_RSA_SIGNATURE_ALGORITHM =
	/*
	** SHA512withRSA/PSS requires API 23+.
	*/

	"SHA512withRSA";
    private final static String SYMMETRIC_ALGORITHM = "AES";
    private final static String SYMMETRIC_CIPHER_TRANSFORMATION =
	"AES/CBC/PKCS7Padding";
    private final static int SIPHASH_STREAM_CREATION_ITERATION_COUNT = 1000;
    private static Cryptography s_instance = null;
    private static SecureRandom s_secureRandom = null;

    private Cryptography()
    {
    }

    private static synchronized void prepareSecureRandom()
    {
	if(s_secureRandom != null)
	    return;

	try
	{
	    /*
	    ** Thread-safe?
	    */

	    s_secureRandom = SecureRandom.getInstance("SHA1PRNG");
	}
	catch(Exception exception)
	{
	    s_secureRandom = new SecureRandom(); // Thread-safe?
	}
    }

    public KeyPair chatEncryptionKeyPair()
    {
	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    return m_chatEncryptionPublicKeyPair;
	}
    }

    public KeyPair chatSignatureKeyPair()
    {
	synchronized(m_chatSignaturePublicKeyPairMutex)
	{
	    return m_chatSignaturePublicKeyPair;
	}
    }

    public PublicKey chatEncryptionPublicKey()
    {
	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    if(m_chatEncryptionPublicKeyPair != null)
		return m_chatEncryptionPublicKeyPair.getPublic();
	    else
		return null;
	}
    }

    public PublicKey chatSignaturePublicKey()
    {
	synchronized(m_chatSignaturePublicKeyPairMutex)
	{
	    if(m_chatSignaturePublicKeyPair != null)
		return m_chatSignaturePublicKeyPair.getPublic();
	    else
		return null;
	}
    }

    public String fancyKeyInformationOutput(KeyPair keyPair)
    {
	if(keyPair == null || keyPair.getPublic() == null)
	    return "";

	PublicKey publicKey = keyPair.getPublic();
	String algorithm = publicKey.getAlgorithm();
	StringBuilder StringBuilder = new StringBuilder();

	StringBuilder.append("Algorithm: ");
	StringBuilder.append(algorithm);
	StringBuilder.append("\n");
	StringBuilder.append("Fingerprint: ");
	StringBuilder.append(publicKeyFingerPrint(publicKey));
	StringBuilder.append("\n");
	StringBuilder.append("Format: ");
	StringBuilder.append(publicKey.getFormat());

	if(algorithm.equals("EC") || algorithm.equals("RSA"))
	    try
	    {
		if(algorithm.equals("EC"))
		{
		    ECPublicKey ecPublicKey = (ECPublicKey) publicKey;

		    if(ecPublicKey != null)
			StringBuilder.append("\n").append("Size: ").
			    append(ecPublicKey.getW().getAffineX().
				   bitLength());
		}
		else if(algorithm.equals("RSA"))
		{
		    RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

		    if(rsaPublicKey != null)
			StringBuilder.append("\n").append("Size: ").
			    append(rsaPublicKey.getModulus().bitLength());
		}
	    }
	    catch(Exception exception)
	    {
	    }

	return StringBuilder.toString();
    }

    public String sipHashId()
    {
	synchronized(m_sipHashIdMutex)
	{
	    return m_sipHashId;
	}
    }

    public boolean compareChatEncryptionPublicKey(PublicKey key)
    {
	if(key == null)
	    return false;

	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    if(key.equals(m_chatEncryptionPublicKeyPair.getPublic()))
		return true;
	    else if(key.hashCode() ==
		    m_chatEncryptionPublicKeyPair.getPublic().hashCode())
	    return true;
	}

	return false;
    }

    public boolean compareChatSignaturePublicKey(PublicKey key)
    {
	if(key == null)
	    return false;

	synchronized(m_chatSignaturePublicKeyPairMutex)
	{
	    if(key.equals(m_chatSignaturePublicKeyPair.getPublic()))
		return true;
	    else if(key.hashCode() ==
		    m_chatSignaturePublicKeyPair.getPublic().hashCode())
		return true;
	}

	return false;
    }

    public boolean isValidSipHashMac(byte data[], byte mac[])
    {
	if(data == null || data.length < 0 || mac == null || mac.length < 0)
	    return false;

	byte bytes[] = null;

	synchronized(m_sipHashMacKeyMutex)
	{
	    bytes = hmac(data, m_sipHashMacKey);
	}

	return memcmp(bytes, mac);
    }

    public boolean iAmTheDestination(byte data[], byte mac[])
    {
	if(data == null || data.length < 0 || mac == null || mac.length < 0)
	    return false;

	synchronized(m_sipHashIdDigestMutex)
	{
	    return memcmp(hmac(data, m_sipHashIdDigest), mac);
	}
    }

    public boolean prepareSipHashKeys()
    {
	try
	{
	    byte bytes[] = null;
	    byte salt[] = null;
	    byte temporary[] = null;

	    synchronized(m_sipHashIdMutex)
	    {
		salt = sha512(m_sipHashId.trim().getBytes());
		temporary = pbkdf2(salt,
				   m_sipHashId.toCharArray(),
				   SIPHASH_STREAM_CREATION_ITERATION_COUNT,
				   768); // 8 * (32 + 64) Bits
	    }

	    if(temporary != null)
		bytes = pbkdf2(salt,
			       new String(temporary).toCharArray(),
			       SIPHASH_STREAM_CREATION_ITERATION_COUNT,
			       768); // 8 * (32 + 64) Bits

	    if(bytes != null)
	    {
		synchronized(m_sipHashEncryptionKeyMutex)
		{
		    m_sipHashEncryptionKey = Arrays.copyOfRange(bytes, 0, 32);
		}

		synchronized(m_sipHashMacKeyMutex)
		{
		    m_sipHashMacKey = Arrays.copyOfRange(bytes, 32, 96);
		}
	    }
	    else
		return false;
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public byte[] chatEncryptionPublicKeyDigest()
    {
	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    if(m_chatEncryptionPublicKeyPair == null ||
	       m_chatEncryptionPublicKeyPair.getPublic() == null)
		return null;

	    return sha512(m_chatEncryptionPublicKeyPair.getPublic().
			  getEncoded());
	}
    }

    public byte[] decryptWithSipHashKey(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	byte bytes[] = null;

	try
	{
	    synchronized(m_sipHashEncryptionKeyMutex)
	    {
		if(m_sipHashEncryptionKey == null)
		    return null;

		Cipher cipher = null;
		SecretKey secretKey = new SecretKeySpec
		    (m_sipHashEncryptionKey, SYMMETRIC_ALGORITHM);
		byte iv[] = Arrays.copyOf(data, 16);

		cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
		cipher.init(Cipher.DECRYPT_MODE,
			    secretKey,
			    new IvParameterSpec(iv));
		bytes = cipher.doFinal
		    (Arrays.copyOfRange(data, 16, data.length));
	    }
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] etm(byte data[]) // Encrypt-Then-MAC
    {
	/*
	** Encrypt-then-MAC.
	*/

	if(data == null || data.length < 0)
	    return null;

	synchronized(m_encryptionKeyMutex)
	{
	    if(m_encryptionKey == null)
		return null;
	}

	synchronized(m_macKeyMutex)
	{
	    if(m_macKey == null)
		return null;
	}

	prepareSecureRandom();

	byte bytes[] = null;

	try
	{
	    synchronized(m_encryptionKeyMutex)
	    {
		if(m_encryptionKey == null)
		    return null;

		Cipher cipher = null;
		byte iv[] = new byte[16];

		cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
		s_secureRandom.nextBytes(iv);
		cipher.init(Cipher.ENCRYPT_MODE,
			    m_encryptionKey,
			    new IvParameterSpec(iv));
		bytes = cipher.doFinal(data);
		bytes = Miscellaneous.joinByteArrays(iv, bytes);
	    }

	    synchronized(m_macKeyMutex)
	    {
		if(m_macKey == null)
		    return null;

		Mac mac = null;

		mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(m_macKey);
		bytes = Miscellaneous.joinByteArrays(bytes, mac.doFinal(bytes));
	    }
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] hmac(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	synchronized(m_macKeyMutex)
	{
	    if(m_macKey == null)
		return null;

	    byte bytes[] = null;

	    try
	    {
		Mac mac = null;

		mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(m_macKey);
		bytes = mac.doFinal(data);
	    }
	    catch(Exception exception)
	    {
		bytes = null;
	    }

	    return bytes;
	}
    }

    public byte[] identity()
    {
	prepareSecureRandom();

	synchronized(m_identityMutex)
	{
	    if(m_identity == null)
	    {
		m_identity = new byte[8];
		s_secureRandom.nextBytes(m_identity);
	    }

	    return m_identity;
	}
    }

    public byte[] mtd(byte data[]) // MAC-Then-Decrypt
    {
	/*
	** MAC-then-decrypt.
	*/

	if(data == null || data.length < 0)
	    return null;

	synchronized(m_encryptionKeyMutex)
	{
	    if(m_encryptionKey == null)
		return null;
	}

	synchronized(m_macKeyMutex)
	{
	    if(m_macKey == null)
		return null;
	}

	try
	{
	    /*
	    ** Verify the computed digest with the provided digest.
	    */

	    byte digest1[] = null; // Provided Digest
	    byte digest2[] = null; // Computed Digest

	    digest1 = Arrays.copyOfRange
		(data, data.length - 512 / 8, data.length);

	    synchronized(m_macKeyMutex)
	    {
		if(m_macKey == null)
		    return null;

		Mac mac = null;

		mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(m_macKey);
		digest2 = mac.doFinal
		    (Arrays.copyOf(data, data.length - 512 / 8));
	    }

	    if(!memcmp(digest1, digest2))
		return null;
	}
	catch(Exception exception)
	{
	    return null;
	}

	byte bytes[] = null;

	try
	{
	    synchronized(m_encryptionKeyMutex)
	    {
		if(m_encryptionKey == null)
		    return null;

		Cipher cipher = null;
		byte iv[] = Arrays.copyOf(data, 16);

		cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
		cipher.init(Cipher.DECRYPT_MODE,
			    m_encryptionKey,
			    new IvParameterSpec(iv));
		bytes = cipher.doFinal
		    (Arrays.copyOfRange(data, 16, data.length - 512 / 8));
	    }
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] pkiDecrypt(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    byte bytes[] = null;

	    try
	    {
		Cipher cipher = null;

		cipher = Cipher.getInstance(PKI_RSA_ENCRYPTION_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE,
			    m_chatEncryptionPublicKeyPair.getPrivate());
		bytes = cipher.doFinal(data);
	    }
	    catch(Exception exception)
	    {
		bytes = null;
	    }

	    return bytes;
	}
    }

    public byte[] signViaChatEncryption(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    if(m_chatEncryptionPublicKeyPair == null ||
	       m_chatEncryptionPublicKeyPair.getPrivate() == null)
		return null;

	    Signature signature = null;
	    byte bytes[] = null;

	    try
	    {
		if(m_chatEncryptionPublicKeyPair.getPrivate().getAlgorithm().
		   equals("EC"))
		    signature = Signature.getInstance
			(PKI_ECDSA_SIGNATURE_ALGORITHM);
		else
		    signature = Signature.getInstance
			(PKI_RSA_SIGNATURE_ALGORITHM);

		signature.initSign(m_chatEncryptionPublicKeyPair.getPrivate());
		signature.update(data);
		bytes = signature.sign();
	    }
	    catch(Exception exception)
	    {
		bytes = null;
	    }

	    return bytes;
	}
    }

    public byte[] signViaChatSignature(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	synchronized(m_chatSignaturePublicKeyPairMutex)
	{
	    if(m_chatSignaturePublicKeyPair == null ||
	       m_chatSignaturePublicKeyPair.getPrivate() == null)
		return null;

	    Signature signature = null;
	    byte bytes[] = null;

	    try
	    {
		if(m_chatSignaturePublicKeyPair.getPrivate().getAlgorithm().
		   equals("EC"))
		    signature = Signature.getInstance
			(PKI_ECDSA_SIGNATURE_ALGORITHM);
		else
		    signature = Signature.getInstance
			(PKI_RSA_SIGNATURE_ALGORITHM);

		signature.initSign(m_chatSignaturePublicKeyPair.getPrivate());
		signature.update(data);
		bytes = signature.sign();
	    }
	    catch(Exception exception)
	    {
		bytes = null;
	    }

	    return bytes;
	}
    }

    public static KeyPair generatePrivatePublicKeyPair(String algorithm,
						       int keySize)
	throws NoSuchAlgorithmException
    {
	prepareSecureRandom();

	KeyPairGenerator keyPairGenerator = KeyPairGenerator.
	    getInstance(algorithm);

	keyPairGenerator.initialize(keySize, s_secureRandom);
	return keyPairGenerator.generateKeyPair();
    }

    public static KeyPair generatePrivatePublicKeyPair(String algorithm,
						       byte privateBytes[],
						       byte publicBytes[])
    {
	try
	{
	    EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec
		(privateBytes);
	    EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicBytes);
	    KeyFactory generator = KeyFactory.getInstance(algorithm);
	    PrivateKey privateKey = generator.generatePrivate(privateKeySpec);
	    PublicKey publicKey = generator.generatePublic(publicKeySpec);

	    return new KeyPair(publicKey, privateKey);
	}
	catch(Exception exception)
	{
	    Database.getInstance().writeLog
		("Cryptography::generatePrivatePublicKeyPair(): " +
		 "exception raised.");
	}

	return null;
    }

    public static PublicKey publicKeyFromBytes(byte publicBytes[])
    {
	try
	{
	    EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicBytes);

	    for(int i = 0; i < 2; i++)
		try
		{
		    KeyFactory generator = null;

		    if(i == 0)
			generator = KeyFactory.getInstance("EC");
		    else
			generator = KeyFactory.getInstance("RSA");

		    return generator.generatePublic(publicKeySpec);
		}
		catch(Exception exception)
		{
		}
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static SecretKey generateEncryptionKey(byte salt[],
						  char password[],
						  int iterations)
	throws InvalidKeySpecException, NoSuchAlgorithmException
    {
	final int length = 256; // Bits.

	KeySpec keySpec = new PBEKeySpec(password, salt, iterations, length);
	SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
	    ("PBKDF2WithHmacSHA1");

	return secretKeyFactory.generateSecret(keySpec);
    }

    public static SecretKey generateMacKey(byte salt[],
					   char password[],
					   int iterations)
	throws InvalidKeySpecException, NoSuchAlgorithmException
    {
	final int length = 512; // Bits.

	KeySpec keySpec = new PBEKeySpec(password, salt, iterations, length);
	SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
	    ("PBKDF2WithHmacSHA1");

	return secretKeyFactory.generateSecret(keySpec);
    }

    public static String fingerPrint(byte bytes[])
    {
	String fingerprint =
	    "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc" +
	    "83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd4" +
	    "7417a81a538327af927da3e";
	StringBuilder StringBuilder = new StringBuilder();

	if(bytes != null)
	{
	    bytes = sha512(bytes);

	    if(bytes != null)
		fingerprint = Miscellaneous.byteArrayAsHexString(bytes);
	}

	try
	{
	    for(int i = 0; i < fingerprint.length(); i += 2)
		if(i < fingerprint.length() - 2)
		    StringBuilder.append(fingerprint.substring(i, i + 2)).
			append(":");
		else
		    StringBuilder.append(fingerprint.substring(i, i + 2));
	}
	catch(Exception exception)
	{
	}

	return StringBuilder.toString();
    }

    public static String publicKeyFingerPrint(PublicKey publicKey)
    {
	if(publicKey == null)
	    return fingerPrint(null);
	else
	    return fingerPrint(publicKey.getEncoded());
    }

    public static String randomBytesAsBase64(int length)
    {
	prepareSecureRandom();

	try
	{
	    byte bytes[] = new byte[length];

	    s_secureRandom.nextBytes(bytes);
	    return Base64.encodeToString(bytes, Base64.DEFAULT);
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public static boolean memcmp(byte a[], byte b[])
    {
	if(a == null || a.length < 0 || b == null || b.length < 0)
	    return false;

	int rc = 0;
	int size = java.lang.Math.max(a.length, b.length);

	for(int i = 0; i < size; i++)
	    rc |= (i < a.length ? a[i] : 0) ^ (i < b.length ? b[i] : 0);

	return rc == 0;
    }

    public static boolean verifySignature(PublicKey publicKey,
					  byte bytes[],
					  byte data[])
    {
	if(bytes == null ||
	   bytes.length < 0 ||
	   data == null ||
	   data.length < 0 ||
	   publicKey == null)
	    return false;

	Signature signature = null;
	boolean ok = false;

	try
	{
	    if(publicKey.getAlgorithm().equals("EC"))
		signature = Signature.getInstance
		    (PKI_ECDSA_SIGNATURE_ALGORITHM);
	    else
		signature = Signature.getInstance(PKI_RSA_SIGNATURE_ALGORITHM);

	    signature.initVerify(publicKey);
	    signature.update(data);
	    ok = signature.verify(bytes);
	}
	catch(Exception exception)
	{
	    return false;
	}

	return ok;
    }

    public static byte[] aes128KeyBytes()
    {
	try
	{
	    KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");

	    keyGenerator.init(128);
	    return keyGenerator.generateKey().getEncoded();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static byte[] aes256KeyBytes()
    {
	try
	{
	    KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");

	    keyGenerator.init(256);
	    return keyGenerator.generateKey().getEncoded();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static byte[] decrypt(byte data[], byte keyBytes[])
    {
	if(data == null ||
	   data.length < 0 ||
	   keyBytes == null ||
	   keyBytes.length < 0)
	    return null;

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    SecretKey secretKey = new SecretKeySpec
		(keyBytes, SYMMETRIC_ALGORITHM);
	    byte iv[] = Arrays.copyOf(data, 16);

	    cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
	    cipher.init(Cipher.DECRYPT_MODE,
			secretKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal
		(Arrays.copyOfRange(data, 16, data.length));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] encrypt(byte data[], byte keyBytes[])
    {
	if(data == null ||
	   data.length < 0 ||
	   keyBytes == null ||
	   keyBytes.length < 0)
	    return null;

	prepareSecureRandom();

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    SecretKey secretKey = new SecretKeySpec
		(keyBytes, SYMMETRIC_ALGORITHM);
	    byte iv[] = new byte[16];

	    cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
	    s_secureRandom.nextBytes(iv);
	    cipher.init(Cipher.ENCRYPT_MODE,
			secretKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal(data);
	    bytes = Miscellaneous.joinByteArrays(iv, bytes);
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] hmac(byte data[], byte keyBytes[])
    {
	if(data == null ||
	   data.length < 0 ||
	   keyBytes == null ||
	   keyBytes.length < 0)
	    return null;

	byte bytes[] = null;

	try
	{
	    Mac mac = null;
	    SecretKey key = new SecretKeySpec(keyBytes, HASH_ALGORITHM);

	    mac = Mac.getInstance(HMAC_ALGORITHM);
	    mac.init(key);
	    bytes = mac.doFinal(data);
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] md5(byte[] ... data)
    {
	byte bytes[] = null;

	try
	{
	    /*
	    ** Thread-safe.
	    */

	    MessageDigest messageDigest = MessageDigest.getInstance("MD5");

	    for(byte b[] : data)
		if(b != null)
		    messageDigest.update(b);

	    bytes = messageDigest.digest();
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] pbkdf2(byte salt[],
				char password[],
				int iterations,
				int length)
    {
	try
	{
	    KeySpec keySpec = new PBEKeySpec
		(password, salt, iterations, length);
	    SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
		("PBKDF2WithHmacSHA1");

	    return secretKeyFactory.generateSecret(keySpec).getEncoded();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static byte[] pkiEncrypt(PublicKey publicKey, byte data[])
    {
	if(data == null || data.length < 0 || publicKey == null)
	    return null;

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;

	    cipher = Cipher.getInstance(PKI_RSA_ENCRYPTION_ALGORITHM);
	    cipher.init(Cipher.ENCRYPT_MODE, publicKey);
	    bytes = cipher.doFinal(data);
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] randomBytes(int length)
    {
	prepareSecureRandom();

	byte bytes[] = null;

	try
	{
	    bytes = new byte[length];
	    s_secureRandom.nextBytes(bytes);
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] sha512(byte[] ... data)
    {
	byte bytes[] = null;

	try
	{
	    /*
	    ** Thread-safe.
	    */

	    MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");

	    for(byte b[] : data)
		if(b != null)
		    messageDigest.update(b);

	    bytes = messageDigest.digest();
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] sha256KeyBytes()
    {
	try
	{
	    KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");

	    keyGenerator.init(256);
	    return keyGenerator.generateKey().getEncoded();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static byte[] sha512KeyBytes()
    {
	try
	{
	    KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA512");

	    keyGenerator.init(512);
	    return keyGenerator.generateKey().getEncoded();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static synchronized Cryptography getInstance()
    {
	if(s_instance == null)
	    s_instance = new Cryptography();

	return s_instance;
    }

    public boolean prepareSipHashIds()
    {
	try
	{
	    byte bytes[] = Miscellaneous.joinByteArrays
		(chatEncryptionKeyPair().getPublic().getEncoded(),
		 chatSignatureKeyPair().getPublic().getEncoded());

	    if(bytes != null)
	    {
		byte key[] = md5(bytes); /*
					 ** Use the MD-5 digest of the
					 ** public keys as the input key to
					 ** SipHash.
					 */

		if(key == null || key.length < 0)
		    return false;

		SipHash sipHash = new SipHash();
		long value = sipHash.hmac(bytes, key);

		if(value == 0)
		    return false;

		bytes = Miscellaneous.longToByteArray(value);

		if(bytes == null || bytes.length < 0)
		    return false;

		synchronized(m_sipHashIdDigestMutex)
		{
		    m_sipHashIdDigest = Miscellaneous.deepCopy
			(sha512(Miscellaneous.
				byteArrayAsHexStringDelimited(bytes, ':', 2).
				getBytes()));
		}

		synchronized(m_sipHashIdMutex)
		{
		    m_sipHashId = Miscellaneous.
			byteArrayAsHexStringDelimited(bytes, ':', 2);
		}
	    }
	    else
		return false;
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public void reset()
    {
	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    m_chatEncryptionPublicKeyPair = null;
	}

	synchronized(m_chatSignaturePublicKeyPairMutex)
	{
	    m_chatSignaturePublicKeyPair = null;
	}

	synchronized(m_encryptionKeyMutex)
	{
	    m_encryptionKey = null;
	}

	synchronized(m_identityMutex)
	{
	    m_identity = null;
	}

	synchronized(m_macKeyMutex)
	{
	    m_macKey = null;
	}

	synchronized(m_sipHashEncryptionKeyMutex)
	{
	    if(m_sipHashEncryptionKey != null)
		Arrays.fill(m_sipHashEncryptionKey, (byte) 0);

	    m_sipHashEncryptionKey = null;
	}

	synchronized(m_sipHashMacKeyMutex)
	{
	    if(m_sipHashMacKey != null)
		Arrays.fill(m_sipHashMacKey, (byte) 0);

	    m_sipHashMacKey = null;
	}
    }

    public void setChatEncryptionPublicKeyPair(KeyPair keyPair)
    {
	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    m_chatEncryptionPublicKeyPair = keyPair;
	}
    }

    public void setChatEncryptionPublicKeyPair(String algorithm,
					       byte privateBytes[],
					       byte publicBytes[])
    {
	synchronized(m_chatEncryptionPublicKeyPairMutex)
	{
	    m_chatEncryptionPublicKeyPair = generatePrivatePublicKeyPair
		(algorithm, privateBytes, publicBytes);
	}
    }

    public void setChatSignaturePublicKeyPair(KeyPair keyPair)
    {
	synchronized(m_chatSignaturePublicKeyPairMutex)
	{
	    m_chatSignaturePublicKeyPair = keyPair;
	}
    }

    public void setChatSignaturePublicKeyPair(String algorithm,
					      byte privateBytes[],
					      byte publicBytes[])
    {
	synchronized(m_chatSignaturePublicKeyPairMutex)
	{
	    m_chatSignaturePublicKeyPair = generatePrivatePublicKeyPair
		(algorithm, privateBytes, publicBytes);
	}
    }

    public void setEncryptionKey(SecretKey key)
    {
	synchronized(m_encryptionKeyMutex)
	{
	    m_encryptionKey = key;
	}
    }

    public void setIdentity(byte identity[])
    {
	synchronized(m_identityMutex)
	{
	    m_identity = Miscellaneous.deepCopy(identity);
	}
    }

    public void setMacKey(SecretKey key)
    {
	synchronized(m_macKeyMutex)
	{
	    m_macKey = key;
	}
    }
}
