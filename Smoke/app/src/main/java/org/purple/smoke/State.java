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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class State
{
    private ArrayList<MessageElement> m_chatMessages = null;
    private ArrayList<ParticipantElement> m_participants = null;
    private AtomicBoolean m_exit = null;
    private AtomicBoolean m_queryTimerServer = null;
    private AtomicBoolean m_silent = null;
    private Bundle m_bundle = null;
    private Map<Integer, Boolean> m_steamDetailsStates = null;
    private Map<String, Boolean> m_selectedSwitches = null;
    private Map<String, FireChannel> m_fireChannels = null;
    private ScheduledExecutorService m_participantsScheduler = null;
    private final ReentrantReadWriteLock m_bundleMutex = new
	ReentrantReadWriteLock();
    private final ReentrantReadWriteLock m_participantsMutex = new
	ReentrantReadWriteLock();
    private final static long POPULATE_PARTICIPANTS_INTERVAL = 2500L;
    private static State s_instance = null;

    private State()
    {
	m_bundle = new Bundle();
	m_exit = new AtomicBoolean(false);
	m_participants = new ArrayList<> ();
	m_queryTimerServer = new AtomicBoolean(false);
	m_selectedSwitches = new TreeMap<> ();
	m_silent = new AtomicBoolean(false);
	m_steamDetailsStates = new TreeMap<> ();
	populateParticipants();
	setAuthenticated(false);
    }

    private void populateParticipants()
    {
	if(m_participantsScheduler == null)
	    m_participantsScheduler = Executors.
		newSingleThreadScheduledExecutor();

	m_participantsScheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    if(!isAuthenticated())
			return;

		    ArrayList<ParticipantElement> arrayList =
			Database.getInstance().readParticipants
			(Cryptography.getInstance(), "");

		    if(arrayList == null)
			return;
		    else
			Collections.sort(arrayList);

		    m_participantsMutex.writeLock().lock();

		    try
		    {
			if(!arrayList.equals(m_participants))
			{
			    m_participants = arrayList;

			    Intent intent = new Intent
				("org.purple.smoke." +
				 "state_participants_populated");
			    LocalBroadcastManager localBroadcastManager =
				LocalBroadcastManager.
				getInstance(Smoke.getApplication());

			    localBroadcastManager.sendBroadcast(intent);
			}
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_participantsMutex.writeLock().unlock();
		    }
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0L, POPULATE_PARTICIPANTS_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public ArrayList<ParticipantElement> participants()
    {
	m_participantsMutex.readLock().lock();

	try
	{
	    return m_participants;
	}
	finally
	{
	    m_participantsMutex.readLock().unlock();
	}
    }

    public ArrayList<String> participantsNames(String sipHashId)
    {
	ArrayList<String> arrayList = new ArrayList<> ();

	m_participantsMutex.readLock().lock();

	try
	{
	    for(ParticipantElement participantElement : m_participants)
		if(participantElement != null &&
		   !participantElement.m_sipHashId.equals(sipHashId))
		    arrayList.add
			(participantElement.m_name +
			 " (" +
			 participantElement.m_sipHashId +
			 ")");
	}
	finally
	{
	    m_participantsMutex.readLock().unlock();
	}

	return arrayList;
    }

    public CharSequence getCharSequence(String key)
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getCharSequence(key, "");
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return "";
    }

    public FireChannel fireChannel(String name)
    {
	if(name == null)
	    return null;

	if(m_fireChannels != null && m_fireChannels.containsKey(name))
	    return m_fireChannels.get(name);

	return null;
    }

    public Map<String, FireChannel> fireChannels()
    {
	return m_fireChannels;
    }

    public Set<String> selectedSwitches()
    {
	return m_selectedSwitches.keySet();
    }

    public String getString(String key)
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getString(key, "");
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return "";
    }

    public String nameOfFireFromView(View view)
    {
	if(m_fireChannels == null || view == null)
	    return "";

	for(Map.Entry<String, FireChannel> entry : m_fireChannels.entrySet())
	    if(entry.getValue() != null)
		if(entry.getValue().view() == view)
		    return entry.getValue().name();

	return "";
    }

    public boolean chatCheckBoxIsSelected(int oid)
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getChar("chat_checkbox_" + oid, '0') == '1';
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return false;
    }

    public boolean containsFire(String name)
    {
	return m_fireChannels != null && m_fireChannels.containsKey(name);
    }

    public boolean exit()
    {
	return m_exit.get();
    }

    public boolean isAuthenticated()
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getChar("is_authenticated", '0') == '1';
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return false;
    }

    public boolean isLocked()
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getChar("is_locked", '0') == '1';
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return false;
    }

    public boolean neighborsEcho()
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getChar("neighbors_echo", '0') == '1';
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return false;
    }

    public boolean queryTimerServer()
    {
	return m_queryTimerServer.get();
    }

    public boolean silent()
    {
	return m_silent.get();
    }

    public char getChar(String key)
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getChar(key, '0');
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return '0';
    }

    public int chatCheckedParticipants()
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getInt("chat_checkbox_counter", 0);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return 0;
    }

    public long chatSequence(String sipHashId)
    {
	m_bundleMutex.readLock().lock();

	try
	{
	    return m_bundle.getLong("chat_sequence" + sipHashId, 1L);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	return 0L;
    }

    public static synchronized State getInstance()
    {
	if(s_instance == null)
	    s_instance = new State();

	return s_instance;
    }

    public synchronized ArrayList<MessageElement> chatLog()
    {
	return m_chatMessages;
    }

    public synchronized boolean steamDetailsState(int oid)
    {
	if(m_steamDetailsStates.containsKey(oid))
	    return m_steamDetailsStates.get(oid);
	else
	    return false;
    }

    public synchronized void clearChatLog()
    {
	if(m_chatMessages != null)
	    m_chatMessages.clear();

	m_chatMessages = null;
    }

    public synchronized void clearSteamDetailsStates()
    {
	m_steamDetailsStates.clear();
    }

    public synchronized void logChatMessage(String message,
					    String name,
					    String sipHashId,
					    boolean purple,
					    long sequence,
					    long timestamp)
    {
	if(message == null || name == null || sipHashId == null)
	    return;

	if(m_chatMessages == null)
	    m_chatMessages = new ArrayList<> ();

	MessageElement messageElement = new MessageElement();

	messageElement.m_id = sipHashId;
	messageElement.m_message = message;
	messageElement.m_name = name;
	messageElement.m_purple = purple;
	messageElement.m_sequence = sequence;
	messageElement.m_timestamp = timestamp;
	m_chatMessages.add(messageElement);
    }

    public synchronized void removeSteamDetailsState(int oid)
    {
	m_steamDetailsStates.remove(oid);
    }

    public synchronized void setSteamDetailsState(boolean state, int oid)
    {
	m_steamDetailsStates.put(oid, state);
    }

    public void addFire(FireChannel fireChannel)
    {
	if(fireChannel == null)
	    return;

	if(m_fireChannels == null)
	    m_fireChannels = new TreeMap<> ();
	else if(m_fireChannels.containsKey(fireChannel.name()))
	    return;

	m_fireChannels.put(fireChannel.name(), fireChannel);
    }

    public void clearParticipants()
    {
	m_participantsMutex.writeLock().lock();

	try
	{
	    if(m_participants != null)
		m_participants.clear();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_participantsMutex.writeLock().unlock();
	}
    }

    public void clearSelectedSwitches()
    {
	m_selectedSwitches.clear();
    }

    public void incrementChatSequence(String sipHashId)
    {
	long sequence = chatSequence(sipHashId);

	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.putLong("chat_sequence" + sipHashId, sequence + 1L);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void removeChatCheckBoxOid(int oid)
    {
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.remove("chat_checkbox_" + oid);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void removeFireChannel(String name)
    {
	if(m_fireChannels == null)
	    return;

	m_fireChannels.remove(name);
    }

    public void removeKey(String key)
    {
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.remove(key);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void reset()
    {
	clearChatLog();
	clearSelectedSwitches();
	clearSteamDetailsStates();
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.clear();
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}

	if(m_fireChannels != null)
	    m_fireChannels.clear();

	m_participantsMutex.writeLock().lock();

	try
	{
	    if(m_participants != null)
		m_participants.clear();
	}
	finally
	{
	    m_participantsMutex.writeLock().unlock();
	}
    }

    public void selectSwitch(String string, boolean state)
    {
	if(!state)
	    m_selectedSwitches.remove(string);

	if(!string.isEmpty())
	    m_selectedSwitches.put(string, state);
    }

    public void setAuthenticated(boolean state)
    {
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.putChar("is_authenticated", state ? '1' : '0');
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void setChatCheckBoxSelected(int oid, boolean checked)
    {
	boolean contains = false;

	m_bundleMutex.readLock().lock();

	try
	{
	    contains = m_bundle.containsKey("chat_checkbox_" + oid);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.readLock().unlock();
	}

	if(checked)
	{
	    m_bundleMutex.writeLock().lock();

	    try
	    {
		m_bundle.putChar("chat_checkbox_" + oid, '1');

		if(!contains)
		    m_bundle.putInt
			("chat_checkbox_counter",
			 chatCheckedParticipants() + 1);
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_bundleMutex.writeLock().unlock();
	    }
	}
	else
	{
	    m_bundleMutex.writeLock().lock();

	    try
	    {
		m_bundle.remove("chat_checkbox_" + oid);
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_bundleMutex.writeLock().unlock();
	    }

	    if(contains)
	    {
		int counter = chatCheckedParticipants(); // Read lock.

		if(counter > 0)
		    counter -= 1;

		m_bundleMutex.writeLock().lock();

		try
		{
		    m_bundle.putInt("chat_checkbox_counter", counter);
		}
		catch(Exception exception)
		{
		}
		finally
		{
		    m_bundleMutex.writeLock().unlock();
		}
	    }
	}
    }

    public void setExit()
    {
	m_exit.set(true);
    }

    public void setLocked(boolean state)
    {
    	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.putChar("is_locked", state ? '1' : '0');
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void setNeighborsEcho(boolean state)
    {
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.putChar("neighbors_echo", state ? '1' : '0');
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void setQueryTimerServer(boolean state)
    {
	m_queryTimerServer.set(state);
    }

    public void setSilent(boolean state)
    {
	m_silent.set(state);
    }

    public void setString(String key, String value)
    {
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.putString(key, value);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void writeChar(String key, char character)
    {
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.putChar(key, character);
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }

    public void writeCharSequence(String key, CharSequence text)
    {
	m_bundleMutex.writeLock().lock();

	try
	{
	    m_bundle.putCharSequence(key, text);
	}
	finally
	{
	    m_bundleMutex.writeLock().unlock();
	}
    }
}
