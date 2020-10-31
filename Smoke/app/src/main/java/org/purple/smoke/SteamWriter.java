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

import android.os.Environment;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SteamWriter
{
    private class FileInformation
    {
	public byte m_fileIdentity[] = null;
	public int m_oid = -1;
	public long m_lastStatusTimestamp = 0L;
	public long m_offset = 0L;
	public long m_previousOffset = 0L;
	public long m_rate = 0L;
	public long m_time0 = 0L;
	public short m_stalled = 0;

	public FileInformation(byte fileIdentity[], int oid, long offset)
	{
	    m_fileIdentity = fileIdentity;
	    m_lastStatusTimestamp = System.currentTimeMillis();
	    m_offset = offset;
	    m_oid = oid;
	    m_time0 = System.currentTimeMillis();
	}

	public String prettyRate()
	{
	    return Miscellaneous.formattedDigitalInformation
		(String.valueOf(m_rate)) + " / s";
	}

	public void computeRate()
	{
	    long seconds = Math.abs
		(System.currentTimeMillis() / 1000L - m_time0);

	    if(seconds >= 1L)
	    {
		long rate = m_rate;

		m_rate = (long) ((double) (m_offset - m_previousOffset) /
				 (double) seconds);

		if(m_rate > 0L)
		    m_stalled = 0;
		else if(m_stalled++ <= 5)
		    m_rate = rate;

		m_previousOffset = m_offset;
		m_time0 = System.currentTimeMillis() / 1000L;
	    }
	}
    }

    private Hashtable<Integer, FileInformation> m_files;
    private ScheduledExecutorService m_scheduler = null;
    private final Object m_schedulerMutex = new Object();
    private final ReentrantReadWriteLock m_filesMutex =
	new ReentrantReadWriteLock();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static long FILE_INFORMATION_LIFETIME =
	15000L; // 15 seconds.
    private final static long SCHEDULER_INTERVAL = 1500L;

    private void removeFileInformation(int oid)
    {
	m_filesMutex.writeLock().lock();

	try
	{
	    m_files.remove(oid);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_filesMutex.writeLock().unlock();
	}
    }

    public SteamWriter()
    {
	m_files = new Hashtable<> ();
	m_scheduler = Executors.newSingleThreadScheduledExecutor();
	m_scheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    boolean empty = false;

		    m_filesMutex.writeLock().lock();

		    try
		    {
			empty = m_files.isEmpty();
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_filesMutex.writeLock().unlock();
		    }

		    if(empty)
			synchronized(m_schedulerMutex)
			{
			    try
			    {
				m_schedulerMutex.wait(SCHEDULER_INTERVAL);
			    }
			    catch(Exception exception)
			    {
			    }
			}

		    m_filesMutex.writeLock().lock();

		    try
		    {
			Iterator<Hashtable.Entry<Integer, FileInformation> >
			    it = m_files.entrySet().iterator();

			while(it.hasNext())
			{
			    Hashtable.Entry<Integer, FileInformation> entry =
				it.next();

			    if(entry.getValue() == null)
			     {
				 it.remove();
				 continue;
			     }

			    int oid = s_databaseHelper.steamOidFromFileIdentity
				(s_cryptography,
				 entry.getValue().m_fileIdentity);

			    if(Math.
			       abs(System.currentTimeMillis() -
				   entry.getValue().m_lastStatusTimestamp) >
			       FILE_INFORMATION_LIFETIME ||
			       oid == -1)
			    {
				it.remove();
				continue;
			    }

			    entry.getValue().computeRate();
			    s_databaseHelper.writeSteamStatus
				(s_cryptography,
				 "receiving",
				 entry.getValue().prettyRate(),
				 entry.getValue().m_oid,
				 entry.getValue().m_offset);
			}
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_filesMutex.writeLock().unlock();
		    }
		}
		catch(Exception exception)
		{
		}
	    }
        }, 1500L, SCHEDULER_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public boolean write(byte fileIdentity[], byte packet[], long offset)
    {
	if(fileIdentity == null ||
	   fileIdentity.length == 0 ||
	   offset < 0 ||
	   packet == null ||
	   packet.length == 0)
	    return false;

	int oid = s_databaseHelper.steamOidFromFileIdentity
	    (s_cryptography, fileIdentity);

	if(oid == -1)
	    return false;

	SteamElement steamElement = s_databaseHelper.readSteam
	    (s_cryptography, -1, oid - 1);

	if(steamElement == null)
	    return false;

	RandomAccessFile randomAccessFile = null;

	try
	{
	    File file = new File
		(Environment.
		 getExternalStoragePublicDirectory(Environment.
						   DIRECTORY_DOWNLOADS),
		 steamElement.m_fileName);

	    if(!file.exists())
		file.createNewFile();

	    randomAccessFile = new RandomAccessFile(file, "rwd");
	    randomAccessFile.seek(offset);
	    randomAccessFile.write(packet);

	    if(offset == 0)
		/*
		** Erase the ephemeral key.
		*/

		s_databaseHelper.writeEphemeralSteamKeys
		    (s_cryptography, null, null, oid);

	    if(offset + packet.length == steamElement.m_fileSize)
	    {
		removeFileInformation(oid);
		s_databaseHelper.writeSteamStatus
		    (s_cryptography,
		     "completed",
		     "",
		     oid,
		     offset + packet.length);
		return true;
	    }

	    m_filesMutex.writeLock().lock();

	    try
	    {
		FileInformation fileInformation = m_files.get(oid);

		if(fileInformation == null)
		    fileInformation = new FileInformation
			(fileIdentity, oid, offset + packet.length);
		else
		{
		    fileInformation.m_lastStatusTimestamp =
			System.currentTimeMillis();
		    fileInformation.m_offset = offset + packet.length;
		}

		m_files.put(oid, fileInformation);
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_filesMutex.writeLock().unlock();
	    }

	    synchronized(m_schedulerMutex)
	    {
		m_schedulerMutex.notify();
	    }
	}
	catch(Exception exception)
	{
	    return false;
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

	return true;
    }
}
