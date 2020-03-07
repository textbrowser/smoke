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

import android.net.Uri;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SteamReaderSimple
{
    private AtomicBoolean m_completed = null;
    private AtomicInteger m_offset = null;
    private AtomicInteger m_rate = null;
    private ScheduledExecutorService m_reader = null;
    private String m_fileName = "";
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private int m_oid = -1;
    private static int PACKET_SIZE = 4096;
    private static long READ_INTERVAL = 150; // 150 Milliseconds

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
		    InputStream inputStream = null;

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
			default:
			    break;
			}

			inputStream = Smoke.getApplication().
			    getContentResolver().openInputStream
			    (Uri.parse(m_fileName));

			if(inputStream == null)
			    return;

			byte bytes[] = new byte[PACKET_SIZE];
			int offset = inputStream.
			    read(bytes, m_offset.get(), bytes.length);

			if(offset == -1)
			{
			    /*
			    ** Completed!
			    */

			    s_databaseHelper.writeSteamStatus
				("completed", m_oid);
			    return;
			}

			/*
			** Send raw bytes.
			*/

			String transferRate = "0 B / s";
			int sent = Kernel.getInstance().sendSimpleSteam(bytes);

			if(sent > 0)
			{
			    m_offset.addAndGet(sent);
			    s_databaseHelper.writeSteamStatus
				(s_cryptography,
				 transferRate,
				 m_offset.get(),
				 m_oid);
			}
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			try
			{
			    if(inputStream != null)
				inputStream.close();
			}
			catch(Exception exception)
			{
			}
		    }
		}
	    }, 1500, READ_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    public SteamReaderSimple(String fileName, int oid)
    {
	m_fileName = fileName;
	m_offset = new AtomicInteger(0);
	m_oid = oid;
	m_rate = new AtomicInteger(0);
    }

    public void cancel()
    {
	m_offset.set(0);
	m_rate.set(0);
	s_databaseHelper.writeSteamStatus("deleted", m_oid);

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
}
