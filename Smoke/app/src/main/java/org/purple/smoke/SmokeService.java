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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class SmokeService extends Service
{
    private boolean m_isRunning = false;
    private final static int NOTIFICATION_ID = 1936551787;

    private void start()
    {
	if(m_isRunning)
	    return;
	else
	    m_isRunning = true;

	Intent notificationIntent = new Intent(this, Settings.class);
	Notification notification = null;
	PendingIntent pendingIntent = PendingIntent.getActivity
	    (this, 0, notificationIntent, 0);

	notification = new Notification.Builder(this).
	    setContentIntent(pendingIntent).
	    setContentText("Smoke Activity").
	    setContentTitle("Smoke Activity").
	    setSmallIcon(R.drawable.smoke).
	    setTicker("Smoke Activity").
	    build();
	startForeground(NOTIFICATION_ID, notification);
    }

    private void stop()
    {
	m_isRunning = false;
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
		return START_NOT_STICKY;
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
	start();
    }

    @Override
    public void onDestroy()
    {
	m_isRunning = false;
	super.onDestroy();
    }
}
