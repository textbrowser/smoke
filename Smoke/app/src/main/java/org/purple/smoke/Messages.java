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
import java.security.PublicKey;
import java.util.Arrays;

public class Messages
{
    public final static byte CALL_HALF_AND_HALF_TAGS[] =
	new byte[] {0x00, 0x01};
    public final static byte CHAT_KEY_TYPE[] = new byte[] {0x00};
    public final static byte CHAT_MESSAGE_TYPE[] = new byte[] {0x00};
    public final static byte CHAT_STATUS_MESSAGE_TYPE[] = new byte[] {0x01};
    public final static int CHAT_GROUP_TWO_ELEMENT_COUNT = 4;
    public final static int EPKS_GROUP_ONE_ELEMENT_COUNT = 6;

    public static String bytesToMessageString(byte bytes[])
    {
	StringBuilder results = new StringBuilder();

	results.append("POST HTTP/1.1\r\n");
	results.append("Content-Type: application/x-www-form-urlencoded\r\n");
	results.append("Content-Length: %1\r\n");
	results.append("\r\n");
	results.append("content=%2\r\n");
	results.append("\r\n\r\n");

	String base64 = "";

	if(bytes != null)
	    base64 = Base64.encodeToString(bytes, Base64.DEFAULT);

	int indexOf = results.indexOf("%1");
	int length = base64.length() + "content=\r\n\r\n\r\n".length();

	results = results.replace(indexOf, indexOf + 2, String.valueOf(length));
	indexOf = results.indexOf("%2");
	results = results.replace(indexOf, indexOf + 2, base64);
	return results.toString();
    }

    public static String stripMessage(String message)
    {
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
				     byte keyStream[],
				     byte tag)
    {
	if(cryptography == null || keyStream == null || keyStream.length <= 0)
	    return null;

	try
	{
	    /*
	    ** [ Public Key Encryption ]
	    */

	    byte aesKey[] = Cryptography.aes256KeyBytes();

	    if(aesKey == null)
		return null;

	    byte shaKey[] = Cryptography.sha512KeyBytes();

	    if(shaKey == null)
		return null;

	    PublicKey publicKey = Database.getInstance().
		publicKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    byte pk[] = Cryptography.pkiEncrypt
		(publicKey, Miscellaneous.joinByteArrays(aesKey, shaKey));

	    if(pk == null)
		return null;

	    byte bytes[] = Miscellaneous.joinByteArrays
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
		 ** [ RSA 2048-Bit Public Key ]
		 */

		 keyStream,

		 /*
		 ** [ Identity ]
		 */

		 cryptography.identity(),

		 /*
		 ** [ Encryption Public Key Digest ]
		 */

		 cryptography.chatEncryptionPublicKeyDigest());

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte signature[] = cryptography.signViaChatSignature
		(Miscellaneous.joinByteArrays(aesKey, shaKey, bytes));

	    if(signature == null)
		return null;

	    /*
	    ** [ AES-256 ]
	    */

	    byte aes256[] = Cryptography.encrypt
		(Miscellaneous.joinByteArrays(bytes, signature), aesKey);

	    if(aes256 == null)
		return null;

	    /*
	    ** [ SHA-512 HMAC ]
	    */

	    byte sha512[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, aes256), shaKey);

