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
import android.util.Log;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant;
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroup;
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroups;
import org.bouncycastle.crypto.agreement.jpake.JPAKERound1Payload;
import org.bouncycastle.crypto.agreement.jpake.JPAKERound2Payload;
import org.bouncycastle.crypto.agreement.jpake.JPAKERound3Payload;
import org.bouncycastle.crypto.digests.SHA512Digest;

public class Juggernaut
{
    private BigInteger m_keyingMaterial = null;
    private JPAKEParticipant m_participant = null;
    private boolean m_isJuggerKnot = false;
    private final Digest m_digest = new SHA512Digest();
    private final SecureRandom m_random = new SecureRandom();
    private final static JPAKEPrimeOrderGroup s_group =
	JPAKEPrimeOrderGroups.NIST_3072;
    private final static int ITERATION_COUNT = 4096;
    private long m_lastEventTime = 0L;

    Juggernaut(String participantId, String secret, boolean isJuggerKnot)
    {
	m_isJuggerKnot = isJuggerKnot;
	m_lastEventTime = System.currentTimeMillis();

	try
	{
	    m_participant = new JPAKEParticipant
		(participantId,
		 secret.toCharArray(),
		 s_group,
		 m_digest,
		 m_random);
	}
	catch(Exception exception)
	{
	    m_participant = null;
	}
    }

    private BigInteger keyingMaterial()
    {
	m_lastEventTime = System.currentTimeMillis();

	try
	{
	    if(m_keyingMaterial == null)
		m_keyingMaterial = m_participant.calculateKeyingMaterial();
	}
	catch(Exception exception)
	{
	    m_keyingMaterial = null;
	}

	return m_keyingMaterial;
    }

    private String payload1Stream()
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
	    stringBuffer.append("\n");

	    BigInteger[] array = payload.getKnowledgeProofForX1();

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

    private String payload2Stream()
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

	    BigInteger[] array = payload.getKnowledgeProofForX2s();

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

    private String payload3Stream(BigInteger keyingMaterial)
    {
	try
	{
	    JPAKERound3Payload payload = m_participant.
		createRound3PayloadToSend(keyingMaterial);
	    String string = Base64.
		encodeToString(payload.getMacTag().toByteArray(),
			       Base64.NO_WRAP) +
                "\n" +
                Base64.encodeToString(payload.getParticipantId().getBytes(),
				      Base64.NO_WRAP);

	    return string;
	}
	catch(Exception exception)
	{
	}

	return "";
    }

    private boolean validatePayload1(String[] strings)
    {
	try
	{
	    BigInteger gx1 = null;
	    BigInteger gx2 = null;
	    BigInteger[] kpx1 = null;
	    BigInteger[] kpx2 = null;
	    String participantId = "";
	    byte[] bytes = null;

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
			       Base64.NO_WRAP), StandardCharsets.UTF_8);

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

    private boolean validatePayload2(String[] strings)
    {
	try
	{
	    BigInteger a = null;
	    BigInteger[] kpx2s = null;
	    String participantId = "";
	    byte[] bytes = null;

	    /*
	    ** strings[0] - a
	    ** strings[1] - length of kpx2s
	    ** strings[2] - kpx2s[0]
	    ** ...
	    ** strings[n] - participant identity
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
		(Base64.decode(strings[kpx2s.length + 2], Base64.NO_WRAP),
		 StandardCharsets.UTF_8);

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

    private boolean validatePayload3(BigInteger keyingMaterial,
                                     String[] strings)
    {
	try
	{
	    BigInteger macTag = null;
	    String participantId = "";
	    byte[] bytes = null;

	    /*
	    ** strings[0] - mac tag
	    ** strings[1] - participant identity
	    */

	    bytes = Base64.decode(strings[0], Base64.NO_WRAP);
	    macTag = new BigInteger(bytes);
	    bytes = Base64.decode(strings[1], Base64.NO_WRAP);
	    participantId = new String(bytes, StandardCharsets.UTF_8);

	    JPAKERound3Payload payload = new JPAKERound3Payload
		(participantId, macTag);

	    m_participant.validateRound3PayloadReceived
		(payload, keyingMaterial);
	}
	catch(Exception exception)
	{
	    return false;
	}

	return true;
    }

