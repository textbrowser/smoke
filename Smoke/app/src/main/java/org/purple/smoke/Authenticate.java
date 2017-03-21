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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKey;

public class Authenticate extends AppCompatActivity
{
    private Database m_databaseHelper = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();

    private void prepareListeners()
    {
        final Button button1 = (Button) findViewById
	    (R.id.authenticate);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		byte encryptionSalt[] = null;
		byte macSalt[] = null;
		byte saltedPassword[] = null;
		final TextView textView1 = (TextView)
		    findViewById(R.id.password);

		textView1.setSelectAllOnFocus(true);
		encryptionSalt = Base64.decode
		    (m_databaseHelper.
		     readSetting(null, "encryptionSalt").getBytes(),
		     Base64.DEFAULT);
		macSalt = Base64.decode
		    (m_databaseHelper.readSetting(null, "macSalt").getBytes(),
		     Base64.DEFAULT);
		saltedPassword = Cryptography.sha512
		    (textView1.getText().toString().getBytes(),
		     encryptionSalt,
		     macSalt);

		if(saltedPassword == null ||
		   !Cryptography.
		   memcmp(m_databaseHelper.readSetting(null,
						       "saltedPassword").
			  getBytes(),
			  Base64.encode(saltedPassword, Base64.DEFAULT)))
		{
		    Miscellaneous.showErrorDialog(Authenticate.this,
						  "Incorrect password.");
		    textView1.requestFocus();
		}
		else
		{
		    final ProgressDialog dialog = new ProgressDialog
			(Authenticate.this);
		    int iterationCount = 1000;

		    try
		    {
			iterationCount = Integer.parseInt
			    (m_databaseHelper.
			     readSetting(null, "iterationCount"));
		    }
		    catch(Exception exception)
		    {
			iterationCount = 1000;
		    }

		    dialog.setCancelable(false);
		    dialog.setIndeterminate(true);
		    dialog.setMessage
			("Generating confidential data. Please be patient...");
		    dialog.show();

		    class SingleShot implements Runnable
		    {
			private String m_password = "";
			private boolean m_error = false;
			private byte m_encryptionSalt[] = null;
			private byte m_macSalt[] = null;
			private int m_iterationCount = 1000;

			SingleShot(String password,
				   byte encryptionSalt[],
				   byte macSalt[],
				   int iterationCount)
			{
			    m_encryptionSalt = encryptionSalt;
			    m_iterationCount = iterationCount;
			    m_macSalt = macSalt;
			    m_password = password;
			}

			@Override
			public void run()
			{
			    SecretKey encryptionKey = null;
			    SecretKey macKey = null;

			    try
			    {
				encryptionKey = Cryptography.
				    generateEncryptionKey
				    (m_encryptionSalt,
				     m_password.toCharArray(),
				     m_iterationCount);
				macKey = Cryptography.generateMacKey
				    (m_macSalt,
				     m_password.toCharArray(),
				     m_iterationCount);

				if(encryptionKey != null && macKey != null)
				{
				    s_cryptography.setEncryptionKey
					(encryptionKey);
				    s_cryptography.setMacKey(macKey);

				    byte privateBytes[] = Base64.decode
					(m_databaseHelper.
					 readSetting(s_cryptography,
						     "pki_chat_encryption_" +
						     "private_key").
					 getBytes(), Base64.DEFAULT);
				    byte publicBytes[] = Base64.decode
					(m_databaseHelper.
					 readSetting(s_cryptography,
						     "pki_chat_encryption_" +
						     "public_key").
					 getBytes(), Base64.DEFAULT);

				    s_cryptography.setChatEncryptionKeyPair
					("RSA", privateBytes, publicBytes);

				    String algorithm = m_databaseHelper.
					readSetting(s_cryptography,
						    "pki_chat_signature_" +
						    "algorithm");

				    privateBytes = Base64.decode
					(m_databaseHelper.
					 readSetting(s_cryptography,
						     "pki_chat_signature_" +
						     "private_key").
					 getBytes(), Base64.DEFAULT);
				    publicBytes = Base64.decode
					(m_databaseHelper.
					 readSetting(s_cryptography,
						     "pki_chat_signature_" +
						     "public_key").
					 getBytes(), Base64.DEFAULT);
				    s_cryptography.setChatSignatureKeyPair
					(algorithm, privateBytes, publicBytes);

				    if(s_cryptography.
				       chatEncryptionKeyPair() == null ||
				       s_cryptography.
				       chatSignatureKeyPair() == null)
				    {
					m_error = true;
					s_cryptography.reset();
				    }
				}
				else
				{
				    m_error = true;
				    s_cryptography.reset();
				}
			    }
			    catch(Exception exception)
			    {
				m_error = true;
				s_cryptography.reset();
			    }

			    Authenticate.this.runOnUiThread(new Runnable()
			    {
				@Override
				public void run()
				{
				    dialog.dismiss();

				    if(m_error)
					Miscellaneous.showErrorDialog
					    (Authenticate.this,
					     "An error occurred while " +
					     "generating the confidential " +
					     "data.");
				    else
				    {
					Kernel.getInstance();
					State.getInstance().
					    setAuthenticated(true);

					/*
					** Disable some widgets.
					*/

					button1.setEnabled(false);
					textView1.setEnabled(false);
					textView1.setText("");

					String str = m_databaseHelper.
					    readSetting
					    (null, "lastActivity");

					if(str.equals("Chat"))
					    showChatActivity();
					else if(str.equals("Settings"))
					    showSettingsActivity();
				    }
				}
			    });

			    m_password = "";
			}
		    }

		    Thread thread = new Thread
			(new SingleShot(textView1.getText().toString(),
					encryptionSalt,
					macSalt,
					iterationCount));

		    thread.start();
		}
	    }
	});

	final DialogInterface.OnCancelListener listener1 =
	    new DialogInterface.OnCancelListener()
	{
	    public void onCancel(DialogInterface dialog)
	    {
		State.getInstance().reset();
		m_databaseHelper.resetAndDrop();
		s_cryptography.reset();

		final Intent intent = new Intent
		    (Authenticate.this, Settings.class);

		finish();
		startActivity(intent);
	    }
	};

	final Button button2 = (Button) findViewById(R.id.reset);

	button2.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		Miscellaneous.showPromptDialog(Authenticate.this,
					       listener1,
					       "Are you sure that you " +
					       "wish to reset Smoke? All " +
					       "data will be lost.");
	    }
	});
    }

    private void showChatActivity()
    {
	final Intent intent = new Intent(Authenticate.this, Chat.class);

	startActivity(intent);
    }

    private void showSettingsActivity()
    {
	final Intent intent = new Intent(Authenticate.this, Settings.class);

	startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
        setContentView(R.layout.activity_authenticate);
	prepareListeners();

	boolean isAuthenticated = State.getInstance().isAuthenticated();
	final Button button1 = (Button) findViewById(R.id.authenticate);

	button1.setEnabled(!isAuthenticated);

	final TextView textView1 = (TextView) findViewById(R.id.password);

	textView1.setEnabled(!isAuthenticated);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.

        getMenuInflater().inflate(R.menu.authenticate_menu, menu);
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

	menu.findItem(R.id.action_chat).setEnabled(isAuthenticated);
	menu.findItem(R.id.action_settings).setEnabled(isAuthenticated);
	return true;
    }
}
