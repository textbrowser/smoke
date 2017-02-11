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

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Neighbor
{
    private static final int s_silence = 90000;
    private static final int s_timerInterval = 15000;
    private Timer m_timer = null;
    protected Date m_lastTimeReadWrite = null;
    protected String m_ipAddress = "";
    protected String m_ipPort = "";
    protected String m_scopeId = "";
    protected String m_version = "";
    protected int m_oid = -1;

    private class NeighborTask extends TimerTask
    {
	@Override
	public void run()
	{
	    terminate();
	}
    }

    private void terminate()
    {
	Date now = new Date();
	boolean disconnect = false;

	synchronized(m_lastTimeReadWrite)
	{
	    disconnect = now.getTime() - m_lastTimeReadWrite.getTime() >
		s_silence;
	}

	if(disconnect)
	    disconnect();
    }

    protected Neighbor(String ipAddress,
		       String ipPort,
		       String scopeId,
		       String transport,
		       String version,
		       int oid)
    {
	m_ipAddress = ipAddress;
	m_ipPort = ipPort;
	m_lastTimeReadWrite = new Date();
	m_oid = oid;
	m_scopeId = scopeId;
	m_timer = new Timer(true);
	m_timer.scheduleAtFixedRate(new NeighborTask(), 0, s_timerInterval);
	m_version = version;
    }

    public String getLocalIp()
    {
	if(m_version.equals("IPv4"))
	    return "0.0.0.0";
	else
	    return "::";
    }

    public boolean connected()
    {
	return false;
    }

    public int getLocalPort()
    {
	return 0;
    }

    public int oid()
    {
	return m_oid;
    }

    public void connect()
    {
    }

    public void disconnect()
    {
    }
}
