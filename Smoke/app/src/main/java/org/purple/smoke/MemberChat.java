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

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap.Config;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.util.Base64;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemberChat extends AppCompatActivity
{
    private class MemberChatBroadcastReceiver extends BroadcastReceiver
    {
	public MemberChatBroadcastReceiver()
	{
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
	    if(intent == null || intent.getAction() == null)
		return;

	    switch(intent.getAction())
	    {
	    case "org.purple.smoke.chat_local_message":
	    case "org.purple.smoke.chat_message":
		boolean local = intent.getAction().equals
		("org.purple.smoke.chat_local_message");

	        if(intent.
		   getStringExtra("org.purple.smoke.sipHashId") != null &&
		   intent.getStringExtra("org.purple.smoke.sipHashId").
		   equals(m_sipHashId))
		{
		    try
		    {
			m_adapter.notifyDataSetChanged(); /*
							  ** Items are inserted
							  ** into the database
							  ** haphazardly.
							  */
			m_adapter.notifyItemInserted
			    (m_adapter.getItemCount() - 1);
		    }
		    catch(Exception exception)
		    {
		    }

		    if(!local)
		    {
			try
			{
			    if(m_ringtone != null)
				m_ringtone.stop();

			    Uri notification = RingtoneManager.getDefaultUri
				(RingtoneManager.TYPE_NOTIFICATION);

			    m_ringtone = RingtoneManager.getRingtone
				(getApplicationContext(), notification);
			    m_ringtone.play();
			}
			catch(Exception exception)
			{
			}
			finally
			{
			}
		    }
		}
		else
		    Miscellaneous.showNotification
			(MemberChat.this,
			 intent,
			 findViewById(R.id.main_layout));

		break;
	    case "org.purple.smoke.half_and_half_call":
	    case "org.purple.smoke.neighbor_aborted":
	    case "org.purple.smoke.neighbor_connected":
	    case "org.purple.smoke.neighbor_disconnected":
	    case "org.purple.smoke.network_connected":
	    case "org.purple.smoke.network_disconnected":
		prepareStatus();
		break;
	    case "org.purple.smoke.state_participants_populated":
		invalidateOptionsMenu();
		break;
	    case "org.purple.smoke.time":
		Miscellaneous.showNotification
		    (MemberChat.this, intent, findViewById(R.id.main_layout));
		break;
	    case "org.purple.smoke.steam_added":
		Miscellaneous.showNotification
		    (MemberChat.this, intent, findViewById(R.id.main_layout));
		break;
	    default:
		break;
	    }
	}
    }

    private class RemoveSelectedMessages implements Runnable
    {
	private String m_sipHashId = null;

	private RemoveSelectedMessages(Dialog dialog, String sipHashId)
	{
	    m_sipHashId = sipHashId;
	}

	@Override
	public void run()
	{
	    try
	    {
		for(Integer key : m_selectedMessages.keySet())
		    m_database.deleteParticipantMessage
			(s_cryptography, m_sipHashId, key);

		m_selectedMessages.clear();
	    }
	    catch(Exception exception)
	    {
	    }
	}
    }

    private static class SmokeLinearLayoutManager extends LinearLayoutManager
    {
	SmokeLinearLayoutManager(Context context)
	{
	    super(context);
	}

	@Override
	public void onLayoutChildren(RecyclerView.Recycler recycler,
				     RecyclerView.State state)
	{
	    /*
	    ** Android may terminate!
	    */

	    try
	    {
		super.onLayoutChildren(recycler, state);
	    }
	    catch(Exception exception)
	    {
	    }
	}
    }

    private ArsonEphemeralKeyGenerator m_arson = null;
    private ConcurrentHashMap<Integer, Boolean> m_selectedMessages = null;
    private Database m_database = null;
    private MemberChatAdapter m_adapter = null;
    private MemberChatBroadcastReceiver m_receiver = null;
    private RecyclerView m_recyclerView = null;
    private Ringtone m_ringtone = null;
    private ScheduledExecutorService m_statusScheduler = null;
    private SmokeLinearLayoutManager m_layoutManager = null;
    private String m_name = Cryptography.DEFAULT_SIPHASH_ID;
    private String m_sipHashId = m_name;
    private boolean m_messageSelectionStateEnabled = false;
    private boolean m_receiverRegistered = false;
    private byte m_attachment[] = null;
    private final int m_lastContextMenuPosition[] = new int[] {0, 0};
    private final static Cryptography s_cryptography =
	Cryptography.getInstance();
    private final static int SELECT_IMAGE_REQUEST = 0;
    private final static long AWAIT_TERMINATION = 5L; // 5 seconds.
    private int m_oid = -1;

    public abstract static class ContextMenuEnumerator
    {
	public final static int CALL_VIA_MCELIECE = 0;
	public final static int CALL_VIA_RSA = 1;
	public final static int COPY_TEXT = 2;
	public final static int CUSTOM_SESSION = 3;
	public final static int DELETE_ALL_MESSAGES = 4;
	public final static int DELETE_MESSAGE = 5;
	public final static int DELETE_SELECTED_MESSAGES = 6;
	public final static int JUGGERKNOT = 7;
	public final static int JUGGERLI = 8;
	public final static int JUGGERNAUT = 9;
	public final static int OPTIONAL_RECEIVE_RESPONSE = 10;
	public final static int OPTIONAL_SIGNATURES = 11;
	public final static int OPTIONAL_STEAM = 12;
	public final static int PURGE_FIASCO_KEYS = 13;
	public final static int RESEND_MESSAGE = 14;
	public final static int RETRIEVE_MESSAGES = 15;
	public final static int SAVE_ATTACHMENT = 16;
	public final static int SELECTION_STATE = 17;
	public final static int VIEW_DETAILS = 18;
    }

    private boolean hasPublicKeys()
    {
	return m_database.hasPublicKeys(s_cryptography, m_sipHashId);
    }

    private boolean isParticipantPaired(ArrayList<ParticipantElement> arrayList)
    {
	if(arrayList == null)
	    arrayList = m_database.readParticipants
		(s_cryptography, m_sipHashId);

	ParticipantElement participantElement =
	    arrayList == null || arrayList.isEmpty() ? null : arrayList.get(0);

	if(arrayList != null)
	    arrayList.clear();

	return participantElement != null &&
	    participantElement.m_keyStream != null &&
	    participantElement.m_keyStream.length ==
	    Cryptography.CIPHER_HASH_KEYS_LENGTH;
    }

    private int getBytesPerPixel(Config config)
    {
        switch(config)
	{
	case ALPHA_8:
	    return 1;
	case ARGB_4444:
	    return 2;
	case ARGB_8888:
	    return 4;
	case RGB_565:
	    return 2;
	default:
	    break;
        }

	return 1;
    }

    private void prepareListeners()
    {
	Button button1 = (Button) findViewById(R.id.attachment);

        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(MemberChat.this.isFinishing())
		    return;

		showGalleryActivity();
	    }
	});

	button1 = (Button) findViewById(R.id.remove_preview);
	button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(MemberChat.this.isFinishing())
		    return;

		findViewById(R.id.preview_layout).setVisibility(View.GONE);
		m_attachment = null;
	    }
	});

	button1 = (Button) findViewById(R.id.send_chat_message);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(MemberChat.this.isFinishing())
		    return;

		final TextView textView1 = (TextView) findViewById
		    (R.id.chat_message);

		if(m_attachment == null &&
		   textView1.getText().toString().trim().isEmpty())
		    return;

		String str = textView1.getText().toString().trim();
		int size = Chat.CHAT_MESSAGE_PREFERRED_SIZE *
		    (int) Math.ceil((1.0 * str.length()) /
				    (1.0 * Chat.CHAT_MESSAGE_PREFERRED_SIZE));

		if(size > str.length())
		{
		    char a[] = new char[size - str.length()];

		    Arrays.fill(a, ' ');
		    str += new String(a);
		}
		else if(str.length() > 0)
		{
		    char a[] = new char[1024 + str.length() % 2];

		    Arrays.fill(a, ' ');
		    str += new String(a);
		}

		byte keyStream[] = m_database.participantKeyStream
		    (s_cryptography, m_sipHashId);

		if(keyStream == null ||
		   keyStream.length != Cryptography.CIPHER_HASH_KEYS_LENGTH)
		    return;

		Kernel.getInstance().enqueueChatMessage
		    (str, m_sipHashId, m_attachment, keyStream);
		findViewById(R.id.preview_layout).setVisibility(View.GONE);
		m_attachment = null;
		textView1.post(new Runnable()
		{
		    @Override
		    public void run()
		    {
			textView1.requestFocus();
		    }
		});
		textView1.setText("");
	    }
	});

	button1 = (Button) findViewById(R.id.status);
        button1.setOnClickListener(new View.OnClickListener()
	{
	    public void onClick(View view)
	    {
		if(MemberChat.this.isFinishing())
		    return;

		registerForContextMenu(findViewById(R.id.status));
		openContextMenu(findViewById(R.id.status));
	    }
	});
    }

    private void prepareSchedulers()
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
			ArrayList<ParticipantElement> arrayList =
			    m_database.readParticipants
			    (s_cryptography, m_sipHashId);
			final ParticipantElement participantElement =
			    arrayList == null || arrayList.isEmpty() ?
			    null : arrayList.get(0);
			final boolean isConnected = Kernel.getInstance().
			    isConnected();
			final boolean isPaired = hasPublicKeys() &&
			    isParticipantPaired(arrayList);

			try
			{
			    MemberChat.this.runOnUiThread(new Runnable()
			    {
				@Override
				public void run()
				{
				    Button button =
					(Button) findViewById(R.id.status);

				    if(!isPaired)
					button.setBackgroundResource
					    (R.drawable.chat_faulty_session);
				    else if(Math.abs(System.
						     currentTimeMillis() -
						     participantElement.
						     m_lastStatusTimestamp) >
					    Chat.STATUS_WINDOW || !isConnected)
					button.setBackgroundResource
					    (R.drawable.chat_status_offline);
				    else
					button.setBackgroundResource
					    (R.drawable.chat_status_online);

				    if(!m_adapter.contextMenuShown())
					m_adapter.notifyDataSetChanged();
				}
			    });
			}
			catch(Exception exception)
			{
			}

			if(arrayList != null)
			    arrayList.clear();
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 0L, Chat.CONNECTION_STATUS_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    private void prepareStatus()
    {
	try
	{
	    ArrayList<ParticipantElement> arrayList =
		m_database.readParticipants(s_cryptography, m_sipHashId);
	    Button button = (Button) findViewById(R.id.send_chat_message);
	    ParticipantElement participantElement =
		arrayList == null || arrayList.isEmpty() ?
		null : arrayList.get(0);
	    boolean isPaired = hasPublicKeys() &&
		isParticipantPaired(arrayList);
	    int availableNeighbors = Kernel.getInstance().availableNeighbors();

	    if(availableNeighbors > 0 && isPaired)
	    {
		button.setBackgroundResource(R.drawable.send);
		button.setEnabled(true);
	    }
	    else
	    {
		button.setBackgroundResource(R.drawable.warning);
		button.setEnabled(false);
	    }

	    button = (Button) findViewById(R.id.status);

	    if(!isPaired)
		button.setBackgroundResource(R.drawable.chat_faulty_session);
	    else if(!Kernel.getInstance().isConnected() ||
		    Math.abs(System.currentTimeMillis() -
			     participantElement.m_lastStatusTimestamp) >
		    Chat.STATUS_WINDOW)
		button.setBackgroundResource(R.drawable.chat_status_offline);
	    else
		button.setBackgroundResource(R.drawable.chat_status_online);

	    if(arrayList != null)
		arrayList.clear();
	}
	catch(Exception exception)
	{
	}

	try
	{
	    getSupportActionBar().setSubtitle(Smoke.networkStatusString());
	}
	catch(Exception exception)
	{
	}
    }

    private void releaseResources()
    {
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
		   awaitTermination(AWAIT_TERMINATION, TimeUnit.SECONDS))
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
    }

    private void saveState()
    {
	TextView textView1 = (TextView) findViewById(R.id.chat_message);

	State.getInstance().writeCharSequence
	    ("member_chat.message", textView1.getText());
    }

    private void showChatActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, Chat.class);

	startActivity(intent);
	finish();
    }

    private void showDetailsOfMessage(int oid)
    {
	if(MemberChat.this.isFinishing())
	    return;
	else if(m_lastContextMenuPosition[0] < 0 ||
		m_lastContextMenuPosition[1] < 0)
	    return;

	class SingleShot implements Runnable
	{
	    private int m_oid = -1;

	    SingleShot(int oid)
	    {
		m_oid = oid;
	    }

	    @Override
	    public void run()
	    {
		MemberChat.this.runOnUiThread(new Runnable()
		{
		    @Override
		    public void run()
		    {
			PopupWindow popupWindow = new PopupWindow
			    (MemberChat.this);
			String string = m_database.messageDetails(m_oid).trim();
			TextView textView1 = new TextView(MemberChat.this);
			float density = getApplicationContext().getResources().
			    getDisplayMetrics().density;

			textView1.setBackgroundColor(Color.rgb(255, 255, 255));
			textView1.setPaddingRelative
			    ((int) (10 * density),
			     (int) (10 * density),
			     (int) (10 * density),
			     (int) (10 * density));
			textView1.setTextSize(16);

			if(string.isEmpty())
			    textView1.setText
				("Cannot retrieve message details.");
			else
			    textView1.setText(string);

			popupWindow.setContentView(textView1);
			popupWindow.setOutsideTouchable(true);

			if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
			{
			    popupWindow.setHeight(450);
			    popupWindow.setWidth(700);
			}

			popupWindow.showAtLocation
			    (findViewById(R.id.recycler_view),
			     Gravity.START | Gravity.TOP,
			     m_lastContextMenuPosition[0],
			     m_lastContextMenuPosition[1]);
			m_lastContextMenuPosition[0] =
			    m_lastContextMenuPosition[1] = -1;
		    }
		});
	    }
	}

	new Thread(new SingleShot(oid)).start();
    }

    private void showFireActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, Fire.class);

	startActivity(intent);
	finish();
    }

    private void showGalleryActivity()
    {
	Intent intent = new Intent
	    (Intent.ACTION_PICK,
	     android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(intent, SELECT_IMAGE_REQUEST);
    }

    private void showMemberChatActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, MemberChat.class);

	startActivity(intent);
	finish();
    }

    private void showSettingsActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, Settings.class);

	startActivity(intent);
	finish();
    }

    private void showSmokescreenActivity()
    {
	Intent intent = new Intent(MemberChat.this, Smokescreen.class);

	startActivity(intent);
	finish();
    }

    private void showSteamActivity()
    {
	saveState();

	Intent intent = new Intent(MemberChat.this, Steam.class);

	startActivity(intent);
	finish();
    }

    @Override
    protected void onActivityResult(int requestCode,
				    int resultCode,
				    Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        try
	{
	    if(data != null &&
	       requestCode == SELECT_IMAGE_REQUEST &&
	       resultCode == RESULT_OK)
	    {
		final ProgressBar bar = (ProgressBar) findViewById
		    (R.id.progress_bar);

		bar.setIndeterminate(true);
		bar.setVisibility(ProgressBar.VISIBLE);
		findViewById(R.id.preview_layout).setVisibility(View.GONE);
		getWindow().setFlags
		    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
		     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
		m_attachment = null;
		Miscellaneous.enableChildren
		    (findViewById(R.id.main_layout), false);

		class SingleShot implements Runnable
		{
		    private Uri m_uri = null;
		    private byte m_bytes[] = null;

		    SingleShot(Uri uri)
		    {
			m_uri = uri;
		    }

		    @Override
		    public void run()
		    {
			ByteArrayOutputStream byteArrayOutputStream = null;
			InputStream inputStream = null;

			try
			{
			    Bitmap bitmap = null;
			    BitmapFactory.Options options = new
				BitmapFactory.Options();

			    options.inSampleSize = 2;
			    inputStream = getContentResolver().
				openInputStream(m_uri);
			    bitmap = BitmapFactory.decodeStream
				(inputStream, null, options);

			    if(bitmap != null)
			    {
				byteArrayOutputStream =
				    new ByteArrayOutputStream();
				bitmap.compress
				    (Bitmap.CompressFormat.JPEG,
				     Miscellaneous.
				     imagePercentFromArrayLength
				     (bitmap.getByteCount() *
				      getBytesPerPixel(bitmap.getConfig())),
				     byteArrayOutputStream);
				m_bytes = byteArrayOutputStream.toByteArray();
			    }
			    else
				m_bytes = null;
			}
			catch(Exception exception)
			{
			    m_bytes = null;
			}
			finally
			{
			    try
			    {
				if(byteArrayOutputStream != null)
				    byteArrayOutputStream.close();
			    }
			    catch(Exception exception)
			    {
			    }

			    try
			    {
				if(inputStream != null)
				    inputStream.close();
			    }
			    catch(Exception exception)
			    {
			    }
			}

			try
			{
			    MemberChat.this.runOnUiThread(new Runnable()
			    {
				@Override
				public void run()
				{
				    try(ByteArrayInputStream
					byteArrayOutputStream =
					new ByteArrayInputStream(m_bytes))
				    {
					BitmapFactory.Options options = new
					    BitmapFactory.Options();

					options.inSampleSize = 2;

					Bitmap bitmap =
					    BitmapFactory.decodeStream
					    (byteArrayOutputStream,
					     null,
					     options);

					if(bitmap != null)
					{
					    ImageView imageView = (ImageView)
						findViewById(R.id.preview);

					    findViewById(R.id.preview_layout).
						setVisibility(View.VISIBLE);
					    imageView.setImageBitmap
						(Bitmap.
						 createScaledBitmap
						 (bitmap,
						  bitmap.getWidth(),
						  Math.min(200,
							   bitmap.getHeight()),
						  false));
					    m_attachment = m_bytes;
					}
					else
					    findViewById(R.id.preview_layout).
						setVisibility(View.GONE);
				    }
				    catch(Exception exception)
				    {
				    }

				    bar.setVisibility(ProgressBar.INVISIBLE);
				    getWindow().clearFlags
					(WindowManager.LayoutParams.
					 FLAG_NOT_TOUCHABLE);
				    Miscellaneous.enableChildren
					(findViewById(R.id.main_layout), true);
				}
			    });
			}
			catch(Exception exception)
			{
			}
		    }
		}

		new Thread(new SingleShot(data.getData())).start();
            }
	}
	catch(Exception exception)
	{
	    m_attachment = null;
	}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_chat);
	setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
	m_arson = new ArsonEphemeralKeyGenerator
	    (State.getInstance().getString("member_chat_siphash_id"));
	m_database = Database.getInstance(getApplicationContext());
	m_layoutManager = new SmokeLinearLayoutManager(MemberChat.this);
	m_layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
	m_layoutManager.setReverseLayout(true);
	m_layoutManager.setStackFromEnd(true);
	m_name = m_sipHashId = State.getInstance().getString
	    ("member_chat_siphash_id");

	try
	{
	    m_oid = Integer.parseInt
		(State.getInstance().getString("member_chat_oid"));
	}
	catch(Exception exception)
	{
	    m_oid = -1;
	}

	m_receiver = new MemberChatBroadcastReceiver();
	m_recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
	m_recyclerView.setHasFixedSize(true);
	m_selectedMessages = new ConcurrentHashMap<> ();

	if(m_sipHashId.isEmpty())
	    m_name = m_sipHashId = Cryptography.DEFAULT_SIPHASH_ID;

	/*
	** Prepare various widgets.
	*/

	m_adapter = new MemberChatAdapter(this, m_sipHashId);
	m_adapter.registerAdapterDataObserver
	    (new RecyclerView.AdapterDataObserver()
	    {
		@Override
		public void onItemRangeInserted
		    (int positionStart, int itemCount)
		{
		    m_layoutManager.smoothScrollToPosition
			(m_recyclerView, null, positionStart);
		}

		@Override
		public void onItemRangeRemoved
		    (int positionStart, int itemCount)
		{
		    m_layoutManager.smoothScrollToPosition
			(m_recyclerView, null, positionStart - itemCount);
		}
	    });
	m_name = m_database.nameFromSipHashId(s_cryptography, m_sipHashId);

	if(m_name.isEmpty())
	    m_name = m_sipHashId;

	m_recyclerView.setAdapter(m_adapter);
	m_recyclerView.setLayoutManager(m_layoutManager);

	String string =	Miscellaneous.prepareSipHashId(m_sipHashId);

	try
	{
	    getSupportActionBar().setSubtitle(Smoke.networkStatusString());

	    if(string.isEmpty())
		getSupportActionBar().setTitle("Smoke | Member Chat");
	    else
		getSupportActionBar().setTitle
		    ("Smoke | " + m_name + "@" + string);
	}
	catch(Exception exception)
	{
	}

	/*
	** Prepare listeners.
	*/

	prepareListeners();

	/*
	** Restore states.
	*/

	try
	{
	    m_layoutManager.smoothScrollToPosition
		(m_recyclerView, null, m_adapter.getItemCount() - 1);

	    TextView textView1 = (TextView) findViewById(R.id.chat_message);

	    textView1.setText
		(State.getInstance().getCharSequence("member_chat.message"));
	}
	catch(Exception exception)
	{
	}
    }

    @Override
    protected void onDestroy()
    {
	if(State.getInstance().exit())
	    android.os.Process.killProcess(android.os.Process.myPid());
	else
	    super.onDestroy();
    }

    @Override
    protected void onPause()
    {
	super.onPause();

	if(m_receiverRegistered)
	{
	    LocalBroadcastManager.getInstance(getApplicationContext()).
		unregisterReceiver(m_receiver);
	    m_receiverRegistered = false;
	}

	releaseResources();
	saveState();
    }

    @Override
    protected void onResume()
    {
	super.onResume();

	if(!m_receiverRegistered)
	{
	    IntentFilter intentFilter = new IntentFilter();

	    intentFilter.addAction("org.purple.smoke.chat_local_message");
	    intentFilter.addAction("org.purple.smoke.chat_message");
	    intentFilter.addAction("org.purple.smoke.half_and_half_call");
	    intentFilter.addAction("org.purple.smoke.neighbor_aborted");
	    intentFilter.addAction("org.purple.smoke.neighbor_connected");
	    intentFilter.addAction("org.purple.smoke.neighbor_disconnected");
	    intentFilter.addAction("org.purple.smoke.network_connected");
	    intentFilter.addAction("org.purple.smoke.network_disconnected");
	    intentFilter.addAction
		("org.purple.smoke.state_participants_populated");
	    intentFilter.addAction("org.purple.smoke.steam_added");
	    intentFilter.addAction("org.purple.smoke.time");
	    LocalBroadcastManager.getInstance(getApplicationContext()).
		registerReceiver(m_receiver, intentFilter);
	    m_receiverRegistered = true;
	}

	prepareSchedulers();
	prepareStatus();

	try
	{
	    m_adapter.notifyDataSetChanged();
	    m_layoutManager.smoothScrollToPosition
		(m_recyclerView, null, m_adapter.getItemCount() - 1);

	    TextView textView1 = (TextView) findViewById(R.id.chat_message);

	    textView1.setText
		(State.getInstance().getCharSequence("member_chat.message"));
	}
	catch(Exception exception)
	{
	}

	try
	{
	    m_messageSelectionStateEnabled = State.getInstance().
		selectSwitch(m_sipHashId + "_selection_state");
	    m_adapter.notifyDataSetChanged();
	}
	catch(Exception exception)
	{
	}
    }

    public boolean isMessageSelected(int oid)
    {
	try
	{
	    if(m_selectedMessages.containsKey(oid))
		return m_selectedMessages.get(oid);
	}
	catch(Exception exception)
	{
	}

	return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem)
    {
	if(menuItem == null)
	    return false;

	final int groupId = menuItem.getGroupId();
	final int itemId = menuItem.getItemId();

	/*
	** Prepare a listener.
	*/

	DialogInterface.OnCancelListener listener =
	    new DialogInterface.OnCancelListener()
	    {
		public void onCancel(DialogInterface dialog)
		{
		    switch(groupId)
		    {
		    case ContextMenuEnumerator.CUSTOM_SESSION:
			try
			{
			    String string = State.getInstance().
				getString("member_chat_secret_input").trim();

			    if(!string.isEmpty())
			    {
				byte bytes[] = Cryptography.pbkdf2
				    (Cryptography.
				     sha512(string.getBytes(StandardCharsets.
							    UTF_8)),
				     string.toCharArray(),
				     Chat.CUSTOM_SESSION_ITERATION_COUNT,
				     160); // SHA-1
				int oid = m_database.participantOidFromSipHash
				    (s_cryptography, m_sipHashId);

				if(bytes != null)
				    bytes = Cryptography.pbkdf2
					(Cryptography.
					 sha512(string.
						getBytes(StandardCharsets.
							 UTF_8)),
					 Base64.
					 encodeToString(bytes, Base64.NO_WRAP).
					 toCharArray(),
					 1,
					 Cryptography.CIPHER_HASH_KEYS_LENGTH *
					 8);

				m_database.setParticipantKeyStream
				    (s_cryptography, bytes, oid);
			    }
			}
			catch(Exception exception)
			{
			}

			State.getInstance().removeKey
			    ("member_chat_secret_input");
			break;
		    case ContextMenuEnumerator.DELETE_ALL_MESSAGES:
			if(State.getInstance().getString("dialog_accepted").
			   equals("true"))
			{
			    m_database.deleteParticipantMessages
				(s_cryptography, m_sipHashId);
			    m_selectedMessages.clear();
			    m_adapter.notifyDataSetChanged();
			}

			break;
		    case ContextMenuEnumerator.DELETE_MESSAGE:
			if(State.getInstance().getString("dialog_accepted").
			   equals("true"))
			{
			    m_database.deleteParticipantMessage
				(s_cryptography, m_sipHashId, itemId);
			    m_selectedMessages.remove(itemId);
			    m_adapter.notifyDataSetChanged();
			}

			break;
		    case ContextMenuEnumerator.DELETE_SELECTED_MESSAGES:
			if(State.getInstance().getString("dialog_accepted").
			   equals("true"))
			{
			    Dialog d = null;

			    try
			    {
				d = new Dialog(MemberChat.this);
				Windows.showProgressDialog
				    (MemberChat.this,
				     d,
				     "Deleting selected message(s).");

				Thread thread = new Thread
				    (new RemoveSelectedMessages(d,
								m_sipHashId));

				thread.start();
				thread.join();
			    }
			    catch(Exception exception_1)
			    {
			    }
			    finally
			    {
				try
				{
				    if(d != null)
					d.dismiss();
				}
				catch(Exception exception_2)
				{
				}
			    }

			    m_adapter.notifyDataSetChanged();
			}

			break;
		    case ContextMenuEnumerator.JUGGERKNOT:
		    case ContextMenuEnumerator.JUGGERLI:
		    case ContextMenuEnumerator.JUGGERNAUT:
			try
			{
			    String string = State.getInstance().
				getString("member_chat_secret_input").trim();

			    if(!string.isEmpty())
			    {
				byte keyStream[] = m_database.
				    participantKeyStream
				    (s_cryptography, m_sipHashId);

				if(!(keyStream == null ||
				     keyStream.length !=
				     Cryptography.CIPHER_HASH_KEYS_LENGTH))
				    Kernel.getInstance().enqueueJuggernaut
					(string,
					 m_sipHashId,
					 groupId ==
					 ContextMenuEnumerator.JUGGERKNOT ||
					 groupId ==
					 ContextMenuEnumerator.JUGGERLI,
					 keyStream);
			    }
			}
			catch(Exception exception)
			{
			}

			State.getInstance().removeKey
			    ("member_chat_secret_input");
			break;
		    case ContextMenuEnumerator.PURGE_FIASCO_KEYS:
			if(State.getInstance().getString("dialog_accepted").
			   equals("true"))
			    m_database.deleteFiascoKeysOfSiphashId
				(s_cryptography, m_sipHashId);

			break;
		    default:
			break;
		    }
		}
	    };

	switch(groupId)
	{
	case ContextMenuEnumerator.CALL_VIA_MCELIECE:
	    Kernel.getInstance().call
		(m_oid, ParticipantCall.Algorithms.MCELIECE, m_sipHashId);
	    break;
	case ContextMenuEnumerator.CALL_VIA_RSA:
	    Kernel.getInstance().
		call(m_oid, ParticipantCall.Algorithms.RSA, m_sipHashId);
	    break;
	case ContextMenuEnumerator.COPY_TEXT:
	    try
	    {
		View view = m_layoutManager.findViewByPosition(itemId);

		if(view != null)
		{
		    TextView textView = (TextView) view.findViewById
			(R.id.text);

		    if(textView != null)
		    {
			ClipboardManager clipboardManager = (ClipboardManager)
			    getSystemService(Context.CLIPBOARD_SERVICE);

			if(clipboardManager != null)
			{
			    ClipData clipData = null;
			    SpannableStringBuilder spannableStringBuilder =
				new SpannableStringBuilder(textView.getText());

			    spannableStringBuilder.clearSpans();
			    clipData = ClipData.newPlainText
				("Smoke", spannableStringBuilder.toString());
			    clipboardManager.setPrimaryClip(clipData);
			}
		    }
		}
	    }
	    catch(Exception exception)
	    {
	    }

	    break;
	case ContextMenuEnumerator.CUSTOM_SESSION:
	    Miscellaneous.showTextInputDialog
		(MemberChat.this,
		 listener,
		 "Please provide a secret.",
		 "",
		 "Secret",
		 true);
	    break;
	case ContextMenuEnumerator.DELETE_ALL_MESSAGES:
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete all " +
		 "of the messages?");
	    break;
	case ContextMenuEnumerator.DELETE_MESSAGE:
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete the " +
		 "selected message?");
	    break;
	case ContextMenuEnumerator.DELETE_SELECTED_MESSAGES:
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete the " +
		 "selected message(s)?");
	    break;
	case ContextMenuEnumerator.JUGGERKNOT:
	case ContextMenuEnumerator.JUGGERLI:
	case ContextMenuEnumerator.JUGGERNAUT:
	    if(groupId == ContextMenuEnumerator.JUGGERKNOT)
		Miscellaneous.showTextInputDialog
		    (MemberChat.this,
		     listener,
		     "Please provide a secret. The Juggernaut Protocol " +
		     "will be initiated shortly (" +
		     Kernel.JUGGERNAUT_DELAY / 1000.0 +
		     " seconds) after this dialog is confirmed. If the " +
		     "protocol completes correctly, new session credentials " +
		     "will be generated.",
		     "",
		     "Juggernaut Secret",
		     true);
	    else if(groupId == ContextMenuEnumerator.JUGGERLI)
	    {
		PublicKey publicKey1 = m_database.
		    publicEncryptionKeyForSipHashId
		    (s_cryptography, m_sipHashId);
		PublicKey publicKey2 = m_database.
		    publicSignatureKeyForSipHashId(s_cryptography, m_sipHashId);
		byte bytes[] = Cryptography.xor
		    (s_cryptography.chatEncryptionPublicKey().getEncoded(),
		     s_cryptography.chatSignaturePublicKey().getEncoded(),
		     publicKey1.getEncoded(),
		     publicKey2.getEncoded());

		Miscellaneous.showTextInputDialog
		    (MemberChat.this,
		     listener,
		     "The Juggernaut Protocol " +
		     "will be initiated shortly (" +
		     Kernel.JUGGERNAUT_DELAY / 1000.0 +
		     " seconds) after this dialog is confirmed. If the " +
		     "protocol completes correctly, new session credentials " +
		     "will be generated.",
		     Miscellaneous.byteArrayAsHexString(bytes),
		     "Juggernaut Via Public Keys",
		     false);
	    }
	    else
		Miscellaneous.showTextInputDialog
		    (MemberChat.this,
		     listener,
		     "Please provide a secret. The Juggernaut Protocol " +
		     "will be initiated shortly (" +
		     Kernel.JUGGERNAUT_DELAY / 1000.0 +
		     " seconds) after this dialog is confirmed.",
		     "",
		     "Juggernaut Secret",
		     true);

	    break;
	case ContextMenuEnumerator.OPTIONAL_RECEIVE_RESPONSE:
	{
	    menuItem.setChecked(!menuItem.isChecked());

	    String strings[] = null;
	    StringBuilder stringBuilder = new StringBuilder
		(m_database.
		 readParticipantOptions(s_cryptography, m_sipHashId));

	    strings = stringBuilder.toString().split(";");

	    if(strings == null || strings.length == 0)
	    {
		if(menuItem.isChecked())
		    stringBuilder.append("optional_receive_response = true");
		else
		    stringBuilder.append("optional_receive_response = false");
	    }
	    else
	    {
		stringBuilder.delete(0, stringBuilder.length());

		int i = 0;
		int length = strings.length;

		for(String string : strings)
		{
		    if(!(string.equals("optional_receive_response = false") ||
			 string.equals("optional_receive_response = true")))
		    {
			stringBuilder.append(string);

			if(i != length - 1)
			    stringBuilder.append(";");
		    }

		    i += 1;
		}

		if(stringBuilder.length() > 0)
		    stringBuilder.append(";");

		stringBuilder.append("optional_receive_response = ");
		stringBuilder.append(menuItem.isChecked() ? "true" : "false");
	    }

	    m_database.writeParticipantOptions
		(s_cryptography, stringBuilder.toString(), m_sipHashId);
	    break;
	}
	case ContextMenuEnumerator.OPTIONAL_SIGNATURES:
	{
	    menuItem.setChecked(!menuItem.isChecked());

	    String strings[] = null;
	    StringBuilder stringBuilder = new StringBuilder
		(m_database.
		 readParticipantOptions(s_cryptography, m_sipHashId));

	    strings = stringBuilder.toString().split(";");

	    if(strings == null || strings.length == 0)
	    {
		if(menuItem.isChecked())
		    stringBuilder.append("optional_signatures = true");
		else
		    stringBuilder.append("optional_signatures = false");
	    }
	    else
	    {
		stringBuilder.delete(0, stringBuilder.length());

		int i = 0;
		int length = strings.length;

		for(String string : strings)
		{
		    if(!(string.equals("optional_signatures = false") ||
			 string.equals("optional_signatures = true")))
		    {
			stringBuilder.append(string);

			if(i != length - 1)
			    stringBuilder.append(";");
		    }

		    i += 1;
		}

		if(stringBuilder.length() > 0)
		    stringBuilder.append(";");

		stringBuilder.append("optional_signatures = ");
		stringBuilder.append
		    (menuItem.isChecked() ? "true" : "false");
	    }

	    m_database.writeParticipantOptions
		(s_cryptography, stringBuilder.toString(), m_sipHashId);
	    break;
	}
	case ContextMenuEnumerator.OPTIONAL_STEAM:
	{
	    menuItem.setChecked(!menuItem.isChecked());

	    String strings[] = null;
	    StringBuilder stringBuilder = new StringBuilder
		(m_database.
		 readParticipantOptions(s_cryptography, m_sipHashId));

	    strings = stringBuilder.toString().split(";");

	    if(strings == null || strings.length == 0)
	    {
		if(menuItem.isChecked())
		    stringBuilder.append("optional_steam = true");
		else
		    stringBuilder.append("optional_steam = false");
	    }
	    else
	    {
		stringBuilder.delete(0, stringBuilder.length());

		int i = 0;
		int length = strings.length;

		for(String string : strings)
		{
		    if(!(string.equals("optional_steam = false") ||
			 string.equals("optional_steam = true")))
		    {
			stringBuilder.append(string);

			if(i != length - 1)
			    stringBuilder.append(";");
		    }

		    i += 1;
		}

		if(stringBuilder.length() > 0)
		    stringBuilder.append(";");

		stringBuilder.append("optional_steam = ");
		stringBuilder.append
		    (menuItem.isChecked() ? "true" : "false");
	    }

	    m_database.writeParticipantOptions
		(s_cryptography, stringBuilder.toString(), m_sipHashId);
	    break;
	}
	case ContextMenuEnumerator.PURGE_FIASCO_KEYS:
	    Miscellaneous.showPromptDialog
		(MemberChat.this,
		 listener,
		 "Are you sure that you wish to delete the Fiasco keys of " +
		 m_sipHashId +
		 "?");
	    break;
	case ContextMenuEnumerator.RESEND_MESSAGE:
	    Kernel.getInstance().resendMessage(m_sipHashId, itemId);
	    break;
	case ContextMenuEnumerator.RETRIEVE_MESSAGES:
	    Kernel.getInstance().retrieveChatMessages(m_sipHashId);
	    break;
	case ContextMenuEnumerator.SAVE_ATTACHMENT:
	    try
	    {
		View view = m_layoutManager.findViewByPosition(itemId);

		if(view != null)
		{
		    ImageView imageView = (ImageView) view.findViewById
			(R.id.image);

		    if(imageView != null)
		    {
			final ProgressBar bar = (ProgressBar) findViewById
			    (R.id.progress_bar);

			bar.setIndeterminate(true);
			bar.setVisibility(ProgressBar.VISIBLE);
			getWindow().setFlags
			    (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
			     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
			Miscellaneous.enableChildren
			    (findViewById(R.id.main_layout), false);

			class SingleShot implements Runnable
			{
			    public SingleShot()
			    {
			    }

			    @Override
			    public void run()
			    {
				ByteArrayInputStream byteArrayInputStream =
				    null;
				ByteArrayOutputStream byteArrayOutputStream =
				    null;

				try
				{
				    BitmapFactory.Options options =
					new BitmapFactory.Options();

				    options.inSampleSize = 2;

				    MemberChatElement memberChatElement =
					m_database.readMemberChat
					(s_cryptography, m_sipHashId, itemId);

				    if(memberChatElement != null)
					byteArrayInputStream = new
					    ByteArrayInputStream
					    (memberChatElement.m_attachment);

				    /*
				    ** Convert the bytes into a bitmap.
				    */

				    Bitmap bitmap = null;

				    if(byteArrayInputStream != null)
					bitmap = BitmapFactory.decodeStream
					    (byteArrayInputStream,
					     null,
					     options);

				    if(bitmap != null)
				    {
					byteArrayOutputStream = new
					    ByteArrayOutputStream();
					bitmap.compress
					    (Bitmap.CompressFormat.JPEG,
					     100,
					     byteArrayOutputStream);

					File file = new File
					    (Environment.
					     getExternalStoragePublicDirectory
					     (Environment.DIRECTORY_DOWNLOADS),
					     "smoke-" +
					     System.currentTimeMillis() +
					     ".jpg");
					FileOutputStream fileOutputStream =
					    null;

					try
					{
					    if(!file.exists())
						file.createNewFile();

					    fileOutputStream =
						new FileOutputStream(file);
					    fileOutputStream.write
						(byteArrayOutputStream.
						 toByteArray());
					}
					catch(Exception exception)
					{
					}
					finally
					{
					    if(fileOutputStream != null)
						fileOutputStream.close();
					}
				    }
				}
				catch(Exception exception)
				{
				}
				finally
				{
				    try
				    {
					if(byteArrayInputStream != null)
					    byteArrayInputStream.close();
				    }
				    catch(Exception exception)
				    {
				    }

				    try
				    {
					if(byteArrayOutputStream != null)
					    byteArrayOutputStream.close();
				    }
				    catch(Exception exception)
				    {
				    }
				}

				try
				{
				    MemberChat.this.runOnUiThread(new Runnable()
				    {
					@Override
					public void run()
					{
					    bar.setVisibility
						(ProgressBar.INVISIBLE);
					    getWindow().clearFlags
						(WindowManager.LayoutParams.
						 FLAG_NOT_TOUCHABLE);
					    Miscellaneous.enableChildren
						(findViewById(R.id.main_layout),
						 true);
					}
				    });
				}
				catch(Exception exception)
				{
				}
			    }
			}

			new Thread(new SingleShot()).start();
		    }
		}
	    }
	    catch(Exception exception)
	    {
	    }

	    break;
	case ContextMenuEnumerator.SELECTION_STATE:
	    m_messageSelectionStateEnabled = !m_messageSelectionStateEnabled;
	    m_selectedMessages.clear();
	    m_adapter.notifyDataSetChanged();
	    State.getInstance().selectSwitch
		(m_sipHashId + "_selection_state",
		 m_messageSelectionStateEnabled);
	    break;
	case ContextMenuEnumerator.VIEW_DETAILS:
	    showDetailsOfMessage(itemId);
	    break;
	default:
	    break;
	}

	return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	getMenuInflater().inflate(R.menu.member_chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
	int groupId = menuItem.getGroupId();
	int itemId = menuItem.getItemId();

	if(groupId == Menu.NONE)
	    switch(itemId)
	    {
	    case R.id.action_chat:
		m_database.writeSetting(null, "lastActivity", "Chat");
		showChatActivity();
		return true;
	    case R.id.action_exit:
		Smoke.exit(true, MemberChat.this);
		return true;
	    case R.id.action_fire:
		m_database.writeSetting(null, "lastActivity", "Fire");
		showFireActivity();
		return true;
	    case R.id.action_settings:
		m_database.writeSetting(null, "lastActivity", "Settings");
		showSettingsActivity();
		return true;
	    case R.id.action_smokescreen:
		showSmokescreenActivity();
		return true;
	    case R.id.action_steam:
		m_database.writeSetting(null, "lastActivity", "Steam");
		showSteamActivity();
		return true;
	    default:
		break;
	    }
	else
	{
	    String sipHashId = menuItem.getTitle().toString();

	    try
	    {
		int indexOf = sipHashId.indexOf("(");

		if(indexOf >= 0)
		    sipHashId = sipHashId.substring
			(indexOf + 1).replace(")", "");
	    }
	    catch(Exception exception)
	    {
	    }

	    sipHashId = Miscellaneous.prepareSipHashId(sipHashId);
	    State.getInstance().setString
		("member_chat_oid", String.valueOf(itemId));
	    State.getInstance().setString
		("member_chat_siphash_id", sipHashId);
	    m_database.writeSetting(null, "lastActivity", "MemberChat");
	    m_database.writeSetting
		(s_cryptography, "member_chat_oid", String.valueOf(itemId));
	    m_database.writeSetting
		(s_cryptography, "member_chat_siphash_id", sipHashId);
	    showMemberChatActivity();
	}

        return super.onOptionsItemSelected(menuItem);
    }

    public boolean messageSelectionState()
    {
	return m_messageSelectionStateEnabled;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
	boolean isAuthenticated = State.getInstance().isAuthenticated();

	if(!m_database.accountPrepared())
	    /*
	    ** The database may have been modified or removed.
	    */

	    isAuthenticated = true;

	menu.findItem(R.id.action_authenticate).setEnabled(!isAuthenticated);
	Miscellaneous.addMembersToMenu(menu, 7, 250);
	return true;
    }

    public int selectedMessagesCount()
    {
	return m_selectedMessages.size();
    }

    @Override
    public void onBackPressed()
    {
	Intent intent = new Intent();

	intent.putExtra("Result", "Done");
	setResult(RESULT_OK, intent);
	super.onBackPressed();
    }

    @Override
    public void onContextMenuClosed(Menu menu)
    {
	m_adapter.setContextMenuClosed();
	super.onContextMenuClosed(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
				    View view,
				    ContextMenuInfo menuInfo)
    {
	if(menu == null || view == null)
	    return;

	super.onCreateContextMenu(menu, view, menuInfo);

	MenuItem menuItem = null;
	boolean hasPublicKeys = hasPublicKeys();
	boolean isParticipantPaired = isParticipantPaired(null);
	boolean state = Kernel.getInstance().isConnected();

	menu.add(ContextMenuEnumerator.CALL_VIA_MCELIECE,
		 -1,
		 0,
		 "Call via McEliece (Fujisaki)").
	    setEnabled(hasPublicKeys && state);
	menu.add(ContextMenuEnumerator.CALL_VIA_RSA,
		 -1,
		 1,
		 "Call via RSA").
	    setEnabled(hasPublicKeys && state);
	menu.add(ContextMenuEnumerator.CUSTOM_SESSION,
		 -1,
		 2,
		 "Custom Session").setEnabled(hasPublicKeys);
	menu.add(ContextMenuEnumerator.JUGGERKNOT,
		 -1,
		 3,
		 "JuggerKnot Credentials").
	    setEnabled(hasPublicKeys && isParticipantPaired && state);
	menu.add(ContextMenuEnumerator.JUGGERLI,
		 -1,
		 4,
		 "JuggerLi Credentials (Public Keys)").
	    setEnabled(hasPublicKeys && isParticipantPaired && state);
	menu.add(ContextMenuEnumerator.JUGGERNAUT,
		 -1,
		 5,
		 "Juggernaut").
	    setEnabled(hasPublicKeys() && isParticipantPaired && state);
	menuItem = menu.add(ContextMenuEnumerator.OPTIONAL_RECEIVE_RESPONSE,
			    -1,
			    6,
			    "Optional Receive Responses");
	menuItem.setCheckable(true);
	menuItem.setChecked
	    (m_database.
	     readParticipantOptions(s_cryptography, m_sipHashId).
	     contains("optional_receive_response = true"));
	menuItem = menu.add(ContextMenuEnumerator.OPTIONAL_SIGNATURES,
			    -1,
			    7,
			    "Optional Signatures");
	menuItem.setCheckable(true);
	menuItem.setChecked
	    (m_database.
	     readParticipantOptions(s_cryptography, m_sipHashId).
	     contains("optional_signatures = true"));
	menuItem = menu.add(ContextMenuEnumerator.OPTIONAL_STEAM,
			    -1,
			    8,
			    "Optional Steam");
	menuItem.setCheckable(true);
	menuItem.setChecked
	    (m_database.
	     readParticipantOptions(s_cryptography, m_sipHashId).
	     contains("optional_steam = true"));
	menu.add(ContextMenuEnumerator.PURGE_FIASCO_KEYS,
		 -1,
		 9,
		 "Purge Fiasco Keys").setEnabled
	    (m_database.fiascoCountViaParticipants(m_oid) >= 1L);
	menu.add(ContextMenuEnumerator.RETRIEVE_MESSAGES,
		 -1,
		 10,
		 "Retrieve Messages").setEnabled
	    (!m_database.readSetting(s_cryptography, "ozone_address").
	     isEmpty() && state);
	menuItem = menu.add(ContextMenuEnumerator.SELECTION_STATE,
			    -1,
			    11,
			    "Selection State").setCheckable(true);
	menuItem.setChecked(messageSelectionState());
    }

    public void prepareContextMenuPosition(View view)
    {
	if(view == null)
	    return;

	view.getLocationOnScreen(m_lastContextMenuPosition);
    }

    public void setMessageSelected(int oid, boolean isChecked)
    {
	try
	{
	    if(isChecked)
		m_selectedMessages.put(oid, isChecked);
	    else
		m_selectedMessages.remove(oid);
	}
	catch(Exception exception)
	{
	}
    }
}
