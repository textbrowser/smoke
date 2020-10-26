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

public class SteamElement
{
    public String m_destination = "";
    public String m_fileName = "";
    public String m_status = "paused";
    public String m_transferRate = "";
    public byte m_ephemeralPrivateKey[] = null;
    public byte m_ephemeralPublicKey[] = null;
    public byte m_fileDigest[] = null;
    public byte m_fileIdentity[] = null;
    public byte m_keyStream[] = null;
    public int m_direction = DOWNLOAD;
    public int m_oid = -1;
    public int m_someOid = -1;
    public long m_fileSize = 0L;
    public long m_readInterval = 4L; // 4 reads / s
    public long m_readOffset = 0L;
    public static int DOWNLOAD = 0;
    public static int UPLOAD = 1;

    public SteamElement()
    {
    }

    public SteamElement(String fileName)
    {
	m_direction = UPLOAD;
	m_fileName = fileName;

	if(fileName.lastIndexOf('.') > 0)
	    fileName = fileName.substring(0, fileName.lastIndexOf('.'));

	m_fileSize = Miscellaneous.fileSize(fileName);
    }
}
