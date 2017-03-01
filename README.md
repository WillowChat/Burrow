# Burrow
Kotlin (JVM targeted), tested, IRC v3.2 daemon. Intended to replace mostly untested and memory-unsafe C-style daemons for small to mid-sized networks.

This project is almost brand new. It's unlikely to be production-ready until version 1.

## Project Goals
* Test-driven from the start (90%+ coverage minimum)
* Get a basic daemon going - clients should be able to:
 * Connect
 * Join a channel
 * Send some messages
* Track performance regressions
* Fuzzing
* Reloadable SSL (AF_LOCAL socket to SSL terminator instead?)
* Experiment with:
 * Redundant linking
 * Non-IRC S2S (protobuf?)

## Code License
The source code of this project is licensed under the terms of the ISC license, listed in the [LICENSE](LICENSE.md) file. A concise summary of the ISC license is available at [choosealicense.org](http://choosealicense.com/licenses/isc/).
