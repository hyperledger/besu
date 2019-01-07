# Contributing to Pantheon
:+1::tada: First off, thanks for taking the time to contribute! :tada::+1:

Welcome to the Pantheon repository!  The following is a set of guidelines for contributing to this repo and its packages. These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

#### Table Of Contents
[Code of Conduct](#code-of-conduct)

[I just have a quick question](#i-just-have-a-quick-question)

[How To Contribute](#how-to-contribute)
  * [Reporting Bugs](#reporting-bugs)
  * [Suggesting Enhancements](#suggesting-enhancements)
  * [Your First Code Contribution](#your-first-code-contribution)
  * [Pull Requests](#pull-requests)
  * [Code Reviews]

[Style Guides](#style-guides)
  * [Java Style Guide](#java-code-style-guide)
  * [Coding Conventions](#coding-conventions)
  * [Git Commit Messages & Pull Request Messages](#git-commit-messages--pull-request-messages)
  
[Issue and Pull Request Labels](#issue-and-pull-request-labels)

## Code of Conduct

This project and everyone participating in it is governed by the [Pantheon Code of Conduct](CODE-OF-CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to [private@pegasys.tech].


## I just have a quick question

> **Note:** Please don't file an issue to ask a question.  You'll get faster results by using the resources below.

* [Gitter]
* [Wiki]

## How To Contribute
### Reporting Bugs

This section guides you through submitting a bug report. Following these guidelines helps maintainers and the community understand your report, reproduce the behavior, and find related reports.

Before creating bug reports, please check the [before-submitting-a-bug-report](#before-submitting-a-bug-report) checklist as you might find out that you don't need to create one. When you are creating a bug report, please [include as many details as possible](#how-do-i-submit-a-good-bug-report). Fill in the [issue_template.md](.github/issue_template.md), the information it asks for helps us resolve issues faster.

> **Note:** If you find a **Closed** issue that seems like it is the same thing that you're experiencing, open a new issue and include a link to the original issue in the body of your new one.

#### Before Submitting A Bug Report
* **Confirm the problem** is reproducible in the latest version of the software
* **Check the [Debugging Wiki]**. You might be able to find the cause of the problem and fix things yourself. 
* **Perform a [cursory search of project issues](https://github.com/search?q=+is%3Aissue+repo%3APegasysEng/Pantheon)** to see if the problem has already been reported. If it has **and the issue is still open**, add a comment to the existing issue instead of opening a new one.

#### How Do I Submit A (Good) Bug Report?
Bugs are tracked as [GitHub issues](https://guides.github.com/features/issues/).  Issues should provide the following information by filling in the [issue_template.md](.github/issue_template.md).

Explain the problem and include additional details to help maintainers reproduce the problem:

* **Use a clear and descriptive title** for the issue to identify the problem.
* **Describe the exact steps which reproduce the problem** in as many details as possible. For example, start by explaining how you started Pantheon, e.g. which command exactly you used in the terminal, or how you started it otherwise. 
* **Provide specific examples to demonstrate the steps**. Include links to files or GitHub projects, or copy/pasteable snippets, which you use in those examples. If you're providing snippets in the issue, use [Markdown code blocks](https://help.github.com/articles/markdown-basics/#multiple-lines).
* **Describe the behavior you observed after following the steps** and point out what exactly is the problem with that behavior.
* **Explain which behavior you expected to see instead and why.**
* **Include screenshots** which show you following the described steps and clearly demonstrate the problem.

Provide more context by answering these questions:

* **Did the problem start happening recently** (e.g. after updating to a new version of the software) or was this always a problem?
* If the problem started happening recently, **can you reproduce the problem in an older version of the software?** What's the most recent version in which the problem doesn't happen? 
* **Can you reliably reproduce the issue?** If not, provide details about how often the problem happens and under which conditions it normally happens.

Include details about your configuration and environment:

* **Which version of the software are you using?** You can get the exact version by running `pantheon -v` in your terminal.
* **What OS & Version are you running?**
  * **For Linux - What kernel are you running?** You can get the exact version by running `uname -a` in your terminal.
* **Are you running in a virtual machine?** If so, which VM software are you using and which operating systems and versions are used for the host and the guest?
* **Are you running in a docker container?** If so, what version of docker?
* **Are you running in a a Cloud?** If so, which one, and what type/size of VM is it?
* **What version of Java are you running?** You can get the exact version by looking at the pantheon logfile during startup.

### Suggesting Enhancements

This section guides you through submitting an enhancement suggestion, including completely new features and minor improvements to existing functionality. Following these guidelines helps maintainers and the community understand your suggestion and find related suggestions.

Before creating enhancement suggestions, please check the [before-submitting-an-enhancement-suggestion](#before-submitting-an-enhancement-suggestion) list as you might find out that you don't need to create one. When you are creating an enhancement suggestion, please [include as many details as possible](#how-do-i-submit-a-good-enhancement-suggestion). Fill in the [issue_template.md](.github/issue_template.md), including the steps that you imagine you would take if the feature you're requesting existed.

#### Before Submitting An Enhancement Suggestion

* **Check the [Debugging Wiki].** You might be able to find the cause of the problem and fix things yourself. 
* **Perform a [cursory search of project issues](https://github.com/search?q=+is%3Aissue+repo%3APegasysEng/Pantheon)** to see if the problem has already been reported. If it has **and the issue is still open**, add a comment to the existing issue instead of opening a new one.

#### How Do I Submit A (Good) Enhancement Suggestion?

Enhancement suggestions are tracked as [GitHub issues](https://guides.github.com/features/issues/).  Issues should and provide the following information by filling in the [issue_template.md](.github/issue_template.md) and providing the following information:

* **Use a clear and descriptive title** for the issue to identify the suggestion.
* **Provide a step-by-step description of the suggested enhancement** in as many details as possible.
* **Provide specific examples to demonstrate the steps**. Include copy/pasteable snippets which you use in those examples, as [Markdown code blocks](https://help.github.com/articles/markdown-basics/#multiple-lines).
* **Describe the current behavior** and **explain which behavior you expected to see instead** and why.
* **Include screenshots** which help you demonstrate the steps.
* **Explain why this enhancement would be useful** to most users.
* **Does this enhancement exist in other clients?**
* **Specify which version of the software you're using.** You can get the exact version by running `pantheon -v` in your terminal.
* **Specify the name and version of the OS you're using.**

## Your First Code Contribution
Start by looking through the 'good first issue' and 'help wanted' issues:
* [Good First Issue][search-label-good-first-issue] - issues which should only require a few lines of code, and a test or two.
* [Help wanted issues][search-label-help-wanted] - issues which are a bit more involved than `good first issue` issues.

### Local Development
The codebase is maintained using the "*contributor workflow*" where everyone without exception contributes patch proposals using "*pull-requests*". This facilitates social contribution, easy testing and peer review.

To contribute a patch, the workflow is as follows:

* Fork repository
* Create topic branch
* Commit patch
* Create pull-request, adhering to the coding conventions herein set forth

In general a commit serves a single purpose and diffs should be easily comprehensible. For this reason do not mix any formatting fixes or code moves with actual code changes.

### Architectural Best Practices

Questions on architectural best practices will be guided by the principles set forth in [Effective Java](http://index-of.es/Java/Effective%20Java.pdf) by Joshua Bloch

### Automated Test coverage
All code submissions must be accompanied by appropriate automated tests.  The goal is to provide confidence in the code’s robustness, while avoiding redundant tests.

### Pull Requests

The process described here has several goals:

- Maintain Product quality
- Fix problems that are important to users
- Engage the community in working toward the best possible product
- Enable a sustainable system for maintainers to review contributions
- Further explanation on PR & commit messages can be found in this post: [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/).

Please follow these steps to have your contribution considered by the approvers:

1. Complete the CLA, as described in [CLA.md]
2. Follow all instructions in [PULL-REQUEST-TEMPLATE.md](.github/pull_request_template.md)
3. Include appropriate test coverage.  Testing is 100% automated.  There is no such thing as a manual test.
4. Follow the [Style Guides](#style-guides)
5. After you submit your pull request, verify that all [status checks](https://help.github.com/articles/about-status-checks/) are passing <details><summary>What if the status checks are failing?</summary>If a status check is failing, and you believe that the failure is unrelated to your change, please leave a comment on the pull request explaining why you believe the failure is unrelated. A maintainer will re-run the status check for you. If we conclude that the failure was a false positive, then we will open an issue to track that problem with our status check suite.</details>

While the prerequisites above must be satisfied prior to having your pull request reviewed, the reviewer(s) may ask you to complete additional design work, tests, or other changes before your pull request can be ultimately accepted.  Please refer to [Code Reviews].

# Style Guides

## Java Code Style Guide

We use Google's Java coding conventions for the project. To reformat code, run: 

```
./gradlew spotlessApply
```

Code style will be checked automatically during a build.

## Coding Conventions
We have a set of [coding conventions](CODING-CONVENTIONS.md) to which we try to adhere.  These are not strictly enforced during the build, but should be adhered to and called out in code reviews.

## Git Commit Messages & Pull Request Messages
* Use the present tense ("Add feature" not "Added feature")
* Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
* Provide a summary on the first line with more details on additional lines as needed
* Reference issues and pull requests liberally

# Issue and Pull Request Labels
#### Type of Issue and Issue State

| Label name | Search Link :mag_right: | Description |
| --- | --- | --- |
| `enhancement` | [search][search-label-enhancement] | Feature requests. |
| `bug` | [search][search-label-bug] | Confirmed bugs or reports that are very likely to be bugs. |
| `help wanted` | [search][search-label-help-wanted] | The core team would appreciate help from the community in resolving these issues. |
| `good first issue` | [search][search-label-good-first-issue] | Less complex issues which would be good first issues to work on for users who want to contribute. |
| `info needed` | [search][search-label-info-needed] | More information needs to be collected about these problems or feature requests (e.g. steps to reproduce). |
| `needs reproduction` | [search][search-label-needs-reproduction] | Likely bugs, but haven't been reliably reproduced. |
| `blocked` | [search][search-label-blocked] | Issues blocked on other issues. |
| `duplicate` | [search][search-label-duplicate] | Issues which are duplicates of other issues, i.e. they have been reported before. |
| `wontfix` | [search][search-label-wontfix] | The core team has decided not to fix these issues for now, either because they're working as intended or for some other reason. |
| `invalid` | [search][search-label-invalid] | Issues which aren't valid (e.g. user errors). |
| `do we want this?` | [search][search-label-do-we-want-this] | Seeking stakeholder consensus on proposed feature. |

#### Topic Categories

| Label name | Search Link :mag_right: | Description |
| --- | --- | --- |
| `windows` | [search][search-label-windows] | Related to running on Windows. |
| `linux` | [search][search-label-linux] | Related to running on Linux. |
| `mac` | [search][search-label-mac] | Related to running on macOS. |
| `documentation` | [search][search-label-documentation] | Related to any type of documentation |
| `performance` | [search][search-label-performance] | Related to performance. |
| `security` | [search][search-label-security] | Related to security. |
| `api` | [search][search-label-api] | Related to public APIs. |

#### Pull Request Labels

| Label name | Search Link :mag_right: | Description
| --- | --- | --- |
| `work-in-progress` | [search][search-label-work-in-progress] | Pull requests which are still being worked on, more changes will follow. |
| `requires-changes` | [search][search-label-requires-changes] | Pull requests which need to be updated based on review comments and then reviewed again. |

[search-label-windows]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Awindows
[search-label-linux]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Alinux
[search-label-mac]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Amac
[search-label-documentation]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Adocumentation
[search-label-performance]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Aperformance
[search-label-security]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Asecurity
[search-label-api]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Aapi

[search-label-enhancement]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Aenhancement
[search-label-bug]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Abug
[search-label-help-wanted]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Ahelp%20wanted
[search-label-good-first-issue]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Agood%20first%20issue
[search-label-info-needed]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Ainfo%20needed
[search-label-needs-reproduction]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Aneeds%20reproduction
[search-label-blocked]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Ablocked
[search-label-duplicate]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Aduplicate
[search-label-wontfix]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Awontfix
[search-label-invalid]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Ainvalid
[search-label-do-we-want-this]: https://github.com/search?q=is%3Aopen+is%3Aissue+repo%3APegasysEng%2FPantheon+label%3Ado%20we%20want%20this
[search-label-work-in-progress]: https://github.com/search?q=is%3Aopen+is%3Apr+repo%3APegasysEng%2FPantheon+label%3Awork%20in%20progress
[search-label-requires-changes]: https://github.com/search?q=is%3Aopen+is%3Apr+repo%3APegasysEng%2FPantheon+label%3Arequires%20changes


[private@pegasys.tech]: mailto:private@pegasys.tech
[Gitter]: https://gitter.im/PegaSysEng/pantheon
[Wiki]: https://github.com/PegaSysEng/pantheon/wiki
[Debugging Wiki]: https://github.com/PegaSysEng/pantheon/wiki/Debugging
[CLA.md]: /CLA.md
[Code Reviews]: /docs/community/code-reviews.md