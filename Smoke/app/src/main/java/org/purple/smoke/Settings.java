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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Base64;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.security.KeyPair;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;

public class Settings extends AppCompatActivity
{
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

	    if(intent.getAction().equals("org.purple.smoke.chat_message"))
	    {
		String message = intent.getStringExtra
		    ("org.purple.smoke.message");
		String name = intent.getStringExtra("org.purple.smoke.name");
		String sipHashId = intent.getStringExtra
		    ("org.purple.smoke.sipHashId");

		if(message == null || name == null || sipHashId == null)
		    return;

		long sequence = intent.getLongExtra
		    ("org.purple.smoke.sequence", 1);
		long timestamp = intent.getLongExtra
		    ("org.purple.smoke.timestamp", 0);

		State.getInstance().logChatMessage
		    (message, name, sipHashId, sequence, timestamp);
		message = message.trim();

		TextView textView = new TextView(Settings.this);
		final PopupWindow popupWindow = new PopupWindow(Settings.this);

		if(name.length() > 15)
		{
		    name = name.substring(0, 15);

		    if(!name.endsWith("..."))
		    {
			if(name.endsWith(".."))
			    name += ".";
			else if(name.endsWith("."))
			    name += "..";
			else
			    name += "...";
		    }
		}

		if(message.length() > 15)
		{
		    message = message.substring(0, 15);

		    if(!message.endsWith("..."))
		    {
			if(message.endsWith(".."))
			    message += ".";
			else if(message.endsWith("."))
			    message += "..";
			else
			    message += "...";
		    }
		}

		textView.setBackgroundColor(Color.rgb(244, 200, 117));
		textView.setText
		    ("A message (" + message + ") from " + name +
		     " has arrived.");
		textView.setTextSize(16);
		popupWindow.setContentView(textView);
		popupWindow.setOutsideTouchable(true);

		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
		{
		    popupWindow.setHeight(300);
		    popupWindow.setWidth(450);
		}

		popupWindow.showAtLocation
		    (findViewById(R.id.main_layout),
		     Gravity.START | Gravity.TOP,
		     75,
		     75);

		Handler handler = new Handler();

		handler.postDelayed(new Runnable()
		{
		    @Override
		    public void run()
		    {
			popupWindow.dismiss();
		    }
		}, 10000); // 10 Seconds
	    }
	    else if(intent.getAction().
	       equals("org.purple.smoke.populate_participants"))
		populateParticipants();
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
		** Allow hexadecimal characters only.
		*/

		if(!((source.charAt(i) == ' ' || source.charAt(i) == '-') ||
		     (source.charAt(i) >= '0' && source.charAt(i) <= '9') ||
		     (source.charAt(i) >= 'A' && source.charAt(i) <= 'F') ||
		     (source.charAt(i) >= 'a' && source.charAt(i) <= 'f')))
		    return source.subSequence(start, i);

