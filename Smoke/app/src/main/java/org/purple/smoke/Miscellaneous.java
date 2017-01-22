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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class Miscellaneous
{
    public static byte[] joinByteArrays(byte a[], byte b[])
    {
	if(a == null || b == null)
	    return null;

	byte bytes[] = new byte[a.length + b.length];

	System.arraycopy(a, 0, bytes, 0, a.length);
	System.arraycopy(b, 0, bytes, a.length, b.length);
	return bytes;
    }

    public static void showErrorDialog(Context context, String error)
    {
	AlertDialog alertDialog = new AlertDialog.Builder(context).create();

	alertDialog.setButton
	    (AlertDialog.BUTTON_NEUTRAL, "Dismiss",
	     new DialogInterface.OnClickListener()
	     {
		 public void onClick(DialogInterface dialog, int which)
		 {
		     dialog.dismiss();
		 }
	     });
	alertDialog.setMessage(error);
	alertDialog.setTitle("Error");
	alertDialog.show();
    }

    public static void showPromptDialog(Context context, String prompt)
    {
	AlertDialog alertDialog = new AlertDialog.Builder(context).create();

	alertDialog.setButton
	    (AlertDialog.BUTTON_NEGATIVE, "No",
	     new DialogInterface.OnClickListener()
	     {
		 public void onClick(DialogInterface dialog, int which)
		 {
		     dialog.dismiss();
		 }
	     });
	alertDialog.setButton
	    (AlertDialog.BUTTON_POSITIVE, "Yes",
	     new DialogInterface.OnClickListener()
	     {
		 public void onClick(DialogInterface dialog, int which)
		 {
		     dialog.dismiss();
		 }
	     });
	alertDialog.setMessage(prompt);
	alertDialog.setTitle("Confirmation");
	alertDialog.show();
    }
}
