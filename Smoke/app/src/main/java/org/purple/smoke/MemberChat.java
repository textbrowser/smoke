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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemberChat extends AppCompatActivity
{
    private class MemberChatBroadcastReceiver extends BroadcastReceiver
    {
	public MemberChatBroadcastReceiver()
	{
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
	    if(intent == null || intent.getAction() == null)
		return;

	    boolean local = false;

	    if((local = intent.getAction().
		equals("org.purple.smoke.chat_local_message")) ||
	       intent.getAction().equals("org.purple.smoke.chat_message"))
	    {
		if(intent.getStringExtra("org.purple.smoke.sipHashId").
		   equals(m_sipHashId))
		{
		    m_adapter.notifyItemInserted(m_adapter.getItemCount() - 1);

		    if(!local)
		    {
			String message = intent.getStringExtra
			    ("org.purple.smoke.message");
			String name = intent.getStringExtra
			    ("org.purple.smoke.name");

			if(!(message == null || name == null))
			{
			    long sequence = intent.getLongExtra
				("org.purple.smoke.sequence", 1);
			    long timestamp = intent.getLongExtra
				("org.purple.smoke.timestamp", 0);

			    State.getInstance().logChatMessage
				(message,
				 name,
				 m_sipHashId,sequence,
				 timestamp);
			}

			try
			{
			    Ringtone ringtone = null;
			    Uri notification = RingtoneManager.getDefaultUri
				(RingtoneManager.TYPE_NOTIFICATION);

			    ringtone = RingtoneManager.getRingtone
				(getApplicationContext(), notification);
			    ringtone.play();
			}
			catch(Exception e)
			{
			}
		    }
		}
		else
		    Miscellaneous.showNotification
			(MemberChat.this,
			 intent,
			 findViewById(R.id.main_layout));
	    }
	}
    }

    private Database m_databaseHelper = Database.getInstance();
    private LinearLayoutManager m_layoutManager = null;
    private MemberChatBroadcastReceiver m_receiver = null;
    private RecyclerView m_recyclerView = null;
    private RecyclerView.Adapter m_adapter = null;
    private ScheduledExecutorService m_connectionStatusScheduler = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private String m_name = "00:00:00:00:00:00:00:00";
    private String m_sipHashId = m_name;
    private boolean m_receiverRegistered = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static int STATUS_INTERVAL = 5000; // 5 Seconds

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.send_chat_message);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		final TextView textView1 = (TextView) findViewById
		    (R.id.chat_message);

		if(textView1.getText().toString().trim().isEmpty())
		    return;

		String str = textView1.getText().toString().trim();
		int size = Chat.CHAT_MESSAGE_PREFERRED_SIZE *
		    (int) Math.ceil((1.0 * str.length()) /
				    (1.0 * Chat.CHAT_MESSAGE_PREFERRED_SIZE));

		if(size > str.length())
		{
		    char a[] = new char[size - str.length()];

		    Arrays.fill(a, ' ');
		    str += new String(a);
		}
		else if(str.length() > 0)
		{
		    char a[] = new char[1024 + str.length() % 2];

		    Arrays.fill(a, ' ');
		    str += new String(a);
		}

		byte keyStream[] = m_databaseHelper.participantKeyStream
		    (s_cryptography, m_sipHashId);

		if(keyStream == null || keyStream.length != 96)
		    return;

		Kernel.getInstance().enqueueChatMessage
		    (str, m_sipHashId, keyStream);
		textView1.post(new Runnable()
		{
		    @Override
		    public void run()
		    {
			textView1.requestFocus();
		    }
		});
		textView1.setText("");
	    }
	});
    }

    private void prepareSchedulers()
    {
	if(m_connectionStatusScheduler == null)
	{
	    m_connectionStatusScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_connectionStatusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			if(Thread.currentThread().isInterrupted())
			    return;

			final boolean state = Kernel.getInstance().
			    isConnected();

			MemberChat.this.runOnUiThread(new Runnable()
			{
			    @Override
			    public void run()
			    {
				Button button1 = (Button) findViewById
				    (R.id.send_chat_message);

				if(state)
				    button1.
					setCompoundDrawablesWithIntrinsicBounds
					(R.drawable.network_up, 0, 0, 0);
				else
				    button1.
					setCompoundDrawablesWithIntrinsicBounds
					(R.drawable.network_down, 0, 0, 0);
			    }
			});
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, Chat.CONNECTION_STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_statusScheduler == null)
	{
	    m_statusScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_statusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    ArrayList<ParticipantElement> arrayList =
			m_databaseHelper.readParticipants
			(s_cryptography, m_sipHashId);

		    if(arrayList == null || arrayList.isEmpty())
			return;

		    final ParticipantElement participantElement =
			arrayList.get(0);

		    try
		    {
			MemberChat.this.runOnUiThread(new Runnable()
			{
			    @Override
			    public void run()
			    {
				ImageView imageView = (ImageView)
				    findViewById(R.id.status);

				if(participantElement.m_keyStream == null ||
				   participantElement.m_keyStream.length != 96)
				    imageView.setImageResource
					(R.drawable.chat_faulty_session);
				else if(Math.abs(System.currentTimeMillis() -
						 participantElement.
						 m_lastStatusTimestamp) >
					Chat.STATUS_WINDOW)
				    imageView.setImageResource
					(R.drawable.chat_status_offline);
				else
				    imageView.setImageResource
					(R.drawable.chat_status_online);
			    }
			});
		    }
		    catch(Exception exception)
		    {
		    }

		    arrayList.clear();
		}
	    }, 1500, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(MemberChat.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showFireActivity()
    {
	Intent intent = new Intent(MemberChat.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
	Intent intent = new Intent(MemberChat.this, Settings.class);

	startActivity(intent);
	finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_chat);
	setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
	m_layoutManager = new LinearLayoutManager(this);
	m_layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
	m_name = m_sipHashId = State.getInstance().getString
	    ("member_chat_siphash_id");
	m_receiver = new MemberChatBroadcastReceiver();
	m_recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
	m_recyclerView.setHasFixedSize(true);

	if(m_sipHashId.isEmpty())
	    m_name = m_sipHashId = "00:00:00:00:00:00:00:00";

	/*
	** Prepare various widgets.
	*/

	m_adapter = new MemberChatAdapter(m_sipHashId);
	m_adapter.registerAdapterDataObserver
	    (new RecyclerView.AdapterDataObserver()
	    {
		@Override
		public void onItemRangeInserted
		    (int positionStart, int itemCount)
		{
		    m_layoutManager.smoothScrollToPosition
			(m_recyclerView, null, positionStart);
		}
	    });
	m_name = m_databaseHelper.nameFromSipHashId
	    (s_cryptography, m_sipHashId);

	if(m_name.isEmpty())
	    m_name = m_sipHashId;

	m_recyclerView.setAdapter(m_adapter);
	m_recyclerView.setLayoutManager(m_layoutManager);

	TextView textView1 = (TextView) findViewById(R.id.banner);

	textView1.setText(m_name +
			  "@" +
			  Miscellaneous.
			  delimitString(m_sipHashId.replace(":", ""), '-', 4).
			  toUpperCase());

	/*
	** Prepare listeners.
	*/

	prepareListeners();

	/*
	** Prepare schedulers.
	*/

	prepareSchedulers();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
	if(item == null)
	    return false;

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
		    {
			if(State.getInstance().getString("dialog_accepted").
			   equals("true"))
			{
			}
		    }
		    else if(State.getInstance().getString("dialog_accepted").
			    equals("true"))
			m_databaseHelper.deleteParticipantMessages
			    (s_cryptography, m_sipHashId);
		}
	    };

	if(itemId > -1)
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete the selected message?");
	else
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete all of the messages?");

	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	getMenuInflater().inflate(R.menu.member_chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
	int id = item.getItemId();

	if(id == R.id.action_chat)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Chat");

	    Intent intent = new Intent(MemberChat.this, Chat.class);

            startActivity(intent);
	    finish();
	    return true;
	}
	else if(id == R.id.action_fire)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Fire");

	    Intent intent = new Intent(MemberChat.this, Fire.class);

            startActivity(intent);
	    finish();
	    return true;
	}
	else if(id == R.id.action_settings)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Settings");

            Intent intent = new Intent(MemberChat.this, Settings.class);

            startActivity(intent);
	    finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause()
    {
	super.onPause();

	if(m_receiverRegistered)
	{
	    LocalBroadcastManager.getInstance(this).
		unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}
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
    public void onResume()
    {
	super.onResume();

	if(!m_receiverRegistered)
	{
	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction("org.purple.smoke.chat_local_message");
	    intentFilter.addAction("org.purple.smoke.chat_message");
	    LocalBroadcastManager.getInstance(this).
		registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}
    }

    @Override
    public void onStop()
    {
	super.onStop();

	if(m_receiverRegistered)
	{
	    LocalBroadcastManager.getInstance(this).
		unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}
    }
}
