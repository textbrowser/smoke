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
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;

public class NeighborsAdapter extends BaseAdapter
{
    ArrayList<NeighborElement> m_arrayList = null;
    Context m_context = null;

    public NeighborsAdapter(ArrayList<NeighborElement> arrayList,
			    Context context)
    {
	m_arrayList = arrayList;
	m_context = context;
    }

    @Override
    public Object getItem(int position)
    {
	if(m_arrayList == null)
	    return "";

	if(position >= 0 && position < m_arrayList.size())
	{
	    if(position == 0)
		return m_arrayList.get(position).m_ipVersion;
	    else if(position == 1)
		return m_arrayList.get(position).m_localIpAddress;
	    else if(position == 2)
		return m_arrayList.get(position).m_localPort;
	    else if(position == 3)
		return m_arrayList.get(position).m_remoteCertificate;
	    else if(position == 4)
		return m_arrayList.get(position).m_remoteIpAddress;
	    else if(position == 5)
		return m_arrayList.get(position).m_remoteScopeId;
	    else if(position == 6)
		return m_arrayList.get(position).m_sessionCipher;
	    else if(position == 7)
		return m_arrayList.get(position).m_status;
	    else if(position == 8)
		return m_arrayList.get(position).m_statusControl;
	    else if(position == 9)
		return m_arrayList.get(position).m_transport;
	    else if(position == 10)
		return m_arrayList.get(position).m_uptime;
	}

	return "";
    }

    @Override
    public int getCount()
    {
	if(m_arrayList == null)
	    return 0;

	return m_arrayList.size();
    }

    @Override
    public long getItemId(int position)
    {
	return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
	TextView textView = null;

	if(convertView == null)
            textView = new TextView(m_context);
	else
            textView = (TextView) convertView;

	textView.setText(getItem(position).toString());
        return textView;
    }
}
