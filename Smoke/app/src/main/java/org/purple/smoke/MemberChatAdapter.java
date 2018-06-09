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

import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import java.util.ArrayList;

public class MemberChatAdapter extends RecyclerView.Adapter
				       <MemberChatAdapter.ViewHolder>
{
    ArrayList<MemberChatElement> m_arrayList = null;
    String m_sipHashId = "";

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
	ChatBubble m_chatBubble = null;
	String m_sipHashId = "";

        public ViewHolder(ChatBubble chatBubble, String sipHashId)
	{
	    super(chatBubble.view());
	    m_chatBubble = chatBubble;
	    m_sipHashId = sipHashId;
        }

	public void setData(MemberChatElement memberChatElement)
	{
	    if(m_chatBubble == null || memberChatElement == null)
		return;

	    StringBuilder stringBuilder = new StringBuilder();
	    boolean local = false;

	    if(memberChatElement.m_fromSmokeStack.equals("local"))
		local = true;

	    stringBuilder.append(memberChatElement.m_message);
	    stringBuilder.append("\n");
	    m_chatBubble.setDate(memberChatElement.m_timestamp);
	    m_chatBubble.setId(memberChatElement.m_oid);
	    m_chatBubble.setTag(m_sipHashId);

	    if(!local)
		m_chatBubble.setText
		    (stringBuilder.toString(), ChatBubble.LEFT);
	    else
		m_chatBubble.setText
		    (stringBuilder.toString(), ChatBubble.RIGHT);
	}
    }

    public MemberChatAdapter(ArrayList<MemberChatElement> arrayList,
			     String sipHashId)
    {
	m_arrayList = arrayList;
	m_sipHashId = sipHashId;
    }

    @Override
    public MemberChatAdapter.ViewHolder onCreateViewHolder
	(ViewGroup parent, int viewType)
    {
	return new ViewHolder
	    (new ChatBubble(parent.getContext(), parent), m_sipHashId);
    }

    @Override
    public int getItemCount()
    {
	if(m_arrayList == null)
	    return 0;
	else
	    return m_arrayList.size();
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
	if(holder == null || m_arrayList == null || m_arrayList.isEmpty())
	    return;

	holder.setData(m_arrayList.get(position));
    }
}
