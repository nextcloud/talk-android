<!--
 ~ SPDX-FileCopyrightText: 2018-2024 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
### Background
The configuration of Nextcloud Talk mainly depends on your desired usage:
- As long as it shall be used only **within one local network**, nothing should be needed at all. Just verify that all browsers support the underlying [WebRTC](https://en.wikipedia.org/wiki/WebRTC) protocol (all famous ones do on current versions) and you should be good to go.
- Talk tries to establish a direct [peer-to-peer (P2P)](https://en.wikipedia.org/wiki/Peer-to-peer) connection, thus on connections **throughout the local network** (behind a [NAT](https://en.wikipedia.org/wiki/Network_address_translation)/router), clients do not only need to know each others public IP, but their local IP as well. Processing this, is the job of a [STUN](https://en.wikipedia.org/wiki/STUN) server. As there is one preconfigured for Nextcloud Talk, still nothing need to be done.
- In some cases, e.g. **in combination with firewalls or [symmetric NAT](https://en.wikipedia.org/wiki/Network_address_translation#Symmetric_NAT)** a STUN server will not work as well, and then a so called [TURN](https://en.wikipedia.org/wiki/Traversal_Using_Relays_around_NAT) server is needed. Now no direct P2P connection is established, but all traffic is relayed through the TURN server, thus additional (at least internal) traffic and resources are needed.
- Nextcloud Talk will try direct P2P in the first place, use STUN if needed and TURN as last resort fallback. Thus to be most flexible and guarantee functionality of your Nextcloud Talk instance in all possible connection cases, you most properly want to setup a TURN server.

### Install and setup _coturn_ as TURN server
1. **Download/install**
   - On **Debian and Ubuntu** there are official repository packages available:
`sudo apt install coturn`
   - For **Fedora**, an official package it is planned, as far as I can see. For this **and other** cases check out: https://github.com/coturn/coturn/wiki/Downloads

2. **Make coturn run as daemon on startup**
   - On **Debian and Ubuntu** you just need to enable the deployed init.d service by adjusting the related environment variable:
      - `sudo sed -i '/TURNSERVER_ENABLED/c\TURNSERVER_ENABLED=1' /etc/default/coturn`
   - On **Debian Buster** the most current package update implements a systemd unit, which does not use `/etc/default/coturn` but is enabled automatically after install. To check whether a systemd unit is available:
      - `ls -l /lib/systemd/system/coturn.service`
   - On **other OS/distributions**, if you installed coturn manually, you may want to setup an init.d/systemd unit or use another method to run the following during boot:
      - `/path/to/turnserver -c /path/to/turnserver.conf -o`
      - `-o` starts the server in daemon mode, `-c` defines the path to the config file.

3. **Configure _turnserver.conf_ for usage with Nextcloud Talk**
At last you need to adjust the TURN servers configuration file to work with Nextcloud Talk. On Debian and Ubuntu, it can be found at `/etc/turnserver.conf`. The configuration depends on if you want to use TLS for secure connection or not. You may want to start without TLS for testing and then switch, if everything is working fine:
   - **Without TLS** uncomment/adjust the following settings. Choose the listening port, e.g. `3478` (default for non-TLS) or `5349` (default for TLS) and an authentication secret, where a random hex is recommended: `openssl rand -hex 32`:

         listening-port=<yourChosenPortNumber>
         fingerprint
         use-auth-secret
         static-auth-secret=<yourChosen/GeneratedSecret>
         realm=your.domain.org
         total-quota=100
         bps-capacity=0
         stale-nonce
         no-loopback-peers
         no-multicast-peers
   - **With TLS** you need to provide the path to your certificate and key files as well and it is highly recommended to adjust the cipher list:

         tls-listening-port=<yourChosenPortNumber>
         fingerprint
         use-auth-secret
         static-auth-secret=<yourChosen/GeneratedSecret>
         realm=your.domain.org
         total-quota=100
         bps-capacity=0
         stale-nonce
         cert=/path/to/your/cert.pem
         pkey=/path/to/your/privkey.pem
         cipher-list="ECDH+AESGCM:DH+AESGCM:ECDH+AES256:DH+AES256:ECDH+AES128:DH+AES:ECDH+3DES:DH+3DES:RSA+AES:RSA+3DES:!ADH:!AECDH:!MD5"
         no-loopback-peers
         no-multicast-peers

     Note that in case of TLS you only need to set `tls-listening-port`, otherwise only `listening-port`. Nextcloud Talk uses a single port only, thus the _alternative_ ports offered by the settings file can be ignored.

     I added a working cipher example here that is also used within most other guides. But it makes totally sense to **use the cipher-list from your Nextcloud webserver** to have the same compatibility versus security versus performance for both.

     If you want it damn secure, you can also configure a custom [Diffie-Hellman](https://en.wikipedia.org/wiki/Diffie–Hellman_key_exchange) file and/or disable TLSv1.0 + TLSv1.1. But again, it does not make much sense for my impression to handle it different here than for Nextcloud itself. Just decide how much compatibility you need and security/performance you want and configure webserver + coturn the same:

         dh-file=/path/to/your/dhparams.pem
         no-tlsv1
         no-tlsv1_1
   - If your TURN server is running **not behind a NAT**, but with direct www connection and **static public IP**, than you can limit the IPs it listens and answers by setting those as `listening-ip` and `relay-ip`. On larger deployments it is recommended to run your TURN server on a dedicated machine that is directly accessible from the internet.
   - The following settings can be used to adjust the **logging behaviour**. On SBCs with SDcards you may want to adjust this, as by default coturn logs veeery much :wink:. The config file explains everything very well:

         no-stdout-log
         log-file=...
         syslog
         simple-log

4. `sudo systemctl restart coturn` or corresponding restart method

5. **Configure Nextcloud Talk to use your TURN server**
Go to Nextcloud admin panel > Talk settings. Btw. if you already have your own TURN server, you can and may want to use it as STUN server as well:

       STUN servers: your.domain.org:<yourChosenPortNumber>
       TURN server: your.domain.org:<yourChosenPortNumber>
       TURN secret: <yourChosen/GeneratedSecret>
       UDP and TCP
   Do not add `http(s)://` here, this causes errors, the protocol is simply a different one. Also `turn:` or something as prefix is not needed. Just enter the bare `domain:port`.

6. **Port opening/forwarding**\
The TURN server on `<yourChosenPortNumber>` needs to be available for all Talk participants, so you need to open it to the web and if your TURN server is running **behind a NAT**, forward it to the related machine.

### What else
 Nextcloud Talk is still based on the Spreed video calls app (just got renamed on last major update) and thus the Spreed.ME WebRTC solution. For this reason all guides about how to configure coturn for one of them, applies to all of them.

**Futher reference**
- https://github.com/spreedbox/spreedbox/wiki/Use-TURN-server
- https://github.com/nextcloud/spreed/issues/667

**Thanks to** @fancycode and @mario for some clarifications about all of this and if you don't mind, please review the HowTo for possible mistakes or wrong understandings.
Thanks as well to @sushidave for motivating me to write this HowTo :slightly_smiling_face:.
