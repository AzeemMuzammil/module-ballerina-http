package org.wso2.transport.http.netty.connectionpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.TransportsConfiguration;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contractimpl.HttpWsConnectorFactoryImpl;
import org.wso2.transport.http.netty.message.HTTPConnectorUtil;
import org.wso2.transport.http.netty.util.HTTPConnectorListener;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.server.HttpServer;
import org.wso2.transport.http.netty.util.server.initializers.SendChannelIDServerInitializer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.testng.Assert.assertTrue;

/**
 * Test case for testing the max active connections per pool configuration.
 */
public class ConnectionPoolMaxConnTestCase {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolMaxConnTestCase.class);

    private HttpServer httpServer;
    private HttpClientConnector httpClientConnector;

    @BeforeClass
    public void setup() {
        TransportsConfiguration transportsConfiguration = TestUtil.getConfiguration(
                "/simple-test-config" + File.separator + "netty-transports.yml");

        httpServer = TestUtil.startHTTPServer(TestUtil.HTTP_SERVER_PORT, new SendChannelIDServerInitializer(1000));

        HttpWsConnectorFactory connectorFactory = new HttpWsConnectorFactoryImpl();
        Map<String, Object> transportProperties = HTTPConnectorUtil.getTransportProperties(transportsConfiguration);
        transportProperties.put(Constants.MAX_ACTIVE_CONNECTIONS_PER_POOL, 5);

        httpClientConnector = connectorFactory.createHttpClientConnector(
                transportProperties,
                HTTPConnectorUtil.getSenderConfiguration(transportsConfiguration, Constants.HTTP_SCHEME));
    }

    @Test
    public void testMaxActiveConnectionsPerPool() {
        try {
            int noOfRequests = 15;
            Set<String> channelIDs = new HashSet<>();
            CountDownLatch[] countDownLatches = getLatchesArray(noOfRequests);
            HTTPConnectorListener[] responseListeners = new HTTPConnectorListener[noOfRequests];

            for (int i = 0; i < countDownLatches.length; i++) {
                responseListeners[i] = TestUtil.sendRequestAsync(countDownLatches[i], httpClientConnector);
            }

            for (int i = 0; i < responseListeners.length; i++) {
                String response = TestUtil.waitAndGetStringEntity(countDownLatches[i], responseListeners[i]);
                channelIDs.add(response);
                log.info("Response #" + (i + 1) + " received: " + response);
            }

            assertTrue(channelIDs.size() <= 10);
        } catch (Exception e) {
            TestUtil.handleException("IOException occurred while running testMaxActiveConnectionsPerPool", e);
        }
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException {
        TestUtil.cleanUp(new ArrayList<>(), httpServer);
    }

    private CountDownLatch[] getLatchesArray(int n) {
        CountDownLatch[] countDownLatches = new CountDownLatch[n];

        for (int i = 0; i < countDownLatches.length; i++) {
            countDownLatches[i] = new CountDownLatch(1);
        }

        return countDownLatches;
    }
}
