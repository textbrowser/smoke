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
import java.lang.Math;
import java.util.ArrayList;

public class NeighborsAdapter extends BaseAdapter
{
    private ArrayList<NeighborElement> m_arrayList = null;
    private Context m_context = null;
    public static final int NUMBER_OF_COLUMNS = 12;

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

	if(position >= 0 && position < getCount())
	{
	    int index = (int) Math.floor(position / NUMBER_OF_COLUMNS);

	    switch(position)
	    {
	    case 0:
                return m_arrayList.get(index).m_statusControl;
            case 1:
                return m_arrayList.get(index).m_status;
            case 2:
                return m_arrayList.get(index).m_remoteIpAddress;
            case 3:
                return m_arrayList.get(index).m_remotePort;
            case 4:
                return m_arrayList.get(index).m_remoteScopeId;
            case 5:
                return m_arrayList.get(index).m_localIpAddress;
            case 6:
                return m_arrayList.get(index).m_localPort;
            case 7:
                return m_arrayList.get(index).m_ipVersion;
            case 8:
                return m_arrayList.get(index).m_transport;
            case 9:
                return m_arrayList.get(index).m_sessionCipher;
            case 10:
                return m_arrayList.get(index).m_uptime;
            case 11:
                return m_arrayList.get(index).m_oid + "";
	    }
	}

	return "";
    }

    @Override
    public int getCount()
    {
	if(m_arrayList == null)
	    return 0;

	return NUMBER_OF_COLUMNS * m_arrayList.size();
    }

    @Override
    public long getItemId(int position)
    {
	return position;
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
