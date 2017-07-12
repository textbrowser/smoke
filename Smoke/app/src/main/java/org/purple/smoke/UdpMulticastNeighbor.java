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
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UdpMulticastNeighbor extends Neighbor
{
    private InetAddress m_inetAddress = null;
    private MulticastSocket m_socket = null;
    private final static int TTL = 255;

    protected String getLocalIp()
    {
	return m_ipAddress;
    }

    protected boolean connected()
    {
	try
	{
	    return m_socket != null && !m_socket.isClosed();
	}
	catch(Exception exception)
	{
	    return false;
	}
    }

    protected boolean send(String message)
    {
	if(!connected())
	    return false;

	try
	{
	    if(m_socket == null)
		return false;

	    DatagramPacket datagramPacket = new DatagramPacket
		(message.getBytes(),
		 message.getBytes().length,
		 m_inetAddress,
		 Integer.parseInt(m_ipPort));

	    Kernel.writeCongestionDigest(datagramPacket.getData());
	    m_socket.send(datagramPacket);
	    m_bytesWritten.getAndAdd(message.length());
	}
	catch(Exception exception)
	{
	    setError("A socket error occurred on send().");
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
	super.disconnect();

	try
	{
	    if(m_socket != null)
	    {
		if(m_inetAddress != null)
		    m_socket.leaveGroup(m_inetAddress);

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

    public UdpMulticastNeighbor(String ipAddress,
				String ipPort,
				String scopeId,
				String version,
				int oid)
    {
	super(ipAddress, ipPort, scopeId, "UDP", version, oid);

	try
	{
	    m_inetAddress = InetAddress.getByName(m_ipAddress);
	}
	catch(Exception exception)
	{
	    m_ipAddress = null;
	}

	m_readSocketScheduler = Executors.newSingleThreadScheduledExecutor();
	m_readSocketScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    if(Thread.currentThread().isInterrupted())
			return;
		    else
			Thread.sleep(5);
		}
		catch(InterruptedException exception)
		{
		    Thread.currentThread().interrupt();
		}
		catch(Exception exception)
		{
		}

		if(!connected())
		    return;

		try
		{
		    if(m_socket == null)
			return;

		    ByteArrayOutputStream byteArrayOutputStream =
			new ByteArrayOutputStream();
		    DatagramPacket datagramPacket = null;

		    datagramPacket = new DatagramPacket
			(m_bytes, m_bytes.length);
		    m_socket.receive(datagramPacket);

		    if(datagramPacket.getLength() > 0)
			byteArrayOutputStream.write
			    (datagramPacket.getData(),
			     0,
			     datagramPacket.getLength());

		    int bytesRead = datagramPacket.getLength();

		    if(bytesRead < 0)
		    {
			setError("A socket receive() error occurred.");
			disconnect();
			return;
		    }

		    m_bytesRead.getAndAdd(bytesRead);
		    m_lastTimeRead.set(System.nanoTime());

		    synchronized(m_stringBuilder)
		    {
			m_stringBuilder.append
			    (new String(byteArrayOutputStream.toByteArray()));
		    }
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0, READ_SOCKET_INTERVAL, TimeUnit.MILLISECONDS);
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

	setError("");

	try
	{
	    m_bytesRead.set(0);
	    m_bytesWritten.set(0);
	    m_lastTimeRead.set(System.nanoTime());
	    m_socket = new MulticastSocket(Integer.parseInt(m_ipPort));
	    m_socket.joinGroup(m_inetAddress);
	    m_socket.setLoopbackMode(true);
	    m_socket.setSoTimeout(SO_TIMEOUT);
	    m_socket.setTimeToLive(TTL);
	    m_startTime.set(System.nanoTime());
	}
	catch(Exception exception)
	{
	    setError("An error occurred while attempting a connection.");
	    disconnect();
	}
    }
}
