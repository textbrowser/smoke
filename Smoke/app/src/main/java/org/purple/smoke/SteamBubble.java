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
import android.widget.TextView;
import java.text.DecimalFormat;

public class SteamBubble extends View
{
    private Button m_control = null;
    private Context m_context = null;
    private Steam m_steam = null;
    private TextView m_destination = null;
    private TextView m_digest = null;
    private TextView m_fileName = null;
    private TextView m_fileSize = null;
    private TextView m_sent = null;
    private TextView m_status = null;
    private View m_view = null;
    private int m_oid = -1;

    private String formatSize(long size)
    {
	DecimalFormat decimalFormat = new DecimalFormat("#.00");
	StringBuilder stringBuilder = new StringBuilder();

	if(size >= 1073741824)
	    stringBuilder.append
		(decimalFormat.format(size / 1073741824.0)).append(" GiB");
	else if(size >= 1048576)
	    stringBuilder.append
		(decimalFormat.format(size / 1048576.0)).append(" MiB");
	else if(size >= 1024)
	    stringBuilder.append
		(decimalFormat.format(size / 1024.0)).append(" KiB");
	else
	    stringBuilder.append(size).append(" B");

	return stringBuilder.toString();
    }

    public SteamBubble(Context context,
		       Steam steam,
		       ViewGroup viewGroup)
    {
	super(context);
	m_context = context;
	m_steam = steam;

	LayoutInflater inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);

	m_view = inflater.inflate(R.layout.steam_bubble, viewGroup, false);
	m_control = (Button) m_view.findViewById(R.id.control);
	m_destination = (TextView) m_view.findViewById(R.id.destination);
	m_digest = (TextView) m_view.findViewById(R.id.digest);
	m_fileName = (TextView) m_view.findViewById(R.id.filename);
	m_fileSize = (TextView) m_view.findViewById(R.id.file_size);
	m_sent = (TextView) m_view.findViewById(R.id.sent);
	m_status = (TextView) m_view.findViewById(R.id.status);
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

	m_destination.setText("Destination: " + steamElement.m_destination);
	m_digest.setText
	    ("SHA-256: " +
	     Miscellaneous.byteArrayAsHexString(steamElement.m_fileDigest));
	m_fileName.setText("File: " + steamElement.m_fileName);
	m_fileSize.setText
	    ("Total Size: " + formatSize(steamElement.m_fileSize));
	m_oid = steamElement.m_oid;

	switch(steamElement.m_status)
	{
	case "completed":
	    m_control.setText("Rewind");
	    break;
	case "paused":
	    m_control.setText("Resume");
	    break;
	case "transferring":
	    m_control.setText("Pause");
	    break;
	default:
	    break;
	}

	m_sent.setText("Total Sent: " + formatSize(steamElement.m_readOffset));
	m_status.setText("Status: " + steamElement.m_status);
	m_view.setId(m_oid);
    }
}
