package org.acme;

public interface Context {

    ClassBuilder newClassBuilder();

    default ClassBuilder newClassBuilder(String simpleName) {
        return newClassBuilder().setSimpleName(simpleName);
    }
}
