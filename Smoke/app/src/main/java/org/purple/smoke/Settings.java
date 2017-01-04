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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
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
    private Cryptography m_cryptography;
    private Database m_databaseHelper;

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
		    AlertDialog alertDialog = new AlertDialog.Builder
			(Settings.this).create();

		    if(textView1.getText().length() < 16)
			alertDialog.setMessage("Each password must contain at least sixteen characters.");
		    else
			alertDialog.setMessage("The provided passwords are not identical.");

		    alertDialog.setTitle("Error");
		    alertDialog.setButton
			(AlertDialog.BUTTON_NEUTRAL, "Fine",
			 new DialogInterface.OnClickListener()
			 {
			     public void onClick(DialogInterface dialog,
						 int which)
			     {
				 dialog.dismiss();
			     }
			 });

		    alertDialog.show();
		}
		else
		{
		    byte[] encryptionSalt;
		    byte[] macSalt;
		    ProgressDialog dialog = new ProgressDialog(Settings.this);
		    SecretKey encryptionKey;
		    SecretKey macKey;

		    dialog.setCancelable(false);
		    dialog.setIndeterminate(true);
		    dialog.setMessage
			("Generating authentication and encryption keys...");
		    dialog.show();
		    encryptionSalt = Cryptography.randomBytes(32);
		    macSalt = Cryptography.randomBytes(64);

		    try
		    {
			Spinner spinner = (Spinner) findViewById
			    (R.id.iteration_count);
			int iterations = Integer.parseInt
			    (spinner.getSelectedItem().toString());

			encryptionKey = Cryptography.generateEncryptionKey
			    (encryptionSalt,
			     textView1.getText().toString().toCharArray(),
			     iterations);
			macKey = Cryptography.generateMacKey
			    (macSalt,
			     textView1.getText().toString().toCharArray(),
			     iterations);
		    }
		    catch(GeneralSecurityException exception)
		    {
		    }

		    dialog.dismiss();
		}
	    }
	});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

	/*
	** Create smoke.db and its tables.
	*/

	m_databaseHelper = new Database(getApplicationContext());
        setContentView(R.layout.activity_settings);

        Button button3 = (Button) findViewById(R.id.add_neighbor);

        button3.setEnabled(false);
        button3 = (Button) findViewById(R.id.delete_neighbor);
        button3.setEnabled(false);
        button3 = (Button) findViewById(R.id.refresh_neighbors);
        button3.setEnabled(false);

        RadioButton radioButton1 = (RadioButton) findViewById
	    (R.id.neighbors_ipv4);

        radioButton1.setEnabled(false);
        radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv6);
        radioButton1.setEnabled(false);

        Spinner spinner1 = (Spinner) findViewById(R.id.neighbors_transport);
        String array[] = new String[]
	{
	    "TCP", "UDP"
	};

        spinner1.setEnabled(false);

        ArrayAdapter<String> adapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);

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
	spinner1 = (Spinner) findViewById(R.id.iteration_count);
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

        textView1.setEnabled(false);
        textView1.setVisibility(View.GONE);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
        textView1.setEnabled(false);
        textView1.setText("4710");
        textView1 = (TextView) findViewById(R.id.neighbors_ip_address);
        textView1.setEnabled(false);
	textView1 = (TextView) findViewById(R.id.reset_neighbor_fields);
	textView1.setEnabled(false);
	textView1 = (TextView) findViewById(R.id.delete_participant);
	textView1.setEnabled(false);
	textView1 = (TextView) findViewById(R.id.refresh_participants);
	textView1.setEnabled(false);
        textView1 = (TextView) findViewById(R.id.password1);
	textView1.requestFocus();
        textView1.setText("");
        textView1 = (TextView) findViewById(R.id.password2);
        textView1.setText("");
	prepareListeners();
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
            final Intent intent = new Intent(Settings.this, Authenticate.class);

            startActivity(intent);
	    m_databaseHelper.writeSetting
		(m_cryptography, "lastActivity", "Authenticate", false);
            return true;
        }
	else if(id == R.id.action_chat)
	{
            final Intent intent = new Intent(Settings.this, Chat.class);

            startActivity(intent);
	    m_databaseHelper.writeSetting
		(m_cryptography, "lastActivity", "Chat", false);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
