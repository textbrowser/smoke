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

import java.net.DatagramSocket;

public class UdpNeighbor extends Neighbor
{
    private DatagramSocket m_socket;

    protected void sendCapabilities()
    {
    }

    public UdpNeighbor(String ipAddress,
		       String ipPort,
		       String scopeId,
		       String version,
		       int oid)
    {
	super(ipAddress, ipPort, scopeId, "UDP", version, oid);
    }

    public boolean connected()
    {
	synchronized(m_socketMutex)
	{
	    return m_socket != null && m_socket.isConnected();
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
	    synchronized(m_socketMutex)
	    {
		m_socket = new DatagramSocket();
	    }
	}
	catch(Exception exception)
	{
	    synchronized(m_socketMutex)
	    {
		m_socket = null;
	    }
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
