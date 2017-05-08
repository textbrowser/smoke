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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TcpNeighbor extends Neighbor
{
    private InetSocketAddress m_inetSocketAddress = null;
    private SSLSocket m_socket = null;
    private String m_protocols[] = null;
    private TrustManager m_trustManagers[] = null;
    private final static int CONNECTION_TIMEOUT = 10000; // 10 Seconds
    private final static int HANDSHAKE_TIMEOUT = 10000; // 10 Seconds

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

    protected String getPeerCertificateString()
    {
	try
	{
	    if(m_socket != null && m_socket.getSession() != null)
	    {
		Certificate peerCertificates[] = m_socket.getSession().
		    getPeerCertificates();

		if(peerCertificates != null && peerCertificates.length > 0)
		    return Cryptography.publicKeyFingerPrint
			(peerCertificates[0].getPublicKey());
	    }
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    protected String getSessionCipher()
    {
	try
	{
	    if(m_socket != null &&
	       m_socket.getSession() != null &&
	       m_socket.getSession().isValid())
		return m_socket.getSession().getCipherSuite();
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    protected boolean connected()
    {
	try
	{
	    return m_socket != null && !m_socket.isClosed() &&
		m_socket.getSession() != null &&
		m_socket.getSession().isValid();
	}
	catch(Exception exception)
	{
	}

	return false;
    }

    protected boolean send(String message)
    {
	if(!connected())
	    return false;

	try
	{
	    if(m_socket == null || m_socket.getOutputStream() == null)
		return false;

	    OutputStream outputStream = m_socket.getOutputStream();

	    outputStream.write(message.getBytes());
	    outputStream.flush();
	    Kernel.writeCongestionDigest(message);
	    m_bytesWritten.getAndAdd(message.length());
	}
	catch(Exception exception)
	{
	    disconnect();
	    return false;
	}

	return true;
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

    protected void disconnect()
    {
	try
	{
	    if(m_socket != null)
	    {
		m_socket.getInputStream().close();
		m_socket.getOutputStream().close();
		m_socket.close();
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bytesRead.set(0);
	    m_bytesWritten.set(0);
	    m_socket = null;
	    m_startTime.set(System.nanoTime());
	}
    }

    protected void sendCapabilities()
    {
	if(!connected())
	    return;

	try
	{
	    String capabilities = "";

	    if(m_socket == null || m_socket.getOutputStream() == null)
		return;

	    OutputStream outputStream = m_socket.getOutputStream();

	    capabilities = getCapabilities();
	    outputStream.write(capabilities.getBytes());
	    outputStream.flush();
	    m_bytesWritten.getAndAdd(capabilities.length());
	}
	catch(Exception exception)
	{
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

	int port = 4710;

	try
	{
	    port = Integer.parseInt(m_ipPort);
	}
	catch(Exception exception)
	{
	    port = 4710;
	}

	m_inetSocketAddress = new InetSocketAddress(m_ipAddress, port);
	m_protocols = new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"};
	m_readSocketScheduler = Executors.newSingleThreadScheduledExecutor();
	m_readSocketScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		if(!connected())
		    return;

		try
		{
		    long bytesRead = 0;

		    if(m_socket == null ||
		       m_socket.getInputStream() == null)
			return;
		    else
			m_socket.setSoTimeout(SO_TIMEOUT);

		    int i = m_socket.getInputStream().read(m_bytes);

		    if(i < 0)
			bytesRead = -1;
		    else if(i > 0)
			bytesRead += i;

		    if(bytesRead < 0)
		    {
			disconnect();
			return;
		    }

		    m_bytesRead.getAndAdd(bytesRead);
		    m_lastTimeRead.set(System.nanoTime());

		    synchronized(m_stringBuffer)
		    {
			m_stringBuffer.append
			    (new String(m_bytes, 0, (int) bytesRead));

			/*
			** Detect our end-of-message delimiter.
			*/

			int indexOf = m_stringBuffer.indexOf(EOM);

			while(indexOf >= 0)
			{
			    String buffer = m_stringBuffer.
				substring(0, indexOf + EOM.length());

			    if(!Kernel.getInstance().ourMessage(buffer))
				echo(buffer);

			    m_stringBuffer.delete(0, buffer.length());
			    indexOf = m_stringBuffer.indexOf(EOM);
			}

			if(m_stringBuffer.length() > MAXIMUM_BYTES)
			    m_stringBuffer.setLength(MAXIMUM_BYTES);
		    }
		}
		catch(java.net.SocketException exception)
		{
		    disconnect();
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0, READ_SOCKET_INTERVAL, TimeUnit.MILLISECONDS);
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

    public void abort()
    {
	disconnect();
	super.abort();
	m_readSocketScheduler.shutdown();

	try
	{
	    m_readSocketScheduler.awaitTermination(60, TimeUnit.SECONDS);
	}
	catch(Exception exception)
	{
	}
    }

    public void connect()
    {
	if(connected())
	    return;

	try
	{
	    m_bytesRead.set(0);
	    m_bytesWritten.set(0);
	    m_lastTimeRead.set(System.nanoTime());

	    SSLContext sslContext = SSLContext.getInstance("TLS");

	    sslContext.init(null, m_trustManagers, null);
	    m_socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
	    m_socket.connect(m_inetSocketAddress, CONNECTION_TIMEOUT);
	    m_socket.setEnabledProtocols(m_protocols);
	    m_socket.setSoTimeout(HANDSHAKE_TIMEOUT); // SSL/TLS process.
	    m_socket.setTcpNoDelay(true);
	    m_startTime.set(System.nanoTime());
	}
	catch(Exception exception)
	{
	    disconnect();
	}
    }
}
