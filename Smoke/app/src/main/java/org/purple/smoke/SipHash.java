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
    private final static int C_ROUNDS = 4;
    private final static int D_ROUNDS = 8;
    private final static long C0 = 0x736f6d6570736575L;
    private final static long C1 = 0x646f72616e646f6dL;
    private final static long C2 = 0x6c7967656e657261L;
    private final static long C3 = 0x7465646279746573L;
    private byte m_key[] = null;
    private long m_v0 = 0;
    private long m_v1 = 0;
    private long m_v2 = 0;
    private long m_v3 = 0;
    public final static int KEY_LENGTH = 16;

    private long byteArrayToLong(byte bytes[], int offset)
    {
	if(bytes == null || (bytes.length - offset) < Long.BYTES)
	    return 0;

	long value = 0;

	for(int i = 0; i < Long.BYTES; i++)
	    value |= (((long) bytes[i + offset]) & 0xff) << (Long.BYTES * i);

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

    public long hmac(byte data[])
    {
	return hmac(data, m_key);
    }

    public long hmac(byte data[], byte key[])
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

	for(int i = 0; i < data.length;)
	{
	    long m = 0;

	    for(int j = 0; i < data.length && j < Long.BYTES; i++, j++)
		m |= (((long) data[i]) & 0xff) << (Long.BYTES * j);

	    m_v3 ^= m;

	    for(int j = 0; j < C_ROUNDS; j++)
		round();

	    m_v0 ^= m;
	}

	int left = data.length & 7;
	long b = ((long) data.length) << 56;

	switch(left)
	{
	case 7:
	    b |= ((long) data[6]) << 48;
	case 6:
	    b |= ((long) data[5]) << 40;
	case 5:
	    b |= ((long) data[4]) << 32;
	case 4:
	    b |= ((long) data[3]) << 24;
	case 3:
	    b |= ((long) data[2]) << 16;
	case 2:
	    b |= ((long) data[1]) << 8;
	case 1:
	    b |= ((long) data[0]);
	    break;
	case 0:
	    break;
	}

	m_v3 ^= b;

	for(int i = 0; i < C_ROUNDS; i++)
	    round();

	m_v0 ^= b;

	/*
	** Finalization
	*/

	m_v2 ^= 0xff;

	for(int i = 0; i < D_ROUNDS; i++)
	    round();

	return m_v0 ^ m_v1 ^ m_v2 ^ m_v3;
    }

    public SipHash()
    {
    }

    public SipHash(byte key[])
    {
	if(key == null || key.length != KEY_LENGTH)
	    return;

	m_key = Miscellaneous.deepCopy(key);
    }
}
