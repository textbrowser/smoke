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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Chat extends AppCompatActivity
{
    private abstract static class ContextMenuEnumerator
    {
	public final static int CUSTOM_SESSION = 0;
	public final static int NEW_WINDOW = 1;
	public final static int OPTIONAL_SIGNATURES = 2;
	public final static int PURGE_SESSION = 3;
	public final static int REFRESH_PARTICIPANTS_TABLE = 4;
	public final static int RETRIEVE_MESSAGES = 5;
	public final static int SHOW_DETAILS = 6;
	public final static int SHOW_ICONS = 7;
    }

    private final SimpleDateFormat m_simpleDateFormat = new
	SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private Database m_databaseHelper = null;

    private class ChatBroadcastReceiver extends BroadcastReceiver
    {
	public ChatBroadcastReceiver()
	{
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
	    if(intent == null || intent.getAction() == null)
		return;

	    switch(intent.getAction())
	    {
	    case "org.purple.smoke.busy_call":
		busyCall
		    (intent.getStringExtra("org.purple.smoke.name"),
		     intent.getStringExtra("org.purple.smoke.sipHashId"));
		break;
	    case "org.purple.smoke.chat_message":
		appendMessage
		    (intent.getStringExtra("org.purple.smoke.message"),
		     intent.getStringExtra("org.purple.smoke.name"),
		     intent.getStringExtra("org.purple.smoke.sipHashId"),
		     intent.getBooleanExtra("org.purple.smoke.purple", false),
		     false,
		     intent.getLongExtra("org.purple.smoke.sequence", 1),
		     intent.getLongExtra("org.purple.smoke.timestamp", 0));
		break;
	    case "org.purple.smoke.half_and_half_call":
		halfAndHalfCall
		    (intent.getStringExtra("org.purple.smoke.name"),
		     intent.getStringExtra("org.purple.smoke.sipHashId"),
		     intent.getBooleanExtra("org.purple.smoke.initial", false),
		     intent.getBooleanExtra("org.purple.smoke.refresh", true),
		     intent.getCharExtra("org.purple.smoke.keyType", 'R'));
		break;
	    case "org.purple.smoke.neighbor_aborted":
	    case "org.purple.smoke.neighbor_connected":
	    case "org.purple.smoke.neighbor_disconnected":
	    case "org.purple.smoke.network_connected":
	    case "org.purple.smoke.network_disconnected":
		networkStatusChanged();
		break;
	    case "org.purple.smoke.populate_participants":
	    case "org.purple.smoke.state_participants_populated":
		invalidateOptionsMenu();
		populateParticipants();
		break;
	    case "org.purple.smoke.time":
		Miscellaneous.showNotification
		    (Chat.this, intent, findViewById(R.id.main_layout));
		break;
	    default:
		break;
	    }
	}
    }

    private ChatBroadcastReceiver m_receiver = null;
    private Ringtone m_ringtone = null;
    private ScheduledExecutorService m_scheduler = null;
    private boolean m_receiverRegistered = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static SipHash s_siphash = new SipHash
	(new byte[] {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
		     (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
		     (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
		     (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f});
    private final static int CHECKBOX_TEXT_SIZE = 13;
    private final static long STATUS_INTERVAL = 30000L; // 30 seconds.
    private final static long AWAIT_TERMINATION = 5L; // 5 seconds.
    public final static int CHAT_MESSAGE_PREFERRED_SIZE = 8 * 1024;
    public final static int CUSTOM_SESSION_ITERATION_COUNT = 4096;
    public final static int SWITCH_ICON_PADDING = 5;
    public final static long CHAT_WINDOW = 60000L; // 1 Minute
    public final static long CONNECTION_STATUS_INTERVAL = 3500L; // 3.5 seconds.
    public final static long STATUS_WINDOW = 30000L; // 30 seconds.

    private String nameFromCheckBoxText(String text)
    {
	/*
	** Name
	** SipHash ID
	** Text
	*/

	if(text == null || text.isEmpty())
	    return "unknown";

	try
	{
	    int indexOf = text.indexOf('\n', 1);

	    if(indexOf > 0)
		return text.substring(0, indexOf);
	    else
		return text;
	}
	catch(Exception exception)
	{
	    return "unknown";
	}
    }

    private boolean hasPublicKeys(String sipHashId)
    {
	return m_databaseHelper.hasPublicKeys(s_cryptography, sipHashId);
    }

    private void appendMessage(String message,
			       String name,
			       String sipHashId,
			       boolean purple,
			       boolean viaChatLog,
			       long sequence,
			       long timestamp)
    {
	if(message == null || name == null || sipHashId == null)
	    return;
	else if(message.trim().length() == 0 ||
		name.trim().length() == 0 ||
		sipHashId.trim().length() == 0)
	    return;

	TextView textView1 = (TextView) findViewById(R.id.chat_messages);

	if(purple)
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append("[");

	    if(timestamp == 0)
		stringBuilder.append(m_simpleDateFormat.format(new Date()));
	    else
		stringBuilder.append
		    (m_simpleDateFormat.format(new Date(timestamp)));

	    stringBuilder.append("] ");
	    stringBuilder.append(name.trim());
	    stringBuilder.append(":");

	    if(sequence != -1)
	    {
		stringBuilder.append(sequence);
		stringBuilder.append(": ");
	    }
	    else
		stringBuilder.append(" ");

	    stringBuilder.append(message.trim());
	    stringBuilder.append("\n\n");

	    SpannableStringBuilder spannable = new SpannableStringBuilder
		(stringBuilder.toString());

	    spannable.setSpan
		(new ForegroundColorSpan(Color.rgb(74, 20, 140)),
		 0, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView1.append(spannable);
	}
	else
	{
	    textView1.append("[");

	    if(timestamp == 0)
		textView1.append(m_simpleDateFormat.format(new Date()));
	    else
		textView1.append
		    (m_simpleDateFormat.format(new Date(timestamp)));

	    textView1.append("] ");

	    SpannableStringBuilder spannable = new SpannableStringBuilder
		(name.trim());

	    spannable.setSpan
		(new StyleSpan(android.graphics.Typeface.BOLD),
		 0, name.trim().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView1.append(spannable);
	    textView1.append(":");

	    if(sequence != -1)
	    {
		textView1.append(String.valueOf(sequence));
		textView1.append(": ");
	    }
	    else
		textView1.append(" ");

	    textView1.append(message.trim());
	    textView1.append("\n\n");

	    if(m_databaseHelper.readSetting(null, "show_chat_icons").
	       equals("true"))
	    {
		Switch switch1 = findViewById
		    (R.id.participants).findViewWithTag(sipHashId);

		if(switch1 != null)
		{
		    if(!Kernel.getInstance().isConnected() ||
		       Math.abs(System.currentTimeMillis() - timestamp) >
		       STATUS_WINDOW)
			switch1.setCompoundDrawablesWithIntrinsicBounds
			    (R.drawable.chat_status_offline, 0, 0, 0);
		    else
			switch1.setCompoundDrawablesWithIntrinsicBounds
			    (R.drawable.chat_status_online, 0, 0, 0);

		    switch1.setCompoundDrawablePadding(SWITCH_ICON_PADDING);
		}
	    }
	}

	scrollMessagesView();

	if(!viaChatLog)
	    try
	    {
		if(m_ringtone != null)
		    m_ringtone.stop();

		Uri notification = RingtoneManager.getDefaultUri
		    (RingtoneManager.TYPE_NOTIFICATION);

		m_ringtone = RingtoneManager.getRingtone
		    (Chat.this, notification);
		m_ringtone.play();
	    }
	    catch(Exception exception)
	    {
	    }

	final TextView textView2 = (TextView) findViewById(R.id.chat_message);

	textView2.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		textView2.requestFocus();
	    }
	});
    }

    private void busyCall(String name, String sipHashId)
    {
	if(name == null || sipHashId == null)
	    return;
	else if(name.trim().length() == 0 || sipHashId.trim().length() == 0)
	    return;

	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById(R.id.chat_messages);

	stringBuilder.append("[");
	stringBuilder.append(m_simpleDateFormat.format(new Date()));
	stringBuilder.append("] ");
	stringBuilder.append
	    ("Received a simultaneous half-and-half organic call from ");
	stringBuilder.append(name.trim());
	stringBuilder.append(" (");
	stringBuilder.append(Miscellaneous.prepareSipHashId(sipHashId));
	stringBuilder.append("). Aborting.");
	stringBuilder.append("\n\n");
	textView1.append(stringBuilder);
	scrollMessagesView();

	final TextView textView2 = (TextView) findViewById(R.id.chat_message);

	textView2.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		textView2.requestFocus();
	    }
	});
    }

    private void halfAndHalfCall(String name,
				 String sipHashId,
				 boolean initial,
				 boolean refresh,
				 char keyType)
    {
	if(name == null || sipHashId == null)
	    return;
	else if(name.trim().length() == 0 || sipHashId.trim().length() == 0)
	    return;

	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById(R.id.chat_messages);

	stringBuilder.append("[");
	stringBuilder.append(m_simpleDateFormat.format(new Date()));
	stringBuilder.append("] ");

	if(initial)
	    stringBuilder.append("Received a half-and-half call from ");
	else
	    stringBuilder.append
		("Received a half-and-half call-response from ");

	stringBuilder.append(name.trim());
	stringBuilder.append(" (");
	stringBuilder.append(Miscellaneous.prepareSipHashId(sipHashId));
	stringBuilder.append(")");

	if(initial)
	{
	    if(keyType == 'M')
		stringBuilder.append(" via McEliece. ");
	    else
		stringBuilder.append(" via RSA. ");

	    stringBuilder.append("Dispatching a response. Please be patient.");
	}
	else
	    stringBuilder.append(".");

	stringBuilder.append("\n\n");
	textView1.append(stringBuilder);
	scrollMessagesView();

	if(refresh)
	    refreshCheckBox(sipHashId);

	final TextView textView2 = (TextView) findViewById(R.id.chat_message);

	textView2.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		textView2.requestFocus();
	    }
	});
    }

    private void networkStatusChanged()
    {
	Button button1 = (Button) findViewById(R.id.call);
	Button button2 = (Button) findViewById(R.id.send_chat_message);
	int chatCheckedParticipants = State.getInstance().
	    chatCheckedParticipants();

	button1.setEnabled
	    (Kernel.getInstance().isConnected() && chatCheckedParticipants > 0);

	if(Kernel.getInstance().availableNeighbors() > 0 &&
	   chatCheckedParticipants > 0)
	{
	    button2.setBackgroundResource(R.drawable.send);
	    button2.setEnabled(true);
	}
	else
	{
	    button2.setBackgroundResource(R.drawable.warning);
	    button2.setEnabled(false);
	}

	try
	{
	    getSupportActionBar().setSubtitle(Smoke.networkStatusString());
	}
	catch(Exception exception)
	{
	}
    }

    private void populateChat()
    {
	ArrayList<MessageElement> arrayList = State.getInstance().chatLog();

	if(arrayList == null || arrayList.isEmpty())
	    return;

	for(MessageElement messageElement : arrayList)
	{
	    if(messageElement == null)
		continue;

	    appendMessage(messageElement.m_message,
			  messageElement.m_name,
			  messageElement.m_id,
			  messageElement.m_purple,
			  true,
			  messageElement.m_sequence,
			  messageElement.m_timestamp);
	}

	State.getInstance().clearChatLog();
	arrayList.clear();
    }

    private void populateParticipants()
    {
	ArrayList<ParticipantElement> arrayList = State.getInstance().
	    participants();
	TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.participants);

	if(arrayList == null || arrayList.isEmpty())
	{
	    tableLayout.removeAllViews();
	    return;
	}

	tableLayout.removeAllViews();

	StringBuilder stringBuilder = new StringBuilder();
	boolean showDetails = m_databaseHelper.readSetting
	    (null, "show_chat_details").equals("true");
	boolean showIcons = m_databaseHelper.readSetting
	    (null, "show_chat_icons").equals("true");
	boolean state = Kernel.getInstance().isConnected();
	int i = 0;

	for(ParticipantElement participantElement : arrayList)
	{
	    if(participantElement == null)
		continue;

	    Switch switch1 = new Switch(Chat.this);
	    final int oid = participantElement.m_oid;

	    if(showIcons)
	    {
		if(participantElement.m_keyStream == null ||
		   participantElement.m_keyStream.length !=
		   Cryptography.CIPHER_HASH_KEYS_LENGTH)
		    switch1.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_faulty_session, 0, 0, 0);
		else if(Math.abs(System.currentTimeMillis() -
				 participantElement.m_lastStatusTimestamp) >
			STATUS_WINDOW ||
			!state)
		    switch1.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_status_offline, 0, 0, 0);
		else
		    switch1.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_status_online, 0, 0, 0);

		switch1.setCompoundDrawablePadding(SWITCH_ICON_PADDING);
	    }

	    registerForContextMenu(switch1);
	    switch1.setChecked
		(State.getInstance().chatCheckBoxIsSelected(oid));
	    switch1.setId(participantElement.m_oid);
	    switch1.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
	    switch1.setLayoutParams
		(new TableRow.LayoutParams(0,
					   LayoutParams.WRAP_CONTENT,
					   1));
	    switch1.setOnCheckedChangeListener
		(new CompoundButton.OnCheckedChangeListener()
		{
		    @Override
		    public void onCheckedChanged
			(CompoundButton buttonView, boolean isChecked)
		    {
			State.getInstance().setChatCheckBoxSelected
			    (buttonView.getId(), isChecked);

			Button button1 = (Button) findViewById(R.id.call);
			Button button2 = (Button) findViewById
			    (R.id.send_chat_message);

			if(Kernel.getInstance().availableNeighbors() > 0 &&
			   State.getInstance().chatCheckedParticipants() > 0)
			{
			    button1.setEnabled
				(Kernel.getInstance().isConnected());
			    button2.setBackgroundResource(R.drawable.send);
			    button2.setEnabled(true);
			}
			else
			{
			    button1.setEnabled(false);
			    button2.setBackgroundResource(R.drawable.warning);
			    button2.setEnabled(false);
			}
		    }
		});
	    stringBuilder.delete(0, stringBuilder.length());
	    stringBuilder.append(participantElement.m_name.trim());

	    if(showDetails)
	    {
		stringBuilder.append("\n");
		stringBuilder.append
		    (Miscellaneous.
		     prepareSipHashId(participantElement.m_sipHashId));
		stringBuilder.append("\n");

		if(participantElement.m_keyStream == null ||
		   participantElement.m_keyStream.length == 0)
		    stringBuilder.append("Session Closed");
		else if(participantElement.m_keyStream.length <
			Cryptography.CIPHER_HASH_KEYS_LENGTH)
		    stringBuilder.append("Session Incomplete");
		else if(participantElement.m_keyStream.length ==
			Cryptography.CIPHER_HASH_KEYS_LENGTH)
		    stringBuilder.append("Session Ready");
		else
		    stringBuilder.append("Session Faulty");

		if(participantElement.m_keyStream != null &&
		   participantElement.m_keyStream.length ==
		   Cryptography.CIPHER_HASH_KEYS_LENGTH)
		{
		    stringBuilder.append("\n");

		    long[] value = s_siphash.
			hmac(participantElement.m_keyStream,
			     Cryptography.SIPHASH_OUTPUT_LENGTH);

		    stringBuilder.append
			(Miscellaneous.
			 byteArrayAsHexStringDelimited
			 (Miscellaneous.
			  longArrayToByteArray(value), '-', 4).toUpperCase());
		}
	    }

	    switch1.setTag(participantElement.m_sipHashId);
	    switch1.setText(stringBuilder);
	    switch1.setTextSize(CHECKBOX_TEXT_SIZE);

	    TableRow row = new TableRow(Chat.this);

	    row.addView(switch1);
	    tableLayout.addView(row, i);
	    i += 1;
	}

	// Do not clear arrayList!
    }

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.call);

	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Chat.this.isFinishing())
		    return;

		StringBuilder stringBuilder = new StringBuilder();
		TextView textView1 = (TextView) findViewById
		    (R.id.chat_messages);
		TableLayout tableLayout = (TableLayout) findViewById
		    (R.id.participants);
		int count = tableLayout.getChildCount();

		for(int i = 0; i < count; i++)
		{
		    TableRow row = (TableRow) tableLayout.getChildAt(i);

		    if(row == null)
			continue;

		    Switch switch1 = (Switch) row.getChildAt(0);

		    if(switch1 == null)
			continue;

		    if(switch1.getTag() != null && switch1.isChecked())
		    {
			boolean ok = Kernel.getInstance().call
			    (switch1.getId(),
			     ParticipantCall.Algorithms.RSA,
			     switch1.getTag().toString());

			stringBuilder.delete(0, stringBuilder.length());
			stringBuilder.append("[");
			stringBuilder.append
			    (m_simpleDateFormat.format(new Date()));
			stringBuilder.append("] ");

			if(ok)
			    stringBuilder.append("Initiating a session with ");
			else
			    stringBuilder.append
				("Smoke is currently attempting to " +
				 "establish a session with ");

			stringBuilder.append
			    (nameFromCheckBoxText(switch1.getText().
						  toString()));
			stringBuilder.append(" (");
			stringBuilder.append
			    (Miscellaneous.
			     prepareSipHashId(switch1.getTag().toString()));
			stringBuilder.append("). ");

			if(!ok)
			{
			    stringBuilder.append("Please try again in ");
			    stringBuilder.append
				(Kernel.getInstance().
				 callTimeRemaining(switch1.getTag().
						   toString()));
			    stringBuilder.append(" second(s).\n\n");
			}
			else
			    stringBuilder.append("Please be patient.\n\n");

			textView1.append(stringBuilder);
		    }
		}

		if(tableLayout.getChildCount() > 0)
		{
		    scrollMessagesView();

		    final TextView textView2 = (TextView)
			findViewById(R.id.chat_message);

		    textView2.post(new Runnable()
		    {
			@Override
			public void run()
			{
			    textView2.requestFocus();
			}
		    });
		}
	    }
        });
    }

    private void prepareSchedulers()
    {
	if(m_scheduler == null)
	{
	    m_scheduler = Executors.newSingleThreadScheduledExecutor();
	    m_scheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			if(!m_databaseHelper.
			   readSetting(null, "show_chat_icons").equals("true"))
			    return;

			ArrayList<String> arrayList =
			    m_databaseHelper.readSipHashIdStrings
			    (s_cryptography);

			if(arrayList == null || arrayList.isEmpty())
			    return;

			for(final String string : arrayList)
			    if(Thread.currentThread().isInterrupted())
				break;
			    else
				Chat.this.runOnUiThread(new Runnable()
				{
				    @Override
				    public void run()
				    {
					refreshCheckBox(string);
				    }
				});

			arrayList.clear();
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 0, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void refreshCheckBox(String sipHashId)
    {
	Switch switch1 = findViewById(R.id.participants).findViewWithTag(sipHashId);

	if(switch1 == null)
	    return;

	ArrayList<ParticipantElement> arrayList =
	    m_databaseHelper.readParticipants(s_cryptography, sipHashId);

	if(arrayList == null || arrayList.isEmpty())
	    return;

	ParticipantElement participantElement = arrayList.get(0);

	if(participantElement == null)
	    return;

	if(m_databaseHelper.readSetting(null, "show_chat_details").
	   equals("true"))
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append(participantElement.m_name.trim());
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Miscellaneous.
		 prepareSipHashId(participantElement.m_sipHashId));
	    stringBuilder.append("\n");

	    if(participantElement.m_keyStream == null ||
	       participantElement.m_keyStream.length == 0)
		stringBuilder.append("Session Closed");
	    else if(participantElement.m_keyStream.length <
		    Cryptography.CIPHER_HASH_KEYS_LENGTH)
		stringBuilder.append("Session Incomplete");
	    else if(participantElement.m_keyStream.length ==
		    Cryptography.CIPHER_HASH_KEYS_LENGTH)
		stringBuilder.append("Session Ready");
	    else
		stringBuilder.append("Session Faulty");

	    if(participantElement.m_keyStream != null &&
	       participantElement.m_keyStream.length ==
	       Cryptography.CIPHER_HASH_KEYS_LENGTH)
	    {
		stringBuilder.append("\n");

		long[] value = s_siphash.hmac
		    (participantElement.m_keyStream,
		     Cryptography.SIPHASH_OUTPUT_LENGTH);

		stringBuilder.append
		    (Miscellaneous.
		     byteArrayAsHexStringDelimited(Miscellaneous.
						   longArrayToByteArray(value),
						   '-', 4).toUpperCase());
	    }

	    switch1.setText(stringBuilder);
	}

	if(m_databaseHelper.readSetting(null, "show_chat_icons").equals("true"))
	{
	    if(participantElement.m_keyStream == null ||
	       participantElement.m_keyStream.length !=
	       Cryptography.CIPHER_HASH_KEYS_LENGTH)
		switch1.setCompoundDrawablesWithIntrinsicBounds
		    (R.drawable.chat_faulty_session, 0, 0, 0);
	    else if(!Kernel.getInstance().isConnected() ||
		    Math.abs(System.currentTimeMillis() -
			     participantElement.m_lastStatusTimestamp) >
		    STATUS_WINDOW)
		switch1.setCompoundDrawablesWithIntrinsicBounds
		    (R.drawable.chat_status_offline, 0, 0, 0);
	    else
		switch1.setCompoundDrawablesWithIntrinsicBounds
		    (R.drawable.chat_status_online, 0, 0, 0);

	    switch1.setCompoundDrawablePadding(SWITCH_ICON_PADDING);
	}

	arrayList.clear();
    }

    private void releaseResources()
    {
	if(m_scheduler != null)
	{
	    try
	    {
		m_scheduler.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }

	    try
	    {
		if(!m_scheduler.
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
		    m_scheduler.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_scheduler = null;
	    }
	}
    }

    private void requestMessages()
    {
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById(R.id.chat_messages);

	stringBuilder.append("[");
	stringBuilder.append(m_simpleDateFormat.format(new Date()));
	stringBuilder.append("] ");
	stringBuilder.append("A request for retrieving offline messages ");
	stringBuilder.append("has been submitted. Offline messages will be ");
	stringBuilder.append("marked by the color ");
	textView1.append(stringBuilder);

	SpannableStringBuilder spannable = new SpannableStringBuilder("purple");

	spannable.setSpan
	    (new ForegroundColorSpan(Color.rgb(74, 20, 140)),
	     0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	textView1.append(spannable);
	textView1.append(". Please note that recently-received messages ");
	textView1.append("may be discarded because of congestion control.\n\n");
	scrollMessagesView();

	final TextView textView2 = (TextView) findViewById(R.id.chat_message);

	textView2.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		textView2.requestFocus();
	    }
	});
    }

    private void saveState()
    {
	TextView textView1 = (TextView) findViewById(R.id.chat_message);

	State.getInstance().writeCharSequence
	    ("chat.message", textView1.getText());
	textView1 = (TextView) findViewById(R.id.chat_messages);
	State.getInstance().writeCharSequence
	    ("chat.messages", textView1.getText());
    }

    private void scrollMessagesView()
    {
	final ScrollView scrollView = (ScrollView)
	    findViewById(R.id.chat_scrollview);

	scrollView.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		scrollView.fullScroll(ScrollView.FOCUS_DOWN);
	    }
	});
    }

    private void showMemberChatActivity()
    {
	saveState();

	Intent intent = new Intent(Chat.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showSmokescreenActivity()
    {
	Intent intent = new Intent(Chat.this, Smokescreen.class);

	startActivity(intent);
	finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
	m_receiver = new ChatBroadcastReceiver();
        setContentView(R.layout.activity_chat);
	setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

	try
	{
	    getSupportActionBar().setSubtitle(Smoke.networkStatusString());
	    getSupportActionBar().setTitle("Smoke | Chat");
	}
	catch(Exception exception)
	{
	}

        Button button1 = (Button) findViewById(R.id.clear_chat_messages);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Chat.this.isFinishing())
		    return;

		State.getInstance().clearChatLog();

		TextView textView1 = (TextView) findViewById
		    (R.id.chat_messages);

		textView1.setText("");
	    }
	});

        button1 = (Button) findViewById(R.id.send_chat_message);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Chat.this.isFinishing())
		    return;

		final TextView textView1 = (TextView) findViewById
		    (R.id.chat_message);

		if(textView1.getText().toString().trim().isEmpty())
		    return;

		String str = textView1.getText().toString().trim();
		StringBuilder stringBuilder = new StringBuilder();
		TableLayout tableLayout = (TableLayout) findViewById
		    (R.id.participants);
		TextView textView2 = (TextView) findViewById
		    (R.id.chat_messages);

		textView2.append("[");
		textView2.append(m_simpleDateFormat.format(new Date()));
		textView2.append("] ");

		{
		    SpannableStringBuilder spannable =
			new SpannableStringBuilder("me");

		    spannable.setSpan
			(new StyleSpan(android.graphics.Typeface.BOLD),
			 0,
			 spannable.length(),
			 Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		    textView2.append(spannable);
		}

		stringBuilder.append(": ");
		stringBuilder.append(str);
		stringBuilder.append("\n\n");
		textView2.append(stringBuilder);
		textView1.setText("");

		int size = CHAT_MESSAGE_PREFERRED_SIZE *
		    (int) Math.ceil((1.0 * str.length()) /
				    (1.0 * CHAT_MESSAGE_PREFERRED_SIZE));

		if(size > str.length())
		{
		    char[] a = new char[size - str.length()];

		    Arrays.fill(a, ' ');
		    str += new String(a);
		}
		else if(str.length() > 0)
		{
		    char[] a = new char[1024 + str.length() % 2];

		    Arrays.fill(a, ' ');
		    str += new String(a);
		}

		int count = tableLayout.getChildCount();

		for(int i = 0; i < count; i++)
		{
		    TableRow row = (TableRow) tableLayout.getChildAt(i);

		    if(row == null)
			continue;

		    Switch switch1 = (Switch) row.getChildAt(0);

		    if(switch1 == null ||
		       switch1.getTag() == null ||
		       !switch1.isChecked())
			continue;

		    String sipHashId = switch1.getTag().toString();
		    byte[] keyStream = m_databaseHelper.participantKeyStream
			(s_cryptography, sipHashId);

		    if(keyStream == null ||
		       keyStream.length != Cryptography.CIPHER_HASH_KEYS_LENGTH)
			continue;

		    Kernel.getInstance().enqueueChatMessage
			(str, sipHashId, null, keyStream);
		}

		scrollMessagesView();
		textView1.post(new Runnable()
		{
		    @Override
		    public void run()
		    {
			textView1.requestFocus();
		    }
		});
	    }
	});

	TextView textView1 = (TextView) findViewById(R.id.chat_message);

	textView1.requestFocus();

	/*
	** Restore some data.
	*/

	try
	{
	    textView1 = (TextView) findViewById(R.id.chat_message);
	    textView1.setText
		(State.getInstance().getCharSequence("chat.message"));
	    textView1 = (TextView) findViewById(R.id.chat_messages);
	    textView1.setText
		(State.getInstance().getCharSequence("chat.messages"));

	}
	catch(Exception exception)
	{
	}

	populateChat();

	if(State.getInstance().isAuthenticated())
	    populateParticipants();

	prepareListeners();
    }

    @Override
    protected void onDestroy()
    {
	if(State.getInstance().exit())
	    android.os.Process.killProcess(android.os.Process.myPid());
	else
	    super.onDestroy();
    }

    @Override
    protected void onPause()
    {
	super.onPause();

	if(m_receiverRegistered)
	{
	    LocalBroadcastManager.getInstance(Chat.this).
		unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}

	releaseResources();
	saveState();
    }

    @Override
    protected void onResume()
    {
	super.onResume();

	if(!m_receiverRegistered)
	{
	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction("org.purple.smoke.busy_call");
	    intentFilter.addAction("org.purple.smoke.chat_message");
	    intentFilter.addAction("org.purple.smoke.half_and_half_call");
	    intentFilter.addAction("org.purple.smoke.neighbor_aborted");
	    intentFilter.addAction("org.purple.smoke.neighbor_connected");
	    intentFilter.addAction("org.purple.smoke.neighbor_disconnected");
	    intentFilter.addAction("org.purple.smoke.network_connected");
	    intentFilter.addAction("org.purple.smoke.network_disconnected");
	    intentFilter.addAction("org.purple.smoke.populate_participants");
	    intentFilter.addAction
		("org.purple.smoke.state_participants_populated");
	    intentFilter.addAction("org.purple.smoke.time");
	    LocalBroadcastManager.getInstance(Chat.this).
		registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}

	networkStatusChanged();
	populateChat();
	populateParticipants();
	prepareSchedulers();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState)
    {
	/*
	** Do not issue a super.onSaveInstanceState().
	*/
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem)
    {
	if(menuItem == null)
	    return false;

	final String sipHashId = Miscellaneous.prepareSipHashId
	    (menuItem.getTitle().toString().replace("Custom Session ", "").
	     replace("New Window ", "").
	     replace("Optional Signatures ", "").
	     replace("Purge Session ", "").replace("(", "").replace(")", ""));
	final int groupId = menuItem.getGroupId();
	final int itemId = menuItem.getItemId();

	/*
	** Prepare a listener.
	*/

	DialogInterface.OnCancelListener listener =
	    new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    if(itemId > -1)
			switch(groupId)
		        {
			case ContextMenuEnumerator.CUSTOM_SESSION:
			    try
			    {
				String string = State.getInstance().
				    getString("chat_secret_input").trim();

				if(!string.isEmpty())
				{
				    byte[] bytes = Cryptography.pbkdf2
					(Cryptography.
					 sha512(string.
						getBytes(StandardCharsets.
							 UTF_8)),
					 string.toCharArray(),
					 CUSTOM_SESSION_ITERATION_COUNT,
					 160); // SHA-1

				    if(bytes != null)
					bytes = Cryptography.pbkdf2
					    (Cryptography.
					     sha512(string.
						    getBytes(StandardCharsets.
							     UTF_8)),
					     Base64.
					     encodeToString(bytes,
							    Base64.NO_WRAP).
					     toCharArray(),
					     1,
					     Cryptography.
					     CIPHER_HASH_KEYS_LENGTH * 8);

				    if(m_databaseHelper.
				       setParticipantKeyStream(s_cryptography,
							       bytes,
							       itemId))
					refreshCheckBox(sipHashId);
				}
			    }
			    catch(Exception exception)
			    {
			    }

			    State.getInstance().removeKey("chat_secret_input");
			    break;
			case ContextMenuEnumerator.PURGE_SESSION:
			    if(State.getInstance().getString("dialog_accepted").
			       equals("true"))
				if(m_databaseHelper.
				   setParticipantKeyStream(s_cryptography,
							   null,
							   itemId))
				    refreshCheckBox(sipHashId);

			    break;
			default:
			    break;
			}
		}
	    };

	if(itemId > -1)
	    switch(groupId)
	    {
	    case ContextMenuEnumerator.CUSTOM_SESSION:
		Miscellaneous.showTextInputDialog
		    (Chat.this,
		     listener,
		     "Please provide a secret for " +
		     menuItem.getTitle().toString().
		     replace("Custom Session (", "").
		     replace(")", "") + ".",
		     "",
		     "Secret",
		     true);
		break;
	    case ContextMenuEnumerator.NEW_WINDOW:
		State.getInstance().setString
		    ("member_chat_oid", String.valueOf(itemId));
		State.getInstance().setString
		    ("member_chat_siphash_id", sipHashId);
		m_databaseHelper.writeSetting
		    (null, "lastActivity", "MemberChat");
		m_databaseHelper.writeSetting
		    (s_cryptography, "member_chat_oid", String.valueOf(itemId));
		m_databaseHelper.writeSetting
		    (s_cryptography, "member_chat_siphash_id", sipHashId);
		showMemberChatActivity();
		break;
	    case ContextMenuEnumerator.OPTIONAL_SIGNATURES:
		menuItem.setChecked(!menuItem.isChecked());

		String[] strings = null;
		StringBuilder stringBuilder = new StringBuilder
		    (m_databaseHelper.
		     readParticipantOptions(s_cryptography, sipHashId));

		strings = stringBuilder.toString().split(";");

		if(strings == null || strings.length == 0)
		{
		    if(menuItem.isChecked())
			stringBuilder.append("optional_signatures = true");
		    else
			stringBuilder.append("optional_signatures = false");
		}
		else
		{
		    stringBuilder.delete(0, stringBuilder.length());

		    int i = 0;
		    int length = strings.length;

		    for(String string : strings)
		    {
			if(!(string.equals("optional_signatures = false") ||
			     string.equals("optional_signatures = true")))
			{
			    stringBuilder.append(string);

			    if(i != length - 1)
				stringBuilder.append(";");
			}

			i += 1;
		    }

		    if(stringBuilder.length() > 0)
			stringBuilder.append(";");

		    stringBuilder.append("optional_signatures = ");
		    stringBuilder.append
			(menuItem.isChecked() ? "true" : "false");
		}

		m_databaseHelper.writeParticipantOptions
		    (s_cryptography, stringBuilder.toString(), sipHashId);
		break;
	    case ContextMenuEnumerator.PURGE_SESSION:
		Miscellaneous.showPromptDialog
		    (Chat.this,
		     listener,
		     "Are you sure that you " +
		     "wish to purge the session key stream for " +
		     menuItem.getTitle().toString().
		     replace("Purge Session (", "").
		     replace(")", "") + "?");
		break;
	    default:
		break;
	    }
	else
	    switch(groupId)
	    {
	    case ContextMenuEnumerator.REFRESH_PARTICIPANTS_TABLE:
		populateParticipants();
		break;
	    case ContextMenuEnumerator.RETRIEVE_MESSAGES:
		Kernel.getInstance().retrieveChatMessages("");
		requestMessages();
		break;
	    case ContextMenuEnumerator.SHOW_DETAILS:
		menuItem.setChecked(!menuItem.isChecked());
		m_databaseHelper.writeSetting
		    (null,
		     "show_chat_details",
		     menuItem.isChecked() ? "true" : "false");
		populateParticipants();
		break;
	    case ContextMenuEnumerator.SHOW_ICONS:
		menuItem.setChecked(!menuItem.isChecked());
		m_databaseHelper.writeSetting
		    (null,
		     "show_chat_icons",
		     menuItem.isChecked() ? "true" : "false");
		populateParticipants();
		break;
	    default:
		break;
	    }

	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
				    View view,
				    ContextMenuInfo menuInfo)
    {
	if(menu == null || view == null)
	    return;

	super.onCreateContextMenu(menu, view, menuInfo);

	MenuItem menuItem = null;

	if(view.getTag() != null)
	{
	    boolean hasPublicKeys = hasPublicKeys
		(Miscellaneous.prepareSipHashId(view.getTag().toString()));

	    menu.add
		(ContextMenuEnumerator.CUSTOM_SESSION,
		 view.getId(),
		 0,
		 "Custom Session (" +
		 Miscellaneous.prepareSipHashId(view.getTag().toString()) +
		 ")").setEnabled(hasPublicKeys);
	    menu.add
		(ContextMenuEnumerator.NEW_WINDOW,
		 view.getId(),
		 1,
		 "New Window (" +
		 Miscellaneous.prepareSipHashId(view.getTag().toString()) +
		 ")").setEnabled(hasPublicKeys);
	    menuItem = menu.add
		(ContextMenuEnumerator.OPTIONAL_SIGNATURES,
		 view.getId(),
		 2,
		 "Optional Signatures (" +
		 Miscellaneous.prepareSipHashId(view.getTag().toString()) +
		 ")").setCheckable(true);
	    menuItem.setChecked
		(m_databaseHelper.
		 readParticipantOptions(s_cryptography, view.getTag().
					toString()).
		 contains("optional_signatures = true"));
	    menuItem = menu.add
		(ContextMenuEnumerator.PURGE_SESSION,
		 view.getId(),
		 3,
		 "Purge Session (" +
		 Miscellaneous.prepareSipHashId(view.getTag().toString()) +
		 ")");
	    menuItem.setEnabled
		(m_databaseHelper.
		 participantsWithSessionKeys(view.getId()) > 0);
	}

	menu.add(ContextMenuEnumerator.REFRESH_PARTICIPANTS_TABLE,
		 -1,
		 4,
		 "Refresh Participants Table");
	menuItem = menu.add(ContextMenuEnumerator.RETRIEVE_MESSAGES,
			    -1,
			    5,
			    "Retrieve Messages");
	menuItem.setEnabled
	    (Kernel.getInstance().isConnected() &&
	     !m_databaseHelper.readSetting(s_cryptography, "ozone_address").
	     isEmpty());
	menuItem = menu.add(ContextMenuEnumerator.SHOW_DETAILS,
			    -1,
			    6,
			    "Show Details").setCheckable(true);
	menuItem.setChecked
	    (m_databaseHelper.
	     readSetting(null, "show_chat_details").equals("true"));
	menuItem = menu.add(ContextMenuEnumerator.SHOW_ICONS,
			    -1,
			    7,
			    "Show Icons").setCheckable(true);
	menuItem.setChecked
	    (m_databaseHelper.
	     readSetting(null, "show_chat_icons").equals("true"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
	int groupId = menuItem.getGroupId();
	int itemId = menuItem.getItemId();

	if(groupId == Menu.NONE)
	{
	    switch(itemId)
	    {
	    case R.id.action_exit:
		Smoke.exit(true, Chat.this);
		return true;
	    case R.id.action_fire:
	    {
		saveState();
		m_databaseHelper.writeSetting(null, "lastActivity", "Fire");

		Intent intent = new Intent(Chat.this, Fire.class);

		startActivity(intent);
		finish();
		return true;
	    }
	    case R.id.action_settings:
	    {
		saveState();
		m_databaseHelper.writeSetting(null, "lastActivity", "Settings");

		Intent intent = new Intent(Chat.this, Settings.class);

		startActivity(intent);
		finish();
		return true;
	    }
	    case R.id.action_smokescreen:
		saveState();
		showSmokescreenActivity();
		finish();
		return true;
	    case R.id.action_steam:
	    {
		saveState();
		m_databaseHelper.writeSetting(null, "lastActivity", "Steam");

		Intent intent = new Intent(Chat.this, Steam.class);

		startActivity(intent);
		finish();
		return true;
	    }
	    default:
		break;
	    }
	}
	else
	{
	    String sipHashId = menuItem.getTitle().toString();

	    try
	    {
		int indexOf = sipHashId.indexOf("(");

		if(indexOf >= 0)
		    sipHashId = sipHashId.substring
			(indexOf + 1).replace(")", "");
	    }
	    catch(Exception exception)
	    {
	    }

	    sipHashId = Miscellaneous.prepareSipHashId(sipHashId);
	    State.getInstance().setString
		("member_chat_oid", String.valueOf(itemId));
	    State.getInstance().setString("member_chat_siphash_id", sipHashId);
	    m_databaseHelper.writeSetting(null, "lastActivity", "MemberChat");
	    m_databaseHelper.writeSetting
		(s_cryptography, "member_chat_oid", String.valueOf(itemId));
	    m_databaseHelper.writeSetting
		(s_cryptography, "member_chat_siphash_id", sipHashId);
	    showMemberChatActivity();
	}

        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
	boolean isAuthenticated = State.getInstance().isAuthenticated();

	if(!m_databaseHelper.accountPrepared())
	    /*
	    ** The database may have been modified or removed.
	    */

	    isAuthenticated = true;

	menu.findItem(R.id.action_authenticate).setEnabled(!isAuthenticated);
	Miscellaneous.addMembersToMenu(menu, 6, 150);
	return true;
    }
}
