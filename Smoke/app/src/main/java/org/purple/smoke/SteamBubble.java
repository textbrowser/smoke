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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class SteamBubble extends View
{
    private Button m_control = null;
    private Context m_context = null;
    private ImageButton m_menuButton = null;
    private LinearLayout m_layoutA = null;
    private LinearLayout m_layoutB = null;
    private ProgressBar m_progress = null;
    private SeekBar m_readInterval = null;
    private Steam m_steam = null;
    private String m_controlString = "";
    private Switch m_details = null;
    private TextView m_destination = null;
    private TextView m_digest = null;
    private TextView m_eta = null;
    private TextView m_fileIdentity = null;
    private TextView m_fileName = null;
    private TextView m_fileSize = null;
    private TextView m_keyStreamDigest = null;
    private TextView m_readIntervalLabel = null;
    private TextView m_sent = null;
    private TextView m_status = null;
    private TextView m_transferRate = null;
    private View m_direction = null;
    private View m_keyExchangeStatus = null;
    private View m_separator = null;
    private View m_view = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static DecimalFormat s_decimalFormat =
	new DecimalFormat("0.00");
    private final static NumberFormat s_numberFormat =
	NumberFormat.getInstance();
    private int m_oid = -1;

    private String formatSize(long size)
    {
	return Miscellaneous.formattedDigitalInformation(String.valueOf(size));
    }

    private String niceBytes(long size)
    {
	return s_numberFormat.format(size);
    }

    private String prettyEta(String transferRate,
			     long fileSize,
			     long readOffset)
    {
	if(fileSize == readOffset)
	    return "ETA: completed";

	try
	{
	    double rate = Double.parseDouble
		(transferRate.substring(0, transferRate.indexOf(' ')));

	    if(transferRate.contains("GiB"))
		rate *= 1073741824.0;
	    else if(transferRate.contains("KiB"))
		rate *= 1024.0;
	    else if(transferRate.contains("MiB"))
		rate *= 1048576.0;

	    if(rate > 0.0)
		return "ETA: " +
		    s_decimalFormat.format(((fileSize - readOffset) / rate) /
					   60.0) +
		    " minutes";
	}
	catch(Exception exception)
	{
	}

	return "ETA: stalled";
    }

    public SteamBubble(Context context, Steam steam, ViewGroup viewGroup)
    {
	super(context);
	m_context = context;
	m_steam = steam;

	LayoutInflater inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);

	m_view = inflater.inflate(R.layout.steam_bubble, viewGroup, false);
	m_control = (Button) m_view.findViewById(R.id.control);
	m_control.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		switch(m_controlString)
		{
		case "pause":
		    m_eta.setText("ETA: stalled");
		    s_databaseHelper.writeSteamStatus
			(s_cryptography, "paused", Miscellaneous.RATE, m_oid);
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_status");
		    break;
		case "resume":
		    s_databaseHelper.writeSteamStatus("transferring", m_oid);
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_status");
		    break;
		case "rewind":
		    s_databaseHelper.writeSteamStatus
			(s_cryptography,
			 "rewind",
			 Miscellaneous.RATE,
			 m_oid,
			 0);
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_status");
		    break;
		default:
		    break;
		}
	    }
        });
	m_destination = (TextView) m_view.findViewById(R.id.destination);
	m_details = (Switch) m_view.findViewById(R.id.details);
	m_digest = (TextView) m_view.findViewById(R.id.digest);
	m_direction = m_view.findViewById(R.id.direction);
	m_eta = (TextView) m_view.findViewById(R.id.eta);
	m_fileIdentity = (TextView) m_view.findViewById(R.id.file_identity);
	m_fileName = (TextView) m_view.findViewById(R.id.filename);
	m_fileSize = (TextView) m_view.findViewById(R.id.file_size);
	m_keyExchangeStatus = m_view.findViewById(R.id.key_exchange_status);
	m_keyStreamDigest = (TextView)
	    m_view.findViewById(R.id.keystream_digest);
	m_layoutA = (LinearLayout) m_view.findViewById(R.id.layout_a);
	m_layoutA.setVisibility(LinearLayout.GONE);
	m_layoutB = (LinearLayout) m_view.findViewById(R.id.layout_b);
	m_layoutB.setVisibility(LinearLayout.GONE);
	m_menuButton = (ImageButton) m_view.findViewById(R.id.menu);
	m_menuButton.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		m_steam.showContextMenu(view);
	    }
        });
	m_progress = (ProgressBar) m_view.findViewById(R.id.progress_bar);
	m_readInterval = (SeekBar) m_view.findViewById(R.id.read_interval);
	m_readInterval.setOnSeekBarChangeListener(new OnSeekBarChangeListener()
	{
	    @Override
	    public void onProgressChanged(SeekBar seekBar,
					  int progress,
					  boolean fromUser)
	    {
		switch(progress)
		{
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
		    String text = "4 reads / s";
		    int readInterval = 4;

		    switch(progress)
		    {
		    case 1:
			readInterval = 10;
			text = "10 reads / s";
			break;
		    case 2:
			readInterval = 20;
			text = "20 reads / s";
			break;
		    case 3:
			readInterval = 50;
			text = "50 reads / s";
			break;
		    case 4:
			readInterval = 100;
			text = "100 reads / s";
			break;
		    }

		    m_readIntervalLabel.setText(text);

		    if(fromUser)
		    {
			s_databaseHelper.writeSteamStatus
			    (s_cryptography, m_oid, readInterval);
			Miscellaneous.sendBroadcast
			    ("org.purple.smoke.steam_read_interval_change",
			     m_oid,
			     readInterval);
		    }

		    break;
		default:
		    break;
		}
	    }

	    @Override
	    public void onStartTrackingTouch(SeekBar seekBar)
	    {
	    }

	    @Override
	    public void onStopTrackingTouch(SeekBar seekBar)
	    {
	    }
	});
	m_readIntervalLabel = (TextView) m_view.findViewById
	    (R.id.read_interval_label);
	m_readIntervalLabel.setText("4 reads / s");
	m_sent = (TextView) m_view.findViewById(R.id.sent);
	m_separator = m_view.findViewById(R.id.separator);
	m_status = (TextView) m_view.findViewById(R.id.status);
	m_transferRate = (TextView) m_view.findViewById(R.id.transfer_rate);
	m_view.setId(-1);
	s_numberFormat.setGroupingUsed(true);
    }

    public View view()
    {
	return m_view;
    }

    public void setData(SteamElement steamElement, int count, int position)
    {
	if(steamElement == null)
	    return;

	m_oid = steamElement.m_oid;

	switch(steamElement.m_status)
	{
	case "completed":
	    if(steamElement.m_direction == SteamElement.DOWNLOAD)
	    {
		m_control.setText("Pause");
		m_controlString = "";
	    }
	    else
	    {
		m_control.setText("Rewind");
		m_controlString = "rewind";
	    }

	    m_progress.setVisibility(View.GONE);
	    break;
	case "paused":
	    m_control.setText("Resume");
	    m_controlString = "resume";
	    m_progress.setVisibility(View.GONE);
	    break;
	case "receiving":
	    m_progress.setVisibility(View.VISIBLE);
	    break;
	case "transferring":
	    m_control.setText("Pause");
	    m_controlString = "pause";
	    m_progress.setVisibility(View.VISIBLE);
	    break;
	default:
	    break;
	}

	if(steamElement.m_destination.equals(Steam.OTHER))
	{
	    /*
	    ** Simple Steams.
	    */

	    int oid = Kernel.getInstance().nextSimpleSteamOid();

	    m_control.setEnabled(m_oid == oid || oid == -1);
	    m_control.setVisibility(View.VISIBLE);
	    m_destination.setText("Destination: " + steamElement.m_destination);
	    m_direction.setBackgroundResource(R.drawable.upload);
	    m_keyExchangeStatus.setVisibility(View.GONE);
	    m_keyStreamDigest.setVisibility(View.GONE);
	    m_readInterval.setVisibility(View.VISIBLE);
	    m_readIntervalLabel.setVisibility(View.VISIBLE);
	    m_sent.setText("Sent: " + formatSize(steamElement.m_readOffset));
	}
	else if(steamElement.m_direction == SteamElement.DOWNLOAD)
	{
	    m_control.setVisibility(View.GONE);
	    m_destination.setText("Origin: " + steamElement.m_destination);
	    m_direction.setBackgroundResource(R.drawable.download);
	    m_keyExchangeStatus.setBackgroundResource
		(steamElement.m_keyStream != null &&
		 steamElement.m_keyStream.length ==
		 Cryptography.CIPHER_HASH_KEYS_LENGTH ?
		 R.drawable.lock : R.drawable.unlock);
	    m_keyExchangeStatus.setVisibility(View.VISIBLE);
	    m_keyStreamDigest.setVisibility(View.VISIBLE);
	    m_readInterval.setVisibility(View.GONE);
	    m_readIntervalLabel.setVisibility(View.GONE);
	    m_sent.setText
		("Received: " +
		 formatSize(steamElement.m_readOffset) +
		 " (" +
		 niceBytes(steamElement.m_readOffset) +
		 ")");
	}
	else
	{
	    /*
	    ** Full Steams.
	    */

	    m_control.setEnabled
		(steamElement.m_keyStream != null &&
		 steamElement.m_keyStream.length ==
		 Cryptography.CIPHER_HASH_KEYS_LENGTH);
	    m_control.setVisibility(View.VISIBLE);
	    m_destination.setText("Destination: " + steamElement.m_destination);
	    m_direction.setBackgroundResource(R.drawable.upload);
	    m_keyExchangeStatus.setBackgroundResource
		(steamElement.m_keyStream != null &&
		 steamElement.m_keyStream.length ==
		 Cryptography.CIPHER_HASH_KEYS_LENGTH ?
		 R.drawable.lock : R.drawable.unlock);
	    m_keyExchangeStatus.setVisibility(View.VISIBLE);
	    m_keyStreamDigest.setVisibility(View.VISIBLE);
	    m_readInterval.setVisibility(View.GONE);
	    m_sent.setText("Sent: " + formatSize(steamElement.m_readOffset));
	}

	m_details.setOnCheckedChangeListener(null);
	m_details.setChecked(State.getInstance().steamDetailsState(m_oid));
	m_details.setOnCheckedChangeListener
	    (new CompoundButton.OnCheckedChangeListener()
	    {
		@Override
		public void onCheckedChanged
		    (CompoundButton buttonView, boolean isChecked)
		{
		    State.getInstance().setSteamDetailsState(isChecked, m_oid);

		    if(isChecked)
		    {
			m_layoutA.setVisibility(LinearLayout.VISIBLE);
			m_layoutB.setVisibility(LinearLayout.VISIBLE);
		    }
		    else
		    {
			m_layoutA.setVisibility(LinearLayout.GONE);
			m_layoutB.setVisibility(LinearLayout.GONE);
		    }
		}
	    });
	m_digest.setText
	    ("SHA-256: " +
	     Miscellaneous.byteArrayAsHexString(steamElement.m_fileDigest));
	m_eta.setText(prettyEta(steamElement.m_transferRate,
				steamElement.m_fileSize,
				steamElement.m_readOffset));
	m_fileIdentity.setText
	    ("File Identity: " +
	     Miscellaneous.byteArrayAsHexString(steamElement.m_fileIdentity));
	m_fileName.setText("File: " + steamElement.m_displayFileName);
	m_fileSize.setText
	    ("Size: " +
	     formatSize(steamElement.m_fileSize) +
	     " (" +
	     niceBytes(steamElement.m_fileSize) +
	     ")");

	if(steamElement.m_keyStream == null ||
	   steamElement.m_keyStream.length !=
	   Cryptography.CIPHER_HASH_KEYS_LENGTH)
	    m_keyStreamDigest.setText("Key Stream SHA-256: N/A");
	else
	    m_keyStreamDigest.setText
		("Key Stream SHA-256: " +
		 Miscellaneous.
		 byteArrayAsHexString(Cryptography.
				      sha256(steamElement.m_keyStream)));

	m_progress.setMax((int) steamElement.m_fileSize);
	m_progress.setProgress((int) steamElement.m_readOffset);

	switch((int) steamElement.m_readInterval)
	{
	case 4:
	    m_readInterval.setProgress(0);
	    break;
	case 10:
	    m_readInterval.setProgress(1);
	    break;
	case 20:
	    m_readInterval.setProgress(2);
	    break;
	case 50:
	    m_readInterval.setProgress(3);
	    break;
	case 100:
	    m_readInterval.setProgress(4);
	    break;
	default:
	    break;
	}

	m_separator.setVisibility
	    (count - 1 == position ? View.GONE : View.VISIBLE);
	m_status.setText("Status: " + steamElement.m_status);

	if(steamElement.m_direction == SteamElement.DOWNLOAD)
	    m_transferRate.setText
		("Receive Rate: " + steamElement.m_transferRate);
	else
	    m_transferRate.setText
		("Transfer Rate: " + steamElement.m_transferRate);

	m_view.setId(m_oid);
    }
}
