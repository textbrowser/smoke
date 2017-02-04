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
import java.security.interfaces.DSAPublicKey;
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
    private final static String HASH_ALGORITHM = "SHA-512";
    private final static String HMAC_ALGORITHM = "HmacSHA512";
    private final static String SYMMETRIC_ALGORITHM = "AES";
    private final static String SYMMETRIC_CIPHER_TRANSFORMATION =
	"AES/CBC/PKCS5Padding";
    private static Cryptography s_instance = null;
    private static SecureRandom s_secureRandom = null;

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
	return m_chatEncryptionKeyPair;
    }

    public KeyPair chatSignatureKeyPair()
    {
	return m_chatSignatureKeyPair;
    }

    public String fancyOutput(KeyPair keyPair)
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

	if(algorithm == "DSA" || algorithm == "RSA")
	    try
	    {
		KeyFactory keyFactory = KeyFactory.getInstance(algorithm);

		if(algorithm == "DSA")
		{
		    DSAPublicKey dsaPublicKey = (DSAPublicKey) publicKey;

		    if(dsaPublicKey != null)
			stringBuffer.append("\n").append("Size: ").
			    append(dsaPublicKey.getY().bitLength());
		}
		else if(algorithm == "RSA")
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

    public byte[] etm(byte data[])
    {
	/*
	** Encrypt-then-MAC.
	*/

	if(data == null || m_encryptionKey == null || m_macKey == null)
	    return null;

	prepareSecureRandom();

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    Mac mac = null;
	    byte iv[] = new byte[16];

	    cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
	    s_secureRandom.nextBytes(iv);
	    cipher.init(Cipher.ENCRYPT_MODE,
			m_encryptionKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal(data);
	    mac = Mac.getInstance(HMAC_ALGORITHM);
	    mac.init(m_macKey);
	    bytes = Miscellaneous.joinByteArrays(iv, bytes);
	    bytes = Miscellaneous.joinByteArrays(bytes, mac.doFinal(bytes));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] hmac(byte data[])
    {
	if(data == null || m_macKey == null)
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

    public byte[] mtd(byte data[])
    {
	/*
	** MAC-then-decrypt.
	*/

	if(data == null || m_encryptionKey == null || m_macKey == null)
	    return null;

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
	    mac = Mac.getInstance(HMAC_ALGORITHM);
	    mac.init(m_macKey);
	    digest2 = mac.doFinal(Arrays.copyOf(data, data.length - 512 / 8));

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
	    byte iv[] = Arrays.copyOf(data, 16);

	    cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
	    cipher.init(Cipher.DECRYPT_MODE,
			m_encryptionKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal
		(Arrays.copyOfRange(data, 16, data.length - 512 / 8));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] signViaChat(byte data[])
    {
	if(data == null || m_chatSignatureKeyPair == null)
	    return null;

	byte bytes[] = null;

	return bytes;
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

    public void reset()
    {
	m_chatEncryptionKeyPair = null;
	m_chatSignatureKeyPair = null;
	m_encryptionKey = null;
	m_macKey = null;
    }

    public void setChatEncryptionKeyPair(KeyPair keyPair)
    {
	m_chatEncryptionKeyPair = keyPair;
    }

    public void setChatEncryptionKeyPair(String algorithm,
					 byte privateBytes[],
					 byte publicBytes[])
    {
	m_chatEncryptionKeyPair = generatePrivatePublicKeyPair
	    (algorithm, privateBytes, publicBytes);
    }

    public void setChatSignatureKeyPair(KeyPair keyPair)
    {
	m_chatSignatureKeyPair = keyPair;
    }

    public void setChatSignatureKeyPair(String algorithm,
					byte privateBytes[],
					byte publicBytes[])
    {
	m_chatSignatureKeyPair = generatePrivatePublicKeyPair
	    (algorithm, privateBytes, publicBytes);
    }

    public void setEncryptionKey(SecretKey key)
    {
	m_encryptionKey = key;
    }

    public void setMacKey(SecretKey key)
    {
	m_macKey = key;
    }
}
