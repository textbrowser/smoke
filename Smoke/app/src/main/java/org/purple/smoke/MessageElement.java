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

public class MessageElement
{
    public String m_id = "";
    public String m_message = "";
    public String m_name = "";
    public boolean m_purple = false;
    public byte[] m_attachment = null;
    public byte[] m_keyStream = null;
    public byte[] m_messageIdentity = null;
    public final static int CHAT_MESSAGE_TYPE = 0;
    public final static int FIRE_MESSAGE_TYPE = 1;
    public final static int FIRE_STATUS_MESSAGE_TYPE = 2;
    public final static int JUGGERNAUT_MESSAGE_TYPE = 3;
    public final static int RESEND_CHAT_MESSAGE_TYPE = 4;
    public final static int RETRIEVE_MESSAGES_MESSAGE_TYPE = 5;
    public final static int SHARE_SIPHASH_ID_MESSAGE_TYPE = 6;
    public final static int STEAM_KEY_EXCHANGE_MESSAGE_TYPE = 7;
    public int m_messageType = -1;
    public int m_position = -1;
    public long m_delay = -1L;
    public long m_sequence = -1L;
    public long m_timestamp = -1L;

    public MessageElement()
    {
    }
}
