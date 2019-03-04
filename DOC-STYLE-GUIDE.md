# Pantheon Documentation Style Guide

## Purpose of this Document

This document contains guidelines to ensure the Pantheon documentation is consistent and well organised.

This is a living document and will evolve to better suit Pantheon users and contributors needs.

> **Note:** Although not everything in this style guide is currently followed in the Pantheon 
documention, these guidelines are intended to be applied when writing new content and revising 
existing content.

**The primary audience for this document is:**

*   Members of the Pantheon team
*   Developers and technical writers contributing to the Pantheon documentation

## Mission Statement

The Pantheon documentation contributes to a consistent and easy to understand experience for end users.
We're focused on creating a great experience for both new and expert users of Ethereum clients.

## General Guidelines

The guiding principles for the Pantheon documentation are: 
1. Be consistent
1. Keep it simple but technically correct
1. Be proactive and suggest good practices
1. Be informative and exhaustive.

### 1. Be Consistent

Consistency is important to help our end users build a mental model of how Pantheon works.
By being consistent with our word choices, visual formatting, and style of communication it helps 
users know what to expect when they refer to or search Pantheon documentation.  

### 2. Keep It Simple But Technically Correct

Avoid technical jargon and always assume our end users may not be Ethereum experts.

This doesn't mean explaining all Ethereum concepts in our documentation. Explain Pantheon functionality  
and when an understanding of complex Ethereum concepts is required refer users to relevant resources.

For example, to explain how the EVM works, link to ethdocs.org documentation such as 
http://ethdocs.org/en/latest/introduction/what-is-ethereum.html#ethereum-virtual-machine

Simple explanations must still be technically correct.

### 3. Be Proactive And Suggest Good Practices

Being proactive means anticipating user needs and guiding them through a process.
This most often takes the form of notes or tip messages alongside the main explanation.
Put yourself in the user's shoes and consider what questions you would have when reading the documentation.

Do not assume required steps are implied. Err on the side of including them if you are unsure. 

Documenting good practices is also important.
For example, instruct users to secure private keys and protect RPC endpoints a production environments. 

### 4. Be Informative But Concise 

We seek a balance between providing enough relevant information to help our users develop a solid 
mental model of how Pantheon works without forcing them to read too much text or redundant detail.

To provide additional detail, use sub-sections.

## Writing Style Guide

We use the [Microsoft Style Guide](https://docs.microsoft.com/en-us/style-guide/welcome/) as our general guide 
to writing technical documentation.
We take guidance from it but do not apply every rule.
For example, we use title case rather than sentence case.

The Micrsoft Style Guide aims for natural, simple, and clear communication.

Here are some important points we follow:
 
### Active Voice
Use active voice. Use _you_ to create a more personal friendly style. Avoid gendered pronouns (_he_ or _she_).

### Contractions
Use contractions. For example, don’t.

Use common contractions, such as it’s and you’re, to create a friendly, informal tone.

### Recommend
It's acceptable to use "we recommend" to introduce a product recommendation.
Don't use "PegaSys recommends" or "it is recommended."

Example: Instead of _This is not recommended for production code_ use _We don't recommend this for production code_.

### Directory vs Folder 
Use _directory_ over _folder_ because we are writing for developers. 

### Title Case For Headings
Use title case for headings.

Note: This is a case where we are not following the Microsoft Writing Style Guide. 

### Assumed Knowledge For Readers
We have two distinct audiences to consider when developing content:

- New to Ethereum and Ethereum clients
- Experienced with Ethereum clients other than Pantheon.

### Avoid Abbreviations

Try not to use abbreviations [except for well known ones and some jargon](MKDOCS-MARKDOWN-GUIDE.md#abbreviations).
Don't use "e.g." but use "for example".
Don't use "i.e." but use "that is".