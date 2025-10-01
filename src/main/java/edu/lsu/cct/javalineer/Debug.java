package edu.lsu.cct.javalineer;

import java.io.*;

public class Debug {
    public static void reallyPrintln(String s) {
        try {
            try(PrintWriter pw = new PrintWriter("/dev/tty")) {
                pw.println(s);
            }
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
