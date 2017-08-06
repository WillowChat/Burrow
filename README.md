# Burrow
Kotlin (JVM targeted), tested, IRC v3.2 daemon. Intended to replace mostly untested and memory-unsafe C-style daemons for small to mid-sized networks.

It relies heavily on [Kale](https://github.com/willowchat/kale) to do the message parsing and serialising.

This software is pre-alpha - version 1 will do enough of RFC1459 (and maybe some of IRCv3) to be functional as a daemon. Namely: logging on with a nickname, and participating in channels or private messages.

Project management (including features, in-progress work and goals) is done [on Trello](https://crrt.io/burrow-pm), and [Issues](https://github.com/WillowChat/Burrow/issues) are for bugs only. If you'd like to suggest a feature, talk to me on IRC at `#carrot/irc.imaginarynet.uk`.

[![trello](https://img.shields.io/badge/trello-%F0%9F%93%8B-blue.svg)](https://crrt.io/burrow-pm) [![patreon](https://img.shields.io/badge/patreon-%F0%9F%A5%95-orange.svg)](https://crrt.io/patreon) [![codecov](https://codecov.io/gh/WillowChat/Burrow/branch/develop/graph/badge.svg)](https://codecov.io/gh/WillowChat/Burrow) 


## Support

<a href="https://crrt.io/patreon"><img src="https://c5.patreon.com/external/logo/become_a_patron_button.png" align="left" width="160" ></a>
If you'd like to support the development of this daemon, you can do so by tipping through [Patreon](https://patreon.com/carrotcodes).

## Code License
The source code of this project is licensed under the terms of the ISC license, listed in the [LICENSE](LICENSE.md) file. A concise summary of the ISC license is available at [choosealicense.org](http://choosealicense.com/licenses/isc/).
