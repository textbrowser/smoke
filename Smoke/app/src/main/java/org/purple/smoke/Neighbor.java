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
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Neighbor
{
    private ArrayList<String> m_echoQueue = null;
    private ArrayList<String> m_queue = null;
    private ScheduledExecutorService m_parsingScheduler = null;
    private ScheduledExecutorService m_scheduler = null;
    private ScheduledExecutorService m_sendOutboundScheduler = null;
    private String m_scopeId = "";
    private UUID m_uuid = null;
    private final String m_echoMode = "full";
    private final static Object m_echoQueueMutex = new Object();
    private final static Object m_queueMutex = new Object();
    private final static int LANE_WIDTH = 8 * 1024 * 1024; // 8 MiB
    private final static int PARSING_INTERVAL = 100; // Milliseconds
    private final static int SEND_OUTBOUND_TIMER_INTERVAL = 200; // Milliseconds
    private final static int TIMER_INTERVAL = 2500; // 2.5 Seconds
    private final static long SILENCE = 90000; // 90 Seconds
    protected AtomicInteger m_oid = null;
    protected AtomicLong m_bytesRead = null;
    protected AtomicLong m_bytesWritten = null;
    protected AtomicLong m_lastTimeRead = null;
    protected AtomicLong m_startTime = null;
    protected Cryptography m_cryptography = null;
    protected Database m_databaseHelper = null;
    protected ScheduledExecutorService m_readSocketScheduler = null;
    protected String m_ipAddress = "";
    protected String m_ipPort = "";
    protected String m_version = "";
    protected byte m_bytes[] = null;
    protected final StringBuffer m_stringBuffer = new StringBuffer();
    protected final StringBuilder m_error = new StringBuilder();
    protected final static Object m_errorMutex = new Object();
    protected final static int MAXIMUM_BYTES = LANE_WIDTH;
    protected final static int READ_SOCKET_INTERVAL = 100; // 100 Milliseconds
    protected final static int SO_TIMEOUT = 2500; // 2.5 Seconds
    public final static int MAXIMUM_QUEUED_ECHO_PACKETS = 256;

    private void saveStatistics()
    {
	String echoQueueSize = "";
	String error = "";
	String localIp = getLocalIp();
	String localPort = String.valueOf(getLocalPort());
	String sessionCiper = getSessionCipher();
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

	m_databaseHelper.saveNeighborInformation
	    (m_cryptography,
	     String.valueOf(m_bytesRead.get()),
	     String.valueOf(m_bytesWritten.get()),
	     echoQueueSize,
	     error,
	     localIp,
	     localPort,
	     sessionCiper,
	     connected ? (m_bytesRead.get() > 0 &&
			  m_bytesWritten.get() > 0 ?
			  "connected" : "connecting") : "disconnected",
	     String.valueOf(uptime),
	     String.valueOf(m_oid.get()));
    }

    private void terminateOnSilence()
    {
	if((System.nanoTime() - m_lastTimeRead.get()) / 1000000 > SILENCE)
	    disconnect();
    }

    protected Neighbor(String ipAddress,
		       String ipPort,
		       String scopeId,
		       String transport,
		       String version,
		       int oid)
    {
	m_bytes = new byte[1024 * 1024];
	m_bytesRead = new AtomicLong(0);
	m_bytesWritten = new AtomicLong(0);
	m_cryptography = Cryptography.getInstance();
	m_databaseHelper = Database.getInstance();
	m_echoQueue = new ArrayList<> ();
	m_ipAddress = ipAddress;
	m_ipPort = ipPort;
	m_lastTimeRead = new AtomicLong(System.nanoTime());
	m_oid = new AtomicInteger(oid);
	m_parsingScheduler = Executors.newSingleThreadScheduledExecutor();
	m_queue = new ArrayList<> ();
	m_scheduler = Executors.newSingleThreadScheduledExecutor();
	m_scopeId = scopeId;
	m_sendOutboundScheduler = Executors.newSingleThreadScheduledExecutor();
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
		/*
		** Detect our end-of-message delimiter.
		*/

		int indexOf = m_stringBuffer.indexOf(Messages.EOM);

		while(indexOf >= 0)
		{
		    String buffer = m_stringBuffer.
			substring(0, indexOf + Messages.EOM.length());

		    m_stringBuffer.delete(0, buffer.length());
		    indexOf = m_stringBuffer.indexOf(Messages.EOM);

		    if(!Kernel.getInstance().ourMessage(buffer))
			echo(buffer);
		}

		if(m_stringBuffer.length() > MAXIMUM_BYTES)
		    m_stringBuffer.setLength(MAXIMUM_BYTES);
	    }
	}, 0, PARSING_INTERVAL, TimeUnit.MILLISECONDS);
	m_scheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		String statusControl = m_databaseHelper.
		    readNeighborStatusControl(m_cryptography, m_oid.get());

		switch(statusControl)
		{
		case "connect":
		    connect();
		    break;
		case "disconnect":
		    disconnect();
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
	}, 0, TIMER_INTERVAL, TimeUnit.MILLISECONDS);
	m_sendOutboundScheduler.scheduleAtFixedRate(new Runnable()
	{
	    private long m_accumulatedTime = System.nanoTime();

	    @Override
	    public void run()
	    {
		if(!connected())
		    return;

		if(System.nanoTime() - m_accumulatedTime >= 10000000000L)
		{
		    m_accumulatedTime = System.nanoTime();
		    send(getCapabilities());
		    send(getIdentities());
		}

		/*
		** Retrieve a database message.
		*/

		String array[] = m_databaseHelper.readOutboundMessage
		    (m_oid.get());

		/*
		** If the message is sent successfully, remove it
		** from the database.
		*/

		if(array != null && array.length == 2)
		    if(send(array[0]))
			m_databaseHelper.deleteEntry
			    (array[1], "outbound_queue");

		/*
		** Echo packets.
		*/

		synchronized(m_echoQueueMutex)
		{
		    if(!m_echoQueue.isEmpty())
			send(m_echoQueue.remove(0)); // Ignore the results.
		}

		/*
		** Transfer real-time packets.
		*/

		synchronized(m_queueMutex)
		{
		    if(!m_queue.isEmpty())
			send(m_queue.remove(0)); // Ignore the results.
		}
	    }
	}, 0, SEND_OUTBOUND_TIMER_INTERVAL, TimeUnit.MILLISECONDS);
    }

    protected String getCapabilities()
    {
	try
	{
	    StringBuilder message = new StringBuilder();

	    message.append(m_uuid.toString());
	    message.append("\n");
	    message.append(String.valueOf(LANE_WIDTH));
	    message.append("\n");
	    message.append(m_echoMode);

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
	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append(Kernel.getInstance().fireIdentities());
	    stringBuilder.append
		(Messages.identityMessage(Cryptography.
					  sha512(m_cryptography.sipHashId().
						 getBytes("UTF-8"))));
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
    protected abstract boolean send(String message);
    protected abstract int getLocalPort();
    protected abstract void connect();

    protected synchronized boolean isWifiConnected()
    {
	try
	{
	    ConnectivityManager connectivityManager = (ConnectivityManager)
		Smoke.getApplication().getApplicationContext().
		getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = connectivityManager.getNetworkInfo
		(ConnectivityManager.TYPE_WIFI);

	    return networkInfo.isConnected();
	}
	catch(Exception exception)
	{
	}

	return false;
    }

    protected synchronized void abort()
    {
	m_parsingScheduler.shutdown();

	try
	{
	    m_parsingScheduler.awaitTermination(60, TimeUnit.SECONDS);
	}
	catch(Exception exception)
	{
	}

	m_scheduler.shutdown();

	try
	{
	    m_scheduler.awaitTermination(60, TimeUnit.SECONDS);
	}
	catch(Exception exception)
	{
	}

	m_sendOutboundScheduler.shutdown();

	try
	{
	    m_sendOutboundScheduler.awaitTermination(60, TimeUnit.SECONDS);
	}
	catch(Exception exception)
	{
	}
    }

    protected void disconnect()
    {
	synchronized(m_echoQueueMutex)
	{
	    m_echoQueue.clear();
	}

	synchronized(m_queueMutex)
	{
	    m_queue.clear();
	}

	m_stringBuffer.setLength(0);
    }

    protected void echo(String message)
    {
	Kernel.getInstance().echo(message, m_oid.get());
    }

    protected void setError(String error)
    {
	synchronized(m_errorMutex)
	{
	    m_error.setLength(0);
	    m_error.append(error);
	}
    }

    public int getOid()
    {
	return m_oid.get();
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
	if(!connected())
	    return;

	synchronized(m_echoQueueMutex)
	{
	    if(m_echoQueue.size() < MAXIMUM_QUEUED_ECHO_PACKETS)
		m_echoQueue.add(message);
	}
    }

    public void scheduleSend(String message)
    {
	if(!connected())
	    return;

	synchronized(m_queueMutex)
	{
	    m_queue.add(message);
	}
    }
}
