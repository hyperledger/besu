# MkDocs And Markdown Guide

Pantheon documentation is written using [Markdown](https://daringfireball.net/projects/markdown/) syntax.

However, we use two flavors of this syntax:

- One for pages inside the [/docs](/docs) directory that will be rendered by [MkDocs] as described below
in the [Installed Markdown Extensions](#installed-markdown-extensions) section.
- Another using the [Github syntax](https://guides.github.com/features/mastering-markdown/) 
for pages outside of this documentation directory. These are mainly files to support our [open source 
community](https://github.com/PegaSysEng/pantheon/community).

## MkDocs Documentation Website

The [Pantheon documentation website](https://docs.pantheon.pegasys.tech/) is maintained by PegaSys from 
the content of the [/docs](/docs) directory.

### /docs Directory

The [/docs](/docs) directory in the Pantheon repository contains all the documentation that 
is generated into a static HTML website using [MkDocs] and the [Mkdocs Material] theme and hosted by [readthedocs.org].

The documentation is automatically updated using [WebHooks](https://docs.readthedocs.io/en/stable/webhooks.html)
linking GitHub to the [readthedocs.org] site when you merge a pull-request in the master branch of Pantheon.

The system also detects tags in the Github repository and takes care of making the latest stable release
and previous versions available.

If any issues occur, contact the maintainers of the [Pantheon documentation project](https://readthedocs.org/projects/pantheon/).

### MkDocs Configuration

[MkDocs] is a Python tool that generates the static HTML website that is published.

Our [MkDocs] setup uses a [Mkdocs Material] theme to render the html pages. It also comes with a number of useful extensions.

[MkDocs] in configured in the [mdkocs.yml](/mkdocs.yml) file.

This file configures:

- Site meta data and variables
- Theme configuration
- Page navigation
- Extensions
- Plugins

If you add pages to the documentation (rather than updating existing pages), update the "nav" section 
to add your page and specify the page name.

### Preview The Documentation

We recommended previewing your work locally before pushing your changes within a pull-request.
As the final documentation is build with [MkDocs], you have to build your docs locally with this tool 
to ensure the Markdown is correctly understood and displayed.

To preview Pantheon documentation locally: 

- [Install Python 3.x](https://www.mkdocs.org/#installing-python) (see [readthedocs.yml](readthedocs.yml) file for 
the exact version)
- [Install PIP](https://www.mkdocs.org/#installing-pip)
- Install all the required dependencies :
  ```bash
  pip install -r docs/requirements.txt
  ```
- Run the following command in the project directory :
  ```bash
  mkdocs serve
  ```
- Follow the link displayed on the output of this command that
looks like `[I 190206 18:48:47 server:298] Serving on http://127.0.0.1:8000`, 
here connect to [http://127.0.0.1:8000]

You can let this doc server run while you work on the doc, it updates the local website
automatically when you save changes in your Markdown files.

>**Important**
> 
> Run `python --version` to make sure you are using version indicated in [readthedocs.yml](readthedocs.yml) file.
>
> If you are updating from a previous Python version you will also have to run pip install again and 
check your path.

## Formatting Markdown For Doc Site

Final documentation rendering is essential, but the format of the Markdown files is also very important.

Formatting the Markdown code helps reviewers and writers easily navigate in the code and review your changes.

A few basic rules:

- Each file must contain a header composed of [meta-data](https://squidfunk.github.io/mkdocs-material/extensions/metadata/) 
and ended by a specific comment.
    Ex:
    ```markdown
    title: Installation overview
    description: Overview and requirements to install Pantheon
    <!--- END of page meta data -->
    ``` 
- [As for other code](https://google.github.io/styleguide/javaguide.html#s4.4-column-limit),
each line of Markdown code must be limited to 100 columns long to be readable on any editor.
Lines have to be wrapped without cutting the line in the middle of a word. A line break displays as a space.
- No HTML markup can be used inside a Markdown document.
We provide [a lot of extensions](#installed-markdown-extensions) that are able to do the same thing 
without HTML. If you think one is missing, just discuss it with the team on [Gitter] and we'll decide together 
if it's worth adding an extension.
- Only one first level title can be present on a page.
- Format tables so they are also readable in the source code.
You can quickly achieve this by using a tool like http://markdowntable.com/

## Installed Markdown Extensions

>**Important** 
> Extensions are only available for the docs under [/docs](/docs) directory.

As markdown can be a bit limited when it comes to some specific rendering of code, TOCs, and other documentation 
elements, we configured some extensions for these items.
Extensions enable you to use simple Markdown syntax to achieve some complex rendering.

>**Important** 
> Never use HTML tags directly in the Markdown files to try to render content.
For consistency reasons we only allow the specific renderings available in the extensions.

Here is a list of the available extensions:

### TOC

This extension automatically displays a table of content of the current page on the right side of the page. 
It displays titles to the third level (`###`). After the third level, titles won't be displayed in the TOC.

This extension also displays a link on the right of any title called "permalink".
This link can be used to point directly to the title from another website.

### Include

If you have content to be repeated on multiple pages, you can create it in a common page in and include 
it in all required pages.

Example: 
To include the content of the "test_accounts.md" page in the "/docs/global" directory in another page, use:
```markdown
{!global/test_accounts.md!}
```

### Admonition

The [Admonition extension](https://squidfunk.github.io/mkdocs-material/extensions/admonition/#admonition) 
enables information, warning, and danger blocks.

Example:
````markdown
!!! note
    This is a multi line note
    in the Pantheon documentation. 
```` 

The 4 spaces indentation is required for the content to be part of the admonition.

We generally use the following [types](https://squidfunk.github.io/mkdocs-material/extensions/admonition/#types)
in our documentation:

- **Note** : Used to add information about a subject that doesn't directly needs to be taken in account 
to use this specific part.
Example: "Available since v0.8.3"
- **Abstract** : Used at the beginning of a long article. Also known as "TL;DR", this can help users jump 
into the content knowing that they will find their answer somewhere in the page.
- **Info** : Used to provide detail about a specific part of the documentation.
Ex: "The miner coinbase account is one of the accounts defined in the genesis file."
- **Tip** : Used to provide information that could help improve the use of the tool, make it faster.
Example: "To restart the private network in the future, start from 4. Restart First Node as Bootnode."
- **Warning** : Used to warn the users about something important.
Ex: "This will be deprecated in next version."
- **Danger** : Used to alert the user about a potential dangerous effect such as a risk of destroying 
something or losing assets.
Ex: "Never use the development private keys for production use".
- **Example** : used to display an example. We usually use it with a single or tabbed code-block:
````markdown
!!! example
    This example shows how to use the `net_listening` RPC method.
    
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"net_listening","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"net_listening","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : true
    }
    ```
```` 

### Footnotes

The [Footnotes extension](https://squidfunk.github.io/mkdocs-material/extensions/footnotes/) enables 
adding footnotes.

Footnotes display content at the end of the page and a numbered link inside the text to go to this content.
The extension also adds a link at the end of the footnote back to the text.

### Definitions List

The [def_list extension](https://python-markdown.github.io/extensions/definition_lists/) enables listing definitions
directly in the Markdown.  

### Abbreviations

Generally we avoid the use of abbreviations but some like "PoW" for proof-of-work or "dApp" for 
decentralized application are now part of the Ethereum jargon. The [Abbreviation extension](https://python-markdown.github.io/extensions/abbreviations/)
enables a tool tip hint to be provided for abbreviations.

Place abbreviations at the beginning of the Markdown file just after the meta-data header.

Example:
```markdown
description: This is an example page
<!--- END of page meta data -->

*[PoA]: Proof of Work

Clique is a PoA mechanism used in the Rinkeby test network
```

### Math

[Arithmatex extension](https://squidfunk.github.io/mkdocs-material/extensions/pymdown/#arithmatex-mathjax )
enables writing math formulas in the documentation using the provided syntax.

Example:
```markdown
$\sigma=\displaystyle\prod_{k=1}^t\sigma_{i_k}^{L_{i_k}(0)}$

Constructing the threshold signature $\sigma$ from $t$ individual 
signatures $\sigma_{i_k}$, $k=1,\dots,t$ and the Lagrange polynomials 
$L_{i_1}, \dots,L_{i_t}$ associated to the set $I=\{i_1,\dots,i_t\}$ of signers.
```

### Better Emphasis

The [Betterem extension](https://facelessuser.github.io/pymdown-extensions/extensions/betterem/) is 
automatically applied to your bold and italic content.

### Keyboard Shortcuts

The [Keys syntax](https://facelessuser.github.io/pymdown-extensions/extensions/keys/) extension enables 
displaying keyboard shortcuts. 

Example:
```markdown
++ctrl+C++
```

### Folding Details Blocks

You can use a folding block to hide content. The block can be closed by default or open.
This pattern helps reduce the content length and enables a faster overview of the whole page.

Ex:
```markdown
???+ note "Folding details"
    This is the detail of my content.
    The plus sign makes it unfolded by default.
    Remove the plus sign and it will be folded by default.
```

### Emojis

Emojis are fun, but they can also be useful to draw the reader's attention.
Try to use only neutral emojis like :warning:

Refer to a [supported full list of available emojis](https://www.webfx.com/tools/emoji-cheat-sheet/)
to find the suitable code.

Example:
`:warning:` displays :warning:

### Magic Links

If you want an URL to be displayed as a link, simply write it and this extension automatically displays it as 
a link. You don't need to surround it with Markdown links syntax.

### Highlight Content

The [Mark extension](https://facelessuser.github.io/pymdown-extensions/extensions/mark/) enables highlighting of 
content. 

Text surrounded by double equals is highlighted in yellow.

Example:
```markdown
==This is highlighted text==
```

### Strike Through

The [tilde syntax](https://squidfunk.github.io/mkdocs-material/extensions/pymdown/#tilde) extensions enables text
strike through to be displayed.

Example:
```markdown
~~This is the wrong way to do~~
```

### Symbols

The [Smart symbols syntax](https://facelessuser.github.io/pymdown-extensions/extensions/smartsymbols/) enables 
the inclusion of symbols. 

Ex:
`-->` will draw a nice right arrow.

### Task lists

The [Task list syntax](https://squidfunk.github.io/mkdocs-material/extensions/pymdown/#tasklist) extension 
enables displaying a list as a checklist. 

### Code Samples And Examples With [SuperFences]

#### Format The Code

For writing code examples inside the documentation, refer to the developer style guides:

- Java : refer to our [coding convention](CODING-CONVENTIONS.md).
- JSON : use https://jsonformatter.curiousconcept.com/ to format your JSON code.
- TOML : we follow version 0.5.0 language definition.
- JavaScript : see [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html).

#### Including Code in the Documentation

We use code-blocks provided by the [SuperFences] extension to present code samples and examples in the
documentation.

A basic code-block uses triple back ticks and the language name to enable syntax highlighting.

For example, a JSON result is written as:

````markdown
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": true
}
```
````

#### Tabbed Code Blocks

[SuperFences] enables additional functionality such as the tabbed code-block.

For example, to group the usage syntax and a usage example in the same block with tabs:

````markdown
```bash tab="Syntax"
$ pantheon rlp encode [--from=<FILE>] [--to=<FILE>] [--type=<type>]
```

```bash tab="File Example"
$ pantheon rlp encode --from=ibft_extra_data.json --to=extra_data_for_ibft_genesis.txt --type=IBFT_EXTRA_DATA
```

```bash tab="Standart Input/Output Example"
$ cat extra_data.json | pantheon rlp encode > rlp.txt
```
````

#### Line Numbers On Long Code Samples

[SuperFences] can also add [line numbers](https://facelessuser.github.io/pymdown-extensions/extensions/superfences/#showing-line-numbers)
to the code sample which makes it easier when discussing the code the sample.

The line numbers will only appear on the code block that uses the `linenums="1"` parameter.

Example:
````markdown
    ```javascript linenums="1"
    // A very long javascript sample code
    ```
```` 

#### Code Syntax Highlight

Codehilite extension enables automatic syntax highlighting of code blocks. Define the language after
the code block delimiter to ensure correct highlighting.
If you don't provide the language name, the extension attempts to automatically discover it 
but this can lead to errors. 

Example:
````markdown
```json
{
  "jsonrpc" : "2.0",
  "id" : 51,
  "result" : {
    "startingBlock" : "0x5a0",
    "currentBlock" : "0xad9",
    "highestBlock" : "0xad9"
  }
}
```
```` 

Pygment is the implementation for this extension, refer to Pygment website for a 
[list of the supported languages](http://pygments.org/languages/).



[MkDocs]: https://www.mkdocs.org/
[readthedocs.org]: https://readthedocs.org/
[Mkdocs Material]: https://squidfunk.github.io/mkdocs-material/
[Gitter]: https://gitter.im/PegaSysEng/pantheon
[SuperFences]: https://squidfunk.github.io/mkdocs-material/extensions/pymdown/#superfences