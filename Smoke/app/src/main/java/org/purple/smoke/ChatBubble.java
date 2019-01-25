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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatBubble extends View
{
    private Context m_context = null;
    private Date m_date = new Date(System.currentTimeMillis());
    private View m_view = null;
    private boolean m_error = false;
    private boolean m_fromSmokeStack = false;
    private boolean m_messageRead = false;
    private boolean m_messageSent = false;
    private final SimpleDateFormat m_simpleDateFormat = new
	SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.getDefault());
    public enum Locations {LEFT, RIGHT}

    public ChatBubble(Context context, ViewGroup viewGroup)
    {
	super(context);
	m_context = context;

	LayoutInflater inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);

	m_view = inflater.inflate(R.layout.chat_bubble, viewGroup, false);
	m_view.findViewById(R.id.image).setVisibility(View.GONE);
	m_view.findViewById(R.id.message_read).setVisibility(View.INVISIBLE);
	m_view.findViewById(R.id.message_sent).setVisibility(View.INVISIBLE);
	m_view.findViewById(R.id.name_left).setVisibility(View.GONE);
	m_view.findViewById(R.id.name_right).setVisibility(View.GONE);
	m_view.setId(-1);
    }

    public View view()
    {
	return m_view;
    }

    public void setDate(long timestamp)
    {
	m_date = new Date(timestamp);
    }

    public void setError(boolean state)
    {
	m_error = state;
    }

    public void setFromeSmokeStack(boolean state)
    {
	m_fromSmokeStack = state;
    }

    public void setImageAttachment(byte bytes[])
    {
	if(bytes == null || bytes.length == 0)
	{
	    m_view.findViewById(R.id.image).setVisibility(View.GONE);
	    return;
	}

	try
	{
	    BitmapFactory.Options options = new BitmapFactory.Options();

	    options.inSampleSize = 2;

	    Bitmap bitmap = BitmapFactory.decodeStream
		(new ByteArrayInputStream(bytes), null, options);
	    ImageView imageView = m_view.findViewById(R.id.image);

	    if(bitmap != null)
	    {
		RoundedBitmapDrawable roundedBitmapDrawable =
		    RoundedBitmapDrawableFactory.create(getResources(), bitmap);

		roundedBitmapDrawable.setCornerRadius(10.0f);
		roundedBitmapDrawable.setAntiAlias(true);
		imageView.setImageDrawable(roundedBitmapDrawable);
		imageView.setVisibility(View.VISIBLE);
	    }
	    else
		imageView.setVisibility(View.GONE);
	}
	catch(Exception exception)
	{
	    m_view.findViewById(R.id.image).setVisibility(View.GONE);
	}
    }

    public void setName(Locations location, String name)
    {
	m_view.findViewById(R.id.name_left).setVisibility(View.GONE);
	m_view.findViewById(R.id.name_right).setVisibility(View.GONE);

	if(name == null || name.trim().isEmpty())
	    return;

	if(location == Locations.LEFT)
	{
	    TextView textView = m_view.findViewById(R.id.name_left);

	    textView.setText(name.substring(0, 1).toUpperCase());
	    textView.setVisibility(View.VISIBLE);
	}
	else
	{
	    TextView textView = m_view.findViewById(R.id.name_right);

	    textView.setText(name.substring(0, 1).toUpperCase());
	    textView.setVisibility(View.VISIBLE);
	}
    }

    public void setOid(int oid)
    {
	m_view.setId(oid);
    }

    public void setRead(Locations location, boolean state)
    {
	if(m_error)
	{
	    m_messageRead = state;
	    m_view.findViewById(R.id.message_read).setVisibility
		(View.INVISIBLE);
	    return;
	}

	m_messageRead = state;

	if(location == Locations.LEFT)
	    m_view.findViewById(R.id.message_read).setVisibility
		(View.INVISIBLE);
	else
	{
	    m_view.findViewById(R.id.message_read).setVisibility
		(m_messageRead ? View.VISIBLE : View.INVISIBLE);

	    float density = m_context.getResources().getDisplayMetrics().
		density;

	    m_view.findViewById(R.id.text).setPaddingRelative
		((int) (10 * density),                         // Start
		 (int) (10 * density),                         // Top
		 (int) (10 * density),                         // End
		 (int) ((m_messageRead ? 20 : 10) * density)); // Bottom
	}
    }

    public void setSent(Locations location, boolean state)
    {
	if(m_error)
	{
	    m_messageSent = state;
	    m_view.findViewById(R.id.message_sent).setVisibility
		(View.INVISIBLE);
	    return;
	}

	m_messageSent = state;

	if(location == Locations.LEFT)
	    m_view.findViewById(R.id.message_sent).setVisibility
		(View.INVISIBLE);
	else
	{
	    m_view.findViewById(R.id.message_sent).setVisibility
		(m_messageSent ? View.VISIBLE : View.INVISIBLE);

	    float density = m_context.getResources().getDisplayMetrics().
		density;

	    m_view.findViewById(R.id.text).setPaddingRelative
		((int) (10 * density),                         // Start
		 (int) (10 * density),                         // Top
		 (int) (10 * density),                         // End
		 (int) ((m_messageSent ? 20 : 10) * density)); // Bottom
	}
    }

    public void setText(Locations location, String text)
    {
	LinearLayout linearLayout = m_view.findViewById(R.id.linear_layout);
	TextView textView = m_view.findViewById(R.id.text);

	textView.setText("");

	{
	    Spannable spannable = new SpannableStringBuilder
		(text == null ? "" : text);

	    if(spannable.length() > 0)
	    {
		if(location == Locations.LEFT)
		    spannable.setSpan
			(new ForegroundColorSpan(Color.rgb(255, 255, 255)),
			 0,
			 spannable.length(),
			 Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		else
		    spannable.setSpan
			(new ForegroundColorSpan(Color.rgb(117, 117, 117)),
			 0,
			 spannable.length(),
			 Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		textView.append(spannable);
	    }
	}

	{
	    Spannable spannable = new SpannableStringBuilder
		((m_fromSmokeStack ? "Ozone " : "") +
		 m_simpleDateFormat.format(m_date));

	    if(spannable.length() > 0)
	    {
		if(location == Locations.LEFT)
		    spannable.setSpan
			(new ForegroundColorSpan(Color.rgb(224, 224, 224)),
			 0,
			 spannable.length(),
			 Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		else
		    spannable.setSpan
			(new ForegroundColorSpan(Color.rgb(158, 158, 158)),
			 0,
			 spannable.length(),
			 Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		spannable.setSpan
		    (new RelativeSizeSpan(0.90f),
		     0,
		     spannable.length(),
		     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		textView.append(spannable);
	    }
	}

	float density = m_context.getResources().getDisplayMetrics().density;

	if(location == Locations.LEFT)
	{
	    if(m_error)
		linearLayout.setBackgroundResource(R.drawable.bubble_error);
	    else if(m_fromSmokeStack)
		linearLayout.setBackgroundResource
		    (R.drawable.bubble_ozone_text);
	    else
		linearLayout.setBackgroundResource
		    (R.drawable.bubble_left_text);

	    m_view.findViewById(R.id.text).setPaddingRelative
		((int) (10 * density),  // Start
		 (int) (10 * density),  // Top
		 (int) (10 * density),  // End
		 (int) (10 * density)); // Bottom
	}
	else
	{
	    if(m_error)
		linearLayout.setBackgroundResource(R.drawable.bubble_error);
	    else
		linearLayout.setBackgroundResource
		    (R.drawable.bubble_right_text);

	    m_view.findViewById(R.id.text).setPaddingRelative
		((int) (10 * density),                         // Start
		 (int) (10 * density),                         // Top
		 (int) (10 * density),                         // End
		 (int) ((m_messageRead ||
			 m_messageSent ? 20 : 10) * density)); // Bottom
	}
    }
}
