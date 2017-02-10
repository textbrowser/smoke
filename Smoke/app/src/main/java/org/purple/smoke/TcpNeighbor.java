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
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TcpNeighbor extends Neighbor
{
    private Object m_socketMutex = null;
    private SSLSocket m_socket = null;
    private final static int s_connectionTimeout = 10000; // 10 Seconds
    private final static int s_soLingerTimeout = 10;
    private final static int s_soTimeout = 10000; // 10 Seconds

    public TcpNeighbor(String ipAddress,
		       String ipPort,
		       String scopeId,
		       String version,
		       int oid)
    {
	super(ipAddress, ipPort, scopeId, "TCP", version, oid);
	m_socketMutex = new Object();
    }

    public boolean connected()
    {
	synchronized(m_socketMutex)
	{
	    return m_socket != null &&
		m_socket.isConnected() &&
		m_socket.getSession() != null &&
		m_socket.getSession().isValid();
	}
    }

    public void connect()
    {
	if(connected())
	    return;
	else
	{
	    synchronized(m_socketMutex)
	    {
		if(m_socket != null)
		    return;
	    }
	}

	try
	{
	    SSLContext sslContext = SSLContext.getInstance("TLS");
	    TrustManager trustManagers[] = new TrustManager[]
	    {
		new X509TrustManager()
		{
		    public X509Certificate[] getAcceptedIssuers()
		    {
			return new X509Certificate[0];
		    }

		    public void checkClientTrusted
			(X509Certificate[] chain, String authType)
		    {
		    }

		    public void checkServerTrusted
			(X509Certificate[] chain, String authType)
		    {
		    }
		}
	    };

	    sslContext.init(null, trustManagers, null);
	    synchronized(m_socketMutex)
	    {
		m_socket = (SSLSocket) sslContext.getSocketFactory().
		    createSocket();
		m_socket.connect
		    (new InetSocketAddress(m_ipAddress,
					   Integer.parseInt(m_ipPort)),
		     s_connectionTimeout);
		m_socket.setEnabledProtocols
		    (new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"});
		m_socket.setKeepAlive(true);
		m_socket.setSoLinger(true, s_soLingerTimeout);
		m_socket.setSoTimeout(s_soTimeout);
		m_socket.setUseClientMode(true);
		m_socket.startHandshake();
	    }
	}
	catch(Exception exception)
	{
	    synchronized(m_socketMutex)
	    {
		m_socket = null; // Most likely not required.
	    }
	}
	finally
	{
	}
    }

    public void disconnect()
    {
	try
	{
	    synchronized(m_socketMutex)
	    {
		if(m_socket != null)
		    m_socket.close();
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    synchronized(m_socketMutex)
	    {
		m_socket = null;
	    }
	}
    }
}
