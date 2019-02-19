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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;

public class Settings extends AppCompatActivity
{
    private abstract class ContextMenuEnumerator
    {
	public final static int DELETE = 0;
	public final static int DELETE_FIASCO_KEYS = 1;
	public final static int NEW_NAME = 2;
	public final static int REQUEST_KEYS_VIA_OZONE = 3;
	public final static int SHARE_KEYS_OF = 4;
	public final static int SHARE_SMOKE_ID_OF = 5;
	public final static int VIEW_DETAILS = 6;
    }

    private class PopulateNeighbors implements Runnable
    {
	private ArrayList<NeighborElement> m_arrayList = null;

	public PopulateNeighbors(ArrayList<NeighborElement> arrayList)
	{
	    m_arrayList = arrayList;
	}

	@Override
	public void run()
	{
	    populateNeighbors(m_arrayList);
	}
    }

    private class SettingsBroadcastReceiver extends BroadcastReceiver
    {
	public SettingsBroadcastReceiver()
	{
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
	    if(intent == null || intent.getAction() == null)
		return;

	    switch(intent.getAction())
	    {
	    case "org.purple.smoke.chat_message":
		Miscellaneous.showNotification
		    (Settings.this, intent, findViewById(R.id.main_layout));
		break;
	    case "org.purple.smoke.populate_participants":
		populateParticipants();
		break;
	    case "org.purple.smoke.siphash_share_confirmation":
		Miscellaneous.showNotification
		    (Settings.this, intent, findViewById(R.id.main_layout));
		break;
	    }
	}
    }

    private Database m_databaseHelper = null;
    private ScheduledExecutorService m_scheduler = null;
    private SettingsBroadcastReceiver m_receiver = null;
    private boolean m_receiverRegistered = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static InputFilter s_portFilter = new InputFilter()
    {
	public CharSequence filter(CharSequence source,
				   int start,
				   int end,
				   Spanned dest,
				   int dstart,
				   int dend)
	{
	    try
	    {
		int port = Integer.parseInt
		    (dest.toString() + source.toString());

		if(port >= 0 && port <= 65535)
		    return null;
	    }
	    catch(Exception exception)
	    {
	    }

	    return "";
	}
    };
    private final static InputFilter s_sipHashInputFilter = new InputFilter()
    {
	public CharSequence filter(CharSequence source,
				   int start,
				   int end,
				   Spanned dest,
				   int dstart,
				   int dend)
	{
	    for(int i = start; i < end; i++)
		/*
		** Allow hexadecimal characters only and some delimiters.
		*/

		if(!((source.charAt(i) == ' ' || source.charAt(i) == '-') ||
		     (source.charAt(i) >= '0' && source.charAt(i) <= '9') ||
		     (source.charAt(i) >= '@' && source.charAt(i) <= 'F') ||
		     (source.charAt(i) >= 'a' && source.charAt(i) <= 'f')))
		    return source.subSequence(start, i);

	    return null;
	}
    };
    private final static int OZONE_STREAM_CREATION_ITERATION_COUNT = 4096;
    private final static int TEXTVIEW_WIDTH = 500;
    private final static int PKI_SIGNATURE_KEY_SIZES[] =
        {384, 3072}; // ECDSA, RSA
    private final static long TIMER_INTERVAL = 2000; // 2 Seconds
    public final static int PKI_ENCRYPTION_KEY_SIZES[] = {3072}; // RSA

    private boolean generateOzone(String string)
    {
	boolean ok = true;
	byte bytes[] = null;
	byte salt[] = null;

	try
	{
	    if(!string.trim().isEmpty())
	    {
		salt = Cryptography.sha512
		    (string.trim().getBytes(StandardCharsets.UTF_8));

		if(salt != null)
		    bytes = Cryptography.pbkdf2
			(salt,
			 string.trim().toCharArray(),
			 OZONE_STREAM_CREATION_ITERATION_COUNT,
			 160); // SHA-1
		else
		    ok = false;

		if(bytes != null)
		    bytes = Cryptography.
			pbkdf2(salt,
			       new String(bytes).toCharArray(),
			       1,
			       768); // 8 * (32 + 64) Bits
		else
		    ok = false;
	    }

	    if(bytes != null || string.trim().isEmpty())
	    {
		m_databaseHelper.writeSetting
		    (s_cryptography, "ozone_address", string.trim());

		if(string.trim().isEmpty())
		{
		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "ozone_address_stream",
			 "");
		    ok = true;
		    s_cryptography.setOzoneEncryptionKey(null);
		    s_cryptography.setOzoneMacKey(null);
		}
		else if(bytes != null && bytes.length == 96)
		    {
			m_databaseHelper.writeSetting
			    (s_cryptography,
			     "ozone_address_stream",
			     Base64.encodeToString(bytes,
						   Base64.DEFAULT));
			s_cryptography.setOzoneEncryptionKey
			    (Arrays.copyOfRange(bytes, 0, 32));
			s_cryptography.setOzoneMacKey
			    (Arrays.copyOfRange(bytes, 32, bytes.length));
		    }
	    }
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	return ok;
    }

    private void addNeighbor()
    {
	CheckBox checkBox1 = (CheckBox) findViewById(R.id.automatic_refresh);
	CheckBox checkBox2 = (CheckBox) findViewById(R.id.initialize_ozone);
	RadioGroup radioGroup1 = (RadioGroup) findViewById
	    (R.id.neighbors_ipv_radio_group);
	Spinner spinner1 = (Spinner) findViewById(R.id.neighbors_transport);
	Spinner spinner2 = (Spinner) findViewById(R.id.proxy_type);
	String ipVersion = "";
	TextView proxyIpAddress = (TextView) findViewById
	    (R.id.proxy_ip_address);
	TextView proxyPort = (TextView) findViewById(R.id.proxy_port);
	TextView textView1 = (TextView) findViewById(R.id.neighbors_ip_address);
	TextView textView2 = (TextView) findViewById(R.id.neighbors_port);
	TextView textView3 = (TextView) findViewById(R.id.neighbors_scope_id);

	if(radioGroup1.getCheckedRadioButtonId() == R.id.neighbors_ipv4)
	    ipVersion = "IPv4";
	else
	    ipVersion = "IPv6";

	if(textView1.getText().toString().trim().isEmpty())
	    Miscellaneous.showErrorDialog
		(Settings.this, "Please complete the IP Address field.");
	else if(!m_databaseHelper.
		writeNeighbor(s_cryptography,
			      proxyIpAddress.getText().toString(),
			      proxyPort.getText().toString(),
			      spinner2.getSelectedItem().
			      toString(),
			      textView1.getText().toString(),
			      textView2.getText().toString(),
			      textView3.getText().toString(),
			      spinner1.getSelectedItem().
			      toString(),
			      ipVersion))
	    Miscellaneous.showErrorDialog
		(Settings.this,
		 "An error occurred while saving the neighbor information.");
	else
	{
	    if(!checkBox1.isChecked())
		populateNeighbors(null);

	    if(checkBox2.isChecked())
	    {
		String string = textView1.getText().toString() + ":" +
		    textView2.getText().toString() + ":" +
		    spinner1.getSelectedItem();

		if(generateOzone(string))
		{
		    textView1 = (TextView) findViewById(R.id.ozone);
		    textView1.setText(string);
		}
	    }
	}
    }

