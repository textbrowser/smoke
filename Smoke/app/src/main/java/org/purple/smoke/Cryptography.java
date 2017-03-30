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
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Cryptography
{
    private KeyPair m_chatEncryptionKeyPair = null;
    private KeyPair m_chatSignatureKeyPair = null;
    private SecretKey m_encryptionKey = null;
    private SecretKey m_macKey = null;
    private String m_sipHashId = "00:00:00:00:00:00:00:00";
    private byte m_sipHashEncryptionKey[] = null;
    private byte m_sipHashMacKey[] = null;
    private final Object m_chatEncryptionKeyPairMutex = new Object();
    private final Object m_chatSignatureKeyPairMutex = new Object();
    private final Object m_encryptionKeyMutex = new Object();
    private final Object m_macKeyMutex = new Object();
    private final Object m_sipHashEncryptionKeyMutex = new Object();
    private final Object m_sipHashMacKeyMutex = new Object();
    private final static String HASH_ALGORITHM = "SHA-512";
    private final static String HMAC_ALGORITHM = "HmacSHA512";
    private final static String PKI_ECDSA_SIGNATURE_ALGORITHM =
	"SHA512withECDSA";
    private final static String PKI_RSA_SIGNATURE_ALGORITHM =
	"SHA512withRSA/PSS";
    private final static String SYMMETRIC_ALGORITHM = "AES";
    private final static String SYMMETRIC_CIPHER_TRANSFORMATION =
	"AES/CBC/PKCS5Padding";
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
	synchronized(m_chatEncryptionKeyPairMutex)
	{
	    return m_chatEncryptionKeyPair;
	}
    }

    public KeyPair chatSignatureKeyPair()
    {
	synchronized(m_chatSignatureKeyPairMutex)
	{
	    return m_chatSignatureKeyPair;
	}
    }

    public PublicKey chatEncryptionPublicKey()
    {
	synchronized(m_chatEncryptionKeyPair)
	{
	    if(m_chatEncryptionKeyPair != null)
		return m_chatEncryptionKeyPair.getPublic();
	    else
		return null;
	}
    }

    public PublicKey chatSignaturePublicKey()
    {
	synchronized(m_chatSignatureKeyPair)
	{
	    if(m_chatSignatureKeyPair != null)
		return m_chatSignatureKeyPair.getPublic();
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
	StringBuffer stringBuffer = new StringBuffer();

	stringBuffer.append("Algorithm: ");
	stringBuffer.append(algorithm);
	stringBuffer.append("\n");
	stringBuffer.append("Fingerprint: ");
	stringBuffer.append(publicKeyFingerPrint(publicKey));
	stringBuffer.append("\n");
	stringBuffer.append("Format: ");
	stringBuffer.append(publicKey.getFormat());

	if(algorithm.equals("EC") || algorithm.equals("RSA"))
	    try
	    {
		if(algorithm.equals("EC"))
		{
		    ECPublicKey ecdsaPublicKey = (ECPublicKey) publicKey;

		    if(ecdsaPublicKey != null)
			stringBuffer.append("\n").append("Size: ").
			    append(ecdsaPublicKey.getW().getAffineX().
				   bitLength());
		}
		else if(algorithm.equals("RSA"))
		{
		    RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

		    if(rsaPublicKey != null)
			stringBuffer.append("\n").append("Size: ").
			    append(rsaPublicKey.getModulus().bitLength());
		}
	    }
	    catch(Exception exception)
	    {
	    }

	return stringBuffer.toString();
    }

    public boolean prepareSipHashKeys()
    {
	try
	{
	    byte bytes[] = null;
	    byte salt[] = sha512(m_sipHashId.trim().getBytes());
	    byte temporary[] = pbkdf2(salt,
				      m_sipHashId.toCharArray(),
				      SIPHASH_STREAM_CREATION_ITERATION_COUNT,
				      768); // 8 * (32 + 64) Bits

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

    public byte[] chatEncryptionKeyDigest()
    {
	synchronized(m_chatEncryptionKeyPairMutex)
	{
	    if(m_chatEncryptionKeyPair == null ||
	       m_chatEncryptionKeyPair.getPublic() == null)
		return null;

	    return sha512(m_chatEncryptionKeyPair.getPublic().getEncoded());
	}
    }

    public byte[] etm(byte data[]) // Encrypt-Then-MAC
    {
	/*
	** Encrypt-then-MAC.
	*/

	if(data == null)
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
	    Cipher cipher = null;
	    Mac mac = null;

	    synchronized(m_encryptionKeyMutex)
	    {
		if(m_encryptionKey == null)
		    return null;

		byte iv[] = new byte[16];

		s_secureRandom.nextBytes(iv);
		cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
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
	if(data == null)
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

    public byte[] mtd(byte data[]) // MAC-Then-Decrypt
    {
	/*
	** MAC-then-decrypt.
	*/

	if(data == null)
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

	    Mac mac = null;
	    byte digest1[] = null; // Provided Digest
	    byte digest2[] = null; // Computed Digest

	    digest1 = Arrays.copyOfRange
		(data, data.length - 512 / 8, data.length);

	    synchronized(m_macKeyMutex)
	    {
		if(m_macKey == null)
		    return null;

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
	    Cipher cipher = null;

	    synchronized(m_encryptionKeyMutex)
	    {
		if(m_encryptionKey == null)
		    return null;

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

    public byte[] signViaChatEncryption(byte data[])
    {
	if(data == null)
	    return null;

	synchronized(m_chatEncryptionKeyPairMutex)
	{
	    if(m_chatEncryptionKeyPair == null ||
	       m_chatEncryptionKeyPair.getPrivate() == null)
		return null;

	    Signature signature = null;
	    byte bytes[] = null;

	    try
	    {
		signature = Signature.getInstance(PKI_RSA_SIGNATURE_ALGORITHM);
		signature.initSign(m_chatEncryptionKeyPair.getPrivate());
		signature.update(data);
		bytes = signature.sign();
	    }
	    catch(Exception exception)
	    {
		return null;
	    }

	    return bytes;
	}
    }

    public byte[] signViaChatSignature(byte data[])
    {
	if(data == null)
	    return null;

	synchronized(m_chatSignatureKeyPairMutex)
	{
	    if(m_chatSignatureKeyPair == null ||
	       m_chatSignatureKeyPair.getPrivate() == null)
		return null;

	    Signature signature = null;
	    byte bytes[] = null;

	    try
	    {
		if(m_chatSignatureKeyPair.getPrivate().getAlgorithm().
		   equals("EC"))
		    signature = Signature.getInstance
			(PKI_ECDSA_SIGNATURE_ALGORITHM);
		else
		    signature = Signature.getInstance
			(PKI_RSA_SIGNATURE_ALGORITHM);

		signature.initSign(m_chatSignatureKeyPair.getPrivate());
		signature.update(data);
		bytes = signature.sign();
	    }
	    catch(Exception exception)
	    {
		return null;
	    }

	    return bytes;
	}
    }

    public byte[] sipHashHmacKey()
    {
	synchronized(m_sipHashMacKeyMutex)
	{
	    return m_sipHashMacKey;
	}
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

    public static KeyPair generatePrivatePublicKeyPair(String algorithm,
						       int keySize)
    {
	try
	{
	    prepareSecureRandom();

	    KeyPairGenerator keyPairGenerator = KeyPairGenerator.
		getInstance(algorithm);

	    keyPairGenerator.initialize(keySize, s_secureRandom);
	    return keyPairGenerator.generateKeyPair();
	}
	catch(Exception exception)
	{
	    return null;
	}
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

    public static SecretKey generateEncryptionKey(byte salt[],
						  char password[],
						  int iterations)
    {
	try
	{
	    final int length = 256; // Bits.

	    KeySpec keySpec = new PBEKeySpec
		(password, salt, iterations, length);
	    SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
		("PBKDF2WithHmacSHA1");

	    return secretKeyFactory.generateSecret(keySpec);
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static SecretKey generateMacKey(byte salt[],
					   char password[],
					   int iterations)
    {
	try
	{
	    final int length = 512; // Bits.

	    KeySpec keySpec = new PBEKeySpec
		(password, salt, iterations, length);
	    SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
		("PBKDF2WithHmacSHA1");

	    return secretKeyFactory.generateSecret(keySpec);
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static String publicKeyFingerPrint(PublicKey publicKey)
    {
	String fingerprint =
	    "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc" +
	    "83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd4" +
	    "7417a81a538327af927da3e";
	StringBuffer stringBuffer = new StringBuffer();

	if(publicKey != null)
	{
	    byte bytes[] = sha512(publicKey.getEncoded());

	    if(bytes != null)
		fingerprint = Miscellaneous.byteArrayAsHexString(bytes);
	}

	try
	{
	    for(int i = 0; i < fingerprint.length(); i += 2)
		if(i < fingerprint.length() - 2)
		    stringBuffer.append(fingerprint.substring(i, i + 2)).
			append(":");
		else
		    stringBuffer.append(fingerprint.substring(i, i + 2));
	}
	catch(Exception exception)
	{
	}

	return stringBuffer.toString();
    }

    public static String randomBytesAsBase64(int length)
    {
	prepareSecureRandom();

	byte bytes[] = new byte[length];

	s_secureRandom.nextBytes(bytes);
	return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    public static boolean memcmp(byte a[], byte b[])
    {
	if(a == null || b == null)
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
	if(bytes == null || data == null || publicKey == null)
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

    public static byte[] encrypt(byte data[], byte keyBytes[])
    {
	if(data == null || keyBytes == null)
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
	if(data == null || keyBytes == null)
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

    public static byte[] pkiEncrypt(PublicKey publicKey, byte data[])
    {
	if(data == null || publicKey == null)
	    return null;

	byte bytes[] = null;

	return bytes;
    }

    public static byte[] randomBytes(int length)
    {
	prepareSecureRandom();

	byte bytes[] = new byte[length];

	s_secureRandom.nextBytes(bytes);
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

		if(key == null)
		    return false;

		SipHash sipHash = new SipHash();
		long value = sipHash.hmac(bytes, key);

		if(value == 0)
		    return false;

		bytes = Miscellaneous.longToByteArray(value);

		if(bytes == null)
		    return false;

		m_sipHashId = Miscellaneous.
		    byteArrayAsHexStringDelimited(bytes, ':');
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
	synchronized(m_chatEncryptionKeyPairMutex)
	{
	    m_chatEncryptionKeyPair = null;
	}

	synchronized(m_chatSignatureKeyPairMutex)
	{
	    m_chatSignatureKeyPair = null;
	}

	synchronized(m_encryptionKeyMutex)
	{
	    m_encryptionKey = null;
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

    public void setChatEncryptionKeyPair(KeyPair keyPair)
    {
	synchronized(m_chatEncryptionKeyPairMutex)
	{
	    m_chatEncryptionKeyPair = keyPair;
	}
    }

    public void setChatEncryptionKeyPair(String algorithm,
					 byte privateBytes[],
					 byte publicBytes[])
    {
	synchronized(m_chatEncryptionKeyPairMutex)
	{
	    m_chatEncryptionKeyPair = generatePrivatePublicKeyPair
		(algorithm, privateBytes, publicBytes);
	}
    }

    public void setChatSignatureKeyPair(KeyPair keyPair)
    {
	synchronized(m_chatSignatureKeyPairMutex)
	{
	    m_chatSignatureKeyPair = keyPair;
	}
    }

    public void setChatSignatureKeyPair(String algorithm,
					byte privateBytes[],
					byte publicBytes[])
    {
	synchronized(m_chatSignatureKeyPairMutex)
	{
	    m_chatSignatureKeyPair = generatePrivatePublicKeyPair
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

    public void setMacKey(SecretKey key)
    {
	synchronized(m_macKeyMutex)
	{
	    m_macKey = key;
	}
    }
}
