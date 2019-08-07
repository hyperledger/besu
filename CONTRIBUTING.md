# Contributing to Pantheon
:+1::tada: First off, thanks for taking the time to contribute! :tada::+1:

Welcome to the Pantheon repository!  The following is a set of guidelines for contributing to this 
repo and its packages. These are mostly guidelines, not rules. Use your best judgment, 
and feel free to propose changes to this document in a pull request.

#### Table Of Contents
[Code of Conduct](#code-of-conduct)

[I just have a quick question](#i-just-have-a-quick-question)

[How To Contribute](#how-to-contribute)
  * [Reporting Bugs](#reporting-bugs)
  * [Suggesting Enhancements](#suggesting-enhancements)
  * [Your First Contribution](#your-first-contribution)
  * [Pull Requests](#pull-requests)
  * [Code Reviews](#code-review)

[Style Guides](#style-guides)
  * [Java Style Guide](#java-code-style-guide)
  * [Coding Conventions](#coding-conventions)
  * [Git Commit Messages & Pull Request Messages](#git-commit-messages--pull-request-messages)
  
[Pull Request Labels](#pull-request-labels)

## Code of Conduct

This project and everyone participating in it is governed by the [Pantheon Code of Conduct](CODE-OF-CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior to [private@pegasys.tech].

## I just have a quick question

> **Note:** Please don't file an issue to ask a question.  You'll get faster results by using the resources below.

* [Pantheon documentation]
* [Gitter]

## How To Contribute
### Reporting Bugs

This section guides you through submitting a bug report. Following these guidelines helps maintainers 
and the community understand your report, reproduce the behavior, and find related reports.

Before creating bug reports, please check the [before-submitting-a-bug-report](#before-submitting-a-bug-report) 
checklist as you might find out that you don't need to create one. When you are creating a bug report, 
please [include as many details as possible](#how-do-i-submit-a-good-bug-report).

> **Note:** If you find a **Closed** issue that seems like it is the same thing that you're experiencing, 
open a new issue and include a link to the original issue in the body of your new one.

#### Before Submitting A Bug Report
* **Confirm the problem** is reproducible in the latest version of the software
* **Check [Pantheon documentation]**. You might be able to find the cause of the problem and fix things yourself. 
* **Perform a [cursory search of project issues](https://pegasys1.atlassian.net/browse/PAN-2502?jql=project%20%3D%20PAN)** 
to see if the problem has already been reported. If it has **and the issue is still open**, add a comment 
to the existing issue instead of opening a new one.

#### How Do I Submit A (Good) Bug Report?
Bugs are tracked as [Jira issues](https://pegasys1.atlassian.net/secure/Dashboard.jspa?selectPageId=10000).  

Explain the problem and include additional details to help maintainers reproduce the problem:

* **Use a clear and descriptive summary** for the issue to identify the problem.
* **Describe the exact steps which reproduce the problem** in as many details as possible. For example, 
start by explaining how you started Pantheon, e.g. which command exactly you used in the terminal, 
or how you started it otherwise. 
* **Provide specific examples to demonstrate the steps**. Include links to files or GitHub projects, 
or copy/pasteable snippets, which you use in those examples. If you're providing snippets in the issue, 
use backticks (```) to format the code snippets.
* **Describe the behavior you observed after following the steps** and point out what exactly is the 
problem with that behavior.
* **Explain which behavior you expected to see instead and why.**
* **Include screenshots** which show you following the described steps and clearly demonstrate the problem.

Provide more context by answering these questions:

* **Did the problem start happening recently** (e.g. after updating to a new version of the software) 
or was this always a problem?
* If the problem started happening recently, **can you reproduce the problem in an older version of the software?** 
What's the most recent version in which the problem doesn't happen? 
* **Can you reliably reproduce the issue?** If not, provide details about how often the problem happens 
and under which conditions it normally happens.

Include details about your configuration and environment:

* **Which version of the software are you using?** You can get the exact version by running `pantheon -v` in your terminal.
* **What OS & Version are you running?**
  * **For Linux - What kernel are you running?** You can get the exact version by running `uname -a` in your terminal.
* **Are you running in a virtual machine?** If so, which VM software are you using and which operating 
systems and versions are used for the host and the guest?
* **Are you running in a docker container?** If so, what version of docker?
* **Are you running in a a Cloud?** If so, which one, and what type/size of VM is it?
* **What version of Java are you running?** You can get the exact version by looking at the pantheon 
logfile during startup.

### Suggesting Enhancements

This section guides you through submitting an enhancement suggestion, including completely new features 
and minor improvements to existing functionality or documentation changes.

Following these guidelines helps maintainers and the community understand your suggestion and find related suggestions.

Before creating enhancement suggestions, please check the 
[before-submitting-an-enhancement-suggestion](#before-submitting-an-enhancement-suggestion) list as 
you might find out that you don't need to create one.

When you are creating an enhancement suggestion, please 
[include as many details as possible](#how-do-i-submit-a-good-enhancement-suggestion). 

#### Before Submitting An Enhancement Suggestion

* **Check the [Pantheon documentation].** You might be able to find the cause of the problem and fix things yourself. 
* **Perform a [cursory search of project issues](https://pegasys1.atlassian.net/browse/PAN-2502?jql=project%20%3D%20PAN)** 
to see if the problem has already been reported. If it has **and the issue is still open**, add a comment 
to the existing issue instead of opening a new one.

#### How Do I Submit A (Good) Enhancement Suggestion?

Enhancement suggestions are tracked as [Jira issues](https://pegasys1.atlassian.net/secure/Dashboard.jspa?selectPageId=10000).
Provide the following information:

* **Use a clear and descriptive title** for the issue to identify the suggestion.
* **Provide a step-by-step description of the suggested enhancement** in as many details as possible.
* **Provide specific examples to demonstrate the steps**. If you're providing code snippets in the issue, 
 use backticks (````) to format the code snippets..
* **Describe the current behavior** and **explain which behavior you expected to see instead** and why.
* **Include screenshots** which help you demonstrate the steps where possible.
* **Explain why this enhancement would be useful** to most users.
* **Does this enhancement exist in other clients?**
* **Specify which version of the software you're using.** You can get the exact version by running 
`pantheon -v` in your terminal.
* **Specify the name and version of the OS you're using.**

## Your First Contribution
Start by looking through the 'good first issue' and 'help wanted' labeled issues on the [Jira dashboard](https://pegasys1.atlassian.net/secure/Dashboard.jspa?selectPageId=10000):
* [Good First Issue][search-label-good-first-issue] - issues which should only require a few lines of code or documentation, 
and a test or two.
* [Help wanted issues][search-label-help-wanted] - issues which are a bit more involved than `good first issue` issues.

When you've identified an issue you'd like to work on, ping us on [Gitter] and we'll assign it to you. 

### Contribution Workflow
The codebase and documentation are maintained using the same "*contributor workflow*" where everyone 
without exception contributes changes proposals using "*pull-requests*".

This facilitates social contribution, easy testing, and peer review.

To contribute changes, use the following workflow:

1. [**Fork the repository**](https://github.com/PegaSysEng/pantheon/fork).
1. **Clone your fork** to your computer.
1. **Create a topic branch** and name it appropriately.
Starting the branch name with the issue number is a good practice and a reminder to fix only one issue in a 
Pull-Request (PR)._
1. **Make your changes** adhering to the coding conventions described below.
_In general a commit serves a single purpose and diffs should be easily comprehensible.
For this reason do not mix any formatting fixes or code moves with actual code changes._
1. **Commit your changes** see [How to Write a Git Commit Message] article by [Chris Beams].
1. **Test your changes** locally before pushing to ensure that what you are proposing is not breaking
another part of the software. Running the `./gradlew clean check test` command locally will help you
to be confident that your changes will pass CI tests once pushed as a Pull Request.
1. **Push your changes** to your remote fork (usually labeled as `origin`).
1. **Create a pull-request** (PR) on the Pantheon repository. If the PR addresses an existing Jira issue, 
include the issue number in the PR title in square brackets (for example, `[PAN-2374]`). 
1. **Add labels** to identify the type of your PR. _For example, if your PR is not ready to validate,
add the "work-in-progress" label. If it fixes a bug, add the "bug" label._
1. If the PR address an existing Jira issue, comment in the Jira issue with the PR number. 
1. **Ensure your changes are reviewed**.
_Select the reviewers you would like to review your PR.
If you don't know who to choose, simply select the reviewers proposed by GitHub or leave blank._
1. **Make any required changes** on your contribution from the reviewers feedback.
_Make the changes, commit to your branch, and push to your remote fork._
1. **When your PR is validated**, all tests passed and your branch has no conflicts with the target branch,
you can **"squash and merge"** your PR and you're done. You contributed to Pantheon! Thanks !

### Architectural Best Practices

Questions on architectural best practices will be guided by the principles set forth in 
[Effective Java](http://index-of.es/Java/Effective%20Java.pdf) by Joshua Bloch

### Automated Test coverage
All code submissions must be accompanied by appropriate automated tests.
The goal is to provide confidence in the code’s robustness, while avoiding redundant tests.

### Pull Requests

The process described here has several goals:

- Maintain Product quality
- Fix problems that are important to users
- Engage the community in working toward the best possible product
- Enable a sustainable system for maintainers to review contributions
- Further explanation on PR & commit messages can be found in the 
[How to Write a Git Commit Message] article by [Chris Beams].

Please follow these steps to have your contribution considered by the approvers:

1. Complete the CLA, as described in [CLA.md].
2. Follow all instructions in [PULL-REQUEST-TEMPLATE.md](.github/pull_request_template.md).
3. Include appropriate test coverage. Testing is 100% automated. There is no such thing as a manual test.
4. Follow the [Style Guides](#style-guides).
5. After you submit your pull request, verify that all [status checks](https://help.github.com/articles/about-status-checks/) 
are passing.
<details><summary>What if the status checks are failing?</summary>If a status check is failing, 
and you believe that the failure is unrelated to your change, please leave a comment on the pull request 
explaining why you believe the failure is unrelated. A maintainer will re-run the status check for you. 
If we conclude that the failure was a false positive, then we will open an issue to track that problem 
with our status check suite.</details>

## Code Review
While the prerequisites above must be satisfied prior to having your pull request reviewed, the reviewer(s) 
may ask you to complete additional design work, tests, or other changes before your pull request 
can be ultimately accepted.  Please refer to [Code Reviews].

# Style Guides

## Java Code Style Guide

We use Google's Java coding conventions for the project. To reformat code, run: 

```
./gradlew spotlessApply
```

Code style will be checked automatically during a build.

## Coding Conventions
We have a set of [coding conventions](CODING-CONVENTIONS.md) to which we try to adhere.
These are not strictly enforced during the build, but should be adhered to and called out in code reviews.

# Pull Request Labels

#### Pull Request Labels

| Label name                                                              | Description                                                                                |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| [`work-in-progress`][search-label-work-in-progress]                     | Pull requests which are still being worked on, more changes will follow.                   |
| [`requires-changes`][search-label-requires-changes]                     | Pull requests which need to be updated based on review comments and then reviewed again.   |
| [`needs engineering approval`][search-label-needs-engineering-approval] | Pull requests which need to be approved from a technical person, mainly documentation PRs. |

[private@pegasys.tech]: mailto:private@pegasys.tech
[Gitter]: https://gitter.im/PegaSysEng/pantheon
[Pantheon documentation]: https://docs.pantheon.pegasys.tech/
[CLA.md]: /CLA.md
[Code Reviews]: /docs/community/code-reviews.md
[MkDocs]: https://www.mkdocs.org/
[How to Write a Git Commit Message]: https://chris.beams.io/posts/git-commit/
[Chris Beams]: https://github.com/cbeams