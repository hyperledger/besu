## Credits & Acknowledgments

These classes were ported from [Javafuzz](https://gitlab.com/gitlab-org/security-products/analyzers/fuzzers/javafuzz/).
Javafuzz is a port of [fuzzitdev/jsfuzz](https://gitlab.com/gitlab-org/security-products/analyzers/fuzzers/jsfuzz).

Which in turn based based on [go-fuzz](https://github.com/dvyukov/go-fuzz) originally developed by [Dmitry Vyukov's](https://twitter.com/dvyukov).
Which is in turn heavily based on [Michal Zalewski](https://twitter.com/lcamtuf) [AFL](http://lcamtuf.coredump.cx/afl/).

## Changes

* Increased max binary size to 48k+1
* ported AbstractFuzzTarget to a functional interface FuzzTarget
* Fixed some incompatibilities with JaCoCo
* Besu style required changes (formatting, final params, etc.)