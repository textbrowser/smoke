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
import java.util.TimerTask;

public class Kernel
{
    private Cryptography m_cryptography = null;
    private Hashtable<Integer, Neighbor> m_neighbors = null;
    private Timer m_congestionPurgeTimer = null;
    private Timer m_neighborsTimer = null;
    private final static int s_congestionPurgeInterval = 15000;
    private final static int s_neighborsInterval = 10000;
    private static Kernel s_instance = null;

    private Kernel()
    {
	m_cryptography = Cryptography.getInstance();
	m_neighbors = new Hashtable<> ();
	prepareTimers();
    }

    private class NeighborsTask extends TimerTask
    {
	@Override
	public void run()
	{
	    prepareNeighbors();
	}
    }

    private synchronized void prepareNeighbors()
    {
	/*
	** Remove null neighbors.
	*/

	for(Hashtable.Entry<Integer, Neighbor> entry: m_neighbors.entrySet())
	    if(entry.getValue() == null)
		m_neighbors.remove(entry.getKey());

	ArrayList<NeighborElement> neighbors =
	    Database.getInstance().readNeighbors(m_cryptography);
	int count = Database.getInstance().count("neighbors");

	if(count == 0 || neighbors == null)
	{
	    if(count == 0)
	    {
		/*
		** The neighbors database table is empty.
		** Remove all neighbors objects.
		*/

		for(Hashtable.Entry<Integer, Neighbor> entry:
			m_neighbors.entrySet())
		    if(entry.getValue() != null)
			entry.getValue().disconnect();

		m_neighbors.clear();
	    }

	    return;
	}

	for(int i = 0; i < neighbors.size(); i++)
	{
	    NeighborElement neighborElement = neighbors.get(i);

	    if(neighborElement == null)
		continue;
	    else if(m_neighbors.containsKey(neighborElement.m_oid))
	    {
		Neighbor neighbor = m_neighbors.get(neighborElement.m_oid);
		String statusControl = neighborElement.m_statusControl.
		    toLowerCase();

		if(statusControl.equals("connect"))
		{
		    if(neighbor != null)
			neighbor.connect();
		}
		else if(statusControl.equals("delete"))
		{
		    if(neighbor != null)
			neighbor.disconnect();

		    m_neighbors.remove(neighborElement.m_oid);
		}
		else if(statusControl.equals("disconnect"))
		{
		    if(neighbor != null)
			neighbor.disconnect();
		}

		if(neighbor != null)
		{
		    if(neighbor.connected())
			Database.getInstance().saveNeighborStatus
			    (m_cryptography,
			     "connected",
			     String.valueOf(neighborElement.m_oid));
		    else
			Database.getInstance().saveNeighborStatus
			    (m_cryptography,
			     "disconnected",
			     String.valueOf(neighborElement.m_oid));
		}

		continue;
	    }
	    else if(neighborElement.m_statusControl.toLowerCase().
		    equals("delete"))
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
	    else if(neighborElement.m_statusControl.toLowerCase().
		    equals("connect"))
		neighbor.connect();
	    else if(neighborElement.m_statusControl.toLowerCase().
		    equals("disconnect"))
		neighbor.disconnect();

	    m_neighbors.put(neighborElement.m_oid, neighbor);
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

	if(m_neighborsTimer == null)
	{
	    m_neighborsTimer = new Timer(true);
	    m_neighborsTimer.scheduleAtFixedRate
		(new NeighborsTask(), 0, s_neighborsInterval);
	}
    }

    public static synchronized Kernel getInstance()
    {
	if(s_instance == null)
	    s_instance = new Kernel();

	return s_instance;
    }
}
