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

import java.util.Arrays;

public class Arson
{
    public final static int V_SIZE = 64; // Moonlander Index size.
    public final static int Y_SIZE = 64; // Size of HMAC output.
    public final static int Z_SIZE = 64; // Size of HMAC output.

    private static class BundleA
    {
	public byte m_x[] = null;
	public byte m_y[] = null;
	public byte m_z[] = null;

	public BundleA(byte bytes[])
	{
	    m_x = Arrays.copyOfRange(bytes, 0, bytes.length - Y_SIZE - Z_SIZE);
	    m_y = Arrays.copyOfRange
		(bytes, bytes.length - Y_SIZE - Z_SIZE, bytes.length - Z_SIZE);
	    m_z = Arrays.copyOfRange
		(bytes, bytes.length - Z_SIZE, bytes.length);
	}
    }

    private static class BundleB
    {
	public byte m_v[] = null;
	public byte m_w[] = null;
	public byte m_x[] = null;
	public byte m_y[] = null;
	public byte m_z[] = null;

	public BundleB(byte bytes[])
	{
	    final int x_size = 8 + // Time
		64 + // Sender Digest
		96 + // Arson Keys
		96;  // Message Keys
	    final int w_size = bytes.length - V_SIZE - Y_SIZE - Z_SIZE - x_size;

	    m_v = Arrays.copyOfRange(bytes, 0, V_SIZE);
	    m_w = Arrays.copyOfRange(bytes, V_SIZE, w_size);
	}
    }

    public Arson()
    {
    }
}
