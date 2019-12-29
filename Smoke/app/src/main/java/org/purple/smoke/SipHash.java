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

/*
** Implementation of https://131002.net/siphash.
*/

package org.purple.smoke;

public class SipHash
{
    private final static int C_ROUNDS[] = {2, 4};
    private final static int D_ROUNDS[] = {4, 8};
    private final static long C0 = 0x736f6d6570736575L;
    private final static long C1 = 0x646f72616e646f6dL;
    private final static long C2 = 0x6c7967656e657261L;
    private final static long C3 = 0x7465646279746573L;
    private byte m_key[] = null;
    private int m_c_rounds_index = 1;
    private int m_d_rounds_index = 1;
    private long m_v0 = 0;
    private long m_v1 = 0;
    private long m_v2 = 0;
    private long m_v3 = 0;
    public final static int KEY_LENGTH = 16; // Bytes.

    private long byteArrayToLong(byte bytes[], int offset)
    {
	if(bytes == null || (bytes.length - offset) < Long.BYTES)
	    return 0;

	long value = 0;

	value |= (((long) bytes[0 + offset]) & 0xff) << (Long.BYTES * 0);
	value |= (((long) bytes[1 + offset]) & 0xff) << (Long.BYTES * 1);
	value |= (((long) bytes[2 + offset]) & 0xff) << (Long.BYTES * 2);
	value |= (((long) bytes[3 + offset]) & 0xff) << (Long.BYTES * 3);
	value |= (((long) bytes[4 + offset]) & 0xff) << (Long.BYTES * 4);
	value |= (((long) bytes[5 + offset]) & 0xff) << (Long.BYTES * 5);
	value |= (((long) bytes[6 + offset]) & 0xff) << (Long.BYTES * 6);
	value |= (((long) bytes[7 + offset]) & 0xff) << (Long.BYTES * 7);
	return value;
    }

    private long rotl(long x, long b)
    {
	return (x << b) | (x >>> (64 - b));
    }

    private void round()
    {
	m_v0 += m_v1;
	m_v1 = rotl(m_v1, 13);
	m_v1 ^= m_v0;
	m_v0 = rotl(m_v0, 32);
	m_v2 += m_v3;
	m_v3 = rotl(m_v3, 16);
	m_v3 ^= m_v2;
	m_v2 += m_v1;
	m_v1 = rotl(m_v1, 17);
	m_v1 ^= m_v2;
	m_v2 = rotl(m_v2, 32);
	m_v0 += m_v3;
	m_v3 = rotl(m_v3, 21);
	m_v3 ^= m_v0;
    }

    public SipHash()
    {
    }

    public SipHash(byte key[])
    {
	if(key == null || key.length != KEY_LENGTH)
	    return;

	m_key = key;
    }

    public SipHash(byte key[], int c_rounds_index, int d_rounds_index)
    {
	if(key == null || key.length != KEY_LENGTH)
	    return;

	if(c_rounds_index >= 0 && c_rounds_index < C_ROUNDS.length)
	    m_c_rounds_index = c_rounds_index;

	if(d_rounds_index >= 0 && d_rounds_index < D_ROUNDS.length)
	    m_d_rounds_index = d_rounds_index;

	m_key = key;
    }

    public long hmac(byte data[])
    {
	return hmac(data, m_key);
    }

    @SuppressWarnings("fallthrough")
    public synchronized long hmac(byte data[], byte key[])
    {
	if(data == null || key == null || key.length != KEY_LENGTH)
	    return 0;

	/*
	** Initialization
	*/

	long k0 = byteArrayToLong(key, 0);
	long k1 = byteArrayToLong(key, Long.BYTES);

	m_v0 = k0 ^ C0;
	m_v1 = k1 ^ C1;
	m_v2 = k0 ^ C2;
	m_v3 = k1 ^ C3;

	/*
	** Compression
	*/

	int length1 = data.length / 8;
	int length2 = C_ROUNDS[m_c_rounds_index];

	for(int i = 0; i < length1; i++)
	{
	    long m = byteArrayToLong(data, 8 * i);

	    m_v3 ^= m;

	    switch(length2)
	    {
	    case 2:
		round();
		round();
		break;
	    case 4:
		round();
		round();
		round();
		round();
		break;
	    default:
		break;
	    }

	    m_v0 ^= m;
	}

	int offset = (data.length / 8) * 8;
	long b = ((long) data.length) << 56;

	switch(data.length % 8)
	{
	case 7:
	    b |= ((long) data[offset + 6]) << 48;
	case 6:
	    b |= ((long) data[offset + 5]) << 40;
	case 5:
	    b |= ((long) data[offset + 4]) << 32;
	case 4:
	    b |= ((long) data[offset + 3]) << 24;
	case 3:
	    b |= ((long) data[offset + 2]) << 16;
	case 2:
	    b |= ((long) data[offset + 1]) << 8;
	case 1:
	    b |= ((long) data[offset]);
	    break;
	case 0:
	    break;
	default:
	    break;
	}

	m_v3 ^= b;

	switch(C_ROUNDS[m_c_rounds_index])
	{
	case 2:
	    round();
	    round();
	    break;
	case 4:
	    round();
	    round();
	    round();
	    round();
	    break;
	default:
	    break;
	}

	m_v0 ^= b;

	/*
	** Finalization
	*/

	m_v2 ^= 0xff;

	switch(D_ROUNDS[m_d_rounds_index])
	{
	case 4:
	    round();
	    round();
	    round();
	    round();
	    break;
	case 8:
	    round();
	    round();
	    round();
	    round();
	    round();
	    round();
	    round();
	    round();
	    break;
	default:
	    break;
	}

	return m_v0 ^ m_v1 ^ m_v2 ^ m_v3;
    }

    public static boolean test1()
    {
	/*
	** Please read the Test Values section of
	** https://131002.net/siphash/siphash.pdf.
	*/

	SipHash s = new SipHash
	    (new byte[] {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
			 (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
			 (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
			 (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f},
	     0, 0);
	long result = Miscellaneous.byteArrayToLong
	    (new byte[] {(byte) 0xa1, (byte) 0x29, (byte) 0xca, (byte) 0x61,
			 (byte) 0x49, (byte) 0xbe, (byte) 0x45, (byte) 0xe5});
	long value = s.hmac
	    (new byte[] {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
			 (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
			 (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
			 (byte) 0x0c, (byte) 0x0d, (byte) 0x0e});

	return result == value;
    }
}
