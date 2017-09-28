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

import org.testng.Assert;
import org.wso2.carbon.transport.http.netty.util.client.websocket.WebSocketTestClient;

import java.nio.ByteBuffer;

/**
 * Timeout assertion executor for WebSocket test cases.
 */
public class WebSocketTestCase {

    private int threadSleepTime = 100;
    private int messageDeliveryCountDown = 100;

    protected void setThreadSleepTime(int threadSleepTime) {
        this.threadSleepTime = threadSleepTime;
    }

    protected void setMessageDeliveryCountDown(int messageDeliveryCountDown) {
        this.messageDeliveryCountDown = messageDeliveryCountDown;
    }

    protected void assertWebSocketClientTextMessage(WebSocketTestClient client, String expected)
            throws InterruptedException {
        for (int j = 0; j < messageDeliveryCountDown; j++) {
            Thread.sleep(threadSleepTime);
            String textReceived = client.getTextReceived();
            if (textReceived != null) {
                Assert.assertEquals(textReceived, expected);
                return;
            }
        }
        Assert.assertEquals(client.getTextReceived(), expected);
    }

    protected void assertWebSocketClientTextMessage(WebSocketTestClientConnectorListener clientConnectorListener,
                                                    String expected) throws InterruptedException {
        for (int j = 0; j < messageDeliveryCountDown; j++) {
            Thread.sleep(threadSleepTime);
            String textReceived = clientConnectorListener.getReceivedTextToClient();
            if (textReceived != null) {
                Assert.assertEquals(textReceived, expected);
                return;
            }
        }
        Assert.assertEquals(clientConnectorListener.getReceivedByteBufferToClient(), expected);
    }

    protected void assertWebSocketClientBinaryMessage(WebSocketTestClient client, ByteBuffer bufferExpected)
            throws InterruptedException {
        ByteBuffer bufferReceived = null;
        for (int j = 0; j < messageDeliveryCountDown; j++) {
            Thread.sleep(threadSleepTime);
            bufferReceived = client.getBufferReceived();
            if (bufferReceived != null) {
                Assert.assertEquals(bufferReceived, bufferExpected);
                return;
            }
        }
        Assert.assertEquals(bufferReceived, bufferExpected);
    }

    protected void assertWebSocketClientBinaryMessage(WebSocketTestClientConnectorListener clientConnectorListener,
                                                      ByteBuffer bufferExpected) throws InterruptedException {
        ByteBuffer bufferReceived = null;
        for (int j = 0; j < messageDeliveryCountDown; j++) {
            Thread.sleep(threadSleepTime);
            bufferReceived = clientConnectorListener.getReceivedByteBufferToClient();
            if (bufferReceived != null) {
                Assert.assertEquals(bufferReceived, bufferExpected);
                return;
            }
        }
        Assert.assertEquals(bufferReceived, bufferExpected);
    }

    protected void assertWebSocketClientPongMessage(WebSocketTestClientConnectorListener clientConnectorListener)
            throws InterruptedException {
        for (int j = 0; j < messageDeliveryCountDown; j++) {
            Thread.sleep(threadSleepTime);
            if (clientConnectorListener.isPongReceived()) {
                Assert.assertTrue(true);
                return;
            }
        }
        Assert.assertTrue(false);
    }
}
