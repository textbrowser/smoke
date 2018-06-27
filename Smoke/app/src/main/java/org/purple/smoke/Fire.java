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
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

public class Fire extends AppCompatActivity
{
    private Database m_databaseHelper = null;

    private class FireBroadcastReceiver extends BroadcastReceiver
    {
	public FireBroadcastReceiver()
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
		    (Fire.this, intent, findViewById(R.id.main_layout));
		break;
	    case "org.purple.smoke.fire_message":
		FireChannel fireChannel = State.getInstance().fireChannel
		    (intent.getStringExtra("org.purple.smoke.channel"));

		if(fireChannel != null)
		{
		    String id = intent.getStringExtra("org.purple.smoke.id");
		    String name = intent.getStringExtra
			("org.purple.smoke.name");

		    if(intent.getStringExtra("org.purple.smoke.message_type").
		       equals(Messages.FIRE_CHAT_MESSAGE_TYPE))
		    {
			String message = intent.getStringExtra
			    ("org.purple.smoke.message");

			fireChannel.append(id, message, name);
		    }
		    else if(intent.
			    getStringExtra("org.purple.smoke.message_type").
			    equals(Messages.FIRE_STATUS_MESSAGE_TYPE))
			fireChannel.status(id, name);
		}

		break;
	    case "org.purple.smoke.state_participants_populated":
		invalidateOptionsMenu();
		break;
	    }
	}
    }

    private FireBroadcastReceiver m_receiver = null;
    private boolean m_receiverRegistered = false;
    private final Hashtable<String, Integer> m_fireHash = new Hashtable<> ();
    private final String m_id = Miscellaneous.byteArrayAsHexString
	(Cryptography.randomBytes(128));
    private final static CharsetEncoder s_latin1Encoder = Charset.
	forName("ISO-8859-1").newEncoder();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static InputFilter s_Latin1InputFilter = new InputFilter()
    {
	public CharSequence filter(CharSequence source,
				   int start,
				   int end,
				   Spanned dest,
				   int dstart,
				   int dend)
	{

	    for(int i = start; i < end; i++)
		if(!s_latin1Encoder.canEncode(source.charAt(i)))
		    return source.subSequence(start, i);

	    return null;
	}
    };

    private void deleteFire(String name, final Integer oid)
    {
	/*
	** Prepare a response.
	*/

	final DialogInterface.OnCancelListener listener =
	    new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    if(State.getInstance().
		       getString("dialog_accepted").equals("true"))
			if(m_databaseHelper.
			   deleteEntry(String.valueOf(oid.intValue()), "fire"))
			    populateFires();
	        }
	    };

	Miscellaneous.showPromptDialog
	    (Fire.this,
	     listener,
	     "Are you sure that you wish to " +
	     "delete the Fire channel " + name + "?");
    }

    private void joinFire(String name)
    {
	if(State.getInstance().containsFire(name))
	    return;

	FireChannel fireChannel = null;
	ViewGroup viewGroup = (ViewGroup) findViewById(R.id.linear_layout);

	fireChannel = new FireChannel(m_id, name, Fire.this, viewGroup);
	State.getInstance().addFire(fireChannel);

	int index = -1;

	for(int i = 0; i < viewGroup.getChildCount(); i++)
	{
	    String other = State.getInstance().nameOfFireFromView
		(viewGroup.getChildAt(i));

	    if(name.compareTo(other) < 0 && name.length() > 0)
	    {
		index = i;
		break;
	    }
	}

	DisplayMetrics displayMetrics = new DisplayMetrics();

	getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

	int height = (int) (0.6 * displayMetrics.heightPixels);

	viewGroup.addView
	    (fireChannel.view(),
	     index,
	     new LayoutParams(LayoutParams.WRAP_CONTENT, height));
	viewGroup.requestLayout();
    }

    private void populateFires()
    {
	ArrayList<FireElement> arrayList =
	    m_databaseHelper.readFires(s_cryptography);
	Spinner spinner = (Spinner) findViewById(R.id.fires);

	m_fireHash.clear();
	spinner.setAdapter(null);

	if(arrayList == null || arrayList.isEmpty())
	    return;

	ArrayList<String> array = new ArrayList<>();

	for(FireElement fireElement : arrayList)
	{
	    if(fireElement == null)
		continue;

	    array.add(fireElement.m_name);
	    m_fireHash.put(fireElement.m_name, fireElement.m_oid);
	}

	ArrayAdapter arrayAdapter = null;

	arrayAdapter = new ArrayAdapter<>
	    (Fire.this, android.R.layout.simple_spinner_item, array);
	spinner.setAdapter(arrayAdapter);
	arrayList.clear();
    }

    private void prepareAutoFill()
    {
	Spinner spinner = (Spinner) findViewById(R.id.auto_fill);
        String array[] = new String[]
	{
	    "Please Select",
	    "Spot-On Developer Channel"
	};

	ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>
	    (Fire.this, android.R.layout.simple_spinner_item, array);

        spinner.setAdapter(arrayAdapter);
	spinner.setOnItemSelectedListener(new OnItemSelectedListener()
	{
	    @Override
	    public void onItemSelected(AdapterView<?> parent,
				       View view,
				       int position,
				       long id)
	    {
		if(position == 1)
		{
		    TextView textView1 = (TextView) findViewById(R.id.channel);
		    TextView textView2 = (TextView) findViewById(R.id.digest);
		    TextView textView3 = (TextView) findViewById(R.id.salt);

		    textView1.setText("Spot-On_Developer_Channel_Key");
		    textView2.setText("Spot-On_Developer_Channel_Hash_Key");
		    textView3.setText("Spot-On_Developer_Channel_Salt");
		}

		parent.setSelection(0);
	    }

	    @Override
	    public void onNothingSelected(AdapterView<?> parent)
	    {
	    }
        });
    }

    private void prepareListeners()
    {
	Button button1 = null;

	button1 = (Button) findViewById(R.id.add_channel);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Fire.this.isFinishing())
		    return;

		TextView textView1 = (TextView) findViewById(R.id.channel);
		TextView textView2 = (TextView) findViewById(R.id.digest);
		TextView textView3 = (TextView) findViewById(R.id.salt);

		if(textView1.getText().toString().trim().isEmpty())
		{
		    Miscellaneous.showErrorDialog
			(Fire.this, "Please complete the Channel field.");
		    textView1.requestFocus();
		}
		else if(textView2.getText().toString().trim().isEmpty())
		{
		    Miscellaneous.showErrorDialog
			(Fire.this, "Please complete the Digest Key field.");
		    textView2.requestFocus();
		}
		else if(textView3.getText().toString().trim().isEmpty())
		{
		    Miscellaneous.showErrorDialog
			(Fire.this, "Please complete the Salt field.");
		    textView3.requestFocus();
		}
		else
		{
		    textView1 = (TextView) findViewById(R.id.channel);

		    final String channel = textView1.getText().toString().
			trim();

		    textView1 = (TextView) findViewById(R.id.salt);

		    final String salt = textView1.getText().toString().trim();

		    /*
		    ** Display a progress bar.
		    */

		    final ProgressBar bar = (ProgressBar) findViewById
			(R.id.progress_bar);

		    bar.setIndeterminate(true);
		    bar.setVisibility(ProgressBar.VISIBLE);
		    getWindow().setFlags
			(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
			 WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
		    Miscellaneous.enableChildren
			(findViewById(R.id.linear_layout), false);

		    class SingleShot implements Runnable
		    {
			private byte m_encryptionKey[] = null;
			private byte m_keyStream[] = null;

			SingleShot()
			{
			}

			@Override
			public void run()
			{
			    try
			    {
				final TextView textView1 =
				    (TextView) findViewById(R.id.channel);
				final TextView textView2 =
				    (TextView) findViewById(R.id.digest);

				m_encryptionKey = s_cryptography.
				    generateFireEncryptionKey(channel, salt);
				m_keyStream = s_cryptography.
				    generateFireDigestKeyStream
				    (textView2.getText().toString().trim());

				Fire.this.runOnUiThread(new Runnable()
				{
				    @Override
				    public void run()
				    {
					bar.setVisibility
					    (ProgressBar.INVISIBLE);
					getWindow().clearFlags
					    (WindowManager.LayoutParams.
					     FLAG_NOT_TOUCHABLE);
					Miscellaneous.enableChildren
					    (findViewById(R.id.linear_layout),
					     true);

					if(m_encryptionKey != null &&
					   m_keyStream != null)
					{
					    m_databaseHelper.saveFireChannel
						(s_cryptography,
						 channel,
						 m_encryptionKey,
						 m_keyStream);
					    populateFires();
					}
				    }
				});
			    }
			    catch(Exception exception)
			    {
			    }
			}
		    }

		    Thread thread = new Thread(new SingleShot());

		    thread.start();
		}
	    }
	});

	button1 = (Button) findViewById(R.id.delete);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Fire.this.isFinishing())
		    return;

		Spinner spinner = (Spinner) findViewById(R.id.fires);

		if(spinner.getAdapter() != null &&
		   spinner.getAdapter().getCount() > 0)
		    deleteFire
			(spinner.getSelectedItem().toString(),
			 m_fireHash.get(spinner.getSelectedItem().toString()));
	    }
	});

	button1 = (Button) findViewById(R.id.join);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Fire.this.isFinishing())
		    return;

		Spinner spinner = (Spinner) findViewById(R.id.fires);

		if(spinner.getAdapter() != null &&
		   spinner.getAdapter().getCount() > 0)
		    joinFire(spinner.getSelectedItem().toString());
	    }
	});

	button1 = (Button) findViewById(R.id.reset_fields);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Fire.this.isFinishing())
		    return;

		TextView textView1 = (TextView) findViewById(R.id.channel);

		textView1.requestFocus();
		textView1.setText("");
		textView1 = (TextView) findViewById(R.id.digest);
		textView1.setText("");
		textView1 = (TextView) findViewById(R.id.salt);
		textView1.setText("");
	    }
	});

	button1 = (Button) findViewById(R.id.save_name);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Fire.this.isFinishing())
		    return;

		TextView textView1 = (TextView) findViewById(R.id.name);

		m_databaseHelper.writeSetting
		    (s_cryptography,
		     "fire_user_name",
		     textView1.getText().toString().trim());
		textView1.setText(textView1.getText().toString().trim());

		Map<String, FireChannel> map = State.getInstance().
		    fireChannels();

		if(map != null)
		    for(Map.Entry<String, FireChannel> entry : map.entrySet())
		    {
			if(entry.getValue() == null)
			    continue;

			entry.getValue().setUserName
			    (textView1.getText().toString().trim());
		    }
	    }
	});

	CheckBox checkBox1 = (CheckBox) findViewById(R.id.show_details);

	checkBox1.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    showFireDetails(isChecked);

		    if(isChecked)
			m_databaseHelper.writeSetting
			    (null, "fire_show_details", "true");
		    else
			m_databaseHelper.writeSetting
			    (null, "fire_show_details", "false");
		}
	    });
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(Fire.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showFireDetails(boolean isChecked)
    {
	Button button1 = (Button) findViewById(R.id.add_channel);
	Button button2 = (Button) findViewById(R.id.reset_fields);
	View linearLayout1 = findViewById(R.id.auto_fill_layout);
	View gridLayout1 = findViewById(R.id.grid_layout);

	button1.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	button2.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	gridLayout1.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	linearLayout1.setVisibility(isChecked ? View.VISIBLE : View.GONE);
    }

    private void showMemberChatActivity()
    {
	Intent intent = new Intent(Fire.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
	Intent intent = new Intent(Fire.this, Settings.class);

	startActivity(intent);
	finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
	m_databaseHelper.cleanDanglingOutboundQueued();
	m_databaseHelper.cleanDanglingParticipants();
	m_receiver = new FireBroadcastReceiver();
        setContentView(R.layout.activity_fire);
	getSupportActionBar().setTitle("Smoke: Fire");

	if(State.getInstance().isAuthenticated())
	    populateFires();

	CheckBox checkBox1 = (CheckBox) findViewById(R.id.show_details);

	checkBox1.setChecked
	    (m_databaseHelper.readSetting(null, "fire_show_details").
	     equals("true"));
	prepareAutoFill();
	prepareListeners();
	showFireDetails
	    (m_databaseHelper.readSetting(null, "fire_show_details").
	     equals("true"));

	Map<String, FireChannel> map = State.getInstance().fireChannels();

	if(map != null)
	{
	    DisplayMetrics displayMetrics = new DisplayMetrics();
	    ViewGroup viewGroup = (ViewGroup) findViewById(R.id.linear_layout);

	    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

	    int height = (int) (0.6 * displayMetrics.heightPixels);

	    for(Map.Entry<String, FireChannel> entry : map.entrySet())
	    {
		if(entry.getValue() == null)
		    continue;

		ViewGroup parent = (ViewGroup) entry.getValue().view().
		    getParent();

		parent.removeView(entry.getValue().view());
		viewGroup.addView
		    (entry.getValue().view(),
		     new LayoutParams(LayoutParams.WRAP_CONTENT, height));
	    }

	    viewGroup.requestLayout();
	}

	TextView textView1 = (TextView) findViewById(R.id.channel);

	textView1.setFilters(new InputFilter[] {s_Latin1InputFilter});
	textView1.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
			       InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
	textView1 = (TextView) findViewById(R.id.digest);
	textView1.setFilters(new InputFilter[] {s_Latin1InputFilter});
	textView1.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
			       InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
	textView1 = (TextView) findViewById(R.id.name);
	textView1.setText
	    (m_databaseHelper.
	     readSetting(s_cryptography, "fire_user_name").trim());
	textView1 = (TextView) findViewById(R.id.salt);
	textView1.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
			       InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
	textView1.setFilters(new InputFilter[] {s_Latin1InputFilter});
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.fire_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
	int groupId = menuItem.getGroupId();
	int itemId = menuItem.getItemId();

	if(groupId == Menu.NONE)
	{
	    if(itemId == R.id.action_chat)
	    {
		m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
		showChatActivity();
		return true;
	    }
	    else if(itemId == R.id.action_settings)
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
	Miscellaneous.addMembersToMenu(menu, 3, 250);
	return true;
    }

    @Override
    public void onResume()
    {
	super.onResume();

	if(!m_receiverRegistered)
	{
	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction("org.purple.smoke.chat_message");
	    intentFilter.addAction("org.purple.smoke.fire_message");
	    intentFilter.addAction
		("org.purple.smoke.state_participants_populated");
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
