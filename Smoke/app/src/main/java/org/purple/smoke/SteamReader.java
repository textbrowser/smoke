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

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import java.io.FileInputStream;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class SteamReader
{
    private final static long AWAIT_TERMINATION = 5L; // 5 seconds.
    protected AssetFileDescriptor m_assetFileDescriptor = null;
    protected AtomicBoolean m_canceled = null;
    protected AtomicBoolean m_completed = null;
    protected AtomicInteger m_oid = null;
    protected AtomicLong m_rate = null;
    protected AtomicLong m_readOffset = null;
    protected AtomicLong m_time0 = null;
    protected FileInputStream m_fileInputStream = null;
    protected ScheduledExecutorService m_reader = null;
    protected final Object m_fileInputStreamMutex = new Object();
    protected final static Cryptography s_cryptography =
	Cryptography.getInstance();
    protected final static Database s_databaseHelper = Database.getInstance();

    protected String prettyRate()
    {
	return Miscellaneous.formattedDigitalInformation
	    (String.valueOf(m_rate.get())) + " / s";
    }

    protected void cancelReader()
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
		if(!m_reader.
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
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

    public SteamReader(String fileName, int oid, long readOffset)
    {
	try
	{
	    if(fileName.lastIndexOf('.') > 0 &&
	       fileName.startsWith("content://"))
	    {
		/*
		** Selected by the operator.
		*/

		fileName = fileName.substring(0, fileName.lastIndexOf('.'));

		Uri uri = Uri.parse(fileName);

		m_assetFileDescriptor = Smoke.getApplication().
		    getContentResolver().openAssetFileDescriptor(uri, "r");
		m_fileInputStream = m_assetFileDescriptor.createInputStream();
	    }
	    else
		/*
		** Shared, or, steamrolled.
		*/

		m_fileInputStream = new FileInputStream(fileName);

	    m_fileInputStream.getChannel().position(Math.max(0, readOffset));
	}
	catch(Exception exception1)
	{
	    try
	    {
		if(m_assetFileDescriptor != null)
		    m_assetFileDescriptor.close();
	    }
	    catch(Exception exception2)
	    {
	    }
	    finally
	    {
		m_assetFileDescriptor = null;
		m_fileInputStream = null;
	    }
	}

	m_canceled = new AtomicBoolean(false);
	m_completed = new AtomicBoolean(false);
	m_oid = new AtomicInteger(oid);
	m_rate = new AtomicLong(0L);
	m_readOffset = new AtomicLong(Math.max(0, readOffset));
	m_time0 = new AtomicLong(System.currentTimeMillis());
    }

    public abstract void setReadInterval(int readInterval);

    public boolean completed()
    {
	return m_completed.get();
    }

    public int getOid()
    {
	return m_oid.get();
    }

    public void delete()
    {
	try
	{
	    if(m_assetFileDescriptor != null)
		m_assetFileDescriptor.close();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_assetFileDescriptor = null;
	}

	m_canceled.set(true);
	m_completed.set(false);

	try
	{
	    synchronized(m_fileInputStreamMutex)
	    {
		if(m_fileInputStream != null)
		    m_fileInputStream.close();
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    synchronized(m_fileInputStreamMutex)
	    {
		m_fileInputStream = null;
	    }
	}

	m_rate.set(0L);
	m_readOffset.set(0L);
	cancelReader();
    }
}
