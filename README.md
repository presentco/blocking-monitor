# Present Unblock

To minimize request latency, [Google App Engine](https://cloud.google.com/appengine/docs/java/) 
developers should parallelize remote operations (using asynchronous methods) and minimize round 
trips over the network. Anyone who has developed an App Engine app can tell you noticing blocking 
operations before they become a problem is hard enough, let alone pinpointing their cause and fixing 
them. *Present Unblock* makes it easy!

Present Unblock intercepts calls to remote services (like Google Cloud Datastore) and logs an error 
when the total duration of blocking calls exceeds the deadline during a request. Unblock surfaces 
problems  early (at development time) and even points you to the code most likely at fault!

For example, if the total duration of remote calls that block during a request exceeds the deadline,
Unblock will log a message like this:

```
SEVERE: 12 of 12 (100%) async calls blocked for 1250ms total during 'Example'.
Result of example.bar blocked 8 times for 826ms total
	at example.Example.lambda$main$0(Example.java:24)
	at example.Example$$Lambda$1/1338668845.run(Unknown Source)
	at co.present.unblock.Unblock.monitor(Unblock.java:42)
	at example.Example.main(Example.java:19)
Result of example.foo blocked 4 times for 424ms total
	at example.Example.lambda$main$0(Example.java:22)
	at example.Example$$Lambda$1/1338668845.run(Unknown Source)
	at co.present.unblock.Unblock.monitor(Unblock.java:42)
	at example.Example.main(Example.java:19)
```

Google App Engine will automatically send you a notification that the error occurred.

### Dependencies

- App Engine
- Java 8

## Installation

### 1. Configure Maven.

Add Present Unblock to your `pom.xml`:

```xml
<dependency>
  <groupId>co.present</groupId>
  <artifactId>unblock</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

**Note:** Unblock is currently available in the Maven Central Snapshot repository. We'll release 1.0
to the main repo soon.

### 2. Configure your web app.

Add the servlet filter to your `web.xml`:

```xml
<filter>
  <filter-name>unblockFilter</filter-name>
  <filter-class>co.present.unblock.UnblockFilter</filter-class>
</filter>
<filter-mapping>
  <filter-name>unblockFilter</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>

```

### 3. Install the monitor.

Call `Unblock.install()` once at startup to hook App Engine's remote API calls.

### 4. Run your app.

Unblock will log information telling you which methods are blocking. Reduce blocking and latency by
caching, making better use of asynchronous and batch APIs, etc..

### Optional: Configuring the Error Threshold

By default, Unblock logs an error after calls block for 750ms during a request. You can
override this value with a system property. For example, in `appengine-web.xml`:

```xml
<system-properties>
  <property name="co.present.unblock.defaultDeadline" value="750"/>
</system-properties>
```

Unblock logs a warning when you exceed 2/3rds of the error deadline (500ms by default). To always 
log stacktraces for blocking calls, enable the FINE logging level.

License: [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt). 