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
				     PublicKey receiverEncryptionKey,
				     String message,
				     String timestamp,
				     int sequence)
    {
	if(cryptography == null || receiverEncryptionKey == null)
	    return null;

	/*
	** Create random encryption and mac keys.
	*/

	byte encryptionKey[] = cryptography.randomBytes(32); // AES
	byte macKey[] = cryptography.randomBytes(64); // SHA-512

	if(encryptionKey == null || macKey == null)
	    return null;

	/*
	** [ Private Key Data ]
	** [ Message Data ]
	** [ Digest of [ Private Key Data ] || [ Message Data ]]
	** [ Destination (Digest) ]
	*/

	ByteArrayOutputStream stream = new ByteArrayOutputStream();
	ObjectOutputStream output = null;
	byte bytes[] = null;

	try
	{
	    output = new ObjectOutputStream(stream);
	    output.writeObject(message);
	    output.writeObject(sequence);
	    output.writeObject(timestamp);
	    output.flush();
	    bytes = stream.toByteArray();
	}
	catch(Exception exception)
	{
	    bytes = null;
	}
	finally
	{
	    try
	    {
		output.close();
		stream.close();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	byte messageBytes[] = cryptography.encrypt(bytes, encryptionKey);

	if(messageBytes == null)
	    return null;

	return null;
    }
}
