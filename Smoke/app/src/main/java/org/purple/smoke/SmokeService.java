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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class SmokeService extends Service
{
    private WakeLock m_wakeLock = null;

    private void start()
    {
	try
	{
	    if(m_wakeLock != null)
	    {
		if(m_wakeLock.isHeld())
		    m_wakeLock.release();

		m_wakeLock.acquire();
	    }
	}
	catch(Exception exception)
	{
	}
    }

    private void stop()
    {
	try
	{
	    if(m_wakeLock != null && m_wakeLock.isHeld())
		m_wakeLock.release();
	}
	catch(Exception exception)
	{
	}

	stopForeground(true);
	stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
	return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
	if(intent != null && intent.getAction() != null)
	    switch(intent.getAction())
	    {
	    case "start":
		start();
		break;
	    case "stop":
		stop();
		break;
	    default:
		break;
	    }

	return START_STICKY;
    }

    public static void startForegroundTask(Context context)
    {
	if(context == null)
	    return;

	Intent intent = new Intent(context, SmokeService.class);

	intent.setAction("start");
	context.startService(intent);
    }

    public static void stopForegroundTask(Context context)
    {
	if(context == null)
	    return;

	Intent intent = new Intent(context, SmokeService.class);

	intent.setAction("stop");
	context.startService(intent);
    }

    @Override
    public void onCreate()
    {
	super.onCreate();

	if(m_wakeLock == null)
	    try
	    {
		PowerManager powerManager = (PowerManager)
		    Smoke.getApplication().getApplicationContext().
		    getSystemService(Context.POWER_SERVICE);

		if(powerManager != null)
		    m_wakeLock = powerManager.newWakeLock
			(PowerManager.PARTIAL_WAKE_LOCK,
			 "SmokeService:SmokeWakeLockTag");

		if(m_wakeLock != null)
		    m_wakeLock.setReferenceCounted(false);
	    }
	    catch(Exception exception)
	    {
	    }
    }
}
