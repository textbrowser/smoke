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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.SparseArray;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant;

public class Kernel
{
    private class KernelBroadcastReceiver extends BroadcastReceiver
    {
	public KernelBroadcastReceiver()
	{
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
	    if(intent == null || intent.getAction() == null)
		return;

	    switch(intent.getAction())
	    {
	    case "org.purple.smoke.steam_read_interval_change":
		int oid = -1;
		int readInterval = 4;

		try
		{
		    oid = intent.getIntExtra("org.purple.smoke.extra1", oid);
		    readInterval = intent.getIntExtra
			("org.purple.smoke.extra2", readInterval);
		}
		catch(Exception exception)
		{
		}

		if(oid != -1)
		{
		    /*
		    ** Discover the Steam reader and prepare its new read
		    ** interval.
		    */

		    m_steamsMutex.readLock().lock();

		    try
		    {
			int size = m_steams.size();

			for(int i = 0; i < size; i++)
			{
			    int j = m_steams.keyAt(i);

			    if(m_steams.get(j) != null &&
			       m_steams.get(j).getOid() == oid)
			    {
				m_steams.get(j).setReadInterval(readInterval);
				return;
			    }
			}
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_steamsMutex.readLock().unlock();
		    }
		}

		break;
	    default:
		break;
	    }
	}
    }

    private ArrayList<MessageElement> m_messagesToSend = null;
    private AtomicLong m_chatTemporaryIdentityLastTick = null;
    private AtomicLong m_shareSipHashIdIdentity = null;
    private AtomicLong m_shareSipHashIdIdentityLastTick = null;
    private ConcurrentHashMap<Integer, Neighbor> m_neighbors = null;
    private ConcurrentHashMap<String, Juggernaut> m_juggernauts = null;
    private ConcurrentHashMap<String, ParticipantCall> m_callQueue = null;
    private ConcurrentHashMap<String, byte[]> m_fireStreams = null;
    private ScheduledExecutorService m_callScheduler = null;
    private ScheduledExecutorService m_messagesToSendScheduler = null;
    private ScheduledExecutorService m_neighborsScheduler = null;
    private ScheduledExecutorService m_networkStatusScheduler = null;
    private ScheduledExecutorService m_publishKeysScheduler = null;
    private ScheduledExecutorService m_purgeScheduler = null;
    private ScheduledExecutorService m_requestMessagesScheduler = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private ScheduledExecutorService m_steamScheduler = null;
    private ScheduledExecutorService m_temporaryIdentityScheduler = null;
    private SteamKeyExchange m_steamKeyExchange = null;
    private Time m_time = null;
    private WakeLock m_wakeLock = null;
    private WifiLock m_wifiLock = null;
    private byte m_chatMessageRetrievalIdentity[] = null;
    private final KernelBroadcastReceiver m_receiver =
	new KernelBroadcastReceiver();
    private final Object m_callSchedulerMutex = new Object();
    private final Object m_messagesToSendSchedulerMutex = new Object();
    private final ReentrantReadWriteLock m_chatMessageRetrievalIdentityMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_messagesToSendMutex =
	new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_steamsMutex =
	new ReentrantReadWriteLock();
    private final SparseArray<SteamReader> m_steams = new SparseArray<> ();
    private final SteamWriter m_steamWriter = new SteamWriter();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static SimpleDateFormat s_fireSimpleDateFormat =
	new SimpleDateFormat("MMddyyyyHHmmss", Locale.getDefault());
    private final static SipHash s_congestionSipHash = new SipHash
	(Cryptography.randomBytes(SipHash.KEY_LENGTH));
    private final static int CONGESTION_LIFETIME = 65; // 65 seconds.
    private final static int FIRE_TIME_DELTA = 30000; // 30 seconds.
    private final static int MCELIECE_OUTPUT_SIZES[] = {304,   // 48 bytes.
							320,   // 64 bytes.
							352,   // 96 bytes.
							491,   // 48 bytes.
							507,   // 64 bytes.
							539,   // 96 bytes.
							560,   // 48 bytes.
							576,   // 64 bytes.
							608,   // 96 bytes.
							1072,  // 48 bytes.
							1088,  // 64 bytes.
							1120}; // 96 bytes.
    private final static int PARTICIPANTS_KEYSTREAMS_LIFETIME =
	864000; // Seconds in ten days.
    private final static long CALL_INTERVAL = 250L; // 0.250 seconds.
    private final static long CALL_LIFETIME = 30000L; // 30 seconds.
    private final static long JUGGERNAUT_LIFETIME = 15000L; // 15 seconds.
    private final static long JUGGERNAUT_WINDOW = 10000L; // 10 seconds.
    private final static long MESSAGES_TO_SEND_INTERVAL =
	50L; // 50 milliseconds.
    private final static long NEIGHBORS_INTERVAL = 5000L; // 5 seconds.
    private final static long NETWORK_STATUS_INTERVAL = 1500L; // 1.5 seconds.
    private final static long PUBLISH_KEYS_INTERVAL = 45000L; // 45 seconds.
    private final static long PURGE_INTERVAL = 5000L; // 5 seconds.
    private final static long REQUEST_MESSAGES_INTERVAL = 60000L; // 60 seconds.
    private final static long SHARE_SIPHASH_ID_CONFIRMATION_WINDOW =
	15000L; // 15 seconds.
    private final static long STATUS_INTERVAL = 15000L; /*
							** Should be less than
							** Chat.STATUS_WINDOW.
							*/
    private final static long STEAM_INTERVAL = 5000L; // 5 seconds.
    private final static long STEAM_SHARE_WINDOW = 15000L; // 15 seconds.
    private final static long TEMPORARY_IDENTITY_INTERVAL = 5000L; // 5 seconds.
    private final static long TEMPORARY_IDENTITY_LIFETIME =
	60000L; // 60 seconds.
    private final static long WAIT_TIMEOUT = 10000L; // 10 seconds.
    private static Kernel s_instance = null;
    public final static long JUGGERNAUT_DELAY = 7500L; // 7.5 seconds.

