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
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

public class Chat extends AppCompatActivity
{
    private Database m_databaseHelper = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
        setContentView(R.layout.activity_chat);
	setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        final Button button1 = (Button) findViewById(R.id.clear_chat_messages);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		final TextView textView = (TextView) findViewById
		    (R.id.chat_messages);

		textView.setText("");
	    }
	});

        final Button button2 = (Button) findViewById(R.id.send_chat_message);

        button2.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		final TextView textView1 = (TextView) findViewById
		    (R.id.chat_message);

		if(textView1.getText().toString().trim().isEmpty())
		    return;

		final TextView textView2 = (TextView) findViewById
		    (R.id.chat_messages);

		String str = textView1.getText().toString().trim();
		String timestamp = "";
		StringBuffer stringBuffer = new StringBuffer();
		byte encryptionKeyBytes[] = null;
		byte macKeyBytes[] = null;
		byte sipHashKeyStream[] = null;
		int sequence = 1;

		stringBuffer.append("me: ");
		stringBuffer.append(str);
		stringBuffer.append("\n");
		textView2.append(stringBuffer);
		textView1.setText("");

		/*
		** Iterate through selected people, if possible.
		** Otherwise, use checked items.
		*/

		byte bytes[] = Messages.chatMessage
		    (s_cryptography,
		     s_cryptography.chatEncryptionKeyPair().getPublic(),
		     str,
		     timestamp,
		     encryptionKeyBytes,
		     macKeyBytes,
		     sipHashKeyStream,
		     sequence);

		if(bytes != null)
		{
		    Kernel.getInstance().enqueueMessage
			(Messages.bytesToMessageString(bytes));

		    final ScrollView scrollView = (ScrollView)
			findViewById(R.id.chat_scrollview);

		    scrollView.post(new Runnable()
		    {
			@Override
			public void run()
			{
			    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
			}
		    });
		}
	    }
	});

	final TextView textView1 = (TextView) findViewById(R.id.chat_message);

	textView1.requestFocus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.

	getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

	int id = item.getItemId();

        if(id == R.id.action_settings)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Settings");

            final Intent intent = new Intent(Chat.this, Settings.class);

            startActivity(intent);
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
