description: Code review guidelines for Besu developers
<!--- END of page meta data -->

All changes must be code reviewed. For non-approvers this is obvious, since
you can't commit anyway. But even for approvers, we want all changes to get at
least one review, preferably (for non-trivial changes obligatorily) from someone
who knows the areas the change touches. For non-trivial changes we may want two
reviewers. The primary reviewer will make this decision and nominate a second
reviewer, if needed. Except for trivial changes, PRs should not be committed
until relevant parties (e.g. owners of the subsystem affected by the PR) have
had a reasonable chance to look at PR in their local business hours.

Most PRs will find reviewers organically. If an approver intends to be the
primary reviewer of a PR they should set themselves as the assignee on GitHub
and say so in a reply to the PR. Only the primary approver of a change should
actually do the merge, except in rare cases (e.g. they are unavailable in a
reasonable timeframe).

If a PR has gone 2 work days without an approver emerging, please ask on [Besu Rocketchat]

## Attribution

This Document was adapted by the following:
- Kubernetes collab.md, available at [kub collab]  

[kub collab]: https://raw.githubusercontent.com/kubernetes/community/master/contributors/devel/collab.md
[Besu Rocketchat]: https://chat.hyperledger.org/channel/besu