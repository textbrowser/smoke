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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Timer;

public class Kernel
{
    private Cryptography m_cryptography = null;
    private Hashtable<Integer, Neighbor> m_neighbors = null;
    private Timer m_congestionPurgeTimer = null;
    private final static int s_congestionPurgeInterval = 15000;
    private static Kernel s_instance = null;

    private Kernel()
    {
	m_cryptography = Cryptography.getInstance();
	m_neighbors = new Hashtable<> ();
	prepareTimers();
    }

    private void prepareNeighbors()
    {
	ArrayList<NeighborElement> neighbors =
	    Database.getInstance().readNeighbors(m_cryptography);

	if(neighbors == null)
	    return;

	m_neighbors.clear();

	for(int i = 0; i < neighbors.size(); i++)
	{
	    NeighborElement neighborElement = neighbors.get(i);

	    if(neighborElement == null)
		continue;

	    Neighbor neighbor = null;

	    if(neighborElement.m_transport.equals("TCP"))
		neighbor = new TcpNeighbor
		    (neighborElement.m_remoteIpAddress,
		     neighborElement.m_remotePort,
		     neighborElement.m_remoteScopeId,
		     neighborElement.m_ipVersion,
		     neighborElement.m_oid);
	    else if(neighborElement.m_transport.equals("UDP"))
		neighbor = new UdpNeighbor
		    (neighborElement.m_remoteIpAddress,
		     neighborElement.m_remotePort,
		     neighborElement.m_remoteScopeId,
		     neighborElement.m_ipVersion,
		     neighborElement.m_oid);

	    if(neighbor == null)
		continue;

	    m_neighbors.put(neighbors.get(i).m_oid, neighbor);
	}
    }

    private void prepareTimers()
    {
	if(m_congestionPurgeTimer == null)
	{
	    m_congestionPurgeTimer = new Timer(true);
	    m_congestionPurgeTimer.scheduleAtFixedRate
		(new CongestionPurgeTask(), 0, s_congestionPurgeInterval);
	}
    }

    public static synchronized Kernel getInstance()
    {
	if(s_instance == null)
	    s_instance = new Kernel();

	return s_instance;
    }

    public synchronized void restart()
    {
	prepareNeighbors();

	if(m_congestionPurgeTimer != null)
	{
	    m_congestionPurgeTimer.cancel();
	    m_congestionPurgeTimer.purge();
	}

	m_congestionPurgeTimer = new Timer(true);
	m_congestionPurgeTimer.scheduleAtFixedRate
	    (new CongestionPurgeTask(), 0, s_congestionPurgeInterval);
    }
}
