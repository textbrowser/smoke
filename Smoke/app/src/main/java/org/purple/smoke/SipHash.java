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
    private long m_v0 = 0L;
    private long m_v1 = 0L;
    private long m_v2 = 0L;
    private long m_v3 = 0L;
    public final static int KEY_LENGTH = 16; // Bytes.

    private long byteArrayToLong(byte bytes[], int offset)
    {
	if(bytes == null || (bytes.length - offset) < Miscellaneous.LONG_BYTES)
	    return 0L;

	long value = 0L;

	value |= (((long) bytes[offset]) & 0xffL) <<
	    (0L);
	value |= (((long) bytes[1 + offset]) & 0xffL) <<
	    (Miscellaneous.LONG_LONG_BYTES);
	value |= (((long) bytes[2 + offset]) & 0xffL) <<
	    (Miscellaneous.LONG_LONG_BYTES * 2L);
	value |= (((long) bytes[3 + offset]) & 0xffL) <<
	    (Miscellaneous.LONG_LONG_BYTES * 3L);
	value |= (((long) bytes[4 + offset]) & 0xffL) <<
	    (Miscellaneous.LONG_LONG_BYTES * 4L);
	value |= (((long) bytes[5 + offset]) & 0xffL) <<
	    (Miscellaneous.LONG_LONG_BYTES * 5L);
	value |= (((long) bytes[6 + offset]) & 0xffL) <<
	    (Miscellaneous.LONG_LONG_BYTES * 6L);
	value |= (((long) bytes[7 + offset]) & 0xffL) <<
	    (Miscellaneous.LONG_LONG_BYTES * 7L);
	return value;
    }

    private long rotl(long x, long b)
    {
	return (x << b) | (x >>> (64L - b));
    }

    private void round()
    {
	m_v0 += m_v1;
	m_v1 = rotl(m_v1, 13L);
	m_v1 ^= m_v0;
	m_v0 = rotl(m_v0, 32L);
	m_v2 += m_v3;
	m_v3 = rotl(m_v3, 16L);
	m_v3 ^= m_v2;
	m_v2 += m_v1;
	m_v1 = rotl(m_v1, 17L);
	m_v1 ^= m_v2;
	m_v2 = rotl(m_v2, 32L);
	m_v0 += m_v3;
	m_v3 = rotl(m_v3, 21L);
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

    public long[] hmac(byte data[], int outputLength)
    {
	return hmac(data, m_key, outputLength);
    }

    @SuppressWarnings("fallthrough")
    public synchronized long[] hmac(byte data[], byte key[], int outputLength)
    {
	if(data == null || key == null || key.length != KEY_LENGTH)
	    return new long[] {0L, 0L};

	/*
	** Initialization
	*/

	long k0 = byteArrayToLong(key, 0);
	long k1 = byteArrayToLong(key, Miscellaneous.LONG_BYTES);

	m_v0 = k0 ^ C0;
	m_v1 = k1 ^ C1;
	m_v2 = k0 ^ C2;
	m_v3 = k1 ^ C3;

	if(outputLength == 16)
	    m_v1 ^= 0xeeL;

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
	    m = 0L;
	}

	int offset = (data.length / 8) * 8;
	long b = ((long) data.length) << 56L;

	switch(data.length % 8)
	{
	case 7:
	    b |= ((long) data[offset + 6]) << 48L;
	case 6:
	    b |= ((long) data[offset + 5]) << 40L;
	case 5:
	    b |= ((long) data[offset + 4]) << 32L;
	case 4:
	    b |= ((long) data[offset + 3]) << 24L;
	case 3:
	    b |= ((long) data[offset + 2]) << 16L;
	case 2:
	    b |= ((long) data[offset + 1]) << 8L;
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

	if(outputLength == 16)
	    m_v2 ^= 0xeeL;
	else
	    m_v2 ^= 0xffL;

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

	long output[] = new long[] {m_v0 ^ m_v1 ^ m_v2 ^ m_v3, 0};

	if(outputLength == 8)
	{
	    k0 = k1 = m_v0 = m_v1 = m_v2 = m_v3 = 0L;
	    return output;
	}

	m_v1 ^= 0xddL;

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

	output[1] = m_v0 ^ m_v1 ^ m_v2 ^ m_v3;
	k0 = k1 = m_v0 = m_v1 = m_v2 = m_v3 = 0L;
	return output;
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
	long value[] = s.hmac
	    (new byte[] {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
			 (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
			 (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
			 (byte) 0x0c, (byte) 0x0d, (byte) 0x0e},
		Cryptography.SIPHASH_OUTPUT_LENGTH / 2);

	return result == value[0];
    }
}
