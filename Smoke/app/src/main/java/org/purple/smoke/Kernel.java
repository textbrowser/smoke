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

import android.content.Intent;
import android.util.Base64;
import android.util.SparseArray;
import android.util.SparseIntArray;
import java.net.InetAddress;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Kernel
{
    private class ParticipantCall
    {
	public String m_sipHashId = "";
	public byte m_keyStream[] = null;
	public int m_participantOid = -1;
	public long m_startTime = -1; // Calls expire.

	public ParticipantCall(String sipHashId, int participantOid)
	{
	    m_participantOid = participantOid;
	    m_sipHashId = sipHashId;
	    m_startTime = System.nanoTime();
	}

	public void prepareHalfKeys()
	{
	    m_keyStream = Miscellaneous.joinByteArrays
		(Cryptography.aes128KeyBytes(),
		 Cryptography.sha256KeyBytes());
	}
    }

    private Hashtable<String, ParticipantCall> m_callQueue = null;
    private ScheduledExecutorService m_callScheduler = null;
    private ScheduledExecutorService m_congestionScheduler = null;
    private ScheduledExecutorService m_neighborsScheduler = null;
    private final Object m_callQueueMutex = new Object();
    private final SparseArray<Neighbor> m_neighbors = new SparseArray<> ();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static SipHash s_congestionSipHash = new SipHash
	(Cryptography.randomBytes(SipHash.KEY_LENGTH));
    private final static int CALL_INTERVAL = 250; // 0.250 Seconds
    private final static int CALL_LIFETIME = 15000; // 15 Seconds
    private final static int CONGESTION_INTERVAL = 15000; // 15 Seconds
    private final static int CONGESTION_LIFETIME = 30;
    private final static int NEIGHBORS_INTERVAL = 5000; // 5 Seconds
    private static Kernel s_instance = null;

    private Kernel()
    {
	m_callQueue = new Hashtable<> ();
	prepareSchedulers();
    }

    private void prepareNeighbors()
    {
	ArrayList<NeighborElement> neighbors =
	    s_databaseHelper.readNeighbors(s_cryptography);

	if(neighbors == null || neighbors.size() == 0)
	{
	    purge();
	    return;
	}

	synchronized(m_neighbors)
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

	for(NeighborElement neighborElement : neighbors)
	{
	    if(neighborElement == null)
		continue;
	    else
	    {
		synchronized(m_neighbors)
		{
		    if(m_neighbors.get(neighborElement.m_oid) != null)
			continue;
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

	    synchronized(m_neighbors)
	    {
		m_neighbors.append(neighborElement.m_oid, neighbor);
	    }
	}

	Runtime.getRuntime().runFinalization();
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

			synchronized(m_callQueueMutex)
			{
			    if(m_callQueue.isEmpty())
				return;

			    /*
			    ** Remove expired calls.
			    */

			    Iterator<Hashtable.Entry<String, ParticipantCall> >
				it = m_callQueue.entrySet().iterator();
			    boolean notify = false;

			    while(it.hasNext())
			    {
				Hashtable.Entry<String, ParticipantCall> entry =
				    it.next();

				if(entry.getValue() == null)
				    it.remove();

				if((System.nanoTime() - entry.getValue().
				    m_startTime) / 1000000 > CALL_LIFETIME)
				{
				    it.remove();
				    notify = true;
				}
			    }

			    /*
			    ** Discover a pending call.
			    */

			    String sipHashId = "";
			    int participantOid = -1;

			    for(String string : m_callQueue.keySet())
			    {
				if(m_callQueue.get(string).m_keyStream != null)
				    continue;

				participantOid = m_callQueue.get(string).
				    m_participantOid;
				sipHashId = string;
				break;
			    }

			    if(notify)
			    {
				/*
				** Expired call(s). Notify some activity.
				*/

				Intent intent = new Intent
				    ("org.purple.smoke.populate_participants");

				Smoke.getApplication().sendBroadcast(intent);
			    }

			    if(participantOid == -1)
				/*
				** A new call does not exist.
				*/

				return;

			    participantCall = m_callQueue.get(sipHashId);
			    participantCall.prepareHalfKeys();
			    m_callQueue.put(sipHashId, participantCall);
			}

			/*
			** Notify some activity to refresh itself.
			*/

			Intent intent = new Intent
			    ("org.purple.smoke.populate_participants");

			Smoke.getApplication().sendBroadcast(intent);

			/*
			** Place a call request to all neighbors.
			*/

			byte bytes[] = Messages.callMessage
			    (s_cryptography,
			     participantCall.m_sipHashId,
			     participantCall.m_keyStream,
			     Messages.CALL_HALF_AND_HALF_TAGS[0]);

			if(bytes != null)
			    echo(Messages.bytesToMessageString(bytes), -1);
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
    }

    private void purge()
    {
	/*
	** Disconnect all existing sockets.
	*/

	synchronized(m_neighbors)
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null)
		    m_neighbors.get(j).abort();
	    }

	    m_neighbors.clear();
	}

	Runtime.getRuntime().runFinalization();
    }

    public boolean ourMessage(String buffer)
    {
	if(s_databaseHelper.containsCongestionDigest(s_congestionSipHash.
						     hmac(buffer.getBytes())))
	    return true;

	s_databaseHelper.writeCongestionDigest
	    (s_congestionSipHash.hmac(buffer.getBytes()));

	try
	{
	    byte bytes[] =
		Base64.decode(Messages.stripMessage(buffer), Base64.DEFAULT);

	    if(bytes == null || bytes.length < 128)
		return false;

	    byte array1[] = Arrays.copyOfRange
		(bytes, 0, bytes.length - 128);
	    byte array2[] = Arrays.copyOfRange
		(bytes, bytes.length - 128, bytes.length - 64);
	    byte array3[] = Arrays.copyOfRange // The destination.
		(bytes, bytes.length - 64, bytes.length);

	    if(!s_cryptography.iAmTheDestination(Miscellaneous.
						 joinByteArrays(array1, array2),
						 array3))
		return false;

	    if(s_cryptography.isValidSipHashMac(array1, array2))
	    {
		/*
		** EPKS
		*/

		array1 = s_cryptography.decryptWithSipHashKey(array1);

		if(s_databaseHelper.writeParticipant(s_cryptography, array1))
		{
		    Intent intent = new Intent
			("org.purple.smoke.populate_participants");

		    Smoke.getApplication().sendBroadcast(intent);
		}
	    }
	    else if((array1 = s_cryptography.pkiDecrypt(array1)) != null &&
		    array1.length > 128)
	    {
		/*
		** Organic Half-And-Half
		*/

		PublicKey signatureKey = s_databaseHelper.signatureKeyForDigest
		    (s_cryptography,
		     Arrays.
		     copyOfRange(array1,
				 Messages.CALL_HALF_AND_HALF_OFFSETS[5],
				 Messages.CALL_HALF_AND_HALF_OFFSETS[5] + 64));

		if(signatureKey != null &&
		   Cryptography.
		   verifySignature(signatureKey,
				   Arrays.
				   copyOfRange(array1,
					       Messages.
					       CALL_HALF_AND_HALF_OFFSETS[6],
					       array1.length),
				   Arrays.copyOfRange(array1,
						      0,
						      129)))
		{
		    long current = System.currentTimeMillis();
		    long timestamp = Miscellaneous.byteArrayToLong
			(Arrays.
			 copyOfRange(array1,
				     Messages.CALL_HALF_AND_HALF_OFFSETS[1],
				     Messages.
				     CALL_HALF_AND_HALF_OFFSETS[1] + 8));

		    if(current - timestamp < 0)
		    {
			if(timestamp - current > CALL_LIFETIME)
			    return false;
		    }
		    else if(current - timestamp > CALL_LIFETIME)
			return false;

		    String array[] = s_databaseHelper.nameSipHashIdFromDigest
			(s_cryptography,
			 Arrays.
			 copyOfRange(array1,
				     Messages.CALL_HALF_AND_HALF_OFFSETS[5],
				     Messages.CALL_HALF_AND_HALF_OFFSETS[5] +
				     64));

		    if(array != null && array.length == 2)
		    {
			byte keyStream[] = null;

			if(array1[0] == Messages.CALL_HALF_AND_HALF_TAGS[0])
			    keyStream = Miscellaneous.joinByteArrays
				(Arrays.copyOfRange(array1, 9, 25),
				 Cryptography.aes128KeyBytes(),
				 Arrays.copyOfRange(array1, 25, 57),
				 Cryptography.sha256KeyBytes());
			else if(array1[0] ==
				Messages.CALL_HALF_AND_HALF_TAGS[1])
			{
			    ParticipantCall participantCall = null;

			    synchronized(m_callQueueMutex)
			    {
				participantCall = m_callQueue.get(array[1]);
			    }

			    if(participantCall == null)
				return false;

			    keyStream = Miscellaneous.joinByteArrays
				(Arrays.copyOfRange(participantCall.m_keyStream,
						    0,
						    16),
				 Arrays.copyOfRange(array1, 9, 25),
				 Arrays.copyOfRange(participantCall.m_keyStream,
						    16,
						    48),
				 Arrays.copyOfRange(array1, 25, 57));
			}
			else
			    return false;

			s_databaseHelper.writeCallKeys
			    (s_cryptography, array[1], keyStream);

			Intent intent = new Intent
			    ("org.purple.smoke.half_and_half_call");

			if(array1[0] == Messages.CALL_HALF_AND_HALF_TAGS[0])
			    intent.putExtra("org.purple.smoke.initial", true);
			else
			    intent.putExtra("org.purple.smoke.initial", false);

			intent.putExtra("org.purple.smoke.name", array[0]);
			intent.putExtra("org.purple.smoke.refresh", true);
			intent.putExtra("org.purple.smoke.sipHashId", array[1]);
			Smoke.getApplication().sendBroadcast(intent);

			if(array1[0] == Messages.CALL_HALF_AND_HALF_TAGS[0])
			{
			    /*
			    ** Respond via all neighbors.
			    */

			    bytes = Messages.callMessage
				(s_cryptography,
				 array[1],
				 Miscellaneous.
				 joinByteArrays(Arrays.copyOfRange(keyStream,
								   16,
								   32),
						Arrays.copyOfRange(keyStream,
								   64,
								   keyStream.
								   length)),
				 Messages.CALL_HALF_AND_HALF_TAGS[1]);

			    if(bytes != null)
				echo(Messages.bytesToMessageString(bytes), -1);
			}
			else
			{
			    synchronized(m_callQueueMutex)
			    {
				m_callQueue.remove(array[1]);
			    }
			}
		    }
		}
	    }
	    else
	    {
		byte pk[] = s_cryptography.pkiDecrypt
		    (Arrays.
		     copyOfRange(bytes,
				 0,
				 Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8));

		if(pk == null)
		    return false;

		byte keyStream[] = s_databaseHelper.participantKeyStream
		    (s_cryptography, pk);

		if(keyStream == null)
		    return false;

		byte hmac[] = Cryptography.hmac
		    (Arrays.copyOfRange(bytes, 0, bytes.length - 128),
		     Arrays.copyOfRange(keyStream, 32, keyStream.length));

		if(!Cryptography.memcmp(array2, hmac))
		    return false;

		bytes = Cryptography.decrypt
		    (Arrays.
		     copyOfRange(bytes,
				 Settings.PKI_ENCRYPTION_KEY_SIZES[0] / 8,
				 bytes.length - 128),
		     Arrays.copyOfRange(keyStream, 0, 32));

		if(bytes == null)
		    return false;

		String strings[] = new String(bytes).split("\\n");

		if(strings == null ||
		   strings.length != Messages.CHAT_GROUP_TWO_ELEMENT_COUNT)
		    return false;

		String message = null;
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
			ii += 1;
			break;
		    case 1:
			message = new String
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP),
			     "UTF-8");
			ii += 1;
			break;
		    case 2:
			sequence = Miscellaneous.byteArrayToLong
			    (Base64.decode(string.getBytes(), Base64.NO_WRAP));
			ii += 1;
			break;
		    case 3:
			publicKeySignature = Base64.decode
			    (string.getBytes(), Base64.NO_WRAP);

			PublicKey signatureKey = s_databaseHelper.
			    signatureKeyForDigest(s_cryptography, pk);

			if(signatureKey == null)
			    return false;

			if(!Cryptography.
			   verifySignature(signatureKey,
					   publicKeySignature,
					   Miscellaneous.
					   joinByteArrays(pk,
							  strings[0].getBytes(),
							  "\n".getBytes(),
							  strings[1].getBytes(),
							  "\n".getBytes(),
							  strings[2].getBytes(),
							  "\n".getBytes())))
			    return false;

			break;
		    }

		if(message == null)
		    return false;

		strings = s_databaseHelper.nameSipHashIdFromDigest
		    (s_cryptography, pk);

		if(strings == null || strings.length != 2)
		    return false;

		Intent intent = new Intent
		    ("org.purple.smoke.chat_message");

		intent.putExtra("org.purple.smoke.message", message);
		intent.putExtra("org.purple.smoke.name", strings[0]);
		intent.putExtra("org.purple.smoke.sequence", sequence);
		intent.putExtra("org.purple.smoke.sipHashId", strings[1]);
		intent.putExtra("org.purple.smoke.timestamp", timestamp);
		Smoke.getApplication().sendBroadcast(intent);
	    }
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public int callingStreamLength(String sipHashId)
    {
	synchronized(m_callQueueMutex)
	{
	    if(m_callQueue.containsKey(sipHashId) &&
	       m_callQueue.get(sipHashId) != null)
		return m_callQueue.get(sipHashId).m_keyStream == null ? -1 :
		    m_callQueue.get(sipHashId).m_keyStream.length;
	}

	return -1;
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
	    (s_congestionSipHash.hmac(data)); /*
					      ** Zero on hmac() failure.
					      ** Acceptable.
					      */
    }

    public void call(int participantOid, String sipHashId)
    {
	/*
	** Calling messages are not placed in the outbound_queue
	** as they are considered temporary.
	*/

	synchronized(m_callQueueMutex)
	{
	    if(m_callQueue.containsKey(sipHashId))
		m_callQueue.remove(sipHashId);

	    m_callQueue.put
		(sipHashId, new ParticipantCall(sipHashId, participantOid));
	}
    }

    public void clearNeighborQueues()
    {
	synchronized(m_neighbors)
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null)
		    m_neighbors.get(j).clearQueue();
	    }
	}
    }

    public void echo(String message, int oid)
    {
	if((!State.getInstance().neighborsEcho() && oid != -1) ||
	   message.trim().isEmpty())
	    return;

	if(s_databaseHelper.
	   containsCongestionDigest(s_congestionSipHash.hmac(message.
							     getBytes())))
	    return;

	synchronized(m_neighbors)
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null &&
		   m_neighbors.get(j).getOid() != oid)
		    m_neighbors.get(j).scheduleSend(message);
	    }
	}
    }

    public void enqueueMessage(String message)
    {
	if(message.trim().isEmpty())
	    return;

	SparseIntArray neighbors = s_databaseHelper.readNeighborOids();

	if(neighbors != null)
	    for(int i = 0; i < neighbors.size(); i++)
		s_databaseHelper.enqueueOutboundMessage
		    (message, neighbors.get(i));
    }
}
