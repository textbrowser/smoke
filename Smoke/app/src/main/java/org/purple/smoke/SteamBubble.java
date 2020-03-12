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
import android.widget.ProgressBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SeekBar;
import android.widget.TextView;

public class SteamBubble extends View
{
    private Button m_control = null;
    private Context m_context = null;
    private ProgressBar m_progress = null;
    private SeekBar m_rate = null;
    private Steam m_steam = null;
    private String m_controlString = "";
    private TextView m_destination = null;
    private TextView m_digest = null;
    private TextView m_fileName = null;
    private TextView m_fileSize = null;
    private TextView m_rateLabel = null;
    private TextView m_sent = null;
    private TextView m_status = null;
    private TextView m_transferRate = null;
    private View m_view = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_databaseHelper = Database.getInstance();
    private final static String READ_INTERVALS[] = {"50 reads / s",
						    "20 reads / s",
						    "10 reads / s",
						    "4 reads / s"};
    private int m_oid = -1;

    private String formatSize(long size)
    {
	return Miscellaneous.formattedDigitalInformation(String.valueOf(size));
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
		    s_databaseHelper.writeSteamStatus
			(s_cryptography, "paused", "", m_oid);
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
			(s_cryptography, "rewind", "", m_oid, 0);
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_status");
		    break;
		}
	    }
        });
	m_destination = (TextView) m_view.findViewById(R.id.destination);
	m_digest = (TextView) m_view.findViewById(R.id.digest);
	m_fileName = (TextView) m_view.findViewById(R.id.filename);
	m_fileSize = (TextView) m_view.findViewById(R.id.file_size);
	m_progress = (ProgressBar) m_view.findViewById(R.id.progress_bar);
	m_rate = (SeekBar) m_view.findViewById(R.id.rate);
	m_rate.setOnSeekBarChangeListener(new OnSeekBarChangeListener()
	{
	    @Override
	    public void onProgressChanged(SeekBar seekBar,
					  int progress,
					  boolean fromUser)
	    {
		switch(progress)
		{
		case 0:
		    m_rateLabel.setText("4 reads / s");
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_rate_change", m_oid, 4);
		    break;
		case 1:
		    m_rateLabel.setText("10 reads / s");
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_rate_change", m_oid, 10);
		    break;
		case 2:
		    m_rateLabel.setText("20 reads / s");
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_rate_change", m_oid, 20);
		    break;
		case 3:
		    m_rateLabel.setText("50 reads / s");
		    Miscellaneous.sendBroadcast
			("org.purple.smoke.steam_rate_change", m_oid, 50);
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
	m_rateLabel = (TextView) m_view.findViewById(R.id.rate_label);
	m_rateLabel.setText("4 reads / s");
	m_sent = (TextView) m_view.findViewById(R.id.sent);
	m_status = (TextView) m_view.findViewById(R.id.status);
	m_transferRate = (TextView) m_view.findViewById(R.id.transfer_rate);
	m_view.setId(-1);
    }

    public View view()
    {
	return m_view;
    }

    public void setData(SteamElement steamElement)
    {
	if(steamElement == null)
	    return;

	switch(steamElement.m_status)
	{
	case "completed":
	    m_control.setText("Rewind");
	    m_controlString = "rewind";
	    m_progress.setVisibility(View.GONE);
	    break;
	case "paused":
	    m_control.setText("Resume");
	    m_controlString = "resume";
	    m_progress.setVisibility(View.GONE);
	    break;
	case "transferring":
	    m_control.setText("Pause");
	    m_controlString = "pause";
	    m_progress.setVisibility(View.VISIBLE);
	    break;
	default:
	    break;
	}

	m_destination.setText("Destination: " + steamElement.m_destination);
	m_digest.setText
	    ("SHA-256: " +
	     Miscellaneous.byteArrayAsHexString(steamElement.m_fileDigest));
	m_fileName.setText("File: " + steamElement.m_fileName);
	m_fileSize.setText
	    ("Size: " + formatSize(steamElement.m_fileSize));
	m_oid = steamElement.m_oid;
	m_progress.setMax((int) steamElement.m_fileSize);
	m_progress.setProgress((int) steamElement.m_readOffset);
	m_sent.setText("Sent: " + formatSize(steamElement.m_readOffset));
	m_status.setText("Status: " + steamElement.m_status);
	m_transferRate.setText
	    ("Transfer Rate: " + steamElement.m_transferRate);
	m_view.setId(m_oid);
    }
}
