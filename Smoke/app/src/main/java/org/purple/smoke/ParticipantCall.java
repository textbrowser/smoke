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

import java.security.KeyPair;

public class ParticipantCall
{
    public Algorithms m_algorithm = Algorithms.RSA;
    public KeyPair m_keyPair = null;
    public String m_sipHashId = "";
    public boolean m_arson = false;
    public int m_participantOid = -1;
    public long m_startTime = -1L; // Calls expire.
    public enum Algorithms {MCELIECE, RSA}

    public ParticipantCall(Algorithms algorithm, String sipHashId)
    {
	m_algorithm = algorithm;
	m_arson = true;
	m_sipHashId = sipHashId;
	m_startTime = System.nanoTime();
    }

    public ParticipantCall(Algorithms algorithm,
			   String sipHashId,
			   int participantOid)
    {
	m_algorithm = algorithm;
	m_participantOid = participantOid;
	m_sipHashId = sipHashId;
	m_startTime = System.nanoTime();
    }

    public void preparePrivatePublicKey()
    {
	if(m_keyPair != null)
	    return;

	try
	{
	    switch(m_algorithm)
	    {
	    case MCELIECE:
		m_keyPair = Cryptography.generatePrivatePublicKeyPair
		    (Cryptography.PARTICIPANT_CALL_MCELIECE_KEY_SIZE, 0, 0);
		break;
	    case RSA:
		m_keyPair = Cryptography.generatePrivatePublicKeyPair
		    ("RSA", Cryptography.PARTICIPANT_CALL_RSA_KEY_SIZE, 0);
		break;
	    default:
		break;
	    }
	}
	catch(Exception exception)
	{
	    m_keyPair = null;
	}
    }
}
