description: Building Pantheon from source code
<!--- END of page meta data -->

# Build from Source

## Prerequisites

* [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

!!!important
    Pantheon requires Java 8+ to compile; earlier versions are not supported.

* [Git](https://git-scm.com/downloads) or [GitHub Desktop](https://desktop.github.com/)

## Running Locally

* [Installation on Linux / Unix / Mac OS X](#installation-on-linux-unix-mac-os-x)
* [Installation on Windows](#installation-on-windows)

## Running On Virtual Machine

* [Installation on VM](#installation-on-vm)


## Installation on Linux / Unix / Mac OS X

###Clone the Pantheon Repository

Clone the **PegaSysEng/pantheon** repo to your home directory (`/home/<user>`):

```bash
$ git clone --recursive https://github.com/PegaSysEng/pantheon.git
```

### Build Pantheon

After cloning, go to the `pantheon` directory.

Build Pantheon with the Gradle wrapper `gradlew`, omitting tests as follows:

```bash
$ ./gradlew build -x test
```

Go to the distribution directory: 
```bash
$ cd build/distributions/
```

Expand the distribution archive: 
```bash
$ tar -xzf pantheon-<version>.tar.gz
```

Move to the expanded folder and display the Pantheon help to confirm installation. 
````bash
$ cd pantheon-<version>/
$ bin/pantheon --help
````

!!! note "Linux Open File Limit"
    If synchronizing to MainNet on Linux  or other chains with large data requirements, increase the maximum number 
    of open files allowed using `ulimit`. If the open files limit is not high enough, a `Too many open files` RocksDB exception occurs. 

Continue with [Starting Pantheon](../Getting-Started/Starting-Pantheon.md).


## Installation on Windows

!!!note
    Pantheon is currently supported only on 64-bit versions of Windows, and requires a 64-bit version of JDK/JRE. 
    We recommend that you also remove any 32-bit JDK/JRE installations.

### Install Pantheon

In Git bash, go to your working directory for repositories. Clone the `PegaSysEng/pantheon` repo into this directory:

```bat
git clone --recursive https://github.com/PegaSysEng/pantheon
```

### Build Pantheon

Go to the `pantheon` directory:

```bat
cd pantheon
```

Open a Windows command prompt. Build Pantheon with the Gradle wrapper `gradlew`, omitting tests as follows:

```bat
.\gradlew build -x test
```

!!!note
    To run `gradlew`, you must have the **JAVA_HOME** system variable set to the Java installation directory.
    For example: `JAVA_HOME = C:\Program Files\Java\jdk1.8.0_181`.

Go to the distribution directory: 
```bat
cd build\distributions
```

Expand the distribution archive: 
```bat
tar -xzf pantheon-<version>.tar.gz
```

Move to the expanded folder and display the Pantheon help to confirm installation. 
```bat
cd pantheon-<version>
bin\pantheon --help
```

Continue with [Starting Pantheon](../Getting-Started/Starting-Pantheon.md).


## Installation on VM

You can run Pantheon on a virtual machine (VM) on a cloud service such as AWS or Azure, or locally using a VM manager such as [VirtualBox](https://www.virtualbox.org/).

If you set up your own VM locally using a VM manager such as [VirtualBox](https://www.virtualbox.org/), there are a few considerations:

* Make sure that Intel Virtualization Technology (VTx) and Virtualization Technology for Directed I/O (VT-d) are enabled in BIOS settings.

* On Windows, you might need to disable Hyper-V in the Windows Feature list.

It is recommended that you create a VM with the following attributes:

* Memory Size: Set to 4096 (recommended)

* Create a virtual hard disk with at least 10 GB; 20 GB is recommended

* Virtual hard disk file type: VDI (if you need to share it with other apps, use VHD)

* (Optional) You can create a shared directory in order to copy block files or genesis files from the host computer to the VM. For details on how to create a shared directory, see "Share Folders" in [Install Ubuntu on Oracle VirtualBox](https://linus.nci.nih.gov/bdge/installUbuntu.html).