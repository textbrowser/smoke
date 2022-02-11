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

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ArsonEphemeralKeyGenerator
{
    private ScheduledExecutorService m_generatorSchedule = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static long GENERATOR_INTERVAL = 250L;

    private void prepareSchedulers()
    {
	if(m_generatorSchedule == null)
	{
	    m_generatorSchedule = Executors.newSingleThreadScheduledExecutor();
	    m_generatorSchedule.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    if(!Kernel.getInstance().isNetworkConnected() ||
		       !State.getInstance().isAuthenticated())
			return;

		    try
		    {
			ArrayList<String> arrayList = s_databaseHelper.
			    readSipHashIdStrings(s_cryptography);

			if(arrayList == null || arrayList.isEmpty())
			    return;

			/*
			** Perform periodic exchanges.
			*/

			for(String string : arrayList)
			    Kernel.getInstance().arsonCall
				(ParticipantCall.Algorithms.MCELIECE, string);

			arrayList.clear();
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, GENERATOR_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    public ArsonEphemeralKeyGenerator()
    {
	prepareSchedulers();
    }
}
