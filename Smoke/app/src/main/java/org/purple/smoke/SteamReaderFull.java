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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SteamReaderFull extends SteamReader
{
    private AtomicBoolean m_read = null; // Perform another read.
    private AtomicLong m_acknowledgedOffset = null;
    private AtomicLong m_lastResponse = null;
    private String m_sipHashId = "";
    private byte m_fileIdentity[] = null;
    private long m_fileSize = 0L;
    private static int PACKET_SIZE = 16384;
    private static long READ_INTERVAL = 250L; // 250 milliseconds.
    private static long RESPONSE_WINDOW = 15000L; // 15 seconds.

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
		    byte m_keyStream[] = null;

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

			if(m_canceled.get() || m_fileInputStream == null)
			    return;

			if(m_completed.get() || !m_read.get())
			{
			    if(m_completed.get())
				return;
			    else if(System.currentTimeMillis() -
				    m_lastResponse.get() <= RESPONSE_WINDOW)
				/*
				** A response has not been received.
				*/

				return;
			    else
				m_readOffset.set(m_acknowledgedOffset.get());
			}

			m_fileInputStream.getChannel().position
			    (m_readOffset.get());

			if(m_keyStream == null)
			{
			    m_keyStream = s_databaseHelper.readSteam
				(s_cryptography, -1, m_oid - 1).m_keyStream;

			    if(m_keyStream == null)
				return;
			}

			byte bytes[] = new byte[PACKET_SIZE];
			int offset = m_fileInputStream.read(bytes);

			if(offset == -1)
			{
			    /*
			    ** A response is required, do not set m_completed.
			    */
			}
			else
			    m_readOffset.addAndGet((long) offset);

			m_read.set(false);

			/*
			** Send a Steam packet.
			*/

			Kernel.getInstance().sendSteam
			    (false,
			     Messages.steamShare(s_cryptography,
						 m_sipHashId,
						 m_fileIdentity,
						 m_keyStream,
						 bytes,
						 Messages.STEAM_SHARE[0],
						 m_readOffset.get()));
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, READ_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void rewind()
    {
	m_acknowledgedOffset.set(0L);
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

	m_lastResponse.set(0L);
	m_read.set(true);
	m_readOffset.set(0L);
	saveReadOffset();
    }

    private void saveReadOffset()
    {
    }

    public SteamReaderFull(String destination,
			   String fileName,
			   byte fileIdentity[],
			   int oid,
			   long fileSize,
			   long readOffset)
    {
	super(fileName, oid, readOffset);
	m_acknowledgedOffset = new AtomicLong(0L);
	m_fileIdentity = fileIdentity;
	m_fileSize = fileSize;
	m_lastResponse = new AtomicLong(System.currentTimeMillis());
	m_read = new AtomicBoolean(true);
	m_sipHashId = Miscellaneous.sipHashIdFromDestination(destination);
	prepareReader();
    }

    public void delete()
    {
	super.delete();
	m_acknowledgedOffset.set(0L);
	m_lastResponse.set(0L);
	m_read.set(false);
    }

    public void setAcknowledgedOffset(long readOffset)
    {
	if(m_acknowledgedOffset.get() == readOffset)
	{
	    m_acknowledgedOffset.set(m_readOffset.get());
	    m_lastResponse.set(System.currentTimeMillis());
	    m_read.set(true);
	    saveReadOffset();
	}

	if(m_acknowledgedOffset.get() == m_fileSize)
	{
	    m_completed.set(true);
	    m_read.set(false);
	}
    }

    public void setReadInterval(int readInterval)
    {
    }
}
