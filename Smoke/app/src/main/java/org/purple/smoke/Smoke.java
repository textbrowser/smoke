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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;

public class Smoke extends Application
{
    private static Smoke s_instance = null;

    public static synchronized Smoke getApplication()
    {
	/*
	** An unpleasant and necessary solution.
	*/

	return s_instance;
    }

    public static synchronized void exit(final Context context)
    {
	if(context != null)
	{
	    final DialogInterface.OnCancelListener listener =
		new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    if(State.getInstance().getString("dialog_accepted").
		       equals("true"))
		    {
			Cryptography.getInstance().exit();
			SmokeService.stopForegroundTask(getApplication());

			if(context instanceof Activity)
			    ((Activity) context).finishAndRemoveTask();

			android.os.Process.killProcess
			    (android.os.Process.myPid());
		    }
	        }
	    };

	    Miscellaneous.showPromptDialog
		(context, listener, "Terminate Smoke?");
	}
    }

    @Override
    public void onCreate()
    {
	super.onCreate();
	About.about();
	s_instance = this;
    }

    @Override
    public void onLowMemory()
    {
	super.onLowMemory();

	try
	{
	    Kernel.getInstance().clearMessagesToSend();
	}
	catch(Exception exception)
	{
	}

	try
	{
	    Kernel.getInstance().clearNeighborQueues();
	}
	catch(Exception exception)
	{
	}
    }
}
