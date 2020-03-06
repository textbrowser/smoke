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

public class SteamAdapter extends RecyclerView.Adapter<SteamAdapter.ViewHolder>
{
    private Steam m_steam = null;
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static Database s_database = Database.getInstance();

    public class ViewHolder extends RecyclerView.ViewHolder
	implements OnCreateContextMenuListener
    {
	SteamBubble m_steamBubble = null;
	int m_position = -1;

        public ViewHolder(SteamBubble steamBubble)
	{
	    super(steamBubble.view());
	    steamBubble.view().setOnCreateContextMenuListener(this);
	    m_steamBubble = steamBubble;
        }

	public void onCreateContextMenu(ContextMenu menu,
					View view,
					ContextMenuInfo menuInfo)
	{
	    if(menu == null || view == null)
		return;

	    /*
	    ** Please update the first parameter if the context menu
	    ** in Steam is modified!
	    */

	    MenuItem menuItem = null;

	    menu.add(Steam.ContextMenuEnumerator.DELETE_ALL_STEAMS,
		     -1,
		     0,
		     "Delete All Steams");
	    menu.add(Steam.ContextMenuEnumerator.DELETE_STEAM,
		     view.getId(),
		     1,
		     "Delete Steam").setEnabled(view.getId() != -1);
	}

	public void setData(SteamElement steamElement, int position)
	{
	    if(m_steamBubble == null)
		return;
	    else if(steamElement == null)
	    {
		m_position = position;
		return;
	    }

	    m_position = position;
	    m_steamBubble.setData(steamElement);
	}
    }

    public SteamAdapter(Steam steam)
    {
	m_steam = steam;
    }

    @Override
    public SteamAdapter.ViewHolder onCreateViewHolder
	(ViewGroup parent, int viewType)
    {
	return new ViewHolder
	    (new SteamBubble(parent.getContext(), m_steam, parent));
    }

    @Override
    public int getItemCount()
    {
	return (int) s_database.countOfSteams();
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position)
    {
	if(viewHolder == null)
	    return;

	SteamElement steamElement = s_database.readSteam
	    (s_cryptography, position);

	viewHolder.setData(steamElement, position);
    }
}
