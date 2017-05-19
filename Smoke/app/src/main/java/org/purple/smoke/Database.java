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
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Patterns;
import android.util.SparseArray;
import android.util.SparseIntArray;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;

public class Database extends SQLiteOpenHelper
{
    private SQLiteDatabase m_db = null;
    private final static Comparator<NeighborElement>
	s_readNeighborsComparator = new Comparator<NeighborElement> ()
	{
	    @Override
	    public int compare(NeighborElement e1, NeighborElement e2)
	    {
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
		/*
		** Sort by name and SipHash identity.
		*/

	    	int i = e1.m_name.compareTo(e2.m_name);

		if(i != 0)
		    return i;

		return e1.m_sipHashId.compareTo(e2.m_sipHashId);
	    }
	};
    private final static String DATABASE_NAME = "smoke.db";
    private final static int DATABASE_VERSION = 1;
    private final static int SIPHASH_STREAM_CREATION_ITERATION_COUNT = 1000;
    private final static int WRITE_PARTICIPANT_TIME_DELTA = 60000; // 60 Seconds
    private static Database s_instance = null;

    private Database(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    private void prepareDb()
    {
	if(m_db == null)
	    try
	    {
		m_db = getWritableDatabase();
	    }
	    catch(Exception exception)
	    {
	    }
    }

    public ArrayList<NeighborElement> readNeighbors(Cryptography cryptography)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	ArrayList<NeighborElement> arrayList = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "bytes_read, " +
		 "bytes_written, " +
		 "echo_queue_size, " +
		 "ip_version, " +
		 "last_error, " +
		 "local_ip_address, " +
		 "local_port, " +
		 "proxy_ip_address, " +
		 "proxy_port, " +
		 "proxy_type, " +
		 "remote_certificate, " +
		 "remote_ip_address, " +
		 "remote_port, " +
		 "remote_scope_id, " +
		 "session_cipher, " +
		 "status, " +
		 "status_control, " +
		 "transport, " +
		 "uptime, " +
		 "OID " +
		 "FROM neighbors", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    NeighborElement neighborElement = new NeighborElement();
		    boolean error = false;

		    for(int i = 0; i < cursor.getColumnCount(); i++)
		    {
			if(i == cursor.getColumnCount() - 1)
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

			    StringBuilder StringBuilder = new StringBuilder();

			    StringBuilder.append("Database::readNeighbors(): ");
			    StringBuilder.append("error on column ");
			    StringBuilder.append(cursor.getColumnName(i));
			    StringBuilder.append(".");
			    writeLog(StringBuilder.toString());
			    break;
			}

			switch(i)
			{
			case 0:
			    neighborElement.m_bytesRead = new String(bytes);
			    break;
			case 1:
			    neighborElement.m_bytesWritten = new String(bytes);
			    break;
			case 2:
			    neighborElement.m_echoQueueSize = new String(bytes);
			    break;
			case 3:
			    neighborElement.m_ipVersion = new String(bytes);
			    break;
			case 4:
			    neighborElement.m_error = new String(bytes);
			    break;
			case 5:
			    neighborElement.m_localIpAddress =
				new String(bytes);
			    break;
			case 6:
			    neighborElement.m_localPort = new String(bytes);
			    break;
			case 7:
			    neighborElement.m_proxyIpAddress =
				new String(bytes);
			    break;
			case 8:
			    neighborElement.m_proxyPort = new String(bytes);
			    break;
			case 9:
			    neighborElement.m_proxyType = new String(bytes);
			    break;
			case 10:
			    neighborElement.m_remoteCertificate =
				Miscellaneous.deepCopy(bytes);
			    break;
			case 11:
			    neighborElement.m_remoteIpAddress =
				new String(bytes);
			    break;
			case 12:
			    neighborElement.m_remotePort = new String(bytes);
			    break;
			case 13:
			    neighborElement.m_remoteScopeId = new String(bytes);
			    break;
			case 14:
			    neighborElement.m_sessionCipher = new String(bytes);
			    break;
			case 15:
			    neighborElement.m_status = new String(bytes);
			    break;
			case 16:
			    neighborElement.m_statusControl = new String(bytes);
			    break;
			case 17:
			    neighborElement.m_transport = new String(bytes);
			    break;
			case 18:
			    neighborElement.m_uptime = new String(bytes);
			    break;
			}
		    }

		    if(!error)
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
	(Cryptography cryptography)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	ArrayList<ParticipantElement> arrayList = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "name, " +
		 "keystream, " +
		 "last_status_timestamp, " +
		 "siphash_id, " +
		 "OID " +
		 "FROM participants", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    ParticipantElement participantElement =
			new ParticipantElement();
		    boolean error = false;

		    for(int i = 0; i < cursor.getColumnCount(); i++)
		    {
			if(i == cursor.getColumnCount() - 1)
			{
			    participantElement.m_oid = cursor.getInt(i);
			    continue;
			}

			byte bytes[] = cryptography.mtd
			    (Base64.decode(cursor.getString(i).getBytes(),
					   Base64.DEFAULT));

			if(bytes == null)
			{
			    error = true;

			    StringBuilder StringBuilder = new StringBuilder();

			    StringBuilder.append
				("Database::readParticipants(): ");
			    StringBuilder.append("error on column ");
			    StringBuilder.append(cursor.getColumnName(i));
			    StringBuilder.append(".");
			    writeLog(StringBuilder.toString());
			    break;
			}

			switch(i)
			{
			case 0:
			    participantElement.m_name = new String(bytes);
			    break;
			case 1:
			    participantElement.m_keyStream =
				Miscellaneous.deepCopy(bytes);
			    break;
			case 2:
			    participantElement.m_lastStatusTimestamp =
				Miscellaneous.byteArrayToLong(bytes);
			    break;
			case 3:
			    participantElement.m_sipHashId = new String
				(bytes, "UTF-8");
			    break;
			}
		    }

		    if(!error)
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

    public ArrayList<SipHashIdElement> readSipHashIds(Cryptography cryptography)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return null;

	ArrayList<SipHashIdElement> arrayList = null;
	Cursor cursor = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "(SELECT encryption_public_key_digest || " +
		 "signature_public_key_digest FROM participants p " +
		 "WHERE p.siphash_id_digest = s.siphash_id_digest) AS c, " +
		 "s.name, " +
		 "s.siphash_id, " +
		 "s.stream, " +
		 "s.OID " +
		 "FROM siphash_ids s ORDER BY s.oid", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		arrayList = new ArrayList<> ();

		while(!cursor.isAfterLast())
		{
		    SipHashIdElement sipHashIdElement = new SipHashIdElement();
		    boolean error = false;

		    for(int i = 0; i < cursor.getColumnCount(); i++)
		    {
			if(i == 0)
			{
			    if(cursor.isNull(i) ||
			       cursor.getString(i).isEmpty())
			    {
				sipHashIdElement.m_epksCompleted = false;
				continue;
			    }

			    String string_a = cursor.getString(i);
			    String string_b = Base64.encodeToString
				(cryptography.hmac("".getBytes()),
				 Base64.DEFAULT);

			    string_b += string_b;
			    sipHashIdElement.m_epksCompleted =
				!string_a.equals(string_b);
			    continue;
			}
			else if(i == cursor.getColumnCount() - 1)
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

			    StringBuilder StringBuilder = new StringBuilder();

			    StringBuilder.append
				("Database::readSipHashIds(): ");
			    StringBuilder.append("error on column ");
			    StringBuilder.append(cursor.getColumnName(i));
			    StringBuilder.append(".");
			    writeLog(StringBuilder.toString());
			    break;
			}

			switch(i)
			{
			case 0:
			    break;
			case 1:
			    sipHashIdElement.m_name = new String(bytes);
			    break;
			case 2:
			    sipHashIdElement.m_sipHashId = new String
				(bytes, "UTF-8");
			    break;
			case 3:
			    sipHashIdElement.m_stream = Miscellaneous.
				deepCopy(bytes);
			    break;
			default:
			    break;
			}
		    }

		    if(!error)
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

    public PublicKey publicKeyForSipHashId(Cryptography cryptography,
					   String sipHashId)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	PublicKey publicKey = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "encryption_public_key " +
		 "FROM participants WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString(cryptography.
					      hmac(sipHashId.toLowerCase().
						   trim().getBytes("UTF-8")),
					      Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    publicKey = KeyFactory.getInstance("RSA").
			generatePublic(new X509EncodedKeySpec(bytes));
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
	prepareDb();

