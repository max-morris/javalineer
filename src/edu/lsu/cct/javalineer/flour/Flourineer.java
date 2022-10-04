package edu.lsu.cct.javalineer.flour;

import de.flourlang.embedded.EvalContext;
import de.flourlang.lang.*;
import edu.lsu.cct.javalineer.Guard;
import edu.lsu.cct.javalineer.Pool;
import org.antlr.v4.runtime.misc.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.flourlang.lang.Type.BaseType.*;

public class Flourineer {

    static EvalContext flourCtx = new EvalContext();

    static Map<FunctionSignature, Function> guardMemberFns = new HashMap<>();
    static Archetype guardArchetype = new Archetype(
            "Guard",
            Map.of("guardInstance", new Type(OPAQUE)),
            guardMemberFns
    );

    static {
        // constructor<Guard>()
        flourCtx.createIntrinsicFunction("Guard", List.of(), vals -> {
            return new Val(new Table(Map.of("guardInstance", new Val(new ValOpaque<>(new Guard()))), guardArchetype));
        }, new Type(TABLE, new Type(guardArchetype)));

        // fn<Guard> runGuarded(this: Guard, a1: () -> void) -> void
        Function.createIntrinsicFunction(
                guardMemberFns,
                "runGuarded",
                List.of(new Type(TABLE, new Type(guardArchetype)), new Type(new Pair<>(List.of(), new Type(VOID)))),
                vals -> {
                    var guard = ((ValOpaque<Guard>) vals.get(0).asTable().getMap().get("guardInstance").getObj()).getVal();
                    var fn = vals.get(1).asFunction();
                    Guard.runGuarded(guard, () -> {
                        flourCtx.functionCall(fn, List.of());
                    });
                    return Val.VOID;
                },
                new Type(VOID)
        );
    }

    public static void main(String[] args) throws IOException  {
        var code = String.join("\n", Files.readAllLines(Path.of("flour/flourProgram.mj")));
        var res = flourCtx.eval(code);
        res.getErr().ifPresent(System.err::println);
        Pool.await();
    }
}
