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
import java.io.FileOutputStream;
import java.util.Hashtable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SteamWriter
{
    private class FileInformation
    {
	public int m_oid = -1;
	public long m_offset = 0;

	public FileInformation(int oid, long offset)
	{
	    m_offset = offset;
	    m_oid = oid;
	}
    }

    private Hashtable<Integer, FileInformation> m_files;
    private final ReentrantReadWriteLock m_filesMutex =
	new ReentrantReadWriteLock();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();

    public SteamWriter()
    {
	m_files = new Hashtable<> ();
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

	FileOutputStream fileOutputStream = null;

	try
	{
	    File file = new File
		(Environment.
		 getExternalStoragePublicDirectory(Environment.
						   DIRECTORY_DOWNLOADS),
		 "smoke-" +
		 Miscellaneous.byteArrayAsHexString(fileIdentity).
		 substring(0, 32));

	    if(!file.exists())
		file.createNewFile();

	    fileOutputStream = new FileOutputStream(file);
	    fileOutputStream.getChannel().position(offset);
	    fileOutputStream.write(packet);
	    m_filesMutex.writeLock().lock();

	    try
	    {
		FileInformation fileInformation = m_files.get(oid);

		if(fileInformation == null)
		    fileInformation = new FileInformation(oid, offset);
		else
		    fileInformation.m_offset = offset;

		m_files.put(oid, fileInformation);
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
	    return false;
	}
	finally
	{
	    try
	    {
		if(fileOutputStream != null)
		    fileOutputStream.close();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	return true;
    }
}
