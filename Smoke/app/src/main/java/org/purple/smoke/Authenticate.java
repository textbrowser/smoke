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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Arrays;
import javax.crypto.SecretKey;

public class Authenticate extends AppCompatActivity
{
    private Database m_databaseHelper = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();

    private void prepareListeners()
    {
        final Button button1 = (Button) findViewById(R.id.authenticate);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Authenticate.this.isFinishing())
		    return;

		byte encryptionSalt[] = Base64.decode
		    (m_databaseHelper.
		     readSetting(null, "encryptionSalt").getBytes(),
		     Base64.DEFAULT);
		final TextView textView1 = (TextView) findViewById
		    (R.id.password);

		textView1.setSelectAllOnFocus(true);

		if(encryptionSalt == null)
		{
		    Miscellaneous.showErrorDialog
			(Authenticate.this,
			 "The encryption salt value is zero. System failure.");
		    textView1.requestFocus();
		    return;
		}

		byte macSalt[] = Base64.decode
		    (m_databaseHelper.readSetting(null, "macSalt").getBytes(),
		     Base64.DEFAULT);

		if(macSalt == null)
		{
		    Miscellaneous.showErrorDialog
			(Authenticate.this,
			 "The mac salt value is zero. System failure.");
		    textView1.requestFocus();
		    return;
		}

		byte saltedPassword[] = Cryptography.sha512
		    (textView1.getText().toString().getBytes(),
		     encryptionSalt,
		     macSalt);

		if(saltedPassword == null)
		{
		    Miscellaneous.showErrorDialog
			(Authenticate.this,
			 "An error occurred with sha512(). System failure.");
		    textView1.requestFocus();
		    return;
		}

		int iterationCount = 1000;

		try
		{
		    iterationCount = Integer.parseInt
			(m_databaseHelper.
			 readSetting(null, "iterationCount"));
		}
		catch(Exception exception)
		{
		    iterationCount = -1;
		}

		if(iterationCount == -1)
		{
		    Miscellaneous.showErrorDialog
			(Authenticate.this,
			 "Invalid iteration count. System failure.");
		    textView1.requestFocus();
		    return;
		}

		if(!Cryptography.memcmp(m_databaseHelper.
					readSetting(null,"saltedPassword").
					getBytes(),
					Base64.encode(saltedPassword,
						      Base64.DEFAULT)))
		{
		    Miscellaneous.showErrorDialog
			(Authenticate.this,
			 "Incorrect password. Please try again.");
		    textView1.setText("");
		    textView1.requestFocus();
		    return;
		}

		final ProgressBar bar = (ProgressBar) findViewById
		    (R.id.progress_bar);

		bar.setIndeterminate(true);
		bar.setVisibility(ProgressBar.VISIBLE);
		getWindow().setFlags
		    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
		     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
		Miscellaneous.enableChildren
		    (findViewById(R.id.relative_layout), false);

		class SingleShot implements Runnable
		{
		    private String m_error = "";
		    private String m_password = "";
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
			    encryptionKey = Cryptography.generateEncryptionKey
				(m_encryptionSalt,
				 m_password.toCharArray(),
				 m_iterationCount);
			    macKey = Cryptography.generateMacKey
				(m_macSalt,
				 m_password.toCharArray(),
				 m_iterationCount);

			    if(encryptionKey != null && macKey != null)
			    {
				s_cryptography.setEncryptionKey(encryptionKey);
				s_cryptography.setMacKey(macKey);

				String algorithm = "";
				byte identity[] = Base64.decode
				    (m_databaseHelper.
				     readSetting(s_cryptography,
						 "identity").
				     getBytes(), Base64.DEFAULT);
				byte ozoneKeyStream[] = Base64.decode
				    (m_databaseHelper.
				     readSetting(s_cryptography,
						 "ozone_address_stream").
						 getBytes(), Base64.DEFAULT);
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

				algorithm = m_databaseHelper.
				    readSetting(s_cryptography,
						"pki_chat_encryption_" +
						"algorithm");
				s_cryptography.setIdentity(identity);
				s_cryptography.setChatEncryptionPublicKeyPair
				    (algorithm, privateBytes, publicBytes);
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
				algorithm = m_databaseHelper.
				    readSetting(s_cryptography,
						"pki_chat_signature_" +
						"algorithm");
				s_cryptography.setChatSignaturePublicKeyPair
				    (algorithm, privateBytes, publicBytes);

				if(ozoneKeyStream != null &&
				   ozoneKeyStream.length == 96)
				{
				    s_cryptography.setOzoneEncryptionKey
					(Arrays.copyOfRange(ozoneKeyStream,
							    0,
							    32));
				    s_cryptography.setOzoneMacKey
					(Arrays.copyOfRange(ozoneKeyStream,
							    32,
							    ozoneKeyStream.
							    length));
				}
				else
				{
				    s_cryptography.setOzoneEncryptionKey(null);
				    s_cryptography.setOzoneMacKey(null);
				}

				boolean e1 = s_cryptography.
				    prepareSipHashIds();
				boolean e2 = s_cryptography.
				    prepareSipHashKeys();

				if(!e1 || !e2 ||
				   s_cryptography.
				   chatEncryptionKeyPair() == null ||
				   s_cryptography.
				   chatSignatureKeyPair() == null ||
				   s_cryptography.identity() == null)
				{
				    if(!e1)
					m_error +=
					    "prepareSipHashIds() failure ";

				    if(!e2)
					m_error += "prepareSipHashKeys() " +
					    "failure ";

				    if(s_cryptography.
				       chatEncryptionKeyPair() == null)
					m_error += "chatEncryptionKeyPair() " +
					    "returned zero ";

				    if(s_cryptography.
				       chatSignatureKeyPair() == null)
					m_error += "chatSignatureKeyPair() " +
					    "return zero ";

				    m_error = m_error.trim();
				    s_cryptography.reset();
				}
			    }
			    else
			    {
				if(encryptionKey == null)
				    m_error = "generateEncryptionKey() " +
					"failure";
				else
				    m_error = "generateMacKey() failure";

				s_cryptography.reset();
			    }
			}
			catch(Exception exception)
			{
			    m_error = exception.getMessage().toLowerCase().
				trim();
			    s_cryptography.reset();
			}

