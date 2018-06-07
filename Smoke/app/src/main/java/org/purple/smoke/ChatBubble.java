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
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatBubble extends View
{
    private Context m_context = null;
    private Date m_date = new Date(System.currentTimeMillis());
    private LayoutInflater m_inflater = null;
    private View m_view = null;
    private final SimpleDateFormat m_simpleDateFormat = new
	SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault());

    @Override
    protected void onDraw(Canvas canvas)
    {
	super.onDraw(canvas);
    }

    public ChatBubble(Context context)
    {
	super(context);
	m_context = context;
	m_inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);
	view();
    }

    public View view()
    {
	if(m_view == null)
	    m_view = m_inflater.inflate(R.layout.chat_bubble, null);

	return m_view;
    }

    public void setDate(long timestamp)
    {
	m_date = new Date(timestamp);
    }

    public void setTextLeft(String text)
    {
	TextView textView = (TextView) m_view.findViewById(R.id.text_left);

	{
	    Spannable spannable = new SpannableStringBuilder(text);

	    spannable.setSpan
		(new ForegroundColorSpan(Color.parseColor("white")),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView.append(spannable);
	}

	{
	    Spannable spannable = new SpannableStringBuilder
		(m_simpleDateFormat.format(m_date));

	    spannable.setSpan
		(new ForegroundColorSpan(Color.rgb(220, 220, 220)),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    spannable.setSpan
		(new RelativeSizeSpan(0.75f),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView.append(spannable);
	}

	textView = (TextView) m_view.findViewById(R.id.text_right);
	textView.setVisibility(View.INVISIBLE);
    }

    public void setTextRight(String text)
    {
	TextView textView = (TextView) m_view.findViewById(R.id.text_left);

	textView.setVisibility(View.INVISIBLE);
	textView = (TextView) m_view.findViewById(R.id.text_right);

	{
	    Spannable spannable = new SpannableStringBuilder(text);

	    spannable.setSpan
		(new ForegroundColorSpan(Color.parseColor("white")),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView.append(spannable);
	}

	{
	    Spannable spannable = new SpannableStringBuilder
		(m_simpleDateFormat.format(m_date));

	    spannable.setSpan
		(new ForegroundColorSpan(Color.rgb(220, 220, 220)),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    spannable.setSpan
		(new RelativeSizeSpan(0.75f),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView.append(spannable);
	}
    }
}
