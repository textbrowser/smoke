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

import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SteamReaderSimple
{
    private AtomicBoolean m_canceled = null;
    private AtomicLong m_lastBytesSent = null;
    private AtomicLong m_lastTime = null;
    private AtomicLong m_offset = null;
    private AtomicLong m_rate = null;
    private AtomicLong m_readInterval = null;
    private ScheduledExecutorService m_reader = null;
    private String m_fileName = "";
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private int m_oid = -1;
    private static int PACKET_SIZE = 4096;

    private String prettyRate()
    {
	return Miscellaneous.formattedDigitalInformation
	    (String.valueOf(m_rate.get())) + " / s";
    }

    private void cancelReader()
    {
	if(m_reader != null)
	{
	    try
	    {
		m_reader.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }

	    try
	    {
		if(!m_reader.awaitTermination(60, TimeUnit.SECONDS))
		    m_reader.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_reader = null;
	    }
	}
    }

    private void computeRate(long bytesSent)
    {
	long seconds = Math.abs
	    (System.currentTimeMillis() - m_lastTime.get()) / 1000;

	m_lastBytesSent.getAndAdd(bytesSent);

	if(seconds >= 1)
	{
	    m_rate.set
		((long) ((double) (m_lastBytesSent.get()) / (double) seconds));
	    m_lastBytesSent.set(0);
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
		    if(m_canceled.get())
			return;

		    RandomAccessFile randomAccessFile = null;

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
			    return;
			case "deleted":
			    return;
			case "paused":
			    return;
			case "rewind":
			    m_offset.set(0);
			    s_databaseHelper.writeSteamStatus
				(s_cryptography, "paused", "", m_oid, 0);
			    return;
			default:
			    break;
			}

			randomAccessFile = new RandomAccessFile
			    (m_fileName, "r");

			if(randomAccessFile == null)
			    return;
			else
			    randomAccessFile.seek(m_offset.get());

			byte bytes[] = new byte[PACKET_SIZE];
			int offset = randomAccessFile.read(bytes);

			if(offset == -1)
			{
			    /*
			    ** Completed!
			    */

			    s_databaseHelper.writeSteamStatus
				(s_cryptography,
				 "completed",
				 "",
				 m_oid,
				 m_offset.get());
			    return;
			}

			/*
			** Send raw bytes.
			*/

			int sent = Kernel.getInstance().sendSimpleSteam
			    (Arrays.copyOfRange(bytes, 0, offset));

			computeRate(sent);
			m_offset.addAndGet(sent);
			s_databaseHelper.writeSteamStatus
			    (s_cryptography,
			     "",
			     prettyRate(),
			     m_oid,
			     m_offset.get());
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			try
			{
			    if(randomAccessFile != null)
				randomAccessFile.close();
			}
			catch(Exception exception)
			{
			}
		    }
		}
	    }, 1500, m_readInterval.get(), TimeUnit.MILLISECONDS);
	}
    }

    public SteamReaderSimple(String fileName, int oid, long offset)
    {
	m_canceled = new AtomicBoolean(false);
	m_fileName = fileName;
	m_lastBytesSent = new AtomicLong(0);
	m_lastTime = new AtomicLong(System.currentTimeMillis());
	m_offset = new AtomicLong(offset);
	m_oid = oid;
	m_rate = new AtomicLong(0);
	m_readInterval = new AtomicLong(1000 / 4);
	prepareReader();
    }

    public void delete()
    {
	m_canceled.set(true);
	m_lastBytesSent.set(0);
	m_lastTime.set(0);
	m_offset.set(0);
	m_rate.set(0);
	cancelReader();
	s_databaseHelper.writeSteamStatus("deleted", m_oid);
    }

    public void setReadInterval(int interval)
    {
	switch(interval)
	{
	case 4:
	case 10:
	case 20:
	case 50:
	    m_readInterval.set(1000 / interval);
	    break;
	default:
	    break;
	}

	m_canceled.set(true);
	cancelReader();
	m_canceled.set(false);
	prepareReader();
    }
}
