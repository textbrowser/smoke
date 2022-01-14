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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Patterns;
import android.util.SparseArray;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

public class Database extends SQLiteOpenHelper
{
    private Cursor m_readMemberChatCursor = null;
    private SQLiteDatabase m_db = null;
    private String m_readMemberChatSipHashId = "";
    private final Object m_readMemberChatCursorMutex = new Object();
    private final static Comparator<FireElement>
	s_readFiresComparator = new Comparator<FireElement> ()
	{
	    @Override
	    public int compare(FireElement e1, FireElement e2)
	    {
		if(e1 == null || e2 == null)
		    return -1;

		return e1.m_name.compareTo(e2.m_name);
	    }
	};
    private final static Comparator<NeighborElement>
	s_readNeighborsComparator = new Comparator<NeighborElement> ()
	{
	    @Override
	    public int compare(NeighborElement e1, NeighborElement e2)
	    {
		if(e1 == null || e2 == null)
		    return -1;

		/*
		** Sort by IP address, port, and transport.
		*/

		try
		{
		    byte bytes1[] = InetAddress.getByName(e1.m_remoteIpAddress).
			getAddress();
		    byte bytes2[] = InetAddress.getByName(e2.m_remoteIpAddress).
			getAddress();
		    int length = Math.max(bytes1.length, bytes2.length);

		    for(int i = 0; i < length; i++)
		    {
			byte b1 = (i >= length - bytes1.length) ?
			    bytes1[i - (length - bytes1.length)] : 0;
			byte b2 = (i >= length - bytes2.length) ?
			    bytes2[i - (length - bytes2.length)] : 0;

			if(b1 != b2)
			    return (0xff & b1) - (0xff & b2);
		    }
		}
		catch(Exception exception)
		{
		}

		int i = e1.m_remotePort.compareTo(e2.m_remotePort);

		if(i != 0)
		    return i;

		return e1.m_transport.compareTo(e2.m_transport);
	    }
	};
    private final static Comparator<ParticipantElement>
	s_readParticipantsComparator = new Comparator<ParticipantElement> ()
	{
	    @Override
	    public int compare(ParticipantElement e1, ParticipantElement e2)
	    {
		if(e1 == null || e2 == null)
		    return -1;

		int i = e1.m_name.compareTo(e2.m_name);

		if(i != 0)
		    return i;

		return e1.m_sipHashId.compareTo(e2.m_sipHashId);
	    }
	};
    private final static Comparator<SipHashIdElement>
	s_readSipHashIdsComparator = new Comparator<SipHashIdElement> ()
	{
	    @Override
	    public int compare(SipHashIdElement e1, SipHashIdElement e2)
	    {
		if(e1 == null || e2 == null)
		    return -1;

		/*
		** Sort by name and SipHash identity.
		*/

	    	int i = e1.m_name.compareTo(e2.m_name);

		if(i != 0)
		    return i;

		return e1.m_sipHashId.compareTo(e2.m_sipHashId);
	    }
	};
    private final static ReentrantReadWriteLock s_congestionControlMutex =
	new ReentrantReadWriteLock();
    private final static String DATABASE_NAME = "smoke.db";
    private final static int DATABASE_VERSION = 20211005;
    private final static long WRITE_PARTICIPANT_TIME_DELTA =
	60000L; // 60 seconds.
    private static Database s_instance = null;
    public enum ExceptionLevels
    {
	EXCEPTION_FATAL, EXCEPTION_NONE, EXCEPTION_PERMISSIBLE
    }
    public final static int SIPHASH_STREAM_CREATION_ITERATION_COUNT = 4096;
    public final static int MESSAGE_DELIVERY_ATTEMPTS = 10; // Must be > 0!

    private Database(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

	try
	{
	    m_db = getWritableDatabase();
	}
	catch(Exception exception)
	{
	    m_db = null;
	}
    }

