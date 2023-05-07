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

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.util.Base64;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
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
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.pqc.asn1.PQCObjectIdentifiers;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.mceliece.BCMcElieceCCA2PublicKey;
import org.bouncycastle.pqc.jcajce.provider.rainbow.BCRainbowPublicKey;
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey;
import org.bouncycastle.pqc.jcajce.spec.McElieceCCA2KeyGenParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.RainbowParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec;
import org.bouncycastle.util.encoders.Hex;

public class Cryptography
{
    public class PKIKeySizeBounds
    {
	/*
	** Private Keys
	*/

	public final static int PRIVATE_RSA = 2000;

	/*
	** Public Keys
	*/

	public final static int PUBLIC_EC = 200;
	public final static int PUBLIC_MCELIECE = 335000;
	public final static int PUBLIC_RSA = 600;
	public final static int PUBLIC_SPHINCS = 1200;
    };

    static
    {
	Security.addProvider(new BouncyCastlePQCProvider());
    }

    private KeyPair m_chatEncryptionPublicKeyPair = null;
    private KeyPair m_chatSignaturePublicKeyPair = null;
    private String m_chatEncryptionPublicKeyAlgorithm = "";
    private String m_sipHashId = "0000-0000-0000-0000-0000-0000-0000-0000";
    private byte m_encryptionKeyBytes[] = null;
    private byte m_encryptionKeyRandom[] = null;
    private byte m_identity[] = null; // Random identity.
    private byte m_macKeyBytes[] = null;
    private byte m_macKeyRandom[] = null;
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
    private final static String FIRE_HASH_ALGORITHM = "SHA-384";
    private final static String FIRE_HMAC_ALGORITHM = "HmacSHA384";
    private final static String FIRE_SYMMETRIC_ALGORITHM = "AES";
    private final static String FIRE_SYMMETRIC_CIPHER_TRANSFORMATION =
	"AES/CTS/NoPadding";
    private final static String HASH_ALGORITHM = "SHA-512";
    private final static String HMAC_ALGORITHM = "HmacSHA512";
    private final static String MCELIECE_HASH_ALGORITHM =
	McElieceCCA2KeyGenParameterSpec.SHA256;
    private final static String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1";
    private final static String PKI_ECDSA_SIGNATURE_ALGORITHM =
	"SHA512withECDSA";
    private final static String PKI_RAINBOW_SIGNATURE_ALGORITHM =
	"SHA512WITHRainbow";
    private final static String PKI_RSA_ENCRYPTION_ALGORITHM =
	"RSA/NONE/OAEPwithSHA-512andMGF1Padding";
    private final static String PKI_RSA_SIGNATURE_ALGORITHM =
	/*
	** SHA512withRSA/PSS requires API 23+.
	*/

