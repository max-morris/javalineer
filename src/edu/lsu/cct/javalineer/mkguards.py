with open("x","w") as fd:
    print(end="",file=fd)

def m(s,kmax,comma):
    r = ""
    for k in range(1,kmax+1):
        if comma and k != 1:
            r += ", "
        r += s.format(k=k)
    return r

def l(s,kmax):
    return m(s,kmax,False)

def c(s,kmax):
    return m(s,kmax,True)

for i in range(2,20):
    decls = l("            final GuardVar<T{k}> gv{k},\n",i)
    tlist = c("T{k}",i)
    adds = l("        ts.add(gv{k});\n",i)
    sets = l("        c.set{k}(gv{k}.var);\n",i)
    runl = c("gv{k}.var",i)
    args = c("t{k}",i)
    setvars = l("    public void set{k}(Var<T{k}> t{k}) {{ this.t{k} = t{k}; }}",i)
    varfields = l("    Var<T{k}> t{k};\n",i)
    vlist = c("Var<T{k}> t{k}",i)
    gvlist = c("gv{k}",i)
    olist = c("o{k}",i)
    oolist = l("        final var o{k} = new AtomicReference<Optional<Var<T{k}>>>();\n",i)

    with open("x","a") as fd:
        print(f"""
    public static <{tlist}> void now({decls} final OptionalGuardTask{i}<{tlist}> c) {{
{oolist.rstrip()}

        CompletableFuture.allOf({c("setNow(gv{k},o{k})",i)})
                         .thenRun(() -> Guard.runAlways(new TreeSet<>() {{{{ {l("add(gv{k});",i)} }}}},
                                  () -> c.run( {c("o{k}.get()",i)} )));
    }}
    public static <{tlist}> CompletableFuture<Void> runCondition(
{decls.rstrip()}
            final CondCheck{i}<{tlist}> c) {{
        return Guard.runCondition({gvlist},new CondTask{i}<{tlist}>(c));
    }}
    public static <{tlist}> CompletableFuture<Void> runCondition(
{decls.rstrip()}
            final CondTask{i}<{tlist}> c) {{
        TreeSet<Guard> ts = new TreeSet<>();
{adds.rstrip()}
{sets.rstrip()}
        return runCondition(ts,c);
    }}
    public static <{tlist}> void runGuarded(
{decls.rstrip()}
            final GuardTask{i}<{tlist}> c) {{
        final TreeSet<Guard> ts = new TreeSet<>();
{adds.rstrip()}
        Guard.runGuarded(ts, () -> c.run({runl}));
    }}

    public static <{tlist}> void runGuardedEtAl(
{decls.rstrip()}
            final GuardTask{i}<{tlist}> c) {{
        final TreeSet<Guard> ts = new TreeSet<>();
        ts.addAll(GuardTask.GUARDS_HELD.get());
{adds.rstrip()}
        Guard.runGuarded(ts, () -> c.run({runl}));
    }}
""",file=fd)
    with open(f"CondTask{i}.java","w") as fd:
        print(f"""
package edu.lsu.cct.javalineer;

public class CondTask{i}<{tlist}> extends CondTask {{
{varfields.rstrip()}
{setvars.rstrip()}

    public final CondCheck{i}<{tlist}> check;
    public CondTask{i}(CondCheck{i}<{tlist}> check) {{
        this.check = check;
    }}

    public final void run() {{
        if (!done) {{
            try {{
                done = check.check({args});
                if (done) {{
                    fut.complete(null);
                }}
            }} catch (Exception e) {{
                done = true;
                fut.completeExceptionally(e);
            }}
        }}
    }}
}}
""",file=fd)
    with open(f"CondCheck{i}.java","w") as fd:
        print(f"""
package edu.lsu.cct.javalineer;

public interface CondCheck{i}<{tlist}> {{
    public boolean check({vlist});
}}
""",file=fd)
    with open(f"GuardTask{i}.java","w") as fd:
        print(f"""
package edu.lsu.cct.javalineer;

public interface GuardTask{i}<{tlist}> {{
    void run({vlist});
}}
""",file=fd)
    with open(f"OptionalGuardTask{i}.java","w") as fd:
        print(f"""
package edu.lsu.cct.javalineer;

import java.util.Optional;

public interface OptionalGuardTask{i}<{tlist}> {{
    void run({c("Optional<Var<T{k}>> o{k}",i)});
}}
""",file=fd)
