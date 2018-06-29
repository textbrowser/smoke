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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
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
    private LayoutInflater m_inflater = null;
    private View m_view = null;
    private boolean m_fromSmokeStack = false;
    private final SimpleDateFormat m_simpleDateFormat = new
	SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.getDefault());
    public static enum Locations {LEFT, RIGHT};

    @Override
    protected void onDraw(Canvas canvas)
    {
	super.onDraw(canvas);
    }

    public ChatBubble(Context context, ViewGroup viewGroup)
    {
	super(context);
	m_context = context;
	m_inflater = (LayoutInflater) m_context.getSystemService
	    (Context.LAYOUT_INFLATER_SERVICE);
	m_view = m_inflater.inflate(R.layout.chat_bubble, viewGroup, false);
    }

    public View view()
    {
	return m_view;
    }

    public void setDate(long timestamp)
    {
	m_date = new Date(timestamp);
    }

    public void setFromeSmokeStack(boolean state)
    {
	m_fromSmokeStack = state;
    }

    public void setImageAttachment(byte bytes[])
    {
	if(bytes == null || bytes.length <= 0)
	{
	    m_view.findViewById(R.id.image).setVisibility(View.GONE);
	    return;
	}

	try
	{
	    BitmapFactory.Options options = new BitmapFactory.Options();

	    options.inSampleSize = 4;

	    Bitmap bitmap = BitmapFactory.decodeStream
		(new ByteArrayInputStream(bytes), null, options);
	    ImageView imageView = (ImageView) m_view.findViewById(R.id.image);

	    if(bitmap != null)
	    {
		imageView.setImageBitmap(bitmap);
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

    public void setOid(int oid)
    {
	m_view.setId(oid);
    }

    public void setText(String text, Locations location)
    {
	ImageView imageView = m_view.findViewById(R.id.image);
	TextView textView = m_view.findViewById(R.id.text);

	textView.setText("");

	{
	    Spannable spannable = new SpannableStringBuilder(text);

	    spannable.setSpan
		(new ForegroundColorSpan(Color.rgb(255, 255, 255)),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView.append(spannable);
	}

	{
	    Spannable spannable = new SpannableStringBuilder
		((m_fromSmokeStack ? "Ozone " : "") +
		 m_simpleDateFormat.format(m_date));

	    spannable.setSpan
		(new ForegroundColorSpan(Color.rgb(220, 220, 220)),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    spannable.setSpan
		(new RelativeSizeSpan(0.90f),
		 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    textView.append(spannable);
	}

	DisplayMetrics displayMetrics = new DisplayMetrics();
	float density = m_context.getResources().getDisplayMetrics().density;

	((Activity) m_context).getWindowManager().getDefaultDisplay().
	    getMetrics(displayMetrics);

	if(location == Locations.LEFT)
	{
	    LinearLayout.LayoutParams layoutParams = new
		LinearLayout.LayoutParams
		(LinearLayout.LayoutParams.MATCH_PARENT,
		 LinearLayout.LayoutParams.WRAP_CONTENT);

	    layoutParams.setMargins
		(0,
		 0,
		 (int) (0.20 * displayMetrics.widthPixels),
		 (int) (5.0 * density));

	    if(m_fromSmokeStack)
		m_view.setBackgroundResource(R.drawable.bubble_ozone_text);
	    else
		m_view.setBackgroundResource(R.drawable.bubble_left_text);

	    m_view.setLayoutParams(layoutParams);
	}
	else
	{
	    LinearLayout.LayoutParams layoutParams = new
		LinearLayout.LayoutParams
		(LinearLayout.LayoutParams.MATCH_PARENT,
		 LinearLayout.LayoutParams.WRAP_CONTENT);

	    layoutParams.setMargins
		((int) (0.20 * displayMetrics.widthPixels),
		 0,
		 (int) (5.0 * density),
		 (int) (5.0 * density));
	    m_view.setBackgroundResource(R.drawable.bubble_right_text);
	    m_view.setLayoutParams(layoutParams);
	}
    }
}
