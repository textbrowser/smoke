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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class Authenticate extends AppCompatActivity
{
    private void prepareListeners()
    {
        final Button button1 = (Button) findViewById
	    (R.id.authenticate);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View v)
	    {
		Database database = Database.getInstance(Authenticate.this);
		byte encryptionSalt[] = null;
		byte macSalt[] = null;
		byte saltedPassword[] = null;
		final TextView textView1 = (TextView)
		    findViewById(R.id.password);

		encryptionSalt = Base64.decode
		    (database.readSetting(null, "encryptionSalt").getBytes(),
		     Base64.DEFAULT);
		macSalt = Base64.decode
		    (database.readSetting(null, "macSalt").getBytes(),
		     Base64.DEFAULT);
		saltedPassword = Cryptography.sha512
		    (textView1.getText().toString().getBytes(),
		     encryptionSalt,
		     macSalt);
		textView1.setText("");

		if(saltedPassword == null ||
		   !Cryptography.
		   memcmp(database.readSetting(null,
					       "saltedPassword").getBytes(),
			  Base64.encode(saltedPassword, Base64.DEFAULT)))
		    Miscellaneous.showErrorDialog(Authenticate.this,
						  "Incorrect password.");
		else
		{
		    State.getInstance().setAuthenticated(true);

		    /*
		    ** Disable some widgets.
		    */

		    button1.setEnabled(false);
		    textView1.setEnabled(false);
		}
	    }
	});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authenticate);
	prepareListeners();
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
            final Intent intent = new Intent(Authenticate.this, Chat.class);

            startActivity(intent);
            return true;
        }
        else if(id == R.id.action_settings)
	{
            final Intent intent = new Intent(Authenticate.this, Settings.class);

            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
