description: Install Pantheon from binary distribution
<!--- END of page meta data -->

# Install Binary Distribution

## Mac OS with Homebrew

### Prerequisites

* [Homebrew](https://brew.sh/)
* Java JDK

!!!attention
    Pantheon requires Java 11+ to compile; earlier versions are not supported. You can install Java using `brew cask install adoptopenjdk`. Alternatively, you can manually install the [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

### Install Using Homebrew

```bash
brew tap pegasyseng/pantheon
brew install pantheon
```
Display Pantheon command line help to confirm installation:

```bash
pantheon --help
```

## Linux / Unix / Windows

### Prerequisites

* [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

!!!attention
    Pantheon requires Java 11+ to compile; earlier versions are not supported.
    Pantheon is currently supported only on 64-bit versions of Windows, and requires a 64-bit version of JDK/JRE.
    We recommend that you also remove any 32-bit JDK/JRE installations.

!!! note "Linux Open File Limit"
    If synchronizing to MainNet on Linux or other chains with large data requirements, increase the maximum
    number of open files allowed using `ulimit`. If the open files limit is not high enough, a `Too many open files` RocksDB exception occurs.

### Install from Packaged Binaries

Download the Pantheon [packaged binaries](https://bintray.com/consensys/pegasys-repo/pantheon/_latestVersion#files).

Unpack the downloaded files and change into the `pantheon-<release>` directory.

Display Pantheon command line help to confirm installation:

```bash tab="Linux/macOS"
bin/pantheon --help
```

```bat tab="Windows"
bin\pantheon --help
```
