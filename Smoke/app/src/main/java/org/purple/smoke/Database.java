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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Patterns;
import android.util.SparseArray;
import android.util.SparseIntArray;
import java.util.regex.Matcher;

public class Database extends SQLiteOpenHelper
{
    private SQLiteDatabase m_db = null;
    private final static String DATABASE_NAME = "smoke.db";
    private final static int DATABASE_VERSION = 1;
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

    public SparseArray<NeighborElement> readNeighbors(Cryptography cryptography)
    {
	if(cryptography == null)
	    return null;

	prepareDb();

	if(m_db == null)
	    return null;

	SparseArray<NeighborElement> sparseArray = null;
	Cursor cursor = null;
	int index = -1;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT " +
		 "bytes_read, " +
		 "bytes_written, " +
		 "ip_version, " +
		 "local_ip_address, " +
		 "local_port, " +
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
		sparseArray = new SparseArray<> ();

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

			byte bytes[] = Base64.decode
			    (cursor.getString(i).getBytes(), Base64.DEFAULT);

			bytes = cryptography.mtd(bytes);

			if(bytes == null)
			{
			    error = true;

			    StringBuffer stringBuffer = new StringBuffer();

			    stringBuffer.append("Database::readNeighbors(): ");
			    stringBuffer.append("error on column ");
			    stringBuffer.append(cursor.getColumnName(i));
			    stringBuffer.append(".");
			    writeLog(stringBuffer.toString());
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
			    neighborElement.m_ipVersion = new String(bytes);
			    break;
			case 3:
			    neighborElement.m_localIpAddress =
				new String(bytes);
			    break;
			case 4:
			    neighborElement.m_localPort = new String(bytes);
			    break;
			case 5:
			    neighborElement.m_remoteCertificate =
				new String(bytes);
			    break;
			case 6:
			    neighborElement.m_remoteIpAddress =
				new String(bytes);
			    break;
			case 7:
			    neighborElement.m_remotePort = new String(bytes);
			    break;
			case 8:
			    neighborElement.m_remoteScopeId = new String(bytes);
			    break;
			case 9:
			    neighborElement.m_sessionCipher = new String(bytes);
			    break;
			case 10:
			    neighborElement.m_status = new String(bytes);
			    break;
			case 11:
			    neighborElement.m_statusControl = new String(bytes);
			    break;
			case 12:
			    neighborElement.m_transport = new String(bytes);
			    break;
			case 13:
			    neighborElement.m_uptime = new String(bytes);
			    break;
			}
		    }

		    if(!error)
		    {
			index += 1;
			sparseArray.append(index, neighborElement);
		    }

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

