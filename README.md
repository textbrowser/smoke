Neither snow nor rain nor heat nor gloom of night stays these violet
couriers from the swift completion of their appointed rounds. A project
for the curious.

Smoke
-----

Summary of Smoke

<ul>
<li>Aliases. Preserve your contacts.</li>
<li>Almost zero-dependency software.</li>
<li>Application lock.</li>
<li>Argon2id and PBKDF2 key-derivation functions.</li>
<li>Automatic, oscillatory public-key exchange protocol, via SipHash.</li>
<li>BSD 3-clause license.</li>
<li>Content is recorded via authenticated encryption.</li>
<li>Decentralized. TCP, and UDP multicast and unicast.</li>
<li>Does not require Internet connectivity.</li>
<li>Does not require registration. Telephone numbers are not required.</li>
<li>Encrypted communications.</li>
<li>Eventful tasks. Limited polling.</li>
<li>F-Droid.</li>
<li>Fiasco forward secrecy.</li>
<li>Future-proof software.</li>
<li>Introduces Cryptographic Discovery. Cryptographic Discovery is a practical protocol which creates coordinated data paths.</li>
<li>Juggling Juggernaut Protocol!</li>
<li>Manufactured tool tips!</li>
<li>McEliece Fujisaka and Pointcheval.</li>
<li>Message structures do not explicitly expose contents. Header-less protocols! Some messages do include type information.</li>
<li>Mobile servers via <a href="https://github.com/textbrowser/smokestack">SmokeStack</a>.</li>
<li>Obfuscation of resident secret keys.</li>
<li>Optional foreground services.</li>
<li>Optional silence over the wires.</li>
<li>Original implementation of SipHash.</li>
<li>Ozone destinations: private and public repositories.</li>
<li>Post offices for messages of the past.</li>
<li>Private servers.</li>
<li>Public and private public-key servers.</li>
<li>Rainbow digital signature scheme.</li>
<li>Reliable distribution of archived messages.</li>
<li>Reliable distribution of deliverable text messages.</li>
<li>SPHINCS digital signature scheme.</li>
<li>SSL/TLS through SmokeStack.</li>
<li>Semi-compatible with <a href="https://github.com/textbrowser/spot-on">Spot-On</a> via Fire.</li>
<li>Share files with TCP utilities such as Netcat.</li>
<li>SipHash-128.</li>
<li>Smoke and mirrors.</li>
<li>Software congestion control.</li>
<li>Software manual included.</li>
<li>Steam, reliable file sharing. TCP over the Echo!</li>
<li>Steamrolling, or, real-time broadcasting of inbound Steams to fellow participants.</li>
<li>Super McEliece: m = 13, t = 118.</li>
</ul>

Please read https://github.com/textbrowser/smoke/tree/master/Documentation for more information.

Starting a Conversation

<ol>
<li>Download and install Smoke.</li>
<li>Download and install SmokeStack, Spot-On, or Spot-On-Lite. More adventurous operators are welcome to create servers using ncat and socat.</li>
<li>Define a private or public listener: 5.180.182.220. The server is provided for everyone so please do not abuse it!</li>
<li>Connect Smoke to defined listener.</li>
<li>Share aliases: super-alias-1, super-alias-2.</li>
<li>Define aliases in respective Smoke instances.</li>
<li>Share public keys.</li>
<li>Done!</li>
<li><b>Public server certificate is valid for one year.</b></li>
<li><b>Public server status is available at http://5.180.182.220:5000.</b></li>
</ol>

Network traffic on 5.180.182.220 covering the last three months.
![alt text](https://github.com/textbrowser/smoke/blob/master/Images/traffic-1.png)

```
Steamrolling is the process of real-time broadcasting of complete and
incomplete inbound Steams. Let’s review a colorful example.
*---------------*
| Participant A | <--------------------------------------
*---------------*                                       |
       |                                                |
       | (Steam A)                                      |
       V                                                |
*---------------*  (Steam A)  *---------------*         | (Steam A)
| Participant B | ----------> | Participant C |         |
*---------------*             *---------------*         |
       |                                                |
       | (Steam A)                                      |
       V                                                |
*---------------*                                       |
| Participant D |                                       |
*---------------*                                       |
       |                                                |
       | (Steam A)                                      |
       V                                                |
*---------------*                                       |
| Participant E | ---------------------------------------
*---------------*
Participants C, D, and E shall receive Steam A data as participant B receives
and writes Steam A data. If the stream of bytes between A and B is interrupted,
the interruption will percolate throughout the network. Unique keys are established
between each of the paired participants.
```

![alt text](https://github.com/textbrowser/smoke/blob/master/Images/smoke_7.png)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid!"
     height="80">](https://f-droid.org/packages/org.purple.smoke/)
