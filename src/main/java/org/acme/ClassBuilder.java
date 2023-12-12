package org.acme;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ClassBuilder {

    private String packageName;
    private String simpleName;
    private List<String> classAnnotations = List.of();
    private String parentClass;

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public ClassBuilder setSimpleName(String simpleName) {
        this.simpleName = simpleName;
        return this;
    }

    public ClassBuilder addClassAnnotation(String annotation) {
        if(classAnnotations.isEmpty()) {
            classAnnotations = new ArrayList<>(2);
        }
        classAnnotations.add(annotation);
        return this;
    }

    public ClassBuilder setApplicationScoped() {
        return addClassAnnotation("jakarta.enterprise.context.ApplicationScoped");
    }

    public ClassBuilder setEntity() {
        return addClassAnnotation("jakarta.persistence.Entity");
    }

    public ClassBuilder setParentClass(String parentClass) {
        this.parentClass = parentClass;
        return this;
    }

    public ClassBuilder setPanacheEntity() {
        return setEntity().setParentClass("io.quarkus.hibernate.orm.panache.PanacheEntity");
    }

    void generate(Path srcDir) throws IOException {
        if(this.getSimpleName() == null || this.getSimpleName().isEmpty()) {
            throw new IllegalArgumentException("Class name has not been initialized");
        }
        var clsDir = srcDir;
        if(this.getPackageName() != null && !this.getPackageName().isEmpty()) {
            for(var name : this.getPackageName().split("\\.")) {
                clsDir = clsDir.resolve(name);
            }
            Files.createDirectories(clsDir);
        }

        try(BufferedWriter writer = Files.newBufferedWriter(clsDir.resolve(this.getSimpleName() + ".java"))) {
            writer.append("package ").append(this.getPackageName()).append(";");
            writer.newLine();
            writer.newLine();

            // imports
            if(!classAnnotations.isEmpty()) {
                for(var a : classAnnotations) {
                    writer.append("import ").append(a).append(';');
                    writer.newLine();
                }
                writer.newLine();
            }
            if(parentClass != null && !parentClass.isEmpty()) {
                writer.append("import ").append(parentClass).append(';');
                writer.newLine();
                writer.newLine();
            }

            if(!classAnnotations.isEmpty()) {
                for(var a : classAnnotations) {
                    writer.append('@');
                    var i = a.lastIndexOf('.');
                    writer.append(i < 0 ? a : a.substring(i + 1));
                    writer.newLine();
                }
            }
            writer.append("public class ").append(this.getSimpleName());
            if(parentClass != null && !parentClass.isEmpty()) {
                var i = parentClass.lastIndexOf('.');
                writer.append(" extends ").append(i < 0 ? parentClass : parentClass.substring(i + 1));
            }
            writer.append(" {");
            writer.newLine();
            writer.append("}");
        }
    }
}
