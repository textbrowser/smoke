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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Cryptography
{
    private SecretKey m_encryptionKey = null;
    private SecretKey m_macKey = null;
    private static final SecureRandom s_secureRandom =
	new SecureRandom(); // Thread-safe.

    public byte[] etm(byte data[])
    {
	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    Mac mac = null;
	    byte iv[] = new byte[16];

	    cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
	    s_secureRandom.nextBytes(iv);
	    cipher.init(Cipher.ENCRYPT_MODE,
			m_encryptionKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal(data);
	    mac = Mac.getInstance("HmacSHA512");
	    mac.init(m_macKey);
	    bytes = Miscellaneous.joinByteArrays(bytes, mac.doFinal(bytes));
	}
	catch(BadPaddingException |
	      IllegalBlockSizeException |
	      InvalidAlgorithmParameterException |
	      InvalidKeyException |
	      NoSuchAlgorithmException |
	      NoSuchPaddingException exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public byte[] hash(byte data[])
    {
	byte bytes[] = null;

	try
	{
	    Mac mac = null;

	    mac = Mac.getInstance("HmacSHA512");
	    mac.init(m_macKey);
	    bytes = mac.doFinal(data);
	}
	catch(InvalidKeyException |
	      NoSuchAlgorithmException exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static KeyPair generatePrivatePublicKeyPair(String algorithm,
						       int keySize)
	throws NoSuchAlgorithmException
    {
	KeyPairGenerator keyPairGenerator = KeyPairGenerator.
	    getInstance(algorithm);

	keyPairGenerator.initialize(keySize, s_secureRandom);
	return keyPairGenerator.generateKeyPair();
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

    public static String randomBytesAsBase64(int length)
    {
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

    public static byte[] randomBytes(int length)
    {
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
	catch(NoSuchAlgorithmException exception)
	{
	}

	return bytes;
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
