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
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemberChat extends AppCompatActivity
{
    private Database m_databaseHelper = Database.getInstance();
    private ScheduledExecutorService m_connectionStatusScheduler = null;
    private String m_name = "00:00:00:00:00:00:00:00";
    private String m_sipHashId = "00:00:00:00:00:00:00:00";
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static int CONNECTION_STATUS_INTERVAL = 1500; // 1.5 Seconds

    private void populate()
    {
	ArrayList<MemberChatElement> arrayList = m_databaseHelper.
	    readMemberChats(s_cryptography, m_sipHashId);

	if(arrayList == null || arrayList.size() == 0)
	    return;

	StringBuilder stringBuilder = new StringBuilder();
	ViewGroup viewGroup = (ViewGroup) findViewById(R.id.messages);
	int i = 0;

	for(MemberChatElement memberChatElement : arrayList)
	{
	    if(memberChatElement == null)
		continue;

	    stringBuilder.setLength(0);

	    ChatBubble chatBubble = new ChatBubble(MemberChat.this);
	    boolean local = false;

	    if(memberChatElement.m_fromSmokeStack.equals("local"))
		local = true;

	    stringBuilder.append(memberChatElement.m_message);
	    stringBuilder.append("\n");
	    chatBubble.setDate(memberChatElement.m_timestamp);
	    chatBubble.setId(memberChatElement.m_oid);
	    chatBubble.setTag(m_sipHashId);

	    if(!local)
		chatBubble.setTextLeft(stringBuilder.toString());
	    else
		chatBubble.setTextRight(stringBuilder.toString());

	    viewGroup.addView(chatBubble.view(), i);
	    viewGroup.requestLayout();
	    i += 1;
	}

	arrayList.clear();
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
	    }, 1500, CONNECTION_STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(MemberChat.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showFireActivity()
    {
	Intent intent = new Intent(MemberChat.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
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
	m_name = m_sipHashId = State.getInstance().getString
	    ("member_chat_siphash_id");

	if(m_sipHashId.isEmpty())
	    m_name = m_sipHashId = "00:00:00:00:00:00:00:00";

	/*
	** Prepare various widgets.
	*/

	m_name = m_databaseHelper.nameFromSipHashId
	    (s_cryptography, m_sipHashId);

	if(m_name.isEmpty())
	    m_name = m_sipHashId;

	TextView textView1 = (TextView) findViewById(R.id.banner);

	textView1.setText(m_name +
			  "@" +
			  Miscellaneous.
			  delimitString(m_sipHashId.replace(":", ""), '-', 4).
			  toUpperCase());

	/*
	** Populate the view.
	*/

	populate();

	/*
	** Prepare schedulers.
	*/

	prepareSchedulers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	getMenuInflater().inflate(R.menu.member_chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
	int id = item.getItemId();

	if(id == R.id.action_chat)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Chat");

	    Intent intent = new Intent(MemberChat.this, Chat.class);

            startActivity(intent);
	    finish();
	    return true;
	}
	else if(id == R.id.action_fire)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Fire");

	    Intent intent = new Intent(MemberChat.this, Fire.class);

            startActivity(intent);
	    finish();
	    return true;
	}
	else if(id == R.id.action_settings)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Settings");

            Intent intent = new Intent(MemberChat.this, Settings.class);

            startActivity(intent);
	    finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause()
    {
	super.onPause();
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

    @Override
    public void onResume()
    {
	super.onResume();
    }

    @Override
    public void onStop()
    {
	super.onStop();
    }
}
