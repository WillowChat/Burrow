# Burrow
Kotlin (JVM targeted) IRC v3.2 daemon. Relies heavily on [Kale](https://github.com/willowchat/kale) for message parsing and serialising. Pre 1.0 - probably not useful right now.

Project management (including features, in-progress work and goals) is done [on Trello](https://crrt.io/burrow-pm), and [Issues](https://github.com/WillowChat/Burrow/issues) are for bugs only. If you'd like to suggest a feature, talk to me on IRC at `#carrot/irc.imaginarynet.uk`.

[![trello](https://img.shields.io/badge/trello-%F0%9F%93%8B-blue.svg)](https://crrt.io/burrow-pm) [![patreon](https://img.shields.io/badge/patreon-%F0%9F%A5%95-orange.svg)](https://crrt.io/patreon) [![codecov](https://codecov.io/gh/WillowChat/Burrow/branch/develop/graph/badge.svg)](https://codecov.io/gh/WillowChat/Burrow)

## Goals:
* Be a suitable replacement for mostly untested and memory-unsafe C-style daemons, for small to mid-sized networks
* Make IRC daemon development easier, by focusing on software quality and maintainability, sometimes at the expense of scaling to thousands of simultaneous active clients
* Verify server behaviour with an extensive suite of unit and integration tests, realised by practising some form of TDD
  
If this sounds good to you, you can support development through [Patreon](https://crrt.io/patreon) 🎉!

## Code License
The source code of this project is licensed under the terms of the ISC license, listed in the [LICENSE](LICENSE.md) file. A concise summary of the ISC license is available at [choosealicense.org](http://choosealicense.com/licenses/isc/).
