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
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class Neighbor
{
    private Object m_oidMutex = null;
    private ScheduledExecutorService m_scheduler = null;
    private String m_echoMode = "full";
    private String m_scopeId = "";
    private UUID m_uuid = null;
    private final static int s_laneWidth = 100000;
    private final static int s_silence = 90000; // 90 Seconds
    private final static int s_timerInterval = 10000; // 10 Seconds
    private int m_oid = -1;
    protected Date m_lastTimeReadWrite = null;
    protected Object m_bytesReadMutex = null;
    protected Object m_bytesWrittenMutex = null;
    protected Object m_socketMutex = null;
    protected ScheduledExecutorService m_readSocketScheduler = null;
    protected String m_ipAddress = "";
    protected String m_ipPort = "";
    protected String m_version = "";
    protected StringBuffer m_stringBuffer = null;
    protected final static String s_eom = "\r\n\r\n\r\n";
    protected final static int s_maximumBytes = 32 * 1024 * 1024; // 32 MiB
    protected final static int s_readSocketInterval = 500; // 0.5 Seconds
    protected final static int s_soTimeout = 200; // 250 Milliseconds
    protected long m_bytesRead = 0;
    protected long m_bytesWritten = 0;

    private void saveStatistics()
    {
	int oid = 0;
	long bytesRead = 0;
	long bytesWritten = 0;

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

	String localIp = getLocalIp();
	String localPort = String.valueOf(getLocalPort());
	String peerCertificate = getPeerCertificateString();
	String sessionCiper = getSessionCipher();
	boolean connected = connected();

	Database.getInstance().saveNeighborInformation
	    (Cryptography.getInstance(),
	     String.valueOf(bytesRead),
	     String.valueOf(bytesWritten),
	     localIp,
	     localPort,
	     peerCertificate,
	     sessionCiper,
	     connected ? "connected" : "disconnected",
	     String.valueOf(oid));
    }

    private void terminateOnSilence()
    {
	Date now = new Date();
	boolean disconnect = false;

	synchronized(m_lastTimeReadWrite)
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
	m_bytesReadMutex = new Object();
	m_bytesWrittenMutex = new Object();
	m_ipAddress = ipAddress;
	m_ipPort = ipPort;
	m_lastTimeReadWrite = new Date();
	m_oid = oid;
	m_oidMutex = new Object();
	m_scheduler = Executors.newSingleThreadScheduledExecutor();
	m_scopeId = scopeId;
	m_socketMutex = new Object();
	m_uuid = UUID.randomUUID();
	m_version = version;

	/*
	** Start schedules.
	*/

	m_scheduler.scheduleAtFixedRate
	    (new Runnable()
	    {
		@Override
		public void run()
		{
		    int oid = 0;

		    synchronized(m_oidMutex)
		    {
			oid = m_oid;
		    }

		    String statusControl = Database.getInstance().
			readNeighborStatusControl(Cryptography.getInstance(),
						  oid);

		    if(statusControl.equals("connect"))
			connect();
		    else if(statusControl.equals("disconnect"))
			disconnect();
		    else
		    {
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
    }

    protected void echo(String message)
    {
	synchronized(m_oidMutex)
	{
	    Kernel.getInstance().echo(message, m_oid);
	}
    }

    public abstract void send(String message);

    public synchronized int getOid()
    {
	return m_oid;
    }
}
