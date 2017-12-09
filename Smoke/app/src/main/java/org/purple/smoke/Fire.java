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

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Hashtable;

public class Fire extends AppCompatActivity
{
    private Database m_databaseHelper = null;
    private final Hashtable<String, Integer> m_fireHash = new Hashtable<> ();
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();

    private void populateFires()
    {
	ArrayList<FireElement> arrayList =
	    m_databaseHelper.readFires(s_cryptography);
	Spinner spinner = (Spinner) findViewById(R.id.fires);

	m_fireHash.clear();
	spinner.setAdapter(null);

	if(arrayList == null || arrayList.size() == 0)
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
			(Fire.this,
					android.R.layout.simple_spinner_item,
					array);
	spinner.setAdapter(arrayAdapter);
	arrayList.clear();
    }

    private void prepareListeners()
    {
	Button button1 = null;

	button1 = (Button) findViewById(R.id.add_channel);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		TextView textView1 = (TextView) findViewById(R.id.name);

		if(textView1.getText().toString().trim().isEmpty())
		{
		    Miscellaneous.showErrorDialog
			(Fire.this, "Please complete the Name field.");
		    textView1.requestFocus();
		}
		else
		{
		    textView1 = (TextView) findViewById(R.id.channel);

		    final String channel = textView1.getText().toString().
			trim();

		    textView1 = (TextView) findViewById(R.id.salt);

		    final String salt = textView1.getText().toString().trim();
		    final ProgressDialog dialog = new ProgressDialog
			(Fire.this);

		    dialog.setCancelable(false);
		    dialog.setIndeterminate(true);
		    dialog.setMessage
			("Generating key material. Please be patient " +
			 "and do not rotate the device while the process " +
			 "executes.");
		    dialog.show();

		    class SingleShot implements Runnable
		    {
			private byte m_bytes[] = null;

			SingleShot()
			{
			}

			@Override
			public void run()
			{
			    m_bytes = s_cryptography.generateFireKey
				(channel, salt);

			    Fire.this.runOnUiThread(new Runnable()
			    {
				@Override
				public void run()
				{
				    dialog.dismiss();

				    if(m_bytes != null)
				    {
					TextView textView1 =
					    (TextView) findViewById
					    (R.id.digest);
					TextView textView2 =
					    (TextView) findViewById
					    (R.id.name);

					m_databaseHelper.saveFireChannel
					    (s_cryptography,
					     textView1.getText().toString(),
					     textView2.getText().toString(),
					     m_bytes);
					populateFires();
				    }
				}
			    });
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
		Spinner spinner = (Spinner) findViewById(R.id.fires);

		if(spinner.getAdapter() != null &&
		   spinner.getAdapter().getCount() > 0)
		    if(m_databaseHelper.
		       deleteEntry(String.valueOf(m_fireHash.
						  get(spinner.
						      getSelectedItem().
						      toString())),
				   "fire"))
			populateFires();
	    }
	});

	button1 = (Button) findViewById(R.id.reset_fields);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		TextView textView1 = (TextView) findViewById(R.id.channel);

		textView1.setText("");
		textView1 = (TextView) findViewById(R.id.digest);
		textView1.setText("");
		textView1 = (TextView) findViewById(R.id.name);
		textView1.requestFocus();
		textView1.setText("");
		textView1 = (TextView) findViewById(R.id.salt);
		textView1.setText("");
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
	View linearLayout1 = findViewById(R.id.channel_layout);
	View linearLayout2 = findViewById(R.id.digest_layout);
	View linearLayout3 = findViewById(R.id.name_layout);
	View linearLayout4 = findViewById(R.id.salt_layout);

	button1.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	button2.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	linearLayout1.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	linearLayout2.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	linearLayout3.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	linearLayout4.setVisibility(isChecked ? View.VISIBLE : View.GONE);

	TextView textView1 = (TextView) findViewById(R.id.name);

	textView1.requestFocus();
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
        setContentView(R.layout.activity_fire);

	if(State.getInstance().isAuthenticated())
	    populateFires();

	CheckBox checkBox1 = (CheckBox) findViewById(R.id.show_details);

	checkBox1.setChecked
	    (m_databaseHelper.readSetting(null, "fire_show_details").
	     equals("true"));
	prepareListeners();
	showFireDetails
	    (m_databaseHelper.readSetting(null, "fire_show_details").
	     equals("true"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.fire_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
	int id = item.getItemId();

        if(id == R.id.action_chat)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
	    showChatActivity();
            return true;
        }
        else if(id == R.id.action_settings)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Settings");
	    showSettingsActivity();
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
}