    public SparseIntArray readNeighborOids()
    {
	prepareDb();

	if(m_db == null)
	    return null;

	SparseIntArray sparseArray = null;
	Cursor cursor = null;
	int index = -1;

	try
	{
	    cursor = m_db.rawQuery
		("SELECT OID FROM neighbors", null);

	    if(cursor != null && cursor.moveToFirst())
	    {
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

    public String readNeighborStatusControl(Cryptography cryptography, int oid)
    {
	if(cryptography == null)
	    return null;

	prepareDb();

	if(m_db == null)
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
		byte bytes[] = Base64.decode
		    (cursor.getString(0).getBytes(), Base64.DEFAULT);

		bytes = cryptography.mtd(bytes);

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
		    byte bytes[] = Base64.decode
			(cursor.getString(0).getBytes(), Base64.DEFAULT);

		    bytes = cryptography.mtd(bytes);

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

    public boolean accountPrepared()
    {
	return !readSetting(null, "encryptionSalt").isEmpty() &&
	    !readSetting(null, "macSalt").isEmpty() &&
	    !readSetting(null, "saltedPassword").isEmpty();
    }

    public boolean deleteEntry(String oid, String table)
    {
	prepareDb();

	if(m_db == null)
	    return false;

	boolean ok = true;

	try
	{
	    StringBuffer stringBuffer = new StringBuffer();

	    stringBuffer.append("DELETE FROM ");
	    stringBuffer.append(table);
	    stringBuffer.append(" WHERE OID = ?");
	    m_db.execSQL(stringBuffer.toString(), new String[] {oid});
	}
	catch(Exception exception)
	{
	    ok = false;
	}

	return ok;
    }

    public boolean writeNeighbor(Cryptography cryptography,
				 String remoteIpAddress,
				 String remoteIpPort,
				 String remoteIpScopeId,
				 String transport,
				 String version)
    {
	if(cryptography == null)
	    return false;

	prepareDb();

	if(m_db == null)
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
	    sparseArray.append(2, "ip_version");
	    sparseArray.append(3, "local_ip_address");
	    sparseArray.append(4, "local_ip_address_digest");
	    sparseArray.append(5, "local_port");
	    sparseArray.append(6, "local_port_digest");
	    sparseArray.append(7, "remote_certificate");
	    sparseArray.append(8, "remote_ip_address");
	    sparseArray.append(9, "remote_ip_address_digest");
	    sparseArray.append(10, "remote_port");
            sparseArray.append(11, "remote_port_digest");
            sparseArray.append(12, "remote_scope_id");
            sparseArray.append(13, "session_cipher");
            sparseArray.append(14, "status");
            sparseArray.append(15, "status_control");
            sparseArray.append(16, "transport");
            sparseArray.append(17, "transport_digest");
            sparseArray.append(18, "uptime");
            sparseArray.append(19, "user_defined_digest");

	    Matcher matcher = Patterns.IP_ADDRESS.matcher
		(remoteIpAddress.trim());

	    if(!matcher.matches())
	    {
		if(version.toLowerCase().equals("ipv4"))
		    remoteIpAddress = "0.0.0.0";
		else
		    remoteIpAddress = "0:0:0:0:0:ffff:0:0";
	    }

	    for(int i = 0; i < sparseArray.size(); i++)
	    {
		if(sparseArray.get(i).equals("ip_version"))
		    bytes = cryptography.etm(version.trim().getBytes());
		else if(sparseArray.get(i).equals("local_ip_address_digest"))
		    bytes = cryptography.hmac("".getBytes());
		else if(sparseArray.get(i).equals("local_port_digest"))
		    bytes = cryptography.hmac("".getBytes());
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
		    StringBuffer stringBuffer = new StringBuffer();

		    stringBuffer.append
			("Database::writeNeighbor(): error with ");
		    stringBuffer.append(sparseArray.get(i));
		    stringBuffer.append(" field.");
		    writeLog(stringBuffer.toString());
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
		if(m_db.replace("neighbors", null, values) == -1)
		    ok = false;
	}
	catch(Exception exception)
        {
	    ok = false;
	}

	return ok;
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
	    StringBuffer stringBuffer = new StringBuffer();

	    stringBuffer.append("SELECT COUNT(*) FROM ");
	    stringBuffer.append(table);
	    cursor = m_db.rawQuery(stringBuffer.toString(), null);

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
	if(message.trim().isEmpty())
	    return;

	prepareDb();

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
	if(cryptography == null)
	    return;

	prepareDb();

	if(m_db == null)
	    return;

	try
	{
	    ContentValues values = new ContentValues();

	    values.put
		("status_control",
		 Base64.encodeToString(cryptography.
				       etm(controlStatus.trim().getBytes()),
				       Base64.DEFAULT));
	    m_db.update("neighbors", values, "oid = ?", new String[] {oid});
	}
	catch(Exception exception)
	{
	}
    }

    public void neighborRecordCertificate(Cryptography cryptography,
					  String oid,
					  byte certificate[])
    {
	if(cryptography == null)
	    return;

	prepareDb();

	if(m_db == null)
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

	    m_db.update("neighbors", values, "oid = ?", new String[] {oid});
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
	    db.execSQL("PRAGMA secure_delete = True", null);
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
	    "ip_version TEXT NOT NULL, " +
	    "local_ip_address TEXT NOT NULL, " +
	    "local_ip_address_digest TEXT NOT NULL, " +
	    "local_port TEXT NOT NULL, " +
	    "local_port_digest TEXT NOT NULL, " +
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
	    "PRIMARY KEY (local_ip_address_digest, " +
	    "local_port_digest, " +
	    "remote_ip_address_digest, " +
	    "remote_port_digest, " +
	    "transport_digest))";

	try
	{
	    db.execSQL(str);
	}
	catch(Exception exception)
	{
	}

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
	    "name TEXT NOT NULL, " +
	    "name_overridden TEXT NOT NULL, " +
	    "encryption_public_key TEXT NOT NULL, " +
	    "encryption_public_key_digest TEXT NOT NULL, " +
	    "forward_secrecy_magnet TEXT NOT NULL, " +
	    "function_digest, " + // chat, e-mail, etc.
	    "gemini_magnet TEXT NOT NULL, " +
	    "signature_public_key TEXT NOT NULL, " +
	    "signature_public_key_digest TEXT NOT NULL, " +
	    "status TEXT NOT NULL, " +
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
	    m_db.delete("participants", null, null);
	    m_db.delete("settings", null, null);
	}
	catch(Exception exception)
	{
	    writeLog("Database::reset() failure.");
	}
    }

    public void saveNeighborInformation(Cryptography cryptography,
					String bytesRead,
					String bytesWritten,
					String ipAddress,
					String ipPort,
					String peerCertificate,
					String sessionCipher,
					String status,
					String uptime,
					String oid)
    {
	if(cryptography == null)
	    return;

	prepareDb();

	if(m_db == null)
	    return;

	try
	{
	    ContentValues values = new ContentValues();

	    if(!status.equals("connected"))
	    {
		bytesRead = "0";
		bytesWritten = "0";
		ipAddress = "";
		ipPort = "0";
		peerCertificate = "";
		sessionCipher = "";
		uptime = "0";
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
				       etm(ipPort.trim().getBytes()),
				       Base64.DEFAULT));
	    values.put
		("remote_certificate",
		 Base64.encodeToString(cryptography.
				       etm(peerCertificate.trim().getBytes()),
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
		    throw new SQLiteException();
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
