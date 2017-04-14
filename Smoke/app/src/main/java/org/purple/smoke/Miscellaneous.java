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
import java.nio.ByteBuffer;
import java.text.DecimalFormat;

public class Miscellaneous
{
    public static String byteArrayAsHexString(byte bytes[])
    {
	if(bytes == null)
	    return "";

	try
	{
	    StringBuffer stringBuffer = new StringBuffer();

	    for(byte b : bytes)
		stringBuffer.append(String.format("%02x", b));

	    return stringBuffer.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    public static String byteArrayAsHexStringDelimited(byte bytes[],
						       char delimiter,
						       int offset)
    {
	String string = byteArrayAsHexString(bytes);
	StringBuffer stringBuffer = new StringBuffer();

	try
	{
	    for(int i = 0; i < string.length(); i += offset)
	    {
		stringBuffer.append(string.substring(i, i + offset));
		stringBuffer.append(delimiter);
	    }

	    if(stringBuffer.length() > 0 &&
	       stringBuffer.charAt(stringBuffer.length() - 1) == delimiter)
		return stringBuffer.substring(0, stringBuffer.length() - 1);
	    else
		return stringBuffer.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    public static String delimitString(String string,
				       char delimiter,
				       int offset)
    {
	StringBuffer stringBuffer = new StringBuffer();

	try
	{
	    for(int i = 0; i < string.length(); i += offset)
	    {
		stringBuffer.append(string.substring(i, i + offset));
		stringBuffer.append(delimiter);
	    }

	    if(stringBuffer.length() > 0 &&
	       stringBuffer.charAt(stringBuffer.length() - 1) == delimiter)
		return stringBuffer.substring(0, stringBuffer.length() - 1);
	    else
		return stringBuffer.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    public static String formattedDigitalInformation(String bytes)
    {
	try
	{
	    DecimalFormat decimalFormat = new DecimalFormat("0.00");
	    StringBuffer stringBuffer = new StringBuffer();
	    long v = Integer.decode(bytes).longValue();

	    if(v < 1024)
	    {
		stringBuffer.append(decimalFormat.format(v));
		stringBuffer.append(" B");
	    }
	    else if(v < 1024 * 1024)
	    {
		stringBuffer.append(decimalFormat.format(v / (1.0 * 1024)));
		stringBuffer.append(" KiB");
	    }
	    else if(v < 1024 * 1024 * 1024)
	    {
		stringBuffer.append
		    (decimalFormat.format(v / (1.0 * 1024 * 1024)));
		stringBuffer.append(" MiB");
	    }
	    else
	    {
		stringBuffer.append
		    (decimalFormat.format(v / (1.0 * 1024 * 1024 * 1024)));
		stringBuffer.append(" GiB");
	    }

	    return stringBuffer.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    public static String sipHashIdFromData(byte bytes[])
    {
	SipHash sipHash = new SipHash();
	byte key[] = Cryptography.md5(bytes); /*
					      ** Use the MD-5 digest
					      ** of the public keys
					      ** as the input key to
					      ** SipHash.
					      */

	return byteArrayAsHexStringDelimited
	    (longToByteArray(sipHash.hmac(bytes, key)), ':', 2);
    }

    public static byte[] intToByteArray(int value)
    {
	try
	{
	    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static byte[] joinByteArrays(byte[] ... data)
    {
	if(data == null)
	    return null;

	try
	{
	    int length = 0;

	    for(byte b[] : data)
		if(b != null)
		    length += b.length;

	    if(length == 0)
		return null;

	    byte bytes[] = new byte[length];
	    int i = 0;

	    for(byte b[] : data)
		if(b != null)
		{
		    System.arraycopy(b, 0, bytes, i, b.length);
		    i += b.length;
		}

	    return bytes; // data[0] + data[1] + ... + data[n - 1]
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static byte[] longToByteArray(long value)
    {
	try
	{
	    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();

	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static int countOf(StringBuffer stringBuffer, char character)
    {
	int count = 0;

	if(stringBuffer == null)
	    return count;

	for(int i = 0; i < stringBuffer.length(); i++)
	    if(character == stringBuffer.charAt(i))
		count += 1;

	return count;
    }

    public static long byteArrayToLong(byte bytes[])
    {
	if(bytes == null || bytes.length != Long.BYTES)
	    return 0;

	try
	{
	    ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES);

	    byteBuffer.put(bytes, 0, Long.BYTES);
	    return byteBuffer.getLong();
	}
	catch(Exception exception)
	{
	    return 0;
	}
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

    public static void showPromptDialog
	(Context context,
	 DialogInterface.OnCancelListener cancelListener,
	 String prompt)
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
		     dialog.cancel();
		 }
	     });
	alertDialog.setMessage(prompt);
	alertDialog.setOnCancelListener(cancelListener); /*
							 ** We cannot wait
							 ** for a response.
							 */
	alertDialog.setTitle("Confirmation");
	alertDialog.show();
    }
}
