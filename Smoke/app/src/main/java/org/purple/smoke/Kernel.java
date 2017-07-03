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

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;
import android.util.Base64;
import android.util.SparseArray;
import java.net.InetAddress;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Kernel
{
    private ArrayList<MessageElement> m_messagesToSend = null;
    private Hashtable<String, ParticipantCall> m_callQueue = null;
    private ScheduledExecutorService m_callScheduler = null;
    private ScheduledExecutorService m_congestionScheduler = null;
    private ScheduledExecutorService m_messagesToSendScheduler = null;
    private ScheduledExecutorService m_neighborsScheduler = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private WakeLock m_wakeLock = null;
    private byte m_chatMessageRetrievalIdentity[] = null;
    private final ReentrantReadWriteLock m_callQueueMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_chatMessageRetrievalIdentityMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_messagesToSendMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_neighborsMutex =
	new ReentrantReadWriteLock();
    private final SparseArray<Neighbor> m_neighbors = new SparseArray<> ();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static SipHash s_congestionSipHash = new SipHash
	(Cryptography.randomBytes(SipHash.KEY_LENGTH));
    private final static int CALL_INTERVAL = 250; // 0.250 Seconds
    private final static int CALL_LIFETIME = 15000; // 15 Seconds
    private final static int CONGESTION_INTERVAL = 5000; // 5 Seconds
    private final static int CONGESTION_LIFETIME = 60; // Seconds
    private final static int MESSAGES_TO_SEND_INTERVAL =
	100; // 100 Milliseconds
    private final static int NEIGHBORS_INTERVAL = 5000; // 5 Seconds
    private final static int STATUS_INTERVAL = 15000; /*
						      ** Should be less than
						      ** Chat.STATUS_WINDOW.
						      */
    private static Kernel s_instance = null;

    private Kernel()
    {
	m_callQueue = new Hashtable<> ();
	m_messagesToSend = new ArrayList<> ();
	prepareSchedulers();
    }

    private void prepareSchedulers()
    {
	if(m_callScheduler == null)
	{
	    m_callScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_callScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			ParticipantCall participantCall = null;
			String sipHashId = "";

			/*
			** Allow the UI to respond to calling requests
			** while the kernel attempts to generate
			** ephemeral RSA keys.
			*/

			m_callQueueMutex.writeLock().lock();

			try
			{
			    if(m_callQueue.isEmpty())
				return;

			    /*
			    ** Remove expired calls.
			    */

			    Iterator<Hashtable.Entry<String, ParticipantCall> >
				it = m_callQueue.entrySet().iterator();

			    while(it.hasNext())
			    {
				Hashtable.Entry<String, ParticipantCall> entry =
				    it.next();

				if(entry.getValue() == null)
				    it.remove();

				if((System.nanoTime() - entry.getValue().
				    m_startTime) / 1000000 > CALL_LIFETIME)
				    it.remove();
			    }

			    /*
			    ** Discover a pending call.
			    */

			    int participantOid = -1;

			    for(String string : m_callQueue.keySet())
			    {
				if(m_callQueue.get(string).m_keyPair != null)
				    continue;

				participantOid = m_callQueue.get(string).
				    m_participantOid;
				sipHashId = string;
				break;
			    }

			    if(participantOid == -1)
				/*
				** A new call does not exist.
				*/

				return;

			    participantCall = m_callQueue.get(sipHashId);
			    participantCall.preparePrivatePublicKey();
			}
			finally
			{
			    m_callQueueMutex.writeLock().unlock();
			}

			m_callQueueMutex.writeLock().lock();

			try
			{
			    /*
			    ** The entry may have been removed.
			    */

			    if(m_callQueue.containsKey(sipHashId))
				m_callQueue.put(sipHashId, participantCall);
			}
			finally
			{
			    m_callQueueMutex.writeLock().unlock();
			}

			/*
			** Place a call request to all neighbors.
			*/

			byte bytes[] = Messages.callMessage
			    (s_cryptography,
			     participantCall.m_sipHashId,
			     participantCall.m_keyPair.getPublic().getEncoded(),
			     Messages.CALL_HALF_AND_HALF_TAGS[0]);

			if(bytes != null)
			    scheduleSend(Messages.bytesToMessageString(bytes));
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, CALL_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_congestionScheduler == null)
	{
	    m_congestionScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_congestionScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    s_databaseHelper.purgeCongestion(CONGESTION_LIFETIME);
		}
	    }, 1500, CONGESTION_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_messagesToSendScheduler == null)
	{
	    m_messagesToSendScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_messagesToSendScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    while(true)
		    {
			MessageElement messageElement = null;

			m_messagesToSendMutex.writeLock().lock();

			try
			{
			    if(!m_messagesToSend.isEmpty())
				messageElement = m_messagesToSend.remove(0);
			    else
				break;
			}
			finally
			{
			    m_messagesToSendMutex.writeLock().unlock();
			}

			if(messageElement == null)
			    continue;

			byte bytes[] = null;

			try
			{
			    switch(messageElement.m_messageType)
			    {
			    case MessageElement.CHAT_MESSAGE_TYPE:
				bytes = Messages.chatMessage
				    (s_cryptography,
				     messageElement.m_message,
				     messageElement.m_sipHashId,
				     false,
				     Cryptography.
				     sha512(messageElement.m_sipHashId.
					    getBytes("UTF-8")),
				     messageElement.m_keyStream,
				     State.getInstance().
				     chatSequence(messageElement.m_sipHashId),
				     System.currentTimeMillis());
				break;
			    case MessageElement.RETRIEVE_MESSAGES_MESSAGE_TYPE:
				bytes = Messages.chatMessageRetrieval
				    (s_cryptography);
				break;
			    }
			}
			catch(Exception exception)
			{
			    bytes = null;
			}

			if(bytes != null)
			{
			    switch(messageElement.m_messageType)
			    {
			    case MessageElement.CHAT_MESSAGE_TYPE:
				enqueueMessage
				    (Messages.bytesToMessageString(bytes));
				State.getInstance().incrementChatSequence
				    (messageElement.m_sipHashId);
				break;
			    case MessageElement.RETRIEVE_MESSAGES_MESSAGE_TYPE:
				scheduleSend
				    (Messages.bytesToMessageString(bytes));
				break;
			    }
			}

			if(messageElement.m_messageType ==
			   MessageElement.CHAT_MESSAGE_TYPE)
			    if(s_cryptography.ozoneMacKey() != null)
			    {
				bytes = Messages.chatMessage
				    (s_cryptography,
				     messageElement.m_message,
				     messageElement.m_sipHashId,
				     true,
				     s_cryptography.ozoneMacKey(),
				     messageElement.m_keyStream,
				     State.getInstance().
				     chatSequence(messageElement.m_sipHashId),
				     System.currentTimeMillis());

				if(bytes != null)
				    enqueueMessage
					(Messages.bytesToMessageString(bytes));
			    }
		    }
		}
	    }, 1500, MESSAGES_TO_SEND_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_neighborsScheduler == null)
	{
	    m_neighborsScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_neighborsScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    prepareNeighbors();
		}
	    }, 1500, NEIGHBORS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_statusScheduler == null)
	{
	    m_statusScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_statusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    ArrayList<ParticipantElement> arrayList =
			s_databaseHelper.readParticipants(s_cryptography, "");

		    if(arrayList == null || arrayList.size() == 0)
			return;

		    for(ParticipantElement participantElement : arrayList)
			if(participantElement != null)
			{
			    byte bytes[] = Messages.chatStatus
				(s_cryptography,
				 participantElement.m_sipHashId,
				 participantElement.m_keyStream);

			    if(bytes != null)
				scheduleSend
				    (Messages.bytesToMessageString(bytes));
			}

		    arrayList.clear();
		}
	    }, 1500, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void purge()
    {
	/*
	** Disconnect all existing sockets.
	*/

	m_neighborsMutex.writeLock().lock();

	try
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null)
		    m_neighbors.get(j).abort();
	    }

	    m_neighbors.clear();
	}
	finally
	{
	    m_neighborsMutex.writeLock().unlock();
	}
    }

    private void scheduleSend(String message)
    {
	if(message.trim().isEmpty())
	    return;

	if(s_databaseHelper.
	   containsCongestionDigest(s_congestionSipHash.hmac(message.
							     getBytes())))
	    return;

	m_neighborsMutex.readLock().lock();

	try
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null)
		    m_neighbors.get(j).scheduleSend(message);
	    }
	}
	finally
	{
	    m_neighborsMutex.readLock().unlock();
	}
    }

    public boolean call(int participantOid, String sipHashId)
    {
	/*
	** Calling messages are not placed in the outbound_queue
	** as they are considered temporary.
	*/

	m_callQueueMutex.writeLock().lock();

	try
	{
	    if(m_callQueue.containsKey(sipHashId))
		return false;

	    m_callQueue.put
		(sipHashId, new ParticipantCall(sipHashId, participantOid));
	}
	finally
	{
	    m_callQueueMutex.writeLock().unlock();
	}

	return true;
    }

    public boolean enqueueMessage(String message)
    {
	if(message.trim().isEmpty())
	    return false;

	ArrayList<NeighborElement> arrayList =
	    s_databaseHelper.readNeighborOids(s_cryptography);

	if(arrayList == null || arrayList.size() == 0)
	    return false;

	for(int i = 0; i < arrayList.size(); i++)
	    if(arrayList.get(i) != null &&
	       arrayList.get(i).m_statusControl.toLowerCase().equals("connect"))
		s_databaseHelper.enqueueOutboundMessage
		    (message, arrayList.get(i).m_oid);

	arrayList.clear();
	return true;
    }

    public boolean isConnected()
    {
	m_neighborsMutex.readLock().lock();

	try
	{
	    if(m_neighbors.size() == 0)
		return false;

	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null)
		    if(m_neighbors.get(j).connected())
			return true;
	    }
	}
	finally
	{
	    m_neighborsMutex.readLock().unlock();
	}

	return false;
    }

    public boolean ourMessage(String buffer)
    {
	/*
	** Return false if the contents of buffer could not be assessed.
	*/

	long value = s_congestionSipHash.hmac(buffer.getBytes());

	if(s_databaseHelper.containsCongestionDigest(value))
	    return true;

	if(s_databaseHelper.writeCongestionDigest(value))
	    return true;

	try
	{
	    byte bytes[] =
		Base64.decode(Messages.stripMessage(buffer), Base64.DEFAULT);

	    if(bytes == null || bytes.length < 128)
		return false;

	    byte array1[] = Arrays.copyOfRange // Blocks #1, #2, etc.
		(bytes, 0, bytes.length - 128);
	    byte array2[] = Arrays.copyOfRange // Second to the last block.
		(bytes, bytes.length - 128, bytes.length - 64);
	    byte array3[] = Arrays.copyOfRange // The last block (destination).
		(bytes, bytes.length - 64, bytes.length);

	    if(!s_cryptography.
	       iAmTheDestination(Miscellaneous.joinByteArrays(array1, array2),
				 array3))
		return false;

	    if(s_cryptography.isValidSipHashMac(array1, array2))
	    {
		/*
		** EPKS
		*/

		array1 = s_cryptography.decryptWithSipHashKey(array1);

		String sipHashId = s_databaseHelper.writeParticipant
		    (s_cryptography, array1);

		if(!sipHashId.isEmpty())
		{
		    /*
		    ** New participant.
		    */

		    Intent intent = new Intent
			("org.purple.smoke.populate_participants");

		    Smoke.getApplication().sendBroadcast(intent);

		    /*
		    ** Response-share.
		    */

		    byte salt[] = Cryptography.sha512
			(sipHashId.trim().getBytes("UTF-8"));
		    byte temporary[] = Cryptography.
			pbkdf2(salt,
			       sipHashId.toCharArray(),
			       Database.
			       SIPHASH_STREAM_CREATION_ITERATION_COUNT,
			       160); // SHA-1

		    if(temporary != null)
			bytes = Cryptography.
			    pbkdf2(salt,
				   new String(temporary).toCharArray(),
				   1,
				   768); // 8 * (32 + 64) Bits
		    else
			bytes = null;

		    if(bytes != null)
			bytes = Messages.epksMessage
			    (s_cryptography,
			     sipHashId,
			     bytes,
			     Messages.CHAT_KEY_TYPE);

		    if(bytes != null)
			enqueueMessage
			    (Messages.bytesToMessageString(bytes));
		}

		return true;
	    }

	    byte pk[] = s_cryptography.pkiDecrypt
		(Arrays.
		 copyOfRange(bytes,
			     0,
			     Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8));

	    if(pk == null)
		return true;

	    if(pk.length == 64)
	    {
		/*
		** Chat, Chat Status
		*/

		byte keyStream[] = s_databaseHelper.participantKeyStream
		    (s_cryptography, pk);

		if(keyStream == null)
		    return true;

		byte sha512[] = Cryptography.hmac
		    (Arrays.copyOfRange(bytes, 0, bytes.length - 128),
		     Arrays.copyOfRange(keyStream, 32, keyStream.length));

		if(!Cryptography.memcmp(array2, sha512))
		    return true;

		byte aes256[] = Cryptography.decrypt
		    (Arrays.
		     copyOfRange(bytes,
				 Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8,
				 bytes.length - 128),
		     Arrays.copyOfRange(keyStream, 0, 32));

		if(aes256 == null)
		    return true;

		byte abyte[] = new byte[] {aes256[0]};

		if(abyte[0] == Messages.CHAT_STATUS_MESSAGE_TYPE[0])
		{
		    String array[] = s_databaseHelper.nameSipHashIdFromDigest
			(s_cryptography, pk);

		    if(array == null || array.length != 2)
			return true;

		    String sipHashId = array[1];

		    if(s_databaseHelper.readParticipantOptions(s_cryptography,
							       sipHashId).
		       contains("optional_signatures = false"))
		    {
			PublicKey signatureKey = s_databaseHelper.
			    signatureKeyForDigest(s_cryptography, pk);

			if(signatureKey == null)
			    return true;

			if(!Cryptography.
			   verifySignature
			   (signatureKey,
			    Arrays.copyOfRange(aes256,
					       10,
					       aes256.length),
			    Miscellaneous.
			    joinByteArrays(pk,
					   Arrays.
					   copyOfRange(aes256,
						       0,
						       10),
					   s_cryptography.
					   chatEncryptionPublicKeyDigest())))
			    return true;
		    }

		    long current = System.currentTimeMillis();
		    long timestamp = Miscellaneous.byteArrayToLong
			(Arrays.copyOfRange(aes256, 1, 1 + 8));

		    if(current - timestamp < 0)
		    {
			if(timestamp - current > Chat.STATUS_WINDOW)
			    return true;
		    }
		    else if(current - timestamp > Chat.STATUS_WINDOW)
			return true;

		    s_databaseHelper.updateParticipantLastTimestamp
			(s_cryptography, pk);
		    return true;
		}

		aes256 = Arrays.copyOfRange(aes256, 1, aes256.length);

		String strings[] = new String(aes256).split("\\n");

		if(strings.length != Messages.CHAT_GROUP_TWO_ELEMENT_COUNT)
		    return true;

		String message = null;
		byte publicKeySignature[] = null;
		byte recipientDigest[] = null;
		int ii = 0;
		long sequence = 0;
		long timestamp = 0;

		for(String string : strings)
		    /*
		    ** Ignore byte 0, please see above.
		    */

		    switch(ii)
		    {
		    case 0:
			timestamp = Miscellaneous.byteArrayToLong
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP));
			ii += 1;
			break;
		    case 1:
			message = new String
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP),
			     "UTF-8").trim();
			ii += 1;
			break;
		    case 2:
			sequence = Miscellaneous.byteArrayToLong
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP));
			ii += 1;
			break;
		    case 3:
			String array[] = s_databaseHelper.
			    nameSipHashIdFromDigest(s_cryptography, pk);

			if(array == null || array.length != 2)
			    return true;

			String sipHashId = array[1];

			if(s_databaseHelper.
			   readParticipantOptions(s_cryptography,
						  sipHashId).
			   contains("optional_signatures = false"))
			{
			    publicKeySignature = Base64.decode
				(string.getBytes(), Base64.NO_WRAP);

			    PublicKey signatureKey = s_databaseHelper.
				signatureKeyForDigest(s_cryptography, pk);

			    if(signatureKey == null)
				return true;

			    if(!Cryptography.
			       verifySignature
			       (signatureKey,
				publicKeySignature,
				Miscellaneous.
				joinByteArrays
				(pk,
				 abyte,
				 strings[0].getBytes(),
				 "\n".getBytes(),
				 strings[1].getBytes(),
				 "\n".getBytes(),
				 strings[2].getBytes(),
				 "\n".getBytes(),
				 s_cryptography.
				 chatEncryptionPublicKeyDigest())))
				return true;
			}

			strings = array;
			break;
		    }

		if(message == null)
		    return true;

		boolean updateTimeStamp = true;
		long current = System.currentTimeMillis();

		if(current - timestamp < 0)
		{
		    if(timestamp - current > Chat.CHAT_WINDOW)
			updateTimeStamp = false;
		}
		else if(current - timestamp > Chat.CHAT_WINDOW)
		    updateTimeStamp = false;

		if(updateTimeStamp)
		    s_databaseHelper.updateParticipantLastTimestamp
			(s_cryptography, strings[1]);

		Intent intent = new Intent
		    ("org.purple.smoke.chat_message");

		intent.putExtra("org.purple.smoke.message", message);
		intent.putExtra("org.purple.smoke.name", strings[0]);
		intent.putExtra("org.purple.smoke.sequence", sequence);
		intent.putExtra("org.purple.smoke.sipHashId", strings[1]);
		intent.putExtra("org.purple.smoke.timestamp", timestamp);
		Smoke.getApplication().sendBroadcast(intent);
		return true;
	    }
	    else if(pk.length == 96)
	    {
		/*
		** Organic Half-And-Half
		*/

		byte sha512[] = Cryptography.hmac
		    (Arrays.copyOfRange(bytes, 0, bytes.length - 128),
		     Arrays.copyOfRange(pk, 32, pk.length));

		if(!Cryptography.memcmp(array2, sha512))
		    return true;

		byte aes256[] = Cryptography.decrypt
		    (Arrays.
		     copyOfRange(bytes,
				 Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8,
				 bytes.length - 128),
		     Arrays.copyOfRange(pk, 0, 32));

		if(aes256 == null)
		    return true;

		byte tag = aes256[0];

		if(!(tag == Messages.CALL_HALF_AND_HALF_TAGS[0] ||
		     tag == Messages.CALL_HALF_AND_HALF_TAGS[1]))
		    return true;

		PublicKey signatureKey = null;

		if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
		    signatureKey = s_databaseHelper.signatureKeyForDigest
			(s_cryptography,
			 Arrays.copyOfRange(aes256, 311, 311 + 64));
		else
		    signatureKey = s_databaseHelper.signatureKeyForDigest
			(s_cryptography,
			 Arrays.copyOfRange(aes256, 273, 273 + 64));

		if(signatureKey == null)
		    return true;

		if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
		{
		    if(!Cryptography.
		       verifySignature
		       (signatureKey,
			Arrays.copyOfRange(aes256, 375, aes256.length),
			Miscellaneous.
			joinByteArrays(pk,
				       Arrays.
				       copyOfRange(aes256,
						   0,
						   375),
				       s_cryptography.
				       chatEncryptionPublicKeyDigest())))
			return true;
		}
		else
		{
		    if(!Cryptography.
		       verifySignature
		       (signatureKey,
			Arrays.copyOfRange(aes256, 337, aes256.length),
			Miscellaneous.
			joinByteArrays(pk,
				       Arrays.
				       copyOfRange(aes256,
						   0,
						   337),
				       s_cryptography.
				       chatEncryptionPublicKeyDigest())))
			return true;
		}

		long current = System.currentTimeMillis();
		long timestamp = Miscellaneous.byteArrayToLong
		    (Arrays.copyOfRange(aes256, 1, 1 + 8));

		if(current - timestamp < 0)
		{
		    if(timestamp - current > CALL_LIFETIME)
			return true;
		}
		else if(current - timestamp > CALL_LIFETIME)
		    return true;

		String array[] = null;

		if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
		    array = s_databaseHelper.nameSipHashIdFromDigest
			(s_cryptography,
			 Arrays.copyOfRange(aes256, 311, 311 + 64));
		else
		    array = s_databaseHelper.nameSipHashIdFromDigest
			(s_cryptography,
			 Arrays.copyOfRange(aes256, 273, 273 + 64));

		if(array != null && array.length == 2)
		{
		    PublicKey publicKey = null;
		    byte keyStream[] = null;

		    if(aes256[0] == Messages.CALL_HALF_AND_HALF_TAGS[0])
		    {
			publicKey = Cryptography.publicRSAKeyFromBytes
			    (Arrays.copyOfRange(aes256, 9, 9 + 294));

			if(publicKey == null)
			    return true;

			/*
			** Generate new AES-256 and SHA-512 keys.
			*/

			keyStream = Miscellaneous.joinByteArrays
			    (Cryptography.aes256KeyBytes(),
			     Cryptography.sha512KeyBytes());
		    }
		    else if(aes256[0] == Messages.CALL_HALF_AND_HALF_TAGS[1])
		    {
			ParticipantCall participantCall = null;

			m_callQueueMutex.readLock().lock();

			try
			{
			    participantCall = m_callQueue.get(array[1]);
			}
			finally
			{
			    m_callQueueMutex.readLock().unlock();
			}

			if(participantCall == null)
			    return true;

			m_callQueueMutex.writeLock().lock();

			try
			{
			    m_callQueue.remove(array[1]);
			}
			finally
			{
			    m_callQueueMutex.writeLock().unlock();
			}

			keyStream = Cryptography.pkiDecrypt
			    (participantCall.m_keyPair.getPrivate(),
			     Arrays.copyOfRange(aes256, 9, 9 + 256));

			if(keyStream == null)
			    return true;
		    }
		    else
			return true;

		    s_databaseHelper.writeCallKeys
			(s_cryptography, array[1], keyStream);

		    Intent intent = new Intent
			("org.purple.smoke.half_and_half_call");

		    if(aes256[0] == Messages.CALL_HALF_AND_HALF_TAGS[0])
			intent.putExtra("org.purple.smoke.initial", true);
		    else
			intent.putExtra("org.purple.smoke.initial", false);

		    intent.putExtra("org.purple.smoke.name", array[0]);
		    intent.putExtra("org.purple.smoke.refresh", true);
		    intent.putExtra("org.purple.smoke.sipHashId", array[1]);
		    Smoke.getApplication().sendBroadcast(intent);

		    if(aes256[0] == Messages.CALL_HALF_AND_HALF_TAGS[0])
		    {
			/*
			** Respond via all neighbors.
			*/

			bytes = Messages.callMessage
			    (s_cryptography,
			     array[1],
			     Cryptography.pkiEncrypt(publicKey, keyStream),
			     Messages.CALL_HALF_AND_HALF_TAGS[1]);

			if(bytes != null)
			    scheduleSend(Messages.bytesToMessageString(bytes));
		    }

		    return true;
		}
	    }
	}
	catch(Exception exception)
	{
	    return false;
	}

	return false;
    }

    public byte[] messageRetrievalIdentity()
    {
	m_chatMessageRetrievalIdentityMutex.writeLock().lock();

	try
	{
	    if(m_chatMessageRetrievalIdentity == null)
		m_chatMessageRetrievalIdentity = Miscellaneous.deepCopy
		    (Cryptography.randomBytes(64));

	    return m_chatMessageRetrievalIdentity;
	}
	finally
	{
	    m_chatMessageRetrievalIdentityMutex.writeLock().unlock();
	}
    }

    public static synchronized Kernel getInstance()
    {
	if(s_instance == null)
	    s_instance = new Kernel();

	return s_instance;
    }

    public static void writeCongestionDigest(String message)
    {
	s_databaseHelper.writeCongestionDigest
	    (s_congestionSipHash.hmac(message.getBytes()));
    }

    public static void writeCongestionDigest(byte data[])
    {
	s_databaseHelper.writeCongestionDigest
	    (s_congestionSipHash.hmac(data));
    }

    public void clearNeighborQueues()
    {
	m_neighborsMutex.readLock().lock();

	try
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null)
		{
		    m_neighbors.get(j).clearEchoQueue();
		    m_neighbors.get(j).clearQueue();
		}
	    }
	}
	finally
	{
	    m_neighborsMutex.readLock().unlock();
	}
    }

    public void echo(String message, int oid)
    {
	if(!State.getInstance().neighborsEcho() || message.trim().isEmpty())
	    return;

	if(s_databaseHelper.
	   containsCongestionDigest(s_congestionSipHash.hmac(message.
							     getBytes())))
	    return;

	m_neighborsMutex.readLock().lock();

	try
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null &&
		   m_neighbors.get(j).getOid() != oid)
		    m_neighbors.get(j).scheduleEchoSend(message);
	    }
	}
	finally
	{
	    m_neighborsMutex.readLock().unlock();
	}
    }

    public void enqueueChatMessage(String message,
				   String sipHashId,
				   byte keystream[])
    {
	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    MessageElement messageElement = new MessageElement();

	    messageElement.m_message = message;
	    messageElement.m_messageType = MessageElement.CHAT_MESSAGE_TYPE;
	    messageElement.m_sipHashId = sipHashId;
	    messageElement.m_keyStream = Miscellaneous.deepCopy(keystream);
	    m_messagesToSend.add(messageElement);
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}
    }

    public void prepareNeighbors()
    {
	ArrayList<NeighborElement> neighbors =
	    s_databaseHelper.readNeighbors(s_cryptography);

	if(neighbors == null || neighbors.size() == 0)
	{
	    purge();
	    return;
	}

	m_neighborsMutex.writeLock().lock();

	try
	{
	    for(int i = m_neighbors.size() - 1; i >= 0; i--)
	    {
		/*
		** Remove neighbor objects which do not exist in the
		** database.
		*/

		boolean found = false;
		int oid = m_neighbors.keyAt(i);

		for(NeighborElement neighbor : neighbors)
		    if(neighbor != null && neighbor.m_oid == oid)
		    {
			found = true;
			break;
		    }

		if(!found)
		{
		    if(m_neighbors.get(oid) != null)
			m_neighbors.get(oid).abort();

		    m_neighbors.remove(oid);
		}
	    }
	}
	finally
	{
	    m_neighborsMutex.writeLock().unlock();
	}

	for(NeighborElement neighborElement : neighbors)
	{
	    if(neighborElement == null)
		continue;
	    else
	    {
		m_neighborsMutex.readLock().lock();

		try
		{
		    if(m_neighbors.get(neighborElement.m_oid) != null)
			continue;
		}
		finally
		{
		    m_neighborsMutex.readLock().unlock();
		}

		if(neighborElement.m_statusControl.toLowerCase().
		   equals("delete") ||
		   neighborElement.m_statusControl.toLowerCase().
		   equals("disconnect"))
		{
		    if(neighborElement.m_statusControl.toLowerCase().
		       equals("disconnect"))
			s_databaseHelper.saveNeighborInformation
			    (s_cryptography,
			     "0",             // Bytes Read
			     "0",             // Bytes Written
			     "0",             // Queue Size
			     "",              // Error
			     "",              // IP Address
			     "0",             // Port
			     "",              // Session Cipher
			     "disconnected",  // Status
			     "0",             // Uptime
			     String.valueOf(neighborElement.m_oid));

		    continue;
		}
	    }

	    Neighbor neighbor = null;

	    if(neighborElement.m_transport.equals("TCP"))
		neighbor = new TcpNeighbor
		    (neighborElement.m_proxyIpAddress,
		     neighborElement.m_proxyPort,
		     neighborElement.m_proxyType,
		     neighborElement.m_remoteIpAddress,
		     neighborElement.m_remotePort,
		     neighborElement.m_remoteScopeId,
		     neighborElement.m_ipVersion,
		     neighborElement.m_oid);
	    else if(neighborElement.m_transport.equals("UDP"))
	    {
		try
		{
		    InetAddress inetAddress = InetAddress.getByName
			(neighborElement.m_remoteIpAddress);

		    if(inetAddress.isMulticastAddress())
			neighbor = new UdpMulticastNeighbor
			    (neighborElement.m_remoteIpAddress,
			     neighborElement.m_remotePort,
			     neighborElement.m_remoteScopeId,
			     neighborElement.m_ipVersion,
			     neighborElement.m_oid);
		    else
			neighbor = new UdpNeighbor
			    (neighborElement.m_remoteIpAddress,
			     neighborElement.m_remotePort,
			     neighborElement.m_remoteScopeId,
			     neighborElement.m_ipVersion,
			     neighborElement.m_oid);
		}
		catch(Exception exception)
		{
		}
	    }

	    if(neighbor == null)
		continue;

	    m_neighborsMutex.writeLock().lock();

	    try
	    {
		m_neighbors.append(neighborElement.m_oid, neighbor);
	    }
	    finally
	    {
		m_neighborsMutex.writeLock().unlock();
	    }
	}

	neighbors.clear();
    }

    public void retrieveChatMessages()
    {
	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    MessageElement messageElement = new MessageElement();

	    messageElement.m_messageType =
		MessageElement.RETRIEVE_MESSAGES_MESSAGE_TYPE;
	    m_messagesToSend.add(messageElement);
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}
    }

    public void setWakeLock(boolean state)
    {
	if(m_wakeLock == null)
	    try
	    {
		PowerManager powerManager = (PowerManager)
		    Smoke.getApplication().getSystemService
		    (Context.POWER_SERVICE);

		m_wakeLock = powerManager.newWakeLock
		    (PowerManager.PARTIAL_WAKE_LOCK, "SmokeLockTag");
	    }
	    catch(Exception exception)
	    {
	    }

	try
	{
	    if(m_wakeLock != null)
	    {
		if(state)
		{
		    if(m_wakeLock.isHeld())
			m_wakeLock.release();

		    m_wakeLock.acquire();
		}
		else if(m_wakeLock.isHeld())
		    m_wakeLock.release();
	    }
	}
	catch(Exception exception)
	{
	}
    }
}
