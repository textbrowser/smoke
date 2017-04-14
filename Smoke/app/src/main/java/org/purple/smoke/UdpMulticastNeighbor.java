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
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UdpMulticastNeighbor extends Neighbor
{
    private InetAddress m_inetAddress = null;
    private MulticastSocket m_socket = null;

    protected String getLocalIp()
    {
	return m_ipAddress;
    }

    protected boolean connected()
    {
	synchronized(m_socketMutex)
	{
	    return m_socket != null && !m_socket.isClosed();
	}
    }

    protected boolean send(String message)
    {
	boolean ok = false;

	if(!connected())
	    return ok;

	try
	{
	    synchronized(m_socketMutex)
	    {
		if(m_socket == null)
		    return ok;

		DatagramPacket datagramPacket = new DatagramPacket
		    (message.getBytes(),
		     message.getBytes().length,
		     m_inetAddress,
		     Integer.parseInt(m_ipPort));

		m_socket.send(datagramPacket);
	    }

	    ok = true;

	    synchronized(m_bytesWrittenMutex)
	    {
		m_bytesWritten += message.length();
	    }

	    synchronized(m_lastTimeReadWriteMutex)
	    {
		m_lastTimeReadWrite = new Date();
	    }
	}
	catch(Exception exception)
	{
	    disconnect();
	}

	return ok;
    }

    protected int getLocalPort()
    {
	synchronized(m_socketMutex)
	{
	    if(m_socket != null && !m_socket.isClosed())
		return m_socket.getLocalPort();
	}

	return 0;
    }

    protected void disconnect()
    {
	try
	{
	    synchronized(m_socketMutex)
	    {
		if(m_socket != null)
		{
		    if(m_inetAddress != null)
			m_socket.leaveGroup(m_inetAddress);

		    m_socket.close();
		}
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    synchronized(m_bytesReadMutex)
	    {
		m_bytesRead = 0;
	    }

	    synchronized(m_bytesWrittenMutex)
	    {
		m_bytesWritten = 0;
	    }

	    synchronized(m_socketMutex)
	    {
		if(m_socket != null && m_socket.isClosed())
		    m_socket = null;
	    }

	    synchronized(m_startTimeMutex)
	    {
		m_startTime = null;
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
		if(m_inetAddress == null || m_socket == null)
		    return;

		capabilities = getCapabilities();

		DatagramPacket datagramPacket = new DatagramPacket
		    (capabilities.getBytes(),
		     capabilities.getBytes().length,
		     m_inetAddress,
		     Integer.parseInt(m_ipPort));

		m_socket.send(datagramPacket);
	    }

	    synchronized(m_bytesWrittenMutex)
	    {
		m_bytesWritten += capabilities.length();
	    }

	    synchronized(m_lastTimeReadWriteMutex)
	    {
		m_lastTimeReadWrite = new Date();
	    }
	}
	catch(Exception exception)
	{
	    disconnect();
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
	m_readSocketScheduler.scheduleAtFixedRate
	    (new Runnable()
	    {
		@Override
		public void run()
		{
		    if(!connected())
			return;

		    try
		    {
			ByteArrayOutputStream byteArrayOutputStream = null;
			int bytesRead = 0;

			synchronized(m_socketMutex)
			{
			    if(m_socket == null)
				return;

			    DatagramPacket datagramPacket = null;

			    byteArrayOutputStream = new ByteArrayOutputStream();
			    datagramPacket = new DatagramPacket
				(m_bytes, m_bytes.length);
			    m_socket.receive(datagramPacket);

			    if(datagramPacket.getLength() > 0)
				byteArrayOutputStream.write
				    (datagramPacket.getData(),
				     0,
				     datagramPacket.getLength());

			    bytesRead += datagramPacket.getLength();
			}

			if(bytesRead < 0)
			{
			    disconnect();
			    return;
			}

			synchronized(m_bytesReadMutex)
			{
			    m_bytesRead += bytesRead;
			}

			synchronized(m_lastTimeReadWriteMutex)
			{
			    m_lastTimeReadWrite = new Date();
			}

			if(byteArrayOutputStream != null &&
			   byteArrayOutputStream.size() > 0)
			    synchronized(m_stringBuffer)
			    {
				m_stringBuffer.append
				    (new String(byteArrayOutputStream.
						toByteArray()));

				/*
				** Detect our end-of-message delimiter and
				** record the message in some database table.
				*/

				int indexOf = m_stringBuffer.indexOf(s_eom);

				while(indexOf >= 0)
				{
				    String buffer = m_stringBuffer.
					substring(0, indexOf + s_eom.length());

				    if(!Kernel.getInstance().ourMessage(buffer))
					echo(buffer);

				    m_stringBuffer.delete(0, buffer.length());
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
	    }, 0, s_readSocketInterval, TimeUnit.MILLISECONDS);
    }

    public void abort()
    {
	super.abort();
	disconnect();
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
	    synchronized(m_socketMutex)
	    {
		if(m_inetAddress == null)
		    return;
		else if(m_socket != null)
		    return;

		m_socket = new MulticastSocket(Integer.parseInt(m_ipPort));
		m_socket.joinGroup(m_inetAddress);
		m_socket.setLoopbackMode(true);
		m_socket.setSoTimeout(s_soTimeout);
	    }

	    synchronized(m_startTimeMutex)
	    {
		m_startTime = new Date();
	    }
	}
	catch(Exception exception)
	{
	    disconnect();
	}
    }
}
