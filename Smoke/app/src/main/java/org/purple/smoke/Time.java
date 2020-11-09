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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Time
{
    private ScheduledExecutorService m_scheduler = null;
    private final static long DELTA = 5L;
    private final static long QUERY_INTERVAL = 30L;

    public Time()
    {
	m_scheduler = Executors.newSingleThreadScheduledExecutor();
	m_scheduler.scheduleAtFixedRate(new Runnable()
	{
	    @Override
	    public void run()
	    {
		if(!Kernel.getInstance().isNetworkConnected() ||
		   !State.getInstance().isAuthenticated() ||
		   !State.getInstance().queryTimerServer())
		    return;

		BufferedReader bufferedReader = null;
		HttpURLConnection httpURLConnection = null;

		try
		{
		    String string = "";
		    URL url = new URL
			("https://worldtimeapi.org/api/timezone/Etc/UTC.txt");

		    httpURLConnection = (HttpURLConnection)
			url.openConnection();
		    httpURLConnection.setRequestMethod("GET");
		    bufferedReader = new BufferedReader
			(new InputStreamReader(httpURLConnection.
					       getInputStream()));

		    while((string = bufferedReader.readLine()) != null)
			if(string.startsWith("unixtime: "))
			{
			    long current = System.currentTimeMillis() / 1000L;

			    if(Math.abs(current -
					Long.parseLong(string.substring(10))) >
			       DELTA)
				Miscellaneous.sendBroadcast
				    ("org.purple.smoke.time",
				     "Please correct the device's time as " +
				     "it is incorrect by at least " +
				     DELTA +
				     " seconds. Local Unix Time: " +
				     current +
				     ". Source Unix Time: " +
				     string +
				     ".");

			    break;
			}

		    httpURLConnection.disconnect();
		}
		catch(Exception exception)
		{
		}
		finally
		{
		    try
		    {
			if(bufferedReader != null)
			    bufferedReader.close();
		    }
		    catch(Exception exception)
		    {
		    }

		    try
		    {
			if(httpURLConnection != null)
			    httpURLConnection.disconnect();
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }
	}, 1L, QUERY_INTERVAL, TimeUnit.SECONDS);
    }
}