    private void writeSteamImplementation
	(Cryptography cryptography, SteamElement steamElement)
    {
	if(cryptography == null || m_db == null || steamElement == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("absolute_filename",
		 cryptography.etmBase64String(steamElement.m_fileName));
	    values.put
		("destination",
		 cryptography.
		 etmBase64String(steamElement.m_destination.
				 getBytes(StandardCharsets.UTF_8)));
	    values.put
		("display_filename",
		 cryptography.
		 etmBase64String(steamElement.m_displayFileName.
				 getBytes(StandardCharsets.UTF_8)));
	    values.put
		("ephemeral_private_key",
		 cryptography.
		 etmBase64String(steamElement.m_ephemeralPrivateKey));
	    values.put
		("ephemeral_public_key",
		 cryptography.
		 etmBase64String(steamElement.m_ephemeralPublicKey));

	    if(steamElement.m_fileDigest == null)
	    {
		String fileName = steamElement.m_fileName;

		if(fileName.lastIndexOf('.') > 0)
		    fileName = fileName.substring(0, fileName.lastIndexOf('.'));

		values.put
		    ("file_digest",
		     cryptography.
		     etmBase64String(Cryptography.sha256FileDigest(fileName)));
	    }
	    else
		values.put
		    ("file_digest",
		     cryptography.etmBase64String(steamElement.m_fileDigest));

	    if(steamElement.m_fileIdentity == null)
	    {
		byte bytes[] = Cryptography.randomBytes
		    (Cryptography.STEAM_FILE_IDENTITY_LENGTH);

		values.put
		    ("file_identity", cryptography.etmBase64String(bytes));
		values.put
		    ("file_identity_digest",
		     Base64.encodeToString(cryptography.hmac(bytes),
					   Base64.DEFAULT));
	    }
	    else
	    {
		values.put
		    ("file_identity",
		     cryptography.etmBase64String(steamElement.m_fileIdentity));
		values.put
		    ("file_identity_digest",
		     Base64.encodeToString(cryptography.
					   hmac(steamElement.m_fileIdentity),
					   Base64.DEFAULT));
		}

	    values.put
		("file_size",
		 cryptography.etmBase64String(steamElement.m_fileSize));
	    values.put
		("is_download", String.valueOf(steamElement.m_direction));
	    values.put
		("key_type",
		 cryptography.etmBase64String(steamElement.m_keyType));
	    values.put
		("keystream",
		 cryptography.etmBase64String(steamElement.m_keyStream));
	    values.put
		("read_interval",
		 cryptography.etmBase64String(steamElement.m_readInterval));
	    values.put
		("read_offset",
		 cryptography.etmBase64String(steamElement.m_readOffset));
	    values.put("status", steamElement.m_status);

	    if(steamElement.m_transferRate.isEmpty())
		values.put
		    ("transfer_rate",
		     cryptography.etmBase64String(Miscellaneous.RATE));
	    else
		values.put
		    ("transfer_rate",
		     cryptography.etmBase64String(steamElement.m_transferRate));

	    m_db.insertOrThrow("steam_files", null, values);
	    m_db.setTransactionSuccessful();

	    Intent intent = new Intent("org.purple.smoke.steam_added");

	    intent.putExtra
		("org.purple.smoke.extra1", steamElement.m_destination);
	    intent.putExtra
		("org.purple.smoke.extra2", steamElement.m_displayFileName);
	    Miscellaneous.sendBroadcast(intent);
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public ArrayList<FireElement> readFires(Cryptography cryptography)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	ArrayList<FireElement> arrayList = null;

	try
	{
	    cursor = m_db.rawQuery("SELECT name, oid FROM fire", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    FireElement fireElement = new FireElement();
		    int count = cursor.getColumnCount();
		    int oid = cursor.getInt(count - 1);

		    for(int i = 0; i < count; i++)
		    {
			if(i == count - 1)
			{
			    fireElement.m_oid = oid;
			    continue;
			}

			byte bytes[] = cryptography.mtd
			    (Base64.decode(cursor.getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append("Database::readFires(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			}

			switch(i)
			{
			case 0:
			    if(bytes != null)
				fireElement.m_name = new String
				    (bytes, StandardCharsets.ISO_8859_1).trim();
			    else
				fireElement.m_name = "error (" + oid + ")";

			    break;
			default:
			    break;
			}
		    }

		    arrayList.add(fireElement);
		    cursor.moveToNext();
		}

		if(arrayList.size() > 1)
		    Collections.sort(arrayList, s_readFiresComparator);
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ArrayList<NeighborElement> readNeighborOids
	(Cryptography cryptography)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	ArrayList<NeighborElement> arrayList = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT passthrough, " +
		 "status_control, " +
		 "oid FROM neighbors", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    NeighborElement neighborElement = new NeighborElement();
		    boolean error = false;
		    int count = cursor.getColumnCount();

		    for(int i = 0; i < count; i++)
		    {
			if(i == count - 1)
			{
			    neighborElement.m_oid = cursor.getInt(i);
			    continue;
			}

			byte bytes[] = cryptography.mtd
			    (Base64.decode(cursor.getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    error = true;

			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append
				("Database::readNeighborOids(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			    break;
			}

			switch(i)
			{
			case 0:
			    neighborElement.m_passthrough = new String(bytes);
			    break;
			case 1:
			    neighborElement.m_statusControl = new String(bytes);
			    break;
			default:
			    break;
			}
		    }

		    if(!error)
			arrayList.add(neighborElement);

		    cursor.moveToNext();
		}
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ArrayList<NeighborElement> readNeighbors(Cryptography cryptography)
    {
	if(!State.getInstance().isAuthenticated())
	    return null;

	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	ArrayList<NeighborElement> arrayList = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "(SELECT COUNT(*) FROM outbound_queue o WHERE " +
		 "o.neighbor_oid = n.oid), " +
		 "n.bytes_read, " +
		 "n.bytes_written, " +
		 "n.echo_queue_size, " +
		 "n.ip_version, " +
		 "n.last_error, " +
		 "n.local_ip_address, " +
		 "n.local_port, " +
		 "n.non_tls, " +
		 "n.passthrough, " +
		 "n.proxy_ip_address, " +
		 "n.proxy_port, " +
		 "n.proxy_type, " +
		 "n.remote_certificate, " +
		 "n.remote_ip_address, " +
		 "n.remote_port, " +
		 "n.remote_scope_id, " +
		 "n.session_cipher, " +
		 "n.status, " +
		 "n.status_control, " +
		 "n.transport, " +
		 "n.uptime, " +
		 "n.oid " +
		 "FROM neighbors n ORDER BY n.oid", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    NeighborElement neighborElement = new NeighborElement();
		    int count = cursor.getColumnCount();
		    int oid = cursor.getInt(count - 1);

		    for(int i = 0; i < count; i++)
		    {
			if(i == count - 1)
			{
			    neighborElement.m_oid = oid;
			    continue;
			}

			byte bytes[] = null;

			if(i != 0)
			    bytes = cryptography.mtd
				(Base64.decode(cursor.getString(i).getBytes(),
					       Base64.DEFAULT));

			if(bytes == null && i != 0)
			{
			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append("Database::readNeighbors(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			}

			switch(i)
			{
			case 0:
			    neighborElement.m_outboundQueued =
				cursor.getInt(i);
			    break;
			case 1:
			    if(bytes != null)
				neighborElement.m_bytesRead = new String(bytes);
			    else
				neighborElement.m_bytesRead =
				    "error (" + oid + ")";

			    break;
			case 2:
			    if(bytes != null)
				neighborElement.m_bytesWritten =
				    new String(bytes);
			    else
				neighborElement.m_bytesWritten =
				    "error (" + oid + ")";

			    break;
			case 3:
			    if(bytes != null)
				neighborElement.m_echoQueueSize =
				    new String(bytes);
			    else
				neighborElement.m_echoQueueSize =
				    "error (" + oid + ")";

			    break;
			case 4:
			    if(bytes != null)
				neighborElement.m_ipVersion = new String(bytes);
			    else
				neighborElement.m_ipVersion =
				    "error (" + oid + ")";

			    break;
			case 5:
			    if(bytes != null)
				neighborElement.m_error = new String(bytes);
			    else
				neighborElement.m_error =
				    "error (" + oid + ")";

			    break;
			case 6:
			    if(bytes != null)
				neighborElement.m_localIpAddress =
				    new String(bytes);
			    else
				neighborElement.m_localIpAddress =
				    "error (" + oid + ")";

			    break;
			case 7:
			    if(bytes != null)
				neighborElement.m_localPort = new String(bytes);
			    else
				neighborElement.m_localPort =
				    "error (" + oid + ")";

			    break;
			case 8:
			    if(bytes != null)
				neighborElement.m_nonTls = new String(bytes);
			    else
				neighborElement.m_nonTls =
				    "error (" + oid + ")";

			    break;
			case 9:
			    if(bytes != null)
				neighborElement.m_passthrough =
				    new String(bytes);
			    else
				neighborElement.m_passthrough =
				    "error (" + oid + ")";

			    break;
			case 10:
			    if(bytes != null)
				neighborElement.m_proxyIpAddress =
				    new String(bytes);
			    else
				neighborElement.m_proxyIpAddress =
				    "error (" + oid + ")";

			    break;
			case 11:
			    if(bytes != null)
				neighborElement.m_proxyPort = new String(bytes);
			    else
				neighborElement.m_proxyPort =
				    "error (" + oid + ")";

			    break;
			case 12:
			    if(bytes != null)
				neighborElement.m_proxyType = new String(bytes);
			    else
				neighborElement.m_proxyType =
				    "error (" + oid + ")";

			    break;
			case 13:
			    neighborElement.m_remoteCertificate = bytes;
			    break;
			case 14:
			    if(bytes != null)
				neighborElement.m_remoteIpAddress =
				    new String(bytes);
			    else
				neighborElement.m_remoteIpAddress =
				    "error (" + oid + ")";

			    break;
			case 15:
			    if(bytes != null)
				neighborElement.m_remotePort =
				    new String(bytes);
			    else
				neighborElement.m_remotePort =
				    "error (" + oid + ")";

			    break;
			case 16:
			    if(bytes != null)
				neighborElement.m_remoteScopeId =
				    new String(bytes);
			    else
				neighborElement.m_remoteScopeId =
				    "error (" + oid + ")";

			    break;
			case 17:
			    if(bytes != null)
				neighborElement.m_sessionCipher =
				    new String(bytes);
			    else
				neighborElement.m_sessionCipher =
				    "error (" + oid + ")";

			    break;
			case 18:
			    if(bytes != null)
				neighborElement.m_status = new String(bytes);
			    else
				neighborElement.m_status =
				    "error (" + oid + ")";

			    break;
			case 19:
			    if(bytes != null)
				neighborElement.m_statusControl =
				    new String(bytes);
			    else
				neighborElement.m_statusControl =
				    "error (" + oid + ")";

			    break;
			case 20:
			    if(bytes != null)
				neighborElement.m_transport = new String(bytes);
			    else
				neighborElement.m_transport =
				    "error (" + oid + ")";

			    break;
			case 21:
			    if(bytes != null)
				neighborElement.m_uptime = new String(bytes);
			    else
				neighborElement.m_uptime =
				    "error (" + oid + ")";

			    break;
			default:
			    break;
			}
		    }

		    arrayList.add(neighborElement);
		    cursor.moveToNext();
		}

		if(arrayList.size() > 1)
		    Collections.sort(arrayList, s_readNeighborsComparator);
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ArrayList<ParticipantElement> readParticipants
	(Cryptography cryptography, String sipHashId)
    {
	if(!State.getInstance().isAuthenticated())
	    return null;

	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	ArrayList<ParticipantElement> arrayList = null;

	try
	{
	    if(sipHashId.isEmpty())
		cursor = m_db.rawQuery
		    ("SELECT " +
		     "(SELECT s.name FROM siphash_ids s " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) " +
		     "AS a, " +
		     "p.keystream, " +
		     "p.last_status_timestamp, " +
		     "p.siphash_id, " +
		     "p.oid " +
		     "FROM participants p", null);
	    else
		cursor = m_db.rawQuery
		    ("SELECT " +
		     "(SELECT s.name FROM siphash_ids s " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) " +
		     "AS a, " +
		     "p.keystream, " +
		     "p.last_status_timestamp, " +
		     "p.siphash_id, " +
		     "p.oid " +
		     "FROM participants p WHERE p.siphash_id_digest = ?",
		     new String[] {Base64.
				   encodeToString
				   (cryptography.
				    hmac(sipHashId.toUpperCase().trim().
					 getBytes(StandardCharsets.UTF_8)),
				    Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    ParticipantElement participantElement =
			new ParticipantElement();
		    int count = cursor.getColumnCount();
		    int oid = cursor.getInt(count - 1);

		    for(int i = 0; i < count; i++)
		    {
			if(i == count - 1)
			{
			    participantElement.m_oid = oid;
			    continue;
			}

			byte bytes[] = cryptography.mtd
			    (Base64.decode(cursor.getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append
				("Database::readParticipants(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			}

			switch(i)
			{
			case 0:
			    if(bytes != null)
				participantElement.m_name = new String(bytes);
			    else
				participantElement.m_name =
				    "error (" + oid + ")";

			    break;
			case 1:
			    participantElement.m_keyStream = bytes;
			    break;
			case 2:
			    if(bytes != null)
				participantElement.m_lastStatusTimestamp =
				    Miscellaneous.byteArrayToLong(bytes);

			    break;
			case 3:
			    if(bytes != null)
				participantElement.m_sipHashId = new String
				    (bytes, StandardCharsets.UTF_8);
			    else
				participantElement.m_sipHashId =
				    "error (" + oid + ")";

			    break;
			default:
			    break;
			}
		    }

		    arrayList.add(participantElement);
		    cursor.moveToNext();
		}

		if(arrayList.size() > 1)
		    Collections.sort(arrayList, s_readParticipantsComparator);
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ArrayList<SipHashIdElement> readNonSharedSipHashIds
	(Cryptography cryptography)
    {
	if(cryptography == null || m_db == null)
	    return null;

	ArrayList<SipHashIdElement> arrayList = null;
	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT siphash_id, stream " +
		 "FROM siphash_ids WHERE siphash_id_digest NOT IN " +
		 "(SELECT siphash_id_digest FROM participants)", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    SipHashIdElement sipHashIdElement = new SipHashIdElement();
		    boolean error = false;
		    int count = cursor.getColumnCount();

		    for(int i = 0; i < count; i++)
		    {
			byte bytes[] = cryptography.mtd
			    (Base64.decode(cursor.getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    error = true;

			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append
				("Database::readNonSharedSipHashIds(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			    break;
			}

			switch(i)
			{
			case 0:
			    sipHashIdElement.m_sipHashId = new String
				(bytes, StandardCharsets.UTF_8);
			    break;
			case 1:
			    sipHashIdElement.m_stream = bytes;
			    break;
			default:
			    break;
			}
		    }

		    if(!error)
			arrayList.add(sipHashIdElement);

		    cursor.moveToNext();
		}
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ArrayList<SipHashIdElement> readSipHashIds
	(Cryptography cryptography, String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return null;

	ArrayList<SipHashIdElement> arrayList = null;
	Cursor cursor = null;

	try
	{
	    if(sipHashId.isEmpty())
		cursor = m_db.rawQuery
		    ("SELECT " +
		     "(SELECT EXISTS(SELECT 1 FROM participants p WHERE " +
		     "p.siphash_id_digest = s.siphash_id_digest)) AS aa, " +
		     "(SELECT p.encryption_public_key_signed " +
		     "FROM participants p " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) AS " +
		     "bb, " +
		     "(SELECT p.signature_public_key_signed " +
		     "FROM participants p " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) AS " +
		     "cc, " +
		     "(SELECT COUNT(p.oid) FROM participants_keys p " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) AS " +
		     "dd, " +
		     "(SELECT p.encryption_public_key_algorithm FROM " +
		     "participants p WHERE p.siphash_id_digest = " +
		     "s.siphash_id_digest) AS ee, " +
		     "s.name, " +
		     "s.siphash_id, " +
		     "s.stream, " +
		     "s.oid " +
		     "FROM siphash_ids s ORDER BY s.oid", null);
	    else
		cursor = m_db.rawQuery
		    ("SELECT " +
		     "(SELECT EXISTS(SELECT 1 FROM participants p WHERE " +
		     "p.siphash_id_digest = s.siphash_id_digest)) AS aa, " +
		     "(SELECT p.encryption_public_key_signed " +
		     "FROM participants p " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) AS " +
		     "bb, " +
		     "(SELECT p.signature_public_key_signed " +
		     "FROM participants p " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) AS " +
		     "cc, " +
		     "(SELECT COUNT(p.oid) FROM participants_keys p " +
		     "WHERE p.siphash_id_digest = s.siphash_id_digest) AS " +
		     "dd, " +
		     "(SELECT p.encryption_public_key_algorithm FROM " +
		     "participants p WHERE p.siphash_id_digest = " +
		     "s.siphash_id_digest) AS ee, " +
		     "s.name, " +
		     "s.siphash_id, " +
		     "s.stream, " +
		     "s.oid " +
		     "FROM siphash_ids s WHERE s.siphash_id_digest = ?",
		     new String[] {Base64.
				   encodeToString
				   (cryptography.
				    hmac(sipHashId.toUpperCase().trim().
					 getBytes(StandardCharsets.UTF_8)),
				    Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    SipHashIdElement sipHashIdElement = new SipHashIdElement();
		    int count = cursor.getColumnCount();
		    int oid = cursor.getInt(count - 1);

		    for(int i = 0; i < count; i++)
		    {
			if(i == 0)
			{
			    sipHashIdElement.m_epksCompleted =
				cursor.getInt(i) > 0;
			    continue;
			}
			else if(i == 1 || i == 2)
			{
			    if(cursor.isNull(i))
				continue;
			}
			else if(i == 3)
			{
			    sipHashIdElement.m_fiascoKeys = cursor.getInt(i);
			    continue;
			}
			else if(i == 4)
			{
			    if(cursor.isNull(i))
				continue;
			}
			else if(i == count - 1)
			{
			    sipHashIdElement.m_oid = oid;
			    continue;
			}

			byte bytes[] = cryptography.mtd
			    (Base64.decode(cursor.getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append
				("Database::readSipHashIds(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			}

			switch(i)
			{
			case 1:
			    if(bytes != null)
				sipHashIdElement.m_keysSigned =
				    Arrays.equals(bytes, "true".getBytes());

			    break;
			case 2:
			    if(bytes != null)
				sipHashIdElement.m_keysSigned &=
				    Arrays.equals(bytes, "true".getBytes());

			    break;
			case 4:
			    if(bytes != null)
				sipHashIdElement.m_encryptionAlgorithm =
				    new String(bytes);
			    else
				sipHashIdElement.m_encryptionAlgorithm =
				    "error (" + oid + ")";

			    break;
			case 5:
			    if(bytes != null)
				sipHashIdElement.m_name = new String(bytes);
			    else
				sipHashIdElement.m_name =
				    "error (" + oid + ")";

			    break;
			case 6:
			    if(bytes != null)
				sipHashIdElement.m_sipHashId = new String
				    (bytes, StandardCharsets.UTF_8);
			    else
				sipHashIdElement.m_sipHashId =
				    "error (" + oid + ")";

			    break;
			case 7:
			    sipHashIdElement.m_stream = bytes;
			    break;
			default:
			    break;
			}
		    }

		    arrayList.add(sipHashIdElement);
		    cursor.moveToNext();
		}

		if(arrayList.size() > 1)
		    Collections.sort(arrayList, s_readSipHashIdsComparator);
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ArrayList<SteamElement> readSteams
	(Cryptography cryptography, short direction)
    {
	if(cryptography == null || m_db == null)
	    return null;

	ArrayList<SteamElement> arrayList = null;
	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT absolute_filename, " + // 0
		 "destination, " +              // 1
		 "display_filename, " +         // 2
		 "file_digest, " +              // 3
		 "file_identity, " +            // 4
		 "file_size, " +                // 5
		 "is_download, " +              // 6
		 "key_type, " +                 // 7
		 "read_interval, " +            // 8
		 "read_offset, " +              // 9
		 "status, " +                   // 10
		 "transfer_rate, " +            // 11
		 "oid " +                       // 12
		 "FROM steam_files WHERE is_download = ? " +
		 "ORDER BY someoid",
		 new String[] {String.valueOf(direction)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    String status = cursor.getString(10).trim();

		    if(status.equals("deleted"))
			continue;

		    SteamElement steamElement = new SteamElement();
		    int count = cursor.getColumnCount();
		    int oid = cursor.getInt(count - 1);

		    for(int i = 0; i < count; i++)
		    {
			if(i == 10)
			{
			    steamElement.m_status = status;
			    continue;
			}
			else if(i == count - 1)
			{
			    steamElement.m_oid = oid;
			    continue;
			}

			byte bytes[] = null;

			if(i == 6) // is_download
			    bytes = cursor.getString(i).getBytes();
			else
			    bytes = cryptography.mtd
				(Base64.decode(cursor.getString(i).getBytes(),
					       Base64.DEFAULT));

			if(bytes == null)
			{
			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append("Database::readSteams(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			}

			switch(i)
		        {
			case 0:
			    if(bytes != null)
				steamElement.m_fileName = new String(bytes);
			    else
				steamElement.m_fileName =
				    "error (" + oid + ")";

			    break;
			case 1:
			    if(bytes != null)
				steamElement.m_destination = new String
				    (bytes, StandardCharsets.UTF_8);
			    else
				steamElement.m_destination =
				    "error (" + oid + ")";

			    break;
			case 2:
			    if(bytes != null)
				steamElement.m_displayFileName = new String
				    (bytes, StandardCharsets.UTF_8);
			    else
				steamElement.m_displayFileName =
				    "error (" + oid + ")";

			    break;
			case 3:
			    steamElement.m_fileDigest = bytes;
			    break;
			case 4:
			    steamElement.m_fileIdentity = bytes;
			    break;
			case 5:
			    if(bytes != null)
				try
				{
				    steamElement.m_fileSize = Long.parseLong
					(new String(bytes));
				}
				catch(Exception exception)
				{
				}

			    break;
			case 6:
			    if(bytes != null)
				try
				{
				    steamElement.m_direction = Short.parseShort
					(new String(bytes));
				}
				catch(Exception exception)
				{
				}

			    break;
			case 7:
			    if(bytes != null)
				steamElement.m_keyType = new String(bytes);
			    else
				steamElement.m_keyType = "error (" + oid + ")";

			    break;
			case 8:
			    if(bytes != null)
				try
				{
				    steamElement.m_readInterval =
					Long.parseLong(new String(bytes));
				}
				catch(Exception exception)
				{
				}

			    break;
			case 9:
			    if(bytes != null)
				try
				{
				    steamElement.m_readOffset =
					Long.parseLong(new String(bytes));
				}
				catch(Exception exception)
				{
				}

			    break;
			case 10:
			    break;
			case 11:
			    if(bytes != null)
				steamElement.m_transferRate = new String(bytes);
			    else
				steamElement.m_transferRate =
				    "error (" + oid + ")";

			    break;
			default:
			    break;
			}
		    }

		    arrayList.add(steamElement);
		    cursor.moveToNext();
		}
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ArrayList<String> readSipHashIdStrings(Cryptography cryptography)
    {
	if(cryptography == null || m_db == null)
	    return null;

	ArrayList<String> arrayList = null;
	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT siphash_id FROM participants", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		int count = cursor.getColumnCount();

		while(!cursor.isAfterLast())
		{
		    for(int i = 0; i < count; i++)
		    {
			byte bytes[] = cryptography.mtd
			    (Base64.decode(cursor.getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append
				("Database::readSipHashIdStrings(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append(cursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			    break;
			}

			arrayList.add
			    (new String(bytes, StandardCharsets.UTF_8));
		    }

		    cursor.moveToNext();
		}
	    }
	}
	catch(Exception exception)
	{
	    if(arrayList != null)
		arrayList.clear();

	    arrayList = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return arrayList;
    }

    public ExceptionLevels writeParticipantMessage(Cryptography cryptography,
						   String fromSmokeStack,
						   String message,
						   String sipHashId,
						   byte attachment[],
						   byte messageIdentity[],
						   long timestamp)
    {
	if(cryptography == null || m_db == null)
	    return ExceptionLevels.EXCEPTION_FATAL;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    if(attachment == null || attachment.length <= 1)
		values.put
		    ("attachment",
		     Base64.encodeToString(cryptography.etm("".getBytes()),
					   Base64.DEFAULT));
	    else
		values.put
		    ("attachment",
		     Base64.encodeToString(cryptography.etm(attachment),
					   Base64.DEFAULT));

	    values.put
		("from_smokestack",
		 Base64.encodeToString(cryptography.etm(fromSmokeStack.
							getBytes()),
				       Base64.DEFAULT));
	    values.put
		("message",
		 Base64.encodeToString(cryptography.etm(message.getBytes()),
				       Base64.DEFAULT));
	    values.put
		("message_digest",
		 Base64.encodeToString(cryptography.
				       /*
				       ** It's very possible that a message
				       ** sent by one device will be identical
				       ** to the message sent by another
				       ** device.
				       */
				       hmac((message +
					     sipHashId +
					     timestamp).getBytes()),
				       Base64.DEFAULT));

	    if(messageIdentity == null)
		values.put
		    ("message_identity_digest",
		     Base64.encodeToString(Cryptography.randomBytes(64),
					   Base64.DEFAULT));
	    else
		values.put
		    ("message_identity_digest",
		     Base64.encodeToString(cryptography.hmac(messageIdentity),
					   Base64.DEFAULT));

	    values.put
		("message_read",
		 Base64.encodeToString(cryptography.etm("false".getBytes()),
				       Base64.DEFAULT));
	    values.put
		("message_sent",
		 Base64.encodeToString(cryptography.etm("false".getBytes()),
				       Base64.DEFAULT));
	    values.put
		("siphash_id_digest",
		 Base64.encodeToString(cryptography.
				       hmac(sipHashId.toUpperCase().trim().
					    getBytes(StandardCharsets.UTF_8)),
				       Base64.DEFAULT));

	    /*
	    ** We want to preserve the order of the time values.
	    ** That is, if t_a < t_b, then E(t_a) < E(t_b) must
	    ** also be true. Or, H(t_a) < H(t_b). A comment, purely.
	    */

	    values.put("timestamp", timestamp);

	    if(m_db.insertOrThrow("participants_messages", null, values) != -1)
		synchronized(m_readMemberChatCursorMutex)
		{
		    if(m_readMemberChatSipHashId.equals(sipHashId))
		    {
			if(m_readMemberChatCursor != null)
			    m_readMemberChatCursor.close();

			m_readMemberChatCursor = null;
		    }
		}

	    m_db.setTransactionSuccessful();
	}
	catch(SQLiteConstraintException exception)
	{
	    if(exception.getMessage().toLowerCase().contains("unique"))
		return ExceptionLevels.EXCEPTION_PERMISSIBLE;
	    else
		return ExceptionLevels.EXCEPTION_FATAL;
	}
	catch(Exception exception)
        {
	    return ExceptionLevels.EXCEPTION_FATAL;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return ExceptionLevels.EXCEPTION_NONE;
    }

    public MemberChatElement readMemberChat
	(Cryptography cryptography, String sipHashId, int position)
    {
	if(cryptography == null || m_db == null)
	    return null;

	MemberChatElement memberChatElement = null;

	synchronized(m_readMemberChatCursorMutex)
	{
	    try
	    {
		if(m_readMemberChatCursor != null)
		    if(!m_readMemberChatSipHashId.equals(sipHashId))
		    {
			m_readMemberChatCursor.close();
			m_readMemberChatCursor = null;
		    }

		if(m_readMemberChatCursor == null)
		{
		    m_readMemberChatCursor = m_db.rawQuery
			("SELECT attachment, " + // 0
			 "from_smokestack, " +   // 1
			 "message, " +           // 2
			 "message_read, " +      // 3
			 "message_sent, " +      // 4
			 "timestamp, " +         // 5
			 "oid " +                // 6
			 "FROM participants_messages " +
			 "WHERE siphash_id_digest = ? ORDER BY timestamp",
			 new String[] {Base64.
				       encodeToString
				       (cryptography.
					hmac(sipHashId.toUpperCase().trim().
					     getBytes(StandardCharsets.UTF_8)),
					Base64.DEFAULT)});

		    if(m_readMemberChatCursor != null)
			m_readMemberChatSipHashId = sipHashId;
		}

		if(m_readMemberChatCursor != null &&
		   m_readMemberChatCursor.moveToPosition(position))
		{
		    memberChatElement = new MemberChatElement();

		    int count = m_readMemberChatCursor.getColumnCount();
		    int oid = m_readMemberChatCursor.getInt(count - 1);

		    for(int i = 0; i < count; i++)
		    {
			if(i == count - 1)
			{
			    memberChatElement.m_oid = oid;
			    continue;
			}
			else if(i == 5)
			{
			    memberChatElement.m_timestamp =
				m_readMemberChatCursor.getLong(i);
			    continue;
			}

			byte bytes[] = cryptography.mtd
			    (Base64.decode(m_readMemberChatCursor.
					   getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    StringBuilder stringBuilder = new StringBuilder();

			    stringBuilder.append
				("Database::readMemberChat(): ");
			    stringBuilder.append("error on column ");
			    stringBuilder.append
				(m_readMemberChatCursor.getColumnName(i));
			    stringBuilder.append(".");
			    writeLog(stringBuilder.toString());
			}

			switch(i)
			{
			case 0:
			    memberChatElement.m_attachment = bytes;
			    break;
			case 1:
			    if(bytes != null)
				memberChatElement.m_fromSmokeStack =
				    new String(bytes).trim();
			    else
				memberChatElement.m_fromSmokeStack =
				    "error (" + oid + ")";

			    break;
			case 2:
			    if(bytes != null)
				memberChatElement.m_message =
				    new String(bytes);
			    else
				memberChatElement.m_message =
				    "error (" + oid + ")";

			    break;
			case 3:
			    if(bytes != null)
				memberChatElement.m_messageRead =
				    (new String(bytes).equals("true"));

			    break;
			case 4:
			    if(bytes != null)
				memberChatElement.m_messageSent =
				    (new String(bytes).equals("true"));

			    break;
			default:
			    break;
			}
		    }
		}
	    }
	    catch(Exception exception)
	    {
		memberChatElement = null;

		if(m_readMemberChatCursor != null)
		    m_readMemberChatCursor.close();

		m_readMemberChatCursor = null;
		m_readMemberChatSipHashId = "";
	    }
	}

	return memberChatElement;
    }

    public PublicKey publicEncryptionKeyForSipHashId(Cryptography cryptography,
						     String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	PublicKey publicKey = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT encryption_public_key " +
		 "FROM participants WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    publicKey = Cryptography.publicKeyFromBytes(bytes);
	    }
	}
	catch(Exception exception)
	{
	    publicKey = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return publicKey;
    }

    public PublicKey publicSignatureKeyForSipHashId(Cryptography cryptography,
						    String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	PublicKey publicKey = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT signature_public_key " +
		 "FROM participants WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    publicKey = Cryptography.publicKeyFromBytes(bytes);
	    }
	}
	catch(Exception exception)
	{
	    publicKey = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return publicKey;
    }

    public PublicKey signatureKeyForDigest(Cryptography cryptography,
					   byte digest[])
    {
	if(cryptography == null || digest == null || m_db == null)
	    return null;

	Cursor cursor = null;
	PublicKey publicKey = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT signature_public_key " +
		 "FROM participants WHERE encryption_public_key_digest = ?",
		 new String[] {Base64.encodeToString(digest, Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		{
		    int length = bytes.length;

		    if(length < 200)
			publicKey = KeyFactory.getInstance("EC").
			    generatePublic(new X509EncodedKeySpec(bytes));
		    else if(length < 600)
			publicKey = KeyFactory.getInstance("RSA").
			    generatePublic(new X509EncodedKeySpec(bytes));
		    else if(length < 1200)
			publicKey = KeyFactory.getInstance
			    ("SPHINCS256",
			     BouncyCastlePQCProvider.PROVIDER_NAME).
			    generatePublic(new X509EncodedKeySpec(bytes));
		    else
			publicKey = KeyFactory.getInstance
			    ("Rainbow", BouncyCastlePQCProvider.PROVIDER_NAME).
			    generatePublic(new X509EncodedKeySpec(bytes));
		}
	    }
	}
	catch(Exception exception)
	{
	    publicKey = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return publicKey;
    }

    public SipHashIdElement readSipHashId(Cryptography cryptography,
					  String oid)
    {
	if(cryptography == null || m_db == null)
	    return null;

	SipHashIdElement sipHashIdElement = null;
	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "(SELECT p.encryption_public_key FROM participants p " + // 0
		 "WHERE p.siphash_id_digest = s.siphash_id_digest) AS a, " +
		 "(SELECT p.signature_public_key FROM participants p " +  // 1
		 "WHERE p.siphash_id_digest = s.siphash_id_digest) AS b, " +
		 "(SELECT p.encryption_public_key_algorithm FROM " +      // 2
		 "participants p WHERE p.siphash_id_digest = " +
		 "s.siphash_id_digest) AS c, " +
		 "s.siphash_id, " +                                       // 3
		 "s.name, " +                                             // 4
		 "s.stream, " +                                           // 5
		 "s.oid " +
		 "FROM siphash_ids s WHERE s.oid = ? ORDER BY s.oid",
		 new String[] {oid});

	    if(cursor != null && cursor.moveToFirst())
	    {
		sipHashIdElement = new SipHashIdElement();

		boolean error = false;
		int count = cursor.getColumnCount();

		for(int i = 0; i < count; i++)
		{
		    if(i == 2)
		    {
			if(cursor.isNull(i))
			    continue;
		    }
		    else if(i == count - 1)
		    {
			sipHashIdElement.m_oid = cursor.getInt(i);
			continue;
		    }

		    byte bytes[] = cryptography.mtd
			(Base64.decode(cursor.getString(i).getBytes(),
				       Base64.DEFAULT));

		    if(bytes == null)
		    {
			error = true;

			StringBuilder stringBuilder = new StringBuilder();

			stringBuilder.append
			    ("Database::readSipHashId(): ");
			stringBuilder.append("error on column ");
			stringBuilder.append(cursor.getColumnName(i));
			stringBuilder.append(".");
			writeLog(stringBuilder.toString());
			break;
		    }

		    switch(i)
		    {
		    case 0:
			sipHashIdElement.m_encryptionPublicKey = bytes;
			break;
		    case 1:
			sipHashIdElement.m_signaturePublicKey = bytes;
			break;
		    case 2:
			sipHashIdElement.m_encryptionAlgorithm =
			    new String(bytes);
			break;
		    case 3:
			sipHashIdElement.m_sipHashId = new String
			    (bytes, StandardCharsets.UTF_8);
			break;
		    case 4:
			sipHashIdElement.m_name = new String
			    (bytes, StandardCharsets.UTF_8);
			break;
		    case 5:
			sipHashIdElement.m_stream = bytes;
			break;
		    default:
			break;
		    }
		}

		if(error)
		    sipHashIdElement = null;
	    }
	}
	catch(Exception exception)
	{
	    sipHashIdElement = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return sipHashIdElement;
    }

    public SteamElement readSteam
	(Cryptography cryptography, int position, int someOid)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	SteamElement steamElement = null;

	try
	{
	    if(position >= 0)
	    {
		cursor = m_db.rawQuery
		    ("SELECT absolute_filename, " + // 0
		     "destination, " +              // 1
		     "display_filename, " +         // 2
		     "ephemeral_private_key, " +    // 3
		     "ephemeral_public_key, " +     // 4
		     "file_digest, " +              // 5
		     "file_identity, " +            // 6
		     "file_size, " +                // 7
		     "is_download, " +              // 8
		     "key_type, " +                 // 9
		     "keystream, " +                // 10
		     "read_interval, " +            // 11
		     "read_offset, " +              // 12
		     "someoid, " +                  // 13
		     "status, " +                   // 14
		     "transfer_rate, " +            // 15
		     "oid " +                       // 16
		     "FROM steam_files ORDER BY someoid", null);

		if(cursor == null || !cursor.moveToPosition(position))
		    return steamElement;
	    }
	    else
	    {
		cursor = m_db.rawQuery
		    ("SELECT absolute_filename, " + // 0
		     "destination, " +              // 1
		     "display_filename, " +         // 2
		     "ephemeral_private_key, " +    // 3
		     "ephemeral_public_key, " +     // 4
		     "file_digest, " +              // 5
		     "file_identity, " +            // 6
		     "file_size, " +                // 7
		     "is_download, " +              // 8
		     "key_type, " +                 // 9
		     "keystream, " +                // 10
		     "read_interval, " +            // 11
		     "read_offset, " +              // 12
		     "someoid, " +                  // 13
		     "status, " +                   // 14
		     "transfer_rate, " +            // 15
		     "oid " +                       // 16
		     "FROM steam_files WHERE someoid > CAST(? AS INTEGER) " +
		     "ORDER BY someoid LIMIT 1",
		     new String[] {String.valueOf(someOid)});

		if(cursor == null || !cursor.moveToFirst())
		    return steamElement;
	    }

	    steamElement = new SteamElement();

	    int count = cursor.getColumnCount();
	    int oid = cursor.getInt(count - 1);

	    for(int i = 0; i < count; i++)
	    {
		if(i == 13)
		{
		    steamElement.m_someOid = cursor.getInt(i);
		    continue;
		}
		else if(i == 14)
		{
		    steamElement.m_status = cursor.getString(i).trim();
		    continue;
		}
		else if(i == count - 1)
		{
		    steamElement.m_oid = oid;
		    continue;
		}

		byte bytes[] = null;

		if(i == 8) // is_download
		    bytes = cursor.getString(i).getBytes();
		else
		    bytes = cryptography.mtd
			(Base64.decode(cursor.getString(i).getBytes(),
				       Base64.DEFAULT));

		if(bytes == null)
		{
		    StringBuilder stringBuilder = new StringBuilder();

		    stringBuilder.append("Database::readSteam(): ");
		    stringBuilder.append("error on column ");
		    stringBuilder.append(cursor.getColumnName(i));
		    stringBuilder.append(".");
		    writeLog(stringBuilder.toString());
		}

		switch(i)
		{
		case 0:
		    if(bytes != null)
			steamElement.m_fileName = new String(bytes);
		    else
			steamElement.m_fileName = "error (" + oid + ")";

		    break;
		case 1:
		    if(bytes != null)
			steamElement.m_destination = new String
			    (bytes, StandardCharsets.UTF_8);
		    else
			steamElement.m_destination = "error (" + oid + ")";

		    break;
		case 2:
		    if(bytes != null)
			steamElement.m_displayFileName = new String
			    (bytes, StandardCharsets.UTF_8);
		    else
			steamElement.m_displayFileName = "error (" + oid + ")";

		    break;
		case 3:
		    steamElement.m_ephemeralPrivateKey = bytes;
		    break;
		case 4:
		    steamElement.m_ephemeralPublicKey = bytes;
		    break;
		case 5:
		    steamElement.m_fileDigest = bytes;
		    break;
		case 6:
		    steamElement.m_fileIdentity = bytes;
		    break;
		case 7:
		    if(bytes != null)
			try
			{
			    steamElement.m_fileSize = Long.parseLong
				(new String(bytes));
			}
			catch(Exception exception)
			{
			}

		    break;
		case 8:
		    if(bytes != null)
			try
			{
			    steamElement.m_direction = Short.parseShort
				(new String(bytes));
			}
			catch(Exception exception)
			{
			}

		    break;
		case 9:
		    if(bytes != null)
			steamElement.m_keyType = new String(bytes);
		    else
			steamElement.m_keyType = "error (" + oid + ")";

		    break;
		case 10:
		    steamElement.m_keyStream = bytes;
		    break;
		case 11:
		    if(bytes != null)
			try
			{
			    steamElement.m_readInterval = Long.parseLong
				(new String(bytes));
			}
			catch(Exception exception)
			{
			}

		    break;
		case 12:
		    if(bytes != null)
			try
			{
			    steamElement.m_readOffset = Long.parseLong
				(new String(bytes));
			}
			catch(Exception exception)
			{
			}

		    break;
		case 13:
		    break;
		case 14:
		    break;
		case 15:
		    if(bytes != null)
			steamElement.m_transferRate = new String(bytes);
		    else
			steamElement.m_transferRate = "error (" + oid + ")";

		    break;
		default:
		    break;
		}
	    }
	}
	catch(Exception exception)
	{
	    steamElement = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return steamElement;
    }

    public String messageDetails(int oid)
    {
	if(m_db == null)
	    return "";

	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT LENGTH(attachment) + " +
		 "LENGTH(from_smokestack) + " +
		 "LENGTH(message) + " +
		 "LENGTH(message_digest) + " +
		 "LENGTH(message_identity_digest) + " +
		 "LENGTH(message_read) + " +
		 "LENGTH(message_sent) + " +
		 "LENGTH(siphash_id_digest) + " +
		 "LENGTH(timestamp) " +
		 "FROM participants_messages WHERE oid = ?",
		 new String[] {String.valueOf(oid)});

	    if(cursor != null && cursor.moveToFirst())
		return "Disk Size: " + cursor.getLong(0) + " Bytes";
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return "";
    }

    public String nameFromSipHashId(Cryptography cryptography,
				    String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return "";

	Cursor cursor = null;
	String name = "";

	try
	{
	    cursor = m_db.rawQuery
		("SELECT name FROM siphash_ids WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    name = new String(bytes);
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return name;
    }

    public String publicKeyEncryptionAlgorithm(Cryptography cryptography,
					       String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT encryption_public_key_algorithm " +
		 "FROM participants WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    return new String(bytes);
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return "";
    }

    public String readNeighborStatusControl(Cryptography cryptography, int oid)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	String status = "";

	try
	{
	    cursor = m_db.rawQuery
		("SELECT status_control FROM neighbors WHERE oid = ?",
		 new String[] {String.valueOf(oid)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    status = new String(bytes);
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return status;
    }

    public String readParticipantOptions(Cryptography cryptography,
					 String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return "";

	Cursor cursor = null;
	String string = "";

	try
	{
	    cursor = m_db.rawQuery
		("SELECT options " +
		 "FROM participants WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    string = new String(bytes);
	    }
	}
	catch(Exception exception)
	{
	    string = "";
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return string;
    }

    public String readSetting(Cryptography cryptography, String name)
    {
	if(m_db == null)
	    return "";

	Cursor cursor = null;
	String str = "";

	try
	{
	    if(cryptography == null)
		cursor = m_db.rawQuery
		    ("SELECT value FROM settings WHERE name = ?",
		     new String[] {name});
	    else
	    {
		byte bytes[] = cryptography.hmac(name.getBytes());

		if(bytes != null)
		    cursor = m_db.rawQuery
			("SELECT value FROM settings WHERE name_digest = ?",
			 new String[] {Base64.encodeToString(bytes,
							     Base64.DEFAULT)});
		else
		    str = "An error occurred (hmac() failure).";
	    }

	    if(cursor != null && cursor.moveToFirst())
		if(cryptography == null)
		    str = cursor.getString(0);
		else
		{
		    byte bytes[] = cryptography.mtd
			(Base64.decode(cursor.getString(0).getBytes(),
				       Base64.DEFAULT));

		    if(bytes != null)
			str = new String(bytes);
		    else
			str = "An error occurred (mtd() failure).";
		}
	}
	catch(Exception exception)
	{
	    str = "An exception was thrown (" +
		exception.getMessage().toLowerCase() +
		").";
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	/*
	** Default values.
	*/

	if(name.equals("show_chat_icons") && str.isEmpty())
	    return "true";

	return str;
    }

    public String readSipHashIdString(Cryptography cryptography, String oid)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT siphash_id FROM siphash_ids WHERE oid = ?",
		 new String[] {oid});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    return new String(bytes, StandardCharsets.UTF_8);
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return "";
    }

    public String steamSipHashId(Cryptography cryptography,
				 byte fileIdentity[])
    {
	if(cryptography == null || fileIdentity == null || m_db == null)
	    return "";

	Cursor cursor = null;
	String sipHashId = "";

	try
	{
	    cursor = m_db.rawQuery
		("SELECT destination FROM steam_files " +
		 "WHERE file_identity_digest = ?",
		 new String[] {Base64.encodeToString(cryptography.
						     hmac(fileIdentity),
						     Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		sipHashId = new String
		    (cryptography.
		     mtd(Base64.decode(cursor.getString(0).getBytes(),
				       Base64.DEFAULT)),
		     StandardCharsets.UTF_8);
		sipHashId = Miscellaneous.sipHashIdFromDestination(sipHashId);
	    }
	}
	catch(Exception exception)
	{
	    sipHashId = "";
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return sipHashId;
    }

    public String steamStatus(int oid)
    {
	if(m_db == null)
	    return "";

	Cursor cursor = null;
	String status = "";

	try
	{
	    cursor = m_db.rawQuery
		("SELECT status FROM steam_files WHERE oid = ?",
		 new String[] {String.valueOf(oid)});

	    if(cursor != null && cursor.moveToFirst())
		status = cursor.getString(0).trim();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return status;
    }

    public String writeParticipant(Cryptography cryptography, byte data[])
    {
	if(cryptography == null || data == null || m_db == null)
	    return "";

	ContentValues values = null;
	Cursor cursor = null;
	String sipHashId = "";
	boolean exists = false;

	try
	{
	    String strings[] = new String(data).split("\\n");

	    if(strings.length != Messages.EPKS_GROUP_ONE_ELEMENT_COUNT)
		return "";

	    PublicKey encryptionKey = null;
	    PublicKey signatureKey = null;
	    String encryptionKeyAlgorithm = "";
	    boolean encryptionKeySigned = false;
	    boolean signatureKeySigned = false;
	    byte keyType[] = null;
	    byte encryptionKeySignature[] = null;
	    byte signatureKeySignature[] = null;
	    byte sipHashIdBytes[] = null;
	    int ii = 0;

	    for(String string : strings)
		switch(ii)
		{
		case 0:
		    long timestamp = Miscellaneous.byteArrayToLong
			(Base64.decode(string.getBytes(), Base64.NO_WRAP));

		    if(Math.abs(System.currentTimeMillis() - timestamp) >
		       WRITE_PARTICIPANT_TIME_DELTA)
			return "";

		    ii += 1;
		    break;
		case 1:
		    keyType = Base64.decode
			(string.getBytes(), Base64.NO_WRAP);

		    if(keyType == null ||
		       keyType.length != 1 ||
		       keyType[0] != Messages.CHAT_KEY_TYPE[0])
			return "";

		    ii += 1;
		    break;
		case 2:
		    sipHashId = new String
			(Base64.decode(string.getBytes(), Base64.NO_WRAP),
			 StandardCharsets.UTF_8);

		    if(sipHashId == null ||
		       sipHashId.length() !=
		       Cryptography.SIPHASH_IDENTITY_LENGTH)
			return "";
		    else
			sipHashIdBytes = sipHashId.getBytes
			    (StandardCharsets.UTF_8);

		    ii += 1;
		    break;
		case 3:
		    cursor = m_db.rawQuery
			("SELECT EXISTS(SELECT 1 " +
			 "FROM participants WHERE " +
			 "encryption_public_key_digest = ?)",
			 new String[] {Base64.
				       encodeToString(Cryptography.
						      sha512(Base64.
							     decode(string.
								    getBytes(),
								    Base64.
								    NO_WRAP)),
						      Base64.DEFAULT)});

		    if(cursor != null && cursor.moveToFirst())
			if(cursor.getInt(0) == 1)
			    exists = true;

		    if(cursor != null)
		    {
			cursor.close();
			cursor = null;
		    }

		    encryptionKey = Cryptography.publicKeyFromBytes
			(Base64.decode(string.getBytes(), Base64.NO_WRAP));

		    if(encryptionKey == null)
			return "";
		    else if(cryptography.
			    compareChatEncryptionPublicKey(encryptionKey))
			return "";
		    else if(cryptography.
			    compareChatSignaturePublicKey(encryptionKey))
			return "";

		    ii += 1;
		    break;
		case 4:
		    encryptionKeySignature = Base64.decode
			(string.getBytes(), Base64.NO_WRAP);
		    ii += 1;
		    break;
		case 5:
		    cursor = m_db.rawQuery
			("SELECT EXISTS(SELECT 1 " +
			 "FROM participants WHERE " +
			 "signature_public_key_digest = ?)",
			 new String[] {Base64.
				       encodeToString(Cryptography.
						      sha512(Base64.
							     decode(string.
								    getBytes(),
								    Base64.
								    NO_WRAP)),
						      Base64.DEFAULT)});

		    if(cursor != null && cursor.moveToFirst())
			if(cursor.getInt(0) == 1)
			    if(exists)
				return "";

		    if(cursor != null)
		    {
			cursor.close();
			cursor = null;
		    }

		    signatureKey = Cryptography.publicKeyFromBytes
			(Base64.decode(string.getBytes(), Base64.NO_WRAP));

		    if(signatureKey == null)
			return "";
		    else if(cryptography.
			    compareChatEncryptionPublicKey(signatureKey))
			return "";
		    else if(cryptography.
			    compareChatSignaturePublicKey(signatureKey))
			return "";

		    ii += 1;
		    break;
		case 6:
		    signatureKeySignature = Base64.decode
			(string.getBytes(), Base64.NO_WRAP);

		    if(!encryptionKey.getAlgorithm().equals("McEliece-CCA2"))
		    {
			if(Cryptography.
			   verifySignature(encryptionKey,
					   encryptionKeySignature,
					   Miscellaneous.
					   joinByteArrays(sipHashIdBytes,
							  encryptionKey.
							  getEncoded(),
							  signatureKey.
							  getEncoded())))
			    encryptionKeySigned = true;
		    }
		    else
		    {
			switch(encryptionKeySignature[0])
			{
			case Messages.MCELIECE_FUJISAKI_11_50:
			    encryptionKeyAlgorithm =
				"McEliece-Fujisaki (11, 50)";
			    break;
			case Messages.MCELIECE_FUJISAKI_12_68:
			    encryptionKeyAlgorithm =
				"McEliece-Fujisaki (12, 68)";
			    break;
			case Messages.MCELIECE_FUJISAKI_13_118:
			    encryptionKeyAlgorithm =
				"McEliece-Fujisaki (13, 118)";
			    break;
			case Messages.MCELIECE_POINTCHEVAL:
			    encryptionKeyAlgorithm =
				"McEliece-Pointcheval (11, 50)";
			    break;
			}
		    }

		    if(Cryptography.
		       verifySignature(signatureKey,
				       signatureKeySignature,
				       Miscellaneous.
				       joinByteArrays(sipHashIdBytes,
						      encryptionKey.
						      getEncoded(),
						      signatureKey.
						      getEncoded())))
			signatureKeySigned = true;

		    break;
		default:
		    break;
		}

	    if(nameFromSipHashId(cryptography, sipHashId).isEmpty())
		return "";

	    values = new ContentValues();

	    SparseArray<String> sparseArray = new SparseArray<> ();

	    sparseArray.append(0, "encryption_public_key");
	    sparseArray.append(1, "encryption_public_key_algorithm");
	    sparseArray.append(2, "encryption_public_key_digest");
	    sparseArray.append(3, "encryption_public_key_signed");
	    sparseArray.append(4, "identity");
	    sparseArray.append(5, "keystream");
	    sparseArray.append(6, "last_status_timestamp");
	    sparseArray.append(7, "options");
	    sparseArray.append(8, "signature_public_key");
	    sparseArray.append(9, "signature_public_key_digest");
	    sparseArray.append(10, "signature_public_key_signed");
	    sparseArray.append(11, "siphash_id");
	    sparseArray.append(12, "siphash_id_digest");

	    int size = sparseArray.size();

	    for(int i = 0; i < size; i++)
	    {
		byte bytes[] = null;

		switch(sparseArray.get(i))
		{
		case "encryption_public_key":
		    bytes = cryptography.etm(encryptionKey.getEncoded());
		    break;
		case "encryption_public_key_algorithm":
		    bytes = cryptography.etm
			(encryptionKeyAlgorithm.getBytes());
		    break;
		case "encryption_public_key_digest":
		    bytes = Cryptography.sha512(encryptionKey.getEncoded());
		    break;
		case "encryption_public_key_signed":
		    bytes = cryptography.etm
			((encryptionKeySigned ? "true" : "false").getBytes());
		    break;
		case "identity":
		    bytes = cryptography.etm("".getBytes());
		    break;
		case "keystream":
		    /*
		    ** Create an initial pairing.
		    */

		    try
		    {
			byte salt[] = null;

			salt = Cryptography.xor
			    (cryptography.chatEncryptionPublicKey().
			     getEncoded(),
			     cryptography.chatSignaturePublicKey().
			     getEncoded(),
			     encryptionKey.getEncoded(),
			     signatureKey.getEncoded());
			bytes = Cryptography.pbkdf2
			    (salt,
			     Miscellaneous.
			     byteArrayAsHexString(Cryptography.sha512(salt)).
			     toCharArray(),
			     Cryptography.KEY_EXCHANGE_INITIAL_PBKDF2_ITERATION,
			     8 * Cryptography.CIPHER_HASH_KEYS_LENGTH);
			bytes = cryptography.etm(bytes);
		    }
		    catch(Exception exception)
		    {
			bytes = cryptography.etm("".getBytes());
		    }

		    break;
		case "last_status_timestamp":
		    bytes = cryptography.etm("".getBytes());
		    break;
		case "options":
		    bytes = cryptography.etm
			(("optional_signatures = false;" +
			  "optional_steam = false").getBytes());
		    break;
		case "signature_public_key":
		    bytes = cryptography.etm(signatureKey.getEncoded());
		    break;
		case "signature_public_key_digest":
		    bytes = Cryptography.sha512(signatureKey.getEncoded());
		    break;
		case "signature_public_key_signed":
		    bytes = cryptography.etm
			((signatureKeySigned ? "true" : "false").getBytes());
		    break;
		case "siphash_id":
		    bytes = cryptography.etm
			(sipHashId.getBytes(StandardCharsets.UTF_8));
		    break;
		case "siphash_id_digest":
		    bytes = cryptography.hmac
			(sipHashId.getBytes(StandardCharsets.UTF_8));
		    break;
		default:
		    break;
		}

		if(bytes == null)
		{
		    sparseArray.clear();
		    return "";
		}

		values.put(sparseArray.get(i),
			   Base64.encodeToString(bytes, Base64.DEFAULT));
	    }

	    sparseArray.clear();
	}
	catch(Exception exception)
	{
	    return "";
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	if(values == null)
	    return "";

	m_db.beginTransactionNonExclusive();

	try
	{
	    if(m_db.replace("participants", null, values) <= 0)
		sipHashId = "";

	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return "";
	}
	finally
	{
	    m_db.endTransaction();
	}

	return sipHashId;
    }

    public String[] keysSigned(Cryptography cryptography, String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	String strings[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT encryption_public_key_signed, " +
		 "signature_public_key_signed " +
		 "FROM participants WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		strings = new String[] {"false", "false"};

		for(int i = 0; i < 2; i++)
		{
		    byte bytes[] = cryptography.mtd
			(Base64.decode(cursor.getString(i).getBytes(),
				       Base64.DEFAULT));

		    if(bytes == null)
		    {
			StringBuilder stringBuilder = new StringBuilder();

			stringBuilder.append("Database::keysSigned(): ");
			stringBuilder.append("error on column ");
			stringBuilder.append(cursor.getColumnName(i));
			stringBuilder.append(".");
			writeLog(stringBuilder.toString());
		    }
		    else
			strings[i] = new String(bytes);
		}
	    }
	}
	catch(Exception exception)
	{
	    strings = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return strings;
    }

    public String[] nameSipHashIdFromDigest(Cryptography cryptography,
					    byte digest[])
    {
	if(cryptography == null || digest == null || m_db == null)
	    return null;

	Cursor cursor = null;
	String array[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT name, siphash_id " +
		 "FROM siphash_ids WHERE siphash_id_digest = " +
		 "(SELECT siphash_id_digest FROM participants " +
		 "WHERE encryption_public_key_digest = ?)",
		 new String[] {Base64.encodeToString(digest, Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		{
		    array = new String[2];
		    array[0] = new String(bytes);
		    bytes = cryptography.mtd
			(Base64.decode(cursor.getString(1).getBytes(),
				       Base64.DEFAULT));

		    if(bytes != null)
			array[1] = new String(bytes, StandardCharsets.UTF_8);
		    else
			array = null;
		}
	    }
	}
	catch(Exception exception)
	{
	    array = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return array;
    }

    public String[] readOutboundMessage(int messageOid, int neighborOid)
    {
	if(m_db == null)
	    return null;

	Cursor cursor = null;
	String array[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT attempts, message, message_identity_digest, oid " +
		 "FROM outbound_queue " +
		 "WHERE attempts < CAST(? AS INTEGER) AND " +
		 "neighbor_oid = ? AND " +
		 "(CAST(? AS INTEGER) - timestamp) >= CAST(? AS INTEGER) AND " +
		 "oid > CAST(? AS INTEGER) " +
		 "ORDER BY oid LIMIT 1",
		 new String[] {String.valueOf(MESSAGE_DELIVERY_ATTEMPTS),
			       String.valueOf(neighborOid),
			       String.valueOf(System.currentTimeMillis()),
			       String.valueOf(Chat.CHAT_WINDOW / (long)
					      MESSAGE_DELIVERY_ATTEMPTS),
			       String.valueOf(messageOid)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		array = new String[4];
		array[0] = String.valueOf(cursor.getInt(0));
		array[1] = cursor.getString(1);
		array[2] = cursor.getString(2);
		array[3] = String.valueOf(cursor.getInt(3));
	    }
	}
	catch(Exception exception)
	{
	    array = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return array;
    }

    public boolean accountPrepared()
    {
	return !readSetting(null, "encryptionSalt").isEmpty() &&
	    !readSetting(null, "macSalt").isEmpty() &&
	    !readSetting(null, "saltedPassword").isEmpty();
    }

    public boolean containsCongestionDigest(long value)
    {
	if(m_db == null)
	    return false;

	boolean contains = false;

	s_congestionControlMutex.readLock().lock();

	try
	{
	    Cursor cursor = null;

	    try
	    {
		cursor = m_db.rawQuery
		    ("SELECT EXISTS(SELECT 1 FROM " +
		     "congestion_control WHERE digest = ?)",
		     new String[] {Base64.
				   encodeToString(Miscellaneous.
						  longToByteArray(value),
						  Base64.DEFAULT)});

		if(cursor != null && cursor.moveToFirst())
		    contains = cursor.getInt(0) == 1;
	    }
	    catch(Exception exception)
	    {
	    }
	    finally
	    {
		if(cursor != null)
		    cursor.close();
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    s_congestionControlMutex.readLock().unlock();
	}

	return contains;
    }

    public boolean containsParticipant(Cryptography cryptography,
				       String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return false;

	Cursor cursor = null;
	boolean contains = false;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT EXISTS(SELECT 1 " +
		 "FROM participants WHERE " +
		 "siphash_id_digest = ?)",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		contains = cursor.getInt(0) == 1;
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return contains;
    }

    public boolean containsSteam(Cryptography cryptography, byte fileIdentity[])
    {
	if(cryptography == null || fileIdentity == null || m_db == null)
	    return false;

	Cursor cursor = null;
	boolean contains = false;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT EXISTS(SELECT 1 " +
		 "FROM steam_files WHERE " +
		 "file_identity_digest = ?)",
		 new String[] {Base64.
			       encodeToString(cryptography.hmac(fileIdentity),
					      Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		contains = cursor.getInt(0) == 1;
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return contains;
    }

    public boolean deleteEntry(String oid, String table)
    {
	if(m_db == null)
	    return false;

	boolean ok = false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ok = m_db.delete(table, "oid = ?", new String[] {oid}) > 0;
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    ok = false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return ok;
    }

    public boolean deleteFiascoKeys(String oid)
    {
	if(m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    m_db.delete
		("participants_keys",
		 "siphash_id_digest IN " +
		 "(SELECT siphash_id_digest FROM siphash_ids WHERE oid = ?)",
		 new String[] {oid});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean deleteFiascoKeysOfSiphashId
	(Cryptography cryptography, String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    m_db.delete
		("participants_keys",
		 "siphash_id_digest = ?",
		 new String[]
		    {Base64.encodeToString(cryptography.
					   hmac(sipHashId.toUpperCase().trim().
						getBytes(StandardCharsets.
							 UTF_8)),
					   Base64.DEFAULT)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean deletePublicKeys(String oid)
    {
	if(m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    m_db.delete
		("participants",
		 "siphash_id_digest IN " +
		 "(SELECT siphash_id_digest FROM siphash_ids WHERE oid = ?)",
		 new String[] {oid});
	    m_db.delete
		("participants_keys",
		 "siphash_id_digest IN " +
		 "(SELECT siphash_id_digest FROM siphash_ids WHERE oid = ?)",
		 new String[] {oid});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean hasPublicKeys(Cryptography cryptography, String sipHashId)
    {
	ArrayList<SipHashIdElement> arrayList = readSipHashIds
	    (cryptography, sipHashId);

	if(arrayList == null ||
	   arrayList.isEmpty() ||
	   arrayList.get(0) == null)
	    return false;

	SipHashIdElement sipHashIdElement = readSipHashId
	    (cryptography, String.valueOf(arrayList.get(0).m_oid));

	arrayList.clear();
	return sipHashIdElement != null &&
	    sipHashIdElement.m_encryptionPublicKey != null &&
	    sipHashIdElement.m_encryptionPublicKey.length > 0 &&
	    sipHashIdElement.m_signaturePublicKey != null &&
	    sipHashIdElement.m_signaturePublicKey.length > 0;
    }

    public boolean isSteamLocked(int oid)
    {
	if(m_db == null)
	    return true;

	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT is_locked FROM steam_files WHERE oid = ?",
		 new String[] {String.valueOf(oid)});

	    if(cursor != null && cursor.moveToFirst())
		return cursor.getInt(0) == 1;
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return true;
    }

    public boolean setParticipantKeyStream(Cryptography cryptography,
					   byte keyStream[],
					   int oid)
    {
	if(cryptography == null || m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    if(keyStream == null)
		values.put
		    ("keystream",
		     Base64.encodeToString(cryptography.etm("".getBytes()),
					   Base64.DEFAULT));
	    else
		values.put
		    ("keystream",
		     Base64.encodeToString(cryptography.etm(keyStream),
					   Base64.DEFAULT));

	    m_db.update
		("participants",
		 values,
		 "oid = ?",
		 new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean writeCallKeys(Cryptography cryptography,
				 String sipHashId,
				 byte keyStream[])
    {
	if(Cryptography.
	   memcmp(keyStream, participantKeyStream(cryptography, sipHashId)) ||
	   cryptography == null ||
	   keyStream == null ||
	   m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("keystream",
		 Base64.encodeToString(cryptography.etm(keyStream),
				       Base64.DEFAULT));
	    values.put
		("last_status_timestamp",
		 Base64.
		 encodeToString(cryptography.
				etm(Miscellaneous.
				    longToByteArray(System.
						    currentTimeMillis())),
				Base64.DEFAULT));
	    m_db.update("participants", values, "siphash_id_digest = ?",
			new String[] {Base64.
				      encodeToString
				      (cryptography.
				       hmac(sipHashId.toUpperCase().trim().
					    getBytes(StandardCharsets.UTF_8)),
				       Base64.DEFAULT)});
	    values.clear();
	    values.put("keystream",
		       Base64.encodeToString(cryptography.etm(keyStream),
					     Base64.DEFAULT));
	    values.put("keystream_digest",
		       Base64.encodeToString(cryptography.hmac(keyStream),
					     Base64.DEFAULT));
	    values.put
		("siphash_id_digest",
		 Base64.encodeToString(cryptography.
				       hmac(sipHashId.toUpperCase().trim().
					    getBytes(StandardCharsets.UTF_8)),
				       Base64.DEFAULT));
	    m_db.insertOrThrow("participants_keys", null, values);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean writeCongestionDigest(long value)
    {
	if(m_db == null)
	    return false;

	s_congestionControlMutex.writeLock().lock();

	try
	{
	    m_db.beginTransactionNonExclusive();

	    try
	    {
		ContentValues values = new ContentValues();

		values.put
		    ("digest",
		     Base64.encodeToString(Miscellaneous.
					   longToByteArray(value),
					   Base64.DEFAULT));
		m_db.insertOrThrow("congestion_control", null, values);
		m_db.setTransactionSuccessful();
	    }
	    catch(Exception exception)
	    {
		if(exception.getMessage().toLowerCase().contains("unique"))
		    return true;
	    }
	    finally
	    {
		m_db.endTransaction();
	    }
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    s_congestionControlMutex.writeLock().unlock();
	}

	return false;
    }

    public boolean writeEphemeralSteamKeys(Cryptography cryptography,
					   byte privateKey[],
					   byte publicKey[],
					   int oid)
    {
	if(cryptography == null || m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("ephemeral_private_key",
		 cryptography.etmBase64String(privateKey));
	    values.put
		("ephemeral_public_key",
		 cryptography.etmBase64String(publicKey));
	    m_db.update
		("steam_files",
		 values,
		 "oid = ?",
		 new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean writeMessageStatus(Cryptography cryptography,
				      String messageIdentityDigest)
    {
	if(cryptography == null || m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("message_sent",
		 Base64.encodeToString(cryptography.etm("true".getBytes()),
				       Base64.DEFAULT));

	    if(m_db.update("participants_messages",
			   values,
			   "message_identity_digest = ?",
			   new String[] {messageIdentityDigest}) > 0)
	    {
		Cursor cursor = null;

		try
		{
		    cursor = m_db.rawQuery
			("SELECT siphash_id_digest FROM " +
			 "participants_messages " +
			 "WHERE message_identity_digest = ?",
			 new String[] {messageIdentityDigest});

		    if(cursor != null && cursor.moveToFirst())
		    {
			String sipHashIdDigest1 = cursor.getString(0);

			synchronized(m_readMemberChatCursorMutex)
			{
			    String sipHashIdDigest2 = Base64.
				encodeToString
				(cryptography.
				 hmac(m_readMemberChatSipHashId.toUpperCase().
				      trim().getBytes(StandardCharsets.UTF_8)),
				 Base64.DEFAULT);

			    if(sipHashIdDigest1.equals(sipHashIdDigest2))
			    {
				if(m_readMemberChatCursor != null)
				    m_readMemberChatCursor.close();

				m_readMemberChatCursor = null;
			    }
			}
		    }
		}
		catch(Exception exception)
		{
		}
		finally
		{
		    if(cursor != null)
			cursor.close();
		}
	    }

	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean writeMessageStatus(Cryptography cryptography,
				      String sipHashId,
				      byte messageIdentity[])
    {
	if(cryptography == null || m_db == null || messageIdentity == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    m_db.delete
		("outbound_queue",
		 "message_identity_digest = ?",
		 new String[] {Base64.encodeToString(cryptography.
						     hmac(messageIdentity),
						     Base64.DEFAULT)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("message_read",
		 Base64.encodeToString(cryptography.etm("true".getBytes()),
				       Base64.DEFAULT));

	    if(m_db.update
	       ("participants_messages",
		values,
		"message_identity_digest = ? AND siphash_id_digest = ?",
		new String[] {Base64.encodeToString(cryptography.
						    hmac(messageIdentity),
						    Base64.DEFAULT),
			      Base64.encodeToString
			      (cryptography.
			       hmac(sipHashId.toUpperCase().trim().
				    getBytes(StandardCharsets.UTF_8)),
			       Base64.DEFAULT)}) > 0)
		synchronized(m_readMemberChatCursorMutex)
		{
		    if(m_readMemberChatSipHashId.equals(sipHashId))
		    {
			if(m_readMemberChatCursor != null)
			    m_readMemberChatCursor.close();

			m_readMemberChatCursor = null;
		    }
		}

	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean writeNeighbor(Cryptography cryptography,
				 String nonTls,
				 String passthrough,
				 String proxyIpAddress,
				 String proxyPort,
				 String proxyType,
				 String remoteIpAddress,
				 String remoteIpPort,
				 String remoteIpScopeId,
				 String transport,
				 String version)
    {
	if(cryptography == null || m_db == null)
	    return false;

	ContentValues values = null;
	boolean ok = true;

	try
	{
	    values = new ContentValues();
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	if(!ok)
	    return ok;

	try
	{
	    SparseArray<String> sparseArray = new SparseArray<> ();
	    byte bytes[] = null;

	    sparseArray.append(0, "bytes_read");
	    sparseArray.append(1, "bytes_written");
	    sparseArray.append(2, "echo_queue_size");
	    sparseArray.append(3, "ip_version");
	    sparseArray.append(4, "last_error");
	    sparseArray.append(5, "local_ip_address");
	    sparseArray.append(6, "local_ip_address_digest");
	    sparseArray.append(7, "local_port");
	    sparseArray.append(8, "local_port_digest");
	    sparseArray.append(9, "non_tls");
	    sparseArray.append(10, "passthrough");
	    sparseArray.append(11, "proxy_ip_address");
	    sparseArray.append(12, "proxy_port");
	    sparseArray.append(13, "proxy_type");
	    sparseArray.append(14, "remote_certificate");
	    sparseArray.append(15, "remote_ip_address");
	    sparseArray.append(16, "remote_ip_address_digest");
	    sparseArray.append(17, "remote_port");
            sparseArray.append(18, "remote_port_digest");
            sparseArray.append(19, "remote_scope_id");
            sparseArray.append(20, "session_cipher");
            sparseArray.append(21, "status");
            sparseArray.append(22, "status_control");
            sparseArray.append(23, "transport");
            sparseArray.append(24, "transport_digest");
            sparseArray.append(25, "uptime");
            sparseArray.append(26, "user_defined_digest");

	    /*
	    ** Proxy information.
	    */

	    if(!transport.equalsIgnoreCase("tcp"))
	    {
		proxyIpAddress = "";
		proxyPort = "";
		proxyType = "HTTP";
	    }
	    else
	    {
		proxyIpAddress = proxyIpAddress.trim();

		if(proxyIpAddress.isEmpty())
		{
		    proxyPort = "";
		    proxyType = "HTTP";
		}
	    }

	    if(!remoteIpAddress.toLowerCase().trim().matches(".*[a-z].*"))
	    {
		Matcher matcher = Patterns.IP_ADDRESS.matcher
		    (remoteIpAddress.trim());

		if(!matcher.matches())
		{
		    if(version.equalsIgnoreCase("ipv4"))
			remoteIpAddress = "0.0.0.0";
		    else
			remoteIpAddress = "0:0:0:0:0:ffff:0:0";
		}
	    }

	    int size = sparseArray.size();

	    for(int i = 0; i < size; i++)
	    {
		switch(sparseArray.get(i))
		{
		case "echo_queue_size":
		    bytes = cryptography.etm("0".getBytes());
		    break;
		case "ip_version":
		    bytes = cryptography.etm(version.trim().getBytes());
		    break;
		case "last_error":
		    bytes = cryptography.etm("".getBytes());
		    break;
		case "local_ip_address_digest":
		    bytes = cryptography.hmac("".getBytes());
		    break;
		case "local_port_digest":
		    bytes = cryptography.hmac("".getBytes());
		    break;
		case "non_tls":
		    bytes = cryptography.etm(nonTls.getBytes());
		    break;
		case "passthrough":
		    bytes = cryptography.etm(passthrough.getBytes());
		    break;
		case "proxy_ip_address":
		    bytes = cryptography.etm(proxyIpAddress.getBytes());
		    break;
		case "proxy_port":
		    bytes = cryptography.etm(proxyPort.getBytes());
		    break;
		case "proxy_type":
		    bytes = cryptography.etm(proxyType.getBytes());
		    break;
		case "remote_ip_address":
		    bytes = cryptography.etm
			(remoteIpAddress.trim().getBytes());
		    break;
		case "remote_ip_address_digest":
		    bytes = cryptography.hmac
			(remoteIpAddress.trim().getBytes());
		    break;
		case "remote_port":
		    bytes = cryptography.etm(remoteIpPort.trim().getBytes());
		    break;
		case "remote_port_digest":
		    bytes = cryptography.hmac(remoteIpPort.trim().getBytes());
		    break;
		case "remote_scope_id":
		    bytes = cryptography.etm(remoteIpScopeId.trim().getBytes());
		    break;
		case "status":
		    bytes = cryptography.etm("disconnected".getBytes());
		    break;
		case "status_control":
		    bytes = cryptography.etm("connect".getBytes());
		    break;
		case "transport":
		    bytes = cryptography.etm(transport.trim().getBytes());
		    break;
		case "transport_digest":
		    bytes = cryptography.hmac(transport.trim().getBytes());
		    break;
		case "user_defined_digest":
		    bytes = cryptography.hmac("true".getBytes());
		    break;
		default:
		    bytes = cryptography.etm("".getBytes());
		    break;
		}

		if(bytes == null)
		{
		    sparseArray.clear();

		    StringBuilder stringBuilder = new StringBuilder();

		    stringBuilder.append
			("Database::writeNeighbor(): error with ");
		    stringBuilder.append(sparseArray.get(i));
		    stringBuilder.append(" field.");
		    writeLog(stringBuilder.toString());
		    throw new Exception();
		}

		String str = Base64.encodeToString(bytes, Base64.DEFAULT);

		values.put(sparseArray.get(i), str);
	    }

	    sparseArray.clear();
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	m_db.beginTransactionNonExclusive();

	try
	{
	    if(ok)
	    {
		m_db.insertOrThrow("neighbors", null, values);
		m_db.setTransactionSuccessful();
	    }
	}
	catch(SQLiteConstraintException exception)
	{
	    ok = exception.getMessage().toLowerCase().contains("unique");
	}
	catch(Exception exception)
        {
	    ok = false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return ok;
    }

    public boolean writeParticipantName(Cryptography cryptography,
					String name,
					int oid)
    {
	if(cryptography == null ||
	   m_db == null ||
	   name == null ||
	   name.trim().isEmpty())
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("name",
		 Base64.encodeToString(cryptography.etm(name.trim().getBytes()),
				       Base64.DEFAULT));
	    m_db.update("siphash_ids", values, "oid = ?",
			new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public boolean writeSipHashParticipant(Cryptography cryptography,
					   String name,
					   String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return false;

	ContentValues values = null;
	boolean ok = true;

	try
	{
	    values = new ContentValues();
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	if(!ok)
	    return ok;

	try
	{
	    SparseArray<String> sparseArray = new SparseArray<> ();
	    byte bytes[] = null;

	    name = name.trim();

	    if(name.isEmpty())
		name = "unknown";

	    sipHashId = Miscellaneous.prepareSipHashId(sipHashId);
	    sparseArray.append(0, "name");
	    sparseArray.append(1, "siphash_id");
	    sparseArray.append(2, "siphash_id_digest");
	    sparseArray.append(3, "stream");

	    int size = sparseArray.size();

	    for(int i = 0; i < size; i++)
	    {
		switch(sparseArray.get(i))
		{
		case "name":
		    bytes = cryptography.etm(name.getBytes());
		    break;
		case "siphash_id":
		    bytes = cryptography.etm
			(sipHashId.trim().getBytes(StandardCharsets.UTF_8));
		    break;
		case "siphash_id_digest":
		    bytes = cryptography.hmac
			(sipHashId.trim().getBytes(StandardCharsets.UTF_8));
		    break;
		default:
		    byte salt[] = Cryptography.sha512
			(sipHashId.trim().getBytes(StandardCharsets.UTF_8));
		    byte temporary[] = Cryptography.
			pbkdf2(salt,
			       sipHashId.toCharArray(),
			       SIPHASH_STREAM_CREATION_ITERATION_COUNT,
			       160); // SHA-1

		    if(temporary != null)
			bytes = cryptography.etm
			    (Cryptography.
			     pbkdf2(salt,
				    Base64.encodeToString(temporary,
							  Base64.NO_WRAP).
				    toCharArray(),
				    1,
				    // Bits.
				    8 * (Cryptography.CIPHER_KEY_LENGTH +
					 Cryptography.HASH_KEY_LENGTH)));

		    break;
		}

		if(bytes == null)
		{
		    sparseArray.clear();

		    StringBuilder stringBuilder = new StringBuilder();

		    stringBuilder.append
			("Database::writeSipHashParticipant(): error with ");
		    stringBuilder.append(sparseArray.get(i));
		    stringBuilder.append(" field.");
		    writeLog(stringBuilder.toString());
		    throw new Exception();
		}

		String str = Base64.encodeToString(bytes, Base64.DEFAULT);

		values.put(sparseArray.get(i), str);
	    }

	    sparseArray.clear();
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	m_db.beginTransactionNonExclusive();

	try
	{
	    if(ok)
	    {
		if(m_db.
		   update("siphash_ids",
			  values,
			  "siphash_id_digest = ?",
			  new String[] {Base64.
					encodeToString
					(cryptography.
					 hmac(sipHashId.toUpperCase().trim().
					      getBytes(StandardCharsets.UTF_8)),
					 Base64.DEFAULT)}) <= 0)
		{
		    if(m_db.replace("siphash_ids", null, values) == -1)
			ok = false;
		}

		m_db.setTransactionSuccessful();
	    }
	}
	catch(Exception exception)
        {
	    ok = false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return ok;
    }

    public boolean writeSteamKeys(Cryptography cryptography,
				  KeyPair keyPair,
				  byte keyStream[],
				  int oid)
    {
	return writeSteamKeys
	    (cryptography,
	     keyStream,
	     keyPair.getPrivate().getEncoded(),
	     keyPair.getPublic().getEncoded(),
	     oid);
    }

    public boolean writeSteamKeys(Cryptography cryptography,
				  byte keyStream[],
				  byte privateKey[],
				  byte publicKey[],
				  int oid)
    {
	if(cryptography == null || m_db == null)
	    return false;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("ephemeral_private_key",
		 cryptography.etmBase64String(privateKey));
	    values.put
		("ephemeral_public_key",
		 cryptography.etmBase64String(publicKey));
	    values.put("keystream", cryptography.etmBase64String(keyStream));
	    m_db.update
		("steam_files",
		 values,
		 "oid = ?",
		 new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	    return false;
	}
	finally
	{
	    m_db.endTransaction();
	}

	return true;
    }

    public byte[] fireStream(Cryptography cryptography, String name)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	byte bytes[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT stream FROM fire WHERE name_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(name.
				     getBytes(StandardCharsets.ISO_8859_1)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		bytes = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return bytes;
    }

    public byte[] neighborRemoteCertificate(Cryptography cryptography,
					    int oid)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	byte bytes[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT remote_certificate FROM neighbors WHERE oid = ?",
		 new String[] {String.valueOf(oid)});

	    if(cursor != null && cursor.moveToFirst())
		bytes = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return bytes;
    }

    public byte[] participantKeyStream(Cryptography cryptography,
				       String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	byte bytes[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT keystream FROM participants " +
		 "WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		bytes = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return bytes;
    }

    public byte[] participantKeyStream(Cryptography cryptography,
				       byte digest[])
    {
	if(cryptography == null || digest == null || m_db == null)
	    return null;

	Cursor cursor = null;
	byte bytes[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT keystream FROM participants " +
		 "WHERE encryption_public_key_digest = ?",
		 new String[] {Base64.encodeToString(digest, Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		bytes = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));
	}
	catch(Exception exception)
	{
	    bytes = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return bytes;
    }

    public byte[] participantKeyStream(Cryptography cryptography,
				       byte digest[],
				       byte array[],
				       byte bytes[])
    {
	if(array == null ||
	   bytes == null ||
	   cryptography == null ||
	   digest == null ||
	   m_db == null)
	    return null;

	Cursor cursor = null;
	byte keyStream[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT keystream FROM participants_keys " +
		 "WHERE siphash_id_digest = " +
		 "(SELECT siphash_id_digest FROM participants WHERE " +
		 "encryption_public_key_digest = ?) ORDER BY timestamp DESC",
		 new String[] {Base64.encodeToString(digest, Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		while(!cursor.isAfterLast())
		{
		    keyStream = cryptography.mtd
			(Base64.decode(cursor.getString(0).getBytes(),
				       Base64.DEFAULT));

		    if(keyStream == null)
			continue;

		    byte hmac[] = Cryptography.hmac
			(Arrays.copyOfRange(bytes,
					    0,
					    bytes.length -
					    2 * Cryptography.HASH_KEY_LENGTH),
			 Arrays.copyOfRange(keyStream,
					    Cryptography.CIPHER_KEY_LENGTH,
					    keyStream.length));

		    if(Cryptography.memcmp(array, hmac))
			break;

		    cursor.moveToNext();
		}
	}
	catch(Exception exception)
	{
	    keyStream = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return keyStream;
    }

    public byte[] steamKeyStream(Cryptography cryptography,
				 byte fileIdentity[])
    {
	if(cryptography == null || fileIdentity == null || m_db == null)
	    return null;

	Cursor cursor = null;
	byte keyStream[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT keystream FROM steam_files " +
		 "WHERE file_identity_digest = ?",
		 new String[] {Base64.encodeToString(cryptography.
						     hmac(fileIdentity),
						     Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		keyStream = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));
	}
	catch(Exception exception)
	{
	    keyStream = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return keyStream;
    }

    public int participantOidFromSipHash(Cryptography cryptography,
					 String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return -1;

	Cursor cursor = null;
	int oid = -1;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT oid FROM participants WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		oid = cursor.getInt(0);
	}
	catch(Exception exception)
	{
	    oid = -1;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return oid;
    }

    public int participantsWithSessionKeys(int oid)
    {
	if(m_db == null)
	    return -1;

	Cursor cursor = null;
	int count = 0;

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    if(oid == -1)
	    {
		stringBuilder.append("SELECT COUNT(*) FROM participants ");
		stringBuilder.append("WHERE LENGTH(keystream) >= ");
		stringBuilder.append
		    (4 * (Math.ceil(Cryptography.CIPHER_HASH_KEYS_LENGTH +
				    Cryptography.CIPHER_IV_LENGTH) / 3.0));
	    }
	    else
	    {
		stringBuilder.append("SELECT COUNT(*) FROM participants ");
		stringBuilder.append("WHERE LENGTH(keystream) >= ");
		stringBuilder.append
		    (4 * (Math.ceil(Cryptography.CIPHER_HASH_KEYS_LENGTH +
				    Cryptography.CIPHER_IV_LENGTH) / 3.0));
		stringBuilder.append(" AND oid = ");
		stringBuilder.append(oid);
	    }

	    cursor = m_db.rawQuery(stringBuilder.toString(), null);

	    if(cursor != null && cursor.moveToFirst())
		count = cursor.getInt(0);
	}
	catch(Exception exception)
	{
	    count = -1;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return count;
    }

    public int steamOidFromFileIdentity(Cryptography cryptography,
					byte fileIdentity[])
    {
	if(cryptography == null || fileIdentity == null || m_db == null)
	    return -1;

	Cursor cursor = null;
	int oid = -1;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT someoid FROM steam_files WHERE " +
		 "file_identity_digest = ?",
		 new String[] {Base64.encodeToString(cryptography.
						     hmac(fileIdentity),
						     Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		oid = cursor.getInt(0);
	}
	catch(Exception exception)
	{
	    oid = -1;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return oid;
    }

    public long countOfMessages(Cryptography cryptography, String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return -1L;

	Cursor cursor = null;
	long count = 0L;

	try
	{
	    StringBuilder stringBuilder = new StringBuilder();

	    stringBuilder.append("SELECT COUNT(*) FROM participants_messages ");
	    stringBuilder.append("WHERE siphash_id_digest = ?");
	    cursor = m_db.rawQuery
		(stringBuilder.toString(),
		 new String[] {Base64.
			       encodeToString
			       (cryptography.
				hmac(sipHashId.toUpperCase().trim().
				     getBytes(StandardCharsets.UTF_8)),
				Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
		count = cursor.getLong(0);
	}
	catch(Exception exception)
	{
	    count = -1L;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return count;
    }

    public long countOfSteams()
    {
	if(m_db == null)
	    return -1L;

	Cursor cursor = null;
	long count = 0L;

	try
	{
	    cursor = m_db.rawQuery("SELECT COUNT(*) FROM steam_files", null);

	    if(cursor != null && cursor.moveToFirst())
		count = cursor.getLong(0);
	}
	catch(Exception exception)
	{
	    count = -1L;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return count;
    }

    public static synchronized Database getInstance()
    {
	return s_instance; // Should never be null.
    }

    public static synchronized Database getInstance(Context context)
    {
	if(s_instance == null)
	    s_instance = new Database(context);

	return s_instance;
    }

    public void cleanDanglingOutboundQueued()
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    m_db.delete
		("outbound_queue",
		 "attempts >= CAST(? AS INTEGER) OR " +
		 "neighbor_oid NOT IN (SELECT oid FROM neighbors)",
		 new String[] {String.valueOf(MESSAGE_DELIVERY_ATTEMPTS)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void cleanDanglingParticipants()
    {
	if(m_db == null)
	    return;

	Cursor cursor = null;

	m_db.beginTransactionNonExclusive();

	try
	{
	    cursor = m_db.rawQuery
		("DELETE FROM participants WHERE siphash_id_digest " +
		 "NOT IN (SELECT siphash_id_digest FROM siphash_ids)",
		 null);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    if(cursor != null)
		cursor.close();

	    m_db.endTransaction();
	}
    }

    public void cleanDanglingSteams()
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    m_db.delete("steam_files", "status = 'deleted'", null);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void cleanNeighborStatistics(Cryptography cryptography)
    {
	ArrayList<NeighborElement> arrayList = readNeighborOids(cryptography);

	if(arrayList == null || arrayList.isEmpty())
	    return;

	for(NeighborElement neighborElement : arrayList)
	    if(neighborElement != null)
		saveNeighborInformation(cryptography,
					"0",             // Bytes Read
					"0",             // Bytes Written
					"0",             // Queue Size
					"",              // Error
					"",              // IP Address
					"0",             // Port
					"",              // Session Cipher
					"disconnected",  // Status
					"0",             // Uptime
					String.valueOf(neighborElement.m_oid));

	arrayList.clear();
    }

    public void clearTable(String table)
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    m_db.delete(table, null, null);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}

	if(table.equals("participants_messages"))
	    synchronized(m_readMemberChatCursorMutex)
	    {
		if(m_readMemberChatCursor != null)
		    m_readMemberChatCursor.close();

		m_readMemberChatCursor = null;
		m_readMemberChatSipHashId = "";
	    }
    }

    public void clearSteamRates(Cryptography cryptography)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("transfer_rate",
		 cryptography.etmBase64String(Miscellaneous.RATE));
	    m_db.update("steam_files", values, null, null);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void deleteParticipantMessage(Cryptography cryptography,
					 String sipHashId,
					 int oid)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    if(m_db.delete("participants_messages",
			   "oid = ? AND siphash_id_digest = ?",
			   new String[] {String.valueOf(oid),
					 Base64.
					 encodeToString
					 (cryptography.
					  hmac(sipHashId.toUpperCase().trim().
					       getBytes(StandardCharsets.
							UTF_8)),
					  Base64.DEFAULT)}) > 0)
		synchronized(m_readMemberChatCursorMutex)
		{
		    if(m_readMemberChatSipHashId.equals(sipHashId))
		    {
			if(m_readMemberChatCursor != null)
			    m_readMemberChatCursor.close();

			m_readMemberChatCursor = null;
		    }
		}

	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void deleteParticipantMessages(Cryptography cryptography,
					  String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    if(m_db.delete("participants_messages", "siphash_id_digest = ?",
			   new String[] {Base64.
					 encodeToString
					 (cryptography.
					  hmac(sipHashId.toUpperCase().trim().
					       getBytes(StandardCharsets.
							UTF_8)),
					  Base64.DEFAULT)}) > 0)
		synchronized(m_readMemberChatCursorMutex)
		{
		    if(m_readMemberChatSipHashId.equals(sipHashId))
		    {
			if(m_readMemberChatCursor != null)
			    m_readMemberChatCursor.close();

			m_readMemberChatCursor = null;
		    }
		}

	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void enqueueOutboundMessage(Cryptography cryptography,
				       String message,
				       byte messageIdentity[],
				       int attempts,
				       int oid)
    {
	if(cryptography == null ||
	   m_db == null ||
	   message == null ||
	   message.trim().isEmpty())
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("attempts", attempts);
	    values.put
		("message",
		 Base64.encodeToString(cryptography.etm(message.getBytes()),
				       Base64.DEFAULT));

	    if(messageIdentity == null)
		values.put
		    ("message_identity_digest",
		     Base64.
		     encodeToString(Cryptography.
				    randomBytes(Cryptography.HASH_KEY_LENGTH),
				    Base64.DEFAULT));
	    else
		values.put
		    ("message_identity_digest",
		     Base64.encodeToString(cryptography.hmac(messageIdentity),
					   Base64.DEFAULT));

	    values.put("neighbor_oid", oid);
	    m_db.insertOrThrow("outbound_queue", null, values);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void markMessageTimestamp(String attempts, String oid)
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("attempts", Integer.parseInt(attempts) + 1);
	    values.put("timestamp", System.currentTimeMillis());
	    m_db.update
		("outbound_queue", values, "oid = ?", new String[] {oid});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void neighborControlStatus(Cryptography cryptography,
				      String controlStatus,
				      String oid)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("status_control",
		 Base64.encodeToString(cryptography.
				       etm(controlStatus.trim().getBytes()),
				       Base64.DEFAULT));
	    m_db.update("neighbors", values, "oid = ?", new String[] {oid});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void neighborRecordCertificate(Cryptography cryptography,
					  String oid,
					  byte certificate[])
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    if(certificate == null)
		values.put
		    ("remote_certificate",
		     Base64.encodeToString(cryptography.etm("".getBytes()),
					   Base64.DEFAULT));
	    else
		values.put
		    ("remote_certificate",
		     Base64.encodeToString(cryptography.etm(certificate),
					   Base64.DEFAULT));

	    m_db.update("neighbors", values, "oid = ?", new String[] {oid});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    @Override
    public void onConfigure(SQLiteDatabase db)
    {
	if(db == null)
	    return;

	try
	{
	    db.enableWriteAheadLogging();
	}
	catch(Exception exception)
	{
	}

	try
	{
	    db.execSQL("VACUUM");
	}
	catch(Exception exception)
	{
	}

	try
	{
	    db.execSQL("PRAGMA secure_delete = True", null);
	}
	catch(Exception exception)
	{
	}

	try
	{
	    db.setForeignKeyConstraintsEnabled(true);
        }
	catch(Exception exception)
	{
	}
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
	if(db == null)
	    return;

	String str = "";

	/*
	** Order is critical.
	*/

	/*
	** Create the siphash_ids table.
	*/

	str = "CREATE TABLE IF NOT EXISTS siphash_ids (" +
	    "name TEXT NOT NULL, " +
	    "siphash_id TEXT NOT NULL, " +
	    "siphash_id_digest TEXT NOT NULL PRIMARY KEY, " +
	    "stream TEXT NOT NULL)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the arson table.
	*/

	str = "CREATE TABLE IF NOT EXISTS arson (" +
	    "authentication_key TEXT NOT NULL, " +
	    "encryption_key TEXT NOT NULL, " +
	    "moonlander TEXT NOT NULL, " +
	    "private_encryption_key TEXT NOT NULL, " +
	    "public_encryption_key TEXT NOT NULL, " +
	    "siphash_id_digest TEXT NOT NULL, " +
	    "FOREIGN KEY (siphash_id_digest) REFERENCES " +
	    "siphash_ids (siphash_id_digest) ON DELETE CASCADE, " +
	    "PRIMARY KEY (siphash_id_digest))";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the congestion_control table.
	*/

	str = "CREATE TABLE IF NOT EXISTS congestion_control (" +
	    "digest TEXT NOT NULL PRIMARY KEY, " +
	    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the fire table.
	*/

	str = "CREATE TABLE IF NOT EXISTS fire (" +
	    "name TEXT NOT NULL, " +
	    "name_digest TEXT NOT NULL, " +
	    "stream TEXT NOT NULL, " +
	    "stream_digest TEXT NOT NULL PRIMARY KEY)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the log table.
	*/

	str = "CREATE TABLE IF NOT EXISTS log (" +
	    "event TEXT NOT NULL, " +
	    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the neighbors table.
	*/

	str = "CREATE TABLE IF NOT EXISTS neighbors (" +
	    "bytes_read TEXT NOT NULL, " +
	    "bytes_written TEXT NOT NULL, " +
	    "echo_queue_size TEXT NOT NULL, " +
	    "ip_version TEXT NOT NULL, " +
	    "last_error TEXT NOT NULL, " +
	    "local_ip_address TEXT NOT NULL, " +
	    "local_ip_address_digest TEXT NOT NULL, " +
	    "local_port TEXT NOT NULL, " +
	    "local_port_digest TEXT NOT NULL, " +
	    "non_tls TEXT NOT NULL, " +
	    "passthrough TEXT NOT NULL, " +
	    "proxy_ip_address TEXT NOT NULL, " +
	    "proxy_port TEXT NOT NULL, " +
	    "proxy_type TEXT NOT NULL, " +
	    "remote_certificate TEXT NOT NULL, " +
	    "remote_ip_address TEXT NOT NULL, " +
	    "remote_ip_address_digest TEXT NOT NULL, " +
	    "remote_port TEXT NOT NULL, " +
	    "remote_port_digest TEXT NOT NULL, " +
	    "remote_scope_id TEXT NOT NULL, " +
	    "session_cipher TEXT NOT NULL, " +
	    "status TEXT NOT NULL, " +
	    "status_control TEXT NOT NULL, " +
	    "transport TEXT NOT NULL, " +
	    "transport_digest TEXT NOT NULL, " +
	    "uptime TEXT NOT NULL, " +
	    "user_defined_digest TEXT NOT NULL, " +
	    "PRIMARY KEY (remote_ip_address_digest, " +
	    "remote_port_digest, " +
	    "transport_digest))";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the outbound_queue table.
	** A foreign-key constraint on the oid of the neighbors
	** table cannot be assigned.
	*/

	str = "CREATE TABLE IF NOT EXISTS outbound_queue (" +
	    "attempts INTEGER DEFAULT 0, " +
	    "message TEXT NOT NULL, " +
	    "message_identity_digest TEXT NOT NULL, " +
	    "neighbor_oid INTEGER NOT NULL, " +
	    "timestamp INTEGER DEFAULT 0, " +
	    "PRIMARY KEY (message, neighbor_oid))";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the participants table.
	*/

	str = "CREATE TABLE IF NOT EXISTS participants (" +
	    "encryption_public_key TEXT NOT NULL, " +
	    "encryption_public_key_algorithm TEXT NOT NULL, " +
	    "encryption_public_key_digest TEXT NOT NULL, " +
	    "encryption_public_key_signed TEXT NOT NULL, " +
	    "identity TEXT NOT NULL, " + // Not recorded.
	    "keystream TEXT NOT NULL, " + /*
					  ** Authentication and encryption
					  ** keys.
					  */
	    "last_status_timestamp TEXT NOT NULL, " +
	    "options TEXT NOT NULL, " +
	    "signature_public_key TEXT NOT NULL, " +
	    "signature_public_key_digest TEXT NOT NULL, " +
	    "signature_public_key_signed TEXT NOT NULL, " +
	    "siphash_id TEXT NOT NULL, " +
	    "siphash_id_digest TEXT NOT NULL, " +
	    "special_value_a TEXT, " + /*
				       ** Telephone number, for example.
				       */
	    "special_value_b TEXT, " +
	    "special_value_c TEXT, " +
	    "special_value_d TEXT, " +
	    "special_value_e TEXT, " +
	    "FOREIGN KEY (siphash_id_digest) REFERENCES " +
	    "siphash_ids (siphash_id_digest) ON DELETE CASCADE, " +
	    "PRIMARY KEY (siphash_id_digest))";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the participants_keys table. Note that the
	** keystream_digest should be unique for all participants.
	*/

	str = "CREATE TABLE IF NOT EXISTS participants_keys (" +
	    "keystream TEXT NOT NULL, " + /*
					  ** Authentication and encryption
					  ** keys.
					  */
	    "keystream_digest TEXT NOT NULL PRIMARY KEY, " +
	    "siphash_id_digest TEXT NOT NULL, " +
	    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
	    "FOREIGN KEY (siphash_id_digest) REFERENCES " +
	    "siphash_ids (siphash_id_digest) ON DELETE CASCADE)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the participants_messages table.
	*/

	str = "CREATE TABLE IF NOT EXISTS participants_messages (" +
	    "attachment BLOB NOT NULL, " +
	    "from_smokestack TEXT NOT NULL, " +
	    "message TEXT NOT NULL, " +
	    "message_digest TEXT NOT NULL, " +
	    "message_identity_digest TEXT NOT NULL, " + // Random.
	    "message_read TEXT NOT NULL, " +
	    "message_sent TEXT NOT NULL, " +
	    "siphash_id_digest TEXT NOT NULL, " +
	    "timestamp INTEGER NOT NULL, " +
	    "FOREIGN KEY (siphash_id_digest) REFERENCES " +
	    "siphash_ids (siphash_id_digest) ON DELETE CASCADE, " +
	    "PRIMARY KEY (message_digest, siphash_id_digest))";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	str = "CREATE INDEX IF NOT EXISTS " +
	    "participants_messages_timestamp_index " +
	    "ON participants_messages(timestamp)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the settings table.
	*/

	str = "CREATE TABLE IF NOT EXISTS settings (" +
	    "name TEXT NOT NULL, " +
	    "name_digest TEXT NOT NULL PRIMARY KEY, " +
	    "value TEXT NOT NULL)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	/*
	** Create the steam_files table.
	*/

	str = "CREATE TABLE IF NOT EXISTS steam_files (" +
	    "absolute_filename TEXT NOT NULL, " +
	    "destination TEXT NOT NULL, " +
	    "display_filename TEXT NOT NULL, " +
	    "ephemeral_private_key TEXT NOT NULL, " +
	    "ephemeral_public_key TEXT NOT NULL, " +
	    "file_digest TEXT NOT NULL, " +
	    "file_identity TEXT NOT NULL, " +
	    "file_identity_digest TEXT NOT NULL, " +
	    "file_size TEXT NOT NULL, " +
	    "is_download TEXT NOT NULL, " +
	    "is_locked INTEGER NOT NULL DEFAULT 1, " +
	    "key_type TEXT NOT NULL, " +
	    "keystream TEXT NOT NULL, " + /*
					  ** Authentication and encryption
					  ** keys.
					  */
	    "read_interval TEXT NOT NULL, " +
	    "read_offset TEXT NOT NULL, " +
	    "someoid INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
	    "status TEXT NOT NULL, " +
	    "transfer_rate TEXT NOT NULL)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
	if(db == null)
	    return;

        onCreate(db);

	String str = "";

	str = "ALTER TABLE steam_files ADD is_locked " +
	    "INTEGER NOT NULL DEFAULT 1";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

	str = "CREATE INDEX IF NOT EXISTS " +
	    "participants_messages_timestamp_index " +
	    "ON participants_messages(timestamp)";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}
    }

    public void pauseAllSteams()
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("status", "paused");
	    m_db.update
		("steam_files",
		 values,
		 "is_download <> ?",
		 new String[] {String.valueOf(SteamElement.DOWNLOAD)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void purgeCongestion(int lifetime)
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    /*
	    ** The bound string value must be cast to an integer.
	    */

	    m_db.delete
		("congestion_control",
		 "ABS(STRFTIME('%s', 'now') - STRFTIME('%s', timestamp)) > " +
		 "CAST(? AS INTEGER)",
		 new String[] {String.valueOf(lifetime)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void purgeNeighborQueue(final String oid)
    {
	if(m_db == null)
	    return;

	Executors.newSingleThreadScheduledExecutor().schedule(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    m_db.beginTransactionNonExclusive();

		    try
		    {
			m_db.delete("outbound_queue",
				    "neighbor_oid = ?",
				    new String[] {oid});
			m_db.setTransactionSuccessful();
		    }
		    catch(Exception exception)
		    {
		    }
		    finally
		    {
			m_db.endTransaction();
		    }
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0L, TimeUnit.MILLISECONDS);
    }

    public void purgeParticipantsKeyStreams(int lifetime)
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    /*
	    ** The bound string value must be cast to an integer.
	    */

	    m_db.delete
		("participants_keys",
		 "ABS(STRFTIME('%s', 'now') - STRFTIME('%s', timestamp)) > " +
		 "CAST(? AS INTEGER)",
		 new String[] {String.valueOf(lifetime)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void reset()
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    String tables[] = new String[]
		{"congestion_control",
		 "fire",
		 "log",
		 "neighbors",
		 "outbound_queue",
		 "participants",
		 "participants_keys",
		 "participants_messages",
		 "settings",
		 "siphash_ids",
		 "steam_files"};

	    for(String string : tables)
		try
		{
		    m_db.delete(string, null, null);
		}
		catch(Exception exception)
		{
		}

	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}

	synchronized(m_readMemberChatCursorMutex)
	{
	    if(m_readMemberChatCursor != null)
		m_readMemberChatCursor.close();

	    m_readMemberChatCursor = null;
	    m_readMemberChatSipHashId = "";
	}
    }

    public void resetAndDrop()
    {
	reset();

	if(m_db == null)
	    return;

	String strings[] = new String[]
	    {"DROP TABLE IF EXISTS arson",
	     "DROP TABLE IF EXISTS congestion_control",
	     "DROP TABLE IF EXISTS fire",
	     "DROP TABLE IF EXISTS log",
	     "DROP TABLE IF EXISTS neighbors",
	     "DROP TABLE IF EXISTS outbound_queue",
	     "DROP TABLE IF EXISTS participants",
	     "DROP TABLE IF EXISTS participants_keys",
	     "DROP TABLE IF EXISTS participants_messages",
	     "DROP TABLE IF EXISTS settings",
	     "DROP TABLE IF EXISTS siphash_ids",
	     "DROP TABLE IF EXISTS steam_files"};

	for(String string : strings)
	    try
	    {
		m_db.execSQL(string);
	    }
	    catch(Exception exception)
	    {
	    }

	onCreate(m_db);
    }

    public void resumeAllSteams()
    {
	if(m_db == null)
	    return;

	/*
	** Ignore received Steams.
	*/

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("status", "resume");
	    m_db.update
		("steam_files",
		 values,
		 "is_download <> ?",
		 new String[] {String.valueOf(SteamElement.DOWNLOAD)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void rewindAllSteams()
    {
	if(m_db == null)
	    return;

	/*
	** Ignore received Steams.
	*/

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("status", "rewind");
	    m_db.update
		("steam_files",
		 values,
		 "is_download <> ?",
		 new String[] {String.valueOf(SteamElement.DOWNLOAD)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void rewindAndResumeAllSteams()
    {
	if(m_db == null)
	    return;

	/*
	** Ignore received Steams.
	*/

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("status", "rewind & resume");
	    m_db.update
		("steam_files",
		 values,
		 "is_download <> ?",
		 new String[] {String.valueOf(SteamElement.DOWNLOAD)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void saveFireChannel(Cryptography cryptography,
				String name,
				byte encryptionKey[],
				byte keyStream[])
    {
	if(cryptography == null ||
	   encryptionKey == null ||
	   keyStream == null ||
	   m_db == null ||
	   name == null ||
	   name.isEmpty())
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();
	    byte bytes[] = Miscellaneous.joinByteArrays
		(encryptionKey, keyStream);

	    values.put
		("name",
		 Base64.encodeToString(cryptography.
				       etm(name.getBytes(StandardCharsets.
							 ISO_8859_1)),
				       Base64.DEFAULT));
	    values.put
		("name_digest",
		 Base64.encodeToString(cryptography.
				       hmac(name.getBytes(StandardCharsets.
							  ISO_8859_1)),
				       Base64.DEFAULT));
	    values.put
		("stream",
		 Base64.encodeToString(cryptography.etm(bytes),
				       Base64.DEFAULT));
	    values.put
		("stream_digest",
		 Base64.encodeToString(cryptography.hmac(bytes),
				       Base64.DEFAULT));
	    m_db.insertOrThrow("fire", null, values);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void saveNeighborInformation(Cryptography cryptography,
					String bytesRead,
					String bytesWritten,
					String echoQueueSize,
					String error,
					String ipAddress,
					String ipPort,
					String sessionCipher,
					String status,
					String uptime,
					String oid)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    if(!status.equals("connected"))
	    {
		bytesRead = "";
		bytesWritten = "";
		echoQueueSize = "0";
		error = error.trim(); // Do not clear the error.
		ipAddress = "";
		ipPort = "";
		sessionCipher = "";
		uptime = "";
	    }

	    values.put
		("bytes_read",
		 Base64.encodeToString(cryptography.etm(bytesRead.getBytes()),
				       Base64.DEFAULT));
	    values.put
		("bytes_written",
		 Base64.encodeToString(cryptography.etm(bytesWritten.
							getBytes()),
				       Base64.DEFAULT));
	    values.put
		("echo_queue_size",
		 Base64.encodeToString(cryptography.etm(echoQueueSize.
							getBytes()),
				       Base64.DEFAULT));
	    values.put
		("last_error",
		 Base64.encodeToString(cryptography.etm(error.getBytes()),
				       Base64.DEFAULT));
	    values.put
		("local_ip_address",
		 Base64.encodeToString(cryptography.
				       etm(ipAddress.trim().getBytes()),
				       Base64.DEFAULT));
	    values.put
		("local_ip_address_digest",
		 Base64.encodeToString(cryptography.
				       hmac(ipAddress.trim().getBytes()),
				       Base64.DEFAULT));
	    values.put
		("local_port",
		 Base64.encodeToString(cryptography.
				       etm(ipPort.trim().getBytes()),
				       Base64.DEFAULT));
	    values.put
		("local_port_digest",
		 Base64.encodeToString(cryptography.
				       hmac(ipPort.trim().getBytes()),
				       Base64.DEFAULT));
	    values.put
		("session_cipher",
		 Base64.encodeToString(cryptography.etm(sessionCipher.
							getBytes()),
				       Base64.DEFAULT));
	    values.put
		("status",
		 Base64.encodeToString(cryptography.
				       etm(status.trim().getBytes()),
				       Base64.DEFAULT));
	    values.put
		("uptime",
		 Base64.encodeToString(cryptography.
				       etm(uptime.trim().getBytes()),
				       Base64.DEFAULT));
	    m_db.update("neighbors", values, "oid = ?", new String[] {oid});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void steamRoll(Cryptography cryptography,
			  Set<String> participants,
			  String downloadsPath,
			  int steamId)
    {
	if(cryptography == null ||
	   participants == null ||
	   participants.isEmpty())
	    return;

	SteamElement steamElement = readSteam(cryptography, -1, steamId - 1);

	if(steamElement == null)
	    return;

	try
	{
	    steamElement.m_direction = SteamElement.UPLOAD;
	    steamElement.m_ephemeralPrivateKey = null;
	    steamElement.m_ephemeralPublicKey = null;
	    steamElement.m_fileIdentity = null;
	    steamElement.m_fileName = downloadsPath +
		File.separator +
		steamElement.m_displayFileName;
	    steamElement.m_keyStream = null;
	    steamElement.m_readInterval = 4L;
	    steamElement.m_readOffset = 0L;
	    steamElement.m_status = "paused";
	    steamElement.m_transferRate = "";

	    for(String string : participants)
	    {
		steamElement.m_destination = string;
		writeSteamImplementation(cryptography, steamElement);
	    }
	}
	catch(Exception exception)
	{
	}
    }

    public void updateParticipantLastTimestamp(Cryptography cryptography,
					       String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("last_status_timestamp",
		 Base64.
		 encodeToString(cryptography.
				etm(Miscellaneous.
				    longToByteArray(System.
						    currentTimeMillis())),
				Base64.DEFAULT));
	    m_db.update("participants", values, "siphash_id_digest = ?",
			new String[] {Base64.
				      encodeToString
				      (cryptography.
				       hmac(sipHashId.toUpperCase().trim().
					    getBytes(StandardCharsets.UTF_8)),
				       Base64.DEFAULT)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void updateParticipantLastTimestamp(Cryptography cryptography,
					       byte digest[])
    {
	if(cryptography == null || digest == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("last_status_timestamp",
		 Base64.
		 encodeToString(cryptography.
				etm(Miscellaneous.
				    longToByteArray(System.
						    currentTimeMillis())),
				Base64.DEFAULT));
	    m_db.update("participants",
			values,
			"encryption_public_key_digest = ?",
			new String[] {Base64.encodeToString(digest,
							    Base64.DEFAULT)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void writeLog(String event)
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("event", event.trim());
	    m_db.insert("log", null, values);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void writeParticipantOptions(Cryptography cryptography,
					String options,
					String sipHashId)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("options",
		 Base64.encodeToString(cryptography.etm(options.getBytes()),
				       Base64.DEFAULT));
	    m_db.update("participants",
			values,
			"siphash_id_digest = ?",
			new String[] {Base64.
				      encodeToString
				      (cryptography.
				       hmac(sipHashId.toUpperCase().trim().
					    getBytes(StandardCharsets.UTF_8)),
				       Base64.DEFAULT)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void writeSetting(Cryptography cryptography,
			     String name,
			     String value)
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    String a = name.trim();
	    String b = name.trim();
	    String c = value; // Do not trim.

	    if(cryptography != null)
	    {
		byte bytes[] = null;

		bytes = cryptography.etm(a.getBytes());

		if(bytes != null)
		    a = Base64.encodeToString(bytes, Base64.DEFAULT);
		else
		    a = "";

		bytes = cryptography.hmac(b.getBytes());

		if(bytes != null)
		    b = Base64.encodeToString(bytes, Base64.DEFAULT);
		else
		    b = "";

		bytes = cryptography.etm(c.getBytes());

		if(bytes != null)
		    c = Base64.encodeToString(bytes, Base64.DEFAULT);
		else
		    c = "";

		if(a.isEmpty() || b.isEmpty() || c.isEmpty())
		    throw new Exception();
	    }

	    ContentValues values = new ContentValues();

	    values.put("name", a);
	    values.put("name_digest", b);
	    values.put("value", c);
	    m_db.replace("settings", null, values);
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
        {
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void writeSteam(final Cryptography cryptography,
			   final SteamElement steamElement)
    {
	/*
	** Record received and transmitted Steams.
	*/

	if(cryptography == null ||
	   m_db == null ||
	   steamElement == null ||
	   steamElement.m_displayFileName.trim().isEmpty() ||
	   steamElement.m_fileName.trim().isEmpty())
	    return;

	Executors.newSingleThreadScheduledExecutor().schedule(new Runnable()
	{
	    @Override
	    public void run()
	    {
		try
		{
		    writeSteamImplementation(cryptography, steamElement);
		}
		catch(Exception exception)
		{
		}
	    }
	}, 0L, TimeUnit.MILLISECONDS);
    }

    public void writeSteamStatus(final Cryptography cryptography,
				 final String status,
				 final String transferRate,
				 final int oid)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    if(!status.isEmpty())
		values.put("status", status);

	    if(transferRate.isEmpty())
		values.put
		    ("transfer_rate",
		     cryptography.etmBase64String(Miscellaneous.RATE));
	    else
		values.put
		    ("transfer_rate",
		     cryptography.etmBase64String(transferRate));

	    m_db.update("steam_files",
			values,
			"oid = ?",
			new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void writeSteamStatus(final Cryptography cryptography,
				 final String status,
				 final String transferRate,
				 final int oid,
				 final long offset)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("read_offset", cryptography.etmBase64String(offset));

	    if(!status.isEmpty())
		values.put("status", status);

	    if(transferRate.isEmpty())
		values.put
		    ("transfer_rate",
		     cryptography.etmBase64String(Miscellaneous.RATE));
	    else
		values.put
		    ("transfer_rate",
		     cryptography.etmBase64String(transferRate));

	    m_db.update("steam_files",
			values,
			"oid = ?",
			new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void writeSteamStatus(final Cryptography cryptography,
				 final int oid,
				 final int readInterval)
    {
	if(cryptography == null || m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("read_interval", cryptography.etmBase64String(readInterval));
	    m_db.update("steam_files",
			values,
			"oid = ?",
			new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }

    public void writeSteamStatus(final String status, final int oid)
    {
	if(m_db == null)
	    return;

	m_db.beginTransactionNonExclusive();

	try
	{
	    ContentValues values = new ContentValues();

	    if(status.isEmpty())
		values.put("status", "deleted");
	    else
		values.put("status", status);

	    m_db.update("steam_files",
			values,
			"oid = ?",
			new String[] {String.valueOf(oid)});
	    m_db.setTransactionSuccessful();
	    Miscellaneous.sendBroadcast("org.purple.smoke.steam_status");
	}
	catch(Exception exception)
	{
	}
	finally
	{
	    m_db.endTransaction();
	}
    }
}
