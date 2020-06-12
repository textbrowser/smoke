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

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SteamReaderSimple extends SteamReader
{
    private AtomicLong m_lastBytesSent = null;
    private AtomicLong m_lastTime = null;
    private AtomicLong m_readInterval = null;
    private static int PACKET_SIZE = 8192;

    private void computeRate(long bytesSent)
    {
	long seconds = Math.abs
	    (System.currentTimeMillis() - m_lastTime.get()) / 1000L;

	m_lastBytesSent.getAndAdd(bytesSent);

	if(seconds >= 1L)
	{
	    m_rate.set
		((long) ((double) (m_lastBytesSent.get()) / (double) seconds));
	    m_lastBytesSent.set(0L);
	    m_lastTime.set(System.currentTimeMillis());
	}
    }

    private void prepareReader()
    {
	if(m_reader == null)
	{
	    m_reader = Executors.newSingleThreadScheduledExecutor();
	    m_reader.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			switch(s_databaseHelper.
			       steamStatus(m_oid).toLowerCase().trim())
			{
			case "":
			    /*
			    ** Deleted.
			    */

			    return;
			case "completed":
			    m_completed.set(true);
			    return;
			case "deleted":
			    return;
			case "paused":
			    s_databaseHelper.writeSteamStatus
				(s_cryptography, "", Miscellaneous.RATE, m_oid);
			    return;
			case "rewind":
			    rewind();
			    s_databaseHelper.writeSteamStatus
				(s_cryptography, "paused", "", m_oid, 0);
			    return;
			case "rewind & resume":
			    rewind();
			    s_databaseHelper.writeSteamStatus
				(s_cryptography, "transferring", "", m_oid, 0);
			    break;
			default:
			    break;
			}

			if(Kernel.getInstance().nextSimpleSteamOid() != m_oid ||
			   m_canceled.get())
			    return;

			synchronized(m_fileInputStreamMutex)
			{
			    if(m_fileInputStream == null)
				return;

			    m_fileInputStream.getChannel().position
				(m_readOffset.get());
			}

			byte bytes[] = new byte[PACKET_SIZE];
			int offset = -1;

			synchronized(m_fileInputStreamMutex)
			{
			    offset = m_fileInputStream.read(bytes);
			}

			if(offset == -1)
			{
			    /*
			    ** Completed!
			    */

			    m_completed.set(true);
			    s_databaseHelper.writeSteamStatus
				(s_cryptography,
				 "completed",
				 "",
				 m_oid,
				 m_readOffset.get());
			    return;
			}

			/*
			** Send raw bytes.
			*/

			int sent = Kernel.getInstance().sendSimpleSteam
			    (Arrays.copyOfRange(bytes, 0, offset));

			computeRate(sent);
			m_readOffset.addAndGet((long) sent);
			s_databaseHelper.writeSteamStatus
			    (s_cryptography,
			     "",
			     prettyRate(),
			     m_oid,
			     m_readOffset.get());
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
		    }
		}
	    }, 1500L, m_readInterval.get(), TimeUnit.MILLISECONDS);
	}
    }

    private void rewind()
    {
	m_completed.set(false);

	try
	{
	    synchronized(m_fileInputStreamMutex)
	    {
		if(m_fileInputStream != null)
		    m_fileInputStream.getChannel().position(0);
	    }
	}
	catch(Exception exception)
	{
	}

	m_lastBytesSent.set(0L);
	m_readOffset.set(0L);
    }

    public SteamReaderSimple(String fileName,
			     int oid,
			     long readInterval,
			     long readOffset)
    {
	super(fileName, oid, readOffset);
	m_lastBytesSent = new AtomicLong(0L);
	m_lastTime = new AtomicLong(System.currentTimeMillis());
	m_readInterval = new AtomicLong(1000L / Math.max(4L, readInterval));
	prepareReader();
    }

    public void delete()
    {
	super.delete();
	m_lastBytesSent.set(0);
	m_lastTime.set(0);
    }

    public void setReadInterval(int interval)
    {
	if(1000 / interval == m_readInterval.get())
	    return;

	switch(interval)
	{
	case 4:
	case 10:
	case 20:
	case 50:
	case 100:
	    m_readInterval.set(1000 / interval);
	    break;
	default:
	    m_readInterval.set(250);
	    break;
	}

	m_canceled.set(true);
	cancelReader();
	m_canceled.set(false);
	prepareReader();
    }
}
