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
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SteamKeyExchange
{
    private class Pair
    {
	public byte m_ciphertext[] = null;
	public byte m_pki[] = null;

	public Pair(byte ciphertext[], byte pki[])
	{
	    m_ciphertext = ciphertext;
	    m_pki = pki;
	}
    }

    private ArrayList<Pair> m_pairs = null;
    private AtomicInteger m_lastReadSteamOid = null;
    private ScheduledExecutorService m_parseScheduler = null;
    private ScheduledExecutorService m_readScheduler = null;
    private final Object m_parseSchedulerMutex = new Object();
    private final ReentrantReadWriteLock m_pairsMutex =
	new ReentrantReadWriteLock();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static long KEY_EXCHANGE_LIFETIME = 30000L;
    private final static long PARSE_INTERVAL = 50L;
    private final static long READ_INTERVAL = 1500L;
    private final static long WAIT_TIMEOUT = 10000L; // 10 seconds.

    private void shareB(SteamElement steamElement)
    {
	/*
	** Enqueue information if the network is not available.
	*/

	if(steamElement == null)
	    return;

	PublicKey publicKey = Cryptography.publicKeyFromBytes
	    (steamElement.m_ephemeralPublicKey);

	if(publicKey == null)
	    return;

	String sipHashId = Miscellaneous.sipHashIdFromDestination
	    (steamElement.m_destination);
	byte bytes[] = null;

	bytes = Messages.steamCall
	    (s_cryptography,
	     steamElement.m_displayFileName,
	     sipHashId,
	     steamElement.m_fileDigest,
	     steamElement.m_fileIdentity,
	     Cryptography.pkiEncrypt(publicKey, "", steamElement.m_keyStream),
	     steamElement.m_keyType.equals("McEliece") ?
	     Cryptography.MESSAGES_KEY_TYPES[0] :
	     Cryptography.MESSAGES_KEY_TYPES[1],
	     Messages.STEAM_KEY_EXCHANGE[1],
	     steamElement.m_fileSize);

	if(bytes != null)
	    Kernel.getInstance().enqueueSteamKeyExchange
		(Messages.bytesToMessageString(bytes), sipHashId);
    }

    private void steamAorB(byte ciphertext[], byte pki[])
    {
	if(ciphertext == null ||
	   ciphertext.length == 0 ||
	   pki == null ||
	   pki.length == 0)
	    return;

	byte tag = ciphertext[0];

	ciphertext = Arrays.copyOfRange(ciphertext, 1, ciphertext.length);

	String strings[] = new String(ciphertext).split("\\n");

	if(strings.length !=
	   Messages.STEAM_KEY_EXCHANGE_GROUP_TWO_ELEMENT_COUNT)
	    return;

	String displayFileName = "";
	String fileExtension = "";
	byte ephemeralPublicKey[] = null;
	byte ephemeralPublicKeyType[] = null;
	byte fileDigest[] = null;
	byte fileIdentity[] = null;
	byte publicKeySignature[] = null;
	byte senderPublicEncryptionKeyDigest[] = null;
	int ii = 0;
	long fileSize = 0;

	for(String string : strings)
	    switch(ii)
	    {
	    case 0:
		long timestamp = Miscellaneous.byteArrayToLong
		    (Base64.decode(string.getBytes(), Base64.NO_WRAP));

		if(Math.abs(System.currentTimeMillis() - timestamp) >
		   KEY_EXCHANGE_LIFETIME)
		    return;

		ii += 1;
		break;
	    case 1:
		ephemeralPublicKey = Base64.decode
		    (string.getBytes(), Base64.NO_WRAP);
		ii += 1;
		break;
	    case 2:
		ephemeralPublicKeyType = Base64.decode
		    (string.getBytes(), Base64.NO_WRAP);
		ii += 1;
		break;
	    case 3:
		fileDigest = Base64.decode(string.getBytes(), Base64.NO_WRAP);
		ii += 1;
		break;
	    case 4:
		fileIdentity = Base64.decode(string.getBytes(), Base64.NO_WRAP);

		/*
		** Is the Steam already registered?
		** If so, return the generated private key pair.
		*/

		if(tag == Messages.STEAM_KEY_EXCHANGE[0]) // Part A
		{
		    int oid = s_databaseHelper.steamOidFromFileIdentity
			(s_cryptography, fileIdentity);

		    if(oid > -1)
		    {
			SteamElement steamElement = s_databaseHelper.readSteam
			    (s_cryptography, -1, oid - 1);

			if(steamElement != null)
			{
			    shareB(steamElement);
			    return;
			}
		    }
		}

		ii += 1;
		break;
	    case 5:
		displayFileName = new String
		    (Base64.decode(string.getBytes(), Base64.NO_WRAP),
		     StandardCharsets.UTF_8);

		if(displayFileName.indexOf('.') == 0)
		    fileExtension = displayFileName.substring
			(displayFileName.indexOf('.'));

		ii += 1;
		break;
	    case 6:
		fileSize = Miscellaneous.byteArrayToLong
		    (Base64.decode(string.getBytes(), Base64.NO_WRAP));

		if(fileSize < 0)
		    return;

		ii += 1;
		break;
	    case 7:
		senderPublicEncryptionKeyDigest = Base64.decode
		    (string.getBytes(), Base64.NO_WRAP);
		ii += 1;
		break;
	    case 8:
		PublicKey signatureKey = s_databaseHelper.signatureKeyForDigest
		    (s_cryptography, senderPublicEncryptionKeyDigest);

		if(signatureKey == null)
		    return;

		publicKeySignature = Base64.decode
		    (string.getBytes(), Base64.NO_WRAP);

		if(!Cryptography.
		   verifySignature(signatureKey,
				   publicKeySignature,
				   Miscellaneous.
				   joinByteArrays
				   (pki,
				    new byte[] {tag},
				    strings[0].getBytes(), // Timestamp
				    "\n".getBytes(),
				    strings[1].getBytes(), /*
							   ** Ephemeral
							   ** Public Key
							   */
				    "\n".getBytes(),
				    strings[2].getBytes(), /*
							   ** Ephemeral
							   ** Public Key Type
							   */
				    "\n".getBytes(),
				    strings[3].getBytes(), // File Digest
				    "\n".getBytes(),
				    strings[4].getBytes(), // File Identity
				    "\n".getBytes(),
				    strings[5].getBytes(), // File Name
				    "\n".getBytes(),
				    strings[6].getBytes(), // File Size
				    "\n".getBytes(),
				    strings[7].getBytes(), /*
							   ** Sender's Public
							   ** Encryption Key
							   ** Digest
							   */
				    "\n".getBytes(),
				    s_cryptography.
				    chatEncryptionPublicKeyDigest())))
		    return;

		ii += 1;
		break;
	    default:
		break;
	    }

	if(tag == Messages.STEAM_KEY_EXCHANGE[0])
	{
	    /*
	    ** Record the new Steam.
	    */

	    SteamElement steamElement = new SteamElement();
	    String array[] = s_databaseHelper.nameSipHashIdFromDigest
		(s_cryptography, senderPublicEncryptionKeyDigest);

	    if(array[1].isEmpty())
		steamElement.m_destination = array[0];
	    else
		steamElement.m_destination = array[0] + " (" + array[1] + ")";

	    if(displayFileName.indexOf('.') == 0)
		steamElement.m_displayFileName = "Smoke_Steam_" +
		    Miscellaneous.byteArrayAsHexString(fileIdentity) +
		    fileExtension;
	    else
		steamElement.m_displayFileName =
		    "Smoke_Steam_" + displayFileName;

	    steamElement.m_ephemeralPublicKey = ephemeralPublicKey;
	    steamElement.m_fileDigest = fileDigest;
	    steamElement.m_fileIdentity = fileIdentity;
	    steamElement.m_fileName = steamElement.m_displayFileName;
	    steamElement.m_fileSize = fileSize;
	    steamElement.m_keyStream = Miscellaneous.joinByteArrays
		(Cryptography.aes256KeyBytes(), Cryptography.sha512KeyBytes());
	    steamElement.m_keyType = ephemeralPublicKeyType[0] ==
		Cryptography.MESSAGES_KEY_TYPES[0] ?
		"McEliece" : "RSA";
	    steamElement.m_readInterval = 0L;
	    steamElement.m_status = "created private-key pair";
	    s_databaseHelper.writeSteam(s_cryptography, steamElement);

	    /*
	    ** Transfer the new credentials.
	    */

	    shareB(steamElement);
	}
	else
	{
	    SteamElement steamElement = null;
	    int oid = s_databaseHelper.steamOidFromFileIdentity
		(s_cryptography, fileIdentity);

	    steamElement = s_databaseHelper.readSteam
		(s_cryptography, -1, oid - 1);

	    if(steamElement == null)
		return;

	    PrivateKey privateKey = Cryptography.privateKeyFromBytes
		(steamElement.m_ephemeralPrivateKey);

	    if(privateKey == null)
		/*
		** Something is strange!
		*/

		return;

	    byte bytes[] = Cryptography.pkiDecrypt
		(privateKey, ephemeralPublicKey);

	    if(bytes == null)
		return;

	    /*
	    ** Erase the ephemeral keys.
	    */

	    s_databaseHelper.writeSteamKeys
		(s_cryptography, bytes, null, null, oid);
	    s_databaseHelper.writeSteamStatus
		(s_cryptography, "received private-key pair", "", oid);
	}
    }

    public SteamKeyExchange()
    {
	m_lastReadSteamOid = new AtomicInteger(-1);
	m_pairs = new ArrayList<> ();
	m_parseScheduler = Executors.newSingleThreadScheduledExecutor();
	m_parseScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    Pair pair = null;

		    m_pairsMutex.writeLock().lock();

		    try
		    {
			if(!m_pairs.isEmpty())
			    pair = m_pairs.remove(m_pairs.size() - 1);
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_pairsMutex.writeLock().unlock();
		    }

		    if(pair == null)
			synchronized(m_parseSchedulerMutex)
			{
			    try
			    {
				m_parseSchedulerMutex.wait(WAIT_TIMEOUT);
			    }
			    catch(Exception exception)
			    {
			    }
			}

		    if(pair != null)
			if(pair.m_ciphertext[0] ==
			   Messages.STEAM_KEY_EXCHANGE[0] ||
			   pair.m_ciphertext[0] ==
			   Messages.STEAM_KEY_EXCHANGE[1])
			    steamAorB(pair.m_ciphertext, pair.m_pki);
		}
		catch(Exception exception)
		{
		}
	    }
        }, 1500L, PARSE_INTERVAL, TimeUnit.MILLISECONDS);
	m_readScheduler = Executors.newSingleThreadScheduledExecutor();
	m_readScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    if(!State.getInstance().isAuthenticated())
			return;

		    /*
		    ** Discover Steam instances which have not established
		    ** key pairs.
		    */

		    SteamElement steamElement = s_databaseHelper.readSteam
			(s_cryptography, -1, m_lastReadSteamOid.get());

		    if(steamElement == null ||
		       steamElement.m_destination.equals(Steam.OTHER) ||
		       steamElement.m_direction == SteamElement.DOWNLOAD ||
		       steamElement.m_fileSize == 0)
		    {
			if(steamElement != null)
			    m_lastReadSteamOid.set(steamElement.m_someOid);
			else
			    m_lastReadSteamOid.set(-1);

			return;
		    }

		    if(steamElement.m_keyStream != null &&
		       steamElement.m_keyStream.length == Cryptography.
		       CIPHER_HASH_KEYS_LENGTH)
		    {
			/*
			** Keys were exchanged.
			*/

			m_lastReadSteamOid.set(steamElement.m_someOid);
			return;
		    }

		    KeyPair keyPair = null;

		    if(steamElement.m_ephemeralPrivateKey == null ||
		       steamElement.m_ephemeralPrivateKey.length == 0 ||
		       steamElement.m_ephemeralPublicKey == null ||
		       steamElement.m_ephemeralPublicKey.length == 0)
		    {
			/*
			** Create a key pair.
			*/

			if(steamElement.m_keyType.equals("McEliece"))
			    keyPair = Cryptography.generatePrivatePublicKeyPair
				(Cryptography.
				 STEAM_KEY_EXCHANGE_MCELIECE_KEY_SIZE,
				 0,
				 0);
			else
			    keyPair = Cryptography.generatePrivatePublicKeyPair
				("RSA",
				 Cryptography.STEAM_KEY_EXCHANGE_RSA_KEY_SIZE,
				 0);

			if(keyPair == null)
			    return;

			/*
			** Record the key pair.
			*/

			if(!s_databaseHelper.
			   writeSteamKeys(s_cryptography,
					  keyPair,
					  null,
					  steamElement.m_someOid))
			    return;
		    }
		    else if(Kernel.getInstance().isNetworkConnected())
		    {
			/*
			** Do not enqueue key information if the network
			** is not available.
			*/

			if(steamElement.m_keyType.equals("McEliece"))
			    keyPair = Cryptography.generatePrivatePublicKeyPair
				(Cryptography.
				 STEAM_KEY_EXCHANGE_MCELIECE_KEY_SIZE,
				 steamElement.m_ephemeralPrivateKey,
				 steamElement.m_ephemeralPublicKey);
			else
			    keyPair = Cryptography.generatePrivatePublicKeyPair
				("RSA",
				 steamElement.m_ephemeralPrivateKey,
				 steamElement.m_ephemeralPublicKey);
		    }

		    /*
		    ** Do not enqueue key information if the network is not
		    ** available.
		    */

		    if(Kernel.getInstance().isNetworkConnected() &&
		       keyPair != null)
		    {
			/*
			** Share the key pair.
			*/

			String sipHashId = Miscellaneous.
			    sipHashIdFromDestination
			    (steamElement.m_destination);
			byte bytes[] = null;

			bytes = Messages.steamCall
			    (s_cryptography,
			     steamElement.m_displayFileName,
			     sipHashId,
			     steamElement.m_fileDigest,
			     steamElement.m_fileIdentity,
			     keyPair.getPublic().getEncoded(),
			     steamElement.m_keyType.equals("McEliece") ?
			     Cryptography.MESSAGES_KEY_TYPES[0] :
			     Cryptography.MESSAGES_KEY_TYPES[1],
			     Messages.STEAM_KEY_EXCHANGE[0],
			     steamElement.m_fileSize);

			if(bytes != null)
			    Kernel.getInstance().enqueueSteamKeyExchange
				(Messages.bytesToMessageString(bytes),
				 sipHashId);
		    }

		    /*
		    ** Next element!
		    */

		    m_lastReadSteamOid.set(steamElement.m_someOid);
		}
		catch(Exception exception)
		{
		}
	    }
        }, 1500L, READ_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void append(byte ciphertext[], byte pki[])
    {
	if(ciphertext == null ||
	   ciphertext.length == 0 ||
	   pki == null ||
	   pki.length == 0)
	    return;

	try
	{
	    m_pairsMutex.writeLock().lock();

	    try
	    {
		m_pairs.add(new Pair(ciphertext, pki));
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_pairsMutex.writeLock().unlock();
	    }

	    synchronized(m_parseSchedulerMutex)
	    {
		m_parseSchedulerMutex.notify();
	    }
	}
	catch(Exception exception)
	{
	}
    }
}
