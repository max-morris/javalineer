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


## Exceptions in Tasks
Javalineer never prints, exits, or rethrows when a task throws. Instead, the exception is captured into the future
associated with the task, and the rest of the system keeps running:

- The futures returned by `runGuarded`, `runCondition`, `Pool.run`, `Loop.parFor`, `Loop.marchingFor`,
  `ReadWriteGuard.runReadGuarded`/`runWriteGuarded`, and the partitioned list operations complete exceptionally with
  the exception thrown by the task. A task which returns a `null` future where a future is required is treated as a
  task which threw a `NullPointerException`.
- The exception never escapes into unrelated code. In particular, a task given to an uncontended guard may run inline
  on the thread which called `runGuarded`, but its exception still goes to its future, not to that caller.
- Guards are always released, so tasks queued behind a failed task still run.
- A conditional task which throws is finished; it is not retried.
- `Loop.parFor` completes exceptionally as soon as any iteration fails, but the remaining iterations still run.
  `Loop.marchingFor` stops at the first failed iteration.

Because failures are only reported through futures, a future which nobody waits on will fail silently. To observe
every failure as it happens (e.g., for logging), install a handler:

```java
Pool.setTaskFailureHandler(t -> System.err.println("Task failed: " + t));
```

The handler is informational only: the failure is still captured into the task's future. It runs synchronously on
the thread which ran the failing task, possibly while that task's guards are still held, so it should be quick.

Raw `Runnable`s given directly to the executor with `Pool.execute` have no future. If one throws, its exception is
passed to the thread's uncaught exception handler, and the pool keeps running.

## Waiting at the End of a Program
A program which waits for its final future with `join()` hangs forever if that future can never complete: a
completion cycle (a future completed only by a continuation which waits for it, directly or through other futures),
a latch signaled fewer times than its count, or a condition which is never signaled. Every task has finished, so the
pool is idle, but nothing reports why. Wait with `Pool.join` instead:

```java
public static void main(String[] args) {
    Pool.join(run(args)); // like run(args).join(), but never hangs silently
}
```

`Pool.join(fut)` returns fut's value, or throws the same `CompletionException` as `fut.join()`. If the pool goes idle
while fut is still incomplete, it writes a report of the pending state to stderr and throws `DeadlockException`, so
the program ends with a failure instead of hanging. This relies on every future being completed by Javalineer tasks
(or by the waiting thread before it waits), not by some other thread; called from a task on the pool, it is the same
as `join()`.

The report names the awaited future. For more detail, set `-DJAVALINEER_TRACK_PENDING=true` (or call
`Pool.setTrackPending(true)` before the work starts): condition tasks (including latches) and any future registered
with `Pool.trackPending(fut, kind, owner)` are then listed while incomplete, with the stack which registered them.
Tracking is slow and meant for debugging. `-DJAVALINEER_DEADLOCK_CHECK=false` turns the check off, making
`Pool.join(fut)` the same as `fut.join()`.

The pool returned by `Pool.getPool()` wraps the executor given to `Pool.setPool` (or chosen by `JAVALINEER_POOL`) to
count the tasks queued or running; give tasks to `Pool.getPool()`, not to the underlying executor, so they are
counted.
