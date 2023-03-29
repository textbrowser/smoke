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

import android.os.Build;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TcpTlsNeighbor extends Neighbor
{
    private AtomicBoolean m_handshakeCompleted = null;
    private AtomicBoolean m_isValidCertificate = null;
    private InetSocketAddress m_proxyInetSocketAddress = null;
    private SSLSocket m_socket = null;
    private String m_protocols[] = null;
    private String m_proxyIpAddress = "";
    private String m_proxyType = "";
    private TrustManager m_trustManagers[] = null;
    private final static int CONNECTION_TIMEOUT = 10000; // 10 seconds.
    private final static int HANDSHAKE_TIMEOUT = 20000; // 20 seconds.
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
	try
	{
	    if(m_socket != null &&
	       m_socket.getSession() != null &&
	       m_socket.getSession().isValid())
		return m_socket.getSession().getCipherSuite() +
		    "_" +
		    m_socket.getSession().getProtocol();
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
	    return isNetworkConnected() &&
		m_handshakeCompleted.get() &&
		m_isValidCertificate.get() &&
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
	if(bytes == null || bytes.length == 0 || !connected())
	    return 0;

	int sent = 0;

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
	m_handshakeCompleted.set(false);
	m_isValidCertificate.set(false);

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
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
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
	    m_handshakeCompleted.set(false);
	    m_lastParsed.set(System.currentTimeMillis());
	    m_lastTimeRead.set(System.nanoTime());

	    InetSocketAddress inetSocketAddress = new InetSocketAddress
		(m_ipAddress, m_ipPort.get());
	    SSLContext sslContext = null;

	    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		sslContext = SSLContext.getInstance("TLS");
	    else
		sslContext = SSLContext.getInstance("SSL");

	    sslContext.init(null, m_trustManagers, new SecureRandom());

	    if(m_proxyInetSocketAddress == null)
	    {
		m_socket = (SSLSocket) sslContext.getSocketFactory().
		    createSocket();
		m_socket.setReceiveBufferSize(SO_RCVBUF);
		m_socket.setSendBufferSize(SO_SNDBUF);
		m_socket.setUseClientMode(true);
		m_socket.connect(inetSocketAddress, CONNECTION_TIMEOUT);
	    }
	    else
	    {
		Socket socket = null;

		if(m_proxyType.equals("HTTP"))
		    socket = new Socket
			(new Proxy(Proxy.Type.HTTP, m_proxyInetSocketAddress));
		else
		    socket = new Socket
			(new Proxy(Proxy.Type.SOCKS, m_proxyInetSocketAddress));

		socket.setReceiveBufferSize(SO_RCVBUF);
		socket.setSendBufferSize(SO_SNDBUF);
		socket.connect(inetSocketAddress, CONNECTION_TIMEOUT);
		m_socket = (SSLSocket) sslContext.getSocketFactory().
		    createSocket(socket, m_proxyIpAddress, m_proxyPort, true);
		m_socket.setUseClientMode(true);
	    }

	    m_socket.addHandshakeCompletedListener
		(new HandshakeCompletedListener()
		{
		    @Override
		    public void handshakeCompleted
			(HandshakeCompletedEvent event)
		    {
			m_disconnected.set(false);
			m_handshakeCompleted.set(true);
			scheduleSend(getCapabilities());
			scheduleSend(getIdentities());
			Kernel.getInstance().retrieveChatMessages
			    (m_cryptography.sipHashId());
			Miscellaneous.sendBroadcast
			    ("org.purple.smoke.neighbor_connected");

			synchronized(m_mutex)
			{
			    m_mutex.notifyAll();
			}
		    }
		});
	    m_socket.setEnabledProtocols(m_protocols);
	    m_socket.setSoLinger(true, 0);
	    m_socket.setSoTimeout(HANDSHAKE_TIMEOUT); // SSL/TLS process.
	    m_socket.setTcpNoDelay(true);
	    m_startTime.set(System.nanoTime());
	    setError("");

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
		m_socket.shutdownInput();
	}
	catch(Exception exception)
	{
	}

	try
	{
	    if(m_socket != null)
		m_socket.shutdownOutput();
	}
	catch(Exception exception)
	{
	}

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
	    m_handshakeCompleted.set(false);
	    m_isValidCertificate.set(false);
	    m_lastParsed.set(0L);
	    m_socket = null;
	    m_startTime.set(System.nanoTime());
	}
    }

    public TcpTlsNeighbor(String passthrough,
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
	m_handshakeCompleted = new AtomicBoolean(false);
	m_isValidCertificate = new AtomicBoolean(false);

	if(Build.VERSION.RELEASE.startsWith("10") ||
	   Build.VERSION.RELEASE.startsWith("11") ||
	   Build.VERSION.RELEASE.startsWith("12") ||
	   Build.VERSION.RELEASE.startsWith("13"))
	    m_protocols = Cryptography.TLS_NEW;
	else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
	    m_protocols = Cryptography.TLS_V1_V12;
	else
	    m_protocols = Cryptography.TLS_LEGACY_V12;

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
		    else if(m_socket.getSoTimeout() == HANDSHAKE_TIMEOUT)
			/*
			** Reset SO_TIMEOUT from HANDSHAKE_TIMEOUT.
			*/

			m_socket.setSoTimeout(SO_TIMEOUT);

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
		    if(authType == null || authType.length() == 0)
		    {
			m_isValidCertificate.set(false);
			setError("Empty authentication type.");
		    }
		    else if(chain == null || chain.length == 0)
		    {
			m_isValidCertificate.set(false);
			setError("Empty chain.");
		    }
		    else
		    {
			try
			{
			    chain[0].checkValidity();

			    byte bytes[] = m_database.
				neighborRemoteCertificate
				(m_cryptography, m_oid.get());

			    if(bytes == null || bytes.length == 0)
			    {
				m_database.neighborRecordCertificate
				    (m_cryptography,
				     String.valueOf(m_oid.get()),
				     chain[0].getEncoded());
				m_isValidCertificate.set(true);
			    }
			    else if(!Cryptography.memcmp(bytes,
							 chain[0].getEncoded()))
			    {
				abort();
				m_database.neighborControlStatus
				    (m_cryptography,
				     "disconnect",
				     String.valueOf(m_oid.get()));
				m_isValidCertificate.set(false);
				setError("The stored server's " +
					 "certificate does not match the " +
					 "certificate that was provided by " +
					 "the server.");
			    }
			    else
				m_isValidCertificate.set(true);
			}
			catch(Exception exception)
			{
			    abort();
			    m_database.neighborControlStatus
				(m_cryptography,
				 "disconnect",
				 String.valueOf(m_oid.get()));
			    m_isValidCertificate.set(false);
			    setError("A certificate error (" +
				     exception.getMessage() +
				     ") occurred.");
			}
		    }

		    if(!m_isValidCertificate.get())
		    {
			abort();

			synchronized(m_errorMutex)
			{
			    if(m_error.length() == 0)
				m_error.append
				    ("A generic certificate error occurred.");
			}
		    }
		}
	    }
	};
    }

    public String remoteIpAddress()
    {
	try
	{
	    if(m_socket != null && m_socket.getInetAddress() != null)
		return m_socket.getInetAddress().getHostAddress();
	}
	catch(Exception exception)
	{
	}

	if(m_version.equals("IPv4"))
	    return "0.0.0.0";
	else
	    return "::";
    }

    public String remotePort()
    {
	try
	{
	    if(m_socket != null)
		return String.valueOf(m_socket.getPort());
	}
	catch(Exception exception)
	{
	}

	return "0";
    }

    public String remoteScopeId()
    {
	return "";
    }

    public String transport()
    {
	return "TCP";
    }
}
