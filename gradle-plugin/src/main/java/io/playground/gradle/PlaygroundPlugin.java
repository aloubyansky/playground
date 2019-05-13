package io.playground.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class PlaygroundPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        System.err.println("Hello from Playground!");
    }
}