	    if(sha512 == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, aes256, sha512),
		 Cryptography.sha512(sipHashId.getBytes("UTF-8")));

	    return Miscellaneous.joinByteArrays
		(pk, aes256, sha512, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] chatMessage(Cryptography cryptography,
				     String message,
				     String sipHashId,
				     byte keyStream[],
				     long sequence,
				     long timestamp)
    {
	if(cryptography == null || keyStream == null || keyStream.length <= 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    PublicKey publicKey = Database.getInstance().
		publicKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    /*
	    ** [ PK ]
	    */

	    byte pk[] = Cryptography.pkiEncrypt
		(publicKey, cryptography.chatEncryptionPublicKeyDigest());

	    if(pk == null)
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
		(Base64.encodeToString(message.getBytes("UTF-8"),
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
	    ** [ Public Key Signature ]
	    */

	    byte signature[] = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(cryptography.chatEncryptionPublicKeyDigest(),
				CHAT_MESSAGE_TYPE,
				stringBuilder.toString().getBytes()));

	    if(signature == null)
		return null;

	    stringBuilder.append
		(Base64.encodeToString(signature, Base64.NO_WRAP));

	    /*
	    ** [ AES-256 ]
	    */

	    byte aes256[] = Cryptography.encrypt
		(Miscellaneous.
		 joinByteArrays(CHAT_MESSAGE_TYPE,
				stringBuilder.toString().getBytes()),
		 Arrays.copyOfRange(keyStream, 0, 32));

	    stringBuilder.setLength(0);
	    stringBuilder = null;

	    if(aes256 == null)
		return null;

	    /*
	    ** [ SHA-512 HMAC ]
	    */

	    byte sha512[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, aes256),
		 Arrays.copyOfRange(keyStream, 32, keyStream.length));

	    if(sha512 == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, aes256, sha512),
		 Cryptography.sha512(sipHashId.getBytes("UTF-8")));

	    return Miscellaneous.joinByteArrays
		(pk, aes256, sha512, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] chatStatus(Cryptography cryptography,
				    String sipHashId,
				    byte keyStream[],
				    long timestamp)
    {
	if(cryptography == null || keyStream == null || keyStream.length <= 0)
	    return null;

	/*
	** keyStream
	** [0 ... 31] - AES-256 Encryption Key
	** [32 ... 95] - SHA-512 HMAC Key
	*/

	try
	{
	    PublicKey publicKey = Database.getInstance().
		publicKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    /*
	    ** [ PK ]
	    */

	    byte pk[] = Cryptography.pkiEncrypt
		(publicKey, cryptography.chatEncryptionPublicKeyDigest());

	    if(pk == null)
		return null;

	    byte bytes[] = Miscellaneous.joinByteArrays
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

	    byte signature[] = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(cryptography.chatEncryptionPublicKeyDigest(),
				bytes));

	    if(signature == null)
		return null;

	    /*
	    ** [ AES-256 ]
	    */

	    byte aes256[] = Cryptography.encrypt
		(Miscellaneous.joinByteArrays(bytes, signature),
		 Arrays.copyOfRange(keyStream, 0, 32));

	    if(aes256 == null)
		return null;

	    /*
	    ** [ SHA-512 HMAC ]
	    */

	    byte sha512[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, aes256),
		 Arrays.copyOfRange(keyStream, 32, keyStream.length));

	    if(sha512 == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, aes256, sha512),
		 Cryptography.sha512(sipHashId.getBytes("UTF-8")));

	    return Miscellaneous.joinByteArrays
		(pk, aes256, sha512, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] epksMessage(Cryptography cryptography,
				     String sipHashId,
				     byte keyStream[],
				     byte keyType[])
    {
	if(cryptography == null || keyStream == null || keyStream.length <= 0)
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
	    ** [ Encryption Public Key ]
	    */

	    PublicKey publicKey = null;
	    byte bytes[] = null;

	    publicKey = cryptography.chatEncryptionPublicKey();

	    if(publicKey == null)
		return null;

	    /*
	    ** [ Encryption Public Key Signature ]
	    */

	    bytes = cryptography.signViaChatEncryption(publicKey.getEncoded());

	    if(bytes == null)
		return null;

	    stringBuilder.append(Base64.encodeToString(publicKey.getEncoded(),
						       Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append(Base64.encodeToString(bytes, Base64.NO_WRAP));
	    stringBuilder.append("\n");

	    /*
	    ** [ Signature Public Key ]
	    */

	    publicKey = cryptography.chatSignaturePublicKey();

	    if(publicKey == null)
		return null;

	    /*
	    ** [ Signature Public Key Signature ]
	    */

	    bytes = cryptography.signViaChatSignature(publicKey.getEncoded());

	    if(bytes == null)
		return null;

	    stringBuilder.append(Base64.encodeToString(publicKey.getEncoded(),
						       Base64.NO_WRAP));
	    stringBuilder.append("\n");
	    stringBuilder.append(Base64.encodeToString(bytes, Base64.NO_WRAP));

	    byte aes256[] = Cryptography.encrypt
		(stringBuilder.toString().getBytes(),
		 Arrays.copyOfRange(keyStream, 0, 32));

	    stringBuilder.setLength(0);
	    stringBuilder = null;

	    if(aes256 == null)
		return null;

	    /*
	    ** [ SHA-512 HMAC ]
	    */

	    byte sha512[] = Cryptography.hmac
		(aes256,
		 Arrays.copyOfRange(keyStream, 32, keyStream.length));

	    if(sha512 == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(aes256, sha512),
		 Cryptography.sha512(sipHashId.getBytes("UTF-8")));

	    return Miscellaneous.joinByteArrays(aes256, sha512, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }
}