    public String next(String payload)
    {
	m_lastEventTime = System.currentTimeMillis();

	String string = "";

	switch(m_participant.getState())
	{
	case JPAKEParticipant.STATE_INITIALIZED:
	    string = payload1Stream();
	    break;
	case JPAKEParticipant.STATE_ROUND_1_CREATED:
	    if(payload != null && validatePayload1(payload.split("\\n")))
		string = payload2Stream();

	    break;
	case JPAKEParticipant.STATE_ROUND_2_CREATED:
	    if(payload != null && validatePayload2(payload.split("\\n")))
		string = payload3Stream(keyingMaterial());

	    break;
	case JPAKEParticipant.STATE_ROUND_3_CREATED:
	    if(payload != null)
		validatePayload3(keyingMaterial(), payload.split("\\n"));

	    break;
	default:
	    break;
	}

	return string;
    }

    public boolean isJuggerKnot()
    {
	return m_isJuggerKnot;
    }

    public byte[] deriveSessionCredentials()
    {
	if(m_isJuggerKnot)
	    try
	    {
		String string = Base64.encodeToString
		    (m_keyingMaterial.toByteArray(), Base64.NO_WRAP);
		byte[] bytes = Cryptography.pbkdf2
		    (Cryptography.
		     sha512(string.getBytes(StandardCharsets.UTF_8)),
		     string.toCharArray(),
		     ITERATION_COUNT,
		     160); // SHA-1

		if(bytes != null)
		    bytes = Cryptography.pbkdf2
			(Cryptography.
			 sha512(string.getBytes(StandardCharsets.UTF_8)),
			 string.toCharArray(),
			 1,
			 Cryptography.CIPHER_HASH_KEYS_LENGTH * 8);

		return bytes;
	    }
	    catch(Exception exception)
	    {
		return null;
	    }
	else
	    return null;
    }

    public int state()
    {
	return m_participant.getState();
    }

    public long lastEventTime()
    {
	return m_lastEventTime;
    }

    public static String stateToText(int state)
    {
	switch(state)
	{
	case JPAKEParticipant.STATE_INITIALIZED:
	    return "initialized";
	case JPAKEParticipant.STATE_KEY_CALCULATED:
	    return "key calculated";
	case JPAKEParticipant.STATE_ROUND_1_CREATED:
	    return "round 1 created";
	case JPAKEParticipant.STATE_ROUND_1_VALIDATED:
	    return "round 1 validated";
	case JPAKEParticipant.STATE_ROUND_2_CREATED:
	    return "round 2 created";
	case JPAKEParticipant.STATE_ROUND_2_VALIDATED:
	    return "round 2 validated";
	case JPAKEParticipant.STATE_ROUND_3_CREATED:
	    return "round 3 created";
	case JPAKEParticipant.STATE_ROUND_3_VALIDATED:
	    return "round 3 validated";
	default:
	    return "unknown";
	}
    }

    public static void test1()
    {
	// Must fail!

	Juggernaut juggernaut1 = new Juggernaut("x", "The Juggernaut!", false);
	Juggernaut juggernaut2 = new Juggernaut("x", "The Juggernaut!", false);
	String payload1 = juggernaut1.payload1Stream();
	String payload2 = juggernaut2.payload1Stream();
	boolean ok1 = false;
	boolean ok2 = false;

	/*
	** Payload 1
	*/

	ok1 = juggernaut1.validatePayload1(payload2.split("\\n"));
	Log.e("test1: Participant a validated payload1?", String.valueOf(ok1));
	ok2 = juggernaut2.validatePayload1(payload1.split("\\n"));
	Log.e("test1: Participant b validated payload1?", String.valueOf(ok2));

	/*
	** Payload 2
	*/

	payload1 = juggernaut1.payload2Stream();
	payload2 = juggernaut2.payload2Stream();
	ok1 = juggernaut1.validatePayload2(payload2.split("\\n"));
	Log.e("test1: Participant a validated payload2?", String.valueOf(ok1));
	ok2 = juggernaut2.validatePayload2(payload1.split("\\n"));
	Log.e("test1: Participant b validated payload2?", String.valueOf(ok2));

	/*
	** Payload 3
	*/

	payload1 = juggernaut1.payload3Stream(juggernaut1.keyingMaterial());
	payload2 = juggernaut2.payload3Stream(juggernaut2.keyingMaterial());
	ok1 = juggernaut1.validatePayload3
	    (juggernaut1.keyingMaterial(), payload2.split("\\n"));
	Log.e("test1: Participant a validated payload3?", String.valueOf(ok1));
	ok2 = juggernaut2.validatePayload3
	    (juggernaut2.keyingMaterial(), payload1.split("\\n"));
	Log.e("test1: Participant b validated payload3?", String.valueOf(ok2));
    }

