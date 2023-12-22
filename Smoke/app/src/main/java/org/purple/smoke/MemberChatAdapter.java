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
    private Database m_database = null;
    private MemberChat m_memberChat = null;
    private String m_sipHashId = "";
    private boolean m_contextMenuShown = false;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();

    public class ViewHolder extends RecyclerView.ViewHolder
	implements OnCreateContextMenuListener
    {
	ChatBubble m_chatBubble = null;
	String m_name = "";
	boolean m_canResend = false;
	boolean m_hasAttachment = false;
	int m_position = -1;

        public ViewHolder(ChatBubble chatBubble, String sipHashId)
	{
	    super(chatBubble.view());
	    chatBubble.view().setOnCreateContextMenuListener(this);
	    m_chatBubble = chatBubble;
	    m_name = m_database.nameFromSipHashId(s_cryptography, sipHashId);
        }

	public void onCreateContextMenu(ContextMenu menu,
					View view,
					ContextMenuInfo menuInfo)
	{
	    if(menu == null || view == null)
		return;

	    m_contextMenuShown = true;
	    m_memberChat.prepareContextMenuPosition(view);

	    /*
	    ** Please update the first parameter if the context menu
	    ** in MemberChat is modified!
	    */

	    MenuItem menuItem = null;

	    menu.add(MemberChat.ContextMenuEnumerator.COPY_TEXT,
		     m_position,
		     0,
		     "Copy Text");
	    menu.add(MemberChat.ContextMenuEnumerator.DELETE_ALL_MESSAGES,
		     -1,
		     1,
		     "Delete All Messages");
	    menu.add(MemberChat.ContextMenuEnumerator.DELETE_MESSAGE,
		     view.getId(),
		     2,
		     "Delete Message").setEnabled(view.getId() != -1);
	    menu.add(MemberChat.ContextMenuEnumerator.DELETE_SELECTED_MESSAGES,
		     -1,
		     3,
		     "Delete Selected Message(s)").setEnabled
		(m_memberChat.selectedMessagesCount() > 0);
	    menu.add(MemberChat.ContextMenuEnumerator.RESEND_MESSAGE,
		     m_position,
		     4,
		     "Resend Message").setEnabled(m_canResend);
	    menu.add
		(MemberChat.ContextMenuEnumerator.SAVE_ATTACHMENT,
		 m_position,
		 5,
		 "Save Attachment").setEnabled(m_hasAttachment);
	    menuItem = menu.add
		(MemberChat.ContextMenuEnumerator.SELECTION_STATE,
		 m_position,
		 6,
		 "Selection State").setCheckable(true);
	    menuItem.setChecked(m_memberChat.messageSelectionState());
	    menu.add
		(MemberChat.ContextMenuEnumerator.VIEW_DETAILS,
		 view.getId(),
		 7,
		 "View Details");
	}

	public void setData(MemberChatElement memberChatElement, int position)
	{
	    if(m_chatBubble == null)
		return;
	    else if(memberChatElement == null)
	    {
		m_canResend = false;
		m_chatBubble.setError(true);
		m_chatBubble.setMessageSelectionStateEnabled(false);
		m_chatBubble.setName(ChatBubble.Locations.LEFT, "?");
		m_chatBubble.setText
		    (ChatBubble.Locations.LEFT,
		     "Smoke malfunction! The database entry at " +
		     position +
		     " is zero!\n");
		m_hasAttachment = false;
		m_position = position;
		return;
	    }

	    StringBuilder stringBuilder = new StringBuilder();
	    boolean local = memberChatElement.m_fromSmokeStack.
		equals("local") ||
		memberChatElement.m_fromSmokeStack.equals("local-protocol");

	    stringBuilder.append(memberChatElement.m_message.trim());

	    if(!memberChatElement.m_message.trim().isEmpty())
		stringBuilder.append("\n");

	    m_canResend = memberChatElement.m_fromSmokeStack.equals("local");
	    m_chatBubble.setDate(memberChatElement.m_timestamp);
	    m_chatBubble.setError(false);
	    m_chatBubble.setFromeSmokeStack
		(memberChatElement.m_fromSmokeStack.equals("true"));
	    m_chatBubble.setImageAttachment(memberChatElement.m_attachment);
	    m_chatBubble.setLocal(local);
	    m_chatBubble.setMessageSelected
		(m_memberChat.isMessageSelected(memberChatElement.m_oid));
	    m_chatBubble.setMessageSelectionStateEnabled
		(m_memberChat.messageSelectionState());
	    m_chatBubble.setOid(memberChatElement.m_oid);

	    if(!local)
	    {
		m_chatBubble.setName(ChatBubble.Locations.LEFT, m_name);
		m_chatBubble.setRead(ChatBubble.Locations.LEFT, false);
		m_chatBubble.setSent
		    (ChatBubble.Locations.LEFT,
		     memberChatElement.m_messageSent);
		m_chatBubble.setText
		    (ChatBubble.Locations.LEFT, stringBuilder.toString());
	    }
	    else
	    {
		m_chatBubble.setName(ChatBubble.Locations.RIGHT, "M");
		m_chatBubble.setRead
		    (ChatBubble.Locations.RIGHT,
		     memberChatElement.m_messageRead);
		m_chatBubble.setSent
		    (ChatBubble.Locations.RIGHT,
		     memberChatElement.m_messageSent);
		m_chatBubble.setText
		    (ChatBubble.Locations.RIGHT, stringBuilder.toString());
	    }

	    m_hasAttachment = memberChatElement.m_attachment != null &&
		memberChatElement.m_attachment.length > 0;
	    m_position = position;
	}
    }

    public MemberChatAdapter(MemberChat memberChat, String sipHashId)
    {
	m_database = Database.getInstance(memberChat.getApplicationContext());
	m_memberChat = memberChat;
	m_sipHashId = sipHashId;
    }

    @Override
    public MemberChatAdapter.ViewHolder onCreateViewHolder
	(ViewGroup parent, int viewType)
    {
	return new ViewHolder
	    (new ChatBubble(parent.getContext(), m_memberChat, parent),
	     m_sipHashId);
    }

    public boolean contextMenuShown()
    {
	return m_contextMenuShown;
    }

    @Override
    public int getItemCount()
    {
	return (int) m_database.countOfMessages(s_cryptography, m_sipHashId);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position)
    {
	if(viewHolder == null)
	    return;

	MemberChatElement memberChatElement = m_database.readMemberChat
	    (s_cryptography, m_sipHashId, position);

	viewHolder.setData(memberChatElement, position);
    }

    public void setContextMenuClosed()
    {
	m_contextMenuShown = false;
    }
}
