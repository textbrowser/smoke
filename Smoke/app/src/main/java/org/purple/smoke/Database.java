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
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

public class Database extends SQLiteOpenHelper
{
    private final static String DATABASE_NAME = "smoke.db";
    private final static int DATABASE_VERSION = 1;
    private static Database s_instance = null;

    private Database(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public String readSetting(Cryptography cryptography,
			      String name)
    {
	SQLiteDatabase db = getReadableDatabase();

	if(db == null)
	    return "";

	String str = "";

	try
	{
	    Cursor cursor = null;

	    if(cryptography == null)
		cursor = db.rawQuery
		    ("SELECT value FROM settings WHERE name = ?",
		     new String[] {name});

	    if(cursor != null && cursor.moveToFirst())
	    {
		str = cursor.getString(0);
		cursor.close();
	    }
	}
	catch(SQLException exception)
	{
	}

	db.close();
	return str;
    }

    public boolean accountPrepared()
    {
	if(!readSetting(null, "encryptionSalt").isEmpty() &&
	   !readSetting(null, "macSalt").isEmpty() &&
	   !readSetting(null, "saltedPassword").isEmpty())
	    return true;
	else
	    return false;
    }

    public boolean writeNeighbor(Cryptography cryptography,
				 String ipAddress,
				 String ipPort,
				 String scopeId,
				 String version,
				 String transport)
    {
	if(cryptography == null)
	    return false;

	return true;
    }

    public static synchronized Database getInstance(Context context)
    {
	if(s_instance == null)
	    s_instance = new Database(context.getApplicationContext());

	return s_instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
	if(db == null)
	    return;

	String str;

	/*
	** Create the neighbors table.
	*/

	str = "CREATE TABLE IF NOT EXISTS neighbors (" +
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
	catch(SQLException exception)
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
	catch(SQLException exception)
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
	catch(SQLException exception)
	{
	}
    }

    @Override
    public void onDowngrade(SQLiteDatabase db,
			    int oldVersion,
			    int newVersion)
    {
        onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,
			  int oldVersion,
			  int newVersion)
    {
        onCreate(db);
    }

    public void writeSetting(Cryptography cryptography,
			     String name,
			     String value)
	throws SQLException, SQLiteException
    {
	SQLiteDatabase db = getWritableDatabase();

	if(db == null)
	    return;

	String a = name;
	String b = name;
	String c = value;

	if(cryptography != null)
	{
	    byte bytes[] = null;

	    bytes = cryptography.etm(a.getBytes());

	    if(bytes != null)
		a = Base64.encodeToString(bytes, Base64.DEFAULT);
	    else
		a = "";

	    bytes = cryptography.hash(b.getBytes());

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
	    {
		db.close();
		throw new SQLiteException();
	    }
	}

	ContentValues values = new ContentValues();

	values.put("name", a);
	values.put("name_digest", b);
	values.put("value", c);

	/*
	** Content values should prevent SQL injections.
	*/

	try
	{
	    db.replace("settings", null, values);
	}
	catch(SQLException exception)
        {
	    db.close();
	    throw exception;
	}

	db.close();
    }
}
