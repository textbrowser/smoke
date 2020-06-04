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

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.support.v4.widget.NestedScrollView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FireChannel extends View
{
    private static class Participant
    {
	public String m_id = Miscellaneous.byteArrayAsHexString
	    (Cryptography.randomBytes(128));
	public String m_name = "unknown";
	public long m_timestamp = -1L;
    }

    private final static Comparator<Participant>
	s_participantComparator = new Comparator<Participant> ()
	{
	    @Override
	    public int compare(Participant p1, Participant p2)
	    {
		return p1.m_name.compareTo(p2.m_name);
	    }
	};

    private Context m_context = null;
    private LayoutInflater m_inflater = null;
    private ScheduledExecutorService m_connectionStatusScheduler = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private String m_id = "";
    private String m_name = "";
    private View m_view = null;
    private ViewGroup m_parent = null;
    private final Hashtable<String, Participant> m_participants =
	new Hashtable<> ();
    private final SimpleDateFormat m_simpleDateFormat =
	new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
    private final static long STATUS_INTERVAL = 30000L;

    private void createSchedulers()
    {
	if(m_statusScheduler == null)
	{
	    m_statusScheduler = Executors.newSingleThreadScheduledExecutor();
	    m_statusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			if(Thread.currentThread().isInterrupted())
			    return;

			if(Kernel.getInstance().isConnected())
			    Kernel.getInstance().enqueueFireStatus
				(m_id, m_name);

			((Activity) m_context).runOnUiThread(new Runnable()
			{
			    @Override
			    public void run()
			    {
				if(m_view == null)
				    return;

				TableLayout tableLayout = (TableLayout)
				    m_view.findViewById(R.id.participants);

				for(int i = tableLayout.getChildCount() - 1;
				    i >= 0; i--)
				{
				    TableRow row = (TableRow) tableLayout.
					getChildAt(i);

				    if(row == null)
					continue;

				    TextView textView1 = (TextView) row.
					getChildAt(0);

				    if(textView1 == null)
				    {
					tableLayout.removeView(row);
					continue;
				    }

				    if(textView1.getId() == -1)
					continue;

				    Participant participant = null;

				    try
				    {
					participant = m_participants.
					    get(textView1.
						getTag(R.id.participants).
						toString());
				    }
				    catch(Exception exception)
				    {
					participant = null;
				    }

				    long current = System.currentTimeMillis();

				    if(participant == null ||
				       Math.abs(current -
						participant.m_timestamp) >=
				       2L * STATUS_INTERVAL)
				    {
					try
					{
					    m_participants.remove
						(textView1.
						 getTag(R.id.participants).
						 toString());
					}
					catch(Exception exception)
					{
					}

					tableLayout.removeView(row);

					StringBuilder stringBuilder =
					    new StringBuilder();

					stringBuilder.append
					    (textView1.getText().toString());
					stringBuilder.append(" has left ");
					stringBuilder.append(m_name);
					stringBuilder.append(".\n\n");

					Spannable spannable =
					    new SpannableStringBuilder
					    (stringBuilder);

					spannable.setSpan
					    (new StyleSpan(android.graphics.
							   Typeface.ITALIC),
					     0,
					     spannable.length(),
					     Spannable.
					     SPAN_EXCLUSIVE_EXCLUSIVE);

					TextView textView2 = (TextView) m_view.
					    findViewById(R.id.chat_messages);

					textView2.append("[");
					textView2.append
					    (m_simpleDateFormat.
					     format(new Date()));
					textView2.append("] ");
					textView2.append(spannable);
					scrollMessagesView();
				    }
				}
			    }
			});
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500L, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void populateParticipants()
    {
	if(m_view == null)
	    return;

	final TableLayout tableLayout = (TableLayout)
	    m_view.findViewById(R.id.participants);

	if(m_participants.isEmpty())
	{
	    tableLayout.removeAllViews();
	    return;
	}
	else
	    tableLayout.removeAllViews();

	ArrayList<Participant> arrayList = new ArrayList<>
	    (m_participants.values());
	int i = 0;

	Collections.sort(arrayList, s_participantComparator);

	for(Participant participant : arrayList)
	{
	    if(participant == null)
		continue;

	    TextView textView = new TextView(m_context);

	    textView.setTag(R.id.participants, participant.m_id);
	    textView.setText(participant.m_name.trim());

	    TableRow row = new TableRow(m_context);

	    row.addView(textView);
	    tableLayout.addView(row, i);
	    i += 1;

	    if(m_id.equals(participant.m_id))
	    {
		textView.setBackgroundColor(Color.rgb(255, 183, 77));
		textView.setId(-1);
	    }
	    else
		textView.setId(0);
	}

	arrayList.clear();
    }

    private void prepareListeners()
    {
	if(m_view == null)
	    return;

	Button button1 = null;

	button1 = (Button) m_view.findViewById(R.id.clear_chat_messages);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		TextView textView1 = (TextView) m_view.findViewById
		    (R.id.chat_messages);

		textView1.setText("");
	    }
	});

	button1 = (Button) m_view.findViewById(R.id.close);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		Kernel.getInstance().extinguishFire(m_name);

		if(m_connectionStatusScheduler != null)
	        {
		    try
		    {
			m_connectionStatusScheduler.shutdown();
		    }
		    catch(Exception exception)
		    {
		    }

		    try
		    {
			if(!m_connectionStatusScheduler.
			   awaitTermination(60L, TimeUnit.SECONDS))
			    m_connectionStatusScheduler.shutdownNow();
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_connectionStatusScheduler = null;
		    }
		}

		if(m_statusScheduler != null)
	        {
		    try
		    {
			m_statusScheduler.shutdown();
		    }
		    catch(Exception exception)
		    {
		    }

		    try
		    {
			if(!m_statusScheduler.
			   awaitTermination(60L, TimeUnit.SECONDS))
			    m_statusScheduler.shutdownNow();
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_statusScheduler = null;
		    }
		}

		ViewGroup parent = (ViewGroup) m_view.getParent();

		parent.removeView(m_view);
		State.getInstance().removeFireChannel(m_name);
	    }
	});

	button1 = (Button) m_view.findViewById(R.id.send_chat_message);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		TextView textView1 = (TextView) m_view.findViewById
		    (R.id.chat_message);

		if(textView1.getText().toString().trim().isEmpty())
		    return;

		String str = textView1.getText().toString().trim();
		StringBuilder stringBuilder = new StringBuilder();
		TextView textView2 = (TextView) m_view.findViewById
		    (R.id.chat_messages);

		textView2.append("[");
		textView2.append(m_simpleDateFormat.format(new Date()));
		textView2.append("] ");

		{
		    Spannable spannable = new SpannableStringBuilder("me");

		    spannable.setSpan
			(new StyleSpan(android.graphics.Typeface.BOLD),
			 0,
			 spannable.length(),
			 Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		    textView2.append(spannable);
		}

		stringBuilder.append(": ");
		stringBuilder.append(str);
		stringBuilder.append("\n\n");
		textView2.append(stringBuilder);
		textView1.setText("");
		Kernel.getInstance().enqueueFireMessage(str, m_id, m_name);
		scrollMessagesView();

		final TextView textView3 = (TextView) m_view.findViewById
		    (R.id.chat_message);

		textView3.post(new Runnable()
		{
		    @Override
		    public void run()
		    {
			textView3.requestFocus();
		    }
		});
	    }
	});
    }

    private void scrollMessagesView()
    {
	if(m_view == null)
	    return;

	final NestedScrollView nestedScrollView = (NestedScrollView)
	    m_view.findViewById(R.id.chat_scrollview);

	nestedScrollView.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		nestedScrollView.fullScroll(NestedScrollView.FOCUS_DOWN);
	    }
	});

	final TextView textView1 = (TextView) m_view.findViewById
	    (R.id.chat_message);

	textView1.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		textView1.requestFocus();
	    }
	});
    }

    public FireChannel(String id,
		       String name,
		       Context context,
		       ViewGroup parent)
    {
	super(context);
	m_context = context;
	m_id = id;
	m_inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);
	m_name = name;
	m_parent = parent;
	createSchedulers();
    }

    public String name()
    {
	return m_name;
    }

    public View view()
    {
	if(m_view == null)
	{
	    m_view = m_inflater.inflate(R.layout.fire_channel, m_parent, false);
	    m_connectionStatusScheduler = Executors.
		newSingleThreadScheduledExecutor();
	    m_connectionStatusScheduler.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			if(Thread.currentThread().isInterrupted())
			    return;

			final boolean state = Kernel.getInstance().
			    isConnected();

			((Activity) m_context).runOnUiThread(new Runnable()
			{
			    @Override
			    public void run()
			    {
				Button button1 = (Button) m_view.findViewById
				    (R.id.send_chat_message);

				button1.setEnabled(state);

				if(state)
				    button1.setBackgroundResource
					(R.drawable.send);
				else
				    button1.setBackgroundResource
					(R.drawable.send_disabled);
			    }
			});
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 0L, Chat.CONNECTION_STATUS_INTERVAL, TimeUnit.MILLISECONDS);

	    prepareListeners();

	    Participant participant = new Participant();

	    participant.m_id = m_id;
	    participant.m_name = Database.getInstance().readSetting
		(Cryptography.getInstance(), "fire_user_name").trim();

	    if(participant.m_name.isEmpty())
		participant.m_name = "unknown";

	    m_participants.put(m_id, participant);
	    populateParticipants();

	    TextView textView1 = (TextView) m_view.findViewById(R.id.fire_name);

	    textView1.setText(m_name);

	    if(!Kernel.getInstance().igniteFire(m_name))
	    {
		Button button1 = (Button) m_view.findViewById
		    (R.id.clear_chat_messages);

		button1.setEnabled(false);
		button1 = (Button) m_view.findViewById(R.id.send_chat_message);
		button1.setEnabled(false);
		textView1 = (TextView) m_view.findViewById(R.id.chat_messages);

		StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append("[");
		stringBuilder.append(m_simpleDateFormat.format(new Date()));
		stringBuilder.append("] ");
		stringBuilder.append("The Fire channel ");
		stringBuilder.append(m_name);
		stringBuilder.append(" cannot be registered with the Kernel." +
				     " Please close this channel.");
		stringBuilder.append("\n\n");

		Spannable spannable = new SpannableStringBuilder
		    (stringBuilder.toString());

		spannable.setSpan
		    (new ForegroundColorSpan(Color.rgb(213, 0, 0)),
		     0, stringBuilder.length(),
		     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		textView1.append(spannable);
	    }

	    textView1 = (TextView) m_view.findViewById(R.id.chat_message);
	    textView1.requestFocus();
	}

	return m_view;
    }

    public void append(String id, String message, String name)
    {
	if(id == null ||
	   id.trim().isEmpty() ||
	   m_view == null ||
	   message == null ||
	   message.trim().isEmpty() ||
	   name == null ||
	   name.trim().isEmpty())
	    return;

	status(id, name);

	StringBuilder stringBuilder = new StringBuilder();
	TextView textView = (TextView) m_view.findViewById(R.id.chat_messages);

	textView.append("[");
	textView.append(m_simpleDateFormat.format(new Date()));
	textView.append("] ");

	Spannable spannable = new SpannableStringBuilder(name.trim());

	spannable.setSpan
	    (new StyleSpan(android.graphics.Typeface.BOLD),
	     0,
	     spannable.length(),
	     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	textView.append(spannable);
	stringBuilder.append(": ");
	stringBuilder.append(message);
	stringBuilder.append("\n\n");
	textView.append(stringBuilder);
	scrollMessagesView();
    }

    public void setUserName(String name)
    {
	status(m_id, name);
    }

    public void status(String id, String name)
    {
	if(id == null ||
	   id.trim().isEmpty() ||
	   name == null ||
	   name.trim().isEmpty())
	    return;

	if(m_participants.containsKey(id))
	{
	    Participant participant = m_participants.get(id);

	    if(participant != null)
	    {
		if(!name.trim().equals(participant.m_name))
		{
		    StringBuilder stringBuilder = new StringBuilder();
		    TextView textView = (TextView) m_view.findViewById
			(R.id.chat_messages);

		    stringBuilder.append(participant.m_name);
		    stringBuilder.append(" is now known as ");
		    stringBuilder.append(name.trim());
		    stringBuilder.append(".\n\n");
		    textView.append("[");
		    textView.append(m_simpleDateFormat.format(new Date()));
		    textView.append("] ");

		    Spannable spannable = new SpannableStringBuilder
			(stringBuilder);

		    spannable.setSpan
			(new StyleSpan(android.graphics.Typeface.ITALIC),
			 0,
			 spannable.length(),
			 Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		    textView.append(spannable);
		    scrollMessagesView();
		    participant.m_name = name.trim();
		    participant.m_timestamp = System.currentTimeMillis();
		    m_participants.put(id, participant);
		    populateParticipants();
		}
		else
		{
		    participant.m_name = name.trim();
		    participant.m_timestamp = System.currentTimeMillis();
		    m_participants.put(id, participant);
		}

		return;
	    }
	    else
		m_participants.remove(id);
	}

	StringBuilder stringBuilder = new StringBuilder();

	stringBuilder.append(name.trim());
	stringBuilder.append(" has joined ");
	stringBuilder.append(m_name);
	stringBuilder.append(".\n\n");

	Spannable spannable = new SpannableStringBuilder(stringBuilder);

	spannable.setSpan
	    (new StyleSpan(android.graphics.Typeface.ITALIC),
	     0,
	     spannable.length(),
	     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

	TextView textView = (TextView) m_view.findViewById(R.id.chat_messages);

	textView.append("[");
	textView.append(m_simpleDateFormat.format(new Date()));
	textView.append("] ");
	textView.append(spannable);
	scrollMessagesView();

	Participant participant = new Participant();

	participant.m_id = id;
	participant.m_name = name.trim();
	participant.m_timestamp = System.currentTimeMillis();
	m_participants.put(id, participant);
	populateParticipants();
    }
}