			Authenticate.this.runOnUiThread(new Runnable()
			{
			    @Override
			    public void run()
			    {
				try
				{
				    bar.setVisibility(ProgressBar.INVISIBLE);
				    getWindow().clearFlags
					(WindowManager.LayoutParams.
					 FLAG_NOT_TOUCHABLE);
				    Miscellaneous.enableChildren
					(findViewById(R.id.relative_layout),
					 true);

				    if(!m_error.isEmpty())
					Miscellaneous.showErrorDialog
					    (Authenticate.this,
					     "An error (" + m_error +
					     ") occurred while " +
					     "generating the confidential " +
					     "data.");
				    else
				    {
					m_databaseHelper.cleanNeighborStatistics
					    (s_cryptography);
					Kernel.getInstance();
					State.getInstance().
					    populateParticipants();
					State.getInstance().
					    setAuthenticated(true);

					/*
					** Disable some widgets.
					*/

					button1.setEnabled(false);
					textView1.setEnabled(false);
					textView1.setText("");

					String str = m_databaseHelper.
					    readSetting(null, "lastActivity");

					switch(str)
					{
					case "Chat":
					    showChatActivity();
					    break;
					case "Fire":
					    showFireActivity();
					    break;
					case "MemberChat":
					    String oid =
						m_databaseHelper.
						readSetting(s_cryptography,
							    "member_chat_oid");
					    String sipHashId =
						m_databaseHelper.
						readSetting(s_cryptography,
							    "member_chat_" +
							    "siphash_id");

					    if(m_databaseHelper.
					       containsParticipant
					       (s_cryptography,
						sipHashId))
					    {
						State.getInstance().setString
						    ("member_chat_oid", oid);
						State.getInstance().setString
						    ("member_chat_siphash_id",
						     sipHashId);
						showMemberChatActivity();
					    }
					    else
					    {
						m_databaseHelper.writeSetting
						    (s_cryptography,
						     "member_chat_oid",
						     "");
						m_databaseHelper.writeSetting
						    (s_cryptography,
						     "member_chat_siphash_id",
						     "");
					    }

					    break;
					case "Settings":
					    showSettingsActivity();
					    break;
					}
				    }
				}
				catch(Exception exception)
				{
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
	});

	final DialogInterface.OnCancelListener listener1 =
	    new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    if(State.getInstance().getString("dialog_accepted").
		       equals("true"))
		    {
			State.getInstance().reset();
			m_databaseHelper.resetAndDrop();
			s_cryptography.reset();

			Intent intent = new Intent
			    (Authenticate.this, Settings.class);

			startActivity(intent);
			finish();
		    }
		}
	    };

	Button button2 = (Button) findViewById(R.id.reset);

	button2.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		Miscellaneous.showPromptDialog(Authenticate.this,
					       listener1,
					       "Are you sure that you " +
					       "wish to reset Smoke? All " +
					       "of the data will be removed.");
	    }
	});
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(Authenticate.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showFireActivity()
    {
	Intent intent = new Intent(Authenticate.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showMemberChatActivity()
    {
	Intent intent = new Intent(Authenticate.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
	Intent intent = new Intent(Authenticate.this, Settings.class);

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
        setContentView(R.layout.activity_authenticate);
	getSupportActionBar().setTitle("Smoke | Authenticate");
	State.getInstance().setNeighborsEcho
	    (m_databaseHelper.readSetting(null,
					  "neighbors_echo").equals("true"));
	prepareListeners();

	boolean isAuthenticated = State.getInstance().isAuthenticated();
	Button button1 = (Button) findViewById(R.id.authenticate);

	button1.setEnabled(!isAuthenticated);

	TextView textView1 = (TextView) findViewById(R.id.password);

	textView1.setEnabled(!isAuthenticated);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.authenticate_menu, menu);
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
		m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
		showChatActivity();
		return true;
	    case R.id.action_fire:
		m_databaseHelper.writeSetting(null, "lastActivity", "Fire");
		showFireActivity();
		return true;
	    case R.id.action_settings:
		m_databaseHelper.writeSetting(null, "lastActivity", "Settings");
		showSettingsActivity();
		return true;
	    }
	else
	{
	    String sipHashId = menuItem.getTitle().toString();
	    int indexOf = sipHashId.indexOf("(");

	    if(indexOf >= 0)
		sipHashId = sipHashId.substring(indexOf + 1).replace(")", "");

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

	menu.findItem(R.id.action_chat).setEnabled(isAuthenticated);
	menu.findItem(R.id.action_fire).setEnabled(isAuthenticated);
	menu.findItem(R.id.action_settings).setEnabled(isAuthenticated);
	Miscellaneous.addMembersToMenu(menu, 3, 150);
	return true;
    }
}
