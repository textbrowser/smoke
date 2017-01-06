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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Cryptography
{
    private SecretKey m_encryptionKey = null;
    private SecretKey m_macKey = null;
    private static SecureRandom s_secureRandom = new SecureRandom();

    public static SecretKey generateEncryptionKey(byte []salt,
						  char []password,
						  int iterations)
	throws InvalidKeySpecException, NoSuchAlgorithmException
    {
	final int length = 256;

	KeySpec keySpec = new PBEKeySpec(password, salt, iterations, length);

	if(keySpec == null)
	    return null;

	SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
	    ("PBKDF2WithHmacSHA1");

	return secretKeyFactory.generateSecret(keySpec);
    }

    public static SecretKey generateMacKey(byte []salt,
					   char []password,
					   int iterations)
	throws InvalidKeySpecException, NoSuchAlgorithmException
    {
	final int length = 512;

	KeySpec keySpec = new PBEKeySpec(password, salt, iterations, length);

	if(keySpec == null)
	    return null;

	SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
	    ("PBKDF2WithHmacSHA1");

	return secretKeyFactory.generateSecret(keySpec);
    }

    public static String randomBytesAsBase64(int length)
    {
	byte bytes[] = new byte[length];

	if(bytes == null)
	    return "";

	s_secureRandom.nextBytes(bytes);
	return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    public static byte[] randomBytes(int length)
    {
	byte bytes[] = new byte[length];

	if(bytes == null)
	    return null;

	s_secureRandom.nextBytes(bytes);
	return bytes;
    }

    public void setEncryptionKey(SecretKey encryptionKey)
    {
	m_encryptionKey = encryptionKey;
    }

    public void setMacKey(SecretKey macKey)
    {
	m_macKey = macKey;
    }
}
