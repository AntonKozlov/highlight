# Highlight demo

## Build

```
.../highlight$ mvn package
```

A bit misleading, it will create a .jar and also build native apps for 
 * highlight function provided
 * highlight mock for testing
 
Those are not packed into jar; so actually the whole highlight demo works when started with current working dir to be
project root.

## Run

Should be started from project root:

```
.../highlight$ java -jar target/highlight-1.0-SNAPSHOT.jar
```

## Tests

JUnit tests are in `ExternalHighlighterTest` class only. Can be started with:  

```
$ mvn test
```