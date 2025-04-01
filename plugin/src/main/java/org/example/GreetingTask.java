package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public class GreetingTask extends DefaultTask {

    private final Configuration config;

    @Inject
    public GreetingTask(Configuration config) {
        super();
        this.config = config;
    }

    @TaskAction
    public void run() {
        config.resolve();
    }
}
