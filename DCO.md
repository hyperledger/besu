DCO
===

As per section 13.a of the [Hyperledger Charter](https://www.hyperledger.org/about/charter) all code submitted to the Hyperledger Foundation needs to have a [Developer Certificate of Origin](http://developercertificate.org/) (DCO) sign-off.

The signoff needs to be using your legal name, not a pseudonym.  Git has a built-in mechanism to allow this with the `-s` or `--signoff` argument to `git commit` command, providing your `user.name` and `user.email` have been setup correctly.

TL;DR:

If you don't want to break the DCO check, ensure all your commits have signoff.

`git config user.name "FIRST_NAME LAST_NAME"`
`git config user.email "MY_NAME@example.com"`

If you use GitHub web UI for commits, also ensure you have your email address set as public in your GitHub profile. This avoids issues with XYZ@users.noreply.github.com placeholder emails.

You can also set up a git global alias. 

More info and what to do if DCO is failing on your PR in the [wiki](https://wiki.hyperledger.org/display/BESU/DCO)

If you have any questions, you can reach us on Besu chat; first, [join the Discord server](https://discord.com/invite/hyperledger) then [join the Besu channel](https://discord.com/channels/905194001349627914/938504958909747250).
