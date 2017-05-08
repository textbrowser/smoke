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
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

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
	    if(intent.getAction().
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
	(new byte[] {0, 0, 0, 0, 0, 0, 0, 0,
		     0, 0, 0, 0, 0, 0, 0, 0});
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

    private void halfAndHalfCall(String name,
				 String sipHashId,
				 boolean initial,
				 boolean refresh)
    {
	SimpleDateFormat simpleDateFormat = new
	    SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	StringBuffer stringBuffer = new StringBuffer();
	final TextView textView = (TextView) findViewById
	    (R.id.chat_messages);

	if(name == null)
	    name = "unknown";

	if(sipHashId == null)
	    sipHashId = "00:00:00:00";

	stringBuffer.append("[");
	stringBuffer.append(simpleDateFormat.format(new Date()));
	stringBuffer.append("] ");

	if(initial)
	    stringBuffer.append("Received a half-and-half organic call from ");
	else
	    stringBuffer.append
		("Received a half-and-half organic response from ");

	stringBuffer.append(name);
	stringBuffer.append(" (");
	stringBuffer.append
	    (Miscellaneous.
	     delimitString(sipHashId.replace(":", ""), '-', 4).toUpperCase());
	stringBuffer.append(").");

	if(initial)
	    stringBuffer.append(" Dispatching a response. Please be patient.");

	stringBuffer.append("\n");
	textView.append(stringBuffer);

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

	Hashtable<String, String> checked = new Hashtable<String, String> ();

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

	for(int i = 0; i < arrayList.size(); i++)
	{
	    ParticipantElement participantElement = arrayList.get(i);

	    if(participantElement == null)
		continue;

	    CheckBox checkBox = new CheckBox(Chat.this);
	    StringBuffer stringBuffer = new StringBuffer();
	    TableRow.LayoutParams layoutParams = new
		TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
	    TableRow row = new TableRow(Chat.this);

	    checkBox.setId(participantElement.m_oid);
	    checkBox.setLayoutParams
		(new TableRow.LayoutParams(0,
					   LayoutParams.WRAP_CONTENT,
					   1));
	    stringBuffer.append(participantElement.m_name);
	    stringBuffer.append("\n");
	    stringBuffer.append
		(Miscellaneous.
		 delimitString(participantElement.m_sipHashId.
			       replace(":", ""), '-', 4).toUpperCase());
	    stringBuffer.append("\n");
	    stringBuffer.append("Session Readiness: ");

	    int guessedLength = Kernel.getInstance().callingStreamLength
		(participantElement.m_sipHashId);

	    if(guessedLength >= 0)
	    {
		stringBuffer.append(100 * guessedLength / 96);
		stringBuffer.append("%");
	    }
	    else if(participantElement.m_keyStream == null ||
		    participantElement.m_keyStream.length == 0)
		stringBuffer.append("0%");
	    else if(participantElement.m_keyStream.length == 48)
		stringBuffer.append("50%");
	    else
		stringBuffer.append("100%");

	    stringBuffer.append("\n");
	    stringBuffer.append("KeyStream ID: ");

	    long value = s_siphash.hmac(participantElement.m_keyStream);

	    stringBuffer.append
		(Miscellaneous.
		 delimitString(Miscellaneous.
			       sipHashIdFromData(Miscellaneous.
						 longToByteArray(value)).
			       replace(":", ""), '-', 4).
		 toUpperCase());

	    if(checked.containsKey(participantElement.m_sipHashId))
		checkBox.setChecked(checked.get(participantElement.m_sipHashId).
				    equals("true"));

	    checkBox.setTag(participantElement.m_sipHashId);
	    checkBox.setText(stringBuffer);
	    checkBox.setTextSize(CHECKBOX_TEXT_SIZE);
	    row.addView(checkBox);

	    if(i % 2 == 0)
		row.setBackgroundColor(Color.argb(100, 179, 230, 255));

	    row.setLayoutParams(layoutParams);
	    tableLayout.addView(row, i);
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
		    SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		StringBuffer stringBuffer = new StringBuffer();
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
			Kernel.getInstance().call(checkBox.getId(),
						  checkBox.getTag().toString());
			m_databaseHelper.writeCallKeys
			    (s_cryptography,
			     checkBox.getTag().toString(),
			     new byte[] {});
			stringBuffer.setLength(0);
			stringBuffer.append("[");
			stringBuffer.append
			    (simpleDateFormat.format(new Date()));
			stringBuffer.append("] ");
			stringBuffer.append("Initiating a session with ");
			stringBuffer.append
			    (nameFromCheckBoxText(checkBox.getText().
						  toString()));
			stringBuffer.append(" (");
			stringBuffer.append
			    (Miscellaneous.
			     delimitString(checkBox.getTag().toString().
					   replace(":", ""), '-', 4).
			     toUpperCase());
			stringBuffer.append("). Please be patient.\n");
			textView.append(stringBuffer);
		    }
		}

		if(tableLayout.getChildCount() > 0)
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

		String str = textView1.getText().toString().trim();
		String timestamp = "";
		StringBuffer stringBuffer = new StringBuffer();
		byte encryptionKeyBytes[] = null;
		byte macKeyBytes[] = null;
		byte sipHashKeyStream[] = null;
		long sequence = 1;

		stringBuffer.append("me: ");
		stringBuffer.append(str);
		stringBuffer.append("\n");
		textView2.append(stringBuffer);
		textView1.setText("");

		/*
		** Iterate through selected people, if possible.
		** Otherwise, use checked items.
		*/

		byte bytes[] = Messages.chatMessage
		    (s_cryptography,
		     s_cryptography.chatEncryptionKeyPair().getPublic(),
		     str,
		     timestamp,
		     encryptionKeyBytes,
		     macKeyBytes,
		     sipHashKeyStream,
		     sequence);

		if(bytes != null)
		{
		    Kernel.getInstance().enqueueMessage
			(Messages.bytesToMessageString(bytes));

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
	    }
	});

	TextView textView1 = (TextView) findViewById(R.id.chat_message);

	textView1.requestFocus();

	if(State.getInstance().isAuthenticated())
	    populateParticipants();

	textView1 = (TextView) findViewById(R.id.participant);
	registerForContextMenu(textView1);

	/*
	** Events.
	*/

	prepareListeners();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
	int itemId = item.getItemId();

	if(itemId == 0)
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
	menu.add(0, 0, 0, "Refresh Participants Table");
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

	    intentFilter.addAction("org.purple.smoke.populate_participants");
	    intentFilter.addAction("org.purple.smoke.half_and_half_call");
	    registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}
    }
}
