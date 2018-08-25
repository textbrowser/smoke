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
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;

public class MemberChatAdapter extends RecyclerView.Adapter
				       <MemberChatAdapter.ViewHolder>
{
    private String m_sipHashId = "";
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_database = Database.getInstance();

    public static class ViewHolder extends RecyclerView.ViewHolder
	implements OnCreateContextMenuListener
    {
	ChatBubble m_chatBubble = null;
	String m_name = "";
	String m_sipHashId = "";
	int m_position = -1;

        public ViewHolder(ChatBubble chatBubble, String sipHashId)
	{
	    super(chatBubble.view());
	    chatBubble.view().setOnCreateContextMenuListener(this);
	    m_chatBubble = chatBubble;
	    m_name = s_database.nameFromSipHashId(s_cryptography, sipHashId);
	    m_sipHashId = sipHashId;
        }

	public void onCreateContextMenu(ContextMenu menu,
					View view,
					ContextMenuInfo menuInfo)
	{
	    /*
	    ** Please update the first parameter if the context menu
	    ** in MemberChat is modified!
	    */

	    MenuItem menuItem = null;

	    menu.add(10, -1, 1, "Delete All Messages");
	    menu.add(15, view.getId(), 2, "Delete Message");
	    menu.add(20, m_position, 0, "Copy Text");
	    menuItem = menu.add(25, m_position, 3, "Save Attachment");

	    MemberChatElement memberChatElement =
		s_database.readMemberChat(s_cryptography,
					  m_sipHashId,
					  m_position);

	    menuItem.setEnabled(memberChatElement != null &&
				memberChatElement.m_attachment != null &&
				memberChatElement.m_attachment.length > 0);
	}

	public void setData(MemberChatElement memberChatElement, int position)
	{
	    if(m_chatBubble == null || memberChatElement == null)
		return;

	    StringBuilder stringBuilder = new StringBuilder();
	    boolean local = false;

	    if(memberChatElement.m_fromSmokeStack.equals("local"))
		local = true;

	    stringBuilder.append(memberChatElement.m_message.trim());

	    if(!memberChatElement.m_message.trim().isEmpty())
		stringBuilder.append("\n");

	    m_chatBubble.setDate(memberChatElement.m_timestamp);
	    m_chatBubble.setFromeSmokeStack
		(memberChatElement.m_fromSmokeStack.equals("true"));
	    m_chatBubble.setImageAttachment(memberChatElement.m_attachment);
	    m_chatBubble.setOid(memberChatElement.m_oid);

	    if(!local)
	    {
		m_chatBubble.setName(ChatBubble.Locations.LEFT, m_name);
		m_chatBubble.setText
		    (ChatBubble.Locations.LEFT, stringBuilder.toString());
	    }
	    else
	    {
		m_chatBubble.setName(ChatBubble.Locations.RIGHT, "M");
		m_chatBubble.setRead
		    (ChatBubble.Locations.RIGHT,
		     memberChatElement.m_messageRead);
		m_chatBubble.setText
		    (ChatBubble.Locations.RIGHT, stringBuilder.toString());
	    }

	    m_position = position;
	}
    }

    public MemberChatAdapter(String sipHashId)
    {
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
	return (int) s_database.countOfMessages(s_cryptography, m_sipHashId);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position)
    {
	if(viewHolder == null)
	    return;

	MemberChatElement memberChatElement =
	    (s_database.readMemberChat(s_cryptography, m_sipHashId, position));

	if(memberChatElement == null)
	    return;

	viewHolder.setData(memberChatElement, position);
    }
}
