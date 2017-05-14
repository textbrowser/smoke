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
import java.util.Hashtable;
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
	(new byte[] {1, 2, 3, 4, 5, 6, 7, 8,
		     9, 10, 11, 12, 13, 14, 15, 16});
    private final static int CHECKBOX_TEXT_SIZE = 13;

    private String nameFromCheckBoxText(String text)
    {
	/*
	** Name
	** SipHash ID
	** Percentage
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
	final TextView textView = (TextView) findViewById
	    (R.id.chat_messages);

	stringBuilder.append("[");

	if(timestamp == 0)
	    stringBuilder.append(simpleDateFormat.format(new Date()));
	else
	    stringBuilder.append(simpleDateFormat.format(new Date(timestamp)));

	stringBuilder.append("] ");
	stringBuilder.append(name);
	stringBuilder.append(" (");
       	stringBuilder.append
	    (Miscellaneous.
	     delimitString(sipHashId.replace(":", ""), '-', 4).toUpperCase());
	stringBuilder.append(")");
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
	final TextView textView = (TextView) findViewById
	    (R.id.chat_messages);

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
	final TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.participants);

	if(arrayList == null || arrayList.size() == 0)
	{
	    tableLayout.removeAllViews();
	    return;
	}

	Hashtable<String, String> checked = new Hashtable<> ();

	for(int i = 0; i < tableLayout.getChildCount(); i++)
	{
	    TableRow row = (TableRow) tableLayout.getChildAt(i);

	    if(row == null)
		continue;

	    CheckBox checkBox = (CheckBox) row.getChildAt(0);

	    if(checkBox == null)
		continue;

	    if(checkBox.getTag() != null)
		checked.put(checkBox.getTag().toString(),
			    String.valueOf(checkBox.isChecked()));
	}

	tableLayout.removeAllViews();

	int i = 0;

	for(ParticipantElement participantElement : arrayList)
	{
	    if(participantElement == null)
		continue;

	    CheckBox checkBox = new CheckBox(Chat.this);
	    final int oid = participantElement.m_oid;

	    checkBox.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    State.getInstance().setChatCheckBoxSelected
			(oid, isChecked);
		}
	    });

	    StringBuilder stringBuilder = new StringBuilder();
	    TableRow.LayoutParams layoutParams = new
		TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
	    TableRow row = new TableRow(Chat.this);

	    registerForContextMenu(checkBox);
	    checkBox.setChecked
		(State.getInstance().chatCheckBoxIsSelected(oid));
	    checkBox.setId(participantElement.m_oid);
	    checkBox.setLayoutParams
		(new TableRow.LayoutParams(0,
					   LayoutParams.WRAP_CONTENT,
					   1));
	    stringBuilder.append(participantElement.m_name);
	    stringBuilder.append("\n");
	    stringBuilder.append
		(Miscellaneous.
		 delimitString(participantElement.m_sipHashId.
			       replace(":", ""), '-', 4).toUpperCase());
	    stringBuilder.append("\n");

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
	       participantElement.m_keyStream.length == 96)
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

	    if(checked.containsKey(participantElement.m_sipHashId))
		checkBox.setChecked(checked.get(participantElement.m_sipHashId).
				    equals("true"));

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
		final TextView textView = (TextView) findViewById
		    (R.id.chat_messages);
		final TableLayout tableLayout = (TableLayout) findViewById
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
		final TextView textView = (TextView) findViewById
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

		final TextView textView2 = (TextView) findViewById
		    (R.id.chat_messages);

		SimpleDateFormat simpleDateFormat = new
		    SimpleDateFormat("MM/dd/yyyy HH:mm:ss",
				     Locale.getDefault());
		String str = textView1.getText().toString().trim();
		StringBuilder stringBuilder = new StringBuilder();
		final TableLayout tableLayout = (TableLayout) findViewById
		    (R.id.participants);

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

		    stringBuilder.setLength(0);
		    stringBuilder.append("[");
		    stringBuilder.append(simpleDateFormat.format(new Date()));
		    stringBuilder.append("] ");
		    stringBuilder.append("me: ");
		    stringBuilder.append(str);
		    stringBuilder.append("\n\n");
		    textView2.append(stringBuilder);
		    textView1.setText("");

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

	final DialogInterface.OnCancelListener listener =
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
				     1000,
				     96 * 8);

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
		     "wish to reset the session for " +
		     item.getTitle().toString().replace("Reset Session (", "").
		     replace(")", "") + "?");
		break;
	    }
	else
	    populateParticipants();

	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.

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
		 "Reset Session (" +
		 Miscellaneous.
		 delimitString(v.getTag().toString().replace(":", ""), '-', 4).
		 toUpperCase() +
		 ")");
	}

	menu.add(0, -1, 0, "Refresh Participants Table");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

	int id = item.getItemId();

        if(id == R.id.action_settings)
	{
	    TextView textView = (TextView) findViewById(R.id.chat_message);

	    State.getInstance().writeCharSequence
		("chat.message", textView.getText());
	    textView = (TextView) findViewById(R.id.chat_messages);
	    State.getInstance().writeCharSequence
		("chat.messages", textView.getText());
	    m_databaseHelper.writeSetting(null, "lastActivity", "Settings");

            final Intent intent = new Intent(Chat.this, Settings.class);

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
}
