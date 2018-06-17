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
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.text.SpannableStringBuilder;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextMenu;
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
		if(intent.
		   getStringExtra("org.purple.smoke.sipHashId") != null &&
		   intent.getStringExtra("org.purple.smoke.sipHashId").
		   equals(m_sipHashId))
		{
		    m_adapter.notifyDataSetChanged(); /*
						      ** Items are inserted
						      ** into the database
						      ** haphazardly.
						      */
		    m_adapter.notifyItemInserted(m_adapter.getItemCount() - 1);

		    if(local)
		    {
			String message = intent.getStringExtra
			    ("org.purple.smoke.message");
			long timestamp = intent.getLongExtra
			    ("org.purple.smoke.timestamp", 0);

			State.getInstance().logChatMessage
			    (message,
			     "me",
			     m_mySipHashId,
			     false,
			     -1,
			     timestamp);
		    }
		    else
		    {
			String message = intent.getStringExtra
			    ("org.purple.smoke.message");
			String name = intent.getStringExtra
			    ("org.purple.smoke.name");
			boolean purple = intent.getBooleanExtra
			    ("org.purple.smoke.purple", false);
			long sequence = intent.getLongExtra
			    ("org.purple.smoke.sequence", 1);
			long timestamp = intent.getLongExtra
			    ("org.purple.smoke.timestamp", 0);

			State.getInstance().logChatMessage
			    (message,
			     name,
			     m_sipHashId,
			     purple,
			     sequence,
			     timestamp);

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

    private class SmokeLinearLayoutManager extends LinearLayoutManager
    {
	SmokeLinearLayoutManager(Context context)
	{
	    super(context);
	}

	@Override
	public void onLayoutChildren(RecyclerView.Recycler recycler,
				     RecyclerView.State state)
	{
	    /*
	    ** Android may terminate!
	    */

	    try
	    {
		super.onLayoutChildren(recycler, state);
	    }
	    catch(Exception exception)
	    {
	    }
	}
    }

    private Database m_databaseHelper = Database.getInstance();
    private MemberChatBroadcastReceiver m_receiver = null;
    private RecyclerView m_recyclerView = null;
    private RecyclerView.Adapter m_adapter = null;
    private ScheduledExecutorService m_connectionStatusScheduler = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private SmokeLinearLayoutManager m_layoutManager = null;
    private String m_name = "00:00:00:00:00:00:00:00";
    private String m_mySipHashId = "";
    private String m_sipHashId = m_name;
    private boolean m_receiverRegistered = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static int STATUS_INTERVAL = 5000; // 5 Seconds
    private int m_oid = -1;

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.send_chat_message);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(MemberChat.this.isFinishing())
		    return;

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
				invalidateOptionsMenu();

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
	    }, 0, Chat.CONNECTION_STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}

	if(m_statusScheduler == null)
	{
	    m_statusScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_statusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
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

				    if(participantElement.
				       m_keyStream == null ||
				       participantElement.
				       m_keyStream.length != 96)
					imageView.setImageResource
					    (R.drawable.chat_faulty_session);
				    else if(Math.abs(System.
						     currentTimeMillis() -
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
		    catch(Exception exception)
		    {
		    }
		}
	    }, 0, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void saveState()
    {
	TextView textView1 = (TextView) findViewById(R.id.chat_message);

	State.getInstance().writeCharSequence
	    ("member_chat.message", textView1.getText());
    }

    private void showChatActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showMemberChatActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showFireActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
	saveState();

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
	m_layoutManager = new SmokeLinearLayoutManager(this);
	m_layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
	m_mySipHashId = s_cryptography.sipHashId();
	m_name = m_sipHashId = State.getInstance().getString
	    ("member_chat_siphash_id");

	try
	{
	    m_oid = Integer.parseInt
		(State.getInstance().getString("member_chat_oid"));
	}
	catch(Exception exception)
	{
	    m_oid = -1;
	}

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

		@Override
		public void onItemRangeRemoved
		    (int positionStart, int itemCount)
		{
		    m_layoutManager.smoothScrollToPosition
			(m_recyclerView, null, positionStart - itemCount);
		}
	    });
	m_name = m_databaseHelper.nameFromSipHashId
	    (s_cryptography, m_sipHashId);

	if(m_name.isEmpty())
	    m_name = m_sipHashId;

	m_recyclerView.setAdapter(m_adapter);
	m_recyclerView.setLayoutManager(m_layoutManager);

	String string =	Miscellaneous.delimitString
	    (m_sipHashId.replace(":", ""), '-', 4).toUpperCase();
	TextView textView1 = (TextView) findViewById(R.id.banner);

	if(string.isEmpty())
	    textView1.setText("Error!");
	else
	    textView1.setText(m_name + "@\n" + string);

	/*
	** Prepare listeners.
	*/

	prepareListeners();

	/*
	** Prepare schedulers.
	*/

	prepareSchedulers();

	/*
	** Register other things.
	*/

	registerForContextMenu(findViewById(R.id.status));

	/*
	** Restore states.
	*/

	try
	{
	    m_layoutManager.smoothScrollToPosition
		(m_recyclerView, null, m_adapter.getItemCount() - 1);
	    textView1 = (TextView) findViewById(R.id.chat_message);
	    textView1.setText
		(State.getInstance().getCharSequence("member_chat.message"));

	}
	catch(Exception exception)
	{
	}
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem)
    {
	if(menuItem == null)
	    return false;

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
		    switch(groupId)
		    {
		    case 1:
			try
			{
			    String string = State.getInstance().
				getString("member_chat_secret_input");
			    byte bytes[] = Cryptography.pbkdf2
				(Cryptography.sha512(string.getBytes("UTF-8")),
				 string.toCharArray(),
				 Chat.CUSTOM_SESSION_ITERATION_COUNT,
				 160); // SHA-1
			    int oid = m_databaseHelper.
				participantOidFromSipHash
				(s_cryptography, m_sipHashId);

			    if(bytes != null)
				bytes = Cryptography.pbkdf2
				    (Cryptography.sha512(string.
							 getBytes("UTF-8")),
				     new String(bytes).toCharArray(),
				     1,
				     96 * 8); // AES-256, SHA-512

			    m_databaseHelper.setParticipantKeyStream
				(s_cryptography, bytes, oid);
			}
			catch(Exception exception)
			{
			}

			State.getInstance().removeKey
			    ("member_chat_secret_input");
			break;
		    case 10:
			if(State.getInstance().getString("dialog_accepted").
			   equals("true"))
			    m_databaseHelper.deleteParticipantMessages
				(s_cryptography, m_sipHashId);

			break;
		    case 15:
			if(State.getInstance().getString("dialog_accepted").
			   equals("true"))
			{
			    m_databaseHelper.deleteParticipantMessage
				(s_cryptography, m_sipHashId, itemId);
			    m_adapter.notifyDataSetChanged();
			}

			break;
		    }
		}
	    };

	switch(groupId)
	{
	case 0:
	    Kernel.getInstance().call(m_oid, m_sipHashId);
	    break;
	case 1:
	    Miscellaneous.showTextInputDialog
		(MemberChat.this,
		 listener,
		 "Please provide a secret.",
		 "Secret");
	    break;
	case 2:
	    Kernel.getInstance().retrieveChatMessages();
	    break;
	case 10:
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete all " +
		 "of the messages?");
	    break;
	case 15:
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete the " +
		 "selected message?");
	    break;
	case 20:
	    try
	    {
		View view = (View) m_layoutManager.findViewByPosition(itemId);

		if(view != null)
		{
		    TextView textView = (TextView) view.findViewById
			(R.id.text);

		    if(textView != null)
		    {
			ClipboardManager clipboardManager = (ClipboardManager)
			    getSystemService(Context.CLIPBOARD_SERVICE);

			if(clipboardManager != null)
			{
			    ClipData clipData = null;
			    SpannableStringBuilder spannableStringBuilder =
				new SpannableStringBuilder(textView.getText());

			    spannableStringBuilder.clearSpans();
			    clipData = ClipData.newPlainText
				("Smoke", spannableStringBuilder.toString());
			    clipboardManager.setPrimaryClip(clipData);
			}
		    }
		}
	    }
	    catch(Exception exception)
	    {
	    }

	    break;
	}

	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	getMenuInflater().inflate(R.menu.member_chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
	int groupId = menuItem.getGroupId();
	int itemId = menuItem.getItemId();

	if(groupId == Menu.NONE)
	    switch(itemId)
	    {
	    case R.id.action_chat:
	    {
		m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
		showChatActivity();
		return true;
	    }
	    case R.id.action_fire:
	    {
		m_databaseHelper.writeSetting(null, "lastActivity", "Fire");
		showFireActivity();
		return true;
	    }
	    case R.id.action_settings:
	    {
		m_databaseHelper.writeSetting(null, "lastActivity", "Settings");
		showSettingsActivity();
		return true;
	    }
	    }
	else
	{
	    String sipHashId = menuItem.getTitle().toString();
	    int indexOf = sipHashId.indexOf("(");

	    if(indexOf >= 0)
		sipHashId = sipHashId.substring(indexOf + 1).replace(")", "");

	    sipHashId = Miscellaneous.delimitString
		(sipHashId.replace("-", "").replace(":", "").
		 toLowerCase(), ':', 2);
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
	}

        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
				    View view,
				    ContextMenuInfo menuInfo)
    {
	super.onCreateContextMenu(menu, view, menuInfo);

	MenuItem menuItem = null;

	menu.add(0, -1, 0, "Call");
	menu.add(1, -1, 0, "Custom Session");
	menuItem = menu.add(2, -1, 0, "Retrieve Messages");
	menuItem.setEnabled
	    (Kernel.getInstance().isConnected() &&
	     !m_databaseHelper.readSetting(s_cryptography, "ozone_address").
	     isEmpty());
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

	saveState();
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
	Miscellaneous.addMembersToMenu(menu, 4, 250);
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

	try
	{
	    m_layoutManager.smoothScrollToPosition
		(m_recyclerView, null, m_adapter.getItemCount() - 1);

	}
	catch(Exception exception)
	{
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

	saveState();
    }
}
