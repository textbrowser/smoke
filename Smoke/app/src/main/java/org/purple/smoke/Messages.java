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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.security.PublicKey;

public class Messages
{
    public static byte[] chatMessage(Cryptography cryptography,
				     PublicKey receiverPublicKey,
				     String message,
				     String timestamp,
				     int sequence)
    {
	if(cryptography == null || receiverPublicKey == null)
	    return null;

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
	    ** [ Destination (Digest) ]
	    */

	    /*
	    ** [ Private Key Data ]
	    */

	    byte keyBytes[] = Cryptography.pkiEncrypt
		(receiverPublicKey,
		 Miscellaneous.joinByteArrays(encryptionKeyBytes, macKeyBytes));

	    if(keyBytes == null)
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
		(Miscellaneous.joinByteArrays(keyBytes, messageBytes),
		 macKeyBytes);

	    if(macBytes == null)
		return null;

	    /*
	    ** [ Destination ]
	    */

	    byte destinationBytes[] = new byte[64]; // Not used.

	    output.reset();
	    output.writeObject(keyBytes);
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
