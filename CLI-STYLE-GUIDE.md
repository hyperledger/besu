# Besu Command Line Interface (CLI) Style Guide

## Purpose of this Document

This document contains guidelines to help the Besu command line interface (CLI) remain usable, modular, and extensible as it grows over time. This is a living document and should evolve to better suit end users and those who contribute to Besu.

> **Note:** Although not every pattern shown in this style guide is currently followed in Besu, it is our intention to revise and build new functionality with these guidelines in mind.

**The primary audience for this document is:**

*   Members of the Besu team
*   Developers contributing pull requests

## Mission Statement

The Besu CLI should create a consistent and easy to understand experience for end users. We're focused on creating a great developer experience for both new and expert users of Ethereum clients.

## General Guidelines

There are four guiding principles for the Besu CLI to help us create a good developer experience for both new and expert users: **_(1) be consistent, (2) keep it simple, (3) be proactive, and (4) be informative (to people and machines)._**

This section outlines what each of these principles mean and the following sections explain how these principles should be applied in specific scenarios.     

### 1. Be Consistent
Consistency is important to help our end users build a mental model of how Besu works. By being consistent with our word choices, visual formatting, and style of communication it helps users know what to expect as they interact with Besu.  

### 2. Keep it Simple
Avoid technical jargon and always assume our end users may have questions. This doesn't mean answering all of those questions in the CLI, but it does mean explaining things in a simple way and when complexity inevitably rises, directing our users to documentation that will help them.

### 3. Be Proactive
Being proactive means anticipating user needs and guiding them through a process. This most often takes the form of solution-oriented warning/error messages. Put yourself in the user's shoes and consider what questions you would have every time we are showing feedback or status to them.

### 4. Be Informative (to people and machines)
We seek a balance between providing enough relevant information to help our users develop a solid mental model of how Besu works without forcing them to read too much text. In addition, it is important we consider not only the end user of the CLI but to be consistent with formatting and feedback so information is easily interpreted by machines.

## User Input & Actions

### **Subcommands**
A subcommand is an action that can be taken on a single object (i.e. import, export, delete, etc.).

#### **Formatting and Grammar**
*   Commands should follow a noun-verb dialogue. This takes the form of `object action` in most cases.
*   Separate the object and action with a space.
*   If there is no easy way to avoid multiple words within an object or action, use a dash to separate the words (i.e. word1-word2).
*   Use commonly understood words (import, export, list, delete, add, etc.).
*   Be consistent with what words are already being used in other subcommands.

**Examples:**

`besu blocks import`

`besu public-key export`

Although noun-verb formatting seems backwards from a speaking perspective (i.e. blocks import vs. import blocks) it allows us to organize commands the same way users think about completing an action (the topic first, then the action).


#### **Inputs**

Using required options instead of arguments helps users have a clear understanding of the impact of an action. Inputs are most often verbs (from, to, etc.). Other options avoid the use of verbs to help make this distinction.

**Example:** `besu blocks import --from=<FILE>`

Requiring the `--from` option makes it clear where you are importing from. In the case of a single parameter (as shown in the example above) we should also accept this as an argument (`besu blocks import <FILE>`). Although we accept this formatting, it is not encouraged and should be excluded from our documentation.

### Flags

Flags are boolean and turn on or off some behavior.

**Formatting and Grammar**

*   Each flag should end with "enabled" regardless of the initial state to keep things consistent.
*   Each state should be supported in the CLI regardless of the default value in order to override a flag set in the configuration file.
*   Do not combine words, instead use a dash to separate them (i.e. `--word1-word2-enabled`).  

**Examples:**

If “foo” is disabled by default, `--foo-enabled` or `--foo-enabled=true` would turn it on.

If “foo” is enabled by default, `--foo-enabled=false` would turn it off.

**Use Smart Defaults**

Always take security into consideration when deciding the default value of a flag. If a flag will be enabled for the majority of use cases and there are no security concerns, it should be enabled by default. Otherwise, it should be disabled.


### Options

Options are used for settings, like specifying a configuration file or to provide input to a subcommand.

**Formatting and Grammar**

