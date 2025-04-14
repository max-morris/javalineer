# Javalineer: A Non-Blocking Synchronization Library for Java
Javalineer is a Java implementation of Guards (todo: publication goes here). In short, guards provide mutual exclusion
with implicit task queues. Unlike locks, guards are composable, prevent deadlock, and are cooperative with fixed- or
limited-size thread pools.

## Quick Start
[JitPack](https://jitpack.io/#max-morris/javalineer) is the easiest way to integrate the latest version of Javalineer
into your project.

If you are using Gradle, add the following to your `build.gradle`:
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.max-morris:javalineer:<TAG OR COMMIT HASH>'
}
```

For information, and instructions for other build systems, please follow the JitPack link.

## Using Javalineer
The fundamental structure in Javalineer is called the Guard. Like a lock, a guard controls access to a critical section
of code. Unlike locks, guards have no explicit lock and unlock operations. Instead, tasks are submitted to an implicit
queue with `runGuarded()`.

```java
var g = new Guard();

Guard.runGuarded(g, () -> {
    // Critical section that depends on the guard 'g'.
});
```
If a critical section should require more than 1 guard, use `GuardSet` with the following form.
```java
var g1 = new Guard(), g2 = new Guard();

Guard.runGuarded(GuardSet.of(g1, g2), () -> {
    // Critical section that depends on the guards 'g1' and 'g2'.
});
```

For the best performance, you should avoid constructing any particular `GuardSet` more than once; it's better to 
construct it once with `GuardSet.of()` and cache it for later use. `GuardSet` maintains a thread-local cache of its own
for this purpose, which you can opt into by using `GuardSet.ofCached()` instead of `GuardSet.of()`.

`GuardVar` is a guard which protects access to a variable.
```java
var guardedInt = new GuardVar<>(0);

Guard.runGuarded(guardedInt, intVar -> {
    // Critical section that depends on the guard 'guardedInt', allowing us to access 'intVar'.
});

var gv1 = new GuardVar<>(0), gv2 = new GuardVar<>(0);

Guard.runGuarded(gv1, gv2, (v1, v2) -> {
    // Access 'v1' and 'v2'
});
```
You can create a condition variable (`CondContext`) from one or more `GuardVar`s, and submit tasks to them.
Tasks submitted in this way must return a boolean which indicates whether the task is complete (true) or needs to be
re-run later (false). These tasks have access to the underlying data in the `GuardVar`s used to construct them.

```java
var gv1 = new GuardVar<>(0), gv2 = new GuardVar<>(0);
var bothNonZero = Guard.newCondition(gv1, gv2);

Guard.runCondition(bothNonZero, (v1, v2) -> {
    if (v1.get() == 0 || v2.get() == 0) {
        return false; // Re-run this task later.
    }
    
    // Do something with v1 and v2...
        
    return true; // Task is done.
});

bothNonZero.signal(); // Re-run one pending task.
bothNonZero.signalAll(); // Re-run all pending tasks.
```

