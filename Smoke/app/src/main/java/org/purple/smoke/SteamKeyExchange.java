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

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SteamKeyExchange
{
    private ArrayList<byte[]> m_array = null;
    private AtomicInteger m_lastReadSteamOid = null;
    private ScheduledExecutorService m_parseScheduler = null;
    private ScheduledExecutorService m_readScheduler = null;
    private final Object m_parseSchedulerMutex = new Object();
    private final ReentrantReadWriteLock m_arrayMutex =
	new ReentrantReadWriteLock();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static long READ_INTERVAL = 7500L;
    private final static long PARSE_INTERVAL = 250;

    public SteamKeyExchange()
    {
	m_array = new ArrayList<> ();
	m_lastReadSteamOid = new AtomicInteger(-1);
	m_parseScheduler = Executors.newSingleThreadScheduledExecutor();
	m_parseScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    boolean empty = false;

		    m_arrayMutex.readLock().lock();

		    try
		    {
			empty = m_array.isEmpty();
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_arrayMutex.readLock().unlock();
		    }

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
		    if(!State.getInstance().isAuthenticated())
			return;

		    /*
		    ** Discover Steam instances which have not established
		    ** key pairs.
		    */

		    SteamElement steamElement = s_databaseHelper.readSteam
			(s_cryptography, -1, m_lastReadSteamOid.get());

		    if(steamElement == null ||
		       steamElement.m_destination.equals(Steam.OTHER) ||
		       steamElement.m_direction == SteamElement.DOWNLOAD)
		    {
			if(steamElement != null)
			    m_lastReadSteamOid.set(steamElement.m_someOid);
			else
			    m_lastReadSteamOid.set(-1);

			return;
		    }

		    if(steamElement.m_keyStream != null &&
		       steamElement.m_keyStream.length == Cryptography.
		       CIPHER_HASH_KEYS_LENGTH)
		    {
			/*
			** Keys were exchanged.
			*/

			m_lastReadSteamOid.set(steamElement.m_someOid);
			return;
		    }

		    KeyPair keyPair = null;

		    if(steamElement.m_ephemeralPrivateKey == null ||
		       steamElement.m_ephemeralPrivateKey.length == 0 ||
		       steamElement.m_ephemeralPublicKey == null ||
		       steamElement.m_ephemeralPublicKey.length == 0)
		    {
			/*
			** Create an RSA private-key pair.
			*/

			keyPair = Cryptography.generatePrivatePublicKeyPair
			    ("RSA", 2048, 0);

			if(keyPair == null)
			    return;

			/*
			** Record the private-key pair.
			*/

			if(!s_databaseHelper.
			   writeSteamEphemeralKeyPair(s_cryptography,
						      keyPair,
						      steamElement.m_someOid))
			    return;
		    }
		    else if(Kernel.getInstance().isNetworkConnected())
			/*
			** Do not enqueue key information if the network
			** is not available.
			*/

			keyPair = Cryptography.generatePrivatePublicKeyPair
			    ("RSA",
			     steamElement.m_ephemeralPrivateKey,
			     steamElement.m_ephemeralPublicKey);

		    /*
		    ** Do not enqueue key information if the network is not
		    ** available.
		    */

		    if(Kernel.getInstance().isNetworkConnected() &&
		       keyPair != null)
		    {
			/*
			** Share the private-key pair.
			*/

			String sipHashId = Miscellaneous.
			    sipHashIdFromDestination
			    (steamElement.m_destination);
			byte bytes[] = null;

			bytes = Messages.steamCall
			    (s_cryptography,
			     steamElement.m_fileName,
			     sipHashId,
			     steamElement.m_fileIdentity,
			     keyPair.getPublic().getEncoded(),
			     Messages.STEAM_KEY_EXCHANGE_KEY_TYPES[1], // RSA
			     Messages.STEAM_KEY_EXCHANGE[0]);

			if(bytes != null)
			    Kernel.getInstance().enqueueSteamKeyExchange
				(Messages.bytesToMessageString(bytes),
				 sipHashId);
		    }

		    /*
		    ** Next element!
		    */

		    m_lastReadSteamOid.set(steamElement.m_someOid);
		}
		catch(Exception exception)
		{
		}
	    }
        }, 1500L, READ_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void append(byte aes[])
    {
	if(aes == null || aes.length == 0)
	    return;

	try
	{
	    m_arrayMutex.writeLock().lock();

	    try
	    {
		m_array.add(aes);
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_arrayMutex.writeLock().unlock();
	    }

	    synchronized(m_parseSchedulerMutex)
	    {
		m_parseSchedulerMutex.notify();
	    }
	}
	catch(Exception exception)
	{
	}
    }
}
