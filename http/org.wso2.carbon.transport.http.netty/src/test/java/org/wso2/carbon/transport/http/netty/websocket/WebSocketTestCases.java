/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.transport.http.netty.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;
import org.wso2.carbon.transport.http.netty.config.TransportsConfiguration;
import org.wso2.carbon.transport.http.netty.config.YAMLTransportConfigurationBuilder;
import org.wso2.carbon.transport.http.netty.listener.HTTPServerConnector;
import org.wso2.carbon.transport.http.netty.util.TestUtil;
import org.wso2.carbon.transport.http.netty.util.client.websocket.WebSocketClient;
import org.wso2.carbon.transport.http.netty.util.client.websocket.WebSocketTestConstants;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.net.ssl.SSLException;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Test class for WebSocket Upgrade
 */
public class WebSocketTestCases {

    Logger logger = LoggerFactory.getLogger(WebSocketTestCases.class);

    private final String host = "localhost";
    private final int port = 8490;
    private final String subprotocolJson = "json";
    private final String subprotocolXML = "xml";
    private final String subprotocolUnknown = "unknown";
    private final boolean allowExtensions = true;
    private final int threadSleepTimeInMilliSeconds = 100;

    private String url = System.getProperty("url", String.format("ws://%s:%d/%s",
                                                                 host, port, "test"));
    private List<HTTPServerConnector> serverConnectors;
    private WebSocketClient primaryClient = new WebSocketClient(url);
    private WebSocketClient secondaryClient = new WebSocketClient(url);

    @BeforeClass
    public void setup() {
        logger.info("\n-------WebSocket Test Cases-------");
        TransportsConfiguration configuration = YAMLTransportConfigurationBuilder
                .build("src/test/resources/simple-test-config/netty-transports.yml");
        serverConnectors = TestUtil.startConnectors(configuration, new WebSocketMessageProcessor());
    }

    @Test(description = "Test WebSocket handshake.")
    public void handshakeTest() throws URISyntaxException, SSLException {
        try {
            assertTrue(primaryClient.handhshake(null, true));
            logger.info("Handshake test completed.");
        } catch (InterruptedException e) {
            logger.error("Handshake interruption.");
            assertTrue(false);
        }
    }

    @Test(description = "Test WebSocket Test Message Delivery")
    public void testText() throws URISyntaxException, InterruptedException, SSLException {
        primaryClient.handhshake(null, true);
        String textSent = "test";
        primaryClient.sendText(textSent);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        String textReceived = primaryClient.getTextReceived();
        assertEquals("Not received the same text.", textReceived, textSent);
        logger.info("pushing and receiving text data from server completed.");
        primaryClient.shutDown();
    }

    @Test(description = "Test WebSocket Binary Message Delivery")
    public void testBinary() throws InterruptedException, URISyntaxException, SSLException {
        primaryClient.handhshake(null, true);
        byte[] bytes = {1, 2, 3, 4, 5};
        ByteBuffer bufferSent = ByteBuffer.wrap(bytes);
        primaryClient.sendBinary(bufferSent);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        ByteBuffer bufferReceived = primaryClient.getBufferReceived();
        assertTrue("Buffer capacity is not the same.",
                   bufferSent.capacity() == bufferReceived.capacity());
        assertEquals("Buffers data are not equal.", bufferReceived, bufferSent);
        logger.info("pushing and receiving binary data from server completed.");
        primaryClient.shutDown();
    }

    /*
    Primary client is the one who is checking the connections of the Server.
    When secondary server is connecting to the endpoint, message will be sent to the primary
    client indicating the state of the secondary client.
     */
    @Test(description = "Test successful connection using two WebSocket clients.")
    public void testClientConnected() throws InterruptedException, SSLException, URISyntaxException {
        primaryClient.handhshake(null, true);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        secondaryClient.handhshake(null, true);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        String textReceived = primaryClient.getTextReceived();
        logger.info("Received text : " + textReceived);
        assertEquals("New Client was not connected.",
                     textReceived, WebSocketTestConstants.NEW_CLIENT_CONNECTED);
        logger.info("New client successfully connected to the server.");
        secondaryClient.shutDown();
        primaryClient.shutDown();
    }

    /*
    Primary client is the one who is checking the connections of the Server.
    When secondary server is closing the connection, message will be sent to the primary
    client indicating the state of the secondary client.
     */
    @Test(description = "Test successful disconnection using two WebSocket clients.")
    public void testClientCloseConnection() throws InterruptedException, URISyntaxException, SSLException {
        primaryClient.handhshake(null, true);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        secondaryClient.handhshake(null, true);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        secondaryClient.shutDown();
        Thread.sleep(threadSleepTimeInMilliSeconds);
        String textReceived = primaryClient.getTextReceived();
        logger.info("Received Text : " + textReceived);
        assertEquals("Connection close is unsuccessful.", textReceived, WebSocketTestConstants.CLIENT_LEFT);
        logger.info("Client left the server successfully.");
        primaryClient.shutDown();
        secondaryClient.shutDown();
    }

    @Test(description = "Test WebSocket Pong Message Delivery.")
    public void testPongMessage() throws InterruptedException, SSLException, URISyntaxException {
        primaryClient.handhshake(null, true);
        byte[] bytes = {6, 7, 8, 9, 10, 11};
        ByteBuffer bufferSent = ByteBuffer.wrap(bytes);
        primaryClient.sendPong(bufferSent);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        ByteBuffer bufferReceived = primaryClient.getBufferReceived();
        assertEquals("Didn't receive the correct pong.", bufferReceived, bufferSent);
        logger.info("Receiving a pong message is completed.");
        primaryClient.shutDown();
    }

    @Test(description = "Test correct WebSocket subprotocol negotiation.")
    public void testSubprotocolNegotiation() throws InterruptedException, SSLException, URISyntaxException {
        String textSent = "subprotocol";
        Assert.assertTrue(primaryClient.handhshake(subprotocolJson + ", " + subprotocolXML, allowExtensions));
        Thread.sleep(threadSleepTimeInMilliSeconds);
        Assert.assertTrue(secondaryClient.handhshake(subprotocolXML, allowExtensions));
        secondaryClient.sendText(textSent);
        Thread.sleep(threadSleepTimeInMilliSeconds);
        String textReceived = secondaryClient.getTextReceived();
        Assert.assertEquals(textReceived, textSent);
        primaryClient.shutDown();
        secondaryClient.shutDown();
    }

    @AfterClass
    public void cleaUp() throws ServerConnectorException, InterruptedException {
        primaryClient.shutDown();
        secondaryClient.shutDown();
        serverConnectors.forEach(
                serverConnector -> {
                    serverConnector.stop();
                }
        );
    }
}
