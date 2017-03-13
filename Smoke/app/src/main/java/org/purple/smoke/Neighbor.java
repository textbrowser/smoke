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
import android.util.SparseArray;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class Neighbor
{
    private ScheduledExecutorService m_scheduler = null;
    private ScheduledExecutorService m_sendOutboundScheduler = null;
    private String m_scopeId = "";
    private UUID m_uuid = null;
    private final Date m_startTime = new Date();
    private final Object m_oidMutex = new Object();
    private final String m_echoMode = "full";
    private final static int s_laneWidth = 100000;
    private final static int s_sendOutboundTimerInterval = 1500; // 1.5 Seconds
    private final static int s_silence = 90000; // 90 Seconds
    private final static int s_timerInterval = 10000; // 10 Seconds
    private int m_oid = -1;
    protected Cryptography m_cryptography = null;
    protected Database m_databaseHelper = null;
    protected Date m_lastTimeReadWrite = null;
    protected ScheduledExecutorService m_readSocketScheduler = null;
    protected String m_ipAddress = "";
    protected String m_ipPort = "";
    protected String m_version = "";
    protected final Object m_bytesReadMutex = new Object();
    protected final Object m_bytesWrittenMutex = new Object();
    protected final Object m_lastTimeReadWriteMutex = new Object();
    protected final Object m_socketMutex = new Object();
    protected final StringBuffer m_stringBuffer = new StringBuffer();
    protected final static String s_eom = "\r\n\r\n\r\n";
    protected final static int s_maximumBytes = 32 * 1024 * 1024; // 32 MiB
    protected final static int s_readSocketInterval = 100; // 100 Milliseconds
    protected final static int s_soTimeout = 200; // 200 Milliseconds
    protected long m_bytesRead = 0;
    protected long m_bytesWritten = 0;

    private void saveStatistics()
    {
	int oid = 0;
	long bytesRead = 0;
	long bytesWritten = 0;
	long uptime = 0;

	synchronized(m_bytesReadMutex)
	{
	    bytesRead = m_bytesRead;
	}

	synchronized(m_bytesWrittenMutex)
	{
	    bytesWritten = m_bytesWritten;
	}

	synchronized(m_oidMutex)
	{
	    oid = m_oid;
	}

	synchronized(m_startTime)
	{
	    uptime = new Date().getTime() - m_startTime.getTime();
	}

	String localIp = getLocalIp();
	String localPort = String.valueOf(getLocalPort());
	String peerCertificate = getPeerCertificateString();
	String sessionCiper = getSessionCipher();
	boolean connected = connected();

	m_databaseHelper.saveNeighborInformation
	    (m_cryptography,
	     String.valueOf(bytesRead),
	     String.valueOf(bytesWritten),
	     localIp,
	     localPort,
	     peerCertificate,
	     sessionCiper,
	     connected ? "connected" : "disconnected",
	     String.valueOf(uptime),
	     String.valueOf(oid));
    }

    private void terminateOnSilence()
    {
	Date now = new Date();
	boolean disconnect = false;

	synchronized(m_lastTimeReadWriteMutex)
	{
	    disconnect = now.getTime() - m_lastTimeReadWrite.getTime() >
		s_silence;
	}

	if(disconnect)
	    disconnect();
    }

    protected Neighbor(String ipAddress,
		       String ipPort,
		       String scopeId,
		       String transport,
		       String version,
		       int oid)
    {
	m_cryptography = Cryptography.getInstance();
	m_databaseHelper = Database.getInstance();
	m_ipAddress = ipAddress;
	m_ipPort = ipPort;
	m_lastTimeReadWrite = new Date();
	m_oid = oid;
	m_scheduler = Executors.newSingleThreadScheduledExecutor();
	m_scopeId = scopeId;
	m_sendOutboundScheduler = Executors.newSingleThreadScheduledExecutor();
	m_uuid = UUID.randomUUID();
	m_version = version;

	/*
	** Start schedules.
	*/

	m_scheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		int oid = -1;

		synchronized(m_oidMutex)
		{
		    oid = m_oid;
		}

		String statusControl = m_databaseHelper.
		    readNeighborStatusControl(m_cryptography, oid);

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

		    abort();
		    disconnect();
		    return;
		}

		saveStatistics();
		sendCapabilities();
		terminateOnSilence();
	    }
	}, 0, s_timerInterval, TimeUnit.MILLISECONDS);

	m_sendOutboundScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		/*
		** Retrieve the first message.
		*/

		int oid = -1;

		synchronized(m_oidMutex)
		{
		    oid = m_oid;
		}

		SparseArray<String> sparseArray =
		    m_databaseHelper.readOutboundMessage(oid);

		/*
		** If the message is sent successfully, remove it.
		*/

		if(sparseArray != null)
		    if(send(sparseArray.get(0)))
			m_databaseHelper.deleteEntry
			    (sparseArray.get(1), "outbound_queue");
	    }
	}, 0, s_sendOutboundTimerInterval, TimeUnit.MILLISECONDS);
    }

    protected String getCapabilities()
    {
	try
	{
	    StringBuffer message = new StringBuffer();

	    message.append(m_uuid.toString());
	    message.append("\n");
	    message.append(String.valueOf(s_laneWidth));
	    message.append("\n");
	    message.append(m_echoMode);

	    StringBuffer results = new StringBuffer();

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

    protected String getPeerCertificateString()
    {
	return "";
    }

    protected String getSessionCipher()
    {
	return "";
    }

    protected abstract String getLocalIp();
    protected abstract boolean connected();
    protected abstract int getLocalPort();
    protected abstract void connect();
    protected abstract void disconnect();
    protected abstract void sendCapabilities();

    protected synchronized void abort()
    {
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

    protected void echo(String message)
    {
	synchronized(m_oidMutex)
	{
	    Kernel.getInstance().echo(message, m_oid);
	}
    }

    public abstract boolean send(String message);

    public synchronized int getOid()
    {
	return m_oid;
    }
}
