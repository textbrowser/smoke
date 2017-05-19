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
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextMenu;
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
import java.util.Date;
import java.util.Locale;

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
	    if(intent == null)
		return;

	    if(intent.getAction().equals("org.purple.smoke.chat_message"))
		appendMessage
		    (intent.getStringExtra("org.purple.smoke.message"),
		     intent.getStringExtra("org.purple.smoke.name"),
		     intent.getStringExtra("org.purple.smoke.sipHashId"),
		     intent.getLongExtra("org.purple.smoke.sequence", 1),
		     intent.getLongExtra("org.purple.smoke.timestamp", 0));
	    else if(intent.getAction().
		    equals("org.purple.smoke.populate_participants"))
		populateParticipants();
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
    private boolean m_receiverRegistered = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static SipHash s_siphash = new SipHash
	(new byte[] {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
		     (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
		     (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
		     (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f});
    private final static int CUSTOM_SESSION_ITERATION_COUNT = 1000;
    private final static int CHECKBOX_TEXT_SIZE = 13;

    private String nameFromCheckBoxText(String text)
    {
	/*
	** Name
	** SipHash ID
	** Text
	*/

	try
	{
	    return text.substring(0, text.indexOf('\n', 1));
	}
	catch(Exception exception)
	{
	    return "unknown";
	}
    }

    private void appendMessage(String message,
			       String name,
			       String sipHashId,
			       long sequence,
			       long timestamp)
    {
	SimpleDateFormat simpleDateFormat = new
	    SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView = (TextView) findViewById(R.id.chat_messages);

	stringBuilder.append("[");

	if(timestamp == 0)
	    stringBuilder.append(simpleDateFormat.format(new Date()));
	else
	    stringBuilder.append(simpleDateFormat.format(new Date(timestamp)));

	stringBuilder.append("] ");
	stringBuilder.append(name);
	stringBuilder.append(":");
	stringBuilder.append(sequence);
	stringBuilder.append(": ");
	stringBuilder.append(message);
	stringBuilder.append("\n\n");
	textView.append(stringBuilder);
	scrollMessagesView();
    }

    private void halfAndHalfCall(String name,
				 String sipHashId,
				 boolean initial,
				 boolean refresh)
    {
	SimpleDateFormat simpleDateFormat = new
	    SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView = (TextView) findViewById(R.id.chat_messages);

	if(name == null)
	    name = "unknown";

	if(sipHashId == null)
	    sipHashId = "00:00:00:00";

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
	textView.append(stringBuilder);
	scrollMessagesView();

	if(refresh)
	    populateParticipants();
    }

    private void populateParticipants()
    {
	ArrayList<ParticipantElement> arrayList =
	    m_databaseHelper.readParticipants(s_cryptography);
	Button button1 = (Button) findViewById(R.id.call);
	Button button2 = (Button) findViewById(R.id.send_chat_message);
	TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.participants);
	boolean showDetails = m_databaseHelper.readSetting
	    (null, "show_chat_details").equals("true");

	button1.setEnabled(false);
	button2.setEnabled(false);

	if(arrayList == null || arrayList.size() == 0)
	{
	    tableLayout.removeAllViews();
	    return;
	}

	tableLayout.removeAllViews();

	int i = 0;

	for(ParticipantElement participantElement : arrayList)
	{
	    if(participantElement == null)
		continue;

	    CheckBox checkBox = new CheckBox(Chat.this);
	    final int oid = participantElement.m_oid;

	    checkBox.setCompoundDrawablesWithIntrinsicBounds
		(R.drawable.chat_status_offline, 0, 0, 0);
	    checkBox.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    State.getInstance().setChatCheckBoxSelected
			(oid, isChecked);

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

	    StringBuilder stringBuilder = new StringBuilder();
	    TableRow.LayoutParams layoutParams = new
		TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
	    TableRow row = new TableRow(Chat.this);

	    registerForContextMenu(checkBox);
	    checkBox.setChecked
		(State.getInstance().chatCheckBoxIsSelected(oid));

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
	    stringBuilder.append(participantElement.m_name);
	    stringBuilder.append("\n");

	    if(showDetails)
	    {
		stringBuilder.append
		    (Miscellaneous.
		     delimitString(participantElement.m_sipHashId.
				   replace(":", ""), '-', 4).toUpperCase());
		stringBuilder.append("\n");
	    }

	    int guessedLength = Kernel.getInstance().callingStreamLength
		(participantElement.m_sipHashId);

	    if(guessedLength >= 0)
		switch(100 * guessedLength / 96)
		{
		case 0:
		    stringBuilder.append("Session Closed");
		    break;
		case 50:
		    stringBuilder.append("Session Incomplete");
		    break;
		case 100:
		    stringBuilder.append("Session Ready");
		    break;
		default:
		    stringBuilder.append("Session Faulty");
		}
	    else if(participantElement.m_keyStream == null ||
		    participantElement.m_keyStream.length == 0)
		stringBuilder.append("Session Closed");
	    else if(participantElement.m_keyStream.length == 48)
		stringBuilder.append("Session Incomplete");
	    else if(participantElement.m_keyStream.length == 96)
		stringBuilder.append("Session Ready");
	    else
		stringBuilder.append("Session Faulty");

	    if(participantElement.m_keyStream != null &&
	       participantElement.m_keyStream.length == 96 &&
	       showDetails)
	    {
		stringBuilder.append("\n");

		long value = s_siphash.hmac(participantElement.m_keyStream);

		stringBuilder.append
		    (Miscellaneous.
		     delimitString(Miscellaneous.
				   sipHashIdFromData(Miscellaneous.
						     longToByteArray(value)).
				   replace(":", ""), '-', 4).
		     toUpperCase());
	    }

	    checkBox.setTag(participantElement.m_sipHashId);
	    checkBox.setText(stringBuilder);
	    checkBox.setTextSize(CHECKBOX_TEXT_SIZE);
	    row.addView(checkBox);

	    if(i % 2 == 0)
		row.setBackgroundColor(Color.argb(100, 179, 230, 255));

	    row.setLayoutParams(layoutParams);
	    tableLayout.addView(row, i);
	    i += 1;
	}
    }

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.call);

	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		SimpleDateFormat simpleDateFormat = new
		    SimpleDateFormat("MM/dd/yyyy HH:mm:ss",
				     Locale.getDefault());
		StringBuilder stringBuilder = new StringBuilder();
		TextView textView = (TextView) findViewById
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
			Kernel.getInstance().call
			    (checkBox.getId(), checkBox.getTag().toString());
			stringBuilder.setLength(0);
			stringBuilder.append("[");
			stringBuilder.append
			    (simpleDateFormat.format(new Date()));
			stringBuilder.append("] ");
			stringBuilder.append("Initiating a session with ");
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
			textView.append(stringBuilder);
		    }
		}

		if(tableLayout.getChildCount() > 0)
		    scrollMessagesView();
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
	m_databaseHelper = Database.getInstance(getApplicationContext());
	m_receiver = new ChatBroadcastReceiver();
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
		TextView textView1 = (TextView) findViewById
		    (R.id.chat_message);

		if(textView1.getText().toString().trim().isEmpty())
		    return;

		SimpleDateFormat simpleDateFormat = new
		    SimpleDateFormat("MM/dd/yyyy HH:mm:ss",
				     Locale.getDefault());
		String str = textView1.getText().toString().trim();
		StringBuilder stringBuilder = new StringBuilder();
		TableLayout tableLayout = (TableLayout) findViewById
		    (R.id.participants);
		TextView textView2 = (TextView) findViewById
		    (R.id.chat_messages);

		stringBuilder.append("[");
		stringBuilder.append(simpleDateFormat.format(new Date()));
		stringBuilder.append("] ");
		stringBuilder.append("me: ");
		stringBuilder.append(str);
		stringBuilder.append("\n\n");
		textView2.append(stringBuilder);
		textView1.setText("");

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

		    bytes = Messages.chatMessage
			(s_cryptography,
			 str,
			 sipHashId,
			 keyStream,
			 State.getInstance().chatSequence(sipHashId),
			 System.currentTimeMillis());

		    if(bytes != null)
		    {
			Kernel.getInstance().enqueueMessage
			    (Messages.bytesToMessageString(bytes));
			State.getInstance().incrementChatSequence(sipHashId);
		    }
		}

		scrollMessagesView();
	    }
	});

	TextView textView1 = (TextView) findViewById(R.id.chat_message);

	textView1.requestFocus();

	if(State.getInstance().isAuthenticated())
	    populateParticipants();

	textView1 = (TextView) findViewById(R.id.participant);
	registerForContextMenu(textView1);

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
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
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
			case 0:
			    try
			    {
				String string = State.getInstance().
				    getString("chat_secret_input");
				byte bytes[] = Cryptography.pbkdf2
				    (Cryptography.sha512(string.
							 getBytes("UTF-8")),
				     string.toCharArray(),
				     CUSTOM_SESSION_ITERATION_COUNT,
				     96 * 8); // AES-256, SHA-512

				if(m_databaseHelper.
				   setParticipantKeyStream(s_cryptography,
							   bytes,
							   itemId))
				    populateParticipants();
			    }
			    catch(Exception exception)
			    {
			    }

			    State.getInstance().removeKey("chat_secret_input");
			    break;
			case 1:
			    if(m_databaseHelper.
			       setParticipantKeyStream(s_cryptography,
						       null,
						       itemId))
				populateParticipants();

			    break;
			default:
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
	    case 0:
		populateParticipants();
		break;
	    case 1:
		item.setChecked(!item.isChecked());
		m_databaseHelper.writeSetting
		    (null,
		     "show_chat_details",
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
	    menu.add
		(1,
		 v.getId(),
		 0,
		 "Purge Session (" +
		 Miscellaneous.
		 delimitString(v.getTag().toString().replace(":", ""), '-', 4).
		 toUpperCase() +
		 ")");
	}

	menu.add(0, -1, 0, "Refresh Participants Table");

	MenuItem item = menu.add(1, -1, 0, "Show Details").setCheckable(true);

	item.setChecked
	    (m_databaseHelper.
	     readSetting(null, "show_chat_details").equals("true"));
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
