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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;

public class Smokescreen extends AppCompatActivity
{
    private Button m_lock = null;
    private Button m_unlock = null;
    private Database m_databaseHelper = null;
    private TextView m_label = null;
    private TextView m_password = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();

    private void authenticate()
    {
	if(Smokescreen.this.isFinishing())
	    return;

	byte encryptionSalt[] = Base64.decode
	    (m_databaseHelper.readSetting(null, "encryptionSalt").getBytes(),
	     Base64.DEFAULT);

	m_password.setSelectAllOnFocus(true);

	if(encryptionSalt == null)
	{
	    m_password.setText("");
	    m_password.requestFocus();
	    return;
	}

	byte macSalt[] = Base64.decode
	    (m_databaseHelper.readSetting(null, "macSalt").getBytes(),
	     Base64.DEFAULT);

	if(macSalt == null)
	{
	    m_password.setText("");
	    m_password.requestFocus();
	    return;
	}

	byte saltedPassword[] = Cryptography.sha512
	    (m_password.getText().toString().getBytes(),
	     encryptionSalt,
	     macSalt);

	if(saltedPassword == null)
	{
	    m_password.setText("");
	    m_password.requestFocus();
	    return;
	}

	if(!Cryptography.memcmp(m_databaseHelper.
				readSetting(null, "saltedPassword").getBytes(),
				Base64.encode(saltedPassword, Base64.DEFAULT)))
	{
	    m_password.setText("");
	    m_password.requestFocus();
	    return;
	}

	State.getInstance().setLocked(false);
    }

    private void prepareListeners()
    {
	if(m_lock != null)
	    m_lock.setOnClickListener(new View.OnClickListener()
	    {
		public void onClick(View view)
		{
		    if(Smokescreen.this.isFinishing())
			return;

		    State.getInstance().setLocked(true);
		    prepareWidgets();
		}
	    });

	if(m_unlock != null)
	    m_unlock.setOnClickListener(new View.OnClickListener()
	    {
		public void onClick(View view)
		{
		    if(Smokescreen.this.isFinishing())
			return;

		    authenticate();
		    prepareWidgets();
		}
	    });
    }

    private void prepareWidgets()
    {
	boolean isLocked = State.getInstance().isLocked();

	m_label.setText
	    (isLocked ?
	     "Smoke is locked. To unlock, please provide the correct " +
	     "password and click the below button." :
	     "Smoke is unlocked. To lock, please click on the below button.");
	m_lock.setVisibility(isLocked ? View.GONE : View.VISIBLE);
	m_password.setVisibility(isLocked ? View.VISIBLE : View.GONE);

	if(isLocked)
	    m_password.post(new Runnable()
	    {
		@Override
		public void run()
		{
		    m_password.requestFocus();
		}
	    });

	m_unlock.setVisibility(isLocked ? View.VISIBLE : View.GONE);
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(Smokescreen.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showFireActivity()
    {
	Intent intent = new Intent(Smokescreen.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showMemberChatActivity()
    {
	Intent intent = new Intent(Smokescreen.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
	Intent intent = new Intent(Smokescreen.this, Settings.class);

	startActivity(intent);
	finish();
    }

    private void showSteamActivity()
    {
	Intent intent = new Intent(Smokescreen.this, Steam.class);

	startActivity(intent);
	finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
        setContentView(R.layout.activity_smokescreen);

	try
	{
	    getSupportActionBar().setTitle("Smoke | Smokescreen");
	}
	catch(Exception exception)
	{
	}

	m_label = (TextView) findViewById(R.id.label);
	m_lock = (Button) findViewById(R.id.lock);
	m_password = (TextView) findViewById(R.id.password);
	m_unlock = (Button) findViewById(R.id.authenticate);
	prepareListeners();
	prepareWidgets();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
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
	    case R.id.action_exit:
		Smoke.exit(Smokescreen.this);
		return true;
	    case R.id.action_fire:
		m_databaseHelper.writeSetting(null, "lastActivity", "Fire");
		showFireActivity();
		return true;
	    case R.id.action_settings:
		m_databaseHelper.writeSetting(null, "lastActivity", "Settings");
		showSettingsActivity();
		return true;
	    case R.id.action_steam:
		m_databaseHelper.writeSetting(null, "lastActivity", "Steam");
		showSteamActivity();
		return true;
	    default:
		break;
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
	menu.clear();
	getMenuInflater().inflate(R.menu.smokescreen_menu, menu);

	boolean isLocked = State.getInstance().isLocked();

	if(isLocked)
	{
	    menu.findItem(R.id.action_authenticate).setEnabled(false);
	    menu.findItem(R.id.action_chat).setEnabled(false);
	    menu.findItem(R.id.action_fire).setEnabled(false);
	    menu.findItem(R.id.action_settings).setEnabled(false);
	    menu.findItem(R.id.action_steam).setEnabled(false);
	}
	else
	{
	    boolean isAuthenticated = State.getInstance().isAuthenticated();

	    if(!m_databaseHelper.accountPrepared())
		/*
		** The database may have been modified or removed.
		*/

		isAuthenticated = true;

	    menu.findItem(R.id.action_authenticate).setEnabled
		(!isAuthenticated);
	    menu.findItem(R.id.action_chat).setEnabled(isAuthenticated);
	    menu.findItem(R.id.action_fire).setEnabled(isAuthenticated);
	    menu.findItem(R.id.action_settings).setEnabled(isAuthenticated);
	    menu.findItem(R.id.action_steam).setEnabled(isAuthenticated);
	    Miscellaneous.addMembersToMenu(menu, 6, 250);
	}

	return true;
    }
}
