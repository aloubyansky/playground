package io.quarkus.advisory;

public class MessageWriter {

    public static void info(Object o) {
        System.out.println(String.valueOf(o));
    }

    public static void warn(Object o) {
        System.out.println("[WARN] " + String.valueOf(o));
    }
}
