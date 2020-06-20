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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SteamKeyExchange
{
    private ScheduledExecutorService m_parseScheduler = null;
    private ScheduledExecutorService m_readScheduler = null;
    private final Object m_parseSchedulerMutex = new Object();
    private final static long READ_INTERVAL = 5000L;
    private final static long PARSE_INTERVAL = 500L;

    public SteamKeyExchange()
    {
	m_parseScheduler = Executors.newSingleThreadScheduledExecutor();
	m_parseScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    boolean empty = false;

		    if(empty)
			synchronized(m_parseSchedulerMutex)
			{
			    try
			    {
				m_parseSchedulerMutex.wait();
			    }
			    catch(Exception exception)
			    {
			    }
			}
		}
		catch(Exception exception)
		{
		}
	    }
        }, 1500L, PARSE_INTERVAL, TimeUnit.MILLISECONDS);
	m_readScheduler = Executors.newSingleThreadScheduledExecutor();
	m_readScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    /*
		    ** Discover Steam instances which have not established
		    ** key pairs.
		    */
		}
		catch(Exception exception)
		{
		}
	    }
        }, 1500L, READ_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void append(byte bytes[])
    {
	if(bytes == null || bytes.length == 0)
	    return;

	synchronized(m_parseSchedulerMutex)
	{
	    m_parseSchedulerMutex.notify();
	}
    }
}
