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

import android.util.SparseArray;
import android.util.SparseIntArray;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Kernel
{
    private Cryptography m_cryptography = null;
    private Database m_databaseHelper = null;
    private ScheduledExecutorService m_neighborsScheduler = null;
    private final SparseArray<Neighbor> m_neighbors = new SparseArray<> ();
    private final static int s_neighborsInterval = 10000; // 10 Seconds
    private static Kernel s_instance = null;

    private Kernel()
    {
	m_cryptography = Cryptography.getInstance();
	m_databaseHelper = Database.getInstance();
	prepareSchedulers();
    }

    private void prepareNeighbors()
    {
	SparseArray<NeighborElement> neighbors =
	    m_databaseHelper.readNeighbors(m_cryptography);
	int count = m_databaseHelper.count("neighbors");

	if(count == 0 || neighbors == null)
	{
	    /*
	    ** Disconnect all existing sockets.
	    */

	    if(count == 0)
		synchronized(m_neighbors)
		{
		    for(int i = 0; i < m_neighbors.size(); i++)
		    {
			int j = m_neighbors.keyAt(i);

			if(m_neighbors.get(j) != null)
			    m_neighbors.get(j).abort();
		    }

		    m_neighbors.clear();
		}

	    Runtime.getRuntime().runFinalization();
	    return;
	}
	else
	    synchronized(m_neighbors)
	    {
		for(int i = m_neighbors.size() - 1; i >= 0; i--)
		{
		    /*
		    ** Remove neighbor objects which do not exist in the
		    ** database.
		    */

		    boolean found = false;
		    int oid = m_neighbors.keyAt(i);

		    for(int j = 0; j < neighbors.size(); j++)
			if(neighbors.get(j) != null &&
			   neighbors.get(j).m_oid == oid)
			{
			    found = true;
			    break;
			}

		    if(!found)
		    {
			if(m_neighbors.get(oid) != null)
			    m_neighbors.get(oid).abort();

			m_neighbors.remove(oid);
		    }
		}
	    }

	for(int i = 0; i < neighbors.size(); i++)
	{
	    NeighborElement neighborElement = neighbors.get(i);

	    if(neighborElement == null)
		continue;
	    else
	    {
		synchronized(m_neighbors)
		{
		    if(m_neighbors.get(neighborElement.m_oid) != null)
			continue;
		}

		if(neighborElement.m_statusControl.toLowerCase().
		   equals("delete") ||
		   neighborElement.m_statusControl.toLowerCase().
		   equals("disconnect"))
		{
		    if(neighborElement.m_statusControl.toLowerCase().
		       equals("disconnect"))
			m_databaseHelper.saveNeighborInformation
			    (m_cryptography,
			     "0",
			     "0",
			     "",
			     "0",
			     "",
			     "",
			     "disconnected",
			     "0",
			     String.valueOf(neighborElement.m_oid));

		    continue;
		}
	    }

	    Neighbor neighbor = null;

	    if(neighborElement.m_transport.equals("TCP"))
		neighbor = new TcpNeighbor
		    (neighborElement.m_remoteIpAddress,
		     neighborElement.m_remotePort,
		     neighborElement.m_remoteScopeId,
		     neighborElement.m_ipVersion,
		     neighborElement.m_oid);
	    else if(neighborElement.m_transport.equals("UDP"))
	    {
		try
		{
		    InetAddress inetAddress = InetAddress.getByName
			(neighborElement.m_remoteIpAddress);

		    if(inetAddress.isMulticastAddress())
			neighbor = new UdpMulticastNeighbor
			    (neighborElement.m_remoteIpAddress,
			     neighborElement.m_remotePort,
			     neighborElement.m_remoteScopeId,
			     neighborElement.m_ipVersion,
			     neighborElement.m_oid);
		    else
			neighbor = new UdpNeighbor
			    (neighborElement.m_remoteIpAddress,
			     neighborElement.m_remotePort,
			     neighborElement.m_remoteScopeId,
			     neighborElement.m_ipVersion,
			     neighborElement.m_oid);
		}
		catch(Exception exception)
		{
		}
	    }

	    if(neighbor == null)
		continue;

	    synchronized(m_neighbors)
	    {
		m_neighbors.append(neighborElement.m_oid, neighbor);
	    }
	}

	Runtime.getRuntime().runFinalization();
    }

    private void prepareSchedulers()
    {
	if(m_neighborsScheduler == null)
	{
	    m_neighborsScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_neighborsScheduler.scheduleAtFixedRate
	    (new Runnable()
	    {
		@Override
		public void run()
		{
		    prepareNeighbors();
		}
	    }, 1500, s_neighborsInterval, TimeUnit.MILLISECONDS);
	}
    }

    public static synchronized Kernel getInstance()
    {
	if(s_instance == null)
	    s_instance = new Kernel();

	return s_instance;
    }

    public void echo(String message, int oid)
    {
	if(message.trim().isEmpty())
	    return;

	synchronized(m_neighbors)
	{
	    for(int i = 0; i < m_neighbors.size(); i++)
	    {
		int j = m_neighbors.keyAt(i);

		if(m_neighbors.get(j) != null &&
		   m_neighbors.get(j).getOid() != oid)
		    m_neighbors.get(j).send(message);
	    }
	}
    }

    public void enqueueMessage(String message)
    {
	if(message.trim().isEmpty())
	    return;

	SparseIntArray neighbors = m_databaseHelper.readNeighborOids();

	if(neighbors != null)
	    for(int i = 0; i < neighbors.size(); i++)
		m_databaseHelper.enqueueOutboundMessage
		    (message, neighbors.get(i));
    }
}
