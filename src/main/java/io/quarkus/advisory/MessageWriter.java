package io.quarkus.advisory;

public class MessageWriter {

    public static void info(Object o) {
        System.out.println(String.valueOf(o));
    }
}
