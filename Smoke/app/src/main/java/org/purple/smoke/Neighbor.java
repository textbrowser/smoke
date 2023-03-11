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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Neighbor
{
    private ArrayList<String> m_echoQueue = null;
    private ArrayList<String> m_queue = null;
    private AtomicBoolean m_capabilitiesSent = null;
    private UUID m_uuid = null;
    private final Object m_echoQueueMutex = new Object();
    private final Object m_queueMutex = new Object();
    private final ScheduledExecutorService m_parsingScheduler =
	Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService m_scheduler =
	Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService m_sendOutboundScheduler =
	Executors.newSingleThreadScheduledExecutor();
    private final static int LANE_WIDTH = 8 * 1024 * 1024; // 8 MiB.
    private final static long DATA_LIFETIME = 15000L; // 15 seconds.
    private final static long PARSING_INTERVAL = 100L; // 100 milliseconds.
    private final static long SEND_OUTBOUND_TIMER_INTERVAL =
	200L; // 200 milliseconds.
    private final static long SILENCE = 90000L; // 90 seconds.
    private final static long TIMER_INTERVAL = 3500L; // 3.5 seconds.
    protected AtomicBoolean m_disconnected = null;
    protected AtomicBoolean m_passthrough = null;
    protected AtomicInteger m_ipPort = null;
    protected AtomicInteger m_oid = null;
    protected AtomicLong m_bytesRead = null;
    protected AtomicLong m_bytesWritten = null;
    protected AtomicLong m_lastParsed = null;
    protected AtomicLong m_lastTimeRead = null;
    protected AtomicLong m_startTime = null;
    protected Cryptography m_cryptography = null;
    protected Database m_database = null;
    protected String m_ipAddress = "";
    protected String m_version = "";
    protected final Object m_errorMutex = new Object();
    protected final Object m_mutex = new Object();
    protected final Object m_parsingSchedulerMutex = new Object();
    protected final ScheduledExecutorService m_readSocketScheduler =
	Executors.newSingleThreadScheduledExecutor();
    protected final StringBuffer m_stringBuffer = new StringBuffer();
    protected final StringBuilder m_error = new StringBuilder();
    protected final static int BYTES_PER_READ = 1024 * 1024; // 1 MiB.
    protected final static int MAXIMUM_BYTES = LANE_WIDTH;
    protected final static int SO_RCVBUF = 65536;
    protected final static int SO_SNDBUF = 65536;
    protected final static int SO_TIMEOUT = 0; // 0 seconds, block.
    protected final static long AWAIT_TERMINATION = 5L; // 5 seconds.
    protected final static long READ_SOCKET_INTERVAL =
	100L; // 100 milliseconds.
    protected final static long WAIT_TIMEOUT = 10000L; // 10 seconds.
    public final static int MAXIMUM_QUEUED_ECHO_PACKETS = 256;

    private void saveStatistics()
    {
	String echoQueueSize = "";
	String error = "";
	String localIp = getLocalIp();
	String localPort = String.valueOf(getLocalPort());
	String sessionCiper = getSessionCipher();
	String status = "connecting";
	boolean connected = connected();
	long uptime = System.nanoTime() - m_startTime.get();

	synchronized(m_echoQueueMutex)
	{
	    echoQueueSize = String.valueOf(m_echoQueue.size());
	}

	synchronized(m_errorMutex)
	{
	    error = m_error.toString();
	}

	if(connected)
	    status = "connected";
	else
	    status = "disconnected";

	try
	{
	    m_database.saveNeighborInformation
		(m_cryptography,
		 String.valueOf(m_bytesRead.get()),
		 String.valueOf(m_bytesWritten.get()),
		 echoQueueSize,
		 error,
		 localIp,
		 localPort,
		 sessionCiper,
		 status,
		 String.valueOf(uptime),
		 String.valueOf(m_oid.get()));
	}
	catch(Exception exception)
	{
	}
    }

    private void terminateOnSilence()
    {
	if(m_passthrough.get())
	    return;

	if((System.nanoTime() - m_lastTimeRead.get()) / 1000000L > SILENCE)
	    disconnect();
    }

    protected Neighbor(String passthrough,
		       String ipAddress,
		       String ipPort,
		       String scopeId,
		       String transport,
		       String version,
		       int oid)
    {
	m_bytesRead = new AtomicLong(0L);
	m_bytesWritten = new AtomicLong(0L);
	m_capabilitiesSent = new AtomicBoolean(false);
	m_cryptography = Cryptography.getInstance();
	m_database = Database.getInstance();
	m_disconnected = new AtomicBoolean(false);
	m_echoQueue = new ArrayList<> ();
	m_ipAddress = ipAddress;
	m_ipPort = new AtomicInteger(Integer.parseInt(ipPort));
	m_lastParsed = new AtomicLong(System.currentTimeMillis());
	m_lastTimeRead = new AtomicLong(System.nanoTime());
	m_oid = new AtomicInteger(oid);
	m_passthrough = new AtomicBoolean(passthrough.equals("true"));
	m_queue = new ArrayList<> ();
	m_startTime = new AtomicLong(System.nanoTime());
	m_uuid = UUID.randomUUID();
	m_version = version;

	/*
	** Start the schedules.
	*/

	m_parsingScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    if(!connected() && !m_disconnected.get())
			synchronized(m_mutex)
			{
			    try
			    {
				m_mutex.wait(WAIT_TIMEOUT);
			    }
			    catch(Exception exception)
			    {
			    }
			}

		    if(!connected() || m_disconnected.get())
			return;

		    /*
		    ** Await new data.
		    */

		    synchronized(m_parsingSchedulerMutex)
		    {
			try
			{
			    m_parsingSchedulerMutex.wait(WAIT_TIMEOUT);
			}
			catch(Exception exception)
			{
			}
		    }

		    if(m_passthrough.get())
		    {
			echo(m_stringBuffer.toString());
			m_lastParsed.set(System.currentTimeMillis());
			m_stringBuffer.delete(0, m_stringBuffer.length());
		    }
		    else
		    {
			/*
			** Detect our end-of-message delimiter.
			*/

			int indexOf = -1;

			while((indexOf = m_stringBuffer.
			                 indexOf(Messages.EOM)) >= 0)
			{
			    if(m_disconnected.get())
				break;

			    m_lastParsed.set(System.currentTimeMillis());

			    String buffer = m_stringBuffer.
				substring(0, indexOf + Messages.EOM.length());

			    m_stringBuffer.delete(0, buffer.length());

			    if(buffer.contains("type=0097a&content="))
			    {
				scheduleSend
				    (Messages.
				     authenticateMessage(m_cryptography,
							 Messages.
							 stripMessage(buffer)));
				continue;
			    }

			    switch(Kernel.getInstance().ourMessage(buffer))
			    {
			    case 0:
				echo(buffer);
				break;
			    case 2:
				echoForce(buffer);
				break;
			    default:
				break;
			    }
			}
		    }

		    if(System.currentTimeMillis() - m_lastParsed.get() >
		       DATA_LIFETIME ||
		       m_stringBuffer.length() > MAXIMUM_BYTES)
			m_stringBuffer.delete(0, m_stringBuffer.length());

		    m_stringBuffer.trimToSize();
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0L, PARSING_INTERVAL, TimeUnit.MILLISECONDS);
	m_scheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    String statusControl = m_database.
			readNeighborStatusControl(m_cryptography, m_oid.get());

		    switch(statusControl)
		    {
		    case "connect":
			connect();
			break;
		    case "disconnect":
			disconnect();
			setError("");
			break;
		    default:
			/*
			** Abort!
			*/

			disconnect();
			return;
		    }

		    saveStatistics();
		    terminateOnSilence();
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0L, TIMER_INTERVAL, TimeUnit.MILLISECONDS);
	m_sendOutboundScheduler.scheduleAtFixedRate(new Runnable()
	{
	    private int m_lastMessageOid = -1;
	    private long m_accumulatedTime = System.nanoTime();

	    @Override
	    public void run()
	    {
		try
		{
		    if(!connected() && !m_disconnected.get())
			synchronized(m_mutex)
			{
			    try
			    {
				m_mutex.wait(WAIT_TIMEOUT);
			    }
			    catch(Exception exception)
			    {
			    }
			}

		    if(!connected() || m_disconnected.get())
			return;

		    if(System.nanoTime() - m_accumulatedTime >= 15000000000L)
		    {
			/*
			** Send every 15 seconds.
			*/

			m_accumulatedTime = System.nanoTime();

			if(!m_passthrough.get())
			{
			    if(!m_capabilitiesSent.get())
				m_capabilitiesSent.set
				    (send(getCapabilities()) > 0);

			    send(getIdentities());
			}
		    }

		    /*
		    ** Retrieve a database message.
		    */

		    String array[] = m_database.readOutboundMessage
			(m_lastMessageOid, m_oid.get());

		    /*
		    ** array[0]: Attempts
		    ** array[1]: Message
		    ** array[2]: Message Identity Digest
		    ** array[3]: OID
		    */

		    /*
		    ** If the message is sent successfully, mark its timestamp.
		    */

		    if(array != null && array.length == 4)
		    {
			m_lastMessageOid = Integer.parseInt(array[3]);

			byte bytes[] = m_cryptography.mtd
			    (Base64.decode(array[1], Base64.DEFAULT));

			if(bytes != null)
			    array[1] = new String(bytes);
			else
			    array[1] = "";

			if(array[1].startsWith("OZONE-"))
			{
			    bytes = Base64.decode
				(array[1].substring(6), Base64.NO_WRAP);

			    if(bytes != null)
			    {
				byte timestamp[] = Miscellaneous.longToByteArray
				    (TimeUnit.MILLISECONDS.
				     toMinutes(System.currentTimeMillis()));

				bytes = Miscellaneous.joinByteArrays
				    /*
				    ** Remove the embedded SipHash.
				    */

				    (Arrays.
				     copyOfRange(bytes,
						 0,
						 bytes.length -
						 Cryptography.
						 SIPHASH_IDENTITY_LENGTH),
				     Cryptography.
				     hmac(Miscellaneous.
					  joinByteArrays(bytes, timestamp),
					  m_cryptography.ozoneMacKey()));
			    }
			    else
				array[1] = "";

			    if(bytes != null)
				array[1] = Messages.bytesToMessageString(bytes);
			    else
				array[1] = "";
			}

			if(array[1].isEmpty())
			    m_database.deleteEntry(array[3], "outbound_queue");
			else if(send(Messages.replaceETag(array[1])) > 0)
			{
			    m_database.markMessageTimestamp(array[0], array[3]);

			    if(m_database.
			       writeMessageStatus(m_cryptography, array[2]))
				Kernel.getInstance().notifyOfDataSetChange
				    (array[3]);
			}
		    }
		    else
			m_lastMessageOid = -1;

		    /*
		    ** Echo packets.
		    */

		    synchronized(m_echoQueueMutex)
		    {
			if(!m_echoQueue.isEmpty())
			    /*
			    ** Results of send() are ignored.
			    */

			    send(m_echoQueue.remove(m_echoQueue.size() - 1));
		    }

		    /*
		    ** Transfer real-time packets.
		    */

		    synchronized(m_queueMutex)
		    {
			if(!m_queue.isEmpty())
			    /*
			    ** Results of send() are ignored.
			    */

			    send(m_queue.remove(m_queue.size() - 1));
		    }
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0L, SEND_OUTBOUND_TIMER_INTERVAL, TimeUnit.MILLISECONDS);
    }

    protected String getCapabilities()
    {
	if(m_passthrough.get())
	    return "";

	try
	{
	    StringBuilder message = new StringBuilder();

	    message.append(m_uuid.toString());
	    message.append("\n");
	    message.append(LANE_WIDTH);
	    message.append("\n");
	    message.append("full"); // Echo Mode

	    StringBuilder results = new StringBuilder();

	    results.append("POST HTTP/1.1\r\n");
	    results.append
		("Content-Type: application/x-www-form-urlencoded\r\n");
	    results.append("Content-Length: %1\r\n");
	    results.append("\r\n");
	    results.append("type=0014&content=%2\r\n");
	    results.append("\r\n\r\n");

	    String base64 = Base64.encodeToString
		(message.toString().getBytes(), Base64.DEFAULT);
	    int indexOf = results.indexOf("%1");
	    int length = base64.length() +
		"type=0014&content=\r\n\r\n\r\n".length();

	    results = results.replace
		(indexOf, indexOf + 2, String.valueOf(length));
	    indexOf = results.indexOf("%2");
	    results = results.replace(indexOf, indexOf + 2, base64);
	    return results.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    protected String getIdentities()
    {
	if(m_passthrough.get())
	    return "";

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append(Kernel.getInstance().fireIdentities());
	    stringBuilder.append
		(Messages.
		 identityMessage(Cryptography.
				 sha512(m_cryptography.sipHashId().
					getBytes(StandardCharsets.UTF_8))));
	    return stringBuilder.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    protected String getSessionCipher()
    {
	return "";
    }

    protected abstract String getLocalIp();
    protected abstract boolean connected();
    protected abstract int getLocalPort();
    protected abstract int send(String message);
    protected abstract int send(byte bytes[]);
    protected abstract void connect();

    protected boolean isNetworkConnected()
    {
	try
	{
	    ConnectivityManager connectivityManager = (ConnectivityManager)
		Smoke.getApplication().getSystemService
		(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = connectivityManager.
		getActiveNetworkInfo();

	    return networkInfo != null && networkInfo.isConnected();
	}
	catch(Exception exception)
	{
	}

	return false;
    }

    protected void abort()
    {
	m_disconnected.set(true);

	synchronized(m_mutex)
	{
	    m_mutex.notifyAll();
	}

	synchronized(m_parsingScheduler)
	{
	    try
	    {
		m_parsingScheduler.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	synchronized(m_parsingSchedulerMutex)
	{
	    m_parsingSchedulerMutex.notify();
	}

	synchronized(m_parsingScheduler)
	{
	    try
	    {
		if(!m_parsingScheduler.
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
		    m_parsingScheduler.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	synchronized(m_scheduler)
	{
	    try
	    {
		m_scheduler.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }

	    try
	    {
		if(!m_scheduler.
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
		    m_scheduler.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	synchronized(m_sendOutboundScheduler)
	{
	    try
	    {
		m_sendOutboundScheduler.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }

	    try
	    {
		if(!m_sendOutboundScheduler.
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
		    m_sendOutboundScheduler.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	Miscellaneous.sendBroadcast
	    ("org.purple.smoke.neighbor_aborted", address());
    }

    protected void disconnect()
    {
	m_capabilitiesSent.set(false);
	m_disconnected.set(true);

	synchronized(m_echoQueueMutex)
	{
	    m_echoQueue.clear();
	}

	synchronized(m_mutex)
	{
	    m_mutex.notifyAll();
	}

	synchronized(m_parsingSchedulerMutex)
	{
	    m_parsingSchedulerMutex.notify();
	}

	synchronized(m_queueMutex)
	{
	    m_queue.clear();
	}

	m_stringBuffer.delete(0, m_stringBuffer.length());
	m_stringBuffer.trimToSize();
	Miscellaneous.sendBroadcast
	    ("org.purple.smoke.neighbor_disconnected", address());
    }

    protected void echo(String message)
    {
	Kernel.getInstance().echo(message, m_oid.get());
    }

    protected void echoForce(String message)
    {
	Kernel.getInstance().echoForce(message, m_oid.get());
    }

    protected void setError(String error)
    {
	synchronized(m_errorMutex)
	{
	    m_error.delete(0, m_error.length());
	    m_error.trimToSize();
	    m_error.append(error);
	}
    }

    public abstract String remoteIpAddress();
    public abstract String remotePort();
    public abstract String remoteScopeId();
    public abstract String transport();

    public boolean passthrough()
    {
	return m_passthrough.get();
    }

    public int getOid()
    {
	return m_oid.get();
    }

    public synchronized String address()
    {
	return m_ipAddress + ":" + m_ipPort.get();
    }

    public void clearEchoQueue()
    {
	synchronized(m_echoQueueMutex)
	{
	    m_echoQueue.clear();
	}
    }

    public void clearQueue()
    {
	synchronized(m_queueMutex)
	{
	    m_queue.clear();
	}
    }

    public void scheduleEchoSend(String message)
    {
	if(!connected() ||
	   m_passthrough.get() ||
	   message == null ||
	   message.trim().isEmpty())
	    return;

	synchronized(m_echoQueueMutex)
	{
	    if(m_echoQueue.size() < MAXIMUM_QUEUED_ECHO_PACKETS)
		m_echoQueue.add(message);
	}
    }

    public void scheduleSend(String message)
    {
	if(!connected() ||
	   m_passthrough.get() ||
	   message == null ||
	   message.trim().isEmpty())
	    return;

	synchronized(m_queueMutex)
	{
	    m_queue.add(message);
	}
    }
}
