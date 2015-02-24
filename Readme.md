# Primus Android Library

This is a very simple [Primus][primus] client for Android. I've only tested with Primus setup  using the [SockJS][sockjs-transformer] transformer.

## Installation

Add the following to your `build.gradle`.

```groovy
dependencies {
  compile 'io.cine:primus:0.0.2'
}
```

Ensure [Maven central](http://search.maven.org/) is included in your `build.gradle`. This should happen by default when building a project with Google's recommended Android IDE, [Android Studio](https://developer.android.com/sdk/installing/studio.html).

```
apply plugin: 'android'
buildscript {
  repositories {
    mavenCentral()
  }
}
repositories {
  mavenCentral()
}
```

Download primus-android to your application with `./gradlew build`.

## Usage

### Initialization

```java
import io.cine.primus.Primus;

public class MainActivity extends Activity {
  private Primus primus;
  private final String PRIMUS_URL = "http://example.com/primus";
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    primus = Primus.connect(this, PRIMUS_URL);
    Primus.PrimusOpenCallback openCallback = new Primus.PrimusOpenCallback() {
      @Override
      public void onOpen() {
        // Websocket open
      }
    };
    Primus.PrimusDataCallback dataCallback = new Primus.PrimusDataCallback() {
      @Override
      public void onData(JSONObject data) {
        //got data
      }
    });
    primus.setOpenCallback(openCallback);
    primus.setDataCallback(dataCallback);
  }
}
```

### Sending data

```java
try {
  JSONObject j = new JSONObject();
  j.put("some", "data");
  primus.send(j);
} catch (JSONException e) {
  e.printStackTrace();
}

```

### Ending
```java
  primus.end();
```

## Features

1. Auto reconnecting using exponential backoff
* heartbeat
* converting messages to a `JSONObject`
* waiting until connection is ready to send messages


<!-- external links -->
[primus]:https://github.com/primus/primus
[sockjs-transformer]:https://github.com/primus/primus#sockjs
