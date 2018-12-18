description: Install Pantheon on Windows from Chocolatey package manager
<!--- END of page meta data -->

# Install Chocolatey

To install Chocolatey: 

1. Open **cmd.exe** as the administrative user by right-clicking on **cmd.exe** in the Start menu and selecting **Run as administrator**. 

2. Run: 

   ```bat
   @"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -InputFormat None -ExecutionPolicy Bypass -Command "iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))" && SET "PATH=%PATH%;%ALLUSERSPROFILE%\chocolatey\bin"
   ``` 
   
3. Display the Chocolatey help to confirm installation: 

   ```bat
   choco -?
   ```
   
For more information, refer to the [Chocolatey documentation](https://chocolatey.org/install). 