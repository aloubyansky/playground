package com.jeeconf.quarkus.forex.h2;

import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import org.h2.tools.Server;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class H2DatabaseResource {

    private Server tcpServer;

    void onStart(@Observes StartupEvent event) {
    	System.out.println("onStart");
        try {
            tcpServer = Server.createTcpServer();
            tcpServer.start();
            System.out.println("[INFO] H2 database started in TCP server mode; server status: " + tcpServer.getStatus());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (tcpServer != null) {
            tcpServer.stop();
            System.out.println("[INFO] H2 database was shut down; server status: " + tcpServer.getStatus());
            tcpServer = null;
        }
    }
}