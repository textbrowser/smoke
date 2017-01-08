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
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import java.security.GeneralSecurityException;
import javax.crypto.SecretKey;

public class Settings extends AppCompatActivity
{
    private Cryptography m_cryptography = new Cryptography();
    private Database m_databaseHelper = null;
    private final State m_state = State.getInstance();

    private void prepareListeners()
    {
        final Button button1 = (Button) findViewById
	    (R.id.reset_neighbor_fields);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View v)
	    {
		final RadioButton radioButton1 = (RadioButton) findViewById
		    (R.id.neighbors_ipv4);
		final Spinner spinner1 = (Spinner) findViewById
		    (R.id.neighbors_transport);
		final TextView textView1 = (TextView) findViewById
		    (R.id.neighbors_ip_address);
		final TextView textView2 = (TextView) findViewById
		    (R.id.neighbors_port);
		final TextView textView3 = (TextView) findViewById
		    (R.id.neighbors_scope_id);

		radioButton1.setChecked(true);
		spinner1.setSelection(0);
		textView1.setText("");
		textView2.setText("4710");
		textView3.setText("");
		textView1.requestFocus();
	    }
	});

        final Button button2 = (Button) findViewById(R.id.set_password);

        button2.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View v)
	    {
		final TextView textView1 = (TextView) findViewById
		    (R.id.password1);
		final TextView textView2 = (TextView) findViewById
		    (R.id.password2);

		if(!textView1.getText().toString().
		   equals(textView2.getText().toString()) ||
		   textView1.getText().length() < 16)
		{
		    String error = "";

		    if(textView1.getText().length() < 16)
			error = "Each password must contain " +
			    "at least sixteen characters.";
		    else
			error = "The provided passwords are not identical.";

		    Miscellaneous.showErrorDialog(Settings.this, error);
		}
		else
		{
		    final ProgressDialog dialog = new ProgressDialog
			(Settings.this);
		    final Spinner spinner = (Spinner) findViewById
			(R.id.iteration_count);
		    int iterationCount = Integer.parseInt
			(spinner.getSelectedItem().toString());

		    dialog.setCancelable(false);
		    dialog.setIndeterminate(true);
		    dialog.setMessage
			("Generating authentication and encryption " +
			 "keys. Please be patient...");
		    dialog.show();

		    class SingleShot implements Runnable
		    {
			private String m_password = "";
			private boolean m_error = false;
			private int m_iterationCount = 1000;

			SingleShot(String password, int iterationCount)
			{
			    m_iterationCount = iterationCount;
			    m_password = password;
			}

			@Override
			public void run()
			{
			    SecretKey encryptionKey = null;
			    SecretKey macKey = null;
			    byte encryptionSalt[] = null;
			    byte macSalt[] = null;

			    encryptionSalt = Cryptography.randomBytes(32);
			    macSalt = Cryptography.randomBytes(64);

			    try
			    {
				encryptionKey = Cryptography.
				    generateEncryptionKey
				    (encryptionSalt,
				     m_password.toCharArray(),
				     m_iterationCount);
				macKey = Cryptography.generateMacKey
				    (macSalt,
				     m_password.toCharArray(),
				     m_iterationCount);
				m_databaseHelper.writeSetting
				    (null,
				     "encryptionSalt",
				     Base64.encodeToString(encryptionSalt,
							   Base64.DEFAULT));
				m_databaseHelper.writeSetting
				    (null,
				     "iterationCount",
				     String.valueOf(m_iterationCount));
				m_databaseHelper.writeSetting
				    (null,
				     "macSalt",
				     Base64.encodeToString(macSalt,
							   Base64.DEFAULT));

				byte saltedPassword[] = Cryptography.
				    sha512(m_password.getBytes(),
					   encryptionSalt,
					   macSalt);

				if(saltedPassword != null)
				{
				    m_databaseHelper.writeSetting
					(null,
					 "saltedPassword",
					 Base64.encodeToString(saltedPassword,
							       Base64.DEFAULT));
				    m_state.setEncryptionKey(encryptionKey);
				    m_state.setMacKey(macKey);
				}
				else
				    m_error = true;
			    }
			    catch(GeneralSecurityException |
				  NumberFormatException exception)
			    {
				m_error = true;
			    }

			    Settings.this.runOnUiThread(new Runnable()
			    {
				public void run()
				{
				    dialog.dismiss();

				    if(m_error)
					Miscellaneous.showErrorDialog
					    (Settings.this,
					     "An error occurred while " +
					     "generating the secret keys.");
				}
			    });

			    encryptionKey = null;
			    m_password = "";
			    macKey = null;
			}
		    }

		    Thread thread = new Thread
			(new SingleShot(textView1.getText().toString(),
					iterationCount));

		    thread.start();
		}
	    }
	});
    }

    private void showAuthenticateActivity()
    {
	final Intent intent = new Intent(Settings.this, Authenticate.class);

	startActivity(intent);
    }

    private void showChatActivity()
    {
	final Intent intent = new Intent(Settings.this, Chat.class);

	startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
        setContentView(R.layout.activity_settings);

	boolean isAuthenticated = State.getInstance().isAuthenticated();
        Button button1 = (Button) findViewById(R.id.add_neighbor);

        button1.setEnabled(isAuthenticated);
        button1 = (Button) findViewById(R.id.delete_neighbor);
        button1.setEnabled(isAuthenticated);
        button1 = (Button) findViewById(R.id.refresh_neighbors);
        button1.setEnabled(isAuthenticated);

        RadioButton radioButton1 = (RadioButton) findViewById
	    (R.id.neighbors_ipv4);

        radioButton1.setEnabled(isAuthenticated);
        radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv6);
        radioButton1.setEnabled(isAuthenticated);

        Spinner spinner1 = (Spinner) findViewById(R.id.neighbors_transport);
        String array[] = new String[]
	{
	    "TCP", "UDP"
	};

        spinner1.setEnabled(isAuthenticated);

        ArrayAdapter<String> adapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	int index = -1;

        spinner1.setAdapter(adapter);
	array = new String[]
	{
	    "1000", "2500", "5000", "7500", "10000", "12500",
	    "15000", "17500", "20000", "25000", "30000", "35000",
	    "40000", "45000", "50000", "55000", "60000", "65000",
	    "70000", "100000"
	};
	adapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	index = adapter.getPosition
	    (m_databaseHelper.readSetting(null, "iterationCount"));
	spinner1 = (Spinner) findViewById(R.id.iteration_count);
	spinner1.setAdapter(adapter);
	array = new String[]
	{
	    "RSA"
	};
	adapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	spinner1 = (Spinner) findViewById(R.id.pki_encryption_algorithm);
	spinner1.setAdapter(adapter);
	array = new String[]
	{
	    "RSA"
	};
	adapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	spinner1 = (Spinner) findViewById(R.id.pki_signature_algorithm);
	spinner1.setAdapter(adapter);

        final RadioGroup radioGroup1 = (RadioGroup) findViewById
	    (R.id.neighbors_ipv_radio_group);

        radioGroup1.setOnCheckedChangeListener
	    (new RadioGroup.OnCheckedChangeListener()
	{
	    public void onCheckedChanged(RadioGroup group,
					 int checkedId)
	    {
		final TextView textView1 = (TextView) findViewById
		    (R.id.neighbors_scope_id);

		if(checkedId == R.id.neighbors_ipv4)
		{
		    textView1.setText("");
		    textView1.setVisibility(View.GONE);
		}
		else
		    textView1.setVisibility(View.VISIBLE);
	    }
	});

        TextView textView1 = (TextView) findViewById(R.id.neighbors_scope_id);

        textView1.setEnabled(isAuthenticated);
        textView1.setVisibility(View.GONE);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
        textView1.setEnabled(isAuthenticated);
        textView1.setText("4710");
        textView1 = (TextView) findViewById(R.id.neighbors_ip_address);
        textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.reset_neighbor_fields);
	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.delete_participant);
	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.refresh_participants);
	textView1.setEnabled(isAuthenticated);
        textView1 = (TextView) findViewById(R.id.password1);
	textView1.requestFocus();
        textView1.setText("");
        textView1 = (TextView) findViewById(R.id.password2);
        textView1.setText("");
	prepareListeners();

	/*
	** Restore some settings.
	*/

	spinner1 = (Spinner) findViewById(R.id.iteration_count);

	if(index >= 0)
	    spinner1.setSelection(index);
	else
	    spinner1.setSelection(0);
    }

    @Override
    protected void onDestroy()
    {
	super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if(id == R.id.action_authenticate)
	{
	    showAuthenticateActivity();
	    m_databaseHelper.writeSetting
		(null, "lastActivity", "Authenticate");
	    return true;
	}
	else if(id == R.id.action_chat)
	{
	    showChatActivity();
	    m_databaseHelper.writeSetting
		(null, "lastActivity", "Chat");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
