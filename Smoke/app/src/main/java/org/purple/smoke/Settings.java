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
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Base64;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.security.KeyPair;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;

public class Settings extends AppCompatActivity
{
    private Database m_databaseHelper = null;
    private ScheduledExecutorService m_scheduler = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
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

		if(!((source.charAt(i) >= '0' && source.charAt(i) <= '9') ||
		     (source.charAt(i) >= 'A' && source.charAt(i) <= 'F') ||
		     (source.charAt(i) >= 'a' && source.charAt(i) <= 'f')))
		    return "";

	    return null;
	}
    };
    private final static int TEXTVIEW_TEXT_SIZE = 13;
    private final static int TEXTVIEW_WIDTH = 500;
    private final static int PKI_ENCRYPTION_KEY_SIZES[] =
        {384, 3072}; // ECC, RSA
    private final static int PKI_SIGNATURE_KEY_SIZES[] =
        {384, 3072}; // ECDSA, RSA
    private final static int TIMER_INTERVAL = 2500; // 2.5 Seconds

    private void addNeighbor()
    {
	String ipVersion = "";
	final CheckBox checkBox = (CheckBox) findViewById
	    (R.id.automatic_refresh);
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
	    Miscellaneous.showErrorDialog
		(Settings.this,
		 "An error occurred while saving the neighbor information.");
	else if(!checkBox.isChecked());
	    populateNeighbors();
    }

    private void addParticipant()
    {
	String string = "";
	StringBuffer stringBuffer = new StringBuffer();
	final TextView textView1 = (TextView) findViewById
	    (R.id.participant_name);
	final TextView textView2 = (TextView) findViewById
	    (R.id.participant_siphash_id);
	final TextView textView3 = (TextView) findViewById
	    (R.id.siphash_identity);

	string = textView2.getText().toString().
	    replace(" ", "").replace("-", "").replace(":", "");

	try
	{
	    for(int i = 0; i < string.length(); i += 2)
	    {
		stringBuffer.append(string.charAt(i));
		stringBuffer.append(string.charAt(i + 1));
		stringBuffer.append(':');
	    }
	}
	catch(Exception exception)
	{
	}

	if(stringBuffer.length() > 0 &&
	   stringBuffer.charAt(stringBuffer.length() - 1) == ':')
	    string = stringBuffer.substring(0, stringBuffer.length() - 1);
	else
	    string = stringBuffer.toString();

	if(string.length() != 23)
	{
	    Miscellaneous.showErrorDialog
		(Settings.this,
		 "A SipHash ID must be of the form 0102-0304-0506-0708.");
	    return;
	}
	else if(textView3.getText().toString().replace("-", "").
		endsWith(string.replace(":", "")))
	{
	    Miscellaneous.showErrorDialog
		(Settings.this,
		 "Please do not attempt to add your own SipHash ID.");
	    return;
	}

	final ProgressDialog dialog = new ProgressDialog(Settings.this);

	dialog.setCancelable(false);
	dialog.setIndeterminate(true);
	dialog.setMessage("Generating key material. Please be patient...");
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
				 "to save the SipHash ID.");
			else
			    populateParticipants();
		    }
		});
	    }
	}

	Thread thread = new Thread
	    (new SingleShot(textView1.getText().toString(), string));

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

	RadioButton radioButton1 = null;

	radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv4);
	radioButton1.setEnabled(state);
	radioButton1 = (RadioButton) findViewById(R.id.neighbors_ipv6);
	radioButton1.setEnabled(state);

	Spinner spinner1 = null;

	spinner1 = (Spinner) findViewById(R.id.neighbors_transport);
	spinner1.setEnabled(state);

	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.neighbors_ip_address);
	textView1.setEnabled(state);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.neighbors_scope_id);
        textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.participant_name);
	textView1.setEnabled(state);
	textView1 = (TextView) findViewById(R.id.participant_siphash_id);
	textView1.setEnabled(state);
    }

    private void epks()
    {
	final ProgressDialog dialog = new ProgressDialog(Settings.this);

	dialog.setCancelable(false);
	dialog.setIndeterminate(true);
	dialog.setMessage
	    ("Transferring public key material. Please be patient...");
	dialog.show();

	class SingleShot implements Runnable
	{
	    private boolean m_error = false;

	    SingleShot()
	    {
	    }

	    @Override
	    public void run()
	    {
		ArrayList<SipHashIdElement> arrayList =
		    m_databaseHelper.readSipHashIds(s_cryptography);

		if(arrayList == null)
		    m_error = true;
		else
		    for(int i = 0; i < arrayList.size(); i++)
		    {
			SipHashIdElement sipHashIdElement = arrayList.get(i);

			if(sipHashIdElement == null)
			{
			    m_error = true;
			    break;
			}

			byte bytes[] = Messages.epksMessage
			    (s_cryptography,
			     "chat",
			     sipHashIdElement.m_sipHashId,
			     sipHashIdElement.m_stream);

			if(bytes == null)
			{
			    m_error = true;
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

			if(m_error)
			    Miscellaneous.showErrorDialog
				(Settings.this,
				 "An error occurred while " +
				 "preparing to transfer public key material. " +
				 "Please verify that participant SipHash " +
				 "identities have been defined.");
		    }
		});
	    }
	}

	Thread thread = new Thread(new SingleShot());

	thread.start();
    }

    private void populateFancyKeyData()
    {
	StringBuffer stringBuffer = null;
	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.chat_encryption_key_data);

	if(s_cryptography.chatEncryptionKeyPair() == null ||
	   s_cryptography.chatEncryptionKeyPair().getPublic() == null)
	    textView1.setVisibility(View.INVISIBLE);
	else
	{
	    stringBuffer = new StringBuffer();
	    stringBuffer.append("Chat Encryption Key\n");
	    stringBuffer.append
		(s_cryptography.
		 fancyKeyInformationOutput(s_cryptography.
					   chatEncryptionKeyPair()));
	    textView1.setText(stringBuffer);
	    textView1.setVisibility(View.VISIBLE);
	}

	textView1 = (TextView) findViewById(R.id.chat_signature_key_data);

	if(s_cryptography.chatSignatureKeyPair() == null ||
	   s_cryptography.chatSignatureKeyPair().getPublic() == null)
	    textView1.setVisibility(View.INVISIBLE);
	else
	{
	    if(stringBuffer == null)
		stringBuffer = new StringBuffer();
	    else
		stringBuffer.delete(0, stringBuffer.length());

	    stringBuffer.append("Chat Signature Key\n");
	    stringBuffer.append
		(s_cryptography.
		 fancyKeyInformationOutput(s_cryptography.
					   chatSignatureKeyPair()));
	    textView1.setText(stringBuffer);
	    textView1.setVisibility(View.VISIBLE);
	}

	textView1 = (TextView) findViewById(R.id.siphash_identity);

	if(stringBuffer == null)
	    textView1.setVisibility(View.INVISIBLE);
	else
	{
	    stringBuffer.delete(0, stringBuffer.length());
	    stringBuffer.append("SipHash Chat Identity\n");

	    byte bytes[] = Miscellaneous.joinByteArrays
		(s_cryptography.chatEncryptionKeyPair().getPublic().
		 getEncoded(),
		 s_cryptography.chatSignatureKeyPair().getPublic().
		 getEncoded());

	    if(bytes != null)
		stringBuffer.append
		    (Miscellaneous.delimitString(Miscellaneous.
						 sipHashIdFromData(bytes).
						 replace(":", ""), '-', 4).
		     toUpperCase());
	    else
		stringBuffer.append("0000-0000-0000-0000");

	    textView1.setText(stringBuffer);
	    textView1.setTextIsSelectable(true);
	    textView1.setVisibility(View.VISIBLE);
	}
    }

    private void populateNeighbors()
    {
	ArrayList<NeighborElement> arrayList =
	    m_databaseHelper.readNeighbors(s_cryptography);
	final TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.neighbors);

	if(arrayList == null || arrayList.size() == 0)
	{
	    tableLayout.removeAllViews();
	    return;
	}

	CheckBox checkBox = (CheckBox) findViewById
	    (R.id.neighbor_details);
	StringBuffer stringBuffer = new StringBuffer();

	for(int i = 0; i < arrayList.size(); i++)
	{
	    NeighborElement neighborElement = arrayList.get(i);

	    if(neighborElement == null)
		continue;

	    Spinner spinner = null;
	    TableRow row = null;
	    TextView textView = null;

	    for(int j = 0; j < tableLayout.getChildCount(); j++)
	    {
		TableRow r = (TableRow) tableLayout.getChildAt(j);
		TextView t = (TextView) r.getChildAt(1);

		if(t == null)
		    continue;

		stringBuffer.setLength(0);
		stringBuffer.append(neighborElement.m_remoteIpAddress);
		stringBuffer.append(":");
		stringBuffer.append(neighborElement.m_remotePort);
		stringBuffer.append(":");
		stringBuffer.append(neighborElement.m_transport);

		if(t.getText().toString().contains(stringBuffer.toString()))
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

	    stringBuffer.setLength(0);
	    stringBuffer.append("Control: ");

	    try
	    {
		stringBuffer.append
		    (neighborElement.m_statusControl.substring(0, 1).
		     toUpperCase());
		stringBuffer.append
		    (neighborElement.m_statusControl.substring(1));
	    }
	    catch(Exception exception)
	    {
		stringBuffer.append("Disconnect");
	    }

	    stringBuffer.append("\n");
	    stringBuffer.append("Status: ");

	    try
	    {
		stringBuffer.append
		    (neighborElement.m_status.substring(0, 1).toUpperCase());
		stringBuffer.append(neighborElement.m_status.substring(1));
	    }
	    catch(Exception exception)
	    {
		stringBuffer.append("Disconnected");
	    }

	    stringBuffer.append("\n");
	    stringBuffer.append(neighborElement.m_remoteIpAddress);
	    stringBuffer.append(":");
	    stringBuffer.append(neighborElement.m_remotePort);
	    stringBuffer.append(":");
	    stringBuffer.append(neighborElement.m_transport);

	    if(!neighborElement.m_localIpAddress.isEmpty() &&
	       !neighborElement.m_localPort.isEmpty())
	    {
		stringBuffer.append("\n");
		stringBuffer.append(neighborElement.m_localIpAddress);
		stringBuffer.append(":");
		stringBuffer.append(neighborElement.m_localPort);
	    }

	    if(checkBox.isChecked())
	    {
		if(!neighborElement.m_remoteCertificate.isEmpty())
		{
		    stringBuffer.append("\n");
		    stringBuffer.append
			("Remote Certificate's Public Key Fingerprint: ");
		    stringBuffer.append(neighborElement.m_remoteCertificate);
		}

		if(!neighborElement.m_sessionCipher.isEmpty())
		{
		    stringBuffer.append("\n");
		    stringBuffer.append("Session Cipher: ");
		    stringBuffer.append(neighborElement.m_sessionCipher);
		}
	    }

	    stringBuffer.append("\n");
	    stringBuffer.append("In: ");
	    stringBuffer.append
		(Miscellaneous.
		 formattedDigitalInformation(neighborElement.m_bytesRead));
	    stringBuffer.append("\n");
	    stringBuffer.append("Out: ");
	    stringBuffer.append
		(Miscellaneous.
		 formattedDigitalInformation(neighborElement.m_bytesWritten));
	    stringBuffer.append("\n");
	    stringBuffer.append("Uptime: ");

	    try
	    {
		DecimalFormat decimalFormat = new DecimalFormat("0.00");

		stringBuffer.append
		    (decimalFormat.format(Integer.
					  parseInt(neighborElement.m_uptime) /
					  60000.0));
	    }
	    catch(Exception exception)
	    {
		stringBuffer.append("0.00");
	    }

	    stringBuffer.append(" Minutes\n");
	    textView.setGravity(Gravity.CENTER_VERTICAL);
	    textView.setLayoutParams
		(new TableRow.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
	    textView.setText(stringBuffer);
	    textView.setTextSize(TEXTVIEW_TEXT_SIZE);
	    textView.setWidth(TEXTVIEW_WIDTH);

	    if(row != null)
	    {
		row.addView(spinner);
		row.addView(textView);
		tableLayout.addView(row, i);
	    }
	}
    }

    private void populateParticipants()
    {
	ArrayList<SipHashIdElement> arrayList =
	    m_databaseHelper.readSipHashIds(s_cryptography);
	final TableLayout tableLayout = (TableLayout) findViewById
	    (R.id.participants);

	tableLayout.removeAllViews();

	if(arrayList == null)
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

		textView.setId(sipHashIdElement.m_oid);
		textView.setLayoutParams
		    (new TableRow.LayoutParams(0,
					       LayoutParams.WRAP_CONTENT,
					       1));

		if(j == 0)
		    textView.setText(sipHashIdElement.m_name);
		else
		    textView.setText
			(Miscellaneous.
			 delimitString(sipHashIdElement.m_sipHashId.
				       replace(":", ""), '-', 4).
			 toUpperCase());

		textView.setTag(textView.getText());
		textView.setTextSize(TEXTVIEW_TEXT_SIZE);
		registerForContextMenu(textView);
		row.addView(textView);
	    }

	    tableLayout.addView(row, i);
	}
    }

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.epks);

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

		final Intent intent = getIntent();

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
					       "data will be removed.");
	    }
	});

	button1 = (Button) findViewById(R.id.reset_neighbor_fields);
        button1.setOnClickListener(new View.OnClickListener()
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

	button1 = (Button) findViewById(R.id.reset_participants_fields);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		final TextView textView1 = (TextView) findViewById
		    (R.id.participant_name);
		final TextView textView2 = (TextView) findViewById
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
		m_databaseHelper.reset();
		populateFancyKeyData();
		populateParticipants();
		prepareCredentials();
	    }
	};

	button1 = (Button) findViewById(R.id.set_password);
        button1.setOnClickListener(new View.OnClickListener()
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
		    Miscellaneous.showPromptDialog(Settings.this,
						   listener2,
						   "Are you sure that you " +
						   "wish to create new " +
						   "credentials? Existing " +
						   "database values will be " +
						   "removed.");
		else
		    prepareCredentials();
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
	    ("Generating confidential material. Please be patient...");
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

		if(m_encryptionAlgorithm.equals("ECC"))
		    m_encryptionAlgorithm = "EC";

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
		    if(m_encryptionAlgorithm.equals("EC"))
			chatEncryptionKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    ("EC", PKI_ENCRYPTION_KEY_SIZES[0]);
		    else
			chatEncryptionKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    (m_encryptionAlgorithm,
			     PKI_ENCRYPTION_KEY_SIZES[1]);


		    if(m_signatureAlgorithm.equals("EC"))
			chatSignatureKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    ("EC", PKI_SIGNATURE_KEY_SIZES[0]);
		    else
			chatSignatureKeyPair = Cryptography.
			    generatePrivatePublicKeyPair
			    (m_signatureAlgorithm, PKI_SIGNATURE_KEY_SIZES[1]);

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
			m_error = true;
			s_cryptography.reset();
		    }
		}
		catch(Exception exception)
		{
		    m_error = true;
		    s_cryptography.reset();
		}

		Settings.this.runOnUiThread(new Runnable()
		{
		    @Override
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
			    State.getInstance().setAuthenticated(true);
			    spinner1.setSelection(0);
			    spinner2.setSelection(1); // RSA
			    spinner3.setSelection(1); // RSA
			    textView1.requestFocus();
			    textView1.setText("");
			    textView2.setText("");
			    populateFancyKeyData();
			    startKernel();

			    if(m_databaseHelper.
			       readSetting(null, "automatic_neighbors_refresh").
			       equals("true"))
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
	    "ECC", "RSA"
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

	TextView textView1 = null;

	textView1 = (TextView) findViewById(R.id.neighbors_scope_id);
        textView1.setEnabled(isAuthenticated);
        textView1.setVisibility(View.GONE);
        textView1 = (TextView) findViewById(R.id.neighbors_port);
        textView1.setEnabled(isAuthenticated);
        textView1.setText("4710");
        textView1 = (TextView) findViewById(R.id.neighbors_ip_address);

	if(isAuthenticated)
	    textView1.requestFocus();

	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.participant_name);
	textView1.setEnabled(isAuthenticated);
	textView1 = (TextView) findViewById(R.id.participant_siphash_id);
	textView1.setEnabled(isAuthenticated);
	textView1.setFilters(new InputFilter[] { s_sipHashInputFilter });
	textView1 = (TextView) findViewById(R.id.password1);

	if(!isAuthenticated)
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

	spinner1 = (Spinner) findViewById(R.id.pki_encryption_algorithm);

	if(spinner1.getAdapter().getCount() > 1)
	    spinner1.setSelection(1); // RSA

	spinner1 = (Spinner) findViewById(R.id.pki_signature_algorithm);

	if(spinner1.getAdapter().getCount() > 1)
	    spinner1.setSelection(1); // RSA

	populateFancyKeyData();

	if(isAuthenticated)
	{
	    checkBox1 = (CheckBox) findViewById(R.id.automatic_refresh);
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
			populateParticipants();

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
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
	/*
	** Empty.
	*/
    }
}