    private Kernel()
    {
	m_callQueue = new ConcurrentHashMap<> ();
	m_chatTemporaryIdentityLastTick = new AtomicLong
	    (System.currentTimeMillis());
	m_fireStreams = new ConcurrentHashMap<> ();
	m_juggernauts = new ConcurrentHashMap<> ();
	m_messagesToSend = new ArrayList<> ();
	m_neighbors = new ConcurrentHashMap<> ();
	m_shareSipHashIdIdentity = new AtomicLong(0L);
	m_shareSipHashIdIdentityLastTick = new AtomicLong
	    (System.currentTimeMillis());
	m_steamKeyExchange = new SteamKeyExchange();
	m_time = new Time();

	try
	{
	    LocalBroadcastManager.getInstance(Smoke.getApplication()).
		unregisterReceiver(m_receiver);

	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction
		("org.purple.smoke.steam_read_interval_change");
	    LocalBroadcastManager.getInstance(Smoke.getApplication()).
		registerReceiver(m_receiver, intentFilter);
	}
	catch(Exception exception)
	{
	}

	try
	{
	    PowerManager powerManager = (PowerManager)
		Smoke.getApplication().getApplicationContext().
		getSystemService(Context.POWER_SERVICE);

	    if(powerManager != null)
		m_wakeLock = powerManager.newWakeLock
		    (PowerManager.PARTIAL_WAKE_LOCK,
		     "Smoke:SmokeWakeLockTag");

	    if(m_wakeLock != null)
		m_wakeLock.setReferenceCounted(false);
	}
	catch(Exception exception)
	{
	}

	try
	{
	    WifiManager wifiManager = (WifiManager)
		Smoke.getApplication().getApplicationContext().
		getSystemService(Context.WIFI_SERVICE);

	    if(wifiManager != null)
		m_wifiLock = wifiManager.createWifiLock
		    (WifiManager.WIFI_MODE_FULL_HIGH_PERF,
		     "Smoke:SmokeWiFiLockTag");

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
	if(!State.getInstance().isAuthenticated())
	    return;

	ArrayList<NeighborElement> neighbors = purgeDeletedNeighbors();

	if(neighbors == null)
	    return;

	for(NeighborElement neighborElement : neighbors)
	{
	    if(neighborElement == null)
		continue;
	    else
	    {
		try
		{
		    if(m_neighbors.get(neighborElement.m_oid) != null)
			continue;
		}
		catch(Exception exception)
		{
		}

		if(neighborElement.m_statusControl.
		   equalsIgnoreCase("delete") ||
		   neighborElement.m_statusControl.
		   equalsIgnoreCase("disconnect"))
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
	    {
		if(neighborElement.m_nonTls.equals("true"))
		    neighbor = new TcpNeighbor
			(neighborElement.m_passthrough,
			 neighborElement.m_proxyIpAddress,
			 neighborElement.m_proxyPort,
			 neighborElement.m_proxyType,
			 neighborElement.m_remoteIpAddress,
			 neighborElement.m_remotePort,
			 neighborElement.m_remoteScopeId,
			 neighborElement.m_ipVersion,
			 neighborElement.m_oid);
		else
		    neighbor = new TcpTlsNeighbor
			(neighborElement.m_passthrough,
			 neighborElement.m_proxyIpAddress,
			 neighborElement.m_proxyPort,
			 neighborElement.m_proxyType,
			 neighborElement.m_remoteIpAddress,
			 neighborElement.m_remotePort,
			 neighborElement.m_remoteScopeId,
			 neighborElement.m_ipVersion,
			 neighborElement.m_oid);
	    }
	    else if(neighborElement.m_transport.equals("UDP"))
	    {
		try
		{
		    InetAddress inetAddress = InetAddress.getByName
			(neighborElement.m_remoteIpAddress);

		    if(inetAddress.isMulticastAddress())
			neighbor = new UdpMulticastNeighbor
			    (neighborElement.m_passthrough,
			     neighborElement.m_remoteIpAddress,
			     neighborElement.m_remotePort,
			     neighborElement.m_remoteScopeId,
			     neighborElement.m_ipVersion,
			     neighborElement.m_oid);
		    else
			neighbor = new UdpNeighbor
			    (neighborElement.m_passthrough,
			     neighborElement.m_remoteIpAddress,
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

	    try
	    {
		m_neighbors.put(neighborElement.m_oid, neighbor);
	    }
	    catch(Exception exception)
	    {
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
			if(m_callQueue.isEmpty())
			    synchronized(m_callSchedulerMutex)
			    {
				try
				{
				    m_callSchedulerMutex.wait(WAIT_TIMEOUT);
				}
				catch(Exception exception)
				{
				}
			    }

			ParticipantCall participantCall = null;
			String sipHashId = "";

			/*
			** Allow the UI to respond to calling requests
			** while the kernel attempts to generate
			** ephemeral keys.
			*/

			try
			{
			    if(m_callQueue.isEmpty())
				return;

			    /*
			    ** Remove expired calls.
			    */

			    for(String key : m_callQueue.keySet())
			    {
				ParticipantCall value = m_callQueue.get(key);

				if(value == null ||
				   (System.nanoTime() -
				    value.m_startTime) / 1000000L >
				   CALL_LIFETIME)
				    m_callQueue.remove(key);
			    }

			    /*
			    ** Discover a pending call.
			    */

			    int participantOid = -1;

			    for(String key : m_callQueue.keySet())
			    {
				ParticipantCall value = m_callQueue.get(key);

				if(value == null)
				{
				    m_callQueue.remove(key);
				    continue;
				}
				else if(value.m_keyPair != null)
				    continue;

				participantOid = value.m_participantOid;
				sipHashId = key;
				break;
			    }

			    if(participantOid == -1)
				/*
				** A new call does not exist.
				*/

				return;

			    participantCall = m_callQueue.get(sipHashId);
			}
			catch(Exception exception)
			{
			}

			if(participantCall == null)
			    return;
			else
			    participantCall.preparePrivatePublicKey();

			try
			{
			    /*
			    ** The entry may have been removed.
			    */

			    if(m_callQueue.containsKey(sipHashId))
				m_callQueue.put(sipHashId, participantCall);
			}
			catch(Exception exception)
			{
			}

			if(isConnected())
			{
			    byte publicKeyType = Cryptography.
				MESSAGES_KEY_TYPES[0];

			    if(participantCall.m_algorithm ==
			       ParticipantCall.Algorithms.RSA)
				publicKeyType =
				    Cryptography.MESSAGES_KEY_TYPES[1];

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
	    }, 1500L, CALL_INTERVAL, TimeUnit.MILLISECONDS);
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
			MessageElement messageElement = null;
			boolean empty = false;

			m_messagesToSendMutex.writeLock().lock();

			try
			{
			    if(!m_messagesToSend.isEmpty())
			    {
				messageElement = m_messagesToSend.get
				    (m_messagesToSend.size() - 1);

				if(messageElement == null)
				{
				    m_messagesToSend.remove
					(m_messagesToSend.size() - 1);
				    return;
				}

				long delay = messageElement.m_delay;

				if(delay > 0)
				{
				    long delta = System.currentTimeMillis() -
					messageElement.m_timestamp;

				    if(delay > delta)
					return;
				    else
					m_messagesToSend.remove
					    (m_messagesToSend.size() - 1);
				}
				else
				    m_messagesToSend.remove
					(m_messagesToSend.size() - 1);
			    }
			    else
				empty = true;
			}
			catch(Exception exception)
			{
			}
			finally
			{
			    m_messagesToSendMutex.writeLock().unlock();
			}

			if(empty)
			    synchronized(m_messagesToSendSchedulerMutex)
			    {
				try
				{
				    m_messagesToSendSchedulerMutex.
					wait(WAIT_TIMEOUT);
				}
				catch(Exception exception)
				{
				}
			    }

			if(messageElement == null)
			    return;
			else
			    messageElement.m_timestamp =
				System.currentTimeMillis();

			byte bytes[] = null;

			try
			{
			    switch(messageElement.m_messageType)
			    {
			    case MessageElement.CHAT_MESSAGE_TYPE:
			    case MessageElement.RESEND_CHAT_MESSAGE_TYPE:
				if(messageElement.m_messageType ==
				   MessageElement.RESEND_CHAT_MESSAGE_TYPE)
				{
				    MemberChatElement memberChatElement =
					s_databaseHelper.readMemberChat
					(s_cryptography,
					 messageElement.m_id,
					 messageElement.m_position);

				    if(memberChatElement != null)
				    {
					messageElement.m_attachment =
					    memberChatElement.m_attachment;
					messageElement.m_keyStream =
					    s_databaseHelper.
					    participantKeyStream
					    (s_cryptography,
					     messageElement.m_id);
					messageElement.m_message =
					    memberChatElement.m_message;
				    }
				}

				bytes = Messages.chatMessage
				    (s_cryptography,
				     messageElement.m_message,
				     messageElement.m_id,
				     messageElement.m_attachment,
				     Cryptography.
				     sha512(messageElement.m_id.
					    getBytes(StandardCharsets.UTF_8)),
				     messageElement.m_keyStream,
				     messageElement.m_messageIdentity,
				     State.getInstance().
				     chatSequence(messageElement.m_id),
				     messageElement.m_timestamp);
				s_databaseHelper.writeParticipantMessage
				    (s_cryptography,
				     "local",
				     messageElement.m_message,
				     messageElement.m_id,
				     messageElement.m_attachment,
				     messageElement.m_messageIdentity,
				     messageElement.m_timestamp);

				Intent intent = new Intent
				    ("org.purple.smoke.chat_local_message");

				intent.putExtra
				    ("org.purple.smoke.message",
				     messageElement.m_message);
				intent.putExtra
				    ("org.purple.smoke.sipHashId",
				     messageElement.m_id);
				Miscellaneous.sendBroadcast(intent);
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
			    case MessageElement.JUGGERNAUT_MESSAGE_TYPE:
				try
				{
				    Juggernaut juggernaut = m_juggernauts.
					get(messageElement.m_id);

				    if(juggernaut != null &&
				       juggernaut.state() ==
				       JPAKEParticipant.STATE_INITIALIZED)
					bytes = juggernaut.next(null).
					    getBytes();
				    else
					bytes = null;
				}
				catch(Exception exception)
				{
				    bytes = null;
				}

				if(bytes != null)
				    bytes = Messages.juggernautMessage
					(s_cryptography,
					 messageElement.m_id,
					 bytes,
					 messageElement.m_keyStream);

				if(bytes != null)
				{
				    s_databaseHelper.writeParticipantMessage
					(s_cryptography,
					 "local-protocol",
					 "Juggernaut Protocol initiated.",
					 messageElement.m_id,
					 null,
					 null,
					 messageElement.m_timestamp);
				    Miscellaneous.sendBroadcast
					("org.purple.smoke." +
					 "notify_data_set_changed");
				}

				break;
			    case MessageElement.RETRIEVE_MESSAGES_MESSAGE_TYPE:
				bytes = Messages.chatMessageRetrieval
				    (s_cryptography);

				if(!messageElement.m_id.isEmpty())
				{
				    s_databaseHelper.writeParticipantMessage
					(s_cryptography,
					 "local-protocol",
					 "Requesting messages from " +
					 "SmokeStack(s).",
					 messageElement.m_id,
					 null,
					 null,
					 messageElement.m_timestamp);
				    Miscellaneous.sendBroadcast
					("org.purple.smoke." +
					 "notify_data_set_changed");
				}

				break;
			    case MessageElement.SHARE_SIPHASH_ID_MESSAGE_TYPE:
				m_shareSipHashIdIdentity.set
				    (Miscellaneous.
				     byteArrayToLong
				     (Cryptography.
				      randomBytes(Cryptography.IDENTITY_SIZE)));
				m_shareSipHashIdIdentityLastTick.set
				    (System.currentTimeMillis());

				if(messageElement.m_id.equals("-1"))
				    bytes = Messages.shareSipHashIdMessage
					(s_cryptography,
					 s_cryptography.sipHashId(),
					 m_shareSipHashIdIdentity.get());
				else
				{
				    String sipHashId = s_databaseHelper.
					readSipHashIdString
					(s_cryptography,
					 messageElement.m_id);

				    bytes = Messages.shareSipHashIdMessage
					(s_cryptography,
					 sipHashId,
					 m_shareSipHashIdIdentity.get());
				}

				break;
			    case MessageElement.
				 STEAM_KEY_EXCHANGE_MESSAGE_TYPE:
				bytes = new byte[1];
				break;
			    default:
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
				case MessageElement.RESEND_CHAT_MESSAGE_TYPE:
				    enqueueMessage
					(Messages.
					 bytesToMessageString(bytes),
					 messageElement.m_messageIdentity,
					 0);

				    if(messageElement.m_messageType !=
				       MessageElement.
				       RESEND_CHAT_MESSAGE_TYPE)
					State.getInstance().
					    incrementChatSequence
					    (messageElement.m_id);

				    break;
				case MessageElement.FIRE_MESSAGE_TYPE:
				    enqueueMessage
					(Messages.
					 bytesToMessageStringNonBase64(bytes),
					 null,
					 Database.
					 MESSAGE_DELIVERY_ATTEMPTS - 1);
				    break;
				case MessageElement.FIRE_STATUS_MESSAGE_TYPE:
				    scheduleSend
					(Messages.
					 bytesToMessageStringNonBase64
					 (bytes));
				    break;
				case MessageElement.JUGGERNAUT_MESSAGE_TYPE:
				    scheduleSend
					(Messages.
					 bytesToMessageString(bytes));
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
				case MessageElement.
				     SHARE_SIPHASH_ID_MESSAGE_TYPE:
				    enqueueMessage
					(Messages.
					 bytesToMessageString(bytes),
					 null,
					 Database.
					 MESSAGE_DELIVERY_ATTEMPTS - 1);
				    break;
				case MessageElement.
				     STEAM_KEY_EXCHANGE_MESSAGE_TYPE:
				    enqueueMessage
					(messageElement.m_message,
					 null,
					 Database.
					 MESSAGE_DELIVERY_ATTEMPTS - 1);
				    break;
				default:
				    break;
				}
			    }
			}
			catch(Exception exception)
			{
			}

			switch(messageElement.m_messageType)
			{
			case MessageElement.CHAT_MESSAGE_TYPE:
			case MessageElement.RESEND_CHAT_MESSAGE_TYPE:
			    if(s_cryptography.hasValidOzoneMacKey())
			    {
				bytes = Messages.chatMessage
				    (s_cryptography,
				     messageElement.m_message,
				     messageElement.m_id,
				     messageElement.m_attachment,
				     null,
				     messageElement.m_keyStream,
				     messageElement.m_messageIdentity,
				     State.getInstance().
				     chatSequence(messageElement.m_id),
				     messageElement.m_timestamp);

				if(bytes != null)
				    enqueueMessage
					("OZONE-" + Base64.
					 encodeToString(bytes,
							Base64.NO_WRAP),
					 null,
					 Database.
					 MESSAGE_DELIVERY_ATTEMPTS - 1);
			    }

			    break;
			default:
			    break;
			}
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, MESSAGES_TO_SEND_INTERVAL, TimeUnit.MILLISECONDS);
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
	    }, 1500L, NEIGHBORS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_networkStatusScheduler == null)
	{
	    m_networkStatusScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_networkStatusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		private final AtomicBoolean m_connected =
		    new AtomicBoolean(false);

		@Override
		public void run()
		{
		    try
		    {
			boolean isConnected = isConnected();

			if(isConnected != m_connected.get())
			{
			    if(isConnected)
				Miscellaneous.sendBroadcast
				    ("org.purple.smoke.network_connected");
			    else
				Miscellaneous.sendBroadcast
				    ("org.purple.smoke.network_disconnected");

			    m_connected.set(isConnected);
			}
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, NETWORK_STATUS_INTERVAL, TimeUnit.MILLISECONDS);
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

			    if(arrayList != null)
			    {
				for(SipHashIdElement sipHashIdElement :
					arrayList)
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
					    (Messages.
					     bytesToMessageString(bytes),
					     null,
					     Database.
					     MESSAGE_DELIVERY_ATTEMPTS - 1);
				}

				arrayList.clear();
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
			    {
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
					     bytesToMessageString(bytes),
					     null,
					     Database.
					     MESSAGE_DELIVERY_ATTEMPTS - 1);
				}

				arrayList.clear();
			    }
			}
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, PUBLISH_KEYS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_purgeScheduler == null)
	{
	    m_purgeScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_purgeScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			for(String key : m_juggernauts.keySet())
			{
			    Juggernaut value = m_juggernauts.get(key);

			    if(value == null ||
			       System.currentTimeMillis() -
			       value.lastEventTime() >
			       JUGGERNAUT_LIFETIME)
				m_juggernauts.remove(key);
			}
		    }
		    catch(Exception exception)
		    {
		    }

		    try
		    {
			s_databaseHelper.cleanDanglingOutboundQueued();
			s_databaseHelper.purgeCongestion(CONGESTION_LIFETIME);
			s_databaseHelper.purgeParticipantsKeyStreams
			    (PARTICIPANTS_KEYSTREAMS_LIFETIME);
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, PURGE_INTERVAL, TimeUnit.MILLISECONDS);
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
			retrieveChatMessages("");
		}
	    }, 10000L, REQUEST_MESSAGES_INTERVAL, TimeUnit.MILLISECONDS);
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
			if(State.getInstance().silent() || !isConnected())
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
	    }, 1500L, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_steamScheduler == null)
	{
	    m_steamScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_steamScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			prepareSteams();
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, STEAM_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_temporaryIdentityScheduler == null)
	{
	    m_temporaryIdentityScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_temporaryIdentityScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    m_chatMessageRetrievalIdentityMutex.writeLock().lock();

		    try
		    {
			if(System.currentTimeMillis() -
			   m_chatTemporaryIdentityLastTick.get() >
			   TEMPORARY_IDENTITY_LIFETIME)
			    m_chatMessageRetrievalIdentity = null;
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_chatMessageRetrievalIdentityMutex.
			    writeLock().unlock();
		    }

		    if(System.currentTimeMillis() -
		       m_shareSipHashIdIdentityLastTick.get() >
		       TEMPORARY_IDENTITY_LIFETIME)
			m_shareSipHashIdIdentity.set(0L);
		}
	    }, 1500L, TEMPORARY_IDENTITY_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void prepareSteams()
    {
	if(!State.getInstance().isAuthenticated())
	    return;

	ArrayList<SteamElement> steams = purgeDeletedSteams();

	if(steams == null)
	    return;

	for(SteamElement steamElement : steams)
	{
	    if(steamElement == null)
		continue;
	    else
	    {
		m_steamsMutex.readLock().lock();

		try
		{
		    if(m_steams.get(steamElement.m_oid) != null)
			continue;
		}
		catch(Exception exception)
		{
		}
		finally
		{
		    m_steamsMutex.readLock().unlock();
		}
	    }

	    if(steamElement.m_status.equals("completed") ||
	       steamElement.m_status.equals("deleted"))
		continue;

	    SteamReader steam = null;

	    if(steamElement.m_destination.equals(Steam.OTHER))
		steam = new SteamReaderSimple(steamElement.m_fileName,
					      steamElement.m_oid,
					      steamElement.m_readInterval,
					      steamElement.m_readOffset);
	    else
		steam = new SteamReaderFull(steamElement.m_destination,
					    steamElement.m_fileName,
					    steamElement.m_fileIdentity,
					    steamElement.m_oid,
					    steamElement.m_fileSize,
					    steamElement.m_readOffset);

	    m_steamsMutex.writeLock().lock();

	    try
	    {
		m_steams.append(steamElement.m_oid, steam);
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_steamsMutex.writeLock().unlock();
	    }
	}

	steams.clear();
    }

    private void purgeNeighbors()
    {
	/*
	** Disconnect all existing sockets.
	*/

	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null)
		    value.abort();
	    }

	    m_neighbors.clear();
	}
	catch(Exception exception)
	{
	}
    }

    private void purgeSteams()
    {
	m_steamsMutex.writeLock().lock();

	try
	{
	    int size = m_steams.size();

	    for(int i = 0; i < size; i++)
	    {
		int j = m_steams.keyAt(i);

		if(m_steams.get(j) != null)
		    m_steams.get(j).delete();
	    }

	    m_steams.clear();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_steamsMutex.writeLock().unlock();
	}
    }

    private void scheduleSend(String message)
    {
	if(message == null || message.trim().isEmpty())
	    return;

	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null && !value.passthrough())
		    value.scheduleSend(message);
	    }
	}
	catch(Exception exception)
	{
	}
    }

    private void wakeMessagesToSendScheduler()
    {
	synchronized(m_messagesToSendSchedulerMutex)
	{
	    m_messagesToSendSchedulerMutex.notify();
	}
    }

    public ArrayList<NeighborElement> purgeDeletedNeighbors()
    {
	ArrayList<NeighborElement> neighbors =
	    s_databaseHelper.readNeighbors(s_cryptography);

	if(neighbors == null || neighbors.isEmpty())
	{
	    purgeNeighbors();
	    return neighbors;
	}

	try
	{
	    /*
	    ** Remove neighbor objects which do not exist in the database.
	    ** Also removed will be neighbors having disconnected statuses.
	    */

	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);
		boolean found = false;

		for(NeighborElement neighbor : neighbors)
		    if(neighbor != null && key == neighbor.m_oid)
		    {
			if(!neighbor.m_statusControl.
			   equalsIgnoreCase("disconnect"))
			    found = true;

			break;
		    }

		if(!found)
		{
		    if(value != null)
			value.abort();

		    m_neighbors.remove(key);
		}
	    }
	}
	catch(Exception exception)
	{
	}

	return neighbors;
    }

    public ArrayList<SteamElement> purgeDeletedSteams()
    {
	ArrayList<SteamElement> steams = s_databaseHelper.readSteams
	    (s_cryptography, SteamElement.UPLOAD);

	if(steams == null || steams.isEmpty())
	{
	    purgeSteams();
	    return steams;
	}

	m_steamsMutex.writeLock().lock();

	try
	{
	    /*
	    ** Remove Steam objects which do not exist in the database.
	    ** Also removed will be Steams having completed or deleted
	    ** statuses.
	    */

	    for(int i = m_steams.size() - 1; i >= 0; i--)
	    {
		boolean found = false;
		int oid = m_steams.keyAt(i);

		for(SteamElement steamElement : steams)
		    if(steamElement != null && steamElement.m_oid == oid)
		    {
			String status = steamElement.m_status.toLowerCase();

			if(!status.equals("completed") &&
			   !status.equals("deleted"))
			    found = true;

			break;
		    }

		if(!found)
		{
		    if(m_steams.get(oid) != null)
			m_steams.get(oid).delete();

		    m_steams.remove(oid);
		}
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_steamsMutex.writeLock().unlock();
	}

	return steams;
    }

    public String connectedNeighborAddress()
    {
	/*
	** If a connected, non-passthrough neighbor is available, return its
	** address. Otherwise, return the address of a connected, passthrough
	** neighbor.
	*/

	ArrayList<IPAddressElement> addresses1 = new ArrayList<> ();
	ArrayList<IPAddressElement> addresses2 = new ArrayList<> ();

	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null)
		    if(value.connected())
		    {
			IPAddressElement ipAddressElement = new IPAddressElement
			    (value.remoteIpAddress(),
			     value.remotePort(),
			     value.remoteScopeId(),
			     value.transport());

			if(!value.passthrough())
			    addresses1.add(ipAddressElement);
			else
			    addresses2.add(ipAddressElement);
		    }
	    }
	}
	catch(Exception exception)
	{
	}

	Collections.sort(addresses1, Miscellaneous.s_ipAddressComparator);
	Collections.sort(addresses2, Miscellaneous.s_ipAddressComparator);

	if(addresses1.isEmpty() && addresses2.isEmpty())
	    return "";
	else if(addresses1.isEmpty())
	    return addresses2.get(0).address();
	else
	    return addresses1.get(0).address();
    }

    public String fireIdentities()
    {
	try
	{
	    if(!m_fireStreams.isEmpty())
	    {
		StringBuilder stringBuilder = new StringBuilder();

		for(String key : m_fireStreams.keySet())
		{
		    byte keys[] = m_fireStreams.get(key);

		    if(keys == null)
		    {
			m_fireStreams.remove(key);
			continue;
		    }

		    stringBuilder.append
			(Messages.identityMessage
			 (Cryptography.
			  sha512(Arrays.copyOfRange(keys,
						    Cryptography.
						    CIPHER_KEY_LENGTH +
						    Cryptography.
						    FIRE_HASH_KEY_LENGTH,
						    keys.length))));
		}

		return stringBuilder.toString();
	    }
	}
	catch(Exception exception)
	{
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

	try
	{
	    if(m_callQueue.containsKey(sipHashId))
		return false;

	    s_databaseHelper.writeParticipantMessage
		(s_cryptography,
		 "local-protocol",
		 "Preparing a call via " +
		 (algorithm == ParticipantCall.Algorithms.MCELIECE ?
		  "McEliece." : "RSA.") + " Please be patient.",
		 sipHashId,
		 null,
		 null,
		 System.currentTimeMillis());
	    m_callQueue.put
		(sipHashId,
		 new ParticipantCall(algorithm, sipHashId, participantOid));
	    Miscellaneous.sendBroadcast
		("org.purple.smoke.notify_data_set_changed");
	}
	catch(Exception exception)
	{
	}

	synchronized(m_callSchedulerMutex)
	{
	    m_callSchedulerMutex.notify();
	}

	return true;
    }

    public boolean enqueueMessage(String message,
				  byte messageIdentity[],
				  int attempts)
    {
	if(message == null || message.trim().isEmpty())
	    return false;

	ArrayList<NeighborElement> arrayList =
	    s_databaseHelper.readNeighborOids(s_cryptography);

	if(arrayList == null || arrayList.isEmpty())
	    return false;

	int size = arrayList.size();

	for(int i = 0; i < size; i++)
	    if(arrayList.get(i) != null &&
	       arrayList.get(i).m_passthrough.toLowerCase().equals("false") &&
	       arrayList.get(i).m_statusControl.toLowerCase().equals("connect"))
		s_databaseHelper.enqueueOutboundMessage
		    (s_cryptography,
		     message,
		     messageIdentity,
		     attempts,
		     arrayList.get(i).m_oid);

	arrayList.clear();
	return true;
    }

    public boolean igniteFire(String name)
    {
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
	catch(Exception exception)
	{
	}

	return false;
    }

    public boolean isConnected()
    {
	if(!isNetworkConnected())
	    return false;

	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null)
		    if(value.connected() && !value.passthrough())
			return true;
	    }
	}
	catch(Exception exception)
	{
	}

	return false;
    }

    public boolean isNetworkConnected()
    {
	try
	{
	    ConnectivityManager connectivityManager = (ConnectivityManager)
		Smoke.getApplication().getApplicationContext().
		getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = connectivityManager.
		getActiveNetworkInfo();

	    return networkInfo != null &&
		networkInfo.getType() == ConnectivityManager.TYPE_WIFI &&
		networkInfo.isConnected();
	}
	catch(Exception exception)
	{
	}

	return false;
    }

    public boolean wakeLocked()
    {
	if(m_wakeLock != null)
	    synchronized(m_wakeLock)
	    {
		return m_wakeLock.isHeld();
	    }

	return false;
    }

    public boolean wifiLocked()
    {
	if(m_wifiLock != null)
	    synchronized(m_wifiLock)
	    {
		return m_wifiLock.isHeld();
	    }

	return false;
    }

    public byte[] messageRetrievalIdentity()
    {
	m_chatMessageRetrievalIdentityMutex.writeLock().lock();

	try
	{
	    if(m_chatMessageRetrievalIdentity == null)
		m_chatMessageRetrievalIdentity =
		    Cryptography.randomBytes(Cryptography.HASH_KEY_LENGTH);

	    m_chatTemporaryIdentityLastTick.set(System.currentTimeMillis());
	    return m_chatMessageRetrievalIdentity;
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_chatMessageRetrievalIdentityMutex.writeLock().unlock();
	}

	return null;
    }

    public int availableNeighbors()
    {
	return m_neighbors.size();
    }

    public int availableSteamReaders()
    {
	m_steamsMutex.readLock().lock();

	try
	{
	    return m_steams.size();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_steamsMutex.readLock().unlock();
	}

	return 0;
    }

    public int availableSteamWriters()
    {
	return m_steamWriter.size();
    }

    public int nextSimpleSteamOid()
    {
	/*
	** Discover the oldest, incomplete Simple Steam.
	*/

	m_steamsMutex.readLock().lock();

	try
	{
	    int size = m_steams.size();

	    for(int i = 0; i < size; i++)
	    {
		int j = m_steams.keyAt(i);

		if(m_steams.get(j) instanceof SteamReaderSimple &&
		   m_steams.get(j).completed() == false)
		    return m_steams.get(j).getOid();
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_steamsMutex.readLock().unlock();
	}

	return -1;
    }

    public int ourMessage(String buffer)
    {
	/*
	** 0 - Echo
	** 1 - Fine (Do not Echo)
	** 2 - Force Echo
	*/

	final int ECHO = 0;
	final int FINE = 1;
	final int FORCE_ECHO = 2;

	if(buffer == null)
	    return FINE;

	try
	{
	    long value = s_congestionSipHash.hmac
		(buffer.getBytes(), Cryptography.SIPHASH_OUTPUT_LENGTH / 2)[0];

	    if(s_databaseHelper.containsCongestionDigest(value))
		return FINE;
	    else if(s_databaseHelper.writeCongestionDigest(value))
		return FINE;

	    /*
	    ** Fire!
	    */

	    try
	    {
		if(!m_fireStreams.isEmpty())
		{
		    String strings[] = Messages.stripMessage(buffer).
			split("\\n");

		    if(strings != null && strings.length >= 2)
		    {
			byte ciphertext[] = Base64.decode
			    (strings[0], Base64.NO_WRAP);
			byte hmac[] = Base64.decode
			    (strings[1], Base64.NO_WRAP);

			for(String key : m_fireStreams.keySet())
			{
			    byte keys[] = m_fireStreams.get(key);

			    if(keys == null)
			    {
				m_fireStreams.remove(key);
				continue;
			    }

			    if(Cryptography.
			       memcmp
			       (Cryptography.
				hmacFire(ciphertext,
					 Arrays.
					 copyOfRange(keys,
						     Cryptography.
						     CIPHER_KEY_LENGTH,
						     Cryptography.
						     CIPHER_KEY_LENGTH +
						     Cryptography.
						     FIRE_HASH_KEY_LENGTH)),
				hmac))
			    {
				ciphertext = Cryptography.decryptFire
				    (ciphertext,
				     Arrays.copyOfRange(keys,
							0,
							Cryptography.
							CIPHER_KEY_LENGTH));

				if(ciphertext == null)
				    return FINE;

				ciphertext = Arrays.copyOfRange

				    /*
				    ** Remove the size information of the
				    ** original data.
				    */

				    (ciphertext, 0, ciphertext.length - 4);
				strings = new String(ciphertext).split("\\n");

				if(!(strings.length == 4 ||
				     strings.length == 5))
				    return FINE;

				strings[strings.length - 1] = new
				    String(Base64.
					   decode(strings[strings.length - 1],
						  Base64.NO_WRAP));

				Date date = s_fireSimpleDateFormat.parse
				    (strings[strings.length - 1]);
				Timestamp timestamp = new Timestamp
				    (date.getTime());

				if(Math.abs(System.currentTimeMillis() -
					    timestamp.getTime()) >
				   FIRE_TIME_DELTA)
				    return FINE;

				int length = strings.length - 1;

				for(int i = 0; i < length; i++)
				    strings[i] = new String
					(Base64.decode(strings[i],
						       Base64.NO_WRAP),
					 StandardCharsets.UTF_8);

				value = s_congestionSipHash.hmac
				    (("fire" +
				      key +
				      strings[2] +
				      strings[3] +
				      timestamp).getBytes(),
				     Cryptography.SIPHASH_OUTPUT_LENGTH / 2)[0];

				if(s_databaseHelper.
				   writeCongestionDigest(value))
				    return FINE;

				final String channel = key;
				final String id = strings[2];
				final String message = strings[3];
				final String name = strings[1];
				final boolean isChatMessageType = strings[0].
				    equals(Messages.FIRE_CHAT_MESSAGE_TYPE);
				final boolean isStatusMessageType = strings[0].
				    equals(Messages.FIRE_STATUS_MESSAGE_TYPE);

				try
				{
				    new Handler(Looper.getMainLooper()).
					post(new Runnable()
					{
					    @Override
					    public void run()
					    {
						FireChannel fireChannel = State.
						    getInstance().fireChannel
						    (channel);

						if(fireChannel != null)
						{
						    if(isChatMessageType)
							fireChannel.append
							    (id, message, name);
						    else if(isStatusMessageType)
							fireChannel.status
							    (id, name);
						}
					    }
					});
				}
				catch(Exception exception)
				{
				}
				finally
				{
				}

				return FORCE_ECHO; // Echo Fire!
			    }
			}
		    }
		}
	    }
	    catch(Exception exception)
	    {
	    }

	    byte bytes[] =
		Base64.decode(Messages.stripMessage(buffer), Base64.DEFAULT);

	    if(bytes == null || bytes.length < 128)
		return ECHO;

	    /*
	    ** Ozone!
	    */

	    if(m_shareSipHashIdIdentity.get() != 0 &&
	       s_cryptography.ozoneEncryptionKey() != null &&
	       s_cryptography.ozoneMacKey() != null)
	    {
		byte data[] = Arrays.copyOfRange
		    (bytes,
		     0,
		     bytes.length - 2 * Cryptography.HASH_KEY_LENGTH);
		byte hmac[] = Arrays.copyOfRange
		    (bytes,
		     bytes.length - 2 * Cryptography.HASH_KEY_LENGTH,
		     bytes.length - Cryptography.HASH_KEY_LENGTH);

		if(Cryptography.
		   memcmp(hmac,
			  Cryptography.hmac(data,
					    s_cryptography.ozoneMacKey())))
		{
		    byte ciphertext[] = Cryptography.decrypt
			(data, s_cryptography.ozoneEncryptionKey());

		    if(ciphertext == null)
			return FINE;

		    long timestamp = Miscellaneous.byteArrayToLong
			(Arrays.copyOfRange(ciphertext, 1, 9));

		    if(Math.abs(System.currentTimeMillis() - timestamp) >
		       SHARE_SIPHASH_ID_CONFIRMATION_WINDOW)
			return FINE;

		    /*
		    ** Did we share something?
		    */

		    long identity = Miscellaneous.byteArrayToLong
			(Arrays.
			 copyOfRange(ciphertext,
				     9 + Cryptography.SIPHASH_IDENTITY_LENGTH,
				     9 +
				     Cryptography.IDENTITY_SIZE +
				     Cryptography.SIPHASH_IDENTITY_LENGTH));

		    if(identity != m_shareSipHashIdIdentity.get())
			return FINE;

		    m_shareSipHashIdIdentity.set(0L);

		    Intent intent = new Intent
			("org.purple.smoke.siphash_share_confirmation");
		    String sipHashId = new String
			(Arrays.
			 copyOfRange(ciphertext,
				     9,
				     9 + Cryptography.SIPHASH_IDENTITY_LENGTH),
			 StandardCharsets.UTF_8);

		    intent.putExtra("org.purple.smoke.sipHashId", sipHashId);
		    Miscellaneous.sendBroadcast(intent);
		    return FINE;
		}
	    }

	    boolean ourMessageViaChatTemporaryIdentity = false; /*
								** Did the
								** message
								** arrive from
								** SmokeStack?
								*/
	    byte data[] = Arrays.copyOfRange // Blocks #1, #2, etc.
		(bytes, 0, bytes.length - 2 * Cryptography.HASH_KEY_LENGTH);
	    byte destination[] = Arrays.copyOfRange
		(bytes,
		 bytes.length - Cryptography.HASH_KEY_LENGTH,
		 bytes.length);
	    byte hmac[] = Arrays.copyOfRange
		(bytes,
		 bytes.length - 2 * Cryptography.HASH_KEY_LENGTH,
		 bytes.length - Cryptography.HASH_KEY_LENGTH);
	    byte sha512OfMessage[] = Cryptography.sha512
		(Arrays.
		 copyOfRange(bytes,
			     0,
			     bytes.length - Cryptography.HASH_KEY_LENGTH));

	    m_chatMessageRetrievalIdentityMutex.readLock().lock();

	    try
	    {
		if(m_chatMessageRetrievalIdentity != null)
		    if(Cryptography.
		       memcmp(Cryptography.hmac(Arrays.
						copyOfRange(bytes,
							    0,
							    bytes.length -
							    Cryptography.
							    HASH_KEY_LENGTH),
						m_chatMessageRetrievalIdentity),
			      destination))
		    {
			m_chatTemporaryIdentityLastTick.set
			    (System.currentTimeMillis());
			ourMessageViaChatTemporaryIdentity = true;
		    }
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_chatMessageRetrievalIdentityMutex.readLock().unlock();
	    }

	    /*
	    ** What's the destination?
	    */

	    if(!ourMessageViaChatTemporaryIdentity)
		if(!s_cryptography.
		   iAmTheDestination(Arrays.copyOfRange(bytes,
							0,
							bytes.length -
							Cryptography.
							HASH_KEY_LENGTH),
				     destination))
		    return ECHO;

	    if(s_cryptography.isValidSipHashMac(data, hmac))
	    {
		/*
		** EPKS
		*/

		data = s_cryptography.decryptWithSipHashKey(data);

		String sipHashId = s_databaseHelper.writeParticipant
		    (s_cryptography, data);

		if(!sipHashId.isEmpty())
		{
		    /*
		    ** New participant.
		    */

		    Miscellaneous.sendBroadcast
			("org.purple.smoke.populate_participants");

		    /*
		    ** Response-share.
		    */

		    byte salt[] = Cryptography.sha512
			(sipHashId.trim().getBytes(StandardCharsets.UTF_8));
		    byte temporary[] = Cryptography.
			pbkdf2(salt,
			       sipHashId.toCharArray(),
			       Database.
			       SIPHASH_STREAM_CREATION_ITERATION_COUNT,
			       160); // SHA-1

		    if(temporary != null)
			bytes = Cryptography.
			    pbkdf2(salt,
				   Base64.encodeToString(temporary,
							 Base64.NO_WRAP).
				   toCharArray(),
				   1,
				   // Bits.
				   8 * (Cryptography.CIPHER_KEY_LENGTH +
					Cryptography.HASH_KEY_LENGTH));
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
			    (Messages.bytesToMessageString(bytes),
			     null,
			     Database.MESSAGE_DELIVERY_ATTEMPTS - 1);
		}

		return FINE;
	    }

	    byte pki[] = null;
	    int pki_output_size = 0;

	    if(s_cryptography.chatEncryptionPublicKeyAlgorithm().
	       startsWith("McEliece"))
	    {
		/*
		** Please see Cryptography.java.
		*/

		int e = 0;
		int s = 0;

		if(s_cryptography.chatEncryptionPublicKeyAlgorithm().
		   startsWith("McEliece-Fujisaki"))
		{
		    int t = s_cryptography.chatEncryptionPublicKeyT();

		    if(t == 50)
		    {
			e = 3;
			s = 0;
		    }
		    else if(t == 68)
		    {
			e = 9;
			s = 6;
		    }
		    else
		    {
			e = 12;
			s = 9;
		    }
		}
		else
		{
		    e = 6;
		    s = 3;
		}

		for(int i = s; i < e; i++)
		{
		    pki = s_cryptography.pkiDecrypt
			(Arrays.copyOfRange(bytes,
					    0,
					    MCELIECE_OUTPUT_SIZES[i]));

		    if(pki != null)
		    {
			pki_output_size = MCELIECE_OUTPUT_SIZES[i];
			break;
		    }
		}
	    }
	    else
	    {
		pki = s_cryptography.pkiDecrypt
		    (Arrays.
		     copyOfRange(bytes,
				 0,
				 Cryptography.PKI_ENCRYPTION_KEY_SIZES[0] / 8));
		pki_output_size = Cryptography.PKI_ENCRYPTION_KEY_SIZES[0] / 8;
	    }

	    if(pki == null)
		return FINE;

	    if(pki.length == Cryptography.HASH_KEY_LENGTH)
	    {
		/*
		** Chat
		** Chat Status
		** Juggernaut
		** Message-Read Proof
		*/

		byte keyStream[] = s_databaseHelper.participantKeyStream
		    (s_cryptography, pki);

		if(keyStream == null)
		    return FINE;

		byte hmacc[] = Cryptography.hmac
		    (Arrays.copyOfRange(bytes,
					0,
					bytes.length -
					2 * Cryptography.HASH_KEY_LENGTH),
		     Arrays.copyOfRange(keyStream,
					Cryptography.CIPHER_KEY_LENGTH,
					keyStream.length));

		if(!Cryptography.memcmp(hmac, hmacc))
		{
		    if(ourMessageViaChatTemporaryIdentity)
		    {
			keyStream = s_databaseHelper.participantKeyStream
			    (s_cryptography, pki, hmac, bytes);

			if(keyStream == null)
			    return FINE;
		    }
		    else
			return FINE;
		}

		byte ciphertext[] = Cryptography.decrypt
		    (Arrays.
		     copyOfRange(bytes,
				 pki_output_size,
				 bytes.length -
				 2 * Cryptography.HASH_KEY_LENGTH),
		     Arrays.copyOfRange(keyStream,
					0,
					Cryptography.CIPHER_KEY_LENGTH));

		if(ciphertext == null)
		    return FINE;

		byte abyte[] = new byte[] {ciphertext[0]};

		if(abyte[0] == Messages.CHAT_STATUS_MESSAGE_TYPE[0])
		{
		    String array[] = s_databaseHelper.nameSipHashIdFromDigest
			(s_cryptography, pki);

		    if(array == null || array.length != 2)
			return FINE;

		    String sipHashId = array[1];

		    if(s_databaseHelper.readParticipantOptions(s_cryptography,
							       sipHashId).
		       contains("optional_signatures = false"))
		    {
			long timestamp = Miscellaneous.byteArrayToLong
			    (Arrays.copyOfRange(ciphertext, 1, 9));

			if(Math.abs(System.currentTimeMillis() - timestamp) >
			   Chat.STATUS_WINDOW)
			    return FINE;

			PublicKey signatureKey = s_databaseHelper.
			    signatureKeyForDigest(s_cryptography, pki);

			if(signatureKey == null)
			    return FINE;

			if(!Cryptography.
			   verifySignature
			   (signatureKey,
			    Arrays.copyOfRange(ciphertext,
					       10,
					       ciphertext.length),
			    Miscellaneous.
			    joinByteArrays(pki,
					   Arrays.
					   copyOfRange(ciphertext,
						       0,
						       10),
					   s_cryptography.
					   chatEncryptionPublicKeyDigest())))
			    return FINE;
		    }

		    s_databaseHelper.updateParticipantLastTimestamp
			(s_cryptography, pki);
		    return FINE;
		}
		else if(abyte[0] == Messages.JUGGERNAUT_TYPE[0])
		{
		    ciphertext = Arrays.copyOfRange
			(ciphertext, 1, ciphertext.length);

		    String payload = "";
		    String strings[] = new String(ciphertext).split("\\n");
		    int ii = 0;

		    for(String string : strings)
			switch(ii)
			{
			case 0:
			    long timestamp = Miscellaneous.byteArrayToLong
				(Base64.
				 decode(string.getBytes(), Base64.NO_WRAP));

			    if(Math.abs(System.currentTimeMillis() -
					timestamp) > JUGGERNAUT_WINDOW)
				return FINE;

			    ii += 1;
			    break;
			case 1:
			    payload = new String
				(Base64.
				 decode(string.getBytes(), Base64.NO_WRAP));
			    ii += 1;
			    break;
			case 2:
			    PublicKey signatureKey = s_databaseHelper.
				signatureKeyForDigest(s_cryptography, pki);

			    if(signatureKey == null)
				return FINE;

			    byte publicKeySignature[] = Base64.decode
				(string.getBytes(), Base64.NO_WRAP);

			    if(!Cryptography.verifySignature
			       (signatureKey,
				publicKeySignature,
				Miscellaneous.
				joinByteArrays
				(pki,
				 abyte,
				 strings[0].getBytes(),
				 "\n".getBytes(),
				 strings[1].getBytes(),
				 "\n".getBytes(),
				 s_cryptography.
				 chatEncryptionPublicKeyDigest())))
				return FINE;

			    break;
			default:
			    break;
			}

		    String array[] = s_databaseHelper.
			nameSipHashIdFromDigest(s_cryptography, pki);

		    if(array == null || array.length != 2)
			return FINE;

		    byte sessionCredentials[] = null;
		    int state = -1;

		    try
		    {
			if(!m_juggernauts.containsKey(array[1]))
			    /*
			    ** Misplaced Juggernaut!
			    */

			    return FINE;

			Juggernaut juggernaut = m_juggernauts.get(array[1]);

			if(juggernaut == null)
			{
			    m_juggernauts.remove(array[1]);
			    return FINE;
			}

			if(juggernaut.state() ==
			   JPAKEParticipant.STATE_INITIALIZED)
			{
			    bytes = juggernaut.next(null).getBytes();
			    bytes = Messages.juggernautMessage
				(s_cryptography,
				 array[1],
				 bytes,
				 keyStream);
			    scheduleSend
				(Messages.bytesToMessageString(bytes));
			}

			bytes = juggernaut.next(payload).getBytes();

			if(bytes != null)
			{
			    bytes = Messages.juggernautMessage
				(s_cryptography,
				 array[1],
				 bytes,
				 keyStream);
			    scheduleSend
				(Messages.bytesToMessageString(bytes));
			}

			if((state = juggernaut.state()) ==
			   JPAKEParticipant.STATE_ROUND_3_VALIDATED)
			{
			    sessionCredentials = juggernaut.
				deriveSessionCredentials();
			    m_juggernauts.remove(array[1]);
			}
		    }
		    catch(Exception exception)
		    {
			bytes = null;
		    }

		    if(bytes != null)
		    {
			s_databaseHelper.writeParticipantMessage
			    (s_cryptography,
			     "local-protocol",
			     "Received a Juggernaut bundle. State of " +
			     state + " (" + Juggernaut.stateToText(state) +
			     "). Responded.",
			     array[1],
			     null,
			     null,
			     System.currentTimeMillis());
			Miscellaneous.sendBroadcast
			    ("org.purple.smoke.notify_data_set_changed");
		    }
		    else
		    {
			if(state == JPAKEParticipant.STATE_ROUND_3_VALIDATED)
			{
			    if(sessionCredentials != null)
			    {
				int oid = s_databaseHelper.
				    participantOidFromSipHash
				    (s_cryptography, array[1]);

				s_databaseHelper.setParticipantKeyStream
				    (s_cryptography, sessionCredentials, oid);
			    }

			    s_databaseHelper.writeParticipantMessage
				(s_cryptography,
				 "local-protocol",
				 "The Juggernaut Protocol has been verified!",
				 array[1],
				 null,
				 null,
				 System.currentTimeMillis());
			}
			else
			    s_databaseHelper.writeParticipantMessage
				(s_cryptography,
				 "local-protocol",
				 "Juggernaut Protocol failure (" +
				 Juggernaut.stateToText(state) + ")!",
				 array[1],
				 null,
				 null,
				 System.currentTimeMillis());

			Miscellaneous.sendBroadcast
			    ("org.purple.smoke.notify_data_set_changed");
		    }

		    return FINE;
		}
		else if(abyte[0] == Messages.MESSAGE_READ_TYPE[0])
		{
		    /*
		    ** We do not have a timestamp!
		    */

		    PublicKey signatureKey = s_databaseHelper.
			signatureKeyForDigest(s_cryptography, pki);

		    if(signatureKey == null)
			return FINE;

		    if(!Cryptography.
		       verifySignature
		       (signatureKey,
			Arrays.copyOfRange(ciphertext,
					   Cryptography.HASH_KEY_LENGTH + 1,
					   ciphertext.length),
			Miscellaneous.
			joinByteArrays(pki,
				       Arrays.
				       copyOfRange(ciphertext,
						   0,
						   Cryptography.
						   HASH_KEY_LENGTH + 1),
				       s_cryptography.
				       chatEncryptionPublicKeyDigest())))
			return FINE;

		    String array[] = s_databaseHelper.nameSipHashIdFromDigest
			(s_cryptography, pki);

		    if(array == null || array.length != 2)
			return FINE;

		    if(s_databaseHelper.
		       writeMessageStatus
		       (s_cryptography,
			array[1],
			Arrays.copyOfRange(ciphertext,
					   1,
					   Cryptography.
					   HASH_KEY_LENGTH + 1)))
			notifyOfDataSetChange("-1");

		    return FINE;
		}

		ciphertext = Arrays.copyOfRange
		    (ciphertext, 1, ciphertext.length);

		String strings[] = new String(ciphertext).split("\\n");

		if(strings.length != Messages.CHAT_GROUP_TWO_ELEMENT_COUNT)
		    return FINE;

		String message = null;
		boolean updateTimeStamp = true;
		byte attachment[] = null;
		byte messageIdentity[] = null;
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

			if(Math.abs(System.currentTimeMillis() - timestamp) >
			   Chat.CHAT_WINDOW)
			    updateTimeStamp = false;

			if(!updateTimeStamp)
			    /*
			    ** Ignore expired messages unless the messages
			    ** were discharged by SmokeStack per our
			    ** temporary identity.
			    */

			    if(!ourMessageViaChatTemporaryIdentity)
				return FINE;

			ii += 1;
			break;
		    case 1:
			message = new String
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP),
			     StandardCharsets.UTF_8).trim();
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
			messageIdentity = Base64.decode
			    (string.getBytes(), Base64.NO_WRAP);
			ii += 1;
			break;
		    case 5:
			String array[] = s_databaseHelper.
			    nameSipHashIdFromDigest(s_cryptography, pki);

			if(array == null || array.length != 2)
			    return FINE;

			String sipHashId = array[1];

			if(s_databaseHelper.
			   readParticipantOptions(s_cryptography,
						  sipHashId).
			   contains("optional_signatures = false"))
			{
			    PublicKey signatureKey = s_databaseHelper.
				signatureKeyForDigest(s_cryptography, pki);

			    if(signatureKey == null)
				return FINE;

			    publicKeySignature = Base64.decode
				(string.getBytes(), Base64.NO_WRAP);

			    if(!Cryptography.
			       verifySignature
			       (signatureKey,
				publicKeySignature,
				Miscellaneous.
				joinByteArrays
				(pki,
				 abyte,
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
				return FINE;
			}

			strings = array;
			break;
		    default:
			break;
		    }

		if(message == null)
		    return FINE;

		if(updateTimeStamp)
		    s_databaseHelper.updateParticipantLastTimestamp
			(s_cryptography, strings[1]);

		value = s_congestionSipHash.hmac
		    (("chat" + message + strings[1] + timestamp).getBytes(),
		     Cryptography.SIPHASH_OUTPUT_LENGTH)[0];

		if(s_databaseHelper.writeCongestionDigest(value))
		    return FINE;

		if(s_databaseHelper.
		   writeParticipantMessage(s_cryptography,
					   ourMessageViaChatTemporaryIdentity ?
					   "true" : "false",
					   message,
					   strings[1],
					   attachment,
					   messageIdentity,
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
		    Miscellaneous.sendBroadcast(intent);

		    /*
		    ** Prepare a read-proof message.
		    */

		    keyStream = s_databaseHelper.participantKeyStream
			(s_cryptography, pki); // Current key stream.
		    enqueueMessage
			(Messages.
			 bytesToMessageString(Messages.
					      messageRead(s_cryptography,
							  strings[1],
							  keyStream,
							  messageIdentity)),
			 null,
			 Database.MESSAGE_DELIVERY_ATTEMPTS - 1);

		    if(ourMessageViaChatTemporaryIdentity)
			enqueueMessage
			    (Messages.
			     bytesToMessageString(Messages.
						  messageRead(s_cryptography,
							      sha512OfMessage)),
			     null,
			     Database.MESSAGE_DELIVERY_ATTEMPTS - 1);
		}

		return FINE;
	    }
	    else if(pki.length == Cryptography.CIPHER_HASH_KEYS_LENGTH)
	    {
		/*
		** Organic Half-And-Half
		** Steam Key Exchange A
		** Steam Key Exchange B
		*/

		byte hmacc[] = Cryptography.hmac
		    (Arrays.copyOfRange(bytes,
					0,
					bytes.length -
					2 * Cryptography.HASH_KEY_LENGTH),
		     Arrays.copyOfRange(pki,
					Cryptography.CIPHER_KEY_LENGTH,
					pki.length));

		if(!Cryptography.memcmp(hmac, hmacc))
		    return FINE;

		byte ciphertext[] = Cryptography.decrypt
		    (Arrays.
		     copyOfRange(bytes,
				 pki_output_size,
				 bytes.length -
				 2 * Cryptography.HASH_KEY_LENGTH),
		     Arrays.copyOfRange(pki,
					0,
					Cryptography.CIPHER_KEY_LENGTH));

		if(ciphertext == null)
		    return FINE;

		byte tag = ciphertext[0];

		if(!(tag == Messages.CALL_HALF_AND_HALF_TAGS[0] ||
		     tag == Messages.CALL_HALF_AND_HALF_TAGS[1] ||
		     tag == Messages.STEAM_KEY_EXCHANGE[0] ||
		     tag == Messages.STEAM_KEY_EXCHANGE[1]))
		    return FINE;
		else if(tag == Messages.STEAM_KEY_EXCHANGE[0] ||
			tag == Messages.STEAM_KEY_EXCHANGE[1])
		{
		    m_steamKeyExchange.append(ciphertext, pki);
		    return FINE;
		}

		ciphertext = Arrays.copyOfRange
		    (ciphertext, 1, ciphertext.length);

		String strings[] = new String(ciphertext).split("\\n");

		if(strings.length != Messages.CALL_GROUP_TWO_ELEMENT_COUNT)
		    return FINE;

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
			timestamp = Miscellaneous.byteArrayToLong
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP));

			if(Math.abs(System.currentTimeMillis() - timestamp) >
			   CALL_LIFETIME)
			    return FINE;

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
			PublicKey signatureKey = s_databaseHelper.
			    signatureKeyForDigest
			    (s_cryptography, senderPublicEncryptionKeyDigest);

			if(signatureKey == null)
			    return FINE;

			publicKeySignature = Base64.decode
			    (string.getBytes(), Base64.NO_WRAP);

			if(!Cryptography.
			   verifySignature(signatureKey,
					   publicKeySignature,
					   Miscellaneous.
					   joinByteArrays
					   (pki,
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
			    return FINE;

			ii += 1;
			break;
		    default:
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

			try
			{
			    participantCall = m_callQueue.get(array[1]);
			}
			catch(Exception exception)
			{
			}

			if(participantCall == null)
			{
			    switch(ephemeralPublicKeyType[0])
			    {
			    case (byte) 'M':
				publicKey = Cryptography.
				    publicMcElieceKeyFromBytes
				    (ephemeralPublicKey);
				break;
			    case (byte) 'R':
				publicKey = Cryptography.publicRSAKeyFromBytes
				    (ephemeralPublicKey);
				break;
			    default:
				break;
			    }

			    if(publicKey == null)
				return FINE;

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

			    try
			    {
				m_callQueue.remove(array[1]);
			    }
			    catch(Exception exception)
			    {
			    }

			    Intent intent = new Intent
				("org.purple.smoke.busy_call");

			    intent.putExtra("org.purple.smoke.name", array[0]);
			    intent.putExtra
				("org.purple.smoke.sipHashId", array[1]);
			    Miscellaneous.sendBroadcast(intent);
			    return FINE;
			}
		    }
		    else if(tag == Messages.CALL_HALF_AND_HALF_TAGS[1])
		    {
			ParticipantCall participantCall = null;

			try
			{
			    participantCall = m_callQueue.get(array[1]);
			}
			catch(Exception exception)
			{
			}

			if(participantCall == null)
			    return FINE;

			try
			{
			    m_callQueue.remove(array[1]);
			}
			catch(Exception exception)
			{
			}

			keyStream = Cryptography.pkiDecrypt
			    (participantCall.m_keyPair.getPrivate(),
			     ephemeralPublicKey);

			if(keyStream == null)
			    return FINE;
		    }
		    else
			return FINE;

		    if(!s_databaseHelper.writeCallKeys(s_cryptography,
						       array[1],
						       keyStream))
			/*
			** Duplicate or error.
			*/

			return FINE;

		    Intent intent = new Intent
			("org.purple.smoke.half_and_half_call");

		    if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
		    {
			intent.putExtra("org.purple.smoke.initial", true);
			s_databaseHelper.writeParticipantMessage
			    (s_cryptography,
			     "local-protocol",
			     "Received a half-and-half call. " +
			     "Dispatching a response. Please be patient.",
			     array[1],
			     null,
			     null,
			     System.currentTimeMillis());
		    }
		    else
		    {
			intent.putExtra("org.purple.smoke.initial", false);
			s_databaseHelper.writeParticipantMessage
			    (s_cryptography,
			     "local-protocol",
			     "Received a half-and-half call-response.",
			     array[1],
			     null,
			     null,
			     System.currentTimeMillis());
		    }

		    intent.putExtra
			("org.purple.smoke.keyType",
			 ephemeralPublicKeyType[0] ==
			 Cryptography.MESSAGES_KEY_TYPES[0] ? 'M' : 'R');
		    intent.putExtra("org.purple.smoke.name", array[0]);
		    intent.putExtra("org.purple.smoke.refresh", true);
		    intent.putExtra("org.purple.smoke.sipHashId", array[1]);
		    Miscellaneous.sendBroadcast(intent);
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.notify_data_set_changed");

		    if(tag == Messages.CALL_HALF_AND_HALF_TAGS[0])
		    {
			/*
			** Respond via all neighbors.
			*/

			bytes = Messages.callMessage
			    (s_cryptography,
			     array[1],
			     Cryptography.
			     pkiEncrypt(publicKey,
					Cryptography.
					PARTICIPANT_CALL_MCELIECE_KEY_SIZE,
					keyStream),
			     ephemeralPublicKeyType[0],
			     Messages.CALL_HALF_AND_HALF_TAGS[1]);

			if(bytes != null)
			    scheduleSend(Messages.bytesToMessageString(bytes));
		    }

		    Miscellaneous.sendBroadcast
			("org.purple.smoke.populate_participants");
		    return FINE;
		}
	    }
	    else if(pki.length == Cryptography.STEAM_FILE_IDENTITY_LENGTH)
	    {
		/*
		** Steam A
		** Steam B
		*/

		/*
		** Discover the Steam having the presented identity.
		*/

		byte keyStream[] = s_databaseHelper.steamKeyStream
		    (s_cryptography, pki);

		if(keyStream == null)
		    return FINE;

		byte hmacc[] = Cryptography.hmac
		    (Arrays.copyOfRange(bytes,
					0,
					bytes.length -
					2 * Cryptography.HASH_KEY_LENGTH),
		     Arrays.copyOfRange(keyStream,
					Cryptography.CIPHER_KEY_LENGTH,
					keyStream.length));

		if(!Cryptography.memcmp(hmac, hmacc))
		    return FINE;

		byte ciphertext[] = Cryptography.decrypt
		    (Arrays.
		     copyOfRange(bytes,
				 pki_output_size,
				 bytes.length -
				 2 * Cryptography.HASH_KEY_LENGTH),
		     Arrays.copyOfRange(keyStream,
					0,
					Cryptography.CIPHER_KEY_LENGTH));

		if(ciphertext == null)
		    return FINE;

		long timestamp = Miscellaneous.byteArrayToLong
		    (Arrays.copyOfRange(ciphertext, 1, 9));

		if(Math.abs(System.currentTimeMillis() - timestamp) >
		   STEAM_SHARE_WINDOW)
		    return FINE;

		long offset = Miscellaneous.byteArrayToLong
		    (Arrays.copyOfRange(ciphertext, 9, 17));

		if(offset < 0)
		    return FINE;

		byte abyte[] = new byte[] {ciphertext[0]};

		if(abyte[0] == Messages.STEAM_SHARE[0])
		{
		    long rc = m_steamWriter.write
			(pki,
			 Arrays.copyOfRange(ciphertext,
					    17,
					    ciphertext.length),
			 offset);

		    if(rc >= 0L)
		    {
			String sipHashId = s_databaseHelper.steamSipHashId
			    (s_cryptography, pki);

			bytes = Messages.steamShare
			    (s_cryptography,
			     sipHashId,
			     pki,
			     keyStream,
			     null,
			     Messages.STEAM_SHARE[1],
			     rc);

			if(bytes != null)
			    sendSteam
				(false,
				 Messages.bytesToMessageString(bytes).
				 getBytes());
		    }
		}
		else if(abyte[0] == Messages.STEAM_SHARE[1])
		{
		    m_steamsMutex.readLock().lock();

		    try
		    {
			int size = m_steams.size();

			for(int i = 0; i < size; i++)
			{
			    int j = m_steams.keyAt(i);

			    if(m_steams.get(j) instanceof SteamReaderFull)
			    {
				SteamReaderFull steamReaderFull =
				    (SteamReaderFull) m_steams.get(j);

				if(Arrays.
				   equals(pki, steamReaderFull.fileIdentity()))
				{
				    steamReaderFull.setAcknowledgedOffset
					(offset);
				    break;
				}
			    }
			}
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_steamsMutex.readLock().unlock();
		    }
		}
	    }
	}
	catch(Exception exception)
	{
	    return ECHO;
	}

	return ECHO;
    }

    public long callTimeRemaining(String sipHashId)
    {
	try
	{
	    ParticipantCall participantCall = m_callQueue.get(sipHashId);

	    if(participantCall != null)
		return Math.abs
		    (CALL_LIFETIME / 1000L - (System.nanoTime() -
					      participantCall.
					      m_startTime) / 1000000000L);
	}
	catch(Exception exception)
	{
	}

	return 0L;
    }

    public static synchronized Kernel getInstance()
    {
	if(s_instance == null)
	    s_instance = new Kernel();

	return s_instance;
    }

    public static void writeCongestionDigest(String message)
    {
	if(message != null)
	    try
	    {
		s_databaseHelper.writeCongestionDigest
		    (s_congestionSipHash.
		     hmac(message.getBytes(),
			  Cryptography.SIPHASH_OUTPUT_LENGTH / 2)[0]);
	    }
	    catch(Exception exception)
	    {
	    }
    }

    public static void writeCongestionDigest(byte data[])
    {
	try
	{
	    s_databaseHelper.writeCongestionDigest
		(s_congestionSipHash.
		 hmac(data, Cryptography.SIPHASH_OUTPUT_LENGTH / 2)[0]);
	}
	catch(Exception exception)
	{
	}
    }

    public void clearMessagesToSend()
    {
	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.clear();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}
    }

    public void clearNeighborQueues()
    {
	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null)
		{
		    value.clearEchoQueue();
		    value.clearQueue();
		}
	    }
	}
	catch(Exception exception)
	{
	}
    }

    public void echo(String message, int oid)
    {
	if(!State.getInstance().neighborsEcho() ||
	   message == null ||
	   message.trim().isEmpty())
	    return;

	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null &&
		   value.getOid() != oid &&
		   !value.passthrough())
		    value.scheduleEchoSend(message);
	    }
	}
	catch(Exception exception)
	{
	}
    }

    public void echoForce(String message, int oid)
    {
	if(message == null || message.trim().isEmpty())
	    return;

	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null &&
		   value.getOid() != oid &&
		   !value.passthrough())
		    value.scheduleEchoSend(message);
	    }
	}
	catch(Exception exception)
	{
	}
    }

    public void enqueueChatMessage(String message,
				   String sipHashId,
				   byte imageBytes[],
				   byte keyStream[])
    {
	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_attachment = imageBytes;
	    messageElement.m_id = sipHashId;
	    messageElement.m_keyStream = keyStream;
	    messageElement.m_message = message;
	    messageElement.m_messageIdentity = Cryptography.randomBytes
		(Cryptography.HASH_KEY_LENGTH);
	    messageElement.m_messageType = MessageElement.CHAT_MESSAGE_TYPE;
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public void enqueueFireMessage(String message, String id, String name)
    {
	byte keyStream[] = null;

	try
	{
	    if(m_fireStreams.containsKey(name))
		keyStream = m_fireStreams.get(name);
	}
	catch(Exception exception)
	{
	}

	if(keyStream == null)
	{
	    m_fireStreams.remove(name);
	    return;
	}

	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_id = id;
	    messageElement.m_keyStream = keyStream;
	    messageElement.m_message = message;
	    messageElement.m_messageType = MessageElement.FIRE_MESSAGE_TYPE;
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public void enqueueFireStatus(String id, String name)
    {
	byte keyStream[] = null;

	try
	{
	    if(m_fireStreams.containsKey(name))
		keyStream = m_fireStreams.get(name);
	}
	catch(Exception exception)
	{
	}

	if(keyStream == null)
	{
	    m_fireStreams.remove(name);
	    return;
	}

	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_id = id;
	    messageElement.m_keyStream = keyStream;
	    messageElement.m_messageType = MessageElement.
		FIRE_STATUS_MESSAGE_TYPE;
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public void enqueueJuggernaut(String secret,
				  String sipHashId,
				  boolean isJuggerKnot,
				  byte keyStream[])
    {
	try
	{
	    m_juggernauts.remove(sipHashId);

	    Juggernaut juggernaut = new Juggernaut
		(sipHashId, secret, isJuggerKnot);

	    m_juggernauts.put(sipHashId, juggernaut);
	}
	catch(Exception exception)
	{
	}

	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_delay = JUGGERNAUT_DELAY;
	    messageElement.m_id = sipHashId;
	    messageElement.m_keyStream = keyStream;
	    messageElement.m_message = secret;
	    messageElement.m_messageType = MessageElement.
		JUGGERNAUT_MESSAGE_TYPE;
	    messageElement.m_timestamp = System.currentTimeMillis();
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public void enqueueShareSipHashIdMessage(int oid)
    {
	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_id = String.valueOf(oid);
	    messageElement.m_messageType = MessageElement.
		SHARE_SIPHASH_ID_MESSAGE_TYPE;
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public void enqueueSteamKeyExchange(String message, String sipHashId)
    {
	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_id = sipHashId;
	    messageElement.m_message = message;
	    messageElement.m_messageType = MessageElement.
		STEAM_KEY_EXCHANGE_MESSAGE_TYPE;
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public void extinguishFire(String name)
    {
	try
	{
	    m_fireStreams.remove(name);
	}
	catch(Exception exception)
	{
	}
    }

    public void notifyOfDataSetChange(String oid)
    {
	/*
	** The oid parameter represents the oid of the database entry.
	** The value of oid may be -1 or some other meaningless value.
	*/

	Miscellaneous.sendBroadcast
	    ("org.purple.smoke.notify_data_set_changed", oid);
    }

    public void resendMessage(String sipHashId, int position)
    {
	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_id = sipHashId;
	    messageElement.m_messageIdentity = Cryptography.randomBytes
		(Cryptography.HASH_KEY_LENGTH);
	    messageElement.m_messageType = MessageElement.
		RESEND_CHAT_MESSAGE_TYPE;
	    messageElement.m_position = position;
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public void retrieveChatMessages(String sipHashId)
    {
	MessageElement messageElement = null;

	try
	{
	    messageElement = new MessageElement();
	    messageElement.m_id = sipHashId;
	    messageElement.m_messageType = MessageElement.
		RETRIEVE_MESSAGES_MESSAGE_TYPE;
	}
	catch(Exception exception)
	{
	    return;
	}

	m_messagesToSendMutex.writeLock().lock();

	try
	{
	    m_messagesToSend.add(messageElement);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_messagesToSendMutex.writeLock().unlock();
	}

	wakeMessagesToSendScheduler();
    }

    public int sendSteam(boolean simple, byte bytes[])
    {
	int sent = 0;

	if(bytes == null || bytes.length == 0)
	    return sent;

	try
	{
	    for(Integer key : m_neighbors.keySet())
	    {
		Neighbor value = m_neighbors.get(key);

		if(value != null && value.connected())
		{
		    /*
		    ** Increase the offset by the minimum number of bytes.
		    */

		    if(simple)
		    {
			if(value.passthrough())
			{
			    int rc = value.send(bytes);

			    sent = Math.max(0, Math.min(Integer.MAX_VALUE, rc));
			}
		    }
		    else
		    {
			if(!value.passthrough())
			{
			    int rc = value.send(bytes);

			    sent = Math.max(0, Math.min(Integer.MAX_VALUE, rc));
			}
		    }
		}
	    }
	}
	catch(Exception exception)
	{
	}

	return sent;
    }

    public void setWakeLock(boolean state)
    {
	if(m_wakeLock != null)
	    synchronized(m_wakeLock)
	    {
		try
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
		catch(Exception exception)
		{
		}
	    }
    }
}
