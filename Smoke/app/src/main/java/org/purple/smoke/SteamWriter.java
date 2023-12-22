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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SteamWriter
{
    private static class FileInformation
    {
	public byte[] m_fileIdentity = null;
	public int m_oid = -1;
	public long m_lastStatusTimestamp = 0L;
	public long m_offset = 0L;
	public long m_previousOffset = 0L;
	public long m_rate = 0L;
	public long m_time0 = 0L;
	public short m_stalled = 0;

	public FileInformation(byte[] fileIdentity, int oid, long offset)
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

    private ScheduledExecutorService m_scheduler = null;
    private final ConcurrentHashMap<Integer, FileInformation> m_files;
    private final Object m_schedulerMutex = new Object();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static long FILE_INFORMATION_LIFETIME = 15000L; // 15 Seconds
    private final static long SCHEDULER_INTERVAL = 1500L;

    private void removeFileInformation(int oid)
    {
	try
	{
	    m_files.remove(oid);
	}
	catch(Exception exception)
	{
	}
    }

    public SteamWriter()
    {
	m_files = new ConcurrentHashMap<> ();
	m_scheduler = Executors.newSingleThreadScheduledExecutor();
	m_scheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    if(m_files.isEmpty())
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

		    try
		    {
			for(Integer key : m_files.keySet())
			{
			    FileInformation value = m_files.get(key);

			    if(value == null)
			     {
				 m_files.remove(key);
				 continue;
			     }

			    int oid = s_databaseHelper.steamOidFromFileIdentity
				(s_cryptography, value.m_fileIdentity);

			    if(Math.abs(System.currentTimeMillis() -
					value.m_lastStatusTimestamp) >
			       FILE_INFORMATION_LIFETIME ||
			       oid == -1)
			    {
				m_files.remove(key);
				continue;
			    }

			    value.computeRate();
			    s_databaseHelper.writeSteamStatus
				(s_cryptography,
				 "receiving",
				 value.prettyRate(),
				 value.m_oid,
				 value.m_offset);
			}
		    }
		    catch(Exception exception)
		    {
		    }
		}
		catch(Exception exception)
		{
		}
	    }
        }, 1500L, SCHEDULER_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public int size()
    {
	return m_files.size();
    }

    public long write(byte[] fileIdentity, byte[] packet, long offset)
    {
	if(fileIdentity == null ||
	   fileIdentity.length == 0 ||
	   offset < 0L ||
	   packet == null ||
	   packet.length == 0)
	    return -1L;

	int oid = s_databaseHelper.steamOidFromFileIdentity
	    (s_cryptography, fileIdentity);

	if(oid == -1)
	    return -1L;

	SteamElement steamElement = s_databaseHelper.readSteam
	    (s_cryptography, -1, oid - 1);

	if(steamElement == null)
	    return -1L;
	else if(offset + packet.length > steamElement.m_fileSize)
	    /*
	    ** Really?
	    */

	    return -1L;
	else if(steamElement.m_locked &&
		steamElement.m_status.equals("completed"))
	    /*
	    ** Completed and locked! The other participant should halt.
	    */

	    return steamElement.m_fileSize;
	else if(steamElement.m_locked)
	    return -1L;

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

	    if(offset == 0L)
		/*
		** Erase the ephemeral keys.
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
		Miscellaneous.sendBroadcast("org.purple.smoke.steam_status");
		return offset;
	    }

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

	    synchronized(m_schedulerMutex)
	    {
		m_schedulerMutex.notify();
	    }
	}
	catch(Exception exception)
	{
	    return -1L;
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

	return offset;
    }
}
