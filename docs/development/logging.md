# Logging

This project employs the logging utility [Apache Log4j](https://logging.apache.org/log4j/2.x/),
accordingly levels of detail can be specified as follows:

```
OFF:	The highest possible rank and is intended to turn off logging.
FATAL:	Designates very severe error events that will presumably lead the application to abort.
ERROR:	Designates error events that might still allow the application to continue running.
WARN:	Designates potentially harmful situations.
INFO:	Designates informational messages that highlight the progress of the application at coarse-grained level.
DEBUG:	Designates fine-grained informational events that are most useful to debug an application.
TRACE:	Designates finer-grained informational events than the DEBUG.
ALL:	All levels including custom levels.
```

One mechanism of globally effecting the log output of a running client is though modification the file
`/besu/src/main/resources/log4j2.xml`, where it can be specified under the `<Property name="root.log.level">`.
As such, corresponding instances of information logs throughout the codebase, e.g. `log.fatal("Fatal Message!");`,
will be rendered to the console while the client is in use.

