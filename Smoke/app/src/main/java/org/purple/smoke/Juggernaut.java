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

import android.util.Base64;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant;
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroup;
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroups;
import org.bouncycastle.crypto.agreement.jpake.JPAKERound1Payload;
import org.bouncycastle.crypto.agreement.jpake.JPAKERound2Payload;
import org.bouncycastle.crypto.digests.SHA512Digest;

public class Juggernaut
{
    private JPAKEParticipant m_participant = null;
    private final Digest m_digest = new SHA512Digest();
    private final SecureRandom m_random = new SecureRandom();
    private final static JPAKEPrimeOrderGroup s_group =
	JPAKEPrimeOrderGroups.NIST_3072;

    Juggernaut()
    {
    }

    public String payload1Stream()
    {
	try
	{
	    JPAKERound1Payload payload = m_participant.
		createRound1PayloadToSend();
	    StringBuffer stringBuffer = new StringBuffer();

	    stringBuffer.append
		(Base64.encodeToString(payload.getGx1().toByteArray(),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");
	    stringBuffer.append
		(Base64.encodeToString(payload.getGx2().toByteArray(),
				       Base64.NO_WRAP));

	    BigInteger array[] = payload.getKnowledgeProofForX1();

	    stringBuffer.append
		(Base64.encodeToString(String.valueOf(array.length).getBytes(),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    for(BigInteger b : array)
	    {
		stringBuffer.append
		    (Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP));
		stringBuffer.append("\n");
	    }

	    array = payload.getKnowledgeProofForX2();
	    stringBuffer.append
		(Base64.encodeToString(String.valueOf(array.length).getBytes(),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    for(BigInteger b : array)
	    {
		stringBuffer.append
		    (Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP));
		stringBuffer.append("\n");
	    }

	    stringBuffer.append
		(Base64.encodeToString(payload.getParticipantId().getBytes(),
				       Base64.NO_WRAP));
	    return stringBuffer.toString();
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public String payload2Stream()
    {
	try
	{
	    JPAKERound2Payload payload = m_participant.
		createRound2PayloadToSend();
	    StringBuffer stringBuffer = new StringBuffer();

	    stringBuffer.append
		(Base64.encodeToString(payload.getA().toByteArray(),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    BigInteger array[] = payload.getKnowledgeProofForX2s();

	    stringBuffer.append
		(Base64.encodeToString(String.valueOf(array.length).getBytes(),
				       Base64.NO_WRAP));
	    stringBuffer.append("\n");

	    for(BigInteger b : array)
	    {
		stringBuffer.append
		    (Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP));
		stringBuffer.append("\n");
	    }

	    stringBuffer.append
		(Base64.encodeToString(payload.getParticipantId().getBytes(),
				       Base64.NO_WRAP));
	    return stringBuffer.toString();
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    public boolean validatePayload1(String strings[])
    {
	try
	{
	    BigInteger gx1 = null;
	    BigInteger gx2 = null;
	    BigInteger kpx1[] = null;
	    BigInteger kpx2[] = null;
	    String participantId = "";
	    byte bytes[] = null;

	    /*
	    ** strings[0]     - gx1
	    ** strings[1]     - gx2
	    ** strings[2]     - length of kpx1
	    ** strings[3]     - kpx1[0]
	    ** ...
	    ** strings[n]     - length of kpx2
	    ** strings[n + 1] - kpx2[0]
	    ** ...
	    ** strings[o]     - participant identity
	    */

	    bytes = Base64.decode(strings[0], Base64.NO_WRAP);
	    gx1 = new BigInteger(bytes);
	    bytes = Base64.decode(strings[1], Base64.NO_WRAP);
	    gx2 = new BigInteger(bytes);
	    bytes = Base64.decode(strings[2], Base64.NO_WRAP);
	    kpx1 = new BigInteger[Integer.parseInt(new String(bytes))];

	    for(int i = 0; i < kpx1.length; i++)
	    {
		bytes = Base64.decode(strings[i + 3], Base64.NO_WRAP);
		kpx1[i] = new BigInteger(bytes);
	    }

	    bytes = Base64.decode(strings[kpx1.length + 3], Base64.NO_WRAP);
	    kpx2 = new BigInteger[Integer.parseInt(new String(bytes))];

	    for(int i = 0; i < kpx2.length; i++)
	    {
		bytes = Base64.decode
		    (strings[i + kpx1.length + 4], Base64.NO_WRAP);
		kpx2[i] = new BigInteger(bytes);
	    }

	    participantId = new String
		(Base64.decode(strings[kpx1.length + kpx2.length + 4],
			       Base64.NO_WRAP));

	    JPAKERound1Payload payload = new JPAKERound1Payload
		(participantId, gx1, gx2, kpx1, kpx2);

	    m_participant.validateRound1PayloadReceived(payload);
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public boolean validatePayload2(String strings[])
    {
	try
	{
	    BigInteger a = null;
	    BigInteger kpx2s[] = null;
	    String participantId = "";
	    byte bytes[] = null;

	    /*
	    ** strings[0]     - a
	    ** strings[1]     - length of kpx2s
	    ** strings[2]     - kpx2s[0]
	    ** ...
	    ** strings[n]     - participant identity
	    */

	    bytes = Base64.decode(strings[0], Base64.NO_WRAP);
	    a = new BigInteger(bytes);
	    bytes = Base64.decode(strings[1], Base64.NO_WRAP);
	    kpx2s = new BigInteger[Integer.parseInt(new String(bytes))];

	    for(int i = 0; i < kpx2s.length; i++)
	    {
		bytes = Base64.decode(strings[i + 2], Base64.NO_WRAP);
		kpx2s[i] = new BigInteger(bytes);
	    }

	    participantId = new String
		(Base64.decode(strings[kpx2s.length + 2], Base64.NO_WRAP));

	    JPAKERound2Payload payload = new JPAKERound2Payload
		(participantId, a, kpx2s);

	    m_participant.validateRound2PayloadReceived(payload);
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public void setSecret(String secret)
    {
	try
	{
	    m_participant = new JPAKEParticipant
		("me", secret.toCharArray(), s_group, m_digest, m_random);
	}
	catch(Exception exception)
	{
	    m_participant = null;
	}
    }
}