	"SHA512withRSA";
    private final static String PKI_SPHINCS_SIGNATURE_ALGORITHM =
	"SHA3-512withSPHINCS256";
    private final static String SYMMETRIC_ALGORITHM = "AES";
    private final static String SYMMETRIC_CIPHER_TRANSFORMATION =
	"AES/CBC/PKCS7Padding";
    private final static int ARSON_MAXIMUM_KEYS_PER_PARTICIPANT = 2500;
    private final static int FIRE_STREAM_CREATION_ITERATION_COUNT = 10000;
    private final static int MCELIECE_M[] = {11, 12, 13};
    private final static int MCELIECE_T[] = {50, 68, 118};
    private final static int NUMBER_OF_CORES = Math.max
	(4, Runtime.getRuntime().availableProcessors());
    private final static int RAINBOW_VI[] = {48, 80, 112};
    private final static int SHA_1_OUTPUT_SIZE_BITS = 160;
    private final static int SIPHASH_STREAM_CREATION_ITERATION_COUNT = 4096;
    private static Cryptography s_instance = null;
    private static SecureRandom s_secureRandom = null;
    public final static String DEFAULT_SIPHASH_ID =
	"0000-0000-0000-0000-0000-0000-0000-0000";
    public final static String PARTICIPANT_CALL_MCELIECE_KEY_SIZE =
	"McEliece-Fujisaki (11, 50)";
    public final static String PUBLIC_KEY_TYPES[] = new String[]
	{ // Please see Kernel.java.
	 "McEliece-Fujisaki (11, 50)",    // 0
	 "McEliece-Fujisaki (12, 68)",    // 1
	 "McEliece-Fujisaki (13, 118)",   // 2
	 "McEliece-Pointcheval (11, 50)", // 3
	 "RSA"                            // 4
	};
    public final static String STEAM_KEY_EXCHANGE_MCELIECE_KEY_SIZE =
	"McEliece-Fujisaki (11, 50)";
    public final static String TLS_LEGACY_V12[] = new String[]
	{"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
    public final static String TLS_NEW[] = new String[]
	{"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};
    public final static String TLS_V1_V12[] = new String[]
	{"TLSv1", "TLSv1.1", "TLSv1.2"};
    public final static byte MESSAGES_KEY_TYPES[] =
	new byte[] {(byte) 'M', (byte) 'R'};
    public final static int CIPHER_HASH_KEYS_LENGTH = 96;
    public final static int CIPHER_IV_LENGTH = 16;
    public final static int CIPHER_KEY_LENGTH = 32;
    public final static int FIRE_CIPHER_IV_LENGTH = 16;
    public final static int FIRE_HASH_KEY_LENGTH = 48;
    public final static int HASH_KEY_LENGTH = 64;
    public final static int IDENTITY_SIZE = 8; // Size of a long.
    public final static int KEY_EXCHANGE_INITIAL_PBKDF2_ITERATION = 1000;
    public final static int PARTICIPANT_CALL_RSA_KEY_SIZE = 3072;
    public final static int PKI_SIGNATURE_KEY_SIZES[] = {384,  // ECDSA
							 4096, // RSA
							 112,  // Rainbow
							 64};  // SPHINCS
    public final static int PKI_ENCRYPTION_KEY_SIZES[] = {4096}; // RSA
    public final static int SIPHASH_OUTPUT_LENGTH = 16; // Bytes (128 bits).
    public final static int SIPHASH_IDENTITY_LENGTH =
	DEFAULT_SIPHASH_ID.length();
    public final static int STEAM_FILE_IDENTITY_LENGTH = 48;
    public final static int STEAM_KEY_EXCHANGE_RSA_KEY_SIZE = 3072;

    private Cryptography()
    {
	prepareSecureRandom();
	m_encryptionKeyRandom = randomBytes(CIPHER_KEY_LENGTH);
	m_macKeyRandom = randomBytes(HASH_KEY_LENGTH);
    }

    private SecretKey encryptionKey()
    {
	m_encryptionKeyMutex.readLock().lock();

	try
	{
	    return new SecretKeySpec
		(xor(m_encryptionKeyBytes, m_encryptionKeyRandom),
		 SYMMETRIC_ALGORITHM);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_encryptionKeyMutex.readLock().unlock();
	}

	return null;
    }

    private SecretKey macKey()
    {
	m_macKeyMutex.readLock().lock();

	try
	{
	    return new SecretKeySpec
		(xor(m_macKeyBytes, m_macKeyRandom), HASH_ALGORITHM);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_macKeyMutex.readLock().unlock();
	}

	return null;
    }

    private static synchronized void prepareSecureRandom()
    {
	if(s_secureRandom != null)
	    return;

	try
	{
	    s_secureRandom = new SecureRandom();
	}
	catch(Exception exception)
	{
	}
    }

    private void destroy(SecretKey key)
    {
	if(key == null)
	    return;

	try
	{
	}
	catch(Exception exception)
	{
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

    public String chatEncryptionPublicKeyAlgorithm()
    {
	return m_chatEncryptionPublicKeyAlgorithm;
    }

    public String etmBase64String(String string)
    {
	try
	{
	    return Base64.encodeToString
		(etm(string.getBytes()), Base64.DEFAULT);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public String etmBase64String(boolean state)
    {
	try
	{
	    return Base64.encodeToString
		(etm(String.valueOf(state).getBytes()), Base64.DEFAULT);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public String etmBase64String(byte data[])
    {
	try
	{
	    if(data == null)
		return Base64.encodeToString
		    (etm("".getBytes()), Base64.DEFAULT);
	    else
		return Base64.encodeToString(etm(data), Base64.DEFAULT);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public String etmBase64String(int value)
    {
	try
	{
	    return Base64.encodeToString
		(etm(String.valueOf(value).getBytes()), Base64.DEFAULT);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public String etmBase64String(long value)
    {
	try
	{
	    return Base64.encodeToString
		(etm(String.valueOf(value).getBytes()), Base64.DEFAULT);
	}
	catch(Exception exception)
	{
	}

	return null;
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
	    if(key.equals(m_chatEncryptionPublicKeyPair.getPublic()) ||
	       key.hashCode() ==
	       m_chatEncryptionPublicKeyPair.getPublic().hashCode())
		return true;
	}
	catch(Exception exception)
	{
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
	    if(key.equals(m_chatSignaturePublicKeyPair.getPublic()) ||
	       key.hashCode() ==
	       m_chatSignaturePublicKeyPair.getPublic().hashCode())
		return true;
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.readLock().unlock();
	}

	return false;
    }

    public boolean hasValidOzoneKeys()
    {
	byte bytes1[] = ozoneEncryptionKey();
	byte bytes2[] = ozoneMacKey();

	return !(bytes1 == null ||
		 bytes1.length != CIPHER_KEY_LENGTH ||
		 bytes2 == null ||
		 bytes2.length != HASH_KEY_LENGTH);
    }

    public boolean hasValidOzoneMacKey()
    {
	byte bytes[] = ozoneMacKey();

	return !(bytes == null || bytes.length != HASH_KEY_LENGTH);
    }

    public boolean isValidSipHashMac(byte data[], byte mac[])
    {
	if(data == null || mac == null)
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
	if(data == null || mac == null)
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
		salt = sha512(m_sipHashId.getBytes(StandardCharsets.UTF_8));
		temporary = pbkdf2(salt,
				   m_sipHashId.toCharArray(),
				   SIPHASH_STREAM_CREATION_ITERATION_COUNT,
				   SHA_1_OUTPUT_SIZE_BITS); // SHA-1
	    }
	    finally
	    {
		m_sipHashIdMutex.readLock().unlock();
	    }

	    if(temporary != null)
		bytes = pbkdf2
		    (salt,
		     Base64.encodeToString(temporary,
					   Base64.NO_WRAP).toCharArray(),
		     1,
		     8 * (CIPHER_KEY_LENGTH + HASH_KEY_LENGTH)); // Bits.

	    if(bytes != null)
	    {
		m_sipHashEncryptionKeyMutex.writeLock().lock();

		try
		{
		    m_sipHashEncryptionKey = Arrays.copyOfRange
			(bytes, 0, CIPHER_KEY_LENGTH);
		}
		finally
		{
		    m_sipHashEncryptionKeyMutex.writeLock().unlock();
		}

		m_sipHashMacKeyMutex.writeLock().lock();

		try
		{
		    m_sipHashMacKey = Arrays.copyOfRange
			(bytes,
			 CIPHER_KEY_LENGTH,
			 CIPHER_KEY_LENGTH + HASH_KEY_LENGTH);
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
	catch(Exception exception)
	{
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}

	return null;
    }

    public byte[] chatSignaturePublicKeyDigest()
    {
	m_chatSignaturePublicKeyPairMutex.readLock().lock();

	try
	{
	    if(m_chatSignaturePublicKeyPair == null ||
	       m_chatSignaturePublicKeyPair.getPublic() == null)
		return null;

	    return sha512
		(m_chatSignaturePublicKeyPair.getPublic().getEncoded());
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_chatSignaturePublicKeyPairMutex.readLock().unlock();
	}

	return null;
    }

    public byte[] decryptWithSipHashKey(byte data[])
    {
	if(data == null)
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
		byte iv[] = Arrays.copyOf(data, CIPHER_IV_LENGTH);

		cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
		cipher.init(Cipher.DECRYPT_MODE,
			    secretKey,
			    new IvParameterSpec(iv));
		bytes = cipher.doFinal
		    (Arrays.copyOfRange(data, CIPHER_IV_LENGTH, data.length));
	    }
	    catch(Exception exception)
	    {
		return null;
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

	if(data == null)
	    return null;

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    byte iv[] = new byte[CIPHER_IV_LENGTH];

	    cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
	    s_secureRandom.nextBytes(iv);
	    cipher.init(Cipher.ENCRYPT_MODE,
			encryptionKey(),
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal(data);
	    bytes = Miscellaneous.joinByteArrays(iv, bytes);
	}
	catch(Exception exception)
	{
	    return null;
	}

	try
	{
	    Mac mac = null;

	    mac = Mac.getInstance(HMAC_ALGORITHM);
	    mac.init(macKey());
	    bytes = Miscellaneous.joinByteArrays(bytes, mac.doFinal(bytes));
	}
	catch(Exception exception)
	{
	    return null;
	}

	return bytes;
    }

    public byte[] generateFireDigestKeyStream(String digest)
    {
	byte salt[] = null;

	try
	{
	    salt = sha512
		(Miscellaneous.
		 joinByteArrays(digest.getBytes(StandardCharsets.ISO_8859_1),
				"sha384".getBytes(StandardCharsets.
						  ISO_8859_1)));
	}
	catch(Exception exception)
	{
	    return null;
	}

	/*
	** Now, a key stream.
	*/

	byte stream[] = null;

	try
	{
	    stream = pbkdf2
		(salt,
		 new String(digest.getBytes(StandardCharsets.UTF_8)).
		 toCharArray(),
		 FIRE_STREAM_CREATION_ITERATION_COUNT,
		 896);
	}
	catch(Exception exception)
	{
	    return null;
	}

	return stream;
    }

    public byte[] generateFireEncryptionKey(String channel, String salt)
    {
	byte ciphertext[] = null;

	try
	{
	    ciphertext = "aes256".getBytes
		(StandardCharsets.ISO_8859_1); // Latin-1
	}
	catch(Exception exception)
	{
	    return null;
	}

	byte c[] = null;

	try
	{
	    c = channel.getBytes(StandardCharsets.ISO_8859_1); // Latin-1
	}
	catch(Exception exception)
	{
	    return null;
	}

	byte s[] = null;

	try
	{
	    s = salt.getBytes(StandardCharsets.ISO_8859_1); // Latin-1
	}
	catch(Exception exception)
	{
	    return null;
	}

	byte sha384[] = null;

	try
	{
	    sha384 = "sha384".getBytes(StandardCharsets.ISO_8859_1); // Latin-1
	}
	catch(Exception exception)
	{
	    return null;
	}

	/*
	** Now, a key.
	*/

	byte key[] = null;

	try
	{
	    key = pbkdf2
		(s,
		 new String(new String(Miscellaneous.
				       joinByteArrays(c, ciphertext, sha384)).
			    getBytes(StandardCharsets.UTF_8)).toCharArray(),
		 FIRE_STREAM_CREATION_ITERATION_COUNT,
		 2304);

	    if(key != null)
		key = Arrays.copyOfRange(key, 0, CIPHER_KEY_LENGTH);
	}
	catch(Exception exception)
	{
	    return null;
	}

	return key;
    }

    public byte[] hmac(byte data[])
    {
	if(data == null)
	    return null;

	try
	{
	    Mac mac = null;

	    mac = Mac.getInstance(HMAC_ALGORITHM);
	    mac.init(macKey());
	    return mac.doFinal(data);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public byte[] identity()
    {
	m_identityMutex.writeLock().lock();

	try
	{
	    if(m_identity == null)
	    {
		m_identity = new byte[IDENTITY_SIZE];
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

	if(data == null)
	    return null;

	try
	{
	    /*
	    ** Verify the computed digest with the provided digest.
	    */

	    byte digest1[] = null; // Provided digest.
	    byte digest2[] = null; // Computed digest.

	    digest1 = Arrays.copyOfRange
		(data, data.length - HASH_KEY_LENGTH, data.length);

	    try
	    {
		Mac mac = null;

		mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(macKey());
		digest2 = mac.doFinal
		    (Arrays.copyOf(data, data.length - HASH_KEY_LENGTH));
	    }
	    catch(Exception exception)
	    {
		return null;
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
	    byte iv[] = Arrays.copyOf(data, CIPHER_IV_LENGTH);

	    cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
	    cipher.init(Cipher.DECRYPT_MODE,
			encryptionKey(),
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal
		(Arrays.copyOfRange(data,
				    CIPHER_IV_LENGTH,
				    data.length - HASH_KEY_LENGTH));
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
	if(data == null)
	    return null;

	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
	{
	    byte bytes[] = null;

	    try
	    {
		Cipher cipher = null;

		if(m_chatEncryptionPublicKeyPair.getPrivate().getAlgorithm().
		   equals("McEliece-CCA2"))
		{
		    if(m_chatEncryptionPublicKeyAlgorithm.
		       startsWith("McEliece-Fujisaki"))
			cipher = Cipher.getInstance("McElieceFujisaki");
		    else
			cipher = Cipher.getInstance("McEliecePointcheval");

		    cipher.init
			(Cipher.DECRYPT_MODE,
			 m_chatEncryptionPublicKeyPair.getPrivate(),
			 (McElieceCCA2KeyGenParameterSpec) null);
		}
		else
		{
		    cipher = Cipher.getInstance(PKI_RSA_ENCRYPTION_ALGORITHM);
		    cipher.init
			(Cipher.DECRYPT_MODE,
			 m_chatEncryptionPublicKeyPair.getPrivate());
		}

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
	if(data == null)
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
		switch(m_chatEncryptionPublicKeyPair.
		       getPrivate().getAlgorithm())
		{
		case "EC":
		    signature = Signature.getInstance
			(PKI_ECDSA_SIGNATURE_ALGORITHM);
		    break;
		case "RSA":
		    signature = Signature.getInstance
			(PKI_RSA_SIGNATURE_ALGORITHM);
		    break;
		case "Rainbow":
		    signature = Signature.getInstance
			(PKI_RAINBOW_SIGNATURE_ALGORITHM,
			 BouncyCastlePQCProvider.PROVIDER_NAME);
		    break;
		default:
		    signature = Signature.getInstance
			(PKI_SPHINCS_SIGNATURE_ALGORITHM,
			 BouncyCastlePQCProvider.PROVIDER_NAME);
		    break;
		}

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
	if(data == null)
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
		switch(m_chatSignaturePublicKeyPair.getPrivate().getAlgorithm())
		{
		case "EC":
		    signature = Signature.getInstance
			(PKI_ECDSA_SIGNATURE_ALGORITHM);
		    break;
		case "RSA":
		    signature = Signature.getInstance
			(PKI_RSA_SIGNATURE_ALGORITHM);
		    break;
		case "Rainbow":
		    signature = Signature.getInstance
			(PKI_RAINBOW_SIGNATURE_ALGORITHM,
			 BouncyCastlePQCProvider.PROVIDER_NAME);
		    break;
		default:
		    signature = Signature.getInstance
			(PKI_SPHINCS_SIGNATURE_ALGORITHM,
			 BouncyCastlePQCProvider.PROVIDER_NAME);
		    break;
		}

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

    public int chatEncryptionPublicKeyT()
    {
	m_chatEncryptionPublicKeyPairMutex.readLock().lock();

	try
	{
	    if(m_chatEncryptionPublicKeyPair != null)
		return
		    ((BCMcElieceCCA2PublicKey) m_chatEncryptionPublicKeyPair.
		     getPublic()).getT();
	    else
		return 0;
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.readLock().unlock();
	}

	return 0;
    }

    public static KeyPair generatePrivatePublicKeyPair(String algorithm,
						       int keySize1,
						       int keySize2)
    {
	if(algorithm.startsWith("McEliece-Fujisaki") ||
	   algorithm.startsWith("McEliece-Pointcheval"))
	    try
	    {
		KeyPairGenerator keyPairGenerator = null;

		if(algorithm.startsWith("McEliece-Fujisaki"))
		    keyPairGenerator = KeyPairGenerator.
			getInstance("McElieceFujisaki");
		else
		    keyPairGenerator = KeyPairGenerator.
			getInstance("McEliecePointcheval");

		McElieceCCA2KeyGenParameterSpec parameters = null;

		if(keySize2 == 0 || keySize2 == 1 || keySize2 == 2)
		    parameters = new McElieceCCA2KeyGenParameterSpec
			(MCELIECE_M[keySize2],
			 MCELIECE_T[keySize2],
			 MCELIECE_HASH_ALGORITHM);
		else
		    parameters = new McElieceCCA2KeyGenParameterSpec
			(MCELIECE_M[0], MCELIECE_T[0], MCELIECE_HASH_ALGORITHM);

		keyPairGenerator.initialize(parameters);
		return keyPairGenerator.generateKeyPair();
	    }
	    catch(Exception exception)
	    {
	    }
	else
	{
	    switch(algorithm)
	    {
	    case "EC":
	    case "ECDSA":
	    case "RSA":
		prepareSecureRandom();

		try
		{
		    KeyPairGenerator keyPairGenerator = KeyPairGenerator.
			getInstance(algorithm);

		    keyPairGenerator.initialize(keySize1, s_secureRandom);
		    return keyPairGenerator.generateKeyPair();
		}
		catch(Exception exception)
		{
		}

		break;
	    case "Rainbow":
		try
		{
		    KeyPairGenerator keyPairGenerator = KeyPairGenerator.
			getInstance(algorithm,
				    BouncyCastlePQCProvider.PROVIDER_NAME);
		    RainbowParameterSpec parameters =
			new RainbowParameterSpec(RAINBOW_VI);

		    keyPairGenerator.initialize(parameters, s_secureRandom);
		    return keyPairGenerator.generateKeyPair();
		}
		catch(Exception exception)
		{
		}

		break;
	    default:
		try
		{
		    KeyPairGenerator keyPairGenerator = KeyPairGenerator.
			getInstance("SPHINCS256",
				    BouncyCastlePQCProvider.PROVIDER_NAME);
		    SPHINCS256KeyGenParameterSpec parameters =
			new SPHINCS256KeyGenParameterSpec
			(SPHINCS256KeyGenParameterSpec.SHA3_256);

		    keyPairGenerator.initialize(parameters, s_secureRandom);
		    return keyPairGenerator.generateKeyPair();
		}
		catch(Exception exception)
		{
		}

		break;
	    }
	}

	return null;
    }

    public static KeyPair generatePrivatePublicKeyPair(String algorithm,
						       byte privateBytes[],
						       byte publicBytes[])
    {
	if(privateBytes == null ||
	   privateBytes.length == 0 ||
	   publicBytes == null ||
	   publicBytes.length == 0)
	    return null;

	try
	{
	    if(algorithm.startsWith("EC") || algorithm.startsWith("RSA"))
	    {
		EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec
		    (privateBytes);
		EncodedKeySpec publicKeySpec = new X509EncodedKeySpec
		    (publicBytes);
		KeyFactory generator = KeyFactory.getInstance(algorithm);
		PrivateKey privateKey = generator.generatePrivate
		    (privateKeySpec);
		PublicKey publicKey = generator.generatePublic(publicKeySpec);

		return new KeyPair(publicKey, privateKey);
	    }
	    else if(algorithm.startsWith("McEliece"))
	    {
		EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec
		    (privateBytes);
		EncodedKeySpec publicKeySpec = new X509EncodedKeySpec
		    (publicBytes);
		KeyFactory generator = KeyFactory.getInstance
		    (PQCObjectIdentifiers.mcElieceCca2.getId(),
		     BouncyCastlePQCProvider.PROVIDER_NAME);
		PrivateKey privateKey = generator.generatePrivate
		    (privateKeySpec);
		PublicKey publicKey = generator.generatePublic(publicKeySpec);

		return new KeyPair(publicKey, privateKey);
	    }
	    else if(algorithm.startsWith("Rainbow"))
	    {
		EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec
		    (privateBytes);
		EncodedKeySpec publicKeySpec = new X509EncodedKeySpec
		    (publicBytes);
		KeyFactory generator = KeyFactory.getInstance
		    ("Rainbow", BouncyCastlePQCProvider.PROVIDER_NAME);
		PrivateKey privateKey = generator.generatePrivate
		    (privateKeySpec);
		PublicKey publicKey = generator.generatePublic(publicKeySpec);

		return new KeyPair(publicKey, privateKey);
	    }
	    else
	    {
		EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec
		    (privateBytes);
		EncodedKeySpec publicKeySpec = new X509EncodedKeySpec
		    (publicBytes);
		KeyFactory generator = KeyFactory.getInstance
		    ("SPHINCS256", BouncyCastlePQCProvider.PROVIDER_NAME);
		PrivateKey privateKey = generator.generatePrivate
		    (privateKeySpec);
		PublicKey publicKey = generator.generatePublic(publicKeySpec);

		return new KeyPair(publicKey, privateKey);
	    }
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static PrivateKey privateKeyFromBytes(byte privateBytes[])
    {
	if(privateBytes == null || privateBytes.length == 0)
	    return null;

	try
	{
	    KeyFactory generator = null;

	    if(privateBytes.length < PKIKeySizeBounds.PRIVATE_RSA)
		generator = KeyFactory.getInstance("RSA");
	    else
		generator = KeyFactory.getInstance
		    (PQCObjectIdentifiers.mcElieceCca2.getId(),
		     BouncyCastlePQCProvider.PROVIDER_NAME);

	    return generator.generatePrivate
		(new PKCS8EncodedKeySpec(privateBytes));
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static PublicKey publicKeyFromBytes(byte publicBytes[])
    {
	if(publicBytes == null || publicBytes.length == 0)
	    return null;

	try
	{
	    KeyFactory generator = null;
	    int length = publicBytes.length;

	    if(length < PKIKeySizeBounds.PUBLIC_EC)
		generator = KeyFactory.getInstance("EC");
	    else if(length < PKIKeySizeBounds.PUBLIC_RSA)
		generator = KeyFactory.getInstance("RSA");
	    else if(length < PKIKeySizeBounds.PUBLIC_SPHINCS)
		generator = KeyFactory.getInstance
		    ("SPHINCS256", BouncyCastlePQCProvider.PROVIDER_NAME);
	    else if(length < PKIKeySizeBounds.PUBLIC_MCELIECE)
		generator = KeyFactory.getInstance
		    (PQCObjectIdentifiers.mcElieceCca2.getId(),
		     BouncyCastlePQCProvider.PROVIDER_NAME);
	    else
		generator = KeyFactory.getInstance
		    ("Rainbow", BouncyCastlePQCProvider.PROVIDER_NAME);

	    return generator.generatePublic
		(new X509EncodedKeySpec(publicBytes));
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static PublicKey publicMcElieceKeyFromBytes(byte publicBytes[])
    {
	if(publicBytes == null || publicBytes.length == 0)
	    return null;

	try
	{
	    KeyFactory generator = KeyFactory.getInstance
		(PQCObjectIdentifiers.mcElieceCca2.getId(),
		 BouncyCastlePQCProvider.PROVIDER_NAME);

	    return generator.generatePublic
		(new X509EncodedKeySpec(publicBytes));
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static PublicKey publicRSAKeyFromBytes(byte publicBytes[])
    {
	if(publicBytes == null || publicBytes.length == 0)
	    return null;

	try
	{
	    KeyFactory generator = KeyFactory.getInstance("RSA");

	    return generator.generatePublic
		(new X509EncodedKeySpec(publicBytes));
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static SecretKey generateEncryptionKey(byte salt[],
						  char password[],
						  int iterations,
						  int keyDerivationFunction)
    {
	if(password == null || salt == null)
	    return null;

	if(keyDerivationFunction == 0) // Argon2id
	{
	    try
	    {
		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		Argon2Parameters.Builder builder = new Argon2Parameters.Builder
		    (Argon2Parameters.ARGON2_id).
		    withVersion(Argon2Parameters.ARGON2_VERSION_13).
		    withIterations(iterations).
		    withMemoryAsKB(CIPHER_KEY_LENGTH).
		    withParallelism(NUMBER_OF_CORES).
		    withAdditional
		    (Hex.decode("010203040506070809000a0b0c0d0e0f" +
				"010203040506070809000a0b0c0d0e0f")).
		    withSecret(new String(password).
			       getBytes(StandardCharsets.UTF_8)).
		    withSalt(salt);
		byte bytes[] = new byte[CIPHER_KEY_LENGTH];

		generator.init(builder.build());
		generator.generateBytes(password, bytes);
		return new SecretKeySpec(bytes, SYMMETRIC_ALGORITHM);
	    }
	    catch(Exception exception)
	    {
	    }
	}
	else // PBKDF2
	{
	    int length = 8 * CIPHER_KEY_LENGTH; // Bits.

	    try
	    {
		KeySpec keySpec = new PBEKeySpec
		    (password, salt, iterations, length);
		SecretKeyFactory secretKeyFactory = SecretKeyFactory.
		    getInstance(PBKDF2_ALGORITHM);

		return secretKeyFactory.generateSecret(keySpec);
	    }
	    catch(Exception exception)
	    {
	    }
	}

	return null;
    }

    public static SecretKey generateMacKey(byte salt[],
					   char password[],
					   int iterations,
					   int keyDerivationFunction)
    {
	if(password == null || salt == null)
	    return null;

	if(keyDerivationFunction == 0) // Argon2id
	{
	    try
	    {
		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		Argon2Parameters.Builder builder = new Argon2Parameters.Builder
		    (Argon2Parameters.ARGON2_id).
		    withVersion(Argon2Parameters.ARGON2_VERSION_13).
		    withIterations(iterations).
		    withMemoryAsKB(HASH_KEY_LENGTH).
		    withParallelism(NUMBER_OF_CORES).
		    withAdditional
		    (Hex.decode("000908070605040302010f0e0d0c0b0a" +
				"000908070605040302010f0e0d0c0b0a" +
				"000908070605040302010f0e0d0c0b0a" +
				"000908070605040302010f0e0d0c0b0a")).
		    withSecret(new String(password).
			       getBytes(StandardCharsets.UTF_8)).
		    withSalt(salt);
		byte bytes[] = new byte[HASH_KEY_LENGTH];

		generator.init(builder.build());
		generator.generateBytes(password, bytes);
		return new SecretKeySpec(bytes, HASH_ALGORITHM);
	    }
	    catch(Exception exception)
	    {
	    }
	}
	else // PBKDF2
	{
	    int length = 8 * HASH_KEY_LENGTH; // Bits.

	    try
	    {
		KeySpec keySpec = new PBEKeySpec
		    (password, salt, iterations, length);
		SecretKeyFactory secretKeyFactory = SecretKeyFactory.
		    getInstance(PBKDF2_ALGORITHM);

		return secretKeyFactory.generateSecret(keySpec);
	    }
	    catch(Exception exception)
	    {
	    }
	}

	return null;
    }

    public static String fancyKeyInformationOutput(KeyPair keyPair,
						   String algorithmIdentifier)
    {
	if(keyPair == null)
	    return "";
	else
	    return fancyKeyInformationOutput
		(keyPair.getPrivate(),
		 keyPair.getPublic(),
		 algorithmIdentifier);
    }

    public static String fancyKeyInformationOutput(PrivateKey privateKey,
						   PublicKey publicKey,
						   String algorithmIdentifier)
    {
	if(publicKey == null)
	    return "";

	try
	{
	    String algorithm = publicKeyAlgorithm(publicKey);
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append("Algorithm: ");
	    stringBuilder.append(algorithm);

	    if(!algorithmIdentifier.isEmpty())
	    {
		stringBuilder.append(" (");
		stringBuilder.append(algorithmIdentifier);
		stringBuilder.append(")");
	    }

	    if(privateKey != null)
	    {
		stringBuilder.append("\nDisk Size (Private): ");
		stringBuilder.append(privateKey.getEncoded().length);
		stringBuilder.append(" Bytes");
	    }

	    stringBuilder.append("\nDisk Size (Public): ");
	    stringBuilder.append(publicKey.getEncoded().length);
	    stringBuilder.append(" Bytes\n");
	    stringBuilder.append("Fingerprint: ");
	    stringBuilder.append(publicKeyFingerPrint(publicKey));
	    stringBuilder.append("\nFormat: ");
	    stringBuilder.append(publicKey.getFormat());
	    stringBuilder.append("\nKey Algorithm: ");
	    stringBuilder.append(publicKey.getAlgorithm());

	    if(algorithm.equals("RSA") ||
	       algorithm.equals("Rainbow") ||
	       algorithm.startsWith("EC") ||
	       algorithm.startsWith("McEliece") ||
	       algorithm.startsWith("SPHINCS"))
		try
		{
		    switch(algorithm)
		    {
		    case "EC":
		    case "ECDSA":
			ECPublicKey ecPublicKey = (ECPublicKey) publicKey;

			if(ecPublicKey != null)
			    stringBuilder.append("\n").append("Size: ").append
				(Math.max(ecPublicKey.getW().getAffineX().
					  bitLength(),
					  ecPublicKey.getW().getAffineY().
					  bitLength()));

			break;
		    case "McEliece-CCA2":
		    case "McEliece-Fujisaki":
		    case "McEliece-Pointcheval":
			BCMcElieceCCA2PublicKey mcEliecePublicKey =
			    (BCMcElieceCCA2PublicKey) publicKey;

			if(mcEliecePublicKey != null)
			    stringBuilder.append("\n").append("m = ").
				append((int) (Math.log(mcEliecePublicKey.
						       getN()) /
					      Math.log(2))).
				append(", t = ").
				append(mcEliecePublicKey.getT());

			break;
		    case "RSA":
			RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

			if(rsaPublicKey != null)
			    stringBuilder.append("\n").append("Size: ").
				append(rsaPublicKey.getModulus().bitLength());

			break;
		    case "Rainbow":
			BCRainbowPublicKey rainbowPublicKey =
			    (BCRainbowPublicKey) publicKey;

			if(rainbowPublicKey != null)
			    stringBuilder.append("\n").append("n = ").
				append(rainbowPublicKey.
				       getCoeffSingular()[0].length);

			break;
		    default:
			BCSphincs256PublicKey sphincsPublicKey =
			    (BCSphincs256PublicKey) publicKey;

			if(sphincsPublicKey != null)
			    stringBuilder.append("\n").append("Size: 512");

			break;
		    }
		}
		catch(Exception exception)
		{
		}

	    return stringBuilder.toString();
	}
	catch(Exception exception)
	{
	}

	return "";
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
	    int length = fingerprint.length();

	    for(int i = 0; i < length; i += 2)
		if(i < length - 2)
		    stringBuilder.append(fingerprint, i, i + 2).
			append(":");
		else
		    stringBuilder.append(fingerprint.substring(i));
	}
	catch(Exception exception)
	{
	}

	return stringBuilder.toString();
    }

    public static String publicKeyAlgorithm(PublicKey publicKey)
    {
	if(publicKey == null)
	    return "";

	try
	{
	    ASN1ObjectIdentifier asn1ObjectIdentifier = null;
	    SubjectPublicKeyInfo subjectPublicKeyInfo =
		SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());

	    asn1ObjectIdentifier = subjectPublicKeyInfo.getAlgorithm().
		getAlgorithm();

	    if(asn1ObjectIdentifier.equals(PQCObjectIdentifiers.mcElieceCca2))
		return "McEliece-CCA2";
	    else if(asn1ObjectIdentifier.
		    equals(PQCObjectIdentifiers.mcElieceFujisaki))
		return "McEliece-Fujisaki";
	    else if(asn1ObjectIdentifier.
		    equals(PQCObjectIdentifiers.mcEliecePointcheval))
		return "McEliece-Pointcheval";
	    else if(publicKey instanceof BCRainbowPublicKey)
		return "Rainbow";
	    else if(publicKey instanceof BCSphincs256PublicKey)
		return "SPHINCS";
	    else if(publicKey.getAlgorithm().equals("EC"))
		return "ECDSA";
	    else
		return "RSA";
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public static String publicKeyFingerPrint(PublicKey publicKey)
    {
	if(publicKey == null)
	    return fingerPrint(null);
	else
	    return fingerPrint(publicKey.getEncoded());
    }

    public static String sipHashIdFromString(String string)
    {
	try
	{
	    byte bytes[] = string.getBytes(StandardCharsets.UTF_8);

	    if(bytes != null)
	    {
		byte key[] = keyForSipHash(bytes);

		if(key == null)
		    return "";

		SipHash sipHash = new SipHash();
		long value[] = sipHash.hmac(bytes, key, SIPHASH_OUTPUT_LENGTH);

		if(Arrays.equals(new long[] {0L, 0L}, value))
		    return "";

		bytes = Miscellaneous.longArrayToByteArray(value);

		if(bytes == null)
		    return "";

		return Miscellaneous.
		    byteArrayAsHexStringDelimited(bytes, '-', 4).toUpperCase();
	    }
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public static boolean memcmp(byte a[], byte b[])
    {
	if(a == null || b == null)
	    return false;

	int rc = 0;
	int size = Math.max(a.length, b.length);

	for(int i = 0; i < size; i++)
	    rc |= (a.length > i ? a[i] : 0) ^ (b.length > i ? b[i] : 0);

	return rc == 0;
    }

    public static boolean verifySignature(PublicKey publicKey,
					  byte bytes[],
					  byte data[])
    {
	if(bytes == null || data == null || publicKey == null)
	    return false;

	try
	{
	    Signature signature = null;

	    switch(publicKey.getAlgorithm())
	    {
	    case "EC":
		signature = Signature.getInstance
		    (PKI_ECDSA_SIGNATURE_ALGORITHM);
		break;
	    case "RSA":
		signature = Signature.getInstance
		    (PKI_RSA_SIGNATURE_ALGORITHM);
		break;
	    case "Rainbow":
		signature = Signature.getInstance
		    (PKI_RAINBOW_SIGNATURE_ALGORITHM,
		     BouncyCastlePQCProvider.PROVIDER_NAME);
		break;
	    default:
		signature = Signature.getInstance
		    (PKI_SPHINCS_SIGNATURE_ALGORITHM,
		     BouncyCastlePQCProvider.PROVIDER_NAME);
		break;
	    }

	    signature.initVerify(publicKey);
	    signature.update(data);
	    return signature.verify(bytes);
	}
	catch(Exception exception)
	{
	}

	return false;
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
	if(data == null || keyBytes == null)
	    return null;

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    SecretKey secretKey = new SecretKeySpec
		(keyBytes, SYMMETRIC_ALGORITHM);
	    byte iv[] = Arrays.copyOf(data, CIPHER_IV_LENGTH);

	    cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORMATION);
	    cipher.init(Cipher.DECRYPT_MODE,
			secretKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal
		(Arrays.copyOfRange(data, CIPHER_IV_LENGTH, data.length));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] decryptFire(byte data[], byte keyBytes[])
    {
	if(data == null || keyBytes == null)
	    return null;

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    SecretKey secretKey = new SecretKeySpec
		(keyBytes, FIRE_SYMMETRIC_ALGORITHM);
	    byte iv[] = Arrays.copyOf(data, FIRE_CIPHER_IV_LENGTH);

	    cipher = Cipher.getInstance(FIRE_SYMMETRIC_CIPHER_TRANSFORMATION);
	    cipher.init(Cipher.DECRYPT_MODE,
			secretKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal
		(Arrays.copyOfRange(data, FIRE_CIPHER_IV_LENGTH, data.length));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
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
	    byte iv[] = new byte[CIPHER_IV_LENGTH];

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

    public static byte[] encryptFire(byte data[], byte keyBytes[])
    {
	if(data == null || keyBytes == null)
	    return null;

	prepareSecureRandom();

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;
	    SecretKey secretKey = new SecretKeySpec
		(keyBytes, FIRE_SYMMETRIC_ALGORITHM);
	    byte iv[] = new byte[FIRE_CIPHER_IV_LENGTH];

	    cipher = Cipher.getInstance(FIRE_SYMMETRIC_CIPHER_TRANSFORMATION);
	    s_secureRandom.nextBytes(iv);
	    cipher.init(Cipher.ENCRYPT_MODE,
			secretKey,
			new IvParameterSpec(iv));
	    bytes = cipher.doFinal
		(Miscellaneous.
		 joinByteArrays(data,

				/*
				** Add the size of the original data.
				*/

				Miscellaneous.intToByteArray(data.length)));
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
	if(data == null)
	    return null;

	return pbkdf2(sha512(data),
		      Miscellaneous.byteArrayAsHexString(data).toCharArray(),
		      SIPHASH_STREAM_CREATION_ITERATION_COUNT,
		      8 * SipHash.KEY_LENGTH);
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

    public static byte[] hmacFire(byte data[], byte keyBytes[])
    {
	if(data == null || keyBytes == null)
	    return null;

	byte bytes[] = null;

	try
	{
	    Mac mac = null;
	    SecretKey key = new SecretKeySpec(keyBytes, FIRE_HASH_ALGORITHM);

	    mac = Mac.getInstance(FIRE_HMAC_ALGORITHM);
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
	if(password == null || salt == null)
	    return null;

	try
	{
	    KeySpec keySpec = new PBEKeySpec
		(password, salt, iterations, length);
	    SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance
		(PBKDF2_ALGORITHM);

	    return secretKeyFactory.generateSecret(keySpec).getEncoded();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static byte[] pkiDecrypt(PrivateKey privateKey, byte data[])
    {
	if(data == null || privateKey == null)
	    return null;

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;

	    if(privateKey.getAlgorithm().equals("McEliece-CCA2"))
		cipher = Cipher.getInstance("McElieceFujisaki");
	    else
		cipher = Cipher.getInstance(PKI_RSA_ENCRYPTION_ALGORITHM);

	    cipher.init(Cipher.DECRYPT_MODE, privateKey);
	    bytes = cipher.doFinal(data);
	}
	catch(Exception exception)
	{
	    bytes = null;
	}

	return bytes;
    }

    public static byte[] pkiEncrypt(PublicKey publicKey,
				    String algorithm,
				    byte data[])
    {
	if(data == null || publicKey == null)
	    return null;

	prepareSecureRandom();

	byte bytes[] = null;

	try
	{
	    Cipher cipher = null;

	    if(publicKey.getAlgorithm().equals("McEliece-CCA2"))
	    {
		if(algorithm.startsWith("McEliece-Fujisaki"))
		    cipher = Cipher.getInstance("McElieceFujisaki");
		else
		    cipher = Cipher.getInstance("McEliecePointcheval");

		cipher.init
		    (Cipher.ENCRYPT_MODE,
		     publicKey,
		     (McElieceCCA2KeyGenParameterSpec) null,
		     s_secureRandom);
	    }
	    else
	    {
		cipher = Cipher.getInstance(PKI_RSA_ENCRYPTION_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
	    }

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
	if(length <= 0)
	    return null;

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

    public static byte[] sha256(byte[] ... data)
    {
	byte bytes[] = null;

	try
	{
	    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

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

    public static byte[] sha256FileDigest(String fileName)
    {
	AssetFileDescriptor assetFileDescriptor = null;

	try
	{
	    Uri uri = Uri.parse(fileName);

	    assetFileDescriptor = Smoke.getApplication().getContentResolver().
		openAssetFileDescriptor(uri, "r");

	    FileInputStream fileInputStream = assetFileDescriptor.
		createInputStream();
	    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
	    byte buffer[] = new byte[4096];
	    int n = 0;

	    while(n != -1)
	    {
		if(Thread.currentThread().isInterrupted())
		    return null;

		n = fileInputStream.read(buffer);

		if(n > 0)
		    messageDigest.update(buffer, 0, n);
	    }

	    return messageDigest.digest();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    try
	    {
		if(assetFileDescriptor != null)
		    assetFileDescriptor.close();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	return null;
    }

    public static byte[] sha3_512(byte[] ... data)
    {
	byte bytes[] = null;

	try
	{
	    SHA3.Digest512 messageDigest = new SHA3.Digest512();

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

    public static byte[] sha512(byte[] ... data)
    {
	byte bytes[] = null;

	try
	{
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

    public static byte[] xor(byte[] ... data)
    {
	if(data == null)
	    return null;

	try
	{
	    int length = 0;

	    for(byte b[] : data)
		if(b != null)
		{
		    if(length == 0)
			length = b.length;
		    else
			length = Math.min(b.length, length);
		}

	    if(length == 0)
		return null;

	    byte bytes[] = new byte[length];

	    Arrays.fill(bytes, (byte) 0);

	    for(byte b[] : data)
		if(b != null)
		    for(int i = 0; i < length; i++)
			bytes[i] = (byte) (b[i] ^ bytes[i]);

	    return bytes;
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static synchronized Cryptography getInstance()
    {
	if(s_instance == null)
	    s_instance = new Cryptography();

	return s_instance;
    }

    public boolean prepareSipHashIds(String alias)
    {
	try
	{
	    byte bytes[] = null;

	    if(alias == null || alias.trim().isEmpty())
		bytes = Miscellaneous.joinByteArrays
		    (chatEncryptionKeyPair().getPublic().getEncoded(),
		     chatSignatureKeyPair().getPublic().getEncoded());
	    else
		bytes = alias.getBytes(StandardCharsets.UTF_8);

	    if(bytes != null)
	    {
		byte key[] = keyForSipHash(bytes);

		if(key == null)
		    return false;

		SipHash sipHash = new SipHash();
		long value[] = sipHash.hmac(bytes, key, SIPHASH_OUTPUT_LENGTH);

		if(Arrays.equals(new long[] {0L, 0L}, value))
		    return false;

		bytes = Miscellaneous.longArrayToByteArray(value);

		if(bytes == null)
		    return false;

		m_sipHashIdDigestMutex.writeLock().lock();

		try
		{
		    m_sipHashIdDigest =
			sha512(Miscellaneous.
			       byteArrayAsHexStringDelimited(bytes, '-', 4).
			       toUpperCase().getBytes());
		}
		catch(Exception exception)
		{
		}
		finally
		{
		    m_sipHashIdDigestMutex.writeLock().unlock();
		}

		m_sipHashIdMutex.writeLock().lock();

		try
		{
		    m_sipHashId = Miscellaneous.
			byteArrayAsHexStringDelimited(bytes, '-', 4).
			toUpperCase();
		}
		catch(Exception exception)
		{
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

    public void exit()
    {
	reset();
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
	    if(m_encryptionKeyBytes != null)
		Arrays.fill(m_encryptionKeyBytes, (byte) 0);

	    m_encryptionKeyBytes = null;
	    m_encryptionKeyRandom = randomBytes(CIPHER_KEY_LENGTH);
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
	    if(m_macKeyBytes != null)
		Arrays.fill(m_macKeyBytes, (byte) 0);

	    m_macKeyBytes = null;
	    m_macKeyRandom = randomBytes(HASH_KEY_LENGTH);
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
	catch(Exception exception)
	{
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
	catch(Exception exception)
	{
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
	catch(Exception exception)
	{
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
	catch(Exception exception)
	{
	}
	finally
	{
	    m_sipHashIdDigestMutex.writeLock().unlock();
	}

	m_sipHashIdMutex.writeLock().lock();

	try
	{
	    m_sipHashId = DEFAULT_SIPHASH_ID;
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
	catch(Exception exception)
	{
	}
	finally
	{
	    m_sipHashMacKeyMutex.writeLock().unlock();
	}
    }

    public void resetPKI()
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

	m_identityMutex.writeLock().lock();

	try
	{
	    m_identity = null;
	}
	finally
	{
	    m_identityMutex.writeLock().unlock();
	}

	m_ozoneEncryptionKeyMutex.writeLock().lock();

	try
	{
	    if(m_ozoneEncryptionKey != null)
		Arrays.fill(m_ozoneEncryptionKey, (byte) 0);

	    m_ozoneEncryptionKey = null;
	}
	catch(Exception exception)
	{
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
	catch(Exception exception)
	{
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
	catch(Exception exception)
	{
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
	catch(Exception exception)
	{
	}
	finally
	{
	    m_sipHashIdDigestMutex.writeLock().unlock();
	}

	m_sipHashIdMutex.writeLock().lock();

	try
	{
	    m_sipHashId = DEFAULT_SIPHASH_ID;
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
	catch(Exception exception)
	{
	}
	finally
	{
	    m_sipHashMacKeyMutex.writeLock().unlock();
	}
    }

    public void setChatEncryptionPublicKeyAlgorithm(String algorithm)
    {
	m_chatEncryptionPublicKeyPairMutex.writeLock().lock();

	try
	{
	    m_chatEncryptionPublicKeyAlgorithm = algorithm;
	}
	finally
	{
	    m_chatEncryptionPublicKeyPairMutex.writeLock().unlock();
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
	catch(Exception exception)
	{
	    m_chatEncryptionPublicKeyPair = null;
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
	catch(Exception exception)
	{
	    m_chatSignaturePublicKeyPair = null;
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
	    m_encryptionKeyBytes = xor
		(key.getEncoded(), m_encryptionKeyRandom);
	}
	finally
	{
	    m_encryptionKeyMutex.writeLock().unlock();
	}

	destroy(key);
    }

    public void setMacKey(SecretKey key)
    {
	m_macKeyMutex.writeLock().lock();

	try
	{
	    m_macKeyBytes = xor(key.getEncoded(), m_macKeyRandom);
	}
	finally
	{
	    m_macKeyMutex.writeLock().unlock();
	}

	destroy(key);
    }

    public void setOzoneEncryptionKey(byte bytes[])
    {
	m_ozoneEncryptionKeyMutex.writeLock().lock();

	try
	{
	    if(bytes != null && bytes.length == CIPHER_KEY_LENGTH)
		m_ozoneEncryptionKey = bytes;
	    else
	    {
		if(m_ozoneEncryptionKey != null)
		    Arrays.fill(m_ozoneEncryptionKey, (byte) 0);

		m_ozoneEncryptionKey = null;
	    }
	}
	catch(Exception exception)
	{
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
	    if(bytes != null && bytes.length == HASH_KEY_LENGTH)
		m_ozoneMacKey = bytes;
	    else
	    {
		if(m_ozoneMacKey != null)
		    Arrays.fill(m_ozoneMacKey, (byte) 0);

		m_ozoneMacKey = null;
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_ozoneMacKeyMutex.writeLock().unlock();
	}
    }
}
