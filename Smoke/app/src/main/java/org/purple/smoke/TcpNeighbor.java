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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TcpNeighbor extends Neighbor
{
    private InetSocketAddress m_inetSocketAddress = null;
    private SSLSocket m_socket = null;
    private String m_protocols[] = null;
    private StringBuffer m_stringBuffer = null;
    private Timer m_readSocketTimer = null;
    private TrustManager m_trustManagers[] = null;
    private final static int s_connectionTimeout = 10000; // 10 Seconds
    private final static int s_readSocketInterval = 500; // 0.5 Seconds
    private final static int s_soTimeout = 250; // 250 Milliseconds

    private class ReadSocketTask extends TimerTask
    {
	@Override
	public void run()
	{
	    if(!connected())
		return;

	    try
	    {
		ByteArrayOutputStream byteArrayOutputStream = null;
		long bytesRead = 0;

		synchronized(m_socketMutex)
		{
		    if(m_socket == null)
			return;

		    InputStream inputStream = m_socket.getInputStream();
		    byte bytes[] = new byte[64 * 1024];

		    byteArrayOutputStream = new ByteArrayOutputStream();

		    int i = inputStream.read(bytes);

		    if(i > 0)
		    {
			byteArrayOutputStream.write(bytes, 0, i);
			bytesRead += i;
		    }
		}

		synchronized(m_bytesReadMutex)
		{
		    m_bytesRead += bytesRead;
		}

		if(byteArrayOutputStream != null &&
		   byteArrayOutputStream.size() > 0)
		    synchronized(m_stringBuffer)
		    {
			m_stringBuffer.append
			    (new String(byteArrayOutputStream.toByteArray()));

			/*
			** Detect our end-of-message delimiter and record
			** the message in some database table.
			*/

			int indexOf = m_stringBuffer.indexOf(s_eom);

			while(indexOf >= 0)
			{
			    String buffer = m_stringBuffer.
				substring(0, indexOf + s_eom.length());

			    m_stringBuffer = m_stringBuffer.delete
				(0, buffer.length());
			    indexOf = m_stringBuffer.indexOf(s_eom);
			}

			if(m_stringBuffer.length() > s_maximumBytes)
			    m_stringBuffer.setLength(s_maximumBytes);
		    }
	    }
	    catch(Exception exception)
	    {
	    }
	}
    }

    protected void sendCapabilities()
    {
	if(!connected())
	    return;

	try
	{
	    String capabilities = "";

	    synchronized(m_socketMutex)
	    {
		if(m_socket == null)
		    return;

		OutputStream outputStream = m_socket.getOutputStream();

		capabilities = getCapabilities();
		outputStream.write(capabilities.getBytes());
		outputStream.flush();
	    }

	    synchronized(m_bytesWrittenMutex)
	    {
		m_bytesWritten += capabilities.length();
	    }

	    synchronized(m_lastTimeReadWrite)
	    {
		m_lastTimeReadWrite = new Date();
	    }
	}
	catch(Exception exception)
	{
	    Database.getInstance().writeLog
		("TcpNeighbor::sendCapabilities(): " +
		 exception.getMessage() + ".");
	    disconnect();
	}
    }

    public TcpNeighbor(String ipAddress,
		       String ipPort,
		       String scopeId,
		       String version,
		       int oid)
    {
	super(ipAddress, ipPort, scopeId, "TCP", version, oid);
	m_inetSocketAddress = new InetSocketAddress
	    (m_ipAddress, Integer.parseInt(m_ipPort));
	m_protocols = new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"};
	m_readSocketTimer = new Timer();
	m_stringBuffer = new StringBuffer();
	m_trustManagers = new TrustManager[]
	{
	    new X509TrustManager()
	    {
		public X509Certificate[] getAcceptedIssuers()
		{
		    return new X509Certificate[0];
		}

		public void checkClientTrusted
		    (X509Certificate chain[], String authType)
		{
		}

		public void checkServerTrusted
		    (X509Certificate chain[], String authType)
		{
		}
	    }
	};
    }

    public String getLocalIp()
    {
	synchronized(m_socketMutex)
	{
	    if(m_socket != null && !m_socket.isClosed())
		return m_socket.getLocalAddress().getHostAddress();
	}

	return super.getLocalIp();
    }

    public boolean connected()
    {
	synchronized(m_socketMutex)
	{
	    return m_socket != null &&
		!m_socket.isClosed() &&
		m_socket.getSession() != null &&
		m_socket.getSession().isValid();
	}
    }

    public int getLocalPort()
    {
	synchronized(m_socketMutex)
	{
	    if(m_socket != null && !m_socket.isClosed())
		return m_socket.getLocalPort();
	}

	return super.getLocalPort();
    }

    public void connect()
    {
	if(connected())
	    return;

	try
	{
	    synchronized(m_socketMutex)
	    {
		SSLContext sslContext = SSLContext.getInstance("TLS");

		sslContext.init(null, m_trustManagers, null);
		m_socket = (SSLSocket) sslContext.getSocketFactory().
		    createSocket();
		m_socket.connect(m_inetSocketAddress, s_connectionTimeout);
		m_socket.setEnabledProtocols(m_protocols);
		m_socket.setReceiveBufferSize(32 * 1024 * 1024);
		m_socket.setSoTimeout(s_soTimeout);
	    }

	    synchronized(m_readSocketTimer)
	    {
		m_readSocketTimer.scheduleAtFixedRate
		    (new ReadSocketTask(), 0, s_readSocketInterval);
	    }
	}
	catch(Exception exception)
	{
	    Database.getInstance().writeLog
		("TcpNeighbor::connect(): " + exception.getMessage() + ".");
	    disconnect();
	}
    }

    public void disconnect()
    {
	synchronized(m_readSocketTimer)
	{
	    m_readSocketTimer.cancel();
	    m_readSocketTimer.purge();
	}

	try
	{
	    synchronized(m_socketMutex)
	    {
		if(m_socket != null && !m_socket.isClosed())
		{
		    m_socket.getInputStream().close();
		    m_socket.getOutputStream().close();
		    m_socket.close();
		}
	    }
	}
	catch(Exception exception)
	{
	    Database.getInstance().writeLog
		("TcpNeighbor::disconnect(): error.");
	}
    }
}
