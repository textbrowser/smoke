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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public abstract class Miscellaneous
{
    public static final String RATE = "0.00 B / s";
    public static final int INTEGER_BYTES = 4;
    public static final int LONG_BYTES = 8;
    public static final long LONG_LONG_BYTES = 8L;

    public final static Comparator<IPAddressElement>
	s_ipAddressComparator = new Comparator<IPAddressElement> ()
	{
	    @Override
	    public int compare(IPAddressElement e1, IPAddressElement e2)
	    {
		if(e1 == null || e2 == null)
		    return -1;

		/*
		** Sort by IP address, port, and transport.
		*/

		try
		{
		    byte bytes1[] = InetAddress.getByName(e1.m_ipAddress).
			getAddress();
		    byte bytes2[] = InetAddress.getByName(e2.m_ipAddress).
			getAddress();
		    int length = Math.max(bytes1.length, bytes2.length);

		    for(int i = 0; i < length; i++)
		    {
			byte b1 = (i >= length - bytes1.length) ?
			    bytes1[i - (length - bytes1.length)] : 0;
			byte b2 = (i >= length - bytes2.length) ?
			    bytes2[i - (length - bytes2.length)] : 0;

			if(b1 != b2)
			    return (0xff & b1) - (0xff & b2);
		    }
		}
		catch(Exception exception)
		{
		}

		int i = e1.m_port.compareTo(e2.m_port);

		if(i != 0)
		    return i;

		return e1.m_transport.compareTo(e2.m_transport);
	    }
	};

    public static String byteArrayAsHexString(byte bytes[])
    {
	if(bytes == null || bytes.length == 0)
	    return "";

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    for(byte b : bytes)
		stringBuilder.append(String.format("%02x", b));

	    return stringBuilder.toString();
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
	if(bytes == null || bytes.length == 0 || offset < 0)
	    return "";

	String string = byteArrayAsHexString(bytes);

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();
	    int length = string.length();

	    for(int i = 0; i < length; i += offset)
	    {
		if(i < length - offset)
		    stringBuilder.append(string, i, i + offset);
		else
		    stringBuilder.append(string.substring(i));

		stringBuilder.append(delimiter);
	    }

	    if(stringBuilder.length() > 0 &&
	       stringBuilder.charAt(stringBuilder.length() - 1) == delimiter)
		return stringBuilder.substring(0, stringBuilder.length() - 1);
	    else
		return stringBuilder.toString();
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
	if(offset < 0)
	    return "";

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();
	    int length = string.length();

	    for(int i = 0; i < length; i += offset)
	    {
		if(i < length - offset)
		    stringBuilder.append(string, i, i + offset);
		else
		    stringBuilder.append(string.substring(i));

		stringBuilder.append(delimiter);
	    }

	    if(stringBuilder.length() > 0 &&
	       stringBuilder.charAt(stringBuilder.length() - 1) == delimiter)
		return stringBuilder.substring(0, stringBuilder.length() - 1);
	    else
		return stringBuilder.toString();
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
	    StringBuilder stringBuilder = new StringBuilder();
	    long v = Integer.decode(bytes).longValue();

	    if(v < 1024L)
	    {
		stringBuilder.append(decimalFormat.format(v));
		stringBuilder.append(" B");
	    }
	    else if(v < 1048576L)
	    {
		stringBuilder.append(decimalFormat.format(v / (1024.0)));
		stringBuilder.append(" KiB");
	    }
	    else if(v < 1073741824L)
	    {
		stringBuilder.append
		    (decimalFormat.format(v / (1048576.0)));
		stringBuilder.append(" MiB");
	    }
	    else
	    {
		stringBuilder.append
		    (decimalFormat.format(v / (1073741824.0)));
		stringBuilder.append(" GiB");
	    }

	    return stringBuilder.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    public static String niceBoolean(boolean state)
    {
	if(state)
	    return "True";
	else
	    return "False";
    }

    public static String pemFormat(byte bytes[])
    {
	if(bytes == null || bytes.length == 0)
	    return "";

	try
	{
	    String string = Base64.encodeToString(bytes, Base64.NO_WRAP);
	    StringBuilder stringBuilder = new StringBuilder();
	    int length = string.length();

	    stringBuilder.append("-----BEGIN CERTIFICATE-----\n");

	    for(int i = 0; i < length; i += 64)
		if(i < length - 64)
		{
		    stringBuilder.append(string, i, i + 64);
		    stringBuilder.append("\n");
		}
		else
		{
		    stringBuilder.append(string.substring(i));
		    stringBuilder.append("\n");
		    break;
		}

	    stringBuilder.append("-----END CERTIFICATE-----\n");
	    return stringBuilder.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
    }

    public static String prepareSipHashId(String string)
    {
	if(string == null)
	    return "";
	else
	    return delimitString
		(string.replace("-", "").toUpperCase().trim(), '-', 4);
    }

    public static String sipHashIdFromData(byte bytes[])
    {
	SipHash sipHash = new SipHash();

	return byteArrayAsHexStringDelimited
	    (longArrayToByteArray(sipHash.
				  hmac(bytes,
				       Cryptography.keyForSipHash(bytes),
				       Cryptography.SIPHASH_OUTPUT_LENGTH)),
	     '-', 4).toUpperCase();
    }

    public static String sipHashIdFromDestination(String string)
    {
	int index1 = string.indexOf('(');
	int index2 = string.indexOf(')');

	if(index1 < index2 && index1 > 0 && index2 > 0)
	    return string.substring(index1 + 1, index2).trim();

	return "";
    }

    public static SubMenu addMembersToMenu(Menu menu, int count, int position)
    {
	if(menu == null)
	    return null;

	ArrayList<ParticipantElement> arrayList = State.getInstance().
	    participants();

	/*
	** Do not clear arrayList!
	*/

	if(arrayList != null && arrayList.size() > 0)
	{
	    SubMenu subMenu = null;

	    if(count == menu.size())
		subMenu = menu.addSubMenu
		    (Menu.NONE,
		     Menu.NONE,
		     position,
		     "Chat Messaging Window");
	    else
		subMenu = menu.getItem(menu.size() - 1).getSubMenu();

	    if(subMenu == null)
		return subMenu;

	    subMenu.clear();

	    for(ParticipantElement participantElement : arrayList)
	    {
		if(participantElement == null)
		    continue;

		subMenu.add
		    (1,
		     participantElement.m_oid,
		     0,
		     participantElement.m_name +
		     " (" +
		     prepareSipHashId(participantElement.m_sipHashId) +
		     ")");
	    }

	    return subMenu;
	}

	return null;
    }

    public static byte[] compressed(byte bytes[])
    {
	if(bytes == null)
	    return null;

	try
	{
	    ByteArrayOutputStream byteArrayOutputStream =
		new ByteArrayOutputStream(bytes.length);

	    try
	    {
		try(GZIPOutputStream gzipOutputStream =
		    new GZIPOutputStream(byteArrayOutputStream))
		{
		    gzipOutputStream.write(bytes);
		}

		return byteArrayOutputStream.toByteArray();
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		try
		{
		    byteArrayOutputStream.close();
		}
		catch(Exception exception)
		{
		}
	    }
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] decompressed(byte bytes[])
    {
	if(bytes == null)
	    return null;

	try
	{
	    ByteArrayInputStream byteArrayInputStream = null;
	    ByteArrayOutputStream byteArrayOutputStream = null;

	    try
	    {
		byteArrayInputStream = new ByteArrayInputStream(bytes);
		byteArrayOutputStream = new ByteArrayOutputStream();

		try(GZIPInputStream gzipInputStream =
		    new GZIPInputStream(byteArrayInputStream))
		{
		    byte buffer[] = new byte[4096];
		    int rc = 0;

		    while((rc = gzipInputStream.read(buffer)) > 0)
			byteArrayOutputStream.write(buffer, 0, rc);
		}

		return byteArrayOutputStream.toByteArray();
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		try
		{
		    if(byteArrayInputStream != null)
			byteArrayInputStream.close();
		}
		catch(Exception exception)
		{
		}

		try
		{
		    if(byteArrayOutputStream != null)
			byteArrayOutputStream.close();
		}
		catch(Exception exception)
		{
		}
	    }
	}
	catch(Exception exception)
	{
	}

	return null;
    }

    public static byte[] intToByteArray(int value)
    {
	try
	{
	    return ByteBuffer.allocate(INTEGER_BYTES).putInt(value).array();
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
		if(b != null && b.length > 0)
		    length += b.length;

	    if(length == 0)
		return null;

	    byte bytes[] = new byte[length];
	    int i = 0;

	    for(byte b[] : data)
		if(b != null && b.length > 0)
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

    public static byte[] longArrayToByteArray(long value[])
    {
	try
	{
	    ByteBuffer byteBuffer = ByteBuffer.allocate
		(LONG_BYTES * value.length);

	    for(long l : value)
		byteBuffer.putLong(l);

	    return byteBuffer.array();
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
	    return ByteBuffer.allocate(LONG_BYTES).putLong(value).array();
	}
	catch(Exception exception)
	{
	    return null;
	}
    }

    public static int countOf(StringBuilder stringBuilder, char character)
    {
	if(stringBuilder == null || stringBuilder.length() == 0)
	    return 0;

	int count = 0;
	int length = stringBuilder.length();

	for(int i = 0; i < length; i++)
	    if(character == stringBuilder.charAt(i))
		count += 1;

	return count;
    }

    public static int imagePercentFromArrayLength(int length)
    {
	final int upper = 8 * 1024 * 1024;

	if(length <= upper)
	    return 100;
	else
	    return (int) ((100.0 * ((double) upper)) / ((double) length));
    }

    public static long byteArrayToLong(byte bytes[])
    {
	if(bytes == null || bytes.length != LONG_BYTES)
	    return 0L;

	try
	{
	    ByteBuffer byteBuffer = ByteBuffer.allocate(LONG_BYTES);

	    byteBuffer.put(bytes);
	    byteBuffer.flip();
	    return byteBuffer.getLong();
	}
	catch(Exception exception)
	{
	    return 0L;
	}
    }

    public static long fileSize(String fileName)
    {
	AssetFileDescriptor assetFileDescriptor = null;

	try
	{
	    Uri uri = Uri.parse(fileName);

	    assetFileDescriptor = Smoke.getApplication().getContentResolver().
		openAssetFileDescriptor(uri, "r");
	    return assetFileDescriptor.getParcelFileDescriptor().getStatSize();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    try
	    {
		if(assetFileDescriptor != null)
		    assetFileDescriptor.close();
	    }
	    catch(Exception exception)
	    {
	    }
	}

	return 0L;
    }

    public static void enableChildren(View view, boolean state)
    {
	if(Build.VERSION.SDK_INT > Build.VERSION_CODES.N)
	    /*
	    ** Otherwise, children will force their scrollable parents
	    ** to scroll after said children are enabled. Android 9
	    ** garbage.
	    */

	    return;

	if(view == null)
	    return;
	else if(!(view instanceof ViewGroup))
	{
	    view.setEnabled(state);
	    return;
	}

	ViewGroup viewGroup = (ViewGroup) view;
	int count = viewGroup.getChildCount();

	for(int i = 0; i < count; i++)
	{
	    View child = viewGroup.getChildAt(i);

	    enableChildren(child, state);
	}
    }

    public static void sendBroadcast(Intent intent)
    {
	if(intent == null)
	    return;

	try
	{
	    LocalBroadcastManager localBroadcastManager =
		LocalBroadcastManager.getInstance(Smoke.getApplication());

	    localBroadcastManager.sendBroadcast(intent);
	}
	catch(Exception exception)
	{
	}
    }

    public static void sendBroadcast(String action)
    {
	try
	{
	    Intent intent = new Intent(action);
	    LocalBroadcastManager localBroadcastManager =
		LocalBroadcastManager.getInstance(Smoke.getApplication());

	    localBroadcastManager.sendBroadcast(intent);
	}
	catch(Exception exception)
	{
	}
    }

    public static void sendBroadcast(String action, String extra1)
    {
	try
	{
	    Intent intent = new Intent(action);
	    LocalBroadcastManager localBroadcastManager =
		LocalBroadcastManager.getInstance(Smoke.getApplication());

	    intent.putExtra("org.purple.smoke.extra1", extra1);
	    localBroadcastManager.sendBroadcast(intent);
	}
	catch(Exception exception)
	{
	}
    }

    public static void sendBroadcast(String action, int extra1, int extra2)
    {
	try
	{
	    Intent intent = new Intent(action);
	    LocalBroadcastManager localBroadcastManager =
		LocalBroadcastManager.getInstance(Smoke.getApplication());

	    intent.putExtra("org.purple.smoke.extra1", extra1);
	    intent.putExtra("org.purple.smoke.extra2", extra2);
	    localBroadcastManager.sendBroadcast(intent);
	}
	catch(Exception exception)
	{
	}
    }

    public static void showErrorDialog(Context context, String error)
    {
	if(context == null ||
	   !(context instanceof Activity) ||
	   ((Activity) context).isFinishing())
	    return;

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

    public static void showNotification(Context context,
					Intent intent,
					View view)
    {
	if(context == null ||
	   !(context instanceof Activity) ||
	   intent == null ||
	   intent.getAction() == null ||
	   view == null)
	    return;

	String message = "";
	String sipHashId = "";

	switch(intent.getAction())
	{
	case "org.purple.smoke.chat_message":
	    if(((Activity) context).isFinishing())
		return;

	    message = intent.getStringExtra("org.purple.smoke.message");

	    if(message == null)
		return;

	    String name = intent.getStringExtra("org.purple.smoke.name");

	    if(name == null)
		return;
	    else
		name = name.trim();

	    sipHashId = intent.getStringExtra("org.purple.smoke.sipHashId");

	    if(sipHashId == null)
		return;
	    else
		sipHashId = sipHashId.toUpperCase();

	    if(name.isEmpty())
		name = "unknown";

	    boolean purple = intent.getBooleanExtra
		("org.purple.smoke.purple", false);
	    long sequence = intent.getLongExtra
		("org.purple.smoke.sequence", 1L);
	    long timestamp = intent.getLongExtra
		("org.purple.smoke.timestamp", 0L);

	    State.getInstance().logChatMessage
		(message, name, sipHashId, purple, sequence, timestamp);
	    message = message.trim();

	    if(name.length() > 15)
	    {
		name = name.substring(0, 15);

		if(!name.endsWith("..."))
		{
		    if(name.endsWith(".."))
			name += ".";
		    else if(name.endsWith("."))
			name += "..";
		    else
			name += "...";
		}
	    }

	    if(message.length() > 15)
	    {
		message = message.substring(0, 15);

		if(!message.endsWith("..."))
		{
		    if(message.endsWith(".."))
			message += ".";
		    else if(message.endsWith("."))
			message += "..";
		    else
			message += "...";
		}
	    }

	    if(message.isEmpty())
		message = "A message from " + name + " has arrived.";
	    else
		message = "A message (" + message + ") from " + name +
		    " has arrived.";

	    break;
	case "org.purple.smoke.siphash_share_confirmation":
	    sipHashId = intent.getStringExtra("org.purple.smoke.sipHashId");

	    if(sipHashId == null)
		return;
	    else
		sipHashId = sipHashId.toUpperCase();

	    if(Cryptography.SIPHASH_IDENTITY_LENGTH == sipHashId.length())
		message = "A SmokeStack has received the Smoke Identity " +
		    sipHashId + ".";
	    else
		message = "A SmokeStack has received the Smoke Identity.";

	    break;
	case "org.purple.smoke.time":
	    String string = intent.getStringExtra("org.purple.smoke.extra1");

	    if(string == null)
		return;
	    else
		message = string;

	    break;
	default:
	    break;
	}

	if(message.isEmpty())
	    return;

	TextView textView1 = new TextView(context);
	final WeakReference<PopupWindow> popupWindow =
	    new WeakReference<> (new PopupWindow(context));

	textView1.setBackgroundColor(Color.rgb(255, 236, 179));
	textView1.setText(message);

	float density = context.getResources().getDisplayMetrics().density;

	textView1.setPaddingRelative
	    ((int) (10 * density),
	     (int) (10 * density),
	     (int) (10 * density),
	     (int) (10 * density));
	textView1.setTextSize(16);
	popupWindow.get().setContentView(textView1);
	popupWindow.get().setOutsideTouchable(true);

	if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
	{
	    popupWindow.get().setHeight(300);
	    popupWindow.get().setWidth(450);
	}

	popupWindow.get().showAtLocation
	    (view, Gravity.START | Gravity.TOP, 75, 75);

	try
	{
	    Ringtone ringtone = null;
	    Uri notification = RingtoneManager.getDefaultUri
		(RingtoneManager.TYPE_NOTIFICATION);

	    ringtone = RingtoneManager.getRingtone(context, notification);
	    ringtone.play();
	}
	catch(Exception exception)
	{
	}

	Handler handler = new Handler();

	handler.postDelayed(new Runnable()
	{
	    @Override
	    public void run()
	    {
		if(popupWindow.get() != null)
		    popupWindow.get().dismiss();
	    }
	}, 10000L); // 10 seconds.
    }

    public static void showPromptDialog
	(Context context,
	 DialogInterface.OnCancelListener cancelListener,
	 String prompt)
    {
	if(context == null ||
	   !(context instanceof Activity) ||
	   ((Activity) context).isFinishing())
	    return;

	AlertDialog alertDialog = new AlertDialog.Builder(context).create();
	CheckBox checkBox1 = new CheckBox(context);

	State.getInstance().removeKey("dialog_accepted");
	alertDialog.setButton
	    (AlertDialog.BUTTON_NEGATIVE, "No",
	     new DialogInterface.OnClickListener()
	     {
		 public void onClick(DialogInterface dialog, int which)
		 {
		     State.getInstance().removeKey("dialog_accepted");
		     dialog.dismiss();
		 }
	     });
	alertDialog.setButton
	    (AlertDialog.BUTTON_POSITIVE, "Yes",
	     new DialogInterface.OnClickListener()
	     {
		 public void onClick(DialogInterface dialog, int which)
		 {
		     State.getInstance().setString("dialog_accepted", "true");
		     dialog.cancel();
		 }
	     });
	alertDialog.setMessage(prompt);
	alertDialog.setOnCancelListener(cancelListener); /*
							 ** We cannot wait
							 ** for a response.
							 */
	alertDialog.setTitle("Confirmation");
	alertDialog.setView(checkBox1);
	alertDialog.show();

	final Button button1 = alertDialog.getButton
	    (AlertDialog.BUTTON_POSITIVE);

	button1.setEnabled(false);
	checkBox1.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    button1.setEnabled(isChecked);
		}
	    });
	checkBox1.setText("Confirm");
    }

    public static void showTextInputDialog
	(Context context,
	 DialogInterface.OnCancelListener cancelListener,
	 String prompt,
	 String text,
	 String title)
    {
	if(context == null ||
	   !(context instanceof Activity) ||
	   ((Activity) context).isFinishing())
	    return;

	AlertDialog alertDialog = new AlertDialog.Builder(context).create();
	final EditText editText = new EditText(context);
	final boolean contextIsChat = context instanceof Chat;
	final boolean contextIsMemberChat = context instanceof MemberChat;
	final boolean contextIsSettings = context instanceof Settings;

	alertDialog.setButton
	    (AlertDialog.BUTTON_NEGATIVE, "Cancel",
	     new DialogInterface.OnClickListener()
	     {
		 public void onClick(DialogInterface dialog, int which)
		 {
		     if(contextIsChat)
			 State.getInstance().removeKey("chat_secret_input");
		     else if(contextIsMemberChat)
			 State.getInstance().removeKey
			     ("member_chat_secret_input");
		     else if(contextIsSettings)
			 State.getInstance().removeKey
			     ("settings_participant_name_input");

		     dialog.dismiss();
		 }
	     });
	alertDialog.setButton
	    (AlertDialog.BUTTON_POSITIVE, "Accept",
	     new DialogInterface.OnClickListener()
	     {
		 public void onClick(DialogInterface dialog, int which)
		 {
		     if(contextIsChat)
		     {
			 String string = editText.getText().toString();

			 if(string.length() <= Cryptography.HASH_KEY_LENGTH)
			     string = Base64.encodeToString
				 (Cryptography.
				  sha512(string.
					 getBytes(StandardCharsets.UTF_8)),
				  Base64.NO_WRAP);

			 State.getInstance().setString
			     ("chat_secret_input", string);
		     }
		     else if(contextIsMemberChat)
		     {
			 String string = editText.getText().toString();

			 if(string.length() <= Cryptography.HASH_KEY_LENGTH)
			     string = Base64.encodeToString
				 (Cryptography.
				  sha512(string.
					 getBytes(StandardCharsets.UTF_8)),
				  Base64.NO_WRAP);

			 State.getInstance().setString
			     ("member_chat_secret_input", string);
		     }
		     else if(contextIsSettings)
			 State.getInstance().setString
			     ("settings_participant_name_input",
			      editText.getText().toString());

		     dialog.cancel();
		 }
	     });
	alertDialog.setMessage(prompt);
	alertDialog.setOnCancelListener(cancelListener); /*
							 ** We cannot wait
							 ** for a response.
							 */
	alertDialog.setTitle(title);
	editText.setInputType(InputType.TYPE_CLASS_TEXT);
	editText.setText(text);
	alertDialog.setView(editText);
	alertDialog.show();
    }
}