    private void addParticipant()
    {
	if(Settings.this.isFinishing())
	    return;

	String string = "";
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById
	    (R.id.participant_siphash_id);
	TextView textView2 = (TextView) findViewById(R.id.siphash_identity);

	stringBuilder.append
	    (Miscellaneous.
	     prepareSipHashId(textView1.getText().toString().
			      replace(" ", "").
			      replace("-", "").
			      replace(":", "").
			      replace("@", "").trim()));
	string = stringBuilder.toString().trim();

	if(string.length() != Cryptography.SIPHASH_ID_LENGTH)
	{
	    Miscellaneous.showErrorDialog
		(Settings.this,
		 "A Smoke ID must be of the form 0102-0304-0506-0708.");
	    return;
	}
	else if(textView2.getText().toString().equals(string))
	{
	    Miscellaneous.showErrorDialog
		(Settings.this, "Please do not assign your Smoke ID.");
	    return;
	}

	final ProgressBar bar = (ProgressBar) findViewById
	    (R.id.add_participants_progress_bar);

	bar.setIndeterminate(true);
	bar.setVisibility(ProgressBar.VISIBLE);
	getWindow().setFlags
	    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
	     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
	Miscellaneous.enableChildren
	    (findViewById(R.id.linear_layout), false);

	class SingleShot implements Runnable
	{
	    private String m_name = "";
	    private String m_sipHashId = "";
	    private boolean m_error = false;

	    SingleShot(String name, String sipHashId)
	    {
		m_name = name;
		m_sipHashId = sipHashId;
	    }

	    @Override
	    public void run()
	    {
		if(!m_databaseHelper.writeSipHashParticipant(s_cryptography,
							     m_name,
							     m_sipHashId))
		    m_error = true;

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			bar.setVisibility(ProgressBar.INVISIBLE);
			getWindow().clearFlags
			    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
			Miscellaneous.enableChildren
			    (findViewById(R.id.linear_layout), true);
			disablePKIButtons();

			if(m_error)
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error occurred while attempting " +
				 "to save the specified Smoke ID.");
			else
			    populateParticipants();
		    }
		});
	    }
	}

	Thread thread = new Thread
	    (new SingleShot(((TextView) findViewById(R.id.participant_name)).
			    getText().toString(), string));

	thread.start();
    }

    private void deleteNeighbor(String ipAndPort, int id)
    {
	final int oid = id;

	/*
	** Prepare a response.
	*/

	final DialogInterface.OnCancelListener listener =
	    new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    if(State.getInstance().getString("dialog_accepted").
		       equals("true"))
			if(m_databaseHelper.
			   deleteEntry(String.valueOf(oid), "neighbors"))
			{
			    /*
			    ** Prepare the kernel's neighbors container
			    ** if a neighbor was deleted as the OID
			    ** field may represent a recycled value.
			    */

			    Kernel.getInstance().purgeDeletedNeighbors();

			    TableLayout tableLayout = (TableLayout)
				findViewById(R.id.neighbors);
			    TableRow row = (TableRow) findViewById(oid);

			    if(row != null)
				tableLayout.removeView(row);
			}
	        }
	    };

	Miscellaneous.showPromptDialog
	    (Settings.this,
	     listener,
	     "Are you sure that you wish to " +
	     "delete the neighbor " + ipAndPort + "?");
    }

    private void disablePKIButtons()
    {
	Button button1 = (Button) findViewById(R.id.generate_pki);
	CheckBox checkBox1 = (CheckBox) findViewById(R.id.overwrite);

	button1.setEnabled(checkBox1.isChecked());
	button1 = (Button) findViewById(R.id.set_password);
	button1.setEnabled(checkBox1.isChecked());
    }

    private void enableWidgets(boolean state)
    {
	Button button1 = null;

	button1 = (Button) findViewById(R.id.add_neighbor);
	button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.add_participant);
	button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.epks);
	button1.setEnabled(state);
        button1 = (Button) findViewById(R.id.refresh_neighbors);
        button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.refresh_participants);
	button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.reset_neighbor_fields);
	button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.reset_participants_fields);
	button1.setEnabled(state);
	button1 = (Button) findViewById(R.id.save_ozone);
	button1.setEnabled(state);

	CheckBox checkBox1 = null;

	checkBox1 = (CheckBox) findViewById(R.id.overwrite);
	checkBox1.setChecked(!state);
	checkBox1.setEnabled(state);
	button1 = (Button) findViewById(R.id.generate_pki);
	button1.setEnabled(!state);
	button1 = (Button) findViewById(R.id.set_password);
	button1.setEnabled(!state);

	RadioButton radioButton1 = null;

	radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv4);
	radioButton1.setEnabled(state);
	radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv6);
	radioButton1.setEnabled(state);

	Spinner spinner1 = null;

	spinner1 = (Spinner) findViewById(R.id.neighbors_transport);
	spinner1.setEnabled(state);
	spinner1 = (Spinner) findViewById(R.id.proxy_type);
	spinner1.setEnabled(state);

	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.neighbors_ip_address);
	textView1.setEnabled(state);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.neighbors_scope_id);
        textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.ozone);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.participant_name);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.participant_siphash_id);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.proxy_ip_address);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.proxy_port);
	textView1.setEnabled(state);
    }

    private void epks(final String sipHashId)
    {
	if(Settings.this.isFinishing())
	    return;

	final ProgressBar bar = (ProgressBar) findViewById
	    (R.id.share_keys_progress_bar);

	bar.setIndeterminate(true);
	bar.setVisibility(ProgressBar.VISIBLE);
	getWindow().setFlags
	    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
	     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
	Miscellaneous.enableChildren
	    (findViewById(R.id.linear_layout), false);

	class SingleShot implements Runnable
	{
	    private String m_error = "";

	    SingleShot()
	    {
	    }

	    @Override
	    public void run()
	    {
		ArrayList<SipHashIdElement> arrayList =
		    m_databaseHelper.readSipHashIds(sipHashId, s_cryptography);

		if(arrayList == null)
		    arrayList = new ArrayList<> ();

		{
		    /*
		    ** Self-sending.
		    */

		    SipHashIdElement sipHashIdElement = new SipHashIdElement();

		    sipHashIdElement.m_sipHashId = s_cryptography.sipHashId();
		    sipHashIdElement.m_stream = Miscellaneous.joinByteArrays
			(s_cryptography.sipHashEncryptionKey(),
			 s_cryptography.sipHashMacKey());
		    arrayList.add(sipHashIdElement);
		}

		for(SipHashIdElement sipHashIdElement : arrayList)
		{
		    if(sipHashIdElement == null)
		    {
			m_error = "zero element";
			break;
		    }

		    byte bytes[] = Messages.epksMessage
			(s_cryptography,
			 sipHashIdElement.m_sipHashId,
			 sipHashIdElement.m_stream,
			 Messages.CHAT_KEY_TYPE);

		    if(bytes == null)
		    {
			m_error = "epksMessage() failure";
			break;
		    }

		    if(!Kernel.getInstance().
		       enqueueMessage(Messages.bytesToMessageString(bytes),
				      null))
		    {
			m_error = "enqueueMessage() failure";
			break;
		    }
		}

		if(sipHashId.isEmpty())
		    Settings.this.runOnUiThread(new Runnable()
		    {
			@Override
			public void run()
			{
			    bar.setVisibility(ProgressBar.INVISIBLE);
			    getWindow().clearFlags
				(WindowManager.LayoutParams.
				 FLAG_NOT_TOUCHABLE);
			    Miscellaneous.enableChildren
				(findViewById(R.id.linear_layout), true);
			    disablePKIButtons();

			    if(!m_error.isEmpty())
				Miscellaneous.showErrorDialog
				    (Settings.this,
				     "An error (" + m_error +
				     ") occurred while " +
				     "preparing to transfer public key " +
				     "material. " +
				     "Please verify that participant Smoke " +
				     "Identities have been defined.");
			}
		    });

		arrayList.clear();
	    }
	}

	Thread thread = new Thread(new SingleShot());

	thread.start();
    }

    private void populateFancyKeyData()
    {
	StringBuilder stringBuilder = null;
	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.chat_encryption_key_data);

	if(s_cryptography.chatEncryptionKeyPair() == null ||
	   s_cryptography.chatEncryptionKeyPair().getPublic() == null)
	    textView1.setVisibility(View.GONE);
	else
	{
	    stringBuilder = new StringBuilder();
	    stringBuilder.append("Chat Encryption Key\n");
	    stringBuilder.append
		(s_cryptography.
		 fancyKeyInformationOutput(s_cryptography.
					   chatEncryptionKeyPair()));
	    textView1.setText(stringBuilder);
	    textView1.setVisibility(View.VISIBLE);
	}

	textView1 = (TextView) findViewById(R.id.chat_signature_key_data);

	if(s_cryptography.chatSignatureKeyPair() == null ||
	   s_cryptography.chatSignatureKeyPair().getPublic() == null)
	    textView1.setVisibility(View.GONE);
	else
	{
	    if(stringBuilder == null)
		stringBuilder = new StringBuilder();
	    else
		stringBuilder.delete(0, stringBuilder.length());

	    stringBuilder.append("Chat Signature Key\n");
	    stringBuilder.append
		(s_cryptography.
		 fancyKeyInformationOutput(s_cryptography.
					   chatSignatureKeyPair()));
	    textView1.setText(stringBuilder);
	    textView1.setVisibility(View.VISIBLE);
	}

	textView1 = (TextView) findViewById(R.id.siphash_identity);

	if(stringBuilder == null)
	    textView1.setVisibility(View.GONE);
	else
	{
	    stringBuilder.delete(0, stringBuilder.length());
	    stringBuilder.append
		(Miscellaneous.prepareSipHashId(s_cryptography.sipHashId()));
	    textView1.setText(stringBuilder);
	    textView1.setTextIsSelectable(true);
	    textView1.setVisibility(View.VISIBLE);
	}
    }

    private void populateNeighbors(ArrayList<NeighborElement> arrayList)
    {
	if(arrayList == null)
	    arrayList = m_databaseHelper.readNeighbors(s_cryptography);

	final TableLayout tableLayout = (TableLayout)
	    findViewById(R.id.neighbors);

	if(arrayList == null || arrayList.isEmpty())
	{
	    tableLayout.removeAllViews();
	    return;
	}

	StringBuilder stringBuilder = new StringBuilder();
	int i = 0;

	/*
	** Remove table entries which do not exist in smoke.db.
	*/

	for(i = tableLayout.getChildCount() - 1; i >= 0; i--)
	{
	    TableRow row = (TableRow) tableLayout.getChildAt(i);

	    if(row == null)
		continue;

	    TextView textView1 = (TextView) row.getChildAt(1);

	    if(textView1 == null)
	    {
		tableLayout.removeView(row);
		continue;
	    }

	    boolean found = false;

	    for(NeighborElement neighborElement : arrayList)
	    {
		stringBuilder.setLength(0);
		stringBuilder.append(neighborElement.m_remoteIpAddress);

		if(neighborElement.m_ipVersion.equals("IPv6"))
		    if(!neighborElement.m_remoteScopeId.isEmpty())
		    {
			stringBuilder.append("-");
			stringBuilder.append(neighborElement.m_remoteScopeId);
		    }

		stringBuilder.append(":");
		stringBuilder.append(neighborElement.m_remotePort);
		stringBuilder.append(":");
		stringBuilder.append(neighborElement.m_transport);

		if(textView1.getText().toString().
		   contains(stringBuilder.toString()))
		{
		    found = true;
		    break;
		}
	    }

	    if(!found)
		tableLayout.removeView(row);
	}

	CheckBox checkBox1 = (CheckBox) findViewById(R.id.neighbor_details);

	i = 0;

	for(NeighborElement neighborElement : arrayList)
	{
	    if(neighborElement == null)
		continue;

	    Spinner spinner = null;
	    TableRow row = null;
	    TextView textView1 = null;
	    int count = tableLayout.getChildCount();

	    for(int j = 0; j < count; j++)
	    {
		TableRow r = (TableRow) tableLayout.getChildAt(j);

		if(r == null)
		    continue;

		TextView t = (TextView) r.getChildAt(1);

		if(t == null)
		    continue;

		stringBuilder.setLength(0);
		stringBuilder.append(neighborElement.m_remoteIpAddress);

		if(neighborElement.m_ipVersion.equals("IPv6"))
		    if(!neighborElement.m_remoteScopeId.isEmpty())
		    {
			stringBuilder.append("-");
			stringBuilder.append(neighborElement.m_remoteScopeId);
		    }

		stringBuilder.append(":");
		stringBuilder.append(neighborElement.m_remotePort);
		stringBuilder.append(":");
		stringBuilder.append(neighborElement.m_transport);

		if(t.getText().toString().contains(stringBuilder.toString()))
		{
		    textView1 = t;
		    break;
		}
	    }

	    if(textView1 == null)
	    {
		TableRow.LayoutParams layoutParams = new
		    TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);

		row = new TableRow(Settings.this);
		row.setId(neighborElement.m_oid);
		row.setLayoutParams(layoutParams);
		spinner = new Spinner(Settings.this);

		ArrayAdapter<String> arrayAdapter = null;
		String array[] = null;
		final String ipAndPort = neighborElement.
		    m_remoteIpAddress + ":" + neighborElement.m_remotePort;

		if(neighborElement.m_transport.equals("TCP"))
		    array = new String[]
		    {
			"Action",
			"Connect",
			"Delete",
			"Disconnect",
			"Purge Queue",
			"Reset SSL/TLS Credentials"
		    };
		else
		    array = new String[]
		    {
			"Action",
			"Connect",
			"Delete",
			"Disconnect",
			"Purge Queue"
		    };

		arrayAdapter = new ArrayAdapter<>
		    (Settings.this,
		     android.R.layout.simple_spinner_item,
		     array);
		spinner.setAdapter(arrayAdapter);
		spinner.setId(neighborElement.m_oid);
		spinner.setOnItemSelectedListener
		    (new OnItemSelectedListener()
		    {
			@Override
			public void onItemSelected(AdapterView<?> parent,
						   View view,
						   int position,
						   long id)
			{
			    switch(position)
			    {
			    case 1: // Connect
				m_databaseHelper.neighborControlStatus
				    (s_cryptography,
				     "connect",
				     String.valueOf(parent.getId()));
				break;
			    case 2: // Delete
				deleteNeighbor(ipAndPort, parent.getId());
				break;
			    case 3: // Disconnect
				m_databaseHelper.neighborControlStatus
				    (s_cryptography,
				     "disconnect",
				     String.valueOf(parent.getId()));
				break;
			    case 4: // Purge Queue
				m_databaseHelper.purgeNeighborQueue
				    (String.valueOf(parent.getId()));
				break;
			    case 5: // Reset SSL/TLS Credentials
				m_databaseHelper.neighborRecordCertificate
				    (s_cryptography,
				     String.valueOf(parent.getId()),
				     null);
				m_databaseHelper.neighborControlStatus
				    (s_cryptography,
				     "disconnect",
				     String.valueOf(parent.getId()));
				break;
			    }

			    parent.setSelection(0);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent)
			{
			}
		    });

		textView1 = new TextView(Settings.this);
	    }

	    switch(neighborElement.m_status)
	    {
            case "connected":
                textView1.setTextColor(Color.rgb(27, 94, 32)); // Dark Green
                break;
            case "connecting":
                textView1.setTextColor(Color.rgb(255, 111, 0)); // Dark Orange
                break;
            default:
                textView1.setTextColor(Color.rgb(183, 28, 28)); // Dark Red
                break;
	    }

	    stringBuilder.setLength(0);
	    stringBuilder.append("Control: ");

	    try
	    {
		stringBuilder.append
		    (neighborElement.m_statusControl.substring(0, 1).
		     toUpperCase());
		stringBuilder.append
		    (neighborElement.m_statusControl.substring(1));
	    }
	    catch(Exception exception)
	    {
		stringBuilder.append("Disconnect");
	    }

	    stringBuilder.append("\n");
	    stringBuilder.append("Status: ");

	    try
	    {
		stringBuilder.append
		    (neighborElement.m_status.substring(0, 1).toUpperCase());
		stringBuilder.append(neighborElement.m_status.substring(1));
	    }
	    catch(Exception exception)
	    {
		stringBuilder.append("Disconnected");
	    }

	    stringBuilder.append("\n");

	    if(!neighborElement.m_error.isEmpty())
	    {
		stringBuilder.append("Error: ");
		stringBuilder.append(neighborElement.m_error);
		stringBuilder.append("\n");
	    }

	    stringBuilder.append(neighborElement.m_remoteIpAddress);

	    if(neighborElement.m_ipVersion.equals("IPv6"))
		if(!neighborElement.m_remoteScopeId.isEmpty())
		{
		    stringBuilder.append("-");
		    stringBuilder.append(neighborElement.m_remoteScopeId);
		}

	    stringBuilder.append(":");
	    stringBuilder.append(neighborElement.m_remotePort);
	    stringBuilder.append(":");
	    stringBuilder.append(neighborElement.m_transport);

	    if(!neighborElement.m_localIpAddress.isEmpty() &&
	       !neighborElement.m_localPort.isEmpty())
	    {
		stringBuilder.append("\n");
		stringBuilder.append(neighborElement.m_localIpAddress);
		stringBuilder.append(":");
		stringBuilder.append(neighborElement.m_localPort);
	    }

	    stringBuilder.append("\nProxy: ");

	    if(!neighborElement.m_proxyIpAddress.isEmpty() &&
	       !neighborElement.m_proxyPort.isEmpty())
	    {
		stringBuilder.append(neighborElement.m_proxyIpAddress);
		stringBuilder.append(":");
		stringBuilder.append(neighborElement.m_proxyPort);
		stringBuilder.append(":");
		stringBuilder.append(neighborElement.m_proxyType);
	    }

	    if(checkBox1.isChecked())
	    {
		if(neighborElement.m_remoteCertificate != null &&
		   neighborElement.m_remoteCertificate.length > 0)
		{
		    stringBuilder.append("\n");
		    stringBuilder.append
			("Remote Certificate's Fingerprint: ");
		    stringBuilder.append
			(Cryptography.
			 fingerPrint(Miscellaneous.
				     pemFormat(neighborElement.
					       m_remoteCertificate).
				     getBytes()));
		}

		if(!neighborElement.m_sessionCipher.isEmpty())
		{
		    stringBuilder.append("\n");
		    stringBuilder.append("Session Cipher: ");
		    stringBuilder.append(neighborElement.m_sessionCipher);
		}
	    }

	    stringBuilder.append("\n");
	    stringBuilder.append("Temp. Queued: ");
	    stringBuilder.append(neighborElement.m_echoQueueSize);
	    stringBuilder.append(" / ");
	    stringBuilder.append(Neighbor.MAXIMUM_QUEUED_ECHO_PACKETS);
	    stringBuilder.append("\n");
	    stringBuilder.append("In: ");
	    stringBuilder.append
		(Miscellaneous.
		 formattedDigitalInformation(neighborElement.m_bytesRead));
	    stringBuilder.append("\n");
	    stringBuilder.append("Out: ");
	    stringBuilder.append
		(Miscellaneous.
		 formattedDigitalInformation(neighborElement.m_bytesWritten));
	    stringBuilder.append("\n");
	    stringBuilder.append("Outbound Queued: ");
	    stringBuilder.append(neighborElement.m_outboundQueued);
	    stringBuilder.append("\n");
	    stringBuilder.append("Uptime: ");

	    try
	    {
		long uptime = Long.parseLong(neighborElement.m_uptime);

		stringBuilder.append
		    (String.
		     format(Locale.getDefault(),
			    "%d:%02d",
			    TimeUnit.NANOSECONDS.toMinutes(uptime),
			    TimeUnit.NANOSECONDS.toSeconds(uptime) -
			    TimeUnit.MINUTES.
			    toSeconds(TimeUnit.NANOSECONDS.
				      toMinutes(uptime))));
	    }
	    catch(Exception exception)
	    {
		stringBuilder.append("0:00");
	    }

	    stringBuilder.append(" Min.\n");
	    textView1.setGravity(Gravity.CENTER_VERTICAL);
	    textView1.setLayoutParams
		(new TableRow.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
	    textView1.setText(stringBuilder);
	    textView1.setWidth(TEXTVIEW_WIDTH);

	    if(row != null)
	    {
		row.addView(spinner);
		row.addView(textView1);
		tableLayout.addView(row, i);
	    }

	    i += 1;
	}

	arrayList.clear();
    }

    private void populateOzone()
    {
	TextView textView1 = (TextView) findViewById(R.id.ozone);

	textView1.setText
	    (m_databaseHelper.readSetting(s_cryptography, "ozone_address"));
    }

    private void populateParticipants()
    {
	ArrayList<SipHashIdElement> arrayList =
	    m_databaseHelper.readSipHashIds("", s_cryptography);
	TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.participants);

	invalidateOptionsMenu();
	tableLayout.removeAllViews();

	if(arrayList == null || arrayList.isEmpty())
	    return;

	int i = 0;

	for(SipHashIdElement sipHashIdElement : arrayList)
	{
	    if(sipHashIdElement == null)
		continue;

	    String sipHashId = Miscellaneous.prepareSipHashId
		(sipHashIdElement.m_sipHashId);
	    TableRow.LayoutParams layoutParams = new
		TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
	    TableRow row = new TableRow(Settings.this);

	    row.setLayoutParams(layoutParams);

	    for(int j = 0; j < 3; j++)
	    {
		TextView textView1 = new TextView(Settings.this);

		textView1.setId(sipHashIdElement.m_oid);

		switch(j)
		{
		case 0:
		    textView1.setGravity(Gravity.CENTER_VERTICAL);
		    textView1.setLayoutParams
			(new TableRow.LayoutParams(0,
						   LayoutParams.MATCH_PARENT,
						   1));
		    textView1.setTag
			(R.id.participants, sipHashIdElement.m_name);
		    textView1.setText(sipHashIdElement.m_name);
		    break;
		case 1:
		    if(sipHashIdElement.m_epksCompleted &&
		       sipHashIdElement.m_keysSigned)
                        textView1.setCompoundDrawablesWithIntrinsicBounds
			    (R.drawable.keys_signed, 0, 0, 0);
                    else if(sipHashIdElement.m_epksCompleted)
                        textView1.setCompoundDrawablesWithIntrinsicBounds
			    (R.drawable.keys_not_signed, 0, 0, 0);
                    else
                        textView1.setCompoundDrawablesWithIntrinsicBounds
			    (R.drawable.warning, 0, 0, 0);

		    textView1.setCompoundDrawablePadding(5);
		    textView1.setGravity(Gravity.CENTER_VERTICAL);
		    textView1.setTag(R.id.participants, sipHashId);
		    textView1.setText(sipHashId);
		    break;
		case 2:
		    textView1.setGravity(Gravity.CENTER);
		    textView1.setLayoutParams
			(new TableRow.LayoutParams(0,
						   LayoutParams.MATCH_PARENT,
						   1));
		    textView1.setTag(R.id.participants, sipHashId);
		    textView1.setText
			(String.valueOf(sipHashIdElement.m_fiascoKeys));
		    break;
		}

		textView1.setTag
		    (R.id.refresh_participants,
		     sipHashIdElement.m_epksCompleted);
		registerForContextMenu(textView1);
		row.addView(textView1);
	    }

	    tableLayout.addView(row, i);
	    i += 1;
	}

	arrayList.clear();
    }

    private void prepareListeners()
    {
	Button button1 = null;
	Spinner spinner1 = (Spinner) findViewById(R.id.neighbors_transport);

	button1 = (Button) findViewById(R.id.add_neighbor);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		addNeighbor();
	    }
        });

	button1 = (Button) findViewById(R.id.add_participant);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		addParticipant();
	    }
        });

	button1 = (Button) findViewById(R.id.clear_log);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		m_databaseHelper.clearTable("log");
	    }
	});

	button1 = (Button) findViewById(R.id.epks);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		epks("");
	    }
        });

	button1 = (Button) findViewById(R.id.generate_pki);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		preparePKI();
	    }
	});

	button1 = (Button) findViewById(R.id.ozone_help);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		PopupWindow popupWindow = new PopupWindow(Settings.this);
		TextView textView1 = new TextView(Settings.this);
		float density = getApplicationContext().getResources().
		    getDisplayMetrics().density;

		textView1.setBackgroundColor(Color.rgb(232, 234, 246));
		textView1.setPaddingRelative
		    ((int) (10 * density),
		     (int) (10 * density),
		     (int) (10 * density),
		     (int) (10 * density));
		textView1.setText
		    ("An Ozone Address defines a virtual location, " +
		     "a separate device where messages are to be stored for " +
		     "later retrieval. A virtual post office. " +
		     "Please remember to share your Ozone Address with your " +
		     "friends as well as at least one SmokeStack.");
		textView1.setTextSize(16);
		popupWindow.setContentView(textView1);
		popupWindow.setOutsideTouchable(true);

		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
		{
		    popupWindow.setHeight(450);
		    popupWindow.setWidth(700);
		}

		popupWindow.showAsDropDown(view);
	    }
	});

	button1 = (Button) findViewById(R.id.refresh_neighbors);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		populateNeighbors(null);
	    }
        });

	button1 = (Button) findViewById(R.id.refresh_participants);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		populateParticipants();
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

			Intent intent = getIntent();

			startActivity(intent);
			finish();
		    }
		}
	    };

	button1 = (Button) findViewById(R.id.reset);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		Miscellaneous.showPromptDialog(Settings.this,
					       listener1,
					       "Are you sure that you " +
					       "wish to reset Smoke? All " +
					       "of the data will be removed.");
	    }
	});

	button1 = (Button) findViewById(R.id.reset_neighbor_fields);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		CheckBox checkbox1 = (CheckBox) findViewById
		    (R.id.initialize_ozone);
		RadioButton radioButton1 = (RadioButton) findViewById
		    (R.id.neighbors_ipv4);
		Spinner spinner1 = (Spinner) findViewById
		    (R.id.neighbors_transport);
		Spinner spinner2 = (Spinner) findViewById
		    (R.id.proxy_type);
		TextView proxyIpAddress = (TextView) findViewById
		    (R.id.proxy_ip_address);
		TextView proxyPort = (TextView) findViewById
		    (R.id.proxy_port);
		TextView textView1 = (TextView) findViewById
		    (R.id.neighbors_ip_address);
		TextView textView2 = (TextView) findViewById
		    (R.id.neighbors_port);
		TextView textView3 = (TextView) findViewById
		    (R.id.neighbors_scope_id);

		checkbox1.setChecked(false);
		proxyIpAddress.setText("");
		proxyPort.setText("");
		radioButton1.setChecked(true);
		spinner1.setSelection(0);
		spinner2.setSelection(0);
		textView1.setText("");
		textView2.setText("4710");
		textView3.setText("");
		textView1.requestFocus();
	    }
	});

	button1 = (Button) findViewById(R.id.reset_participants_fields);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		TextView textView1 = (TextView) findViewById
		    (R.id.participant_name);
		TextView textView2 = (TextView) findViewById
		    (R.id.participant_siphash_id);

		textView1.setText("");
		textView2.setText("");
		textView1.requestFocus();
	    }
	});

	final DialogInterface.OnCancelListener listener2 =
	    new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    if(State.getInstance().getString("dialog_accepted").
		       equals("true"))
		    {
			TextView textView1 = (TextView) findViewById
			    (R.id.ozone);

			textView1.setText("");
			m_databaseHelper.reset();
			populateFancyKeyData();
			populateNeighbors(null);
			populateParticipants();
			prepareCredentials();
		    }
		}
	    };

	button1 = (Button) findViewById(R.id.save_ozone);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		TextView textView1 = (TextView) findViewById(R.id.ozone);

		if(!generateOzone(textView1.getText().toString()))
		{
		    Miscellaneous.showErrorDialog
			(Settings.this,
			 "An error occurred while processing the Ozone data.");
		    textView1.requestFocus();
		}
	    }
	});

	button1 = (Button) findViewById(R.id.set_password);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		TextView textView1 = (TextView) findViewById(R.id.password1);
		TextView textView2 = (TextView) findViewById(R.id.password2);

		textView1.setSelectAllOnFocus(true);
		textView2.setSelectAllOnFocus(true);

		if(textView1.getText().length() < 1 ||
		   !textView1.getText().toString().
		   equals(textView2.getText().toString()))
		{
		    String error = "";

		    if(textView1.getText().length() < 1)
			error = "Each password must contain " +
			    "at least one character.";
		    else
			error = "The provided passwords are not identical.";

		    Miscellaneous.showErrorDialog(Settings.this, error);
		    textView1.requestFocus();
		    return;
		}

		int iterationCount = 1000;

		try
		{
		    final Spinner spinner1 = (Spinner) findViewById
			(R.id.iteration_count);

		    iterationCount = Integer.parseInt
			(spinner1.getSelectedItem().toString());
		}
		catch(Exception exception)
		{
		    iterationCount = 1000;
		}

		if(iterationCount > 7500)
		    Miscellaneous.showPromptDialog
			(Settings.this,
			 listener2,
			 "You have selected an elevated iteration count. " +
			 "If you proceed, the initialization process may " +
			 "require a significant amount of time to complete. " +
			 "Continue?");
		else
		    prepareCredentials();
	    }
	});

	button1 = (Button) findViewById(R.id.share_via_ozone);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		shareSipHashId(-1);
	    }
        });

	button1 = (Button) findViewById(R.id.siphash_help);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(Settings.this.isFinishing())
		    return;

		PopupWindow popupWindow = new PopupWindow(Settings.this);
		TextView textView1 = new TextView(Settings.this);
		float density = getApplicationContext().getResources().
		    getDisplayMetrics().density;

		textView1.setBackgroundColor(Color.rgb(232, 234, 246));
		textView1.setPaddingRelative
		    ((int) (10 * density),
		     (int) (10 * density),
		     (int) (10 * density),
		     (int) (10 * density));
		textView1.setText
		    ("A Smoke ID is a sequence of digits and " +
		     "letters assigned to a specific subscriber " +
		     "(public key pair). " +
		     "The tokens allow participants to exchange public " +
		     "key pairs via the Echo Public Key Sharing (EPKS) " +
		     "protocol. " +
		     "An example Smoke ID is ABAB-0101-CDCD-0202.");
		textView1.setTextSize(16);
		popupWindow.setContentView(textView1);
		popupWindow.setOutsideTouchable(true);

		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
		{
		    popupWindow.setHeight(450);
		    popupWindow.setWidth(700);
		}

		popupWindow.showAsDropDown(view);
	    }
	});

	CheckBox checkBox1 = (CheckBox) findViewById(R.id.automatic_refresh);

	checkBox1.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    if(isChecked)
		    {
			m_databaseHelper.writeSetting
			    (null, "automatic_neighbors_refresh", "true");
			startTimers();
		    }
		    else
		    {
			m_databaseHelper.writeSetting
			    (null, "automatic_neighbors_refresh", "false");
			stopTimers();
		    }
		}
	    });

	checkBox1 = (CheckBox) findViewById(R.id.echo);
	checkBox1.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    if(isChecked)
		    {
			m_databaseHelper.writeSetting
			    (null, "neighbors_echo", "true");
			State.getInstance().setNeighborsEcho(true);
		    }
		    else
		    {
			m_databaseHelper.writeSetting
			    (null, "neighbors_echo", "false");
			Kernel.getInstance().clearNeighborQueues();
			State.getInstance().setNeighborsEcho(false);
		    }
		}
	    });

	checkBox1 = (CheckBox) findViewById(R.id.neighbor_details);
	checkBox1.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    if(isChecked)
			m_databaseHelper.writeSetting
			    (null, "neighbors_details", "true");
		    else
			m_databaseHelper.writeSetting
			    (null, "neighbors_details", "false");

		    CheckBox checkBox1 = (CheckBox) findViewById
			(R.id.automatic_refresh);

		    if(!checkBox1.isChecked())
			populateNeighbors(null);
		}
	    });

	checkBox1 = (CheckBox) findViewById(R.id.overwrite);
	checkBox1.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    Button button1 = null;

		    button1 = (Button) findViewById(R.id.generate_pki);
		    button1.setEnabled(isChecked);
		    button1 = (Button) findViewById(R.id.set_password);
		    button1.setEnabled(isChecked);
		}
	    });

	checkBox1 = (CheckBox) findViewById(R.id.sleepless);
	checkBox1.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    Kernel.getInstance().setWakeLock(isChecked);
		    m_databaseHelper.writeSetting
			(null, "always_awake", isChecked ? "true" : "false");

		    TextView textView1 = (TextView) findViewById(R.id.about);
		    textView1.setText(About.s_about);
		    textView1.append("\n");
		    textView1.append
			("WakeLock Locked: " +
			 Miscellaneous.niceBoolean(Kernel.getInstance().
						   wakeLocked()));
		    textView1.append("\n");
		    textView1.append
			("WiFiLock Locked: " +
			 Miscellaneous.niceBoolean(Kernel.getInstance().
						   wifiLocked()));
		}
	    });

	spinner1.setOnItemSelectedListener
	    (new OnItemSelectedListener()
	    {
		@Override
		public void onItemSelected(AdapterView<?> parent,
					   View view,
					   int position,
					   long id)
		{
		    Spinner proxyType = (Spinner)
			findViewById(R.id.proxy_type);
		    TextView proxyIpAddress =
			(TextView) findViewById(R.id.proxy_ip_address);
		    TextView proxyPort = (TextView) findViewById
			(R.id.proxy_port);

		    if(position == 0)
		    {
			/*
			** Events may occur prematurely.
			*/

			boolean isAuthenticated = State.getInstance().
			    isAuthenticated();

			proxyIpAddress.setEnabled(isAuthenticated);
			proxyPort.setEnabled(isAuthenticated);
			proxyType.setEnabled(isAuthenticated);
		    }
		    else
		    {
			proxyIpAddress.setEnabled(false);
			proxyIpAddress.setText("");
			proxyPort.setEnabled(false);
			proxyPort.setText("");
			proxyType.setEnabled(false);
		    }
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent)
		{
		}
	    });
    }

    private void prepareCredentials()
    {
	if(Settings.this.isFinishing())
	    return;

	final Spinner spinner1 = (Spinner) findViewById(R.id.iteration_count);
	final Spinner spinner2 = (Spinner) findViewById
	    (R.id.pki_encryption_algorithm);
	final Spinner spinner3 = (Spinner) findViewById
	    (R.id.pki_signature_algorithm);
	final TextView textView1 = (TextView) findViewById
	    (R.id.password1);
	final TextView textView2 = (TextView) findViewById
	    (R.id.password2);
	int iterationCount = 1000;

	try
	{
	    iterationCount = Integer.parseInt
		(spinner1.getSelectedItem().toString());
	}
	catch(Exception exception)
	{
	    iterationCount = 1000;
	}

	final ProgressBar bar = (ProgressBar) findViewById
	    (R.id.generate_progress_bar);

	bar.setIndeterminate(true);
	bar.setVisibility(ProgressBar.VISIBLE);
	getWindow().setFlags
	    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
	     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
	Miscellaneous.enableChildren
	    (findViewById(R.id.linear_layout), false);

	class SingleShot implements Runnable
	{
	    private String m_encryptionAlgorithm = "";
	    private String m_error = "";
	    private String m_password = "";
	    private String m_signatureAlgorithm = "";
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

		if(m_signatureAlgorithm.equals("ECDSA"))
		    m_signatureAlgorithm = "EC";
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
		m_databaseHelper.reset();

		try
		{
		    chatEncryptionKeyPair = Cryptography.
			generatePrivatePublicKeyPair
			(m_encryptionAlgorithm, PKI_ENCRYPTION_KEY_SIZES[0], 0);

		    if(chatEncryptionKeyPair == null)
		    {
			m_error = "encryption " +
			    "generatePrivatePublicKeyPair() failure";
			s_cryptography.reset();
			return;
		    }

		    if(m_signatureAlgorithm.equals("EC"))
			chatSignatureKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    ("EC", PKI_SIGNATURE_KEY_SIZES[0], 0);
		    else
			chatSignatureKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    (m_signatureAlgorithm,
			     PKI_SIGNATURE_KEY_SIZES[1],
			     0);

		    if(chatSignatureKeyPair == null)
		    {
			m_error = "signature " +
			    "generatePrivatePublicKeyPair() failure";
			s_cryptography.reset();
			return;
		    }

		    encryptionKey = Cryptography.
			generateEncryptionKey
			(encryptionSalt,
			 m_password.toCharArray(),
			 m_iterationCount);

		    if(encryptionSalt == null)
		    {
			m_error = "generateEncryptionKey() failure";
			s_cryptography.reset();
			return;
		    }

		    macKey = Cryptography.generateMacKey
			(macSalt,
			 m_password.toCharArray(),
			 m_iterationCount);

		    if(macKey == null)
		    {
			m_error = "generateMacKey() failure";
			s_cryptography.reset();
			return;
		    }

		    /*
		    ** Prepare the Cryptography object's data.
		    */

		    s_cryptography.setChatEncryptionPublicKeyPair
			(chatEncryptionKeyPair);
		    s_cryptography.setChatSignaturePublicKeyPair
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
			(s_cryptography,
			 "identity",
			 Base64.encodeToString(s_cryptography.identity(),
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
			 "pki_chat_encryption_algorithm",
			 m_encryptionAlgorithm);
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

		    boolean e1 = s_cryptography.prepareSipHashIds();
		    boolean e2 = s_cryptography.prepareSipHashKeys();
		    boolean e3 = generateOzone
			(Miscellaneous.
			 prepareSipHashId(s_cryptography.sipHashId()));
		    byte saltedPassword[] = Cryptography.
			sha512(m_password.getBytes(),
			       encryptionSalt,
			       macSalt);

		    if(e1 && e2 && e3 && saltedPassword != null)
			m_databaseHelper.writeSetting
			    (null,
			     "saltedPassword",
			     Base64.encodeToString(saltedPassword,
						   Base64.DEFAULT));
		    else
		    {
			if(!e1)
			    m_error = "prepareSipHashIds() failure";
			else if(!e2)
			    m_error = "prepareSipHashKeys() failure";
			else
			    m_error = "sha512() failure";

			s_cryptography.reset();
		    }
		}
		catch(Exception exception)
		{
		    m_error = exception.getMessage().toLowerCase().trim();
		    s_cryptography.reset();
		}

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			bar.setVisibility(ProgressBar.INVISIBLE);
			getWindow().clearFlags
			    (WindowManager.LayoutParams.
			     FLAG_NOT_TOUCHABLE);
			Miscellaneous.enableChildren
			    (findViewById(R.id.linear_layout), true);
			disablePKIButtons();

			if(!m_error.isEmpty())
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error (" + m_error +
				 ") occurred while " +
				 "generating the confidential " +
				 "data.");
			else
			{
			    Kernel.getInstance().setWakeLock(true);
			    Settings.this.enableWidgets(true);
			    Settings.this.showWidgets();
			    State.getInstance().setAuthenticated(true);
			    spinner2.setSelection(1); // RSA
			    spinner3.setSelection(1); // RSA
			    textView1.requestFocus();
			    textView1.setText("");
			    textView2.setText("");
			    m_databaseHelper.writeNeighbor
				(s_cryptography,
				 "",
				 "",
				 "HTTP",
				 BuildConfig.TULIP_IPV4_HOST,
				 BuildConfig.TULIP_IPV4_PORT,
				 "",
				 "TCP",
				 "IPv4");
			    populateFancyKeyData();
			    populateOzone();
			    populateParticipants();
			    startKernel();
			    startTimers();
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

    private void preparePKI()
    {
	if(Settings.this.isFinishing())
	    return;

	final ProgressBar bar = (ProgressBar) findViewById
	    (R.id.generate_progress_bar);
	final Spinner spinner1 = (Spinner) findViewById
	    (R.id.pki_encryption_algorithm);
	final Spinner spinner2 = (Spinner) findViewById
	    (R.id.pki_signature_algorithm);

	bar.setIndeterminate(true);
	bar.setVisibility(ProgressBar.VISIBLE);
	getWindow().setFlags
	    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
	     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
	Miscellaneous.enableChildren
	    (findViewById(R.id.linear_layout), false);

	class SingleShot implements Runnable
	{
	    private String m_encryptionAlgorithm = "";
	    private String m_error = "";
	    private String m_signatureAlgorithm = "";

	    SingleShot(String encryptionAlgorithm, String signatureAlgorithm)
	    {
		m_encryptionAlgorithm = encryptionAlgorithm;
		m_signatureAlgorithm = signatureAlgorithm;

		if(m_signatureAlgorithm.equals("ECDSA"))
		    m_signatureAlgorithm = "EC";
	    }

	    @Override
	    public void run()
	    {
		KeyPair chatEncryptionKeyPair = null;
		KeyPair chatSignatureKeyPair = null;

		try
		{
		    chatEncryptionKeyPair = Cryptography.
			generatePrivatePublicKeyPair
			(m_encryptionAlgorithm, PKI_ENCRYPTION_KEY_SIZES[0], 0);

		    if(chatEncryptionKeyPair == null)
		    {
			m_error = "encryption " +
			    "generatePrivatePublicKeyPair() failure";
			s_cryptography.resetPKI();
			return;
		    }

		    if(m_signatureAlgorithm.equals("EC"))
			chatSignatureKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    ("EC", PKI_SIGNATURE_KEY_SIZES[0], 0);
		    else
			chatSignatureKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    (m_signatureAlgorithm,
			     PKI_SIGNATURE_KEY_SIZES[1],
			     0);

		    if(chatSignatureKeyPair == null)
		    {
			m_error = "signature " +
			    "generatePrivatePublicKeyPair() failure";
			s_cryptography.resetPKI();
			return;
		    }

		    /*
		    ** Prepare the Cryptography object's data.
		    */

		    s_cryptography.setChatEncryptionPublicKeyPair
			(chatEncryptionKeyPair);
		    s_cryptography.setChatSignaturePublicKeyPair
			(chatSignatureKeyPair);

		    /*
		    ** Record the data.
		    */

		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "identity",
			 Base64.encodeToString(s_cryptography.identity(),
					       Base64.DEFAULT));
		    m_databaseHelper.writeSetting
			(s_cryptography,
			 "pki_chat_encryption_algorithm",
			 m_encryptionAlgorithm);
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

		    boolean e1 = s_cryptography.prepareSipHashIds();
		    boolean e2 = s_cryptography.prepareSipHashKeys();
		    boolean e3 = generateOzone
			(Miscellaneous.
			 prepareSipHashId(s_cryptography.sipHashId()));

		    if(!e1 || !e2 || !e3)
		    {
			if(!e1)
			    m_error = "prepareSipHashIds() failure";
			else if(!e2)
			    m_error = "prepareSipHashKeys() failure";
			else
			    m_error = "sha512() failure";

			s_cryptography.resetPKI();
		    }
		}
		catch(Exception exception)
		{
		    m_error = exception.getMessage().toLowerCase().trim();
		    s_cryptography.resetPKI();
		}

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			bar.setVisibility(ProgressBar.INVISIBLE);
			getWindow().clearFlags
			    (WindowManager.LayoutParams.
			     FLAG_NOT_TOUCHABLE);
			Miscellaneous.enableChildren
			    (findViewById(R.id.linear_layout), true);
			disablePKIButtons();

			if(!m_error.isEmpty())
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error (" + m_error +
				 ") occurred while " +
				 "generating the PKI " +
				 "data.");
			else
			{
			    Settings.this.enableWidgets(true);
			    Settings.this.showWidgets();
			    spinner1.setSelection(1); // RSA
			    spinner2.setSelection(1); // RSA
			    populateFancyKeyData();
			    populateOzone();
			}
		    }
		});
	    }
	}

	Thread thread = new Thread
	    (new SingleShot(spinner1.getSelectedItem().toString(),
			    spinner2.getSelectedItem().toString()));

	thread.start();
    }

    private void requestKeysOf(final String oid)
    {
	if(Settings.this.isFinishing())
	    return;

	final ProgressBar bar = (ProgressBar) findViewById
	    (R.id.share_keys_progress_bar);

	bar.setIndeterminate(true);
	bar.setVisibility(ProgressBar.VISIBLE);
	getWindow().setFlags
	    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
	     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
	Miscellaneous.enableChildren
	    (findViewById(R.id.linear_layout), false);

	class SingleShot implements Runnable
	{
	    private String m_error = "";

	    SingleShot()
	    {
	    }

	    @Override
	    public void run()
	    {
		String sipHashId = m_databaseHelper.readSipHashIdString
		    (s_cryptography, oid);

		if(sipHashId.isEmpty())
		    m_error = "readSipHashIdString() failure";
		else
		{
		    byte bytes[] = Messages.pkpRequestMessage
			(s_cryptography, sipHashId);

		    if(bytes == null)
			m_error = "pkpRequestMessage() failure";
		    else if(!Kernel.getInstance().
			    enqueueMessage(Messages.
					   bytesToMessageString(bytes),
					   null))
			m_error = "enqueueMessage() failure";
		}

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			bar.setVisibility(ProgressBar.INVISIBLE);
			getWindow().clearFlags
			    (WindowManager.LayoutParams.
			     FLAG_NOT_TOUCHABLE);
			Miscellaneous.enableChildren
			    (findViewById(R.id.linear_layout), true);
			disablePKIButtons();

			if(!m_error.isEmpty())
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error (" + m_error + ") occurred while " +
				 "preparing a request of public key material.");
		    }
		});
	    }
	}

	Thread thread = new Thread(new SingleShot());

	thread.start();
    }

    private void shareKeysOf(final String oid)
    {
	if(Settings.this.isFinishing())
	    return;

	final ProgressBar bar = (ProgressBar) findViewById
	    (R.id.share_keys_progress_bar);

	bar.setIndeterminate(true);
	bar.setVisibility(ProgressBar.VISIBLE);
	getWindow().setFlags
	    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
	     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
	Miscellaneous.enableChildren
	    (findViewById(R.id.linear_layout), false);

	class SingleShot implements Runnable
	{
	    private String m_error = "";

	    SingleShot()
	    {
	    }

	    @Override
	    public void run()
	    {
		SipHashIdElement sipHashIdElement =
		    m_databaseHelper.readSipHashId(s_cryptography, oid);

		if(sipHashIdElement == null)
		    m_error = "readSipHashId() failure";
		else
		{
		    byte bytes[] = Messages.epksMessage
			(sipHashIdElement.m_encryptionPublicKey,
			 sipHashIdElement.m_signaturePublicKey,
			 sipHashIdElement.m_sipHashId,
			 sipHashIdElement.m_stream,
			 Messages.CHAT_KEY_TYPE);

		    if(bytes == null)
			m_error = "epksMessage() failure";
		    else if(!Kernel.getInstance().
			    enqueueMessage(Messages.
					   bytesToMessageString(bytes),
					   null))
			m_error = "enqueueMessage() failure";
		}

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			bar.setVisibility(ProgressBar.INVISIBLE);
			getWindow().clearFlags
			    (WindowManager.LayoutParams.
			     FLAG_NOT_TOUCHABLE);
			Miscellaneous.enableChildren
			    (findViewById(R.id.linear_layout), true);
			disablePKIButtons();

			if(!m_error.isEmpty())
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error (" + m_error + ") occurred while " +
				 "preparing to transfer public key material.");
		    }
		});
	    }
	}

	Thread thread = new Thread(new SingleShot());

	thread.start();
    }

    private void shareSipHashId(int oid)
    {
	if(Settings.this.isFinishing())
	    return;
	else if(!s_cryptography.hasValidOzoneKeys())
	{
	    Miscellaneous.showErrorDialog
		(Settings.this, "Please prepare Ozone credentials.");
	    return;
	}

	Kernel.getInstance().enqueueShareSipHashIdMessage(oid);
    }

    private void showAuthenticateActivity()
    {
	Intent intent = new Intent(Settings.this, Authenticate.class);

	startActivity(intent);
	finish();
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(Settings.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showDetailsOfParticipant(String oid)
    {
	if(Settings.this.isFinishing())
	    return;

	final ProgressBar bar = (ProgressBar) findViewById
	    (R.id.share_keys_progress_bar);

	bar.setIndeterminate(true);
	bar.setVisibility(ProgressBar.VISIBLE);
	getWindow().setFlags
	    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
	     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
	Miscellaneous.enableChildren
	    (findViewById(R.id.linear_layout), false);

	class SingleShot implements Runnable
	{
	    private String m_error = "";
	    private String m_name = "";
	    private String m_oid = "";
	    private String m_sipHashId = "";
	    private String m_string1 = "";
	    private String m_string2 = "";
	    private String m_strings[] = null;

	    SingleShot(String oid)
	    {
		m_oid = oid;
	    }

	    @Override
	    public void run()
	    {
		m_sipHashId = m_databaseHelper.readSipHashIdString
		    (s_cryptography, m_oid);

		/*
		** Retrieve everything that requires the SipHash.
		*/

		m_name = m_databaseHelper.nameFromSipHashId
		    (s_cryptography, m_sipHashId).trim();
		m_string1 = s_cryptography.fancyKeyInformationOutput
		    (m_databaseHelper.
		     publicEncryptionKeyForSipHashId(s_cryptography,
						     m_sipHashId)).trim();
		m_string2 = s_cryptography.fancyKeyInformationOutput
		    (m_databaseHelper.
		     publicSignatureKeyForSipHashId(s_cryptography,
						    m_sipHashId)).trim();
		m_strings = m_databaseHelper.
		    keysSigned(s_cryptography, m_sipHashId);

		if(m_name.isEmpty())
		    m_name = m_sipHashId;

		if(m_strings == null)
		    m_strings = new String[] {"false", "false"};

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			bar.setVisibility(ProgressBar.INVISIBLE);
			getWindow().clearFlags
			    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
			Miscellaneous.enableChildren
			    (findViewById(R.id.linear_layout), true);
			disablePKIButtons();

			PopupWindow popupWindow = new PopupWindow
			    (Settings.this);
			StringBuilder stringBuilder = new StringBuilder();
			TextView textView1 = new TextView(Settings.this);
			float density = getApplicationContext().getResources().
			    getDisplayMetrics().density;

			if(m_string1.isEmpty() || m_string2.isEmpty())
			{
			    if(m_sipHashId.isEmpty())
			    {
				stringBuilder.append
				    ("Unable to gather details ");
				stringBuilder.append
				    ("for the selected participant.");
			    }
			    else
			    {
				stringBuilder.append
				    ("Unable to gather details for ");
				stringBuilder.append(m_name);
				stringBuilder.append(" (");
				stringBuilder.append(m_sipHashId);
				stringBuilder.append(").");
			    }

			    textView1.setText(stringBuilder.toString());
			}
			else
			{
			    stringBuilder.append(m_name);
			    stringBuilder.append(" (");
			    stringBuilder.append(m_sipHashId);
			    stringBuilder.append(")\n");
			    stringBuilder.append("\nChat Encryption Key (");
			    textView1.append(stringBuilder.toString());

			    Spannable spannable = new SpannableStringBuilder
				(m_strings[0].equals("true") ?
				 "Signature Verified" :
				 "Signature Not Verified");

			    if(m_strings[0].equals("true"))
				spannable.setSpan
				    (new ForegroundColorSpan(Color.
							     rgb(46, 125, 50)),
				     0, spannable.length(),
				     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			    else
				spannable.setSpan
				    (new ForegroundColorSpan(Color.
							     rgb(198, 40, 40)),
				     0, spannable.length(),
				     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			    textView1.append(spannable);
			    stringBuilder.setLength(0);
			    stringBuilder.append(")\n");
			    stringBuilder.append(m_string1);
			    stringBuilder.append("\nChat Signature Key (");
			    textView1.append(stringBuilder);
			    spannable = new SpannableStringBuilder
				(m_strings[1].equals("true") ?
				 "Signature Verified" :
				 "Signature Not Verified");

			    if(m_strings[1].equals("true"))
				spannable.setSpan
				    (new ForegroundColorSpan(Color.
							     rgb(46, 125, 50)),
				     0, spannable.length(),
				     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			    else
				spannable.setSpan
				    (new ForegroundColorSpan(Color.
							     rgb(198, 40, 40)),
				     0, spannable.length(),
				     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			    textView1.append(spannable);
			    stringBuilder.setLength(0);
			    stringBuilder.append(")\n");
			    stringBuilder.append(m_string2);
			    textView1.append(stringBuilder.toString());
			}

			textView1.setBackgroundColor(Color.rgb(255, 255, 255));
			textView1.setPaddingRelative
			    ((int) (10 * density),
			     (int) (10 * density),
			     (int) (10 * density),
			     (int) (10 * density));
			textView1.setTextSize(16);
			popupWindow.setContentView(textView1);
			popupWindow.setOutsideTouchable(true);

			if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
			{
			    popupWindow.setHeight(450);
			    popupWindow.setWidth(700);
			}

			popupWindow.showAsDropDown
			    (findViewById(R.id.participants));
		    }
		});
	    }
	}

	Thread thread = new Thread(new SingleShot(oid));

	thread.start();
    }

    private void showFireActivity()
    {
	Intent intent = new Intent(Settings.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showMemberChatActivity()
    {
	Intent intent = new Intent(Settings.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showWidgets()
    {
	ViewGroup viewGroup = (ViewGroup) findViewById(R.id.linear_layout);
	int count = viewGroup.getChildCount();

	for(int i = 0; i < count; i++)
	{
	    View child = viewGroup.getChildAt(i);

	    if(child != findViewById(R.id.neighbors_scope_id))
		child.setVisibility(View.VISIBLE);
	}

	findViewById(R.id.generate_pki).setVisibility(View.VISIBLE);
	findViewById(R.id.overwrite).setVisibility(View.VISIBLE);
    }

    private void startKernel()
    {
	Kernel.getInstance();
    }

    private void startTimers()
    {
	if(m_scheduler == null)
	{
	    m_scheduler = Executors.newSingleThreadScheduledExecutor();
	    m_scheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			Settings.this.runOnUiThread
			    (new PopulateNeighbors(m_databaseHelper.
			     readNeighbors(s_cryptography)));
			m_databaseHelper.cleanDanglingOutboundQueued();
			m_databaseHelper.cleanDanglingParticipants();
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, TIMER_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    private void stopTimers()
    {
	if(m_scheduler != null)
	{
	    try
	    {
		m_scheduler.shutdown();
	    }
	    catch(Exception exception)
	    {
	    }

	    try
	    {
		if(!m_scheduler.awaitTermination(60, TimeUnit.SECONDS))
		    m_scheduler.shutdownNow();
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		m_scheduler = null;
	    }
	}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
	m_receiver = new SettingsBroadcastReceiver();
        setContentView(R.layout.activity_settings);

	try
	{
	    getSupportActionBar().setTitle("Smoke | Settings");
	}
	catch(Exception exception)
	{
	}

	Intent intent = new Intent(this, SmokeService.class);

	startService(intent);

	boolean isAuthenticated = State.getInstance().isAuthenticated();
        Button button1 = (Button) findViewById(R.id.add_neighbor);

        button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.add_participant);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.epks);
	button1.setCompoundDrawablesWithIntrinsicBounds
	    (R.drawable.share, 0, 0, 0);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.ozone_help);
	button1.setCompoundDrawablesWithIntrinsicBounds
	    (R.drawable.help, 0, 0, 0);
        button1 = (Button) findViewById(R.id.refresh_neighbors);
        button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.refresh_participants);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.reset_neighbor_fields);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.reset_participants_fields);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.save_ozone);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.share_via_ozone);
	button1.setCompoundDrawablesWithIntrinsicBounds
	    (R.drawable.share, 0, 0, 0);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.siphash_help);
	button1.setCompoundDrawablesWithIntrinsicBounds
	    (R.drawable.help, 0, 0, 0);

	CheckBox checkBox1 = null;

	checkBox1 = (CheckBox) findViewById(R.id.automatic_refresh);

	if(m_databaseHelper.readSetting(null, "automatic_neighbors_refresh").
	   isEmpty())
	{
	    checkBox1.setChecked(true);
	    m_databaseHelper.writeSetting
		(null, "automatic_neighbors_refresh", "true");
	}
	else if(m_databaseHelper.
		readSetting(null, "automatic_neighbors_refresh").equals("true"))
	    checkBox1.setChecked(true);
	else
	    checkBox1.setChecked(false);

	checkBox1 = (CheckBox) findViewById(R.id.echo);

	if(m_databaseHelper.readSetting(null, "neighbors_echo").equals("true"))
	    checkBox1.setChecked(true);
	else
	    checkBox1.setChecked(false);

	checkBox1 = (CheckBox) findViewById(R.id.neighbor_details);

	if(m_databaseHelper.readSetting(null, "neighbors_details").
	   equals("true"))
	    checkBox1.setChecked(true);
	else
	    checkBox1.setChecked(false);

	checkBox1 = (CheckBox) findViewById(R.id.sleepless);

	if(m_databaseHelper.readSetting(null, "always_awake").isEmpty())
	{
	    checkBox1.setChecked(true);
	    m_databaseHelper.writeSetting(null, "always_awake", "true");
	}
	else if(m_databaseHelper.readSetting(null, "always_awake").
		equals("true"))
	    checkBox1.setChecked(true);
	else
	    checkBox1.setChecked(false);

        RadioButton radioButton1 = (RadioButton) findViewById
	    (R.id.neighbors_ipv4);

        radioButton1.setEnabled(isAuthenticated);
        radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv6);
        radioButton1.setEnabled(isAuthenticated);

	Spinner spinner1 = (Spinner) findViewById(R.id.proxy_type);
        String array[] = new String[]
	{
	    "HTTP", "SOCKS"
	};

	spinner1.setEnabled(isAuthenticated);

	ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);

        spinner1.setAdapter(arrayAdapter);
        spinner1 = (Spinner) findViewById(R.id.neighbors_transport);
        array = new String[]
	{
	    "TCP", "UDP"
	};
        spinner1.setEnabled(isAuthenticated);
        arrayAdapter = new ArrayAdapter<>
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
	    "McEliece-Fujisaki", "RSA"
	};
	arrayAdapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	spinner1 = (Spinner) findViewById(R.id.pki_encryption_algorithm);
	spinner1.setAdapter(arrayAdapter);
	array = new String[]
	{
	    "ECDSA", "RSA"
	};
	arrayAdapter = new ArrayAdapter<>
	    (Settings.this, android.R.layout.simple_spinner_item, array);
	spinner1 = (Spinner) findViewById(R.id.pki_signature_algorithm);
	spinner1.setAdapter(arrayAdapter);

        RadioGroup radioGroup1 = (RadioGroup) findViewById
	    (R.id.neighbors_ipv_radio_group);

        radioGroup1.setOnCheckedChangeListener
	    (new RadioGroup.OnCheckedChangeListener()
	{
	    public void onCheckedChanged(RadioGroup group,
					 int checkedId)
	    {
		TextView textView1 = (TextView) findViewById
		    (R.id.neighbors_scope_id);

		if(checkedId == R.id.neighbors_ipv4)
		{
		    textView1.setEnabled(false);
		    textView1.setText("");
		    textView1 = (TextView) findViewById(R.id.neighbors_port);
		    textView1.setNextFocusDownId(R.id.proxy_ip_address);
		}
		else
		{
		    textView1.setEnabled(true);
		    textView1 = (TextView) findViewById(R.id.neighbors_port);
		    textView1.setNextFocusDownId(R.id.neighbors_scope_id);
		}
	    }
	});

	/*
	** Enable widgets.
	*/

	checkBox1 = (CheckBox) findViewById(R.id.overwrite);
	checkBox1.setChecked(false);
	checkBox1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.generate_pki);

	if(isAuthenticated)
	    button1.setEnabled(false);

	button1 = (Button) findViewById(R.id.set_password);

	if(isAuthenticated)
	    button1.setEnabled(false);

	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.about);
	textView1.setText(About.s_about);
	textView1.append("\n");
	textView1.append
	    ("WakeLock Locked: " +
	     Miscellaneous.niceBoolean(Kernel.getInstance().wakeLocked()));
	textView1.append("\n");
	textView1.append
	    ("WiFiLock Locked: " +
	     Miscellaneous.niceBoolean(Kernel.getInstance().wifiLocked()));
	textView1 = (TextView) findViewById(R.id.neighbors_scope_id);
        textView1.setEnabled(false);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
        textView1.setEnabled(isAuthenticated);
	textView1.setFilters(new InputFilter[] {s_portFilter});
        textView1.setText("4710");
        textView1 = (TextView) findViewById(R.id.neighbors_ip_address);

	if(isAuthenticated)
	    textView1.requestFocus();

	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.ozone);
	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.participant_name);
	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.participant_siphash_id);
	textView1.setEnabled(isAuthenticated);
	textView1.setFilters(new InputFilter[] {new InputFilter.AllCaps(),
						s_sipHashInputFilter});
	textView1.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
			       InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
	textView1 = (TextView) findViewById(R.id.password1);

	if(!isAuthenticated)
	    textView1.requestFocus();

	textView1.setText("");
        textView1 = (TextView) findViewById(R.id.password2);
        textView1.setText("");
	textView1 = (TextView) findViewById(R.id.proxy_ip_address);
	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.proxy_port);
	textView1.setEnabled(isAuthenticated);
	textView1.setFilters(new InputFilter[] {s_portFilter});
	prepareListeners();

	/*
	** Restore some settings.
	*/

	spinner1 = (Spinner) findViewById(R.id.iteration_count);

	if(index >= 0)
	    spinner1.setSelection(index);
	else
	    spinner1.setSelection(0);

	spinner1 = (Spinner) findViewById(R.id.pki_encryption_algorithm);
	spinner1.setSelection(1); // RSA
	spinner1 = (Spinner) findViewById(R.id.pki_signature_algorithm);

	if(spinner1.getAdapter().getCount() > 1)
	    spinner1.setSelection(1); // RSA

	populateFancyKeyData();

	if(isAuthenticated)
	{
	    checkBox1 = (CheckBox) findViewById(R.id.automatic_refresh);
	    textView1 = (TextView) findViewById(R.id.ozone);
	    textView1.setText
		(m_databaseHelper.readSetting(s_cryptography, "ozone_address"));
	    populateParticipants();
	    startKernel();

	    if(checkBox1.isChecked())
		startTimers();
	    else
		populateNeighbors(null);
	}
	else
	{
	    ViewGroup viewGroup = (ViewGroup) findViewById(R.id.linear_layout);
	    int count = viewGroup.getChildCount();

	    for(int i = 0; i < count; i++)
	    {
		View child = viewGroup.getChildAt(i);

		if(!(child == findViewById(R.id.password1) ||
		     child == findViewById(R.id.password2) ||
		     child == findViewById(R.id.password_separator) ||
		     child == findViewById(R.id.pki_layout) ||
		     child == findViewById(R.id.set_password_linear_layout)))
		    child.setVisibility(View.GONE);
	    }

	    findViewById(R.id.generate_pki).setVisibility(View.GONE);
	    findViewById(R.id.overwrite).setVisibility(View.GONE);
	}

	Kernel.getInstance().setWakeLock
	    (m_databaseHelper.readSetting(null, "always_awake").equals("true"));

	/*
	** Show the Authenticate activity if an account is present.
	*/

	if(!State.getInstance().isAuthenticated())
	    if(m_databaseHelper.accountPrepared())
		showAuthenticateActivity();
    }

    @Override
    protected void onDestroy()
    {
	super.onDestroy();
	stopTimers();
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem)
    {
	if(menuItem == null)
	    return false;

	final int groupId = menuItem.getGroupId();
	final int itemId = menuItem.getItemId();

	/*
	** Prepare a listener.
	*/

	final DialogInterface.OnCancelListener listener =
	    new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    String string = "";

		    switch(groupId)
		    {
		    case ContextMenuEnumerator.DELETE:
			switch(itemId)
			{
			default:
			    if(State.getInstance().
			       getString("dialog_accepted").equals("true"))
				if(m_databaseHelper.
				   deleteEntry(String.valueOf(itemId),
					       "siphash_ids"))
				{
				    State.getInstance().populateParticipants();
				    State.getInstance().
					removeChatCheckBoxOid(itemId);
				    State.getInstance().setString
					("member_chat_oid", "");
				    State.getInstance().setString
					("member_chat_siphash_id", "");
				    m_databaseHelper.writeSetting
					(s_cryptography,
					 "member_chat_oid", "");
				    m_databaseHelper.writeSetting
					(s_cryptography,
					 "member_chat_siphash_id", "");
				    populateParticipants();
				}

			    break;
			}

			break;
		    case ContextMenuEnumerator.DELETE_FIASCO_KEYS:
			if(State.getInstance().
			   getString("dialog_accepted").equals("true"))
			    if(m_databaseHelper.
			       deleteFiascoKeys(String.valueOf(itemId)))
				populateParticipants();

			break;
		    case ContextMenuEnumerator.NEW_NAME:
			string = State.getInstance().
			    getString("settings_participant_name_input");

			if(m_databaseHelper.
			   writeParticipantName(s_cryptography,
						string,
						itemId))
			{
			    State.getInstance().populateParticipants();
			    populateParticipants();
			}

			State.getInstance().removeKey
			    ("settings_participant_name_input");
			break;
		    }
		}
	    };

	/*
	** Regular expression?
	*/

	switch(groupId)
	{
	case ContextMenuEnumerator.DELETE:
	    Miscellaneous.showPromptDialog
		(Settings.this,
		 listener,
		 "Are you sure that you " +
		 "wish to delete the participant " +
		 menuItem.getTitle().toString().replace("Delete (", "").
		 replace(")", "") + "?");
	    break;
	case ContextMenuEnumerator.DELETE_FIASCO_KEYS:
	    Miscellaneous.showPromptDialog
		(Settings.this,
		 listener,
		 "Are you sure that you " +
		 "wish to delete the Fiasco keys of " +
		 menuItem.getTitle().toString().
		 replace("Delete Fiasco Keys (", "").replace(")", "") + "?");
	    break;
	case ContextMenuEnumerator.NEW_NAME:
	    Miscellaneous.showTextInputDialog
		(Settings.this,
		 listener,
		 "Please provide a new name for " +
		 menuItem.getTitle().toString().
		 replace("New Name (", "").
		 replace(")", "") + ".",
		 "Name");
	    break;
	case ContextMenuEnumerator.REQUEST_KEYS_VIA_OZONE:
	    requestKeysOf(String.valueOf(itemId));
	    break;
	case ContextMenuEnumerator.SHARE_KEYS_OF:
	    shareKeysOf(String.valueOf(itemId));
	    break;
	case ContextMenuEnumerator.SHARE_SMOKE_ID_OF:
	    shareSipHashId(itemId);
	    break;
	case ContextMenuEnumerator.VIEW_DETAILS:
	    showDetailsOfParticipant(String.valueOf(itemId));
	    break;
	}

	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
	int groupId = menuItem.getGroupId();
        int itemId = menuItem.getItemId();

	if(groupId == Menu.NONE)
	{
	    if(itemId == R.id.action_chat)
	    {
		m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
		showChatActivity();
		return true;
	    }
	    else if(itemId == R.id.action_fire)
	    {
		m_databaseHelper.writeSetting(null, "lastActivity", "Fire");
		showFireActivity();
		return true;
	    }
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

	menu.findItem(R.id.action_authenticate).setEnabled(!isAuthenticated);
	menu.findItem(R.id.action_chat).setEnabled
	    (State.getInstance().isAuthenticated());
	menu.findItem(R.id.action_fire).setEnabled
	    (State.getInstance().isAuthenticated());
	Miscellaneous.addMembersToMenu(menu, 3, 250);
	return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
				    View view,
				    ContextMenuInfo menuInfo)
    {
	if(menu == null || view == null)
	    return;

	Object tag1 = view.getTag(R.id.participants);
	Object tag2 = view.getTag(R.id.refresh_participants);

	if(tag1 != null && tag2 != null)
	{
	    super.onCreateContextMenu(menu, view, menuInfo);
	    menu.add(ContextMenuEnumerator.DELETE,
		     view.getId(),
		     0,
		     "Delete (" + tag1 + ")");
	    menu.add(ContextMenuEnumerator.DELETE_FIASCO_KEYS,
		     view.getId(),
		     0,
		     "Delete Fiasco Keys (" + tag1 + ")");
	    menu.add(ContextMenuEnumerator.NEW_NAME,
		     view.getId(),
		     0,
		     "New Name (" + tag1 + ")");

	    boolean validOzone = s_cryptography.hasValidOzoneKeys();

	    menu.add
		(ContextMenuEnumerator.REQUEST_KEYS_VIA_OZONE,
		 view.getId(),
		 0,
		 "Request Keys via Ozone (" + tag1 + ")").
		setEnabled(validOzone);
	    menu.add(ContextMenuEnumerator.SHARE_KEYS_OF,
		     view.getId(),
		     0,
		     "Share Keys Of (" + tag1 + ")").
		setEnabled((boolean) tag2);
	    menu.add(ContextMenuEnumerator.SHARE_SMOKE_ID_OF,
		     view.getId(),
		     0,
		     "Share Smoke ID Of (" + tag1 + ")").
		setEnabled(validOzone);
	    menu.add(ContextMenuEnumerator.VIEW_DETAILS,
		     view.getId(),
		     0,
		     "View Details (" + tag1 + ")");
	}
    }

    @Override
    public void onPause()
    {
	super.onPause();

	if(m_receiverRegistered)
	{
	    LocalBroadcastManager.getInstance(this).
		unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
	/*
	** Empty.
	*/
    }

    @Override
    public void onResume()
    {
	super.onResume();

	if(!m_receiverRegistered)
	{
	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction("org.purple.smoke.chat_message");
	    intentFilter.addAction("org.purple.smoke.populate_participants");
	    intentFilter.addAction
		("org.purple.smoke.siphash_share_confirmation");
	    LocalBroadcastManager.getInstance(this).
		registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}

	/*
	** Resume the last activity, if necessary.
	*/

	String str = m_databaseHelper.readSetting(null, "lastActivity");

	switch(str)
	{
	case "Chat":
	    showChatActivity();
	    break;
	case "Fire":
	    showFireActivity();
	    break;
	case "MemberChat":
	    showMemberChatActivity();
	    break;
	}
    }

    @Override
    public void onStop()
    {
	super.onStop();

	if(m_receiverRegistered)
	{
	    LocalBroadcastManager.getInstance(this).
		unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}
    }
}
