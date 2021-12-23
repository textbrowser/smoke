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
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Steam extends AppCompatActivity
{
    private class SteamBroadcastReceiver extends BroadcastReceiver
    {
	public SteamBroadcastReceiver()
	{
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
	    if(intent == null || intent.getAction() == null)
		return;

	    switch(intent.getAction())
	    {
	    case "org.purple.smoke.chat_message":
		Miscellaneous.showNotification
		    (Steam.this, intent, findViewById(R.id.main_layout));
		break;
	    case "org.purple.smoke.neighbor_connected":
	    case "org.purple.smoke.network_connected":
		networkStatusChanged();
		break;
	    case "org.purple.smoke.network_disconnected":
		networkStatusChanged();
		break;
	    case "org.purple.smoke.state_participants_populated":
		invalidateOptionsMenu();
		populateParticipants();
		break;
	    case "org.purple.smoke.steam_added":
	    case "org.purple.smoke.steam_status":
		m_adapter.notifyDataSetChanged();
		break;
	    case "org.purple.smoke.time":
		Miscellaneous.showNotification
		    (Steam.this, intent, findViewById(R.id.main_layout));
		break;
	    default:
		break;
	    }
	}
    }

    private static class SteamLinearLayoutManager extends LinearLayoutManager
    {
	SteamLinearLayoutManager(Context context)
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

    private Button m_attachmentButton = null;
    private Button m_sendButton = null;
    private Database m_databaseHelper = null;
    private RecyclerView m_recyclerView = null;
    private RecyclerView.Adapter<?> m_adapter = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private Spinner m_keysSpinner = null;
    private Spinner m_participantsSpinner = null;
    private SteamBroadcastReceiver m_receiver = null;
    private SteamLinearLayoutManager m_layoutManager = null;
    private String m_absoluteFileName = "";
    private TextView m_displayFileName = null;
    private TextView m_downloads = null;
    private TextView m_information = null;
    private boolean m_receiverRegistered = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static int SELECT_FILE_REQUEST = 0;
    private final static long AWAIT_TERMINATION = 5L; // 5 seconds.
    private final static long STATUS_INTERVAL = 1500L; // 1.5 seconds.
    public final static String OTHER = "Other (Non-Smoke)";

    public abstract static class ContextMenuEnumerator
    {
	public final static int DELETE_ALL_STEAMS = 0;
	public final static int DELETE_STEAM = 1;
	public final static int PAUSE_ALL_STEAMS = 2;
	public final static int RESUME_ALL_STEAMS = 3;
	public final static int REWIND_ALL_STEAMS = 4;
	public final static int REWIND_AND_RESUME_ALL_STEAMS = 5;
	public final static int REWIND_STEAM = 6;
	public final static int STEAMROLL_STEAM = 7;
    }

    private void networkStatusChanged()
    {
	try
	{
	    getSupportActionBar().setSubtitle(Smoke.networkStatusString());
	}
	catch(Exception exception)
	{
	}
    }

    private void populateParticipants()
    {
	if(m_participantsSpinner == null)
	    return;

	ArrayList<ParticipantElement> arrayList = State.getInstance().
	    participants();

	if(arrayList == null || arrayList.isEmpty())
	{
	    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>
		(Steam.this,
		 android.R.layout.simple_spinner_item,
		 new String[] {OTHER});

	    m_participantsSpinner.setAdapter(arrayAdapter);
	    return;
	}

	m_participantsSpinner.setAdapter(null);

	ArrayList<String> list = new ArrayList<> ();

	list.add(OTHER);

	for(ParticipantElement participant : arrayList)
	    if(participant != null)
		list.add
		    (participant.m_name + " (" + participant.m_sipHashId + ")");

	ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>
	    (Steam.this, android.R.layout.simple_spinner_item, list);

	m_participantsSpinner.setAdapter(arrayAdapter);
    }

    private void prepareListeners()
    {
	if(m_attachmentButton != null)
	    m_attachmentButton.setOnClickListener(new View.OnClickListener()
	    {
		public void onClick(View view)
		{
		    if(Steam.this.isFinishing())
			return;

		    showFileActivity();
		}
	    });

	m_participantsSpinner.setOnItemSelectedListener
	    (new OnItemSelectedListener()
	    {
		@Override
		public void onItemSelected(AdapterView<?> parent,
					   View view,
					   int position,
					   long id)
		{
		    m_keysSpinner.setEnabled(position != 0);
		    m_keysSpinner.setSelection(1); // RSA
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent)
		{
		}
	    });

	if(m_sendButton != null)
	    m_sendButton.setOnClickListener(new View.OnClickListener()
	    {
		public void onClick(View view)
		{
		    if(Steam.this.isFinishing())
			return;

		    saveSteam();
		}
	    });
    }

    private void prepareSchedulers()
    {
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
			final int availableSteamReaders = Kernel.getInstance().
			    availableSteamReaders();
			final int availableSteamWriters = Kernel.getInstance().
			    availableSteamWriters();

			Steam.this.runOnUiThread(new Runnable()
			{
			    @Override
			    public void run()
			    {
				m_adapter.notifyDataSetChanged();
				m_information.setText
				    ("Active Steam Readers: " +
				     availableSteamReaders + "\n" +
				     "Active Steam Writers: " +
				     availableSteamWriters);
			    }
			});
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 0L, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void prepareWidgets()
    {
	if(m_adapter == null && m_recyclerView != null)
	{
	    m_adapter = new SteamAdapter(this);
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
	    m_recyclerView.setAdapter(m_adapter);
	    m_recyclerView.setLayoutManager(m_layoutManager);
	}
    }

    private void releaseResources()
    {
	if(m_statusScheduler != null)
	{
	    try
	    {
		m_statusScheduler.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }

	    try
	    {
		if(!m_statusScheduler.
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
		    m_statusScheduler.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_statusScheduler = null;
	    }
	}
    }

    private void saveSteam()
    {
	String displayFileName = m_displayFileName.getText().toString();

	if(displayFileName.trim().isEmpty())
	    return;

	SteamElement steamElement = new SteamElement
	    (displayFileName,
	     m_absoluteFileName,
	     m_keysSpinner.getSelectedItem().toString());

	steamElement.m_destination =
	    m_participantsSpinner.getSelectedItem().toString();
	m_absoluteFileName = "";
	m_databaseHelper.writeSteam(s_cryptography, steamElement);
	m_displayFileName.setText("");
	m_participantsSpinner.setSelection(0); // Other (Non-Smoke)
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(Steam.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showFileActivity()
    {
	Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

	intent.setType("*/*");
        startActivityForResult(intent, SELECT_FILE_REQUEST);
    }

    private void showFireActivity()
    {
	Intent intent = new Intent(Steam.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showMemberChatActivity()
    {
	Intent intent = new Intent(Steam.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
	Intent intent = new Intent(Steam.this, Settings.class);

	startActivity(intent);
	finish();
    }

    private void showSmokescreenActivity()
    {
	Intent intent = new Intent(Steam.this, Smokescreen.class);

	startActivity(intent);
	finish();
    }

    @Override
    protected void onActivityResult(int requestCode,
				    int resultCode,
				    Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        try
	{
	    if(data != null &&
	       requestCode == SELECT_FILE_REQUEST &&
	       resultCode == RESULT_OK)
	    {
		Cursor cursor = null;
		String type = getContentResolver().getType(data.getData());

		if(type.lastIndexOf('/') > 0)
		    type = type.substring(type.lastIndexOf('/') + 1);

		m_absoluteFileName = data.getData().toString() + "." + type;

		try
		{
		    cursor = getContentResolver().query
			(data.getData(), null, null, null, null);

		    if(cursor != null && cursor.moveToFirst())
		    {
			String string = cursor.getString
			    (cursor.
			     getColumnIndex(OpenableColumns.DISPLAY_NAME));

			if(string.isEmpty())
			    m_displayFileName.setText("." + type);
			else
			    m_displayFileName.setText(string);
		    }
		}
		catch(Exception exception)
		{
		    /*
		    ** Special failure.
		    */

		    m_displayFileName.setText("." + type);
		}
		finally
		{
		    if(cursor != null)
			cursor.close();
		}
	    }
	}
	catch(Exception exception)
	{
	}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
	m_databaseHelper.clearSteamRates(s_cryptography);
	m_receiver = new SteamBroadcastReceiver();
        setContentView(R.layout.activity_steam);
	m_layoutManager = new SteamLinearLayoutManager(Steam.this);
	m_layoutManager.setOrientation(LinearLayoutManager.VERTICAL);

	try
	{
	    getSupportActionBar().setSubtitle(Smoke.networkStatusString());
	    getSupportActionBar().setTitle("Smoke | Steam");
	}
	catch(Exception exception)
	{
	}

	m_attachmentButton = (Button) findViewById(R.id.attachment);
	m_displayFileName = (TextView) findViewById(R.id.filename);
	m_downloads = (TextView) findViewById(R.id.downloads);
	m_downloads.setText
	    ("Downloads Directory: " +
	     Environment.
	     getExternalStoragePublicDirectory(Environment.
					       DIRECTORY_DOWNLOADS).
	     toString());
	m_information = (TextView) findViewById(R.id.information);
	m_keysSpinner = (Spinner) findViewById(R.id.keys);
	m_keysSpinner.setAdapter
	    (new ArrayAdapter<>(Steam.this,
				android.R.layout.simple_spinner_item,
				new String[] {"McEliece", "RSA"}));
	m_keysSpinner.setEnabled(false);
	m_keysSpinner.setSelection(1); // RSA
	m_participantsSpinner = (Spinner) findViewById(R.id.participants);
	m_recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
	m_recyclerView.setHasFixedSize(true);
	m_sendButton = (Button) findViewById(R.id.send);
	populateParticipants();
	prepareListeners();
	prepareWidgets();

	/*
	** Restore states.
	*/

	try
	{
	    m_layoutManager.smoothScrollToPosition(m_recyclerView, null, 0);
	}
	catch(Exception exception)
	{
	}
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
	    LocalBroadcastManager.getInstance(Steam.this).unregisterReceiver
		(m_receiver);
	    m_receiverRegistered = false;
	}

	releaseResources();
    }

    @Override
    protected void onResume()
    {
	super.onResume();
	networkStatusChanged();

	if(!m_receiverRegistered)
	{
	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction("org.purple.smoke.chat_message");
	    intentFilter.addAction("org.purple.smoke.neighbor_connected");
	    intentFilter.addAction("org.purple.smoke.network_connected");
	    intentFilter.addAction("org.purple.smoke.network_disconnected");
	    intentFilter.addAction
		("org.purple.smoke.state_participants_populated");
	    intentFilter.addAction("org.purple.smoke.steam_added");
	    intentFilter.addAction("org.purple.smoke.steam_status");
	    intentFilter.addAction("org.purple.smoke.time");
	    LocalBroadcastManager.getInstance(Steam.this).
		registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}

	try
	{
	    m_adapter.notifyDataSetChanged();
	    m_layoutManager.smoothScrollToPosition(m_recyclerView, null, 0);
	}
	catch(Exception exception)
	{
	}

	prepareSchedulers();
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
		    case ContextMenuEnumerator.DELETE_ALL_STEAMS:
			if(State.getInstance().
			   getString("dialog_accepted").equals("true"))
			    try
			    {
				State.getInstance().clearSteamDetailsStates();
				m_databaseHelper.clearTable("steam_files");
				m_adapter.notifyDataSetChanged();
			    }
			    catch(Exception exception)
			    {
			    }

			break;
		    case ContextMenuEnumerator.DELETE_STEAM:
			if(State.getInstance().
			   getString("dialog_accepted").equals("true"))
			    try
			    {
				if(m_databaseHelper.
				   deleteEntry(String.valueOf(itemId),
					       "steam_files"))
				{
				    State.getInstance().removeSteamDetailsState
					(itemId);
				    m_adapter.notifyDataSetChanged();
				}
			    }
			    catch(Exception exception)
			    {
			    }

			break;
		    case ContextMenuEnumerator.STEAMROLL_STEAM:
			m_databaseHelper.steamRoll
			    (s_cryptography,
			     State.getInstance().selectedSwitches(),
			     Environment.
			     getExternalStoragePublicDirectory
			     (Environment.DIRECTORY_DOWNLOADS).toString(),
			     itemId);
			State.getInstance().clearSelectedSwitches();
			break;
		    default:
			break;
		    }
		}
	    };

	switch(groupId)
	{
	case ContextMenuEnumerator.DELETE_ALL_STEAMS:
	    Miscellaneous.showPromptDialog
		(Steam.this,
		 listener,
		 "Are you sure that you wish to delete all of the Steams?");
	    break;
	case ContextMenuEnumerator.DELETE_STEAM:
	    Miscellaneous.showPromptDialog
		(Steam.this,
		 listener,
		 "Are you sure that you wish to delete the selected Steam?");
	    break;
	case ContextMenuEnumerator.PAUSE_ALL_STEAMS:
	    m_databaseHelper.pauseAllSteams();
	    m_adapter.notifyDataSetChanged();
	    break;
	case ContextMenuEnumerator.RESUME_ALL_STEAMS:
	    m_databaseHelper.resumeAllSteams();
	    m_adapter.notifyDataSetChanged();
	    break;
	case ContextMenuEnumerator.REWIND_ALL_STEAMS:
	    m_databaseHelper.rewindAllSteams();
	    m_adapter.notifyDataSetChanged();
	    break;
	case ContextMenuEnumerator.REWIND_AND_RESUME_ALL_STEAMS:
	    m_databaseHelper.rewindAndResumeAllSteams();
	    m_adapter.notifyDataSetChanged();
	    break;
	case ContextMenuEnumerator.REWIND_STEAM:
	    m_databaseHelper.writeSteamStatus
		(s_cryptography, "rewind", Miscellaneous.RATE, itemId, 0);
	    m_adapter.notifyDataSetChanged();
	    break;
	case ContextMenuEnumerator.STEAMROLL_STEAM:
	    SteamElement steamElement = m_databaseHelper.readSteam
		(s_cryptography, -1, itemId - 1);
	    String sipHashId = steamElement != null ?
		Miscellaneous.sipHashIdFromDestination
		(steamElement.m_destination) : "";

	    Miscellaneous.showSwitchDialog
		(State.getInstance().participantsNames(sipHashId),
		 Steam.this,
		 listener,
		 "Please select the desired destination participant(s).",
		 "Steamroll Participant Selection");
	    State.getInstance().clearSelectedSwitches();
	    break;
	default:
	    break;
	}

	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.steam_menu, menu);
        return true;
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
	    case R.id.action_chat:
		m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
		showChatActivity();
		return true;
	    case R.id.action_exit:
		Smoke.exit(true, Steam.this);
		return true;
	    case R.id.action_fire:
		m_databaseHelper.writeSetting(null, "lastActivity", "Fire");
		showFireActivity();
		return true;
	    case R.id.action_settings:
		m_databaseHelper.writeSetting(null, "lastActivity", "Settings");
		showSettingsActivity();
		return true;
	    case R.id.action_smokescreen:
		showSmokescreenActivity();
		return true;
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
    public boolean onPrepareOptionsMenu(Menu menu)
    {
	boolean isAuthenticated = State.getInstance().isAuthenticated();

	if(!m_databaseHelper.accountPrepared())
	    /*
	    ** The database may have been modified or removed.
	    */

	    isAuthenticated = true;

	menu.findItem(R.id.action_authenticate).setEnabled(!isAuthenticated);
	Miscellaneous.addMembersToMenu(menu, 6, 250);
	return true;
    }

    @Override
    public void onBackPressed()
    {
	Intent intent = new Intent();

	intent.putExtra("Result", "Done");
	setResult(RESULT_OK, intent);
	super.onBackPressed();
    }

    public void showContextMenu(View view)
    {
	if(view != null)
	{
	    registerForContextMenu(view);
	    openContextMenu(view);
	}
    }
}