	    return null;
	}
    };
    private final static int OZONE_STREAM_CREATION_ITERATION_COUNT = 4096;
    private final static int TEXTVIEW_TEXT_SIZE = 13;
    private final static int TEXTVIEW_WIDTH = 500;
    private final static int PKI_SIGNATURE_KEY_SIZES[] =
        {384, 3072}; // ECDSA, RSA
    private final static int TIMER_INTERVAL = 2500; // 2.5 Seconds
    public final static int PKI_ENCRYPTION_KEY_SIZES[] = {3072}; // RSA

    private void addNeighbor()
    {
	CheckBox checkBox1 = (CheckBox) findViewById(R.id.automatic_refresh);
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
		(Settings.this,
		 "Please complete the IP Address field.");
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
	else if(!checkBox1.isChecked())
	    populateNeighbors();
    }

    private void addParticipant()
    {
	String string = "";
	StringBuilder stringBuilder = new StringBuilder();
	TextView textView1 = (TextView) findViewById
	    (R.id.participant_siphash_id);
	TextView textView2 = (TextView) findViewById(R.id.siphash_identity);

	string = textView1.getText().toString().
	    replace(" ", "").replace("-", "").replace(":", "");

	try
	{
	    for(int i = 0; i < string.length(); i += 2)
	    {
		stringBuilder.append(string.charAt(i));
		stringBuilder.append(string.charAt(i + 1));
		stringBuilder.append(':');
	    }
	}
	catch(Exception exception)
	{
	}

	if(stringBuilder.length() > 0 &&
	   stringBuilder.charAt(stringBuilder.length() - 1) == ':')
	    string = stringBuilder.substring(0, stringBuilder.length() - 1);
	else
	    string = stringBuilder.toString();

	if(string.length() != 23)
	{
	    Miscellaneous.showErrorDialog
		(Settings.this,
		 "A SipHash Identity must be of the form 0102-0304-0506-0708.");
	    return;
	}
	else if(textView2.getText().toString().toLowerCase().replace("-", "").
		endsWith(string.replace(":", "").toLowerCase()))
	{
	    Miscellaneous.showErrorDialog
		(Settings.this,
		 "Please do not assign your SipHash Identity.");
	    return;
	}

	final ProgressDialog dialog = new ProgressDialog(Settings.this);

	dialog.setCancelable(false);
	dialog.setIndeterminate(true);
	dialog.setMessage("Generating key material. Please be patient and " +
			  "do not rotate the device while the process " +
			  "executes.");
	dialog.show();

	class SingleShot implements Runnable
	{
	    private String m_name = "";
	    private String m_siphashId = "";
	    private boolean m_error = false;

	    SingleShot(String name, String sipHashId)
	    {
		m_name = name;
		m_siphashId = sipHashId;
	    }

	    @Override
	    public void run()
	    {
		if(!m_databaseHelper.writeSipHashParticipant(s_cryptography,
							     m_name,
							     m_siphashId))
		    m_error = true;

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			dialog.dismiss();

			if(m_error)
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error occurred while attempting " +
				 "to save the specified SipHash Identity.");
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
	button1 = (Button) findViewById(R.id.set_password);
	button1.setEnabled(checkBox1.isChecked());

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

    private void epks()
    {
	final ProgressDialog dialog = new ProgressDialog(Settings.this);

	dialog.setCancelable(false);
	dialog.setIndeterminate(true);
	dialog.setMessage
	    ("Transferring public key material. Please be patient " +
	     "and do not rotate the device while the process executes.");
	dialog.show();

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
		    m_databaseHelper.readSipHashIds(s_cryptography);

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

		    Kernel.getInstance().enqueueMessage
			(Messages.bytesToMessageString(bytes));
		}

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			dialog.dismiss();

			if(!m_error.isEmpty())
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error (" + m_error + ") occurred while " +
				 "preparing to transfer public key material. " +
				 "Please verify that participant SipHash " +
				 "Identities have been defined.");
		    }
		});

		if(arrayList != null)
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
	    stringBuilder.append("SipHash Chat Identity\n");

	    byte bytes[] = Miscellaneous.joinByteArrays
		(s_cryptography.chatEncryptionKeyPair().getPublic().
		 getEncoded(),
		 s_cryptography.chatSignatureKeyPair().getPublic().
		 getEncoded());

	    if(bytes != null)
		stringBuilder.append
		    (Miscellaneous.delimitString(Miscellaneous.
						 sipHashIdFromData(bytes).
						 replace(":", ""), '-', 4).
		     toUpperCase());
	    else
		stringBuilder.append("0000-0000-0000-0000");

	    textView1.setText(stringBuilder);
	    textView1.setTextIsSelectable(true);
	    textView1.setVisibility(View.VISIBLE);
	}
    }

    private void populateNeighbors()
    {
	ArrayList<NeighborElement> arrayList =
	    m_databaseHelper.readNeighbors(s_cryptography);
	final TableLayout tableLayout = (TableLayout)
	    findViewById(R.id.neighbors);

	if(arrayList == null || arrayList.size() == 0)
	{
	    tableLayout.removeAllViews();
	    return;
	}

	DecimalFormat decimalFormat = new DecimalFormat("0.00");
	StringBuilder stringBuilder = new StringBuilder();
	int i = 0;

	/*
	** Remove table entries which do not exist in smoke.db.
	*/

	for(i = 0; i < tableLayout.getChildCount(); i++)
	{
	    TableRow row = (TableRow) tableLayout.getChildAt(i);

	    if(row == null)
		continue;

	    TextView textView = (TextView) row.getChildAt(1);

	    if(textView == null)
		continue;

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

		if(textView.getText().toString().
		   contains(stringBuilder.toString()))
		{
		    found = true;
		    break;
		}
	    }

	    if(!found)
		tableLayout.removeView(row);
	}

	CheckBox checkBox = (CheckBox) findViewById(R.id.neighbor_details);

	i = 0;

	for(NeighborElement neighborElement : arrayList)
	{
	    if(neighborElement == null)
		continue;

	    Spinner spinner = null;
	    TableRow row = null;
	    TextView textView = null;

	    for(int j = 0; j < tableLayout.getChildCount(); j++)
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
		    textView = t;
		    break;
		}
	    }

	    if(textView == null)
	    {
		TableRow.LayoutParams layoutParams = new
		    TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);

		row = new TableRow(Settings.this);
		row.setId(neighborElement.m_oid);
		row.setLayoutParams(layoutParams);
		spinner = new Spinner(Settings.this);

		ArrayAdapter<String> arrayAdapter = null;
		String array[] = null;

		if(neighborElement.m_transport.equals("TCP"))
		    array = new String[]
		    {
			"Action",
			"Connect", "Delete", "Disconnect",
			"Reset SSL/TLS Credentials"
		    };
		else
		    array = new String[]
		    {
			"Action",
			"Connect", "Delete", "Disconnect"
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
			    if(position == 1) // Connect
				m_databaseHelper.neighborControlStatus
				    (s_cryptography,
				     "connect",
				     String.valueOf(parent.getId()));
			    else if(position == 2 && // Delete
				    m_databaseHelper.
				    deleteEntry(String.valueOf(parent.getId()),
						"neighbors"))
			    {
				/*
				** Prepare the kernel's neighbors container
				** if a neighbor was deleted as the OID
				** field may represent a recycled value.
				*/

				Kernel.getInstance().prepareNeighbors();

				TableRow row = (TableRow) findViewById
				    (parent.getId());

				tableLayout.removeView(row);
			    }
			    else if(position == 3) // Disconnect
				m_databaseHelper.neighborControlStatus
				    (s_cryptography,
				     "disconnect",
				     String.valueOf(parent.getId()));
			    else if(position == 4) // Reset SSL/TLS Credentials
				m_databaseHelper.neighborRecordCertificate
				    (s_cryptography,
				     String.valueOf(parent.getId()),
				     null);

			    parent.setSelection(0);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent)
			{
			}
		    });

		textView = new TextView(Settings.this);
	    }

	    if(neighborElement.m_status.equals("connected"))
		textView.setTextColor(Color.rgb(0, 100, 0)); // Dark Green
	    else
		textView.setTextColor(Color.rgb(139, 0, 0)); // Dark Red

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

	    if(checkBox.isChecked())
	    {
		if(neighborElement.m_remoteCertificate != null &&
		   neighborElement.m_remoteCertificate.length > 0)
		{
		    stringBuilder.append("\n");
		    stringBuilder.append
			("Remote Certificate's Fingerprint: ");
		    stringBuilder.append
			(Cryptography.
			 fingerPrint(neighborElement.m_remoteCertificate));
		}

		if(!neighborElement.m_sessionCipher.isEmpty())
		{
		    stringBuilder.append("\n");
		    stringBuilder.append("Session Cipher: ");
		    stringBuilder.append(neighborElement.m_sessionCipher);
		}
	    }

	    stringBuilder.append("\n");
	    stringBuilder.append("Temporary Queue Size: ");
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
	    stringBuilder.append("Uptime: ");

	    try
	    {
		stringBuilder.append
		    (decimalFormat.format(Long.
					  parseLong(neighborElement.m_uptime) /
					  6e+10));
	    }
	    catch(Exception exception)
	    {
		stringBuilder.append("0.00");
	    }

	    stringBuilder.append(" Minutes\n");
	    textView.setGravity(Gravity.CENTER_VERTICAL);
	    textView.setLayoutParams
		(new TableRow.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
	    textView.setText(stringBuilder);
	    textView.setTextSize(TEXTVIEW_TEXT_SIZE);
	    textView.setWidth(TEXTVIEW_WIDTH);

	    if(row != null)
	    {
		row.addView(spinner);
		row.addView(textView);
		tableLayout.addView(row, i);
	    }

	    i += 1;
	}

	arrayList.clear();
    }

    private void populateParticipants()
    {
	ArrayList<SipHashIdElement> arrayList =
	    m_databaseHelper.readSipHashIds(s_cryptography);
	TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.participants);

	tableLayout.removeAllViews();

	if(arrayList == null || arrayList.size() == 0)
	    return;

	for(int i = 0; i < arrayList.size(); i++)
	{
	    SipHashIdElement sipHashIdElement = arrayList.get(i);

	    if(sipHashIdElement == null)
		continue;

	    TableRow.LayoutParams layoutParams = new
		TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
	    TableRow row = new TableRow(Settings.this);

	    row.setLayoutParams(layoutParams);

	    for(int j = 0; j < 2; j++)
	    {
		TextView textView = new TextView(Settings.this);

		textView.setGravity(Gravity.CENTER_VERTICAL);
		textView.setId(sipHashIdElement.m_oid);
		textView.setLayoutParams
		    (new TableRow.LayoutParams(0,
					       LayoutParams.WRAP_CONTENT,
					       1));

		if(j == 0)
		    textView.setText(sipHashIdElement.m_name);
		else if(j == 1)
		{
		    if(sipHashIdElement.m_epksCompleted)
			textView.setCompoundDrawablesWithIntrinsicBounds
			    (R.drawable.lock, 0, 0, 0);
		    else
			textView.setCompoundDrawablesWithIntrinsicBounds
			    (R.drawable.lockless, 0, 0, 0);

		    textView.setCompoundDrawablePadding(5);
		    textView.setText
			(Miscellaneous.
			 delimitString(sipHashIdElement.m_sipHashId.
				       replace(":", ""), '-', 4).
			 toUpperCase());
		}

		textView.setTag(textView.getText());
		textView.setTextSize(TEXTVIEW_TEXT_SIZE);
		registerForContextMenu(textView);
		row.addView(textView);
	    }

	    if(i % 2 == 0)
		row.setBackgroundColor(Color.argb(100, 179, 230, 255));

	    tableLayout.addView(row, i);
	}

	arrayList.clear();
    }

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.epks);
	Spinner spinner1 = (Spinner) findViewById(R.id.neighbors_transport);

	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		epks();
	    }
        });

	button1 = (Button) findViewById(R.id.add_neighbor);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		addNeighbor();
	    }
        });

	button1 = (Button) findViewById(R.id.add_participant);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		addParticipant();
	    }
        });

	button1 = (Button) findViewById(R.id.refresh_neighbors);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		populateNeighbors();
	    }
        });

	button1 = (Button) findViewById(R.id.refresh_participants);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		populateParticipants();
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

		Intent intent = getIntent();

		finish();
		startActivity(intent);
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
		TextView textView = (TextView) findViewById(R.id.ozone);

		textView.setText("");
		m_databaseHelper.reset();
		populateFancyKeyData();
		populateNeighbors();
		populateParticipants();
		prepareCredentials();
	    }
	};

	button1 = (Button) findViewById(R.id.save_ozone);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		String string = "";
		TextView textView = (TextView) findViewById(R.id.ozone);
		boolean ok = true;
		byte bytes[] = null;
		byte salt[] = null;

		try
		{
		    string = textView.getText().toString().trim();
		    salt = Cryptography.sha512(string.getBytes("UTF-8"));

		    if(salt != null)
			bytes = Cryptography.pbkdf2
			    (salt,
			     string.toCharArray(),
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

		    if(bytes != null || string.isEmpty())
		    {
			m_databaseHelper.writeSetting
			    (s_cryptography, "ozone_address", string);

			if(string.isEmpty())
			{
			    m_databaseHelper.writeSetting
				(s_cryptography,
				 "ozone_address_stream",
				 "");
			    ok = true;
			    s_cryptography.setOzoneMacKey(null);
			}
			else
			{
			    m_databaseHelper.writeSetting
				(s_cryptography,
				 "ozone_address_stream",
				 Base64.encodeToString(bytes,
						       Base64.DEFAULT));
			    s_cryptography.setOzoneMacKey
				(Arrays.copyOfRange(bytes, 32, bytes.length));
			}
		    }
		}
		catch(Exception exception)
		{
		    ok = false;
		}

		if(!ok)
		{
		    Miscellaneous.showErrorDialog
			(Settings.this,
			 "An error occurred while processing the Ozone data. " +
			 "Perhaps a value should be provided.");
		    textView.requestFocus();
		}
	    }
	});

	button1 = (Button) findViewById(R.id.set_password);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		TextView textView1 = (TextView) findViewById(R.id.password1);
		TextView textView2 = (TextView) findViewById(R.id.password2);

		textView1.setSelectAllOnFocus(true);
		textView2.setSelectAllOnFocus(true);

		if(textView1.getText().length() < 8 ||
		   !textView1.getText().toString().
		   equals(textView2.getText().toString()))
		{
		    String error = "";

		    if(textView1.getText().length() < 8)
			error = "Each password must contain " +
			    "at least eight characters.";
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

	button1 = (Button) findViewById(R.id.siphash_help);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		PopupWindow popupWindow = new PopupWindow(Settings.this);
		TextView textView = new TextView(Settings.this);

		textView.setBackgroundColor(Color.rgb(135, 206, 250));
		textView.setText
		    ("A SipHash Identity is a sequence of digits and " +
		     "letters assigned to a specific subscriber " +
		     "(public key pair). " +
		     "The tokens allow participants to exchange public " +
		     "key pairs via the EPKS protocol. " +
		     "An example SipHash Identity is ABAB-0101-CDCD-0202.");
		textView.setTextSize(16);
		popupWindow.setContentView(textView);
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

		    CheckBox checkBox = (CheckBox) findViewById
			(R.id.automatic_refresh);

		    if(!checkBox.isChecked())
			populateNeighbors();
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
		    Button button = (Button) findViewById
			(R.id.set_password);

		    button.setEnabled(isChecked);
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

	dialog.setCancelable(false);
	dialog.setIndeterminate(true);
	dialog.setMessage
	    ("Generating confidential material. Please be patient and " +
	     "do not rotate the device while the process executes.");
	dialog.show();

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

		try
		{
		    chatEncryptionKeyPair = Cryptography.
			generatePrivatePublicKeyPair
			(m_encryptionAlgorithm, PKI_ENCRYPTION_KEY_SIZES[0]);

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
			    ("EC", PKI_SIGNATURE_KEY_SIZES[0]);
		    else
			chatSignatureKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    (m_signatureAlgorithm, PKI_SIGNATURE_KEY_SIZES[1]);

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
		    byte saltedPassword[] = Cryptography.
			sha512(m_password.getBytes(),
			       encryptionSalt,
			       macSalt);

		    if(e1 && e2 && saltedPassword != null)
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
			dialog.dismiss();

			if(!m_error.isEmpty())
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error (" + m_error +
				 ") occurred while " +
				 "generating the confidential " +
				 "data.");
			else
			{
			    Settings.this.enableWidgets(true);
			    State.getInstance().setAuthenticated(true);
			    spinner2.setSelection(0); // RSA
			    spinner3.setSelection(1); // RSA
			    textView1.requestFocus();
			    textView1.setText("");
			    textView2.setText("");
			    populateFancyKeyData();
			    populateParticipants();
			    startKernel();

			    if(m_databaseHelper.
			       readSetting(null, "automatic_neighbors_refresh").
			       equals("true"))
				startTimers();
			    else
				populateNeighbors();
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
	Intent intent = new Intent(Settings.this, Authenticate.class);

	startActivity(intent);
    }

    private void showChatActivity()
    {
	Intent intent = new Intent(Settings.this, Chat.class);

	startActivity(intent);
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
		private final Runnable runnable = new Runnable()
		{
		    @Override
		    public void run()
		    {
			populateNeighbors();
		    }
		};

		@Override
		public void run()
		{
		    Settings.this.runOnUiThread(runnable);
		}
	    }, 0, TIMER_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    private void stopTimers()
    {
	if(m_scheduler == null)
	    return;

	m_scheduler.shutdown();

	try
	{
	    m_scheduler.awaitTermination(60, TimeUnit.SECONDS);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_scheduler = null;
	}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	m_databaseHelper = Database.getInstance(getApplicationContext());
	m_receiver = new SettingsBroadcastReceiver();
        setContentView(R.layout.activity_settings);

	boolean isAuthenticated = State.getInstance().isAuthenticated();
        Button button1 = (Button) findViewById(R.id.add_neighbor);

        button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.add_participant);
	button1.setEnabled(isAuthenticated);
	button1 = (Button) findViewById(R.id.epks);
	button1.setEnabled(isAuthenticated);
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
	button1 = (Button) findViewById(R.id.siphash_help);
	button1.setCompoundDrawablesWithIntrinsicBounds
	    (R.drawable.help, 0, 0, 0);

	CheckBox checkBox1 = (CheckBox) findViewById
	    (R.id.automatic_refresh);

	if(m_databaseHelper.
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

	if(m_databaseHelper.
	   readSetting(null, "neighbors_details").equals("true"))
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
	    "RSA"
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
		int marginEnd = 0;
		TextView textView1 = (TextView) findViewById
		    (R.id.neighbors_scope_id);

		if(checkedId == R.id.neighbors_ipv4)
		{
		    marginEnd = 5;
		    textView1.setText("");
		    textView1.setVisibility(View.GONE);
		    textView1 = (TextView) findViewById(R.id.neighbors_port);
		    textView1.setNextFocusDownId(R.id.proxy_ip_address);
		}
		else
		{
		    textView1.setVisibility(View.VISIBLE);
		    textView1 = (TextView) findViewById(R.id.neighbors_port);
		    textView1.setNextFocusDownId(R.id.neighbors_scope_id);
		}

		LayoutParams layoutParams = new LayoutParams
		    (LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		TextView textView2 = (TextView) findViewById
		    (R.id.neighbors_port);

		layoutParams.setMarginEnd(marginEnd);
		textView2.setLayoutParams(layoutParams);
	    }
	});

	/*
	** Enable widgets.
	*/

	checkBox1 = (CheckBox) findViewById(R.id.overwrite);
	checkBox1.setChecked(!isAuthenticated);
	checkBox1.setEnabled(isAuthenticated);

	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.set_password);
	textView1.setEnabled(checkBox1.isChecked());
	textView1 = (TextView) findViewById(R.id.about);
	textView1.setText(About.about());
	textView1 = (TextView) findViewById(R.id.neighbors_scope_id);
        textView1.setEnabled(isAuthenticated);
        textView1.setVisibility(View.GONE);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
	textView1.setNextFocusDownId(R.id.proxy_ip_address);
        textView1.setEnabled(isAuthenticated);
	textView1.setFilters(new InputFilter[] { s_portFilter });

	LayoutParams layoutParams = new LayoutParams
	    (LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

	layoutParams.setMarginEnd(5);
	textView1.setLayoutParams(layoutParams);
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
	textView1.setFilters(new InputFilter[] { new InputFilter.AllCaps(),
						 s_sipHashInputFilter });
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
	textView1.setFilters(new InputFilter[] { s_portFilter });
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

	spinner1 = (Spinner) findViewById(R.id.pki_encryption_algorithm);
	spinner1.setSelection(0); // RSA
	spinner1 = (Spinner) findViewById(R.id.pki_signature_algorithm);

	if(spinner1.getAdapter().getCount() > 1)
	    spinner1.setSelection(1); // RSA

	populateFancyKeyData();

	if(isAuthenticated)
	{
	    checkBox1 = (CheckBox) findViewById(R.id.automatic_refresh);
	    textView1 = (TextView) findViewById(R.id.ozone);
	    textView1.setText
		(m_databaseHelper.readSetting(s_cryptography,
					      "ozone_address"));
	    populateNeighbors();
	    populateParticipants();
	    startKernel();

	    if(checkBox1.isChecked())
		startTimers();
	}
    }

    @Override
    protected void onDestroy()
    {
	super.onDestroy();
	stopTimers();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
	if(item == null)
	    return false;

	final int itemId = item.getItemId();

	/*
	** Prepare a listener.
	*/

	final DialogInterface.OnCancelListener listener =
	    new DialogInterface.OnCancelListener()
	{
	    public void onCancel(DialogInterface dialog)
	    {
		switch(itemId)
	        {
		default:
		    if(m_databaseHelper.deleteEntry(String.valueOf(itemId),
						    "siphash_ids"))
		    {
			State.getInstance().setChatCheckBoxSelected
			    (itemId, false);
			m_databaseHelper.cleanDanglingParticipants();
			populateParticipants();
		    }

		    break;
		}
	    }
	};

	/*
	** Regular expression?
	*/

	Miscellaneous.showPromptDialog
	    (Settings.this,
	     listener,
	     "Are you sure that you " +
	     "wish to delete the participant " +
	     item.getTitle().toString().replace("Delete (", "").
	     replace(")", "") + "?");
	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

	if(id == R.id.action_chat)
	{
	    m_databaseHelper.writeSetting(null, "lastActivity", "Chat");
	    showChatActivity();
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
	menu.findItem(R.id.action_chat).setEnabled
	    (State.getInstance().isAuthenticated());
	return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
				    View v,
				    ContextMenuInfo menuInfo)
    {
	Object tag = v.getTag();

	if(tag != null)
	{
	    super.onCreateContextMenu(menu, v, menuInfo);
	    menu.add(0, v.getId(), 0, "Delete (" + v.getTag() + ")");
	}
    }

    @Override
    public void onPause()
    {
	super.onPause();

	if(m_receiverRegistered)
	{
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
	    registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}
    }

    @Override
    public void onStop()
    {
	super.onStop();

	if(m_receiverRegistered)
	{
	    unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}
    }
}
