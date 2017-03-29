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
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.security.PublicKey;
import java.util.Arrays;

public class Messages
{
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

    public static byte[] chatMessage(Cryptography cryptography,
				     PublicKey receiverPublicKey,
				     String message,
				     String timestamp,
				     byte sipHashKeyStream[],
				     int sequence)
    {
	if(cryptography == null || receiverPublicKey == null)
	    return null;

	/*
	** sipHashKeyStream
	** [0 .. 31] - AES-256 Encryption Key
	** [32 .. 95] - SHA-512 HMAC Key
	*/

	ByteArrayOutputStream stream = new ByteArrayOutputStream();
	ObjectOutputStream output = null;

	try
	{
	    output = new ObjectOutputStream(stream);

	    /*
	    ** Create random encryption and mac keys.
	    */

	    byte encryptionKeyBytes[] = Cryptography.randomBytes(32); // AES
	    byte macKeyBytes[] = Cryptography.randomBytes(64); // SHA-512

	    if(encryptionKeyBytes == null || macKeyBytes == null)
		return null;

	    /*
	    ** [ Private Key Data ]
	    ** [ Message Data ]
	    ** [ Digest ([ Private Key Data ] || [ Message Data ]) ]
	    ** [ Destination Digest ]
	    */

	    /*
	    ** [ Private Key Data ]
	    */

	    byte keysBytes[] = Cryptography.pkiEncrypt
		(receiverPublicKey,
		 Miscellaneous.joinByteArrays(encryptionKeyBytes, macKeyBytes));

	    if(keysBytes == null)
		return null;

	    /*
	    ** [ Message Data ]
	    */

	    byte senderPublicKeyDigest[] = cryptography.
		chatEncryptionKeyDigest();

	    if(senderPublicKeyDigest == null)
		return null;

	    output.reset();
	    output.writeObject(message);
	    output.writeObject(sequence);
	    output.writeObject(timestamp);
	    output.writeObject(senderPublicKeyDigest);
	    output.flush();

	    /*
	    ** Produce a signature of [ Private Key Data ] || [ Message Data ].
	    */

	    byte signature[] = cryptography.signViaChatSignature
		(Miscellaneous.joinByteArrays(encryptionKeyBytes,
					      macKeyBytes,
					      stream.toByteArray()));

	    if(signature == null)
		return null;

	    output.writeObject(signature);
	    output.flush();

	    byte messageBytes[] = Cryptography.encrypt
		(stream.toByteArray(), encryptionKeyBytes);

	    if(messageBytes == null)
		return null;

	    /*
	    ** [ Digest ([ Private Key Data ] || [ Message Data ]) ]
	    */

	    byte macBytes[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(keysBytes, messageBytes),
		 macKeyBytes);

	    if(macBytes == null)
		return null;

	    /*
	    ** [ Destination Digest ]
	    */

	    SipHash sipHash = new SipHash();
	    long destinationBytes = sipHash.hmac
		(Miscellaneous.joinByteArrays(keysBytes,
					      messageBytes,
					      macBytes),
		 Arrays.copyOfRange(sipHashKeyStream,
				    32,
				    sipHashKeyStream.length));

	    output.reset();
	    output.writeObject(keysBytes);
	    output.writeObject(messageBytes);
	    output.writeObject(macBytes);
	    output.writeObject(destinationBytes);
	    output.flush();
	}
	catch(Exception exception)
	{
	    return null;
	}
	finally
	{
	    try
	    {
		if(output != null)
		    output.close();

		stream.close();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	return stream.toByteArray();
    }

    public static byte[] epksMessage(Cryptography cryptography,
				     String keyType,
				     byte keyStream[])
    {
	if(cryptography == null || keyStream == null)
	    return null;

	/*
	** keyStream
	** [0 .. 31] - AES-256 Encryption Key
	** [32 .. 95] - SHA-512 HMAC Key
	*/

	ByteArrayOutputStream stream = new ByteArrayOutputStream();
	ObjectOutputStream output = null;

	try
	{
	    output = new ObjectOutputStream(stream);
	    output.reset();

	    /*
	    ** [ Key Type ]
	    */

	    output.writeObject(keyType);

	    PublicKey publicKey = null;
	    byte bytes[] = null;

	    /*
	    ** [ Public Key Data ]
	    ** [ Digest ([ Public Key Data ]) ]
	    ** [ Destination Digest ]
	    */

	    if(keyType.equals("chat"))
	    {
		/*
		** [ Public Key ]
		*/

		publicKey = cryptography.chatEncryptionPublicKey();

		if(publicKey == null)
		    return null;

		/*
		** [ Public Key Signature ]
		*/

		bytes = cryptography.signViaChatEncryption
		    (publicKey.getEncoded());

		if(bytes == null)
		    return null;

		output.writeObject(publicKey);
		output.writeObject(bytes);

		/*
		** [ Signature Key ]
		*/

		publicKey = cryptography.chatSignaturePublicKey();

		if(publicKey == null)
		    return null;

		/*
		** [ Signature Key Signature ]
		*/

		bytes = cryptography.signViaChatSignature
		    (publicKey.getEncoded());

		if(bytes == null)
		    return null;

		output.writeObject(publicKey);
		output.writeObject(bytes);
		output.flush();

		byte messageBytes[] = Cryptography.encrypt
		    (stream.toByteArray(),
		     Arrays.copyOfRange(keyStream, 0, 32));

		if(messageBytes == null)
		    return null;

		/*
		** [ Digest ([ Message Data ]) ]
		*/

		byte macBytes[] = Cryptography.hmac
		    (messageBytes,
		     Arrays.copyOfRange(keyStream, 32, keyStream.length));

		if(macBytes == null)
		    return null;

		/*
		** [ Destination Digest ]
		*/

		SipHash sipHash = new SipHash();
		long destinationBytes = sipHash.hmac
		    (Miscellaneous.joinByteArrays(messageBytes,
						  macBytes),
		     Arrays.copyOfRange(keyStream, 32, keyStream.length));

		output.reset();
		output.writeObject(messageBytes);
		output.writeObject(macBytes);
		output.writeObject(destinationBytes);
		output.flush();
	    }
	}
	catch(Exception exception)
	{
	    return null;
	}
	finally
	{
	    try
	    {
		if(output != null)
		    output.close();

		stream.close();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	return stream.toByteArray();
    }
}
