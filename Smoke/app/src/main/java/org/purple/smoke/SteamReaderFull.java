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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SteamReaderFull extends SteamReader
{
    private AtomicBoolean m_read = null; // Perform another read.
    private AtomicInteger m_stalled = null;
    private AtomicLong m_lastResponse = null;
    private AtomicLong m_previousOffset = null;
    private AtomicLong m_rc = null;
    private Object m_waitMutex = new Object();
    private String m_sipHashId = "";
    private byte m_fileIdentity[] = null;
    private long m_fileSize = 0L;
    private static int PACKET_SIZE = 32768;
    private static long READ_INTERVAL = 250L; // 250 milliseconds.
    private static long RESPONSE_WINDOW = 7500L; // 7.5 seconds.

    private void computeRate()
    {
	long seconds = Math.abs
	    (System.currentTimeMillis() / 1000L - m_time0.get());

	if(seconds >= 1L)
	{
	    long rate = m_rate.get();

	    m_rate.set
		((long) ((double) (m_readOffset.get() -
				   m_previousOffset.get()) / (double) seconds));

	    if(m_rate.get() > 0L)
		m_stalled.set(0);
	    else if(m_stalled.getAndIncrement() <= 5)
		m_rate.set(rate);

	    m_previousOffset.set(m_readOffset.get());
	    m_time0.set(System.currentTimeMillis() / 1000L);
	}
    }

    private void prepareReader()
    {
	if(m_reader == null)
	{
	    m_reader = Executors.newSingleThreadScheduledExecutor();
	    m_reader.scheduleAtFixedRate(new Runnable()
	    {
		private byte m_keyStream[] = null;

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
			case "received private-key pair":
			    s_databaseHelper.writeSteamStatus
				(s_cryptography, "transferring", "", m_oid, 0);
			    break;
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

			computeRate();
			saveReadOffset();

			if(m_canceled.get() || m_completed.get())
			    return;

			synchronized(m_fileInputStreamMutex)
			{
			    if(m_fileInputStream == null)
				return;
			}

			synchronized(m_waitMutex)
			{
			    m_waitMutex.wait(RESPONSE_WINDOW);
			}

			if(!m_read.get())
			{
			    if(System.currentTimeMillis() -
			       m_lastResponse.get() <= RESPONSE_WINDOW)
				/*
				** A response has not been received.
				*/

				return;
			    else
				m_lastResponse.set(System.currentTimeMillis());
			}

			if(m_keyStream == null)
			{
			    m_keyStream = s_databaseHelper.readSteam
				(s_cryptography, -1, m_oid - 1).m_keyStream;

			    if(m_keyStream == null)
				return;
			}

			byte bytes[] = new byte[PACKET_SIZE];
			int offset = 0;

			synchronized(m_fileInputStreamMutex)
			{
			    m_fileInputStream.getChannel().position
				(m_readOffset.get());
			    offset = m_fileInputStream.read(bytes);
			}

			if(offset == -1)
			{
			    /*
			    ** A response is required, do not set m_completed.
			    */

			    m_read.set(false);
			    return;
			}
			else
			    m_rc.set((long) offset);

			m_read.set(false);

			/*
			** Send a Steam packet.
			*/

			bytes = Messages.steamShare
			    (s_cryptography,
			     m_sipHashId,
			     m_fileIdentity,
			     m_keyStream,
			     Arrays.copyOfRange(bytes, 0, offset),
			     Messages.STEAM_SHARE[0],
			     m_readOffset.get());

			if(bytes != null)
			    Kernel.getInstance().
				sendSteam(false,
					  Messages.bytesToMessageString(bytes).
					  getBytes());
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
	m_previousOffset.set(0L);
	m_rc.set(0L);
	m_read.set(true);
	m_readOffset.set(0L);
	saveReadOffset();

	synchronized(m_waitMutex)
	{
	    m_waitMutex.notify();
	}
    }

    private void saveReadOffset()
    {
	s_databaseHelper.writeSteamStatus
	    (s_cryptography, "", prettyRate(), m_oid, m_readOffset.get());
    }

    public SteamReaderFull(String destination,
			   String fileName,
			   byte fileIdentity[],
			   int oid,
			   long fileSize,
			   long readOffset)
    {
	super(fileName, oid, readOffset);
	m_fileIdentity = fileIdentity;
	m_fileSize = fileSize;
	m_lastResponse = new AtomicLong(System.currentTimeMillis());
	m_previousOffset = new AtomicLong(readOffset);
	m_rc = new AtomicLong(0L);
	m_read = new AtomicBoolean(true);
	m_sipHashId = Miscellaneous.sipHashIdFromDestination(destination);
	m_stalled = new AtomicInteger(0);
	prepareReader();
    }

    public byte[] fileIdentity()
    {
	return m_fileIdentity;
    }

    public void delete()
    {
	super.delete();
	m_lastResponse.set(0L);
	m_previousOffset.set(0L);
	m_rc.set(0L);
	m_read.set(false);

	synchronized(m_waitMutex)
	{
	    m_waitMutex.notify();
	}
    }

    public void setAcknowledgedOffset(long readOffset)
    {
	boolean read = false;

	if(m_readOffset.get() == readOffset)
	{
	    m_lastResponse.set(System.currentTimeMillis());
	    m_readOffset.addAndGet(m_rc.get());
	    read = true;
	    saveReadOffset();
	}

	if(m_fileSize == m_readOffset.get())
	{
	    m_completed.set(true);
	    read = false;
	    s_databaseHelper.writeSteamStatus
		(s_cryptography, "completed", "", m_oid, m_readOffset.get());
	}

	m_read.set(read);

	synchronized(m_waitMutex)
	{
	    m_waitMutex.notify();
	}
    }

    public void setReadInterval(int readInterval)
    {
    }
}
