package org.neo4j.jdbc;

import java.io.IOException;
import java.util.Arrays;

import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.server.CommunityNeoServer;
import org.neo4j.server.WrappingNeoServer;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ServerConfigurator;
import org.neo4j.server.helpers.CommunityServerBuilder;
import org.neo4j.server.modules.RESTApiModule;
import org.neo4j.server.modules.ServerModule;
import org.neo4j.server.modules.ThirdPartyJAXRSModule;
import org.neo4j.server.web.WebServer;
import org.neo4j.test.ImpermanentGraphDatabase;

/**
 * @author Michael Hunger @since 25.10.13
 */
public class TestServer
{
    public static final int PORT = 7475;

    public static CommunityNeoServer startWebServer( int port, boolean auth )
    {
        try
        {
            CommunityNeoServer server = CommunityServerBuilder.server().onPort( port ).build();
            final WebServer webServer = server.getWebServer();
            if ( auth )
            {
                webServer.addFilter( new TestAuthenticationFilter(), "/*" );
            }
            server.start();
            return server;
        } catch(IOException ioe) {
            throw new RuntimeException( "Error starting server on port "+port+" with auth "+auth,ioe );
        }
    }
}
