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
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FireChannel extends View
{
    private Context m_context = null;
    private LayoutInflater m_inflater = null;
    private String m_id;
    private String m_name = "";
    private View m_view = null;
    private int m_oid = -1;
    private final SimpleDateFormat m_simpleDateFormat = new
	SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());

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
	    }
	});
    }

    private void scrollMessagesView()
    {
	if(m_view == null)
	    return;

	final ScrollView scrollView = (ScrollView)
	    m_view.findViewById(R.id.chat_scrollview);

	scrollView.post(new Runnable()
	{
	    @Override
	    public void run()
	    {
		scrollView.fullScroll(ScrollView.FOCUS_DOWN);
	    }
	});
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
	super.onDraw(canvas);
    }

    public FireChannel(String name, int oid, Context context)
    {
	super(context);
	m_context = context;
	m_inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);
	m_name = name;
	m_oid = oid;
    }

    public FireChannel(Context context, AttributeSet attrs)
    {
	super(context, attrs);
	m_context = context;
	m_inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);
    }

    public FireChannel(Context context, AttributeSet attrs, int defStyle)
    {
	super(context, attrs, defStyle);
	m_context = context;
	m_inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);
    }

    public String name()
    {
	return m_name;
    }

    public View view()
    {
	if(m_view == null)
	{
	    m_id = Miscellaneous.byteArrayAsHexString
		(Cryptography.randomBytes(128));
	    m_view = m_inflater.inflate(R.layout.fire_channel, null);
	    prepareListeners();

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
		stringBuilder.append(" cannot be registered with the Kernel.");
		stringBuilder.append("\n\n");

		Spannable spannable = new SpannableStringBuilder
		    (stringBuilder.toString());

		spannable.setSpan
		    (new ForegroundColorSpan(Color.rgb(255, 68, 68)),
		     0, stringBuilder.length(),
		     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		textView1.append(spannable);
	    }

	    textView1 = (TextView) m_view.findViewById(R.id.chat_message);
	    textView1.requestFocus();
	}

	return m_view;
    }
}