    public static void test2()
    {
	// Must fail!

	Juggernaut juggernaut1 = new Juggernaut("a", "The Juggernaut!", false);
	Juggernaut juggernaut2 = new Juggernaut("b", "The Juggernaut.", false);
	String payload1 = juggernaut1.payload1Stream();
	String payload2 = juggernaut2.payload1Stream();
	boolean ok1 = false;
	boolean ok2 = false;

	/*
	** Payload 1
	*/

	ok1 = juggernaut1.validatePayload1(payload2.split("\\n"));
	Log.e("test2: Participant a validated payload1?", String.valueOf(ok1));
	ok2 = juggernaut2.validatePayload1(payload1.split("\\n"));
	Log.e("test2: Participant b validated payload1?", String.valueOf(ok2));

	/*
	** Payload 2
	*/

	payload1 = juggernaut1.payload2Stream();
	payload2 = juggernaut2.payload2Stream();
	ok1 = juggernaut1.validatePayload2(payload2.split("\\n"));
	Log.e("test2: Participant a validated payload2?", String.valueOf(ok1));
	ok2 = juggernaut2.validatePayload2(payload1.split("\\n"));
	Log.e("test2: Participant b validated payload2?", String.valueOf(ok2));

	/*
	** Payload 3
	*/

	payload1 = juggernaut1.payload3Stream(juggernaut1.keyingMaterial());
	payload2 = juggernaut2.payload3Stream(juggernaut2.keyingMaterial());
	ok1 = juggernaut1.validatePayload3
	    (juggernaut1.keyingMaterial(), payload2.split("\\n"));
	Log.e("test2: Participant a validated payload3?", String.valueOf(ok1));
	ok2 = juggernaut2.validatePayload3
	    (juggernaut2.keyingMaterial(), payload1.split("\\n"));
	Log.e("test2: Participant b validated payload3?", String.valueOf(ok2));
    }

    public static void test3()
    {
	// Must pass!

	Juggernaut juggernaut1 = new Juggernaut("a", "The Juggernaut!", false);
	Juggernaut juggernaut2 = new Juggernaut("b", "The Juggernaut!", false);

	/*
	** Participants initialize. Send payloads.
	*/

	String payload1a = juggernaut1.next(null); // STATE_INITIALIZED
	String payload2a = juggernaut2.next(null); // STATE_INITIALIZED

	/*
	** Validate state 1, create state 2. Send payloads.
	*/

	String payload1b = juggernaut1.next
	    (payload2a); // STATE_ROUND_2_CREATED
	String payload2b = juggernaut2.next
	    (payload1a); // STATE_ROUND_2_CREATED

	/*
	** Validate state 2, create keying material, create state 3.
	** Send payloads.
	*/

	String payload1c = juggernaut1.next
	    (payload2b); // STATE_ROUND_3_CREATED
	String payload2c = juggernaut2.next
	    (payload1b); // STATE_ROUND_3_CREATED

	/*
	** Validate state 3.
	*/

	juggernaut1.next(payload2c); // STATE_3_VALIDATED
	juggernaut2.next(payload1c); // STATE_3_VALIDATED
	Log.e(String.valueOf(juggernaut1.state()), "test3: Participant a state?");
	Log.e(String.valueOf(juggernaut2.state()), "test3: Participant b state?");
    }
}
