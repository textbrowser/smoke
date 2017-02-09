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
import android.content.DialogInterface;
import android.content.Intent;
import android.database.SQLException;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import javax.crypto.SecretKey;

public class Settings extends AppCompatActivity
{
    private Database m_databaseHelper = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static int s_pkiEncryptionKeySize = 3072;
    private final static int s_pkiSignatureKeySize = 3072;

    private void addNeighbor()
    {
	String ipVersion = "";
	final RadioGroup radioGroup1 = (RadioGroup) findViewById
	    (R.id.neighbors_ipv_radio_group);
	final Spinner spinner1 = (Spinner) findViewById
	    (R.id.neighbors_transport);
	final TextView textView1 = (TextView) findViewById
	    (R.id.neighbors_ip_address);
	final TextView textView2 = (TextView) findViewById
	    (R.id.neighbors_port);
	final TextView textView3 = (TextView) findViewById
	    (R.id.neighbors_scope_id);

	if(radioGroup1.getCheckedRadioButtonId() == R.id.neighbors_ipv4)
	    ipVersion = "IPv4";
	else
	    ipVersion = "IPv6";

	if(!m_databaseHelper.writeNeighbor(s_cryptography,
					   textView1.getText().toString(),
					   textView2.getText().toString(),
					   textView3.getText().toString(),
					   spinner1.getSelectedItem().
					   toString(),
					   ipVersion))
	    Miscellaneous.showErrorDialog(Settings.this,
					  "An error occurred while " +
					  "saving the neighbor information.");
	else
	    populateNeighbors();
    }

