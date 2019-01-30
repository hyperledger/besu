description: Passing Java virtual machine JVM options to Pantheon at runtime
<!--- END of page meta data -->

# Passing JVM Options

To perform tasks such as attaching a debugger or configuring the garbage collector, pass JVM options to Pantheon.  

Pantheon passes the contents of the `PANTHEON_OPTS` environmental variable to the JVM.  Set standard JVM options in the `PANTHEON_OPTS` variable.  

For Bash-based executions, you can set the variable for only the scope of the program execution by setting it before starting Pantheon.

!!! example
    ```bash
    $ PANTHEON_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
    $ pantheon --network=rinkeby
    ```