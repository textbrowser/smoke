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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.SparseArray;
import java.net.InetAddress;
import java.security.PublicKey;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Kernel
{
    private ArrayList<MessageElement> m_messagesToSend = null;
    private AtomicLong m_chatTemporaryIdentityLastTick = null;
    private Hashtable<String, ParticipantCall> m_callQueue = null;
    private Hashtable<String, byte[]> m_fireStreams = null;
    private ScheduledExecutorService m_callScheduler = null;
    private ScheduledExecutorService m_chatTemporaryIdentityScheduler = null;
    private ScheduledExecutorService m_messagesToSendScheduler = null;
    private ScheduledExecutorService m_neighborsScheduler = null;
    private ScheduledExecutorService m_publishKeysScheduler = null;
    private ScheduledExecutorService m_purgeScheduler = null;
    private ScheduledExecutorService m_requestMessagesScheduler = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private WakeLock m_wakeLock = null;
    private WifiLock m_wifiLock = null;
    private byte m_chatMessageRetrievalIdentity[] = null;
    private final ReentrantReadWriteLock m_callQueueMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_chatMessageRetrievalIdentityMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_fireStreamsMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_messagesToSendMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_neighborsMutex =
	new ReentrantReadWriteLock();
    private final SparseArray<Neighbor> m_neighbors = new SparseArray<> ();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static SimpleDateFormat s_fireSimpleDateFormat =
	new SimpleDateFormat("MMddyyyyHHmmss", Locale.getDefault());
    private final static SipHash s_congestionSipHash = new SipHash
	(Cryptography.randomBytes(SipHash.KEY_LENGTH));
    private final static int CALL_INTERVAL = 250; // 0.250 Seconds
    private final static int CHAT_TEMPORARY_IDENTITY_INTERVAL =
	5000; // 5 Seconds
    private final static int CONGESTION_LIFETIME = 60; // Seconds
    private final static int FIRE_TIME_DELTA = 30000; // 30 Seconds
    private final static int MCELIECE_OUTPUT_SIZES[] = {320, 352, 576, 608};
    private final static int MESSAGES_TO_SEND_INTERVAL =
	100; // 100 Milliseconds
    private final static int NEIGHBORS_INTERVAL = 5000; // 5 Seconds
    private final static int PARTICIPANTS_KEYSTREAMS_LIFETIME =
	864000; // Seconds in ten days.
    private final static int PUBLISH_KEYS_INTERVAL = 15000; // 15 Seconds
    private final static int PURGE_INTERVAL = 30000; // 30 Seconds
    private final static int REQUEST_MESSAGES_INTERVAL = 60000; // 60 Seconds
    private final static int STATUS_INTERVAL = 15000; /*
						      ** Should be less than
						      ** Chat.STATUS_WINDOW.
						      */
    private final static long CALL_LIFETIME = 30000; // 30 Seconds
    private final static long CHAT_TEMPORARY_IDENTITY_LIFETIME =
	60000; // 60 Seconds
    private static Kernel s_instance = null;

    private Kernel()
    {
	m_callQueue = new Hashtable<> ();
	m_chatTemporaryIdentityLastTick = new AtomicLong
	    (System.currentTimeMillis());
	m_fireStreams = new Hashtable<> ();
	m_messagesToSend = new ArrayList<> ();

	try
	{
	    WifiManager wifiManager = (WifiManager)
		Smoke.getApplication().getApplicationContext().
		getSystemService(Context.WIFI_SERVICE);

	    if(wifiManager != null)
		m_wifiLock = wifiManager.createWifiLock
		    (WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SmokeWiFiLockTag");

	    if(m_wifiLock != null)
	    {
		m_wifiLock.setReferenceCounted(false);
		m_wifiLock.acquire();
	    }
	}
	catch(Exception exception)
	{
	}

	prepareSchedulers();
	s_fireSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private void prepareNeighbors()
    {
	ArrayList<NeighborElement> neighbors = purgeDeletedNeighbors();

	if(neighbors == null)
	    return;

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
			}
			finally
			{
			    m_callQueueMutex.writeLock().unlock();
			}

			if(participantCall == null)
			    return;
			else
			    participantCall.preparePrivatePublicKey();

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

			if(isConnected())
			{
			    byte publicKeyType = 'M';

			    if(participantCall.m_algorithm ==
			       ParticipantCall.Algorithms.RSA)
				publicKeyType = 'R';

			    /*
			    ** Place a call request to all neighbors.
			    */

			    byte bytes[] = Messages.callMessage
				(s_cryptography,
				 participantCall.m_sipHashId,
				 participantCall.m_keyPair.getPublic().
				 getEncoded(),
				 publicKeyType,
				 Messages.CALL_HALF_AND_HALF_TAGS[0]);

			    if(bytes != null)
				scheduleSend
				    (Messages.bytesToMessageString(bytes));
			}
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, CALL_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_chatTemporaryIdentityScheduler == null)
	{
	    m_chatTemporaryIdentityScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_chatTemporaryIdentityScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			m_chatMessageRetrievalIdentityMutex.writeLock().lock();

			try
			{
			    if(System.currentTimeMillis() -
			       m_chatTemporaryIdentityLastTick.get() >
			       CHAT_TEMPORARY_IDENTITY_LIFETIME)
				m_chatMessageRetrievalIdentity = null;
			}
			finally
			{
			    m_chatMessageRetrievalIdentityMutex.writeLock().
				unlock();
			}
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, CHAT_TEMPORARY_IDENTITY_INTERVAL, TimeUnit.MILLISECONDS);
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
		    try
		    {
			while(true)
			{
			    MessageElement messageElement = null;

			    m_messagesToSendMutex.writeLock().lock();

			    try
			    {
				if(!m_messagesToSend.isEmpty())
				    messageElement = m_messagesToSend.
					remove(0);
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
			    long timestamp = System.currentTimeMillis();

			    try
			    {
				switch(messageElement.m_messageType)
				{
				case MessageElement.CHAT_MESSAGE_TYPE:
				    bytes = Messages.chatMessage
					(s_cryptography,
					 messageElement.m_message,
					 messageElement.m_id,
					 messageElement.m_attachment,
					 Cryptography.
					 sha512(messageElement.m_id.
						getBytes("UTF-8")),
					 messageElement.m_keyStream,
					 State.getInstance().
					 chatSequence(messageElement.m_id),
					 timestamp);
				    s_databaseHelper.writeParticipantMessage
					(s_cryptography,
					 "local",
					 messageElement.m_message,
					 messageElement.m_id,
					 messageElement.m_attachment,
					 timestamp);

				    Intent intent = new Intent
					("org.purple.smoke.chat_local_message");

				    intent.putExtra
					("org.purple.smoke.message",
					 messageElement.m_message);
				    intent.putExtra
					("org.purple.smoke.sipHashId",
					 messageElement.m_id);

				    LocalBroadcastManager
					localBroadcastManager =
					LocalBroadcastManager.getInstance
					(Smoke.getApplication());

				    localBroadcastManager.sendBroadcast
					(intent);
				    break;
				case MessageElement.FIRE_MESSAGE_TYPE:
				    bytes = Messages.fireMessage
					(s_cryptography,
					 messageElement.m_id,
					 messageElement.m_message,
					 s_databaseHelper.
					 readSetting(s_cryptography,
						     "fire_user_name"),
					 messageElement.m_keyStream);
				    break;
				case MessageElement.FIRE_STATUS_MESSAGE_TYPE:
				    bytes = Messages.fireStatus
					(s_cryptography,
					 messageElement.m_id,
					 s_databaseHelper.
					 readSetting(s_cryptography,
						     "fire_user_name"),
					 messageElement.m_keyStream);
				    break;
				case MessageElement.
				    RETRIEVE_MESSAGES_MESSAGE_TYPE:
				    bytes = Messages.chatMessageRetrieval
					(s_cryptography);
				    break;
				}
			    }
			    catch(Exception exception)
			    {
				bytes = null;
			    }

			    try
			    {
				if(bytes != null)
				{
				    switch(messageElement.m_messageType)
				    {
				    case MessageElement.CHAT_MESSAGE_TYPE:
					enqueueMessage
					    (Messages.
					     bytesToMessageString(bytes));
					State.getInstance().
					    incrementChatSequence
					    (messageElement.m_id);
					break;
				    case MessageElement.FIRE_MESSAGE_TYPE:
					enqueueMessage
					    (Messages.
					     bytesToMessageStringNonBase64
					     (bytes));
					break;
				    case MessageElement.
					FIRE_STATUS_MESSAGE_TYPE:
					scheduleSend
					    (Messages.
					     bytesToMessageStringNonBase64
					     (bytes));
					break;
				    case MessageElement.
					RETRIEVE_MESSAGES_MESSAGE_TYPE:
					scheduleSend
					    (Messages.
					     identityMessage
					     (messageRetrievalIdentity()));
					scheduleSend
					    (Messages.
					     bytesToMessageString(bytes));
					break;
				    }
				}
			    }
			    catch(Exception exception)
			    {
			    }

			    if(messageElement.m_messageType ==
			       MessageElement.CHAT_MESSAGE_TYPE)
				if(s_cryptography.ozoneMacKey() != null)
				{
				    bytes = Messages.chatMessage
					(s_cryptography,
					 messageElement.m_message,
					 messageElement.m_id,
					 messageElement.m_attachment,
					 null,
					 messageElement.m_keyStream,
					 State.getInstance().
					 chatSequence(messageElement.m_id),
					 timestamp);

				    if(bytes != null)
					enqueueMessage
					    ("OZONE-" + Base64.
					     encodeToString(bytes,
							    Base64.NO_WRAP));
				}
			}
		    }
		    catch(Exception exception)
		    {
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
		    try
		    {
			prepareNeighbors();
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, NEIGHBORS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_publishKeysScheduler == null)
	{
	    m_publishKeysScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_publishKeysScheduler.scheduleAtFixedRate(new Runnable()
	    {
		private byte m_state = 0x00;

		@Override
		public void run()
		{
		    try
		    {
			if(!isConnected())
			    return;

			if(m_state == 0x00)
			{
			    /*
			    ** EPKS!
			    */

			    m_state = 0x01;

			    ArrayList<SipHashIdElement> arrayList =
				s_databaseHelper.readNonSharedSipHashIds
				(s_cryptography);

			    if(arrayList == null)
				arrayList = new ArrayList<> ();

			    {
				/*
				** Self-sending.
				*/

				SipHashIdElement sipHashIdElement =
				    new SipHashIdElement();

				sipHashIdElement.m_sipHashId = s_cryptography.
				    sipHashId();
				sipHashIdElement.m_stream = Miscellaneous.
				    joinByteArrays(s_cryptography.
						   sipHashEncryptionKey(),
						   s_cryptography.
						   sipHashMacKey());
				arrayList.add(sipHashIdElement);
			    }

			    for(SipHashIdElement sipHashIdElement : arrayList)
			    {
				if(sipHashIdElement == null)
				    continue;

				byte bytes[] = Messages.epksMessage
				    (s_cryptography,
				     sipHashIdElement.m_sipHashId,
				     sipHashIdElement.m_stream,
				     Messages.CHAT_KEY_TYPE);

				if(bytes != null)
				    enqueueMessage
					(Messages.bytesToMessageString(bytes));
			    }
			}
			else
			{
			    /*
			    ** Request keys!
			    */

			    m_state = 0x00;

			    ArrayList<SipHashIdElement> arrayList =
				s_databaseHelper.readNonSharedSipHashIds
				(s_cryptography);

			    if(arrayList != null)
				for(SipHashIdElement sipHashIdElement :
					arrayList)
				{
				    if(sipHashIdElement == null)
					continue;

				    byte bytes[] = Messages.pkpRequestMessage
					(s_cryptography,
					 sipHashIdElement.m_sipHashId);

				    if(bytes != null)
					enqueueMessage
					    (Messages.
					     bytesToMessageString(bytes));
				}
			}
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, PUBLISH_KEYS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_purgeScheduler == null)
	{
	    m_purgeScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_purgeScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			s_databaseHelper.purgeCongestion(CONGESTION_LIFETIME);
			s_databaseHelper.purgeParticipantsKeyStreams
			    (PARTICIPANTS_KEYSTREAMS_LIFETIME);
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, PURGE_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_requestMessagesScheduler == null)
	{
	    m_requestMessagesScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_requestMessagesScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    if(isConnected() && s_cryptography.ozoneMacKey() != null)
			retrieveChatMessages();
		}
	    }, 10000, REQUEST_MESSAGES_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_statusScheduler == null)
	{
	    m_statusScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_statusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			if(!isConnected())
			    return;

			ArrayList<ParticipantElement> arrayList =
			    s_databaseHelper.
			    readParticipants(s_cryptography, "");

			if(arrayList == null || arrayList.isEmpty())
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
		    catch(Exception exception)
		    {
		    }
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

    public ArrayList<NeighborElement> purgeDeletedNeighbors()
    {
	ArrayList<NeighborElement> neighbors =
	    s_databaseHelper.readNeighbors(s_cryptography);

	if(neighbors == null || neighbors.isEmpty())
	{
	    purge();
	    return neighbors;
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

	return neighbors;
    }

    public String fireIdentities()
    {
	try
	{
	    m_fireStreamsMutex.readLock().lock();

	    if(!m_fireStreams.isEmpty())
	    {
		StringBuilder stringBuilder = new StringBuilder();

		for(Hashtable.Entry<String, byte[]> entry :
			m_fireStreams.entrySet())
		{
		    if(entry.getValue() == null)
			continue;

		    stringBuilder.append
			(Messages.
			 identityMessage(Cryptography.
					 sha512(Arrays.
						copyOfRange(entry.getValue(),
							    80,
							    entry.getValue().
							    length))));
		}

		return stringBuilder.toString();
	    }
	}
	finally
	{
	    m_fireStreamsMutex.readLock().unlock();
	}

	return "";
    }

    public boolean call(int participantOid,
			ParticipantCall.Algorithms algorithm,
			String sipHashId)
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
		(sipHashId, new ParticipantCall(algorithm,
						sipHashId,
						participantOid));
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

	if(arrayList == null || arrayList.isEmpty())
	    return false;

	for(int i = 0; i < arrayList.size(); i++)
	    if(arrayList.get(i) != null &&
	       arrayList.get(i).m_statusControl.toLowerCase().equals("connect"))
		s_databaseHelper.enqueueOutboundMessage
		    (s_cryptography, message, arrayList.get(i).m_oid);

	arrayList.clear();
	return true;
    }

    public boolean igniteFire(String name)
    {
	m_fireStreamsMutex.writeLock().lock();

	try
	{
	    if(!m_fireStreams.containsKey(name))
	    {
		byte bytes[] = s_databaseHelper.fireStream
		    (s_cryptography, name);

		if(bytes != null)
		{
		    m_fireStreams.put(name, bytes);
		    return true;
		}
	    }
	}
	finally
	{
	    m_fireStreamsMutex.writeLock().unlock();
	}

	return false;
    }

    public boolean isConnected()
    {
	try
	{
	    ConnectivityManager connectivityManager = (ConnectivityManager)
		Smoke.getApplication().getApplicationContext().
		getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = connectivityManager.
		getActiveNetworkInfo();

	    if(networkInfo.getState() !=
	       android.net.NetworkInfo.State.CONNECTED)
		return false;
	}
	catch(Exception exception)
	{
	}

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

    public boolean wakeLocked()
    {
	if(m_wakeLock != null)
	    return m_wakeLock.isHeld();

	return false;
    }

    public boolean wifiLocked()
    {
	if(m_wifiLock != null)
	    return m_wifiLock.isHeld();

	return false;
    }

    public byte[] messageRetrievalIdentity()
    {
	m_chatMessageRetrievalIdentityMutex.writeLock().lock();

	try
	{
	    if(m_chatMessageRetrievalIdentity == null)
	    {
		m_chatMessageRetrievalIdentity = Miscellaneous.deepCopy
		    (Cryptography.randomBytes(64));
		m_chatTemporaryIdentityLastTick.set(System.currentTimeMillis());
	    }

	    return m_chatMessageRetrievalIdentity;
	}
	finally
	{
	    m_chatMessageRetrievalIdentityMutex.writeLock().unlock();
	}
    }

    public int ourMessage(String buffer)
    {
	long value = s_congestionSipHash.hmac(buffer.getBytes());

	if(s_databaseHelper.containsCongestionDigest(value))
	    return 1;
	else if(s_databaseHelper.writeCongestionDigest(value))
	    return 1;

	try
	{
	    m_fireStreamsMutex.readLock().lock();

	    try
	    {
		if(!m_fireStreams.isEmpty())
		{
		    String strings[] = Messages.stripMessage(buffer).
			split("\\n");

		    if(strings != null && strings.length >= 2)
		    {
			byte aes256[] = Base64.decode
			    (strings[0], Base64.NO_WRAP);
			byte sha384[] = Base64.decode
			    (strings[1], Base64.NO_WRAP);

			for(Hashtable.Entry<String, byte[]> entry :
				m_fireStreams.entrySet())
			{
			    if(entry.getValue() == null)
				continue;

			    if(Cryptography.
			       memcmp(Cryptography.
				      hmacFire(aes256,
					       Arrays.
					       copyOfRange(entry.getValue(),
							   32,
							   80)),
				      sha384))
			    {
				aes256 = Cryptography.decryptFire
				    (aes256,
				     Arrays.copyOfRange(entry.getValue(),
							0,
							32));

				if(aes256 == null)
				    return 1;

				aes256 = Arrays.copyOfRange

				    /*
				    ** Remove the size information of the
				    ** original data.
				    */

				    (aes256, 0, aes256.length - 4);
				strings = new String(aes256).split("\\n");

				if(!(strings.length == 4 ||
				     strings.length == 5))
				    return 1;

				strings[strings.length - 1] = new
				    String(Base64.
					   decode(strings[strings.length - 1],
						  Base64.NO_WRAP));

				Date date = s_fireSimpleDateFormat.parse
				    (strings[strings.length - 1]);
				Timestamp timestamp = new Timestamp
				    (date.getTime());
				long current = System.currentTimeMillis();

				if(Math.abs(current - timestamp.getTime()) >
				   FIRE_TIME_DELTA)
				    return 1;

				for(int i = 0; i < strings.length - 1; i++)
				    strings[i] = new String
					(Base64.decode(strings[i],
						       Base64.NO_WRAP),
					 "UTF-8");

				value = s_congestionSipHash.hmac
				    (("fire" +
				      entry.getKey() +
				      strings[2] +
				      strings[3] +
				      timestamp).getBytes());

				if(s_databaseHelper.
				   writeCongestionDigest(value))
				    return 1;

				Intent intent = new Intent
				    ("org.purple.smoke.fire_message");

				intent.putExtra
				    ("org.purple.smoke.channel",
				     entry.getKey());
				intent.putExtra
				    ("org.purple.smoke.id", strings[2]);
				intent.putExtra
				    ("org.purple.smoke.message_type",
				     strings[0]);
				intent.putExtra
				    ("org.purple.smoke.name", strings[1]);

				if(strings[0].
				   equals(Messages.FIRE_CHAT_MESSAGE_TYPE))
				    intent.putExtra
					("org.purple.smoke.message",
					 strings[3]);

				LocalBroadcastManager localBroadcastManager =
				    LocalBroadcastManager.getInstance
				    (Smoke.getApplication());

				localBroadcastManager.sendBroadcast(intent);
				return 2; // Echo Fire!
			    }
			}
		    }
		}
	    }
	    finally
	    {
		m_fireStreamsMutex.readLock().unlock();
	    }

	    byte bytes[] =
		Base64.decode(Messages.stripMessage(buffer), Base64.DEFAULT);

	    if(bytes == null || bytes.length < 128)
		return 0;

	    boolean ourMessageViaChatTemporaryIdentity = false; /*
								** Did the
								** message
								** arrive from
								** SmokeStack?
								*/
	    byte array1[] = Arrays.copyOfRange // Blocks #1, #2, etc.
		(bytes, 0, bytes.length - 128);
	    byte array2[] = Arrays.copyOfRange // Second to the last block.
		(bytes, bytes.length - 128, bytes.length - 64);
	    byte array3[] = Arrays.copyOfRange // The last block (destination).
		(bytes, bytes.length - 64, bytes.length);

	    m_chatMessageRetrievalIdentityMutex.readLock().lock();

	    try
	    {
		if(m_chatMessageRetrievalIdentity != null)
		    if(Cryptography.
		       memcmp(Cryptography.hmac(Arrays.
						copyOfRange(bytes,
							    0,
							    bytes.length - 64),
						m_chatMessageRetrievalIdentity),
			      array3))
		    {
			m_chatTemporaryIdentityLastTick.set
			    (System.currentTimeMillis());
			ourMessageViaChatTemporaryIdentity = true;
		    }
	    }
	    finally
	    {
		m_chatMessageRetrievalIdentityMutex.readLock().unlock();
	    }

	    if(!ourMessageViaChatTemporaryIdentity)
		if(!s_cryptography.
		   iAmTheDestination(Arrays.copyOfRange(bytes,
							0,
							bytes.length - 64),
				     array3))
		    return 0;

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

		    State.getInstance().populateParticipants();

		    Intent intent = new Intent
			("org.purple.smoke.populate_participants");
		    LocalBroadcastManager localBroadcastManager =
			LocalBroadcastManager.
			getInstance(Smoke.getApplication());

		    localBroadcastManager.sendBroadcast(intent);

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

		return 1;
	    }

	    byte pk[] = null;
	    int mceliece_output_size = 0;

	    if(s_cryptography.chatEncryptionPublicKeyAlgorithm().
	       equals("McEliece-CCA2"))
	    {
		int e = 0;
		int s = 0;
		int t = s_cryptography.chatEncryptionPublicKeyT();

		if(t == 50)
		    e = 2;
		else
		{
		    e = 4;
		    s = 2;
		}

		for(int i = s; i < e; i++)
		{
		    pk = s_cryptography.pkiDecrypt
			(Arrays.copyOfRange(bytes,
					    0,
					    MCELIECE_OUTPUT_SIZES[i]));

		    if(pk != null)
		    {
			mceliece_output_size = MCELIECE_OUTPUT_SIZES[i];
			break;
		    }
		}
	    }
	    else
		pk = s_cryptography.pkiDecrypt
		    (Arrays.
		     copyOfRange(bytes,
				 0,
				 Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8));

	    if(pk == null)
		return 1;

	    if(pk.length == 64)
	    {
		/*
		** Chat, Chat Status
		*/

		byte keyStream[] = s_databaseHelper.participantKeyStream
		    (s_cryptography, pk);

		if(keyStream == null)
		    return 1;

		byte sha512[] = Cryptography.hmac
		    (Arrays.copyOfRange(bytes, 0, bytes.length - 128),
		     Arrays.copyOfRange(keyStream, 32, keyStream.length));

		if(!Cryptography.memcmp(array2, sha512))
		{
		    if(ourMessageViaChatTemporaryIdentity)
		    {
			keyStream = s_databaseHelper.participantKeyStream
			    (s_cryptography, pk, array2, bytes);

			if(keyStream == null)
			    return 1;
		    }
		    else
			return 1;
		}

		byte aes256[] = null;

		if(s_cryptography.chatEncryptionPublicKeyAlgorithm().
		   equals("McEliece-CCA2"))
		    aes256 = Cryptography.decrypt
			(Arrays.
			 copyOfRange(bytes,
				     mceliece_output_size,
				     bytes.length - 128),
			 Arrays.copyOfRange(keyStream, 0, 32));
		else
		    aes256 = Cryptography.decrypt
			(Arrays.
			 copyOfRange(bytes,
				     Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8,
				     bytes.length - 128),
			 Arrays.copyOfRange(keyStream, 0, 32));

		if(aes256 == null)
		    return 1;

		byte abyte[] = new byte[] {aes256[0]};

		if(abyte[0] == Messages.CHAT_STATUS_MESSAGE_TYPE[0])
		{
		    String array[] = s_databaseHelper.nameSipHashIdFromDigest
			(s_cryptography, pk);

		    if(array == null || array.length != 2)
			return 1;

		    String sipHashId = array[1];

		    if(s_databaseHelper.readParticipantOptions(s_cryptography,
							       sipHashId).
		       contains("optional_signatures = false"))
		    {
			PublicKey signatureKey = s_databaseHelper.
			    signatureKeyForDigest(s_cryptography, pk);

			if(signatureKey == null)
			    return 1;

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
			    return 1;
		    }

		    long current = System.currentTimeMillis();
		    long timestamp = Miscellaneous.byteArrayToLong
			(Arrays.copyOfRange(aes256, 1, 1 + 8));

		    if(current - timestamp < 0)
		    {
			if(timestamp - current > Chat.STATUS_WINDOW)
			    return 1;
		    }
		    else if(current - timestamp > Chat.STATUS_WINDOW)
			return 1;

		    s_databaseHelper.updateParticipantLastTimestamp
			(s_cryptography, pk);
		    return 1;
		}

		aes256 = Arrays.copyOfRange(aes256, 1, aes256.length);

		String strings[] = new String(aes256).split("\\n");

		if(strings.length != Messages.CHAT_GROUP_TWO_ELEMENT_COUNT)
		    return 1;

		String message = null;
		boolean updateTimeStamp = true;
		byte attachment[] = null;
		byte publicKeySignature[] = null;
		int ii = 0;
		long sequence = 0;
		long timestamp = 0;

		for(String string : strings)
		    switch(ii)
		    {
		    case 0:
			timestamp = Miscellaneous.byteArrayToLong
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP));

			long current = System.currentTimeMillis();

			if(current - timestamp < 0)
			{
			    if(timestamp - current > Chat.CHAT_WINDOW)
				updateTimeStamp = false;
			}
			else if(current - timestamp > Chat.CHAT_WINDOW)
			    updateTimeStamp = false;

			if(!updateTimeStamp)
			    /*
			    ** Ignore expired messages unless the messages
			    ** were discharged by SmokeStack per our
			    ** temporary identity.
			    */

			    if(!ourMessageViaChatTemporaryIdentity)
				return 1;

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
			attachment = Miscellaneous.
			    decompressed(Base64.decode(string.getBytes(),
						       Base64.NO_WRAP));
			ii += 1;
			break;
		    case 4:
			String array[] = s_databaseHelper.
			    nameSipHashIdFromDigest(s_cryptography, pk);

			if(array == null || array.length != 2)
			    return 1;

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
				return 1;

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
				 strings[3].getBytes(),
				 "\n".getBytes(),
				 s_cryptography.
				 chatEncryptionPublicKeyDigest())))
				return 1;
			}

			strings = array;
			break;
		    }

		if(message == null)
		    return 1;

		if(updateTimeStamp)
		    s_databaseHelper.updateParticipantLastTimestamp
			(s_cryptography, strings[1]);

		value = s_congestionSipHash.hmac
		    (("chat" + message + strings[1] + timestamp).getBytes());

		if(s_databaseHelper.writeCongestionDigest(value))
		    return 1;

		if(s_databaseHelper.
		   writeParticipantMessage(s_cryptography,
					   ourMessageViaChatTemporaryIdentity ?
					   "true" : "false",
					   message,
					   strings[1],
					   attachment,
					   timestamp) !=
		   Database.ExceptionLevels.EXCEPTION_PERMISSIBLE)
		{
		    Intent intent = new Intent
			("org.purple.smoke.chat_message");

		    intent.putExtra("org.purple.smoke.message", message);
		    intent.putExtra("org.purple.smoke.name", strings[0]);
		    intent.putExtra
			("org.purple.smoke.purple",
			 ourMessageViaChatTemporaryIdentity);
		    intent.putExtra("org.purple.smoke.sequence", sequence);
		    intent.putExtra("org.purple.smoke.sipHashId", strings[1]);
		    intent.putExtra("org.purple.smoke.timestamp", timestamp);

		    LocalBroadcastManager localBroadcastManager =
			LocalBroadcastManager.getInstance
			(Smoke.getApplication());

		    localBroadcastManager.sendBroadcast(intent);
		}

		return 1;
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
		    return 1;

		byte aes256[] = null;

		if(s_cryptography.chatEncryptionPublicKeyAlgorithm().
		   equals("McEliece-CCA2"))
		    aes256 = Cryptography.decrypt
			(Arrays.
			 copyOfRange(bytes,
				     mceliece_output_size,
				     bytes.length - 128),
			 Arrays.copyOfRange(pk, 0, 32));
		else
		    aes256 = Cryptography.decrypt
			(Arrays.
			 copyOfRange(bytes,
				     Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8,
				     bytes.length - 128),
			 Arrays.copyOfRange(pk, 0, 32));

		if(aes256 == null)
		    return 1;

		byte tag = aes256[0];

		if(!(tag == Messages.CALL_HALF_AND_HALF_TAGS[0] ||
		     tag == Messages.CALL_HALF_AND_HALF_TAGS[1]))
		    return 1;

		aes256 = Arrays.copyOfRange(aes256, 1, aes256.length);

		String strings[] = new String(aes256).split("\\n");

		if(strings.length != Messages.CALL_GROUP_TWO_ELEMENT_COUNT)
		    return 1;

		byte ephemeralPublicKey[] = null;
		byte ephemeralPublicKeyType[] = null;
		byte publicKeySignature[] = null;
		byte senderPublicEncryptionKeyDigest[] = null;
		int ii = 0;
		long timestamp = 0;

		for(String string : strings)
		    switch(ii)
		    {
		    case 0:
			long current = System.currentTimeMillis();

			timestamp = Miscellaneous.byteArrayToLong
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP));

			if(current - timestamp < 0)
			{
			    if(timestamp - current > CALL_LIFETIME)
				return 1;
			}
			else if(current - timestamp > CALL_LIFETIME)
			    return 1;

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
			ii += 1;
			break;
		    case 4:
			senderPublicEncryptionKeyDigest = Base64.
			    decode(string.getBytes(), Base64.NO_WRAP);
			ii += 1;
			break;
		    case 5:
			publicKeySignature = Base64.decode
			    (string.getBytes(), Base64.NO_WRAP);

			PublicKey signatureKey = s_databaseHelper.
			    signatureKeyForDigest
			    (s_cryptography, senderPublicEncryptionKeyDigest);

			if(signatureKey == null)
			    return 1;

			if(!Cryptography.
			   verifySignature(signatureKey,
					   publicKeySignature,
					   Miscellaneous.
					   joinByteArrays
					   (pk,
					    new byte[] {tag},
					    strings[0].getBytes(),
					    "\n".getBytes(),
					    strings[1].getBytes(),
					    "\n".getBytes(),
					    strings[2].getBytes(),
					    "\n".getBytes(),
					    strings[3].getBytes(),
					    "\n".getBytes(),
					    strings[4].getBytes(),
					    "\n".getBytes(),
					    s_cryptography.
					    chatEncryptionPublicKeyDigest())))
			    return 1;

			ii += 1;
			break;
		    }

		String array[] = s_databaseHelper.nameSipHashIdFromDigest
		    (s_cryptography, senderPublicEncryptionKeyDigest);

		if(array != null && array.length == 2)
		{
		    PublicKey publicKey = null;
		    byte keyStream[] = null;

		    if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
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
			{
			    if(ephemeralPublicKeyType[0] == 'M')
				publicKey = Cryptography.publicKeyFromBytes
				    (ephemeralPublicKey);
			    else
				publicKey = Cryptography.publicRSAKeyFromBytes
				    (ephemeralPublicKey);

			    if(publicKey == null)
				return 1;

			    /*
			    ** Generate new AES-256 and SHA-512 keys.
			    */

			    keyStream = Miscellaneous.joinByteArrays
				(Cryptography.aes256KeyBytes(),
				 Cryptography.sha512KeyBytes());
			}
			else
			{
			    /*
			    ** We're busy!
			    */

			    m_callQueueMutex.writeLock().lock();

			    try
			    {
				m_callQueue.remove(array[1]);
			    }
			    finally
			    {
				m_callQueueMutex.writeLock().unlock();
			    }

			    Intent intent = new Intent
				("org.purple.smoke.busy_call");

			    intent.putExtra("org.purple.smoke.name", array[0]);
			    intent.putExtra
				("org.purple.smoke.sipHashId", array[1]);

			    LocalBroadcastManager localBroadcastManager =
				LocalBroadcastManager.getInstance
				(Smoke.getApplication());

			    localBroadcastManager.sendBroadcast(intent);
			    return 1;
			}
		    }
		    else if(tag == Messages.CALL_HALF_AND_HALF_TAGS[1])
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
			    return 1;

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
			     ephemeralPublicKey);

			if(keyStream == null)
			    return 1;
		    }
		    else
			return 1;

		    s_databaseHelper.writeCallKeys
			(s_cryptography, array[1], keyStream);

		    Intent intent = new Intent
			("org.purple.smoke.half_and_half_call");

		    if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
			intent.putExtra("org.purple.smoke.initial", true);
		    else
			intent.putExtra("org.purple.smoke.initial", false);

		    intent.putExtra("org.purple.smoke.name", array[0]);
		    intent.putExtra("org.purple.smoke.refresh", true);
		    intent.putExtra("org.purple.smoke.sipHashId", array[1]);

		    LocalBroadcastManager localBroadcastManager =
			LocalBroadcastManager.getInstance
			(Smoke.getApplication());

		    localBroadcastManager.sendBroadcast(intent);

		    if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
		    {
			/*
			** Respond via all neighbors.
			*/

			bytes = Messages.callMessage
			    (s_cryptography,
			     array[1],
			     Cryptography.pkiEncrypt(publicKey, keyStream),
			     (byte) '0', // Public Key Type
			     Messages.CALL_HALF_AND_HALF_TAGS[1]);

			if(bytes != null)
			    scheduleSend(Messages.bytesToMessageString(bytes));
		    }

		    return 1;
		}
	    }
	}
	catch(Exception exception)
	{
	    return 0;
	}

	return 0;
    }

    public long callTimeRemaining(String sipHashId)
    {
	m_callQueueMutex.readLock().lock();

	try
	{
	    if(m_callQueue.containsKey(sipHashId))
		return
		    Math.abs
		    (CALL_LIFETIME / 1000 - (System.nanoTime() -
					     m_callQueue.get(sipHashId).
					     m_startTime) / 1000000000);
	}
	finally
	{
	    m_callQueueMutex.readLock().unlock();
	}

	return 0;
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

    public void echoForce(String message, int oid)
    {
	if(message.trim().isEmpty())
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
				   byte imageBytes[],
				   byte keystream[])
    {
	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    MessageElement messageElement = new MessageElement();

	    messageElement.m_attachment = Miscellaneous.deepCopy(imageBytes);
	    messageElement.m_id = sipHashId;
	    messageElement.m_keyStream = Miscellaneous.deepCopy(keystream);
	    messageElement.m_message = message;
	    messageElement.m_messageType = MessageElement.CHAT_MESSAGE_TYPE;
	    m_messagesToSend.add(messageElement);
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}
    }

    public void enqueueFireMessage(String message, String id, String name)
    {
	byte keystream[] = null;

	m_fireStreamsMutex.readLock().lock();

	try
	{
	    if(m_fireStreams.containsKey(name))
		keystream = m_fireStreams.get(name);
	}
	finally
	{
	    m_fireStreamsMutex.readLock().unlock();
	}

	if(keystream == null)
	    return;

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    MessageElement messageElement = new MessageElement();

	    messageElement.m_id = id;
	    messageElement.m_keyStream = Miscellaneous.deepCopy(keystream);
	    messageElement.m_message = message;
	    messageElement.m_messageType = MessageElement.FIRE_MESSAGE_TYPE;
	    m_messagesToSend.add(messageElement);
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}
    }

    public void enqueueFireStatus(String id, String name)
    {
	byte keystream[] = null;

	m_fireStreamsMutex.readLock().lock();

	try
	{
	    if(m_fireStreams.containsKey(name))
		keystream = m_fireStreams.get(name);
	}
	finally
	{
	    m_fireStreamsMutex.readLock().unlock();
	}

	if(keystream == null)
	    return;

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    MessageElement messageElement = new MessageElement();

	    messageElement.m_id = id;
	    messageElement.m_keyStream = Miscellaneous.deepCopy(keystream);
	    messageElement.m_messageType =
		MessageElement.FIRE_STATUS_MESSAGE_TYPE;
	    m_messagesToSend.add(messageElement);
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}
    }

    public void extinguishFire(String name)
    {
	m_fireStreamsMutex.writeLock().lock();

	try
	{
	    m_fireStreams.remove(name);
	}
	finally
	{
	    m_fireStreamsMutex.writeLock().unlock();
	}
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
		    Smoke.getApplication().getApplicationContext().
		    getSystemService(Context.POWER_SERVICE);

		if(powerManager != null)
		    m_wakeLock = powerManager.newWakeLock
			(PowerManager.PARTIAL_WAKE_LOCK, "SmokeWakeLockTag");

		if(m_wakeLock != null)
		    m_wakeLock.setReferenceCounted(false);
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
