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

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class TcpNeighbor extends Neighbor
{
    private InetSocketAddress m_proxyInetSocketAddress = null;
    private Socket m_socket = null;
    private String m_proxyIpAddress = "";
    private String m_proxyType = "";
    private final static int CONNECTION_TIMEOUT = 10000; // 10 seconds.
    private int m_proxyPort = -1;

    protected String getLocalIp()
    {
	try
	{
	    if(m_socket != null && m_socket.getLocalAddress() != null)
		return m_socket.getLocalAddress().getHostAddress();
	}
	catch(Exception exception)
	{
	}

	if(m_version.equals("IPv4"))
	    return "0.0.0.0";
	else
	    return "::";
    }

    protected String getSessionCipher()
    {
	return "";
    }

    protected boolean connected()
    {
	try
	{
	    return isNetworkConnected() &&
		m_socket != null &&
		!m_socket.isClosed();
	}
	catch(Exception exception)
	{
	}

	return false;
    }

    protected int getLocalPort()
    {
	try
	{
	    if(m_socket != null && !m_socket.isClosed())
		return m_socket.getLocalPort();
	}
	catch(Exception exception)
	{
	}

	return 0;
    }

    protected int send(String message)
    {
	if(!connected() || message == null || message.length() == 0)
	    return 0;
	else
	    return send(message.getBytes());
    }

    protected int send(byte bytes[])
    {
	int sent = 0;

	if(bytes == null || bytes.length == 0 || !connected())
	    return sent;

	try
	{
	    if(m_socket == null || m_socket.getOutputStream() == null)
		return sent;
	    else
		m_socket.getOutputStream().write(bytes);

	    Kernel.writeCongestionDigest(bytes);
	    m_bytesWritten.getAndAdd(bytes.length);
	    sent += bytes.length;
	}
	catch(Exception exception)
	{
	    setError("A socket error occurred on send().");
	    disconnect();
	}

	return sent;
    }

    protected void abort()
    {
	disconnect();
	super.abort();

	synchronized(m_readSocketScheduler)
	{
	    try
	    {
		m_readSocketScheduler.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }

	    try
	    {
		if(!m_readSocketScheduler.
		   awaitTermination(60, TimeUnit.SECONDS))
		    m_readSocketScheduler.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	}
    }

    protected void connect()
    {
	if(connected())
	    return;
	else if(!isNetworkConnected())
	{
	    setError("A network is not available.");
	    return;
	}

	try
	{
	    m_bytesRead.set(0L);
	    m_bytesWritten.set(0L);
	    m_lastParsed.set(System.currentTimeMillis());
	    m_lastTimeRead.set(System.nanoTime());

	    InetSocketAddress inetSocketAddress = new InetSocketAddress
		(m_ipAddress, Integer.parseInt(m_ipPort));

	    if(m_proxyInetSocketAddress == null)
	    {
		m_socket = new Socket();
		m_socket.connect(inetSocketAddress, CONNECTION_TIMEOUT);
	    }
	    else
	    {
		Socket socket = null;

		if(m_proxyType.equals("HTTP"))
		    m_socket = new Socket
			(new Proxy(Proxy.Type.HTTP, m_proxyInetSocketAddress));
		else
		    m_socket = new Socket
			(new Proxy(Proxy.Type.SOCKS, m_proxyInetSocketAddress));

		m_socket.connect(inetSocketAddress, CONNECTION_TIMEOUT);
	    }

	    m_socket.setSoTimeout(CONNECTION_TIMEOUT);
	    m_socket.setTcpNoDelay(true);
	    m_startTime.set(System.nanoTime());
	    setError("");

	    if(!m_passthrough.get())
		Kernel.getInstance().retrieveChatMessages
		    (m_cryptography.sipHashId());

	    synchronized(m_mutex)
	    {
		m_mutex.notifyAll();
	    }
	}
	catch(Exception exception)
	{
	    setError("An error (" +
		     exception.getMessage() +
		     ") occurred while attempting a connection (" +
		     System.nanoTime() + ").");
	    disconnect();
	}
    }

    protected void disconnect()
    {
	super.disconnect();

	try
	{
	    if(m_socket != null)
		m_socket.close();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bytesRead.set(0L);
	    m_bytesWritten.set(0L);
	    m_lastParsed.set(0L);
	    m_socket = null;
	    m_startTime.set(System.nanoTime());
	}
    }

    public TcpNeighbor(String passthrough,
		       String proxyIpAddress,
		       String proxyPort,
		       String proxyType,
		       String ipAddress,
		       String ipPort,
		       String scopeId,
		       String version,
		       int oid)
    {
	super(passthrough, ipAddress, ipPort, scopeId, "TCP", version, oid);
	m_proxyIpAddress = proxyIpAddress;

	try
	{
	    m_proxyPort = Integer.parseInt(proxyPort);
	}
	catch(Exception exception)
	{
	    m_proxyPort = -1;
	}

	m_proxyType = proxyType;

	if(!m_proxyIpAddress.isEmpty() &&
	   m_proxyPort != -1 &&
	   !m_proxyType.isEmpty())
	    try
	    {
		m_proxyInetSocketAddress = new InetSocketAddress
		    (m_proxyIpAddress, m_proxyPort);
	    }
	    catch(Exception exception)
	    {
		m_proxyInetSocketAddress = null;
	    }

	m_readSocketScheduler.scheduleAtFixedRate(new Runnable()
	{
	    private boolean m_error = false;

	    @Override
	    public void run()
	    {
		try
		{
		    if(!connected() && !m_aborted.get())
			synchronized(m_mutex)
			{
			    try
			    {
				m_mutex.wait();
			    }
			    catch(Exception exception)
			    {
			    }
			}

		    if(!connected())
			return;
		    else if(m_error)
		    {
			if(connected())
			    m_error = false;
			else
			    return;
		    }
		    else if(m_socket == null ||
			    m_socket.getInputStream() == null)
			return;

		    byte bytes[] = new byte[BYTES_PER_READ];
		    int i = 0;

		    try
		    {
			i = m_socket.getInputStream().read(bytes);
		    }
		    catch(java.net.SocketTimeoutException exception)
		    {
			i = 0;
		    }
		    catch(Exception exception)
		    {
			m_error = true;
		    }

		    long bytesRead = (long) i;

		    if(bytesRead < 0L || m_error)
		    {
			m_error = true;
			setError("A socket read() error occurred.");
			disconnect();
			return;
		    }
		    else if(bytesRead == 0L)
			return;

		    m_bytesRead.getAndAdd(bytesRead);
		    m_lastTimeRead.set(System.nanoTime());

		    if(m_stringBuffer.length() < MAXIMUM_BYTES)
			m_stringBuffer.append
			    (new String(bytes, 0, (int) bytesRead));

		    synchronized(m_parsingSchedulerMutex)
		    {
			m_parsingSchedulerMutex.notify();
		    }
		}
		catch(java.net.SocketException exception)
		{
		    m_error = true;
		    setError("A socket error occurred while reading data.");
		    disconnect();
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0L, READ_SOCKET_INTERVAL, TimeUnit.MILLISECONDS);
    }
}
