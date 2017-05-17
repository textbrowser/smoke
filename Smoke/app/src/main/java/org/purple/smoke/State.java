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

import android.os.Bundle;

public class State
{
    private static Bundle s_bundle = null;
    private static State s_instance = null;

    private State()
    {
	setAuthenticated(false);
    }

    public static synchronized State getInstance()
    {
	if(s_bundle == null)
	    s_bundle = new Bundle();

	if(s_instance == null)
	    s_instance = new State();

	return s_instance;
    }

    public synchronized CharSequence getCharSequence(String key)
    {
	return s_bundle.getCharSequence(key);
    }

    public synchronized String getString(String key)
    {
	return s_bundle.getString(key, "");
    }

    public synchronized boolean chatCheckBoxIsSelected(int oid)
    {
	return s_bundle.getChar("chat_checkbox_" + String.valueOf(oid)) == '1';
    }

    public synchronized boolean isAuthenticated()
    {
	return s_bundle.getChar("is_authenticated") == '1';
    }

    public synchronized boolean neighborsEcho()
    {
	return s_bundle.getChar("neighbors_echo") == '1';
    }

    public synchronized int chatCheckedParticipants()
    {
	return s_bundle.getInt("chat_checkbox_counter", 0);
    }

    public synchronized long chatSequence(String sipHashId)
    {
	if(s_bundle.containsKey("chat_sequence" + sipHashId))
	    return s_bundle.getLong("chat_sequence" + sipHashId);
	else
	    return 1;
    }

    public synchronized void incrementChatSequence(String sipHashId)
    {
	long sequence = chatSequence(sipHashId);

	s_bundle.putLong("chat_sequence" + sipHashId, sequence + 1);
    }

    public synchronized void removeKey(String key)
    {
	s_bundle.remove(key);
    }

    public synchronized void reset()
    {
	s_bundle.clear();
    }

    public synchronized void setAuthenticated(boolean state)
    {
	s_bundle.putChar("is_authenticated", state ? '1' : '0');
    }

    public synchronized void setChatCheckBoxSelected(int oid, boolean checked)
    {
	boolean contains = s_bundle.containsKey("chat_checkbox_" +
						String.valueOf(oid));

	if(checked)
	{
	    s_bundle.putChar("chat_checkbox_" + String.valueOf(oid), '1');

	    if(!contains)
		s_bundle.putInt
		    ("chat_checkbox_counter", chatCheckedParticipants() + 1);
	}
	else
	{
	    s_bundle.remove("chat_checkbox_" + String.valueOf(oid));

	    if(contains)
	    {
		int counter = chatCheckedParticipants();

		if(counter > 0)
		    counter -= 1;

		s_bundle.putInt("chat_checkbox_counter", counter);
	    }
	}
    }

    public synchronized void setNeighborsEcho(boolean state)
    {
	s_bundle.putChar("neighbors_echo", state ? '1' : '0');
    }

    public synchronized void setString(String key, String value)
    {
	s_bundle.putString(key, value);
    }

    public synchronized void writeCharSequence(String key, CharSequence text)
    {
	s_bundle.putCharSequence(key, text);
    }
}
