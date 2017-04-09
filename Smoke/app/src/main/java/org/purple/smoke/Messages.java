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
    public enum MESSAGE_TYPES
    {
	MESSAGE_CHAT,
	MESSAGE_EPKS
    }

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
				     byte encryptionKeyBytes[],
				     byte macKeyBytes[],
				     byte sipHashKeyStream[],
				     int sequence)
    {
	if(cryptography == null || encryptionKeyBytes == null ||
	   macKeyBytes == null || receiverPublicKey == null)
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

	    ByteArrayOutputStream s = new ByteArrayOutputStream();
	    ObjectOutputStream o = new ObjectOutputStream(s);

	    /*
	    ** [ Message Data ]
	    */

	    byte senderPublicKeyDigest[] = cryptography.
		chatEncryptionKeyDigest();

	    if(senderPublicKeyDigest == null)
		return null;

	    o.writeObject(message);
	    o.writeObject(sequence);
	    o.writeObject(timestamp);
	    o.writeObject(senderPublicKeyDigest);
	    o.flush();

	    /*
	    ** Produce a signature of [ Private Key Data ] || [ Message Data ].
	    */

	    byte signature[] = cryptography.signViaChatSignature
		(s.toByteArray());

	    if(signature == null)
		return null;

	    byte messageBytes[] = Cryptography.encrypt
		(signature, encryptionKeyBytes);

	    if(messageBytes == null)
		return null;

	    /*
	    ** [ Digest ([ Private Key Data ] || [ Message Data ]) ]
	    */

	    byte macBytes[] = Cryptography.hmac
		(messageBytes, macKeyBytes);

	    if(macBytes == null)
		return null;

	    /*
	    ** [ Destination Digest ]
	    */

	    byte destination[] = Cryptography.hmac
		(Miscellaneous.joinByteArrays(messageBytes,
					      macBytes),
		 Arrays.copyOfRange(sipHashKeyStream,
				    32,
				    sipHashKeyStream.length));

	    output.writeObject(messageBytes);
	    output.writeObject(macBytes);
	    output.writeObject(destination);
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

	    ByteArrayOutputStream s = new ByteArrayOutputStream();
	    ObjectOutputStream o = new ObjectOutputStream(s);

	    /*
	    ** [ Key Type ]
	    */

	    o.writeObject(keyType);

	    /*
	    ** [ Public Key ]
	    */

	    PublicKey publicKey = null;
	    byte bytes[] = null;

	    publicKey = cryptography.chatEncryptionPublicKey();

	    if(publicKey == null)
		return null;

	    /*
	    ** [ Public Key Signature ]
	    */

	    bytes = cryptography.signViaChatEncryption(publicKey.getEncoded());

	    if(bytes == null)
		return null;

	    o.writeObject(publicKey);
	    o.writeObject(bytes);

	    /*
	    ** [ Signature Key ]
	    */

	    publicKey = cryptography.chatSignaturePublicKey();

	    if(publicKey == null)
		return null;

	    /*
	    ** [ Signature Key Signature ]
	    */

	    bytes = cryptography.signViaChatSignature(publicKey.getEncoded());

	    if(bytes == null)
		return null;

	    o.writeObject(publicKey);
	    o.writeObject(bytes);
	    output.flush();

	    byte messageBytes[] = Cryptography.encrypt
		(s.toByteArray(), Arrays.copyOfRange(keyStream, 0, 32));

	    if(messageBytes == null)
		return null;

	    /*
	    ** [ Digest ([ Public Data ]) ]
	    */

	    byte macBytes[] = Cryptography.hmac
		(messageBytes,
		 Arrays.copyOfRange(keyStream, 32, keyStream.length));

	    if(macBytes == null)
		return null;

	    /*
	    ** [ Single-Byte Array ]
	    */

	    byte array[] = new byte[1];

	    array[0] = 0;
	    output.writeObject(messageBytes);
	    output.writeObject(macBytes);
	    output.writeObject(array);
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
}
