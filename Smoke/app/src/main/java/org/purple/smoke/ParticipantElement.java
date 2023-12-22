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

public class ParticipantElement implements Comparable<ParticipantElement>
{
    public String m_name = "";
    public String m_sipHashId = "";
    public byte[] m_keyStream = null;
    public int m_oid = -1;
    public long m_lastStatusTimestamp = -1L;

    public ParticipantElement()
    {
    }

    @Override
    public boolean equals(Object object)
    {
        if(object instanceof ParticipantElement)
	{
            ParticipantElement participantElement =
		(ParticipantElement) object;

	    if(participantElement != null)
                return m_sipHashId.equals(participantElement.m_sipHashId);
        }

	return false;
    }

    @Override
    public int compareTo(ParticipantElement participantElement)
    {
	if(participantElement != null)
	    return m_name.compareTo(participantElement.m_name);
	else
	    return 1;
    }

    @Override
    public int hashCode()
    {
	return m_sipHashId.hashCode();
    }
}