	if(cryptography == null ||
	   digest == null ||
	   digest.length < 0 ||
	   m_db == null)
	    return null;

	Cursor cursor = null;
	PublicKey publicKey = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "signature_public_key " +
		 "FROM participants WHERE encryption_public_key_digest = ?",
		 new String[] {Base64.encodeToString(digest, Base64.DEFAULT)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		byte bytes[] = cryptography.mtd
		    (Base64.decode(cursor.getString(0).getBytes(),
				   Base64.DEFAULT));

		if(bytes != null)
		    for(int i = 0; i < 2; i++)
			try
			{
			    if(i == 0)
				publicKey = KeyFactory.getInstance("EC").
				    generatePublic
				    (new X509EncodedKeySpec(bytes));
			    else
				publicKey = KeyFactory.getInstance("RSA").
				    generatePublic
				    (new X509EncodedKeySpec(bytes));

			    break;
			}
			catch(Exception exception)
			{
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

    public SparseIntArray readNeighborOids()
    {
	prepareDb();

	if(m_db == null)
	    return null;

	Cursor cursor = null;
	SparseIntArray sparseArray = null;

	try
	{
	    cursor = m_db.rawQuery("SELECT OID FROM neighbors", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
		int index = -1;

		sparseArray = new SparseIntArray();

		while(!cursor.isAfterLast())
		{
		    index += 1;
		    sparseArray.append(index, cursor.getInt(0));
		    cursor.moveToNext();
		}
	    }
	}
	catch(Exception exception)
	{
	    if(sparseArray != null)
		sparseArray.clear();

	    sparseArray = null;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return sparseArray;
    }

    public String nameFromSipHashId(Cryptography cryptography, String sipHashId)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return "";

	Cursor cursor = null;
	String name = "";

	try
	{
	    cursor = m_db.rawQuery
		("SELECT name FROM siphash_ids WHERE siphash_id_digest = ?",
		 new String[] {Base64.
			       encodeToString(cryptography.
					      hmac(sipHashId.toLowerCase().
						   trim().getBytes("UTF-8")),
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

    public String readNeighborStatusControl(Cryptography cryptography, int oid)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	String status = "";

	try
	{
	    cursor = m_db.rawQuery
		("SELECT status_control FROM neighbors WHERE OID = ?",
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

    public String readSetting(Cryptography cryptography, String name)
    {
	prepareDb();

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
		}
	}
	catch(Exception exception)
	{
	    str = "";
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return str;
    }

    public String[] nameSipHashIdFromDigest(Cryptography cryptography,
					    byte digest[])
    {
	prepareDb();

	if(cryptography == null ||
	   digest == null ||
	   digest.length < 0 ||
	   m_db == null)
	    return null;

	Cursor cursor = null;
	String array[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT name, siphash_id " +
		 "FROM participants WHERE encryption_public_key_digest = ?",
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
			array[1] = new String(bytes, "UTF-8");
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

    public String[] readOutboundMessage(int oid)
    {
	prepareDb();

	if(m_db == null)
	    return null;

	Cursor cursor = null;
	String array[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT message, OID FROM outbound_queue " +
		 "WHERE neighbor_oid = ? ORDER BY OID",
		 new String[] {String.valueOf(oid)});

	    if(cursor != null && cursor.moveToFirst())
	    {
		array = new String[2];
		array[0] = cursor.getString(0);
		array[1] = String.valueOf(cursor.getInt(1));
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
	prepareDb();

	if(m_db == null)
	    return false;

	Cursor cursor = null;
	boolean contains = false;

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

	return contains;
    }

    public boolean deleteEntry(String oid, String table)
    {
	prepareDb();

	if(m_db == null)
	    return false;

	boolean ok = false;

	try
	{
	    ok = m_db.delete(table, "OID = ?", new String[]{oid}) > 0;
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	return ok;
    }

    public boolean setParticipantKeyStream(Cryptography cryptography,
					   byte keyStream[],
					   int oid)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return false;

	try
	{
	    ContentValues values = new ContentValues();

	    if(keyStream == null || keyStream.length < 0)
		values.put
		    ("keystream",
		     Base64.encodeToString(cryptography.etm("".getBytes()),
					   Base64.DEFAULT));
	    else
		values.put
		    ("keystream",
		     Base64.encodeToString(cryptography.etm(keyStream),
					   Base64.DEFAULT));

	    m_db.update("participants", values, "OID = ?",
			new String[] {String.valueOf(oid)});
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public boolean writeNeighbor(Cryptography cryptography,
				 String proxyIpAddress,
				 String proxyPort,
				 String proxyType,
				 String remoteIpAddress,
				 String remoteIpPort,
				 String remoteIpScopeId,
				 String transport,
				 String version)
    {
	prepareDb();

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

	/*
	** Content values should prevent SQL injections.
	*/

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
	    sparseArray.append(9, "proxy_ip_address");
	    sparseArray.append(10, "proxy_port");
	    sparseArray.append(11, "proxy_type");
	    sparseArray.append(12, "remote_certificate");
	    sparseArray.append(13, "remote_ip_address");
	    sparseArray.append(14, "remote_ip_address_digest");
	    sparseArray.append(15, "remote_port");
            sparseArray.append(16, "remote_port_digest");
            sparseArray.append(17, "remote_scope_id");
            sparseArray.append(18, "session_cipher");
            sparseArray.append(19, "status");
            sparseArray.append(20, "status_control");
            sparseArray.append(21, "transport");
            sparseArray.append(22, "transport_digest");
            sparseArray.append(23, "uptime");
            sparseArray.append(24, "user_defined_digest");

	    /*
	    ** Proxy information.
	    */

	    proxyIpAddress = proxyIpAddress.trim();

	    if(proxyIpAddress.isEmpty())
		proxyPort = "";

	    if(!remoteIpAddress.toLowerCase().trim().matches(".*[a-z].*"))
	    {
		Matcher matcher = Patterns.IP_ADDRESS.matcher
		    (remoteIpAddress.trim());

		if(!matcher.matches())
		{
		    if(version.toLowerCase().equals("ipv4"))
			remoteIpAddress = "0.0.0.0";
		    else
			remoteIpAddress = "0:0:0:0:0:ffff:0:0";
		}
	    }

	    for(int i = 0; i < sparseArray.size(); i++)
	    {
		if(sparseArray.get(i).equals("echo_queue_size"))
		    bytes = cryptography.etm("0".getBytes());
		else if(sparseArray.get(i).equals("ip_version"))
		    bytes = cryptography.etm(version.trim().getBytes());
		else if(sparseArray.get(i).equals("last_error"))
		    bytes = cryptography.etm("".getBytes());
		else if(sparseArray.get(i).equals("local_ip_address_digest"))
		    bytes = cryptography.hmac("".getBytes());
		else if(sparseArray.get(i).equals("local_port_digest"))
		    bytes = cryptography.hmac("".getBytes());
		else if(sparseArray.get(i).equals("proxy_ip_address"))
		    bytes = cryptography.etm(proxyIpAddress.getBytes());
		else if(sparseArray.get(i).equals("proxy_port"))
		    bytes = cryptography.etm(proxyPort.getBytes());
		else if(sparseArray.get(i).equals("proxy_type"))
		    bytes = cryptography.etm(proxyType.getBytes());
		else if(sparseArray.get(i).equals("remote_ip_address"))
		    bytes = cryptography.etm(remoteIpAddress.trim().getBytes());
		else if(sparseArray.get(i).equals("remote_ip_address_digest"))
		    bytes = cryptography.hmac(remoteIpAddress.trim().
					      getBytes());
		else if(sparseArray.get(i).equals("remote_port"))
		    bytes = cryptography.etm(remoteIpPort.trim().getBytes());
		else if(sparseArray.get(i).equals("remote_port_digest"))
		    bytes = cryptography.hmac(remoteIpPort.trim().getBytes());
		else if(sparseArray.get(i).equals("remote_scope_id"))
		    bytes = cryptography.etm(remoteIpScopeId.trim().getBytes());
		else if(sparseArray.get(i).equals("status"))
		    bytes = cryptography.etm("disconnected".getBytes());
		else if(sparseArray.get(i).equals("status_control"))
		    bytes = cryptography.etm("Disconnect".getBytes());
		else if(sparseArray.get(i).equals("transport"))
		    bytes = cryptography.etm(transport.trim().getBytes());
		else if(sparseArray.get(i).equals("transport_digest"))
		    bytes = cryptography.hmac(transport.trim().getBytes());
		else if(sparseArray.get(i).equals("user_defined_digest"))
		    bytes = cryptography.hmac("true".getBytes());
		else
		    bytes = cryptography.etm("".getBytes());

		if(bytes == null)
		{
		    StringBuilder StringBuilder = new StringBuilder();

		    StringBuilder.append
			("Database::writeNeighbor(): error with ");
		    StringBuilder.append(sparseArray.get(i));
		    StringBuilder.append(" field.");
		    writeLog(StringBuilder.toString());
		    throw new Exception();
		}

		String str = Base64.encodeToString(bytes, Base64.DEFAULT);

		values.put(sparseArray.get(i), str);
	    }
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	try
	{
	    if(ok)
		m_db.insert("neighbors", null, values);
	}
	catch(SQLiteConstraintException exception)
	{
	    ok = exception.getMessage().toLowerCase().contains("unique");
	}
	catch(Exception exception)
        {
	    ok = false;
	}

	return ok;
    }

    public boolean writeParticipant(Cryptography cryptography,
				    byte data[])
    {
	prepareDb();

	if(cryptography == null ||
	   data == null ||
	   data.length < 0 ||
	   m_db == null)
	    return false;

	try
	{
	    String strings[] = new String(data).split("\\n");

	    if(strings.length != Messages.EPKS_GROUP_ONE_ELEMENT_COUNT)
		return false;

	    PublicKey publicKey = null;
	    PublicKey signatureKey = null;
	    byte keyType[] = null;
	    byte publicKeySignature[] = null;
	    byte signatureKeySignature[] = null;
	    int ii = 0;

	    for(String string : strings)
		switch(ii)
		{
		case 0:
		    long current = System.currentTimeMillis();
		    long timestamp = Miscellaneous.byteArrayToLong
			(Base64.decode(string.getBytes(), Base64.NO_WRAP));

		    if(current - timestamp < 0)
		    {
			if(timestamp - current > WRITE_PARTICIPANT_TIME_DELTA)
			    return false;
		    }
		    else if(current - timestamp > WRITE_PARTICIPANT_TIME_DELTA)
			return false;

		    ii += 1;
		    break;
		case 1:
		    keyType = Base64.decode
			(string.getBytes(), Base64.NO_WRAP);

		    if(keyType == null ||
		       keyType.length != 1 ||
		       keyType[0] != Messages.CHAT_KEY_TYPE[0])
			return false;

		    ii += 1;
		    break;
		case 2:
		    publicKey = Cryptography.publicKeyFromBytes
			(Base64.decode(string.getBytes(), Base64.NO_WRAP));

		    if(publicKey == null)
			return false;
		    else if(cryptography.
			    compareChatEncryptionPublicKey(publicKey))
			return false;
		    else if(cryptography.
			    compareChatSignaturePublicKey(publicKey))
			return false;

		    ii += 1;
		    break;
		case 3:
		    publicKeySignature = Base64.decode
			(string.getBytes(), Base64.NO_WRAP);

		    if(!Cryptography.verifySignature(publicKey,
						     publicKeySignature,
						     publicKey.getEncoded()))
			return false;

		    ii += 1;
		    break;
		case 4:
		    signatureKey = Cryptography.publicKeyFromBytes
			(Base64.decode(string.getBytes(), Base64.NO_WRAP));

		    if(signatureKey == null)
			return false;
		    else if(cryptography.
			    compareChatEncryptionPublicKey(signatureKey))
			return false;
		    else if(cryptography.
			    compareChatSignaturePublicKey(signatureKey))
			return false;

		    ii += 1;
		    break;
		case 5:
		    signatureKeySignature = Base64.decode
			(string.getBytes(), Base64.NO_WRAP);

		    if(!Cryptography.verifySignature(signatureKey,
						     signatureKeySignature,
						     signatureKey.getEncoded()))
			return false;

		    break;
		}

	    /*
	    ** We shall use the two public keys to generate the
	    ** provider's SipHash ID. If a SipHash ID is not defined,
	    ** we'll reject the data.
	    */

	    String name = "";
	    String sipHashId = Miscellaneous.
		sipHashIdFromData(Miscellaneous.
				  joinByteArrays(publicKey.getEncoded(),
						 signatureKey.getEncoded())).
		toLowerCase();

	    name = nameFromSipHashId(cryptography, sipHashId);

	    if(name.isEmpty())
		return false;

	    ContentValues values = new ContentValues();
	    SparseArray<String> sparseArray = new SparseArray<> ();

	    sparseArray.append(0, "encryption_public_key");
	    sparseArray.append(1, "encryption_public_key_digest");
	    sparseArray.append(2, "function_digest");
	    sparseArray.append(3, "identity");
	    sparseArray.append(4, "keystream");
	    sparseArray.append(5, "last_status_timestamp");
	    sparseArray.append(6, "name");
	    sparseArray.append(7, "signature_public_key");
	    sparseArray.append(8, "signature_public_key_digest");
	    sparseArray.append(9, "siphash_id");
	    sparseArray.append(10, "siphash_id_digest");

	    for(int i = 0; i < sparseArray.size(); i++)
	    {
		byte bytes[] = null;

		if(sparseArray.get(i).equals("encryption_public_key"))
		    bytes = cryptography.etm(publicKey.getEncoded());
		else if(sparseArray.get(i).
			equals("encryption_public_key_digest"))
		    bytes = Cryptography.sha512(publicKey.getEncoded());
		else if(sparseArray.get(i).equals("function_digest"))
		{
		    if(keyType != null &&
		       keyType.length == 1 &&
		       keyType[0] == Messages.CHAT_KEY_TYPE[0])
			bytes = cryptography.hmac("chat".getBytes());
		}
		else if(sparseArray.get(i).equals("identity"))
		    bytes = cryptography.etm("".getBytes());
		else if(sparseArray.get(i).equals("keystream"))
		    bytes = cryptography.etm("".getBytes());
		else if(sparseArray.get(i).equals("last_status_timestamp"))
		    bytes = cryptography.etm("".getBytes());
		else if(sparseArray.get(i).equals("name"))
		    bytes = cryptography.etm(name.getBytes());
		else if(sparseArray.get(i).equals("signature_public_key"))
		    bytes = cryptography.etm(signatureKey.getEncoded());
		else if(sparseArray.get(i).
			equals("signature_public_key_digest"))
		    bytes = Cryptography.sha512(signatureKey.getEncoded());
		else if(sparseArray.get(i).equals("siphash_id"))
		    bytes = cryptography.etm(sipHashId.getBytes("UTF-8"));
		else if(sparseArray.get(i).equals("siphash_id_digest"))
		    bytes = cryptography.hmac(sipHashId.getBytes("UTF-8"));

		if(bytes == null)
		    return false;

		values.put(sparseArray.get(i),
			   Base64.encodeToString(bytes, Base64.DEFAULT));
	    }

	    m_db.insert("participants", null, values);
	}
	catch(SQLiteConstraintException exception)
	{
	    return exception.getMessage().toLowerCase().contains("unique");
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public boolean writeSipHashParticipant(Cryptography cryptography,
					   String name,
					   String sipHashId)
    {
	prepareDb();

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

	/*
	** Content values should prevent SQL injections.
	*/

	try
	{
	    SparseArray<String> sparseArray = new SparseArray<> ();
	    byte bytes[] = null;

	    name = name.trim();

	    if(name.isEmpty())
		name = "unknown";

	    sipHashId = sipHashId.toLowerCase().trim();
	    sparseArray.append(0, "name");
	    sparseArray.append(1, "siphash_id");
	    sparseArray.append(2, "siphash_id_digest");
	    sparseArray.append(3, "stream");

	    for(int i = 0; i < sparseArray.size(); i++)
	    {
		if(sparseArray.get(i).equals("name"))
		    bytes = cryptography.etm(name.getBytes());
		else if(sparseArray.get(i).equals("siphash_id"))
		    bytes = cryptography.etm
			(sipHashId.trim().getBytes("UTF-8"));
		else if(sparseArray.get(i).equals("siphash_id_digest"))
		    bytes = cryptography.hmac
			(sipHashId.trim().getBytes("UTF-8"));
		else
		{
		    byte salt[] = Cryptography.sha512
			(sipHashId.trim().getBytes("UTF-8"));
		    byte temporary[] = Cryptography.
			pbkdf2(salt,
			       sipHashId.toCharArray(),
			       SIPHASH_STREAM_CREATION_ITERATION_COUNT,
			       768); // 8 * (32 + 64) Bits

		    if(temporary != null)
			bytes = cryptography.etm
			    (Cryptography.
			     pbkdf2(salt,
				    new String(temporary, "UTF-8").
				    toCharArray(),
				    SIPHASH_STREAM_CREATION_ITERATION_COUNT,
				    768)); // 8 * (32 + 64) Bits
		}

		if(bytes == null)
		{
		    StringBuilder StringBuilder = new StringBuilder();

		    StringBuilder.append
			("Database::writeSipHashParticipant(): error with ");
		    StringBuilder.append(sparseArray.get(i));
		    StringBuilder.append(" field.");
		    writeLog(StringBuilder.toString());
		    throw new Exception();
		}

		String str = Base64.encodeToString(bytes, Base64.DEFAULT);

		values.put(sparseArray.get(i), str);
	    }
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	try
	{
	    if(ok)
		if(m_db.replace("siphash_ids", null, values) == -1)
		    ok = false;
	}
	catch(Exception exception)
        {
	    ok = false;
	}

	return ok;
    }

    public byte[] neighborRemoteCertificate(Cryptography cryptography,
					    int oid)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return null;

	Cursor cursor = null;
	byte bytes[] = null;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT remote_certificate FROM neighbors WHERE OID = ?",
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
	prepareDb();

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
			       encodeToString(cryptography.
					      hmac(sipHashId.toLowerCase().
						   trim().getBytes("UTF-8")),
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
	prepareDb();

	if(cryptography == null ||
	   digest == null ||
	   digest.length < 0 ||
	   m_db == null)
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

    public int count(String table)
    {
	prepareDb();

	if(m_db == null)
	    return -1;

	Cursor cursor = null;
	int c = 0;

	try
	{
	    StringBuilder StringBuilder = new StringBuilder();

	    StringBuilder.append("SELECT COUNT(*) FROM ");
	    StringBuilder.append(table);
	    cursor = m_db.rawQuery(StringBuilder.toString(), null);

	    if(cursor != null && cursor.moveToFirst())
		c = cursor.getInt(0);
	}
	catch(Exception exception)
	{
	    c = -1;
	}
	finally
	{
	    if(cursor != null)
		cursor.close();
	}

	return c;
    }

    public static synchronized Database getInstance()
    {
	return s_instance; // Should never be null.
    }

    public static synchronized Database getInstance(Context context)
    {
	if(s_instance == null)
	    s_instance = new Database(context.getApplicationContext());

	return s_instance;
    }

    public void enqueueOutboundMessage(String message, int oid)
    {
	prepareDb();

	if(message.trim().isEmpty() || m_db == null)
	    return;

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("message", message);
	    values.put("neighbor_oid", oid);
	    m_db.insert("outbound_queue", null, values);
	}
	catch(Exception exception)
        {
	}
    }

    public void neighborControlStatus(Cryptography cryptography,
				      String controlStatus,
				      String oid)
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return;

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("status_control",
		 Base64.encodeToString(cryptography.
				       etm(controlStatus.trim().getBytes()),
				       Base64.DEFAULT));
	    m_db.update("neighbors", values, "OID = ?", new String[] {oid});
	}
	catch(Exception exception)
	{
	}
    }

    public void neighborRecordCertificate(Cryptography cryptography,
					  String oid,
					  byte certificate[])
    {
	prepareDb();

	if(cryptography == null || m_db == null)
	    return;

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

	    m_db.update("neighbors", values, "OID = ?", new String[] {oid});
	}
	catch(Exception exception)
	{
	}
    }

    @Override
    public void onConfigure(SQLiteDatabase db)
    {
	try
	{
	    db.enableWriteAheadLogging();
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
	String str = "";

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
	*/

	str = "CREATE TABLE IF NOT EXISTS outbound_queue (" +
	    "message TEXT NOT NULL, " +
	    "neighbor_oid INTEGER NOT NULL, " +
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
	    "encryption_public_key_digest TEXT NOT NULL, " +
	    "function_digest, " + // chat, e-mail, etc.
	    "identity TEXT NOT NULL, " +
	    "keystream TEXT NOT NULL, " +
	    "last_status_timestamp TEXT NOT NULL, " +
	    "name TEXT NOT NULL, " +
	    "signature_public_key TEXT NOT NULL, " +
	    "signature_public_key_digest TEXT NOT NULL, " +
	    "siphash_id TEXT NOT NULL, " +
	    "siphash_id_digest TEXT NOT NULL, " +
	    "FOREIGN KEY (siphash_id_digest) REFERENCES " +
	    "siphash_ids(siphash_id_digest) ON DELETE CASCADE, " +
	    "PRIMARY KEY (encryption_public_key_digest, " +
	    "signature_public_key_digest))";

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
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        onCreate(db);
    }

    public void purgeCongestion(int lifetime)
    {
	prepareDb();

	if(m_db == null)
	    return;

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
	}
	catch(Exception exception)
	{
	}
    }

    public void reset()
    {
	prepareDb();

	if(m_db == null)
	    return;

	try
	{
	    m_db.delete("congestion_control", null, null);
	    m_db.delete("log", null, null);
	    m_db.delete("neighbors", null, null);
	    m_db.delete("outbound_queue", null, null);
	    m_db.delete("participants", null, null);
	    m_db.delete("settings", null, null);
	    m_db.delete("siphash_ids", null, null);
	}
	catch(Exception exception)
	{
	}
    }

    public void resetAndDrop()
    {
	reset();

	if(m_db == null)
	    return;

	try
	{
	    m_db.execSQL("DROP TABLE IF EXISTS congestion_control");
	    m_db.execSQL("DROP TABLE IF EXISTS log");
	    m_db.execSQL("DROP TABLE IF EXISTS neighbors");
	    m_db.execSQL("DROP TABLE IF EXISTS outbound_queue");
	    m_db.execSQL("DROP TABLE IF EXISTS participants");
	    m_db.execSQL("DROP TABLE IF EXISTS settings");
	    m_db.execSQL("DROP TABLE IF EXISTS siphash_ids");
	}
	catch(Exception exception)
	{
	}

	onCreate(m_db);
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
	prepareDb();

	if(cryptography == null || m_db == null)
	    return;

	try
	{
	    ContentValues values = new ContentValues();

	    if(!status.equals("connected"))
	    {
		bytesRead = "";
		bytesWritten = "";
		echoQueueSize = "0";
		error = error; // Do not clear the error.
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
	    m_db.update("neighbors", values, "OID = ?", new String[] {oid});
	}
	catch(Exception exception)
	{
	}
    }

    public void writeCallKeys(Cryptography cryptography,
			      String sipHashId,
			      byte keyStream[])
    {
	prepareDb();

	if(cryptography == null ||
	   keyStream == null ||
	   keyStream.length < 0 ||
	   m_db == null)
	    return;

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
				      encodeToString(cryptography.
						     hmac(sipHashId.
							  toLowerCase().trim().
							  getBytes("UTF-8")),
						     Base64.DEFAULT)});

	}
	catch(Exception exception)
        {
	}
    }

    public void writeCongestionDigest(long value)
    {
	prepareDb();

	if(m_db == null)
	    return;

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("digest",
		 Base64.encodeToString(Miscellaneous.
				       longToByteArray(value), Base64.DEFAULT));
	    m_db.insert("congestion_control", null, values);
	}
	catch(Exception exception)
        {
	}
    }

    public void writeLog(String event)
    {
	prepareDb();

	if(m_db == null)
	    return;

	try
	{
	    ContentValues values = new ContentValues();

	    values.put("event", event.trim());
	    m_db.insert("log", null, values);
	}
	catch(Exception exception)
        {
	}
    }

    public void writeSetting(Cryptography cryptography,
			     String name,
			     String value)
    {
	prepareDb();

	if(m_db == null)
	    return;

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
	}
	catch(Exception exception)
        {
	}
    }
}
