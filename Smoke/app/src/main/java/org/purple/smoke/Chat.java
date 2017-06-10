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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Chat extends AppCompatActivity
{
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

	    if(intent.getAction().equals("org.purple.smoke.chat_message"))
		appendMessage
		    (intent.getStringExtra("org.purple.smoke.message"),
		     intent.getStringExtra("org.purple.smoke.name"),
		     intent.getStringExtra("org.purple.smoke.sipHashId"),
		     false,
		     intent.getLongExtra("org.purple.smoke.sequence", 1),
		     intent.getLongExtra("org.purple.smoke.timestamp", 0));
	    else if(intent.getAction().
		    equals("org.purple.smoke.populate_participants"))
	    {
		String sipHashId = intent.getStringExtra
		    ("org.purple.smoke.sipHashId");

		if(sipHashId == null || sipHashId.trim().isEmpty())
		    populateParticipants();
		else
		    refreshCheckBox(sipHashId);
	    }
	    else if(intent.getAction().
		    equals("org.purple.smoke.half_and_half_call"))
		halfAndHalfCall
		    (intent.getStringExtra("org.purple.smoke.name"),
		     intent.getStringExtra("org.purple.smoke.sipHashId"),
		     intent.getBooleanExtra("org.purple.smoke.initial", false),
		     intent.getBooleanExtra("org.purple.smoke.refresh", false));
	}
    }

    private ChatBroadcastReceiver m_receiver = null;
    private ScheduledExecutorService m_connectionStatusScheduler = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private boolean m_receiverRegistered = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static SipHash s_siphash = new SipHash
	(new byte[] {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
		     (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
		     (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
		     (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f});
    private final static int CHAT_MESSAGE_PREFERRED_SIZE = 8 * 1024;
    private final static int CHECKBOX_TEXT_SIZE = 13;
    private final static int CONNECTION_STATUS_INTERVAL = 1500; // 1.5 Seconds
    private final static int CUSTOM_SESSION_ITERATION_COUNT = 4096;
    private final static int STATUS_INTERVAL = 30000; // 30 Seconds
    public final static int CHAT_WINDOW = 60000; // 1 Minute
    public final static int STATUS_WINDOW = 30000; // 30 Seconds

    private String nameFromCheckBoxText(String text)
    {
	/*
	** Name
	** SipHash ID
	** Text
	*/

	if(text.isEmpty())
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

    private void appendMessage(String message,
			       String name,
			       String sipHashId,
			       boolean fromChatLog,
			       long sequence,
			       long timestamp)
    {
	if(message == null || name == null || sipHashId == null)
	    return;
	else if(message.trim().length() == 0 ||
		name.trim().length() == 0 ||
		sipHashId.trim().length() == 0)
	    return;

	SimpleDateFormat simpleDateFormat = new
	    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
	TextView textView1 = (TextView) findViewById(R.id.chat_messages);
	boolean purple = fromChatLog ? false :
	    Math.abs(System.currentTimeMillis() - timestamp) > CHAT_WINDOW;

	if(purple)
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append("[");

	    if(timestamp == 0)
		stringBuilder.append(simpleDateFormat.format(new Date()));
	    else
		stringBuilder.append
		    (simpleDateFormat.format(new Date(timestamp)));

	    stringBuilder.append("] ");
	    stringBuilder.append(name);
	    stringBuilder.append(":");
	    stringBuilder.append(sequence);
	    stringBuilder.append(": ");
	    stringBuilder.append(message);
	    stringBuilder.append("\n\n");

	    Spannable spannable = new SpannableStringBuilder
		(stringBuilder.toString());

	    spannable.setSpan
		(new ForegroundColorSpan(Color.rgb(30, 144, 255)),
		 0, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView1.append(spannable);
	}
	else
	{
	    textView1.append("[");

	    if(timestamp == 0)
		textView1.append(simpleDateFormat.format(new Date()));
	    else
		textView1.append
		    (simpleDateFormat.format(new Date(timestamp)));

	    textView1.append("] ");

	    Spannable spannable = new SpannableStringBuilder(name);

	    spannable.setSpan
		(new StyleSpan(android.graphics.Typeface.BOLD),
		 0, name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView1.append(spannable);
	    textView1.append(":");
	    textView1.append(String.valueOf(sequence));
	    textView1.append(": ");
	    textView1.append(message);
	    textView1.append("\n\n");
	}

	if(m_databaseHelper.readSetting(null, "show_chat_icons").equals("true"))
	{
	    CheckBox checkBox = (CheckBox)
		findViewById(R.id.participants).findViewWithTag(sipHashId);

	    if(checkBox != null)
	    {
		if(Math.abs(System.currentTimeMillis() - timestamp) >
		   STATUS_WINDOW)
		    checkBox.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_status_offline, 0, 0, 0);
		else
		    checkBox.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_status_online, 0, 0, 0);

		checkBox.setCompoundDrawablePadding(5);
	    }
	}

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

    private void appendMessage(String message,
			       int color)
    {
	SimpleDateFormat simpleDateFormat = new
	    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById(R.id.chat_messages);

	stringBuilder.append("[");
	stringBuilder.append(simpleDateFormat.format(new Date()));
	stringBuilder.append("] ");
	stringBuilder.append(message);
	stringBuilder.append("\n\n");

	Spannable spannable = new SpannableStringBuilder
	    (stringBuilder.toString());

	spannable.setSpan
	    (new ForegroundColorSpan(color),
	     0, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	textView1.append(spannable);
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
				 boolean refresh)
    {
	if(name == null || sipHashId == null)
	    return;
	else if(name.trim().length() == 0 || sipHashId.trim().length() == 0)
	    return;

	SimpleDateFormat simpleDateFormat = new
	    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById(R.id.chat_messages);

	stringBuilder.append("[");
	stringBuilder.append(simpleDateFormat.format(new Date()));
	stringBuilder.append("] ");

	if(initial)
	    stringBuilder.append("Received a half-and-half organic call from ");
	else
	    stringBuilder.append
		("Received a half-and-half organic response from ");

	stringBuilder.append(name);
	stringBuilder.append(" (");
	stringBuilder.append
	    (Miscellaneous.
	     delimitString(sipHashId.replace(":", ""), '-', 4).toUpperCase());
	stringBuilder.append(").");

	if(initial)
	    stringBuilder.append(" Dispatching a response. Please be patient.");

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

    private void populateChat()
    {
	ArrayList<ChatMessageElement> arrayList = State.getInstance().
	    chatLog();

	if(arrayList == null || arrayList.size() == 0)
	    return;

	for(ChatMessageElement chatMessageElement : arrayList)
	{
	    if(chatMessageElement == null)
		continue;

	    appendMessage(chatMessageElement.m_message,
			  chatMessageElement.m_name,
			  chatMessageElement.m_sipHashId,
			  true,
			  chatMessageElement.m_sequence,
			  chatMessageElement.m_timestamp);
	}

	State.getInstance().clearChatLog();
	arrayList.clear();
    }

    private void populateParticipants()
    {
	ArrayList<ParticipantElement> arrayList =
	    m_databaseHelper.readParticipants(s_cryptography, "");
	Button button1 = (Button) findViewById(R.id.call);
	Button button2 = (Button) findViewById(R.id.send_chat_message);
	TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.participants);

	button1.setEnabled(false);
	button2.setEnabled(false);

	if(arrayList == null || arrayList.size() == 0)
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
	int i = 0;

	for(ParticipantElement participantElement : arrayList)
	{
	    if(participantElement == null)
		continue;

	    CheckBox checkBox = new CheckBox(Chat.this);
	    final int oid = participantElement.m_oid;

	    if(showIcons)
	    {
		long current = System.currentTimeMillis();

		if(participantElement.m_keyStream == null ||
		   participantElement.m_keyStream.length != 96)
		    checkBox.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_faulty_session, 0, 0, 0);
		else if(Math.abs(current -
				 participantElement.m_lastStatusTimestamp) >
			STATUS_WINDOW)
		    checkBox.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_status_offline, 0, 0, 0);
		else
		    checkBox.setCompoundDrawablesWithIntrinsicBounds
			(R.drawable.chat_status_online, 0, 0, 0);

		checkBox.setCompoundDrawablePadding(5);
	    }

	    registerForContextMenu(checkBox);
	    checkBox.setChecked
		(State.getInstance().chatCheckBoxIsSelected(oid));
	    checkBox.setOnCheckedChangeListener
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

			if(State.getInstance().chatCheckedParticipants() > 0)
			{
			    button1.setEnabled(true);
			    button2.setEnabled(true);
			}
			else
			{
			    button1.setEnabled(false);
			    button2.setEnabled(false);
			}
		    }
		});

	    if(checkBox.isChecked())
	    {
		button1.setEnabled(true);
		button2.setEnabled(true);
	    }

	    checkBox.setId(participantElement.m_oid);
	    checkBox.setLayoutParams
		(new TableRow.LayoutParams(0,
					   LayoutParams.WRAP_CONTENT,
					   1));
	    stringBuilder.setLength(0);
	    stringBuilder.append(participantElement.m_name);

	    if(showDetails)
	    {
		stringBuilder.append("\n");
		stringBuilder.append
		    (Miscellaneous.
		     delimitString(participantElement.m_sipHashId.
				   replace(":", ""), '-', 4).toUpperCase());
		stringBuilder.append("\n");

		if(participantElement.m_keyStream == null ||
		   participantElement.m_keyStream.length == 0)
		    stringBuilder.append("Session Closed");
		else if(participantElement.m_keyStream.length == 48)
		    stringBuilder.append("Session Incomplete");
		else if(participantElement.m_keyStream.length == 96)
		    stringBuilder.append("Session Ready");
		else
		    stringBuilder.append("Session Faulty");

		if(participantElement.m_keyStream != null &&
		   participantElement.m_keyStream.length == 96)
		{
		    stringBuilder.append("\n");

		    long value = s_siphash.hmac(participantElement.m_keyStream);

		    stringBuilder.append
			(Miscellaneous.
			 byteArrayAsHexStringDelimited(Miscellaneous.
						       longToByteArray(value),
						       '-', 4).toUpperCase());
		}
	    }

	    checkBox.setTag(participantElement.m_sipHashId);
	    checkBox.setText(stringBuilder);
	    checkBox.setTextColor(Color.rgb(255, 255, 255));
	    checkBox.setTextSize(CHECKBOX_TEXT_SIZE);

	    TableRow row = new TableRow(Chat.this);

	    row.addView(checkBox);

	    if(i % 2 == 0)
		row.setBackgroundColor(Color.argb(100, 179, 230, 255));

	    tableLayout.addView(row, i);
	    i += 1;
	}

	arrayList.clear();
    }

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.call);

	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		SimpleDateFormat simpleDateFormat = new
		    SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
				     Locale.getDefault());
		StringBuilder stringBuilder = new StringBuilder();
		TextView textView1 = (TextView) findViewById
		    (R.id.chat_messages);
		TableLayout tableLayout = (TableLayout) findViewById
		    (R.id.participants);

		for(int i = 0; i < tableLayout.getChildCount(); i++)
		{
		    TableRow row = (TableRow) tableLayout.getChildAt(i);

		    if(row == null)
			continue;

		    CheckBox checkBox = (CheckBox) row.getChildAt(0);

		    if(checkBox == null)
			continue;

		    if(checkBox.getTag() != null && checkBox.isChecked())
		    {
			boolean ok = Kernel.getInstance().call
			    (checkBox.getId(), checkBox.getTag().toString());

			stringBuilder.setLength(0);
			stringBuilder.append("[");
			stringBuilder.append
			    (simpleDateFormat.format(new Date()));
			stringBuilder.append("] ");

			if(ok)
			    stringBuilder.append("Initiating a session with ");
			else
			    stringBuilder.append
				("Smoke is currently attempting to " +
				 "establish a session with ");

			stringBuilder.append
			    (nameFromCheckBoxText(checkBox.getText().
						  toString()));
			stringBuilder.append(" (");
			stringBuilder.append
			    (Miscellaneous.
			     delimitString(checkBox.getTag().toString().
					   replace(":", ""), '-', 4).
			     toUpperCase());
			stringBuilder.append("). Please be patient.\n\n");
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

    private void refreshCheckBox(String sipHashId)
    {
	CheckBox checkBox = (CheckBox)
	    findViewById(R.id.participants).findViewWithTag(sipHashId);

	if(checkBox == null)
	    return;

	ArrayList<ParticipantElement> arrayList =
	    m_databaseHelper.readParticipants(s_cryptography, sipHashId);

	if(arrayList == null || arrayList.size() == 0)
	    return;

	ParticipantElement participantElement = arrayList.get(0);

	if(participantElement == null)
	    return;

	if(m_databaseHelper.readSetting(null, "show_chat_details").
	   equals("true"))
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append(participantElement.m_name);
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Miscellaneous.
		 delimitString(participantElement.m_sipHashId.
			       replace(":", ""), '-', 4).toUpperCase());
	    stringBuilder.append("\n");

	    if(participantElement.m_keyStream == null ||
	       participantElement.m_keyStream.length == 0)
		stringBuilder.append("Session Closed");
	    else if(participantElement.m_keyStream.length == 48)
		stringBuilder.append("Session Incomplete");
	    else if(participantElement.m_keyStream.length == 96)
		stringBuilder.append("Session Ready");
	    else
		stringBuilder.append("Session Faulty");

	    if(participantElement.m_keyStream != null &&
	       participantElement.m_keyStream.length == 96)
	    {
		stringBuilder.append("\n");

		long value = s_siphash.hmac(participantElement.m_keyStream);

		stringBuilder.append
		    (Miscellaneous.
		     byteArrayAsHexStringDelimited(Miscellaneous.
						   longToByteArray(value),
						   '-', 4).toUpperCase());
	    }

	    checkBox.setText(stringBuilder);
	}

	if(m_databaseHelper.readSetting(null, "show_chat_icons").equals("true"))
	{
	    long current = System.currentTimeMillis();

	    if(participantElement.m_keyStream == null ||
	       participantElement.m_keyStream.length != 96)
		checkBox.setCompoundDrawablesWithIntrinsicBounds
		    (R.drawable.chat_faulty_session, 0, 0, 0);
	    else if(Math.abs(System.currentTimeMillis() -
			     participantElement.m_lastStatusTimestamp) >
		    STATUS_WINDOW)
		checkBox.setCompoundDrawablesWithIntrinsicBounds
		    (R.drawable.chat_status_offline, 0, 0, 0);
	    else
		checkBox.setCompoundDrawablesWithIntrinsicBounds
		    (R.drawable.chat_status_online, 0, 0, 0);

	    checkBox.setCompoundDrawablePadding(5);
	}

	arrayList.clear();
    }

    private void requestMessages()
    {
	SimpleDateFormat simpleDateFormat = new
	    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById(R.id.chat_messages);

	stringBuilder.append("[");
	stringBuilder.append(simpleDateFormat.format(new Date()));
	stringBuilder.append("] ");
	stringBuilder.append("A request for retrieving offline messages ");
	stringBuilder.append("has been submitted. Offline messages will be ");
	stringBuilder.append("marked by the color ");
	textView1.append(stringBuilder);

	Spannable spannable = new SpannableStringBuilder("blue");

	spannable.setSpan
	    (new ForegroundColorSpan(Color.rgb(30, 144, 255)),
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
	TextView textView = (TextView) findViewById(R.id.chat_message);

	State.getInstance().writeCharSequence
	    ("chat.message", textView.getText());
	textView = (TextView) findViewById(R.id.chat_messages);
	State.getInstance().writeCharSequence
	    ("chat.messages", textView.getText());
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

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_connectionStatusScheduler = Executors.
	    newSingleThreadScheduledExecutor();
	m_connectionStatusScheduler.scheduleAtFixedRate(new Runnable()
        {
	    private AtomicInteger m_greenWritten = new AtomicInteger(0);
	    private AtomicInteger m_redWritten = new AtomicInteger(0);

	    @Override
	    public void run()
	    {
		if(Thread.currentThread().isInterrupted())
		    return;

		final boolean state = Kernel.getInstance().isConnected();

		Chat.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			Button button = (Button) findViewById
			    (R.id.send_chat_message);

			if(state)
			{
			    if(m_greenWritten.get() == 0)
			    {
				appendMessage
				    ("The network is active.",
				     Color.rgb(0, 100, 0));
				m_greenWritten.set(1);
			    }

			    button.setBackgroundColor
				(Color.rgb(153, 204, 0));
			    m_redWritten.set(0);
			}
			else
			{
			    if(m_redWritten.get() == 0)
			    {
				appendMessage
				    ("The device is unable to access the " +
				     "network.",
				     Color.rgb(139, 0, 0));
				m_redWritten.set(1);
			    }

			    button.setBackgroundColor
				(Color.rgb(255, 68, 68));
			    m_greenWritten.set(0);
			}
		    }
		});
	    }
	}, 1500, CONNECTION_STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	m_databaseHelper = Database.getInstance(getApplicationContext());
	m_receiver = new ChatBroadcastReceiver();
	m_statusScheduler = Executors.newSingleThreadScheduledExecutor();
	m_statusScheduler.scheduleAtFixedRate(new Runnable()
        {
	    @Override
	    public void run()
	    {
		if(!m_databaseHelper.readSetting(null, "show_chat_icons").
		   equals("true"))
		    return;

		ArrayList<String> arrayList =
		    m_databaseHelper.readSipHashIdStrings(s_cryptography);

		if(arrayList == null || arrayList.size() == 0)
		    return;

		for(String string : arrayList)
		{
		    if(Thread.currentThread().isInterrupted())
			return;

		    final String sipHashId = string;

		    Chat.this.runOnUiThread(new Runnable()
		    {
			@Override
			public void run()
			{
			    refreshCheckBox(sipHashId);
			}
		    });
		}

		arrayList.clear();
	    }
	}, 1500, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
        setContentView(R.layout.activity_chat);
	setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        Button button1 = (Button) findViewById(R.id.clear_chat_messages);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		TextView textView = (TextView) findViewById
		    (R.id.chat_messages);

		textView.setText("");
	    }
	});

        button1 = (Button) findViewById(R.id.send_chat_message);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		final TextView textView1 = (TextView) findViewById
		    (R.id.chat_message);

		if(textView1.getText().toString().trim().isEmpty())
		    return;

		SimpleDateFormat simpleDateFormat = new
		    SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
				     Locale.getDefault());
		String str = textView1.getText().toString().trim();
		StringBuilder stringBuilder = new StringBuilder();
		TableLayout tableLayout = (TableLayout) findViewById
		    (R.id.participants);
		TextView textView2 = (TextView) findViewById
		    (R.id.chat_messages);

		textView2.append("[");
		textView2.append(simpleDateFormat.format(new Date()));
		textView2.append("] ");

		{
		    Spannable spannable = new SpannableStringBuilder("me");

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
		    char a[] = new char[size - str.length()];

		    Arrays.fill(a, ' ');
		    str += new String(a);
		}

		for(int i = 0; i < tableLayout.getChildCount(); i++)
		{
		    TableRow row = (TableRow) tableLayout.getChildAt(i);

		    if(row == null)
			continue;

		    CheckBox checkBox = (CheckBox) row.getChildAt(0);

		    if(checkBox == null ||
		       checkBox.getTag() == null ||
		       !checkBox.isChecked())
			continue;

		    String sipHashId = checkBox.getTag().toString();
		    byte keyStream[] = m_databaseHelper.participantKeyStream
			(s_cryptography, sipHashId);

		    if(keyStream == null || keyStream.length != 96)
			continue;

		    byte bytes[] = null;

		    try
		    {
			bytes = Messages.chatMessage
			    (s_cryptography,
			     str,
			     sipHashId,
			     false,
			     Cryptography.sha512(sipHashId.getBytes("UTF-8")),
			     keyStream,
			     State.getInstance().chatSequence(sipHashId),
			     System.currentTimeMillis());
		    }
		    catch(Exception exception)
		    {
			bytes = null;
		    }

		    if(bytes != null)
		    {
			Kernel.getInstance().enqueueMessage
			    (Messages.bytesToMessageString(bytes));
			State.getInstance().incrementChatSequence(sipHashId);
		    }

		    if(s_cryptography.ozoneMacKey() != null)
		    {
			bytes = Messages.chatMessage
			    (s_cryptography,
			     str,
			     sipHashId,
			     true,
			     s_cryptography.ozoneMacKey(),
			     keyStream,
			     State.getInstance().chatSequence(sipHashId),
			     System.currentTimeMillis());

			if(bytes != null)
			    Kernel.getInstance().enqueueMessage
				(Messages.bytesToMessageString(bytes));
		    }
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

	if(State.getInstance().isAuthenticated())
	    populateParticipants();

	findViewById(R.id.view).setBackgroundColor
	    (Color.rgb(36, 52, 71));

	/*
	** Preparse some event listeners.
	*/

	prepareListeners();

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

	    if(textView1.getText().length() > 0)
		scrollMessagesView();
	}
	catch(Exception exception)
	{
	}

	populateChat();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
	if(item == null)
	    return false;

	final String sipHashId = Miscellaneous.delimitString
	    (item.getTitle().toString().replace("Custom Session ", "").
	     replace("Optional Signatures ", "").
	     replace("Purge Session ", "").replace("(", "").replace(")", "").
	     replace("-", ""), ':', 2).toLowerCase();
	final int groupId = item.getGroupId();
	final int itemId = item.getItemId();

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
			case 0: // Custom Session
			    try
			    {
				String string = State.getInstance().
				    getString("chat_secret_input");
				byte bytes[] = Cryptography.pbkdf2
				    (Cryptography.sha512(string.
							 getBytes("UTF-8")),
				     string.toCharArray(),
				     CUSTOM_SESSION_ITERATION_COUNT,
				     160); // SHA-1

				if(bytes != null)
				    bytes = Cryptography.pbkdf2
					(Cryptography.sha512(string.
							     getBytes("UTF-8")),
					 new String(bytes).toCharArray(),
					 1,
					 96 * 8); // AES-256, SHA-512

				if(m_databaseHelper.
				   setParticipantKeyStream(s_cryptography,
							   bytes,
							   itemId))
				    refreshCheckBox(sipHashId);
			    }
			    catch(Exception exception)
			    {
			    }

			    State.getInstance().removeKey("chat_secret_input");
			    break;
			case 2: // Purge Session
			    if(m_databaseHelper.
			       setParticipantKeyStream(s_cryptography,
						       null,
						       itemId))
				refreshCheckBox(sipHashId);

			    break;
			}
		}
	    };

	/*
	** Regular expression?
	*/

	if(itemId > -1)
	    switch(groupId)
	    {
	    case 0:
		Miscellaneous.showTextInputDialog
		    (Chat.this,
		     listener,
		     "Please provide a secret for " +
		     item.getTitle().toString().replace("Custom Session (", "").
		     replace(")", "") + ".",
		     "Secret");
		break;
	    case 1:
		item.setChecked(!item.isChecked());

		String string = m_databaseHelper.
		    readParticipantOptions(s_cryptography, sipHashId);
		String strings[] = string.split(";");

		if(strings == null || strings.length == 0)
		{
		    if(item.isChecked())
			string += "optional_signatures = true";
		    else
			string += "optional_signatures = false";
		}
		else
		{
		    string = "";

		    for(int i = 0; i < strings.length; i++)
			if(!(strings[i].equals("optional_signatures = false") ||
			     strings[i].equals("optional_signatures = true")))
			{
			    string += strings[i];

			    if(i != strings.length - 1)
				string += ";";
			}

		    if(!string.isEmpty())
			string += ";";

		    string += "optional_signatures = " +
			(item.isChecked() ? "true" : "false");
		}

		m_databaseHelper.writeParticipantOptions
		    (s_cryptography, string, sipHashId);
		break;
	    case 2:
		Miscellaneous.showPromptDialog
		    (Chat.this,
		     listener,
		     "Are you sure that you " +
		     "wish to purge the session key stream for " +
		     item.getTitle().toString().replace("Purge Session (", "").
		     replace(")", "") + "?");
		break;
	    }
	else
	    switch(groupId)
	    {
	    case 0: // Refresh Participants Table
		populateParticipants();
		break;
	    case 1: // Retrieve Messages
		Kernel.getInstance().retrieveChatMessages();
		requestMessages();
		break;
	    case 2: // Show Details
		item.setChecked(!item.isChecked());
		m_databaseHelper.writeSetting
		    (null,
		     "show_chat_details",
		     item.isChecked() ? "true" : "false");
		populateParticipants();
		break;
	    case 3: // Show Icons
		item.setChecked(!item.isChecked());
		m_databaseHelper.writeSetting
		    (null,
		     "show_chat_icons",
		     item.isChecked() ? "true" : "false");
		populateParticipants();
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
				    View v,
				    ContextMenuInfo menuInfo)
    {
	super.onCreateContextMenu(menu, v, menuInfo);

	MenuItem item = null;

	if(v.getTag() != null)
	{
	    menu.add
		(0,
		 v.getId(),
		 0,
		 "Custom Session (" +
		 Miscellaneous.
		 delimitString(v.getTag().toString().replace(":", ""), '-', 4).
		 toUpperCase() +
		 ")");
	    item = menu.add
		(1,
		 v.getId(),
		 0,
		 "Optional Signatures (" +
		 Miscellaneous.
		 delimitString(v.getTag().toString().replace(":", ""), '-', 4).
		 toUpperCase() +
		 ")").setCheckable(true);
	    item.setChecked
		(m_databaseHelper.
		 readParticipantOptions(s_cryptography, v.getTag().toString()).
		 contains("optional_signatures = true"));
	    menu.add
		(2,
		 v.getId(),
		 0,
		 "Purge Session (" +
		 Miscellaneous.
		 delimitString(v.getTag().toString().replace(":", ""), '-', 4).
		 toUpperCase() +
		 ")");
	}

	menu.add(0, -1, 0, "Refresh Participants Table");
	menu.add(1, -1, 0, "Retrieve Messages");
	item = menu.add(2, -1, 0, "Show Details").setCheckable(true);
	item.setChecked
	    (m_databaseHelper.
	     readSetting(null, "show_chat_details").equals("true"));
	item = menu.add(3, -1, 0, "Show Icons").setCheckable(true);
	item.setChecked
	    (m_databaseHelper.
	     readSetting(null, "show_chat_icons").equals("true"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
	int id = item.getItemId();

        if(id == R.id.action_settings)
	{
	    saveState();
	    m_databaseHelper.writeSetting(null, "lastActivity", "Settings");

            Intent intent = new Intent(Chat.this, Settings.class);

            startActivity(intent);
	    finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
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
	return true;
    }

    @Override
    public void onPause()
    {
	super.onPause();

	if(m_receiverRegistered)
	{
	    unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}

	saveState();
    }

    @Override
    public void onResume()
    {
	super.onResume();

	if(!m_receiverRegistered)
	{
	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction("org.purple.smoke.chat_message");
	    intentFilter.addAction("org.purple.smoke.half_and_half_call");
	    intentFilter.addAction("org.purple.smoke.populate_participants");
	    registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}

	populateChat();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
	/*
	** Do not issue a super.onSaveInstanceState().
	*/
    }

    @Override
    public void onStop()
    {
	super.onStop();

	if(m_receiverRegistered)
	{
	    unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}

	saveState();
    }
}
