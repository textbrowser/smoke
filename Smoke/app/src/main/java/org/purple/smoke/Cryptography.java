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
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private byte m_ozoneEncryptionKey[] = null;
    private byte m_ozoneMacKey[] = null;
    private byte m_sipHashEncryptionKey[] = null;
    private byte m_sipHashIdDigest[] = null;
    private byte m_sipHashMacKey[] = null;
    private final ReentrantReadWriteLock m_chatEncryptionPublicKeyPairMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_chatSignaturePublicKeyPairMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_encryptionKeyMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_identityMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_macKeyMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_ozoneEncryptionKeyMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_ozoneMacKeyMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_sipHashEncryptionKeyMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_sipHashIdDigestMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_sipHashIdMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_sipHashMacKeyMutex =
	new ReentrantReadWriteLock();
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
    private final static int SIPHASH_STREAM_CREATION_ITERATION_COUNT = 4096;
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
	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
	{
	    return m_chatEncryptionPublicKeyPair;
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}
    }

    public KeyPair chatSignatureKeyPair()
    {
	m_chatSignaturePublicKeyPairMutex.readLock().lock();

	try
	{
	    return m_chatSignaturePublicKeyPair;
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.readLock().unlock();
	}
    }

    public PublicKey chatEncryptionPublicKey()
    {
	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
	{
	    if(m_chatEncryptionPublicKeyPair != null)
		return m_chatEncryptionPublicKeyPair.getPublic();
	    else
		return null;
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}
    }

    public PublicKey chatSignaturePublicKey()
    {
	m_chatSignaturePublicKeyPairMutex.readLock().lock();

	try
	{
	    if(m_chatSignaturePublicKeyPair != null)
		return m_chatSignaturePublicKeyPair.getPublic();
	    else
		return null;
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.readLock().unlock();
	}
    }

    public String fancyKeyInformationOutput(KeyPair keyPair)
    {
	if(keyPair == null || keyPair.getPublic() == null)
	    return "";

	PublicKey publicKey = keyPair.getPublic();
	String algorithm = publicKey.getAlgorithm();
	StringBuilder stringBuilder = new StringBuilder();

	stringBuilder.append("Algorithm: ");
	stringBuilder.append(algorithm);
	stringBuilder.append("\n");
	stringBuilder.append("Fingerprint: ");
	stringBuilder.append(publicKeyFingerPrint(publicKey));
	stringBuilder.append("\n");
	stringBuilder.append("Format: ");
	stringBuilder.append(publicKey.getFormat());

	if(algorithm.equals("EC") || algorithm.equals("RSA"))
	    try
	    {
		if(algorithm.equals("EC"))
		{
		    ECPublicKey ecPublicKey = (ECPublicKey) publicKey;

		    if(ecPublicKey != null)
			stringBuilder.append("\n").append("Size: ").
			    append(ecPublicKey.getW().getAffineX().
				   bitLength());
		}
		else if(algorithm.equals("RSA"))
		{
		    RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

		    if(rsaPublicKey != null)
			stringBuilder.append("\n").append("Size: ").
			    append(rsaPublicKey.getModulus().bitLength());
		}
	    }
	    catch(Exception exception)
	    {
	    }

	return stringBuilder.toString();
    }

    public String sipHashId()
    {
	m_sipHashIdMutex.readLock().lock();

	try
	{
	    return m_sipHashId;
	}
	finally
	{
	    m_sipHashIdMutex.readLock().unlock();
	}
    }

    public boolean compareChatEncryptionPublicKey(PublicKey key)
    {
	if(key == null)
	    return false;

	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
	{
	    if(key.equals(m_chatEncryptionPublicKeyPair.getPublic()))
		return true;
	    else if(key.hashCode() ==
		    m_chatEncryptionPublicKeyPair.getPublic().hashCode())
	    return true;
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}

	return false;
    }

    public boolean compareChatSignaturePublicKey(PublicKey key)
    {
	if(key == null)
	    return false;

	m_chatSignaturePublicKeyPairMutex.readLock().lock();

	try
	{
	    if(key.equals(m_chatSignaturePublicKeyPair.getPublic()))
		return true;
	    else if(key.hashCode() ==
		    m_chatSignaturePublicKeyPair.getPublic().hashCode())
		return true;
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.readLock().unlock();
	}

	return false;
    }

    public boolean isValidSipHashMac(byte data[], byte mac[])
    {
	if(data == null || data.length < 0 || mac == null || mac.length < 0)
	    return false;

	m_sipHashMacKeyMutex.readLock().lock();

	try
	{
	    return memcmp(hmac(data, m_sipHashMacKey), mac);
	}
	finally
	{
	    m_sipHashMacKeyMutex.readLock().unlock();
	}
    }

    public boolean iAmTheDestination(byte data[], byte mac[])
    {
	if(data == null || data.length < 0 || mac == null || mac.length < 0)
	    return false;

	m_sipHashIdDigestMutex.readLock().lock();

	try
	{
	    return memcmp(hmac(data, m_sipHashIdDigest), mac);
	}
	finally
	{
	    m_sipHashIdDigestMutex.readLock().unlock();
	}
    }

    public boolean prepareSipHashKeys()
    {
	try
	{
	    byte bytes[] = null;
	    byte salt[] = null;
	    byte temporary[] = null;

	    m_sipHashIdMutex.readLock().lock();

	    try
	    {
		salt = sha512(m_sipHashId.getBytes("UTF-8"));
		temporary = pbkdf2(salt,
				   m_sipHashId.toCharArray(),
				   SIPHASH_STREAM_CREATION_ITERATION_COUNT,
				   160); // SHA-1
	    }
	    finally
	    {
		m_sipHashIdMutex.readLock().unlock();
	    }

	    if(temporary != null)
		bytes = pbkdf2(salt,
			       new String(temporary).toCharArray(),
			       1,
			       768); // 8 * (32 + 64) Bits

	    if(bytes != null)
	    {
		m_sipHashEncryptionKeyMutex.writeLock().lock();

		try
		{
		    m_sipHashEncryptionKey = Arrays.copyOfRange(bytes, 0, 32);
		}
		finally
		{
		    m_sipHashEncryptionKeyMutex.writeLock().unlock();
		}

		m_sipHashMacKeyMutex.writeLock().lock();

		try
		{
		    m_sipHashMacKey = Arrays.copyOfRange(bytes, 32, 96);
		}
		finally
		{
		    m_sipHashMacKeyMutex.writeLock().unlock();
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
	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
	{
	    if(m_chatEncryptionPublicKeyPair == null ||
	       m_chatEncryptionPublicKeyPair.getPublic() == null)
		return null;

	    return sha512
		(m_chatEncryptionPublicKeyPair.getPublic().getEncoded());
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}
    }

    public byte[] decryptWithSipHashKey(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	byte bytes[] = null;

	try
	{
	    m_sipHashEncryptionKeyMutex.readLock().lock();

	    try
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
	    finally
	    {
		m_sipHashEncryptionKeyMutex.readLock().unlock();
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

	m_encryptionKeyMutex.readLock().lock();

	try
	{
	    if(m_encryptionKey == null)
		return null;
	}
	finally
	{
	    m_encryptionKeyMutex.readLock().unlock();
	}

	m_macKeyMutex.readLock().lock();

	try
	{
	    if(m_macKey == null)
		return null;
	}
	finally
	{
	    m_macKeyMutex.readLock().unlock();
	}

	prepareSecureRandom();

	byte bytes[] = null;

	try
	{
	    m_encryptionKeyMutex.readLock().lock();

	    try
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
	    finally
	    {
		m_encryptionKeyMutex.readLock().unlock();
	    }

	    m_macKeyMutex.readLock().lock();

	    try
	    {
		if(m_macKey == null)
		    return null;

		Mac mac = null;

		mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(m_macKey);
		bytes = Miscellaneous.joinByteArrays(bytes, mac.doFinal(bytes));
	    }
	    finally
	    {
		m_macKeyMutex.readLock().unlock();
	    }
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] generateFireKey(String channel,
				  String salt)
    {
	byte c[] = null;

	try
	{
	    c = channel.getBytes("ISO-8859-1"); // Latin-1.
	}
	catch(Exception exception)
	{
	    return null;
	}

	byte s[] = null;

	try
	{
	    channel.getBytes("ISO-8859-1"); // Latin-1.
	}
	catch(Exception exception)
	{
	    return null;
	}

	return null;
    }

    public byte[] hmac(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	m_macKeyMutex.readLock().lock();

	try
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
	finally
	{
	    m_macKeyMutex.readLock().unlock();
	}
    }

    public byte[] identity()
    {
	prepareSecureRandom();
	m_identityMutex.writeLock().lock();

	try
	{
	    if(m_identity == null)
	    {
		m_identity = new byte[8];
		s_secureRandom.nextBytes(m_identity);
	    }

	    return m_identity;
	}
	finally
	{
	    m_identityMutex.writeLock().unlock();
	}
    }

    public byte[] mtd(byte data[]) // MAC-Then-Decrypt
    {
	/*
	** MAC-then-decrypt.
	*/

	if(data == null || data.length < 0)
	    return null;

	m_encryptionKeyMutex.readLock().lock();

	try
	{
	    if(m_encryptionKey == null)
		return null;
	}
	finally
	{
	    m_encryptionKeyMutex.readLock().unlock();
	}

	m_macKeyMutex.readLock().lock();

	try
	{
	    if(m_macKey == null)
		return null;
	}
	finally
	{
	    m_macKeyMutex.readLock().unlock();
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
	    m_macKeyMutex.readLock().lock();

	    try
	    {
		if(m_macKey == null)
		    return null;

		Mac mac = null;

		mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(m_macKey);
		digest2 = mac.doFinal
		    (Arrays.copyOf(data, data.length - 512 / 8));
	    }
	    finally
	    {
		m_macKeyMutex.readLock().unlock();
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
	    m_encryptionKeyMutex.readLock().lock();

	    try
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
	    finally
	    {
		m_encryptionKeyMutex.readLock().unlock();
	    }
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] ozoneEncryptionKey()
    {
	m_ozoneEncryptionKeyMutex.readLock().lock();

	try
	{
	    return m_ozoneEncryptionKey;
	}
	finally
	{
	    m_ozoneEncryptionKeyMutex.readLock().unlock();
	}
    }

    public byte[] ozoneMacKey()
    {
	m_ozoneMacKeyMutex.readLock().lock();

	try
	{
	    return m_ozoneMacKey;
	}
	finally
	{
	    m_ozoneMacKeyMutex.readLock().unlock();
	}
    }

    public byte[] pkiDecrypt(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
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
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}
    }

    public byte[] signViaChatEncryption(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
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
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}
    }

    public byte[] signViaChatSignature(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	m_chatSignaturePublicKeyPairMutex.readLock().lock();

	try
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
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.readLock().unlock();
	}
    }

    public byte[] sipHashEncryptionKey()
    {
	m_sipHashEncryptionKeyMutex.readLock().lock();

	try
	{
	    return m_sipHashEncryptionKey;
	}
	finally
	{
	    m_sipHashEncryptionKeyMutex.readLock().unlock();
	}
    }

    public byte[] sipHashMacKey()
    {
	m_sipHashMacKeyMutex.readLock().lock();

	try
	{
	    return m_sipHashMacKey;
	}
	finally
	{
	    m_sipHashMacKeyMutex.readLock().unlock();
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

    public static PublicKey publicRSAKeyFromBytes(byte publicBytes[])
    {
	try
	{
	    EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicBytes);
	    KeyFactory generator = KeyFactory.getInstance("RSA");

	    return generator.generatePublic(publicKeySpec);
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
	StringBuilder stringBuilder = new StringBuilder();

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
		    stringBuilder.append(fingerprint.substring(i, i + 2)).
			append(":");
		else
		    stringBuilder.append(fingerprint.substring(i, i + 2));
	}
	catch(Exception exception)
	{
	}

	return stringBuilder.toString();
    }

    public static String publicKeyFingerPrint(PublicKey publicKey)
    {
	if(publicKey == null)
	    return fingerPrint(null);
	else
	    return fingerPrint(publicKey.getEncoded());
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

    public static byte[] keyForSipHash(byte data[])
    {
	if(data == null || data.length < 0)
	    return null;

	return pbkdf2(sha512(data),
		      Miscellaneous.byteArrayAsHexString(data).toCharArray(),
		      SIPHASH_STREAM_CREATION_ITERATION_COUNT,
		      8 * SipHash.KEY_LENGTH);
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

    public static byte[] pkiDecrypt(PrivateKey key, byte data[])
    {
	if(data == null || data.length < 0 || key == null)
	    return null;

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;

	    cipher = Cipher.getInstance(PKI_RSA_ENCRYPTION_ALGORITHM);
	    cipher.init(Cipher.DECRYPT_MODE, key);
	    bytes = cipher.doFinal(data);
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
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
		byte key[] = keyForSipHash(bytes);

		if(key == null || key.length < 0)
		    return false;

		SipHash sipHash = new SipHash();
		long value = sipHash.hmac(bytes, key);

		if(value == 0)
		    return false;

		bytes = Miscellaneous.longToByteArray(value);

		if(bytes == null || bytes.length < 0)
		    return false;

		m_sipHashIdDigestMutex.writeLock().lock();

		try
		{
		    m_sipHashIdDigest = Miscellaneous.deepCopy
			(sha512(Miscellaneous.
				byteArrayAsHexStringDelimited(bytes, ':', 2).
				getBytes()));
		}
		finally
		{
		    m_sipHashIdDigestMutex.writeLock().unlock();
		}

		m_sipHashIdMutex.writeLock().lock();

		try
		{
		    m_sipHashId = Miscellaneous.
			byteArrayAsHexStringDelimited(bytes, ':', 2);
		}
		finally
		{
		    m_sipHashIdMutex.writeLock().unlock();
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
	m_chatEncryptionPublicKeyPairMutex.writeLock().lock();

	try
	{
	    m_chatEncryptionPublicKeyPair = null;
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.writeLock().unlock();
	}

	m_chatSignaturePublicKeyPairMutex.writeLock().lock();

	try
	{
	    m_chatSignaturePublicKeyPair = null;
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.writeLock().unlock();
	}

	m_encryptionKeyMutex.writeLock().lock();

	try
	{
	    m_encryptionKey = null;
	}
	finally
	{
	    m_encryptionKeyMutex.writeLock().unlock();
	}

	m_identityMutex.writeLock().lock();

	try
	{
	    m_identity = null;
	}
	finally
	{
	    m_identityMutex.writeLock().unlock();
	}

	m_macKeyMutex.writeLock().lock();

	try
	{
	    m_macKey = null;
	}
	finally
	{
	    m_macKeyMutex.writeLock().unlock();
	}

	m_ozoneEncryptionKeyMutex.writeLock().lock();

	try
	{
	    if(m_ozoneEncryptionKey != null)
		Arrays.fill(m_ozoneEncryptionKey, (byte) 0);

	    m_ozoneEncryptionKey = null;
	}
	finally
	{
	    m_ozoneEncryptionKeyMutex.writeLock().unlock();
	}

	m_ozoneMacKeyMutex.writeLock().lock();

	try
	{
	    if(m_ozoneMacKey != null)
		Arrays.fill(m_ozoneMacKey, (byte) 0);

	    m_ozoneMacKey = null;
	}
	finally
	{
	    m_ozoneMacKeyMutex.writeLock().unlock();
	}

	m_sipHashEncryptionKeyMutex.writeLock().lock();

	try
	{
	    if(m_sipHashEncryptionKey != null)
		Arrays.fill(m_sipHashEncryptionKey, (byte) 0);

	    m_sipHashEncryptionKey = null;
	}
	finally
	{
	    m_sipHashEncryptionKeyMutex.writeLock().unlock();
	}

	m_sipHashIdDigestMutex.writeLock().lock();

	try
	{
	    if(m_sipHashIdDigest != null)
		Arrays.fill(m_sipHashIdDigest, (byte) 0);

	    m_sipHashIdDigest = null;
	}
	finally
	{
	    m_sipHashIdDigestMutex.writeLock().unlock();
	}

	m_sipHashIdMutex.writeLock().lock();

	try
	{
	    m_sipHashId = "00:00:00:00:00:00:00:00";
	}
	finally
	{
	    m_sipHashIdMutex.writeLock().unlock();
	}

	m_sipHashMacKeyMutex.writeLock().lock();

	try
	{
	    if(m_sipHashMacKey != null)
		Arrays.fill(m_sipHashMacKey, (byte) 0);

	    m_sipHashMacKey = null;
	}
	finally
	{
	    m_sipHashMacKeyMutex.writeLock().unlock();
	}
    }

    public void setChatEncryptionPublicKeyPair(KeyPair keyPair)
    {
	m_chatEncryptionPublicKeyPairMutex.writeLock().lock();

	try
	{
	    m_chatEncryptionPublicKeyPair = keyPair;
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.writeLock().unlock();
	}
    }

    public void setChatEncryptionPublicKeyPair(String algorithm,
					       byte privateBytes[],
					       byte publicBytes[])
    {
	m_chatEncryptionPublicKeyPairMutex.writeLock().lock();

	try
	{
	    m_chatEncryptionPublicKeyPair = generatePrivatePublicKeyPair
		(algorithm, privateBytes, publicBytes);
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.writeLock().unlock();
	}
    }

    public void setChatSignaturePublicKeyPair(KeyPair keyPair)
    {
	m_chatSignaturePublicKeyPairMutex.writeLock().lock();

	try
	{
	    m_chatSignaturePublicKeyPair = keyPair;
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.writeLock().unlock();
	}
    }

    public void setChatSignaturePublicKeyPair(String algorithm,
					      byte privateBytes[],
					      byte publicBytes[])
    {
	m_chatSignaturePublicKeyPairMutex.writeLock().lock();

	try
	{
	    m_chatSignaturePublicKeyPair = generatePrivatePublicKeyPair
		(algorithm, privateBytes, publicBytes);
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.writeLock().unlock();
	}
    }

    public void setEncryptionKey(SecretKey key)
    {
	m_encryptionKeyMutex.writeLock().lock();

	try
	{
	    m_encryptionKey = key;
	}
	finally
	{
	    m_encryptionKeyMutex.writeLock().unlock();
	}
    }

    public void setIdentity(byte identity[])
    {
	m_identityMutex.writeLock().lock();

	try
	{
	    m_identity = Miscellaneous.deepCopy(identity);
	}
	finally
	{
	    m_identityMutex.writeLock().unlock();
	}
    }

    public void setMacKey(SecretKey key)
    {
	m_macKeyMutex.writeLock().lock();

	try
	{
	    m_macKey = key;
	}
	finally
	{
	    m_macKeyMutex.writeLock().unlock();
	}
    }

    public void setOzoneEncryptionKey(byte bytes[])
    {
	m_ozoneEncryptionKeyMutex.writeLock().lock();

	try
	{
	    if(bytes != null && bytes.length == 32)
		m_ozoneEncryptionKey = Miscellaneous.deepCopy(bytes);
	    else
	    {
		if(m_ozoneEncryptionKey != null)
		    Arrays.fill(m_ozoneEncryptionKey, (byte) 0);

		m_ozoneEncryptionKey = null;
	    }
	}
	finally
	{
	    m_ozoneEncryptionKeyMutex.writeLock().unlock();
	}
    }

    public void setOzoneMacKey(byte bytes[])
    {
	m_ozoneMacKeyMutex.writeLock().lock();

	try
	{
	    if(bytes != null && bytes.length == 64)
		m_ozoneMacKey = Miscellaneous.deepCopy(bytes);
	    else
	    {
		if(m_ozoneMacKey != null)
		    Arrays.fill(m_ozoneMacKey, (byte) 0);

		m_ozoneMacKey = null;
	    }
	}
	finally
	{
	    m_ozoneMacKeyMutex.writeLock().unlock();
	}
    }
}