*   Always separate words with a dash and do not combine words even if it adds to the overall character count.
*   Avoid using verbs in option names unless they specify an input for a subcommand.
*   Specify what the expected value is at the end of the option (i.e. `--config-file` vs. `--config)`.

**Example:**`--option-file=<VALUE>`


## Adding New Inputs


### General Naming Guidelines

Words matter. Most users will not be interacting with Besu on a regular basis so we should name things for ease of understanding.

* Don't use abbreviations unless they are widely understood. Optimize for understanding, not number of characters.

* Consider existing word choices in other Ethereum clients and follow their lead if it makes sense.

    *   When using words from other clients, make sure to use them in the EXACT same context to avoid confusion.

* Consider what "group" a new addition will fit in when displayed on the `--help` screen. Follow the patterns that are already being used to keep things consistent.

>  **Note:** Grouping of options on the `--help` screen does not currently exist.


### Developer vs. End User

In general, creating hidden functionality is not ideal. However, if a new subcommand, flag or option has been created specifically for development purposes and has minimal use for end users, begin the name with  `--X` to indicate it's temporary and should not be documented.


### Consider Dependent Settings

No command, flag or option exists in a vacuum. The user is trying to accomplish a task and we should keep their entire workflow in mind as new things are added. Things to consider are:

*   What other options or flags may be impacted. Can they be combined to avoid extra work for the user and keep our documentation simple?
*   What error, success or warning messages need to be added to ensure the user has a clear understanding of what happened and what their next steps may be?

## Responding to User Input

> **Note:** The patterns and functionality in this section are not currently implemented.

Feedback to the user should always be clear, concise and avoid technical jargon. We use color and clear labels to help imply meaning. Color should only be used during TTY sessions and can be turned off with the `--color-enabled=false` flag.


### Input Warnings

Warnings should be used when an option has been successfully set but there may be other factors the user should take into consideration.

**Color/text formatting:**

*   **"WARNING" Label:** Bold, Bright - Yellow - ANSI 33
*   **Warning Message:** Bright - Yellow - ANSI 33
*   **Additional Message (Optional):** No formatting

There should be a clear line break before and after a warning to call attention to it. A second line containing more details or a potential solution is recommended but optional.


### Input Errors

When a value cannot be applied, show a clear error message and provide context on how the error can be avoided.

**Color/text formatting:**

*   **"ERROR" Label:** Bold, Red - ANSI 31
*   **Error Message:** Red - ANSI 31
*   **"Solution:" Label (Optional):** Bold
*   **Solution Message (Optional):** No formatting

There should be a clear line break before and after an error to help call attention to it. A second line containing a potential solution to fix the error is recommended but optional.

If the error is caused by an unknown option, the following formatting should be followed:

## Logging in the CLI

> **Note:** The patterns and functionality in this section are not currently implemented.

Displaying process should be a balance between human readability and ease of interpretation by machines. We remain as consistent as possible between the information shown in the CLI and what will be included in logs. Having said that, we use color and progress indicators to help users easily scan and interpret the information we are showing them while a TTY session is active. This formatting is optional and can be turned off by the user if desired.


### Spacing, Alignment, & Visual Formatting

Consistency in spacing is important for machines to easily interpret logs. Consistency in alignment and visual formatting is important for humans to easily scan and find relevant information.


#### General Format:

`<timestamp> | <log level> | <thread> | <class> | <message>`


#### Color & Text Formatting:

*   **Timestamp:** Dimmed
*   **Log Level - INFO:** Cyan - ANSI 96
*   **Log Level - ERROR:** Bold, Red - ANSI 31
*   **Log Level - WARNING:** Bold, Bright - Yellow - ANSI 33
*   **Info Message:** No formatting
*   **Highlighted Information:** Green - ANSI 32
*   **Error Message:** Bold, Red - ANSI 31
*   **Warning Message:** Bold, Bright - Yellow - ANSI 33
*   **Failure Message:** Bold, Red - ANSI 31
*   **Failure Reasons:** Dimmed
*   **Thread:** Cyan - ANSI 36
*   **Class:** Cyan - ANSI 36
*   **Vertical Bar Separator:** Dimmed

Color and formatting is used to help users to easily scan and find relevant information. Cyan is our default color making it easy for users to ignore. This makes red and yellow stand out. Green is used selectively to help call attention to specific information or values in messages.


#### Spacing & Alignment:

*   Each piece of information is separated by a vertical bar.
*   We keep consistent visual spacing between the log level and the thread/classes (7 spaces) so messages that relate to the same thread and class are visually grouped.
    *   **INFO** is followed by 3 spaces
    *   **ERROR** is followed by 2 spaces
    *   **WARNING** is followed by a single space
*   Spacing and alignment is the same in the command line and logs to ensure users have a consistent experience.


### Information

"Information" (i.e. "INFO")  is status of what is currently happening or has happened. The lack of an error or warning indicates that things are running smoothly. Information should be output with "stdout".

### Error

An "error" should be shown if a process cannot be completed. Errors should be output with "stderr". Errors should include a clear reason why the error has occurred.   

### Warning

A "warning" is shown when a process has been completed but it did not go as expected. Warnings should be output with "stderr". Warnings should include a clear reason why the warning has occured.

### Failure

A "failure" should be shown if a process cannot be completed. Details about the failure should follow the main failure statement.  Failures should be output with "stderr".

## Indicating Progress

>  **Note:** The patterns and functionality in this section are not currently implemented.

We should always indicate that progress is being made so the user knows a process has not stalled. Progress indicators should only be included for TTY sessions (not in output) and can be turned off with the `--progress-enabled=false` flag. The progress indicator should appear on a new line below the last line of information. Once the process is complete, the progress indicator will move down to the next empty line. A progress indicator is not needed if a process takes less than a second.
