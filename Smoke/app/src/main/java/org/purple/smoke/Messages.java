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
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Messages
{
    private final static SimpleDateFormat s_fireSimpleDateFormat = new
	SimpleDateFormat("MMddyyyyHHmmss", Locale.getDefault());
    private final static int ETAG_LENGTH = 32;
    public final static String EOM = "\r\n\r\n\r\n";
    public final static String AUTHENTICATE_MESSAGE_TYPE = "0097b";
    public final static String FIRE_CHAT_MESSAGE_TYPE = "0040b";
    public final static String FIRE_STATUS_MESSAGE_TYPE = "0040a";
    public final static String IDENTITY_MESSAGE_TYPE = "0095a";
    public final static byte[] ARSON_CALL_HALF_AND_HALF_TAGS =
	new byte[] {0x02, 0x03}; // Be careful of Steam tags.
    public final static byte[] CALL_HALF_AND_HALF_TAGS =
	new byte[] {0x00, 0x01}; // Be careful of Steam tags.
    public final static byte[] CHAT_KEY_TYPE = new byte[] {0x00};
    public final static byte[] CHAT_MESSAGE_RETRIEVAL = new byte[] {0x00};
    public final static byte[] CHAT_MESSAGE_TYPE = new byte[] {0x00};
    public final static byte[] CHAT_STATUS_MESSAGE_TYPE = new byte[] {0x01};
    public final static byte[] JUGGERNAUT_TYPE = new byte[] {0x03};
    public final static byte MCELIECE_FUJISAKI_11_50 = 0x01;
    public final static byte MCELIECE_FUJISAKI_12_68 = 0x02;
    public final static byte MCELIECE_FUJISAKI_13_118 = 0x03;
    public final static byte MCELIECE_POINTCHEVAL = 0x04;
    public final static byte[] MESSAGE_READ_SMOKESTACK = new byte[] {0x04};
    public final static byte[] MESSAGE_READ_TYPE = new byte[] {0x02};
    public final static byte[] PKP_MESSAGE_REQUEST = new byte[] {0x01};
    public final static byte[] SHARE_SIPHASH_ID = new byte[] {0x02};
    public final static byte[] STEAM_KEY_EXCHANGE =
	new byte[] {0x04, 0x05}; // Be careful of calling tags.
    public final static byte[] STEAM_SHARE = new byte[] {0x06, 0x07};
    public final static int CALL_GROUP_TWO_ELEMENT_COUNT = 6; /*
							      ** The first
							      ** byte is not
							      ** considered.
							      */
    public final static int CHAT_GROUP_TWO_ELEMENT_COUNT = 6; /*
							      ** The first
							      ** byte is not
							      ** considered.
							      */
    public final static int EPKS_GROUP_ONE_ELEMENT_COUNT = 7;
    public final static int STEAM_KEY_EXCHANGE_GROUP_TWO_ELEMENT_COUNT =
	9; // The first byte is not considered.

    public static String authenticateMessage(Cryptography cryptography,
					     String string)
    {
	if(cryptography == null || string == null || string.length() == 0)
	    return "";

	try
	{
	    byte[] random = Cryptography.randomBytes
		(Cryptography.HASH_KEY_LENGTH);
	    byte[] signature = null;

	    signature = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(random,
				cryptography.chatSignaturePublicKeyDigest(),
				string.getBytes()));

	    if(signature == null)
		return "";

	    StringBuilder results = new StringBuilder();

	    results.append("POST HTTP/1.1\r\n");
	    results.append("Content-Length: %1\r\n");
	    results.append
		("Content-Type: application/x-www-form-urlencoded\r\n");
	    results.append("\r\n");
	    results.append("type=");
	    results.append(AUTHENTICATE_MESSAGE_TYPE);
	    results.append("&content=%2\r\n\r\n\r\n");

	    String base64 = Base64.encodeToString
		(Miscellaneous.
		 joinByteArrays(random,
				cryptography.chatSignaturePublicKeyDigest(),
				signature),
		 Base64.NO_WRAP);
	    int indexOf = results.indexOf("%1");
	    int length = base64.length() +
		("type=" +
		 AUTHENTICATE_MESSAGE_TYPE +
		 "&content=\r\n\r\n\r\n").length();

	    results = results.replace
		(indexOf, indexOf + 2, String.valueOf(length));
	    indexOf = results.indexOf("%2");
	    results = results.replace(indexOf, indexOf + 2, base64);
	    return results.toString();
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public static String bytesToMessageString(byte[] bytes)
    {
	if(bytes == null || bytes.length == 0)
	    return "";

	try
	{
	    StringBuilder results = new StringBuilder();

	    results.append("POST HTTP/1.1\r\n");
	    results.append("Content-Length: %1\r\n");
	    results.append
		("Content-Type: application/x-www-form-urlencoded\r\n");
	    results.append("ETag: ");
	    results.append
		(Miscellaneous.
		 byteArrayAsHexString(Cryptography.
				      randomBytes(ETAG_LENGTH / 2)));
	    results.append("\r\n\r\n");
	    results.append("content=%2\r\n");
	    results.append("\r\n\r\n");

	    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
	    int indexOf = results.indexOf("%1");
	    int length = base64.length() + "content=\r\n\r\n\r\n".length();

	    results = results.replace
		(indexOf, indexOf + 2, String.valueOf(length));
	    indexOf = results.indexOf("%2");
	    results = results.replace(indexOf, indexOf + 2, base64);
	    return results.toString();
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public static String bytesToMessageStringNonBase64(byte[] bytes)
    {
	if(bytes == null || bytes.length == 0)
	    return "";

	try
	{
	    StringBuilder results = new StringBuilder();

	    results.append("POST HTTP/1.1\r\n");
	    results.append("Content-Length: %1\r\n");
	    results.append
		("Content-Type: application/x-www-form-urlencoded\r\n");
	    results.append("ETag: ");
	    results.append
		(Miscellaneous.
		 byteArrayAsHexString(Cryptography.
				      randomBytes(ETAG_LENGTH / 2)));
	    results.append("\r\n\r\n");
	    results.append("content=%2\r\n");
	    results.append("\r\n\r\n");

	    int indexOf = results.indexOf("%1");
	    int length = bytes.length + "content=\r\n\r\n\r\n".length();

	    results = results.replace
		(indexOf, indexOf + 2, String.valueOf(length));
	    indexOf = results.indexOf("%2");
	    results = results.replace
		(indexOf,
		 indexOf + 2,
		 new String(bytes, StandardCharsets.UTF_8));
	    return results.toString();
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public static String identityMessage(byte[] bytes)
    {
	if(bytes == null || bytes.length == 0)
	    return "";

	try
	{
	    StringBuilder results = new StringBuilder();

	    results.append("POST HTTP/1.1\r\n");
	    results.append("Content-Length: %1\r\n");
	    results.append
		("Content-Type: application/x-www-form-urlencoded\r\n");
	    results.append("\r\n");
	    results.append("type=");
	    results.append(IDENTITY_MESSAGE_TYPE);
	    results.append("&content=%2;sha-512\r\n\r\n\r\n");

	    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
	    int indexOf = results.indexOf("%1");
	    int length = base64.length() +
		("type=" +
		 IDENTITY_MESSAGE_TYPE +
		 "&content=;sha-512\r\n\r\n\r\n").length();

	    results = results.replace
		(indexOf, indexOf + 2, String.valueOf(length));
	    indexOf = results.indexOf("%2");
	    results = results.replace(indexOf, indexOf + 2, base64);
	    return results.toString();
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public static String replaceETag(String message)
    {
	if(message == null)
	    return "";

	int indexOf = message.indexOf("ETag: ");

	if(indexOf >= 0)
	{
	    StringBuilder stringBuilder = new StringBuilder(message);

	    stringBuilder = stringBuilder.replace
		(indexOf,
		 ETAG_LENGTH + indexOf + 6,
		 "ETag: " +
		 Miscellaneous.
		 byteArrayAsHexString(Cryptography.
				      randomBytes(ETAG_LENGTH / 2)));
	    return stringBuilder.toString();
	}

	return message;
    }

    public static String stripMessage(String message)
    {
	if(message == null)
	    return "";

	/*
	** Remove Smoke-specific leading and trailing data.
	*/

	int indexOf = message.indexOf("content=");

	if(indexOf >= 0)
	    message = message.substring(indexOf + 8);

	return message.trim();
    }

    public static byte[] callMessage(Cryptography cryptography,
				     String sipHashId,
				     byte[] keyStream,
				     byte publicKeyType,
				     byte tag)
    {
	if(cryptography == null || keyStream == null || keyStream.length == 0)
	    return null;

	try
	{
	    /*
	    ** [ Public Key Encryption ]
	    */

	    byte[] aesKey = Cryptography.aes256KeyBytes();

	    if(aesKey == null)
		return null;

	    byte[] shaKey = Cryptography.sha512KeyBytes();

	    if(shaKey == null)
		return null;

	    PublicKey publicKey = Database.getInstance().
		publicEncryptionKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    byte[] pki = Cryptography.pkiEncrypt
		(publicKey,
		 Database.getInstance().
		 publicKeyEncryptionAlgorithm(cryptography, sipHashId),
		 Miscellaneous.joinByteArrays(aesKey, shaKey));

	    if(pki == null)
		return null;

	    StringBuilder stringBuilder = new StringBuilder();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(System.
						       currentTimeMillis()),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Ephemeral Public Key ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(keyStream, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Ephemeral Public Key Type ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(new byte[] {publicKeyType},
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Identity ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(cryptography.identity(),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Encryption Public Key Digest ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(cryptography.
				       chatEncryptionPublicKeyDigest(),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(aesKey,
				shaKey,
				new byte[] {tag},
				stringBuilder.toString().getBytes(),
				Cryptography.sha512(publicKey.getEncoded())));

	    if(signature == null)
		return null;

	    stringBuilder.append
		(Base64.encodeToString(signature, Base64.NO_WRAP));

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.
		 joinByteArrays(new byte[] {tag},
				stringBuilder.toString().getBytes()),
		 aesKey);

	    if(ciphertext == null)
		return null;

	    stringBuilder.delete(0, stringBuilder.length());

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext), shaKey);

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays
		(pki, ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] chatMessage(Cryptography cryptography,
				     String message,
				     String sipHashId,
				     byte[] attachment,
				     byte[] destinationKey,
				     byte[] keyStream,
				     byte[] messageIdentity,
				     long sequence,
				     long timestamp)
    {
	if(cryptography == null ||
	   keyStream == null ||
	   keyStream.length == 0 ||
	   messageIdentity == null ||
	   messageIdentity.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    PublicKey publicKey = Database.getInstance().
		publicEncryptionKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    /*
	    ** [ PKI ]
	    */

	    byte[] pki = Cryptography.pkiEncrypt
		(publicKey,
		 Database.getInstance().
		 publicKeyEncryptionAlgorithm(cryptography, sipHashId),
		 cryptography.chatEncryptionPublicKeyDigest());

	    if(pki == null)
		return null;

	    StringBuilder stringBuilder = new StringBuilder();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(Miscellaneous.longToByteArray(timestamp),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Message ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(message.getBytes(StandardCharsets.UTF_8),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Sequence ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(sequence),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Attachment ]
	    */

	    if(attachment != null)
		stringBuilder.append
		    (Base64.
		     encodeToString(Miscellaneous.compressed(attachment),
				    Base64.NO_WRAP));
	    else
		stringBuilder.append
		    (Base64.
		     encodeToString(Miscellaneous.compressed(new byte[1]),
				    Base64.NO_WRAP));

	    stringBuilder.append("\n");

	    /*
	    ** [ Message Identity ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(messageIdentity, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = null;

	    if(Database.getInstance().readParticipantOptions(cryptography,
							     sipHashId).
	       contains("optional_signatures = false"))
		signature = cryptography.signViaChatSignature
		    (Miscellaneous.
		     joinByteArrays(cryptography.
				    chatEncryptionPublicKeyDigest(),
				    CHAT_MESSAGE_TYPE,
				    stringBuilder.toString().getBytes(),
				    Cryptography.sha512(publicKey.
							getEncoded())));
	    else
		signature = new byte[1];

	    if(signature == null)
		return null;

	    stringBuilder.append
		(Base64.encodeToString(signature, Base64.NO_WRAP));

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.
		 joinByteArrays(CHAT_MESSAGE_TYPE,
				stringBuilder.toString().getBytes()),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    stringBuilder.delete(0, stringBuilder.length());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext),
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    keyStream.length));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    if(destinationKey != null)
	    {
		byte[] destination = Cryptography.hmac
		    (Miscellaneous.joinByteArrays(pki, ciphertext, hmac),
		     destinationKey);

		return Miscellaneous.joinByteArrays
		    (pki, ciphertext, hmac, destination);
	    }
	    else
		return Miscellaneous.joinByteArrays
		    /*
		    ** The SipHash ID will be removed by the Neighbor object
		    ** before the message is created.
		    */

		    (pki,
		     ciphertext,
		     hmac,
		     sipHashId.getBytes(StandardCharsets.UTF_8));
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] chatMessageRetrieval(Cryptography cryptography)
    {
	if(cryptography == null)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    byte[] bytes = Miscellaneous.joinByteArrays
		(
		 /*
		 ** [ A Byte ]
		 */

		 CHAT_MESSAGE_RETRIEVAL,

		 /*
		 ** [ A Timestamp ]
		 */

		 Miscellaneous.longToByteArray(System.currentTimeMillis()),

		 /*
		 ** [ Some Identity ]
		 */

		 Kernel.getInstance().messageRetrievalIdentity(),

		 /*
		 ** [ Encryption Public Key Digest ]
		 */

		 cryptography.chatEncryptionPublicKeyDigest());

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = cryptography.signViaChatSignature(bytes);

	    if(signature == null)
		return null;

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.joinByteArrays(bytes, signature),
		 cryptography.ozoneEncryptionKey());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(ciphertext, cryptography.ozoneMacKey());

	    if(hmac == null)
		return null;

	    return Miscellaneous.joinByteArrays(ciphertext, hmac);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] chatStatus(Cryptography cryptography,
				    String sipHashId,
				    byte[] keyStream)
    {
	if(cryptography == null || keyStream == null || keyStream.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    PublicKey publicKey = Database.getInstance().
		publicEncryptionKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    /*
	    ** [ PKI ]
	    */

	    byte[] pki = Cryptography.pkiEncrypt
		(publicKey,
		 Database.getInstance().
		 publicKeyEncryptionAlgorithm(cryptography, sipHashId),
		 cryptography.chatEncryptionPublicKeyDigest());

	    if(pki == null)
		return null;

	    byte[] bytes = Miscellaneous.joinByteArrays
		(
		 /*
		 ** [ A Byte ]
		 */

		 CHAT_STATUS_MESSAGE_TYPE,

		 /*
		 ** [ A Timestamp ]
		 */

		 Miscellaneous.longToByteArray(System.currentTimeMillis()),

		 /*
		 ** [ Status ]
		 */

		 new byte[] {0x00});

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = null;

	    if(Database.getInstance().readParticipantOptions(cryptography,
							     sipHashId).
	       contains("optional_signatures = false"))
		signature = cryptography.signViaChatSignature
		    (Miscellaneous.
		     joinByteArrays(cryptography.
				    chatEncryptionPublicKeyDigest(),
				    bytes,
				    Cryptography.sha512(publicKey.
							getEncoded())));
	    else
		signature = new byte[1];

	    if(signature == null)
		return null;

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.joinByteArrays(bytes, signature),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext),
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    keyStream.length));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays
		(pki, ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] epksMessage(Cryptography cryptography,
				     String sipHashId,
				     byte[] keyStream,
				     byte[] keyType)
    {
	if(cryptography == null ||
	   keyStream == null ||
	   keyStream.length == 0 ||
	   keyType == null ||
	   keyType.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(System.
						       currentTimeMillis()),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Key Type ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(keyType, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Sender's Smoke Identity ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(cryptography.sipHashId().
				       getBytes(StandardCharsets.UTF_8),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Encryption and Signature Public Keys ]
	    */

	    PublicKey encryptionKey = cryptography.chatEncryptionPublicKey();
	    PublicKey signatureKey = cryptography.chatSignaturePublicKey();

	    if(encryptionKey == null || signatureKey == null)
		return null;

	    byte[] bytes = null;

	    /*
	    ** [ Encryption Public Key Signature ]
	    */

	    if(!encryptionKey.getAlgorithm().equals("McEliece-CCA2"))
	    {
		bytes = cryptography.signViaChatEncryption
		    (Miscellaneous.
		     joinByteArrays(cryptography.sipHashId().
				    getBytes(StandardCharsets.UTF_8),
				    encryptionKey.getEncoded(),
				    signatureKey.getEncoded()));

		if(bytes == null)
		    return null;
	    }
	    else
	    {
		bytes = new byte[1];

		if(cryptography.chatEncryptionPublicKeyAlgorithm().
		   startsWith("McEliece-Fujisaki (11"))
		    bytes[0] = MCELIECE_FUJISAKI_11_50;
		else if(cryptography.chatEncryptionPublicKeyAlgorithm().
			startsWith("McEliece-Fujisaki (12"))
		    bytes[0] = MCELIECE_FUJISAKI_12_68;
		else if(cryptography.chatEncryptionPublicKeyAlgorithm().
			startsWith("McEliece-Fujisaki (13"))
		    bytes[0] = MCELIECE_FUJISAKI_13_118;
		else
		    bytes[0] = MCELIECE_POINTCHEVAL;
	    }

	    stringBuilder.
		append(Base64.encodeToString(encryptionKey.getEncoded(),
					     Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append(Base64.encodeToString(bytes, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Signature Public Key Signature ]
	    */

	    bytes = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(cryptography.sipHashId().
				getBytes(StandardCharsets.UTF_8),
				encryptionKey.getEncoded(),
				signatureKey.getEncoded()));

	    if(bytes == null)
		return null;

	    stringBuilder.
		append(Base64.encodeToString(signatureKey.getEncoded(),
					     Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append(Base64.encodeToString(bytes, Base64.NO_WRAP));

	    byte[] ciphertext = Cryptography.encrypt
		(stringBuilder.toString().getBytes(),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    stringBuilder.delete(0, stringBuilder.length());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(ciphertext,
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    keyStream.length));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays(ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] epksMessage(String encryptionAlgorithm,
				     String sipHashId,
				     byte[] encryptionPublicKey,
				     byte[] signaturePublicKey,
				     byte[] keyStream,
				     byte[] keyType)
    {
	if(encryptionPublicKey == null ||
	   encryptionPublicKey.length == 0 ||
	   keyStream == null ||
	   keyStream.length == 0 ||
	   keyType == null ||
	   keyType.length == 0 ||
	   signaturePublicKey == null ||
	   signaturePublicKey.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(System.
						       currentTimeMillis()),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Key Type ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(keyType, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Sender's Smoke Identity ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(sipHashId.
				       getBytes(StandardCharsets.UTF_8),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    byte[] bytes = new byte[1]; // Artificial signatures.

	    if(encryptionAlgorithm.startsWith("McEliece-Fujisaki (11"))
		bytes[0] = MCELIECE_FUJISAKI_11_50;
	    else if(encryptionAlgorithm.startsWith("McEliece-Fujisaki (12"))
		bytes[0] = MCELIECE_FUJISAKI_12_68;
	    else if(encryptionAlgorithm.startsWith("McEliece-Fujisaki (13"))
		bytes[0] = MCELIECE_FUJISAKI_13_118;
	    else if(encryptionAlgorithm.startsWith("McEliece-Pointcheval"))
		bytes[0] = MCELIECE_POINTCHEVAL;
	    else
		bytes[0] = 0;

	    /*
	    ** [ Encryption Public Key ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(encryptionPublicKey, Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append(Base64.encodeToString(bytes, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Signature Public Key ]
	    */

	    bytes[0] = 0; // Artificial signatures.
	    stringBuilder.append
		(Base64.encodeToString(signaturePublicKey, Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append(Base64.encodeToString(bytes, Base64.NO_WRAP));

	    byte[] ciphertext = Cryptography.encrypt
		(stringBuilder.toString().getBytes(),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    stringBuilder.delete(0, stringBuilder.length());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(ciphertext,
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    keyStream.length));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays(ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] fireMessage(Cryptography cryptography,
				     String id,
				     String message,
				     String name,
				     byte[] keyStream)
    {
	if(cryptography == null || keyStream == null || keyStream.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 79] - SHA-384 HMAC Key
	** [80 ... N] - Destination SHA-512 HMAC Key
	*/

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append
		(Base64.
		 encodeToString(FIRE_CHAT_MESSAGE_TYPE.
				getBytes(StandardCharsets.ISO_8859_1),
				Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    if(name.trim().isEmpty())
		stringBuilder.append
		    (Base64.encodeToString("unknown".
					   getBytes(StandardCharsets.UTF_8),
					   Base64.NO_WRAP));
	    else
		stringBuilder.append
		    (Base64.encodeToString(name.trim().
					   getBytes(StandardCharsets.UTF_8),
					   Base64.NO_WRAP));

	    stringBuilder.append("\n");
	    stringBuilder.append
		(Base64.encodeToString(id.getBytes(StandardCharsets.ISO_8859_1),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Base64.encodeToString(message.getBytes(StandardCharsets.UTF_8),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    TimeZone utc = TimeZone.getTimeZone("UTC");

	    s_fireSimpleDateFormat.setTimeZone(utc);
	    stringBuilder.append
		(Base64.
		 encodeToString(s_fireSimpleDateFormat.
				format(new Date(System.currentTimeMillis())).
				getBytes(StandardCharsets.ISO_8859_1),
				Base64.NO_WRAP));

	    byte[] ciphertext = Cryptography.encryptFire
		(stringBuilder.toString().getBytes(StandardCharsets.ISO_8859_1),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmacFire
		(ciphertext,
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    Cryptography.CIPHER_KEY_LENGTH +
				    Cryptography.FIRE_HASH_KEY_LENGTH));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(ciphertext, hmac),
		 Cryptography.sha512(Arrays.copyOfRange(keyStream,
							Cryptography.
							CIPHER_KEY_LENGTH +
							Cryptography.
							FIRE_HASH_KEY_LENGTH,
							keyStream.length)));

	    stringBuilder.delete(0, stringBuilder.length());
	    stringBuilder.append
		(Base64.encodeToString(ciphertext, Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Base64.encodeToString(hmac, Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Base64.encodeToString(destination, Base64.NO_WRAP));
	    return stringBuilder.toString().getBytes
		(StandardCharsets.ISO_8859_1);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] fireStatus(Cryptography cryptography,
				    String id,
				    String name,
				    byte[] keyStream)
    {
	if(cryptography == null || keyStream == null || keyStream.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 79] - SHA-384 HMAC Key
	** [80 ... N] - Destination SHA-512 HMAC Key
	*/

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append
		(Base64.
		 encodeToString(FIRE_STATUS_MESSAGE_TYPE.
				getBytes(StandardCharsets.ISO_8859_1),
				Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    if(name.trim().isEmpty())
		stringBuilder.append
		    (Base64.encodeToString("unknown".
					   getBytes(StandardCharsets.UTF_8),
					   Base64.NO_WRAP));
	    else
		stringBuilder.append
		    (Base64.encodeToString(name.trim().
					   getBytes(StandardCharsets.UTF_8),
					   Base64.NO_WRAP));

	    stringBuilder.append("\n");
	    stringBuilder.append
		(Base64.encodeToString(id.getBytes(StandardCharsets.ISO_8859_1),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    TimeZone utc = TimeZone.getTimeZone("UTC");

	    s_fireSimpleDateFormat.setTimeZone(utc);
	    stringBuilder.append
		(Base64.
		 encodeToString(s_fireSimpleDateFormat.
				format(new Date(System.currentTimeMillis())).
				getBytes(StandardCharsets.ISO_8859_1),
				Base64.NO_WRAP));

	    byte[] ciphertext = Cryptography.encryptFire
		(stringBuilder.toString().getBytes(StandardCharsets.ISO_8859_1),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmacFire
		(ciphertext,
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    Cryptography.CIPHER_KEY_LENGTH +
				    Cryptography.FIRE_HASH_KEY_LENGTH));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(ciphertext, hmac),
		 Cryptography.sha512(Arrays.copyOfRange(keyStream,
							Cryptography.
							CIPHER_KEY_LENGTH +
							Cryptography.
							FIRE_HASH_KEY_LENGTH,
							keyStream.length)));

	    stringBuilder.delete(0, stringBuilder.length());
	    stringBuilder.append
		(Base64.encodeToString(ciphertext, Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Base64.encodeToString(hmac, Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Base64.encodeToString(destination, Base64.NO_WRAP));
	    return stringBuilder.toString().
		getBytes(StandardCharsets.ISO_8859_1);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] juggernautMessage(Cryptography cryptography,
					   String sipHashId,
					   byte[] bytes,
					   byte[] keyStream)
    {
	if(bytes == null ||
	   bytes.length == 0 ||
	   cryptography == null ||
	   keyStream == null ||
	   keyStream.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    PublicKey publicKey = Database.getInstance().
		publicEncryptionKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    /*
	    ** [ PKI ]
	    */

	    byte[] pki = Cryptography.pkiEncrypt
		(publicKey,
		 Database.getInstance().
		 publicKeyEncryptionAlgorithm(cryptography, sipHashId),
		 cryptography.chatEncryptionPublicKeyDigest());

	    if(pki == null)
		return null;

	    StringBuilder stringBuilder = new StringBuilder();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuilder.append
		(Base64.
		 encodeToString(Miscellaneous.
				longToByteArray(System.currentTimeMillis()),
				Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Payload ]
	    */

	    stringBuilder.append(Base64.encodeToString(bytes, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(cryptography.
				chatEncryptionPublicKeyDigest(),
				JUGGERNAUT_TYPE,
				stringBuilder.toString().getBytes(),
				Cryptography.sha512(publicKey.getEncoded())));

	    if(signature == null)
		return null;

	    stringBuilder.append
		(Base64.encodeToString(signature, Base64.NO_WRAP));

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.
		 joinByteArrays(JUGGERNAUT_TYPE,
				stringBuilder.toString().getBytes()),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    stringBuilder.delete(0, stringBuilder.length());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext),
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    keyStream.length));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays
		(pki, ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] messageRead(Cryptography cryptography,
				     String sipHashId,
				     byte[] keyStream,
				     byte[] messageIdentity)
    {
	if(cryptography == null ||
	   keyStream == null ||
	   keyStream.length == 0 ||
	   messageIdentity == null ||
	   messageIdentity.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    PublicKey publicKey = Database.getInstance().
		publicEncryptionKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    /*
	    ** [ PKI ]
	    */

	    byte[] pki = Cryptography.pkiEncrypt
		(publicKey,
		 Database.getInstance().
		 publicKeyEncryptionAlgorithm(cryptography, sipHashId),
		 cryptography.chatEncryptionPublicKeyDigest());

	    if(pki == null)
		return null;

	    byte[] bytes = Miscellaneous.joinByteArrays
		(
		 /*
		 ** [ A Byte ]
		 */

		 MESSAGE_READ_TYPE,

		 /*
		 ** [ Message Identity ]
		 */

		 messageIdentity);

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(cryptography.
				chatEncryptionPublicKeyDigest(),
				bytes,
				Cryptography.sha512(publicKey.getEncoded())));

	    if(signature == null)
		return null;

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.joinByteArrays(bytes, signature),
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext),
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    keyStream.length));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays
		(pki, ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] messageRead(Cryptography cryptography,
				     byte[] messageIdentity)
    {
	if(cryptography == null ||
	   messageIdentity == null ||
	   messageIdentity.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    byte[] bytes = Miscellaneous.joinByteArrays
		(
		 /*
		 ** [ A Byte ]
		 */

		 MESSAGE_READ_SMOKESTACK,

		 /*
		 ** [ A Timestamp ]
		 */

		 Miscellaneous.longToByteArray(System.currentTimeMillis()),

		 /*
		 ** [ Message Identity ]
		 */

		 messageIdentity,

		 /*
		 ** [ Encryption Public Key Digest ]
		 */

		 cryptography.chatEncryptionPublicKeyDigest());

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = cryptography.signViaChatSignature(bytes);

	    if(signature == null)
		return null;

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.joinByteArrays(bytes, signature),
		 cryptography.ozoneEncryptionKey());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(ciphertext, cryptography.ozoneMacKey());

	    if(hmac == null)
		return null;

	    return Miscellaneous.joinByteArrays(ciphertext, hmac);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] pkpRequestMessage(Cryptography cryptography,
					   String requestedSipHashId)
    {
	if(cryptography == null)
	    return null;

	try
	{
	    byte[] bytes = Miscellaneous.joinByteArrays
		(
		 /*
		 ** [ A Byte ]
		 */

		 PKP_MESSAGE_REQUEST,

		 /*
		 ** [ A Timestamp ]
		 */

		 Miscellaneous.longToByteArray(System.currentTimeMillis()),

		 /*
		 ** [ Destination SipHash Identity ]
		 */

		 cryptography.sipHashId().getBytes(StandardCharsets.UTF_8),

		 /*
		 ** [ Requested SipHash Identity ]
		 */

		 requestedSipHashId.getBytes(StandardCharsets.UTF_8));

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(bytes, cryptography.ozoneEncryptionKey());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(ciphertext, cryptography.ozoneMacKey());

	    if(hmac == null)
		return null;

	    return Miscellaneous.joinByteArrays(ciphertext, hmac);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] shareSipHashIdMessage(Cryptography cryptography,
					       String sipHashId,
					       long identity)
    {
	if(cryptography == null)
	    return null;

	try
	{
	    byte[] bytes = Miscellaneous.joinByteArrays
		(
		 /*
		 ** [ A Byte ]
		 */

		 SHARE_SIPHASH_ID,

		 /*
		 ** [ A Timestamp ]
		 */

		 Miscellaneous.longToByteArray(System.currentTimeMillis()),

		 /*
		 ** [ SipHash Identity ]
		 */

		 sipHashId.getBytes(StandardCharsets.UTF_8),

		 /*
		 ** [ Temporary Identity ]
		 */

		 Miscellaneous.longToByteArray(identity));

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(bytes, cryptography.ozoneEncryptionKey());

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(ciphertext, cryptography.ozoneMacKey());

	    if(hmac == null)
		return null;

	    return Miscellaneous.joinByteArrays(ciphertext, hmac);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] steamCall(Cryptography cryptography,
				   String fileName,
				   String sipHashId,
				   byte[] fileDigest,
				   byte[] fileIdentity,
				   byte[] keyStream,
				   byte publicKeyType,
				   byte tag,
				   long fileSize)
    {
	if(cryptography == null ||
	   fileDigest == null ||
	   fileDigest.length == 0 ||
	   fileIdentity == null ||
	   fileIdentity.length == 0 ||
	   fileName.isEmpty() ||
	   keyStream == null ||
	   keyStream.length == 0)
	    return null;

	try
	{
	    /*
	    ** [ Public Key Encryption ]
	    */

	    byte[] aesKey = Cryptography.aes256KeyBytes();

	    if(aesKey == null)
		return null;

	    byte[] shaKey = Cryptography.sha512KeyBytes();

	    if(shaKey == null)
		return null;

	    PublicKey publicKey = Database.getInstance().
		publicEncryptionKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    byte[] pki = Cryptography.pkiEncrypt
		(publicKey,
		 Database.getInstance().
		 publicKeyEncryptionAlgorithm(cryptography, sipHashId),
		 Miscellaneous.joinByteArrays(aesKey, shaKey));

	    if(pki == null)
		return null;

	    StringBuilder stringBuilder = new StringBuilder();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(System.
						       currentTimeMillis()),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Ephemeral Public Key ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(keyStream, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Ephemeral Public Key Type ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(new byte[] {publicKeyType},
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ File Digest ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(fileDigest, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ File Identity ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(fileIdentity, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ File Name ]
	    */

	    stringBuilder.append
		(Base64.
		 encodeToString(fileName.getBytes(StandardCharsets.UTF_8),
				Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ File Size ]
	    */

	    stringBuilder.append
		(Base64.
		 encodeToString(Miscellaneous.longToByteArray(fileSize),
				Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Encryption Public Key Digest ]
	    */

	    stringBuilder.append
		(Base64.encodeToString(cryptography.
				       chatEncryptionPublicKeyDigest(),
				       Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte[] signature = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(aesKey,
				shaKey,
				new byte[] {tag},
				stringBuilder.toString().getBytes(),
				Cryptography.sha512(publicKey.getEncoded())));

	    if(signature == null)
		return null;

	    stringBuilder.append
		(Base64.encodeToString(signature, Base64.NO_WRAP));

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(Miscellaneous.
		 joinByteArrays(new byte[] {tag},
				stringBuilder.toString().getBytes()),
		 aesKey);

	    if(ciphertext == null)
		return null;

	    stringBuilder.delete(0, stringBuilder.length());

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext), shaKey);

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays
		(pki, ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] steamShare(Cryptography cryptography,
				    String sipHashId,
				    byte[] fileIdentity,
				    byte[] keyStream,
				    byte[] packet,
				    byte tag,
				    long fileOffset)
    {
	if(cryptography == null ||
	   fileIdentity == null ||
	   fileIdentity.length == 0 ||
	   keyStream == null ||
	   keyStream.length == 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    /*
	    ** [ Public Key Encryption ]
	    */

	    PublicKey publicKey = Database.getInstance().
		publicEncryptionKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    byte[] pki = Cryptography.pkiEncrypt
		(publicKey,
		 Database.getInstance().
		 publicKeyEncryptionAlgorithm(cryptography, sipHashId),
		 fileIdentity);

	    if(pki == null)
		return null;

	    byte[] bytes = Miscellaneous.joinByteArrays
		(
		 /*
		 ** [ A Tag ]
		 */

		 new byte[] {tag},

		 /*
		 ** [ A Timestamp ]
		 */

		 Miscellaneous.longToByteArray(System.currentTimeMillis()),

		 /*
		 ** [ File Offset ]
		 */

		 Miscellaneous.longToByteArray(fileOffset),

		 /*
		 ** [ File Packet ]
		 */

		 packet);

	    /*
	    ** [ Ciphertext ]
	    */

	    byte[] ciphertext = Cryptography.encrypt
		(bytes,
		 Arrays.copyOfRange(keyStream,
				    0,
				    Cryptography.CIPHER_KEY_LENGTH));

	    if(ciphertext == null)
		return null;

	    /*
	    ** [ HMAC ]
	    */

	    byte[] hmac = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext),
		 Arrays.copyOfRange(keyStream,
				    Cryptography.CIPHER_KEY_LENGTH,
				    keyStream.length));

	    if(hmac == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte[] destination = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pki, ciphertext, hmac),
		 Cryptography.
		 sha512(sipHashId.getBytes(StandardCharsets.UTF_8)));

	    return Miscellaneous.joinByteArrays
		(pki, ciphertext, hmac, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }
}