    private void enableWidgets(boolean state)
    {
	Button button1 = null;

	button1 = (Button) findViewById(R.id.add_neighbor);
	button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.export);
	button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.save_name);
	button1.setEnabled(state);

	RadioButton radioButton1 = null;

	radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv4);
	radioButton1.setEnabled(state);
	radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv6);
	radioButton1.setEnabled(state);

	Spinner spinner1 = null;

	spinner1 = (Spinner) findViewById(R.id.neighbors_transport);
	spinner1.setEnabled(state);

	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.name);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.neighbors_ip_address);
	textView1.setEnabled(state);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.neighbors_scope_id);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.refresh_neighbors);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.refresh_participants);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.reset_neighbor_fields);
	textView1.setEnabled(state);
    }

    private void populateFancyKeyData()
    {
	StringBuffer stringBuffer = new StringBuffer();
	TextView textView1 = null;

	stringBuffer.append("Chat Encryption Key\n");
	stringBuffer.append
	    (s_cryptography.fancyKeyInformationOutput(s_cryptography.
						      chatEncryptionKeyPair()));
	textView1 = (TextView) findViewById(R.id.chat_encryption_key_data);
	textView1.setText(stringBuffer);
	textView1.setVisibility(View.VISIBLE);
	stringBuffer.delete(0, stringBuffer.length());
	stringBuffer.append("Chat Signature Key\n");
	stringBuffer.append
	    (s_cryptography.fancyKeyInformationOutput(s_cryptography.
						      chatSignatureKeyPair()));
	textView1 = (TextView) findViewById(R.id.chat_signature_key_data);
	textView1.setText(stringBuffer);
	textView1.setVisibility(View.VISIBLE);
    }

    private void populateName()
    {
    }

    private void populateNeighbors()
    {
	ArrayList<NeighborElement> arrayList =
	    m_databaseHelper.readNeighbors(s_cryptography);
	final TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.neighbors);

	tableLayout.removeAllViews();

	if(arrayList != null)
	    for(int i = 0; i < arrayList.size(); i++)
	    {
		TableRow row = new TableRow(Settings.this);
		TableRow.LayoutParams layoutParams = new
		    TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);

		row.setLayoutParams(layoutParams);

		ArrayAdapter<String> arrayAdapter = null;
		Spinner spinner = new Spinner(Settings.this);
		String array[] = new String[]
		{
		    "Connect", "Delete", "Disconnect",
		    "Reset SSL/TLS Credentials"
		};

		arrayAdapter = new ArrayAdapter<>
		    (Settings.this,
		     android.R.layout.simple_spinner_item,
		     array);
		spinner.setAdapter(arrayAdapter);
		spinner.setId(arrayList.get(i).m_oid);
		spinner.setOnItemSelectedListener
		    (new OnItemSelectedListener()
		    {
			@Override
			public void onItemSelected(AdapterView<?> parent,
						   View view,
						   int position,
						   long id)
			{
			    if(position == 1 && // Delete
			       m_databaseHelper.
			       deleteEntry(String.valueOf(parent.getId()),
					   "neighbors"))
				populateNeighbors();

			    parent.setSelection(0);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent)
			{
			}
		    });

		TextView textView = new TextView(Settings.this);

		if(arrayList.get(i).m_status.equals("connected"))
		    textView.setBackgroundColor
			(Color.parseColor("lightgreen"));
		else
		    textView.setBackgroundColor(Color.rgb(240, 128, 128));

		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append(arrayList.get(i).m_remoteIpAddress);
		stringBuffer.append(":");
		stringBuffer.append(arrayList.get(i).m_remotePort);
		stringBuffer.append(":");
		stringBuffer.append(arrayList.get(i).m_transport);
		textView.setText(stringBuffer);
		row.addView(spinner);
		row.addView(textView);
		tableLayout.addView(row, i);
	    }
    }

    private void prepareListeners()
    {
	final Button button1 = (Button) findViewById
	    (R.id.add_neighbor);

	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		addNeighbor();
	    }
        });

	final Button button2 = (Button) findViewById
	    (R.id.refresh_neighbors);

	button2.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		populateNeighbors();
	    }
        });

	final Button button3 = (Button) findViewById
	    (R.id.reset_neighbor_fields);

        button3.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
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

	final DialogInterface.OnCancelListener listener =
	    new DialogInterface.OnCancelListener()
	{
	    public void onCancel(DialogInterface dialog)
	    {
		m_databaseHelper.reset();
		prepareCredentials();
	    }
	};

        final Button button4 = (Button) findViewById(R.id.set_password);

        button4.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		final TextView textView1 = (TextView) findViewById
		    (R.id.password1);
		final TextView textView2 = (TextView) findViewById
		    (R.id.password2);

		textView1.setSelectAllOnFocus(true);
		textView2.setSelectAllOnFocus(true);

		if(textView1.getText().length() < 16 ||
		   !textView1.getText().toString().
		   equals(textView2.getText().toString()))
		{
		    String error = "";

		    if(textView1.getText().length() < 16)
			error = "Each password must contain " +
			    "at least sixteen characters.";
		    else
			error = "The provided passwords are not identical.";

		    Miscellaneous.showErrorDialog(Settings.this, error);
		    textView1.requestFocus();
		    return;
		}

		if(State.getInstance().isAuthenticated())
		    Miscellaneous.
			showPromptDialog(Settings.this,
					 listener,
					 "Are you sure that you " +
					 "wish to create new " +
					 "credentials? Existing " +
					 "database values will be " +
					 "removed.");
		else
		    prepareCredentials();
	    }
	});
    }

    private void prepareCredentials()
    {
	final ProgressDialog dialog = new ProgressDialog(Settings.this);
	final Spinner spinner1 = (Spinner) findViewById(R.id.iteration_count);
	final Spinner spinner2 = (Spinner) findViewById
	    (R.id.pki_encryption_algorithm);
	final Spinner spinner3 = (Spinner) findViewById
	    (R.id.pki_signature_algorithm);
	final TextView textView1 = (TextView) findViewById
	    (R.id.password1);
	final TextView textView2 = (TextView) findViewById
	    (R.id.password2);
	int iterationCount = Integer.parseInt
	    (spinner1.getSelectedItem().toString());

	dialog.setCancelable(false);
	dialog.setIndeterminate(true);
	dialog.setMessage("Generating confidential material. " +
			  "Please be patient...");
	dialog.show();

	class SingleShot implements Runnable
	{
	    private String m_encryptionAlgorithm = "";
	    private String m_password = "";
	    private String m_signatureAlgorithm = "";
	    private boolean m_error = false;
	    private int m_iterationCount = 1000;

	    SingleShot(String encryptionAlgorithm,
		       String password,
		       String signatureAlgorithm,
		       int iterationCount)
	    {
		m_encryptionAlgorithm = encryptionAlgorithm;
		m_iterationCount = iterationCount;
		m_password = password;
		m_signatureAlgorithm = signatureAlgorithm;
	    }

	    @Override
	    public void run()
	    {
		KeyPair chatEncryptionKeyPair = null;
		KeyPair chatSignatureKeyPair = null;
		SecretKey encryptionKey = null;
		SecretKey macKey = null;
		byte encryptionSalt[] = null;
		byte macSalt[] = null;

		encryptionSalt = Cryptography.randomBytes(32);
		macSalt = Cryptography.randomBytes(64);

		try
		{
		    chatEncryptionKeyPair = Cryptography.
			generatePrivatePublicKeyPair
			(m_encryptionAlgorithm, s_pkiEncryptionKeySize);
		    chatSignatureKeyPair = Cryptography.
			generatePrivatePublicKeyPair
			(m_signatureAlgorithm, s_pkiSignatureKeySize);
		    encryptionKey = Cryptography.
			generateEncryptionKey
			(encryptionSalt,
			 m_password.toCharArray(),
			 m_iterationCount);
		    macKey = Cryptography.generateMacKey
			(macSalt,
			 m_password.toCharArray(),
			 m_iterationCount);

		    /*
		    ** Prepare the Cryptography object's data.
		    */

		    s_cryptography.setChatEncryptionKeyPair
			(chatEncryptionKeyPair);
		    s_cryptography.setChatSignatureKeyPair
			(chatSignatureKeyPair);
		    s_cryptography.setEncryptionKey
			(encryptionKey);
		    s_cryptography.setMacKey(macKey);

		    /*
		    ** Record the data.
		    */

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
		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "pki_chat_encryption_private_key",
			 Base64.
			 encodeToString(chatEncryptionKeyPair.
					getPrivate().
					getEncoded(),
					Base64.DEFAULT));
		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "pki_chat_encryption_public_key",
			 Base64.
			 encodeToString(chatEncryptionKeyPair.
					getPublic().
					getEncoded(),
					Base64.DEFAULT));
		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "pki_chat_signature_algorithm",
			 m_signatureAlgorithm);
		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "pki_chat_signature_private_key",
			 Base64.encodeToString(chatSignatureKeyPair.
					       getPrivate().
					       getEncoded(),
					       Base64.DEFAULT));
		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "pki_chat_signature_public_key",
			 Base64.encodeToString(chatSignatureKeyPair.
					       getPublic().
					       getEncoded(),
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
		    }
		    else
		    {
			m_error = true;
			s_cryptography.reset();
		    }
		}
		catch(InvalidKeySpecException |
		      NoSuchAlgorithmException |
		      NumberFormatException |
		      SQLException exception)
		{
		    m_error = true;
		    s_cryptography.reset();
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
				 "generating the confidential " +
				 "data.");
			else
			{
			    Settings.this.enableWidgets(true);
			    State.getInstance().setAuthenticated
				(true);
			    textView1.requestFocus();
			    textView1.setText("");
			    textView2.setText("");
			    populateFancyKeyData();
			    populateNeighbors();
			    startKernel();
			}
		    }
		});

		m_password = "";
	    }
	}

	Thread thread = new Thread
	    (new SingleShot(spinner2.getSelectedItem().toString(),
			    textView1.getText().toString(),
			    spinner3.getSelectedItem().toString(),
			    iterationCount));

	thread.start();
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

    private void startKernel()
    {
	Kernel.getInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	m_databaseHelper = Database.getInstance(getApplicationContext());
	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

	boolean isAuthenticated = State.getInstance().isAuthenticated();
        Button button1 = (Button) findViewById(R.id.add_neighbor);

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

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);

        spinner1.setAdapter(arrayAdapter);
	array = new String[]
	{
	    "1000", "2500", "5000", "7500", "10000", "12500",
	    "15000", "17500", "20000", "25000", "30000", "35000",
	    "40000", "45000", "50000", "55000", "60000", "65000",
	    "70000", "100000"
	};
	arrayAdapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);

	int index = arrayAdapter.getPosition
	    (m_databaseHelper.readSetting(null, "iterationCount"));

	spinner1 = (Spinner) findViewById(R.id.iteration_count);
	spinner1.setAdapter(arrayAdapter);
	array = new String[]
	{
	    "RSA"
	};
	arrayAdapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	spinner1 = (Spinner) findViewById(R.id.pki_encryption_algorithm);
	spinner1.setAdapter(arrayAdapter);
	array = new String[]
	{
	    "DSA", "RSA"
	};
	arrayAdapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	spinner1 = (Spinner) findViewById(R.id.pki_signature_algorithm);
	spinner1.setAdapter(arrayAdapter);

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

	/*
	** Enable widgets.
	*/

	button1 = (Button) findViewById(R.id.export);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.save_name);
	button1.setEnabled(isAuthenticated);

	TextView textView1;

	textView1 = (TextView) findViewById(R.id.chat_encryption_key_data);
	textView1.setVisibility
	    (s_cryptography.chatEncryptionKeyPair() == null ?
	     View.GONE : View.VISIBLE);
	textView1 = (TextView) findViewById(R.id.chat_signature_key_data);
	textView1.setVisibility
	    (s_cryptography.chatSignatureKeyPair() == null ?
	     View.GONE : View.VISIBLE);
	textView1 = (TextView) findViewById(R.id.name);
	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.neighbors_scope_id);
        textView1.setEnabled(isAuthenticated);
        textView1.setVisibility(View.GONE);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
        textView1.setEnabled(isAuthenticated);
        textView1.setText("4710");
        textView1 = (TextView) findViewById(R.id.neighbors_ip_address);
        textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.reset_neighbor_fields);
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

	if(!State.getInstance().isAuthenticated())
	    if(m_databaseHelper.accountPrepared())
		showAuthenticateActivity();

	spinner1 = (Spinner) findViewById(R.id.pki_signature_algorithm);

	if(spinner1.getAdapter().getCount() > 1)
	    spinner1.setSelection(1); // RSA

	if(isAuthenticated)
	{
	    populateFancyKeyData();
	    populateName();
	    populateNeighbors();
	}
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

	if(id == R.id.action_chat)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
	    showChatActivity();
            return true;
        }
	else if(id == R.id.action_exit)
	{
	    State.getInstance().setFinished(true);
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
