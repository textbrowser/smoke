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

import android.os.Bundle;
import java.security.KeyPair;
import javax.crypto.SecretKey;

class State
{
    private static Bundle s_bundle = null;
    private static State s_instance = null;

    private State()
    {
	s_bundle = new Bundle();
	setAuthenticated(false);
	setEncryptionKey(null);
	setMacKey(null);
    }

    public static synchronized State getInstance()
    {
	if(s_instance == null)
	    s_instance = new State();

	return s_instance;
    }

    public synchronized boolean isAuthenticated()
    {
	return s_bundle.getChar("is_authenticated") == '1';
    }

    public synchronized void setAuthenticated(boolean state)
    {
	s_bundle.putChar("is_authenticated", state ? '1' : '0');
    }

    public synchronized void setEncryptionKey(SecretKey encryptionKey)
    {
	if(encryptionKey == null)
	    s_bundle.putByteArray("encryption_key", new byte[0]);
	else
	    s_bundle.putByteArray("encryption_key", encryptionKey.getEncoded());
    }

    public synchronized void setMacKey(SecretKey macKey)
    {
	if(macKey == null)
	    s_bundle.putByteArray("mac_key", new byte[0]);
	else
	    s_bundle.putByteArray("mac_key", macKey.getEncoded());
    }

    public synchronized void setPKIEncryptionKey(KeyPair keyPair)
    {
	if(keyPair == null)
	{
	    s_bundle.putByteArray("pki_encryption_private_key", new byte[0]);
	    s_bundle.putByteArray("pki_encryption_public_key", new byte[0]);
	}
	else
	{
	    s_bundle.putByteArray("pki_encryption_private_key",
				  keyPair.getPrivate().getEncoded());
	    s_bundle.putByteArray("pki_encryption_public_key",
				  keyPair.getPublic().getEncoded());
	}
    }
}
