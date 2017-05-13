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
    public final static byte CALL_HALF_AND_HALF_TAGS[] = new byte[] {0, 1};
    public final static byte CHAT_KEY_TYPE[] = new byte[] {0};
    public final static int CALL_HALF_AND_HALF_OFFSETS[] = new int[]
	{0, 1, 9, 25, 57, 65, 129};
    public final static int CHAT_GROUP_TWO_ELEMENT_COUNT = 4;
    public final static int EPKS_GROUP_ONE_ELEMENT_COUNT = 6;

    public static String bytesToMessageString(byte bytes[])
    {
	StringBuffer results = new StringBuffer();

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

	/*
	** keyStream
	** [0 ... 15] - AES-128 Encryption Key
	** [16 ... 47] - SHA-256 HMAC Key
	*/

	try
	{
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
		 ** [ AES-128 Key ]
		 */

		 Arrays.copyOfRange(keyStream, 0, 16),

		 /*
		 ** [ SHA-256 Key ]
		 */

		 Arrays.copyOfRange(keyStream, 16, keyStream.length),

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

	    byte signature[] = cryptography.signViaChatSignature(bytes);

	    if(signature == null)
		return null;

	    PublicKey publicKey = Database.getInstance().
		publicKeyForSipHashId(cryptography, sipHashId);

	    if(publicKey == null)
		return null;

	    byte messageBytes[] = Cryptography.pkiEncrypt
		(publicKey, Miscellaneous.joinByteArrays(bytes, signature));

	    if(messageBytes == null)
		return null;

	    /*
	    ** [ Random Bytes ]
	    */

	    byte randomBytes[] = Cryptography.randomBytes(64);

	    /*
	    ** [ Destination ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(messageBytes, randomBytes),
		 Cryptography.sha512(sipHashId.getBytes()));

	    return Miscellaneous.joinByteArrays
		(messageBytes, randomBytes, destination);
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

	    StringBuffer stringBuffer = new StringBuffer();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuffer.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(System.
						       currentTimeMillis()),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    /*
	    ** [ Message ]
	    */

	    stringBuffer.append
		(Base64.encodeToString(message.getBytes("UTF-8"),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    /*
	    ** [ Sequence ]
	    */

	    stringBuffer.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(sequence),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    /*
	    ** [ Public Key Signature ]
	    */

	    byte signature[] = cryptography.signViaChatSignature
		(Miscellaneous.
		 joinByteArrays(pk, stringBuffer.toString().getBytes()));

	    if(signature == null)
		return null;

	    stringBuffer.append(Base64.encodeToString(signature,
						      Base64.NO_WRAP));

	    byte messageBytes[] = Cryptography.encrypt
		(stringBuffer.toString().getBytes(),
		 Arrays.copyOfRange(keyStream, 0, 32));

	    if(messageBytes == null)
		return null;

	    /*
	    ** [ Digest ]
	    */

	    byte macBytes[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, messageBytes),
		 Arrays.copyOfRange(keyStream, 32, keyStream.length));

	    if(macBytes == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(pk, messageBytes, macBytes),
		 Cryptography.sha512(sipHashId.getBytes()));

	    return Miscellaneous.joinByteArrays
		(pk, messageBytes, macBytes, destination);
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
	    StringBuffer stringBuffer = new StringBuffer();

	    /*
	    ** [ A Timestamp ]
	    */

	    stringBuffer.append
		(Base64.encodeToString(Miscellaneous.
				       longToByteArray(System.
						       currentTimeMillis()),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    /*
	    ** [ Key Type ]
	    */

	    stringBuffer.append
		(Base64.encodeToString(keyType, Base64.NO_WRAP));
	    stringBuffer.append("\n");

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

	    stringBuffer.append(Base64.encodeToString(publicKey.getEncoded(),
						      Base64.NO_WRAP));
	    stringBuffer.append("\n");
	    stringBuffer.append(Base64.encodeToString(bytes, Base64.NO_WRAP));
	    stringBuffer.append("\n");

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

	    stringBuffer.append(Base64.encodeToString(publicKey.getEncoded(),
						      Base64.NO_WRAP));
	    stringBuffer.append("\n");
	    stringBuffer.append(Base64.encodeToString(bytes, Base64.NO_WRAP));

	    byte messageBytes[] = Cryptography.encrypt
		(stringBuffer.toString().getBytes(),
		 Arrays.copyOfRange(keyStream, 0, 32));

	    if(messageBytes == null)
		return null;

	    /*
	    ** [ Digest ]
	    */

	    byte macBytes[] = Cryptography.hmac
		(messageBytes,
		 Arrays.copyOfRange(keyStream, 32, keyStream.length));

	    if(macBytes == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(messageBytes, macBytes),
		 Cryptography.sha512(sipHashId.getBytes()));

	    return Miscellaneous.joinByteArrays
		(messageBytes, macBytes, destination);
	}
	catch(Exception exception)
	{
	}

	return null;
    }
}
