package org.acme.library;


public class StartupObserver {

    public void onStartup() {
        throw new RuntimeException("Should not be called in GreetingResourceTest");
    }
}
