# jetty-manager

Connects to a running Jetty, allows you to stop, start or restart individual webapps/servlets.

```
sudo -u username jetty-manager

Usage:
  jetty-manager jvms
  jetty-manager webapps <jvm> [ <webappfilter> ]
  jetty-manager threads <jvm>
  jetty-manager stop <jvm> <webappfilter>
  jetty-manager start <jvm> <webappfilter>
  jetty-manager restart <jvm> <webappfilter>
  jetty-manager (-h | --help)

Commands:
  jvms           Show the running JVMs (PID, name)
  webapps        Show the webapps hosted by <jvm> and their state
  threads        Show the total number of threads in the <jvm>

Arguments:
  <jvm>          JVM PID or regexp (matched against the JVM name)
  <webappfilter> regexp matched against the context path (URL)

Options:
  -h --help     Show this screen
```

## Example

```
$ jetty-manager jvms
JVM        DisplayName
---        ---
1340       org.jetbrains.idea.maven.server.RemoteMavenServer
1575       /usr/local/Cellar/jetty/9.2.1/libexec/start.jar --module=jmx,webapp,deploy,http jetty.port=8080
1213
```

```
$ sudo -u jetty jetty-manager webapps start.jar
Attaching to PID 1575 - /usr/local/Cellar/jetty/9.2.1/libexec/start.jar --module=jmx,webapp,deploy,http jetty.port=8080

HelloWorld Web Application
/javademo-1.0-SNAPSHOT
STARTED

test-classes
/test-classes
STARTED

HelloWorld Web Application
/javademo-1.1-SNAPSHOT
STARTED
```

```
$ sudo -u jetty jetty-manager restart start.jar /javademo
Attaching to PID 1575 - /usr/local/Cellar/jetty/9.2.1/libexec/start.jar --module=jmx,webapp,deploy,http jetty.port=8080
Calling STOP on /javademo-1.0-SNAPSHOT
Calling START on /javademo-1.0-SNAPSHOT
Calling STOP on /javademo-1.1-SNAPSHOT
Calling START on /javademo-1.1-SNAPSHOT
```

## Compile

- You'll need [docopt](https://github.com/docopt/docopt.java). The build script expects to find it at `../docopt.java/target/docopt-0.6.0-SNAPSHOT.jar`, adjust to fit your environment.
- `gradle jar`
- `gradle createRpm`. The RPM is very basic, it contains a wrapper script that supports MacOS and Linux. On Linux, the script expects java to be managed by the _alternatives_ system (see [here](http://alternatives.sourceforge.net/)).
