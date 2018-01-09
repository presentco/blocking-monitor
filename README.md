# Present Unblock

To minimize request latency, [Google App Engine](https://cloud.google.com/appengine/docs/java/) 
developers should parallelize remote operations 
(using asynchronous methods) and minimize round trips over the network. Anyone who has developed
an App Engine app can tell you noticing blocking operations before they become a problem is hard
enough, let alone pinpointing and fixing them.

*Present Unblock* proxies remote services (like Google Cloud Datastore) and logs an error when the 
number of blocking calls exceeds the threshold during a request. Unblock surfaces problems early
(at development time) and even points you to the code most likely at fault!

For example, if the number of remote calls that block during a request exceeds the threshold,
Unblock will log a message like this:

```
SEVERE: 12 of 12 (100%) async calls blocked in 'Example'.
Result of Foo.b() blocked 8 times
	at co.present.LogExample.lambda$main$0(LogExample.java:17)
	at co.present.LogExample$$Lambda$1/122883338.run(Unknown Source)
	at co.present.unblock.Unblock.monitor(Unblock.java:48)
	at co.present.LogExample.main(LogExample.java:13)
Result of Foo.a() blocked 4 times
	at co.present.LogExample.lambda$main$0(LogExample.java:16)
	at co.present.LogExample$$Lambda$1/122883338.run(Unknown Source)
	at co.present.unblock.Unblock.monitor(Unblock.java:48)
	at co.present.LogExample.main(LogExample.java:13)
```

Google App Engine will automatically send you a notification that the error occurred.

### Requirements

Present Unblock was designed for use with Google App Engine, but it doesn't depend on App Engine
and can be used in any app that wants to monitor whether or not `Future`s returned by an interface 
block.

Unblock currently requires Java 8. Let us know if you'd like us to support Java 7.

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

### 3. Proxy remote services.

Unblock intercepts and monitors methods that return `Future`. For example, you can configure 
[Objectify](https://github.com/objectify/objectify) to monitor datastore operations:

```java
ObjectifyService.setFactory(new ObjectifyFactory() {
  @Override public AsyncDatastoreService createAsyncDatastoreService(DatastoreServiceConfig cfg,
      boolean globalCache) {
    return Unblock.proxy(AsyncDatastoreService.class, super.createAsyncDatastoreService(cfg, globalCache));
  }
});

```

### 4. Run your app.

Unblock will log information telling you which methods are blocking! Reduce blocking and latency by \
making better use of asynchronous and batch APIs.

### Optional: Configuring the Error Threshold

By default, Unblock logs an error after **10** calls block during a request. Any more than 10 calls, 
depending on how long they block, and your request will probably exceed a 1s response time. You can
override this value with a system property. For example, in `appengine-web.xml`:

```xml
<system-properties>
  <property name="co.present.unblock.Unblock.ERROR_THRESHOLD" value="6"/>
</system-properties>
```

Unblock logs a warning when you exceed 2/3rds of the error threshold. To always log stacktraces for
blocking calls, enable the FINE logging level.

License: [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt). 