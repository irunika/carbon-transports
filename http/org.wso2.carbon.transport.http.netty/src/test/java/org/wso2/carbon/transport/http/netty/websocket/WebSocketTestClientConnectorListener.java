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
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketBinaryMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketCloseMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketConnectorListener;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketControlMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketControlSignal;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketInitMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketTextMessage;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * WebSocket test class for WebSocket Connector Listener.
 */
public class WebSocketTestClientConnectorListener implements WebSocketConnectorListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTestClientConnectorListener.class);

    private String receivedTextToClient;
    private ByteBuffer receivedByteBufferToClient;
    private boolean isPongReceived = false;
    private boolean isIdleTimeout = false;

    @Override
    public void onMessage(WebSocketInitMessage initMessage) {
        // Not applicable
    }

    @Override
    public void onMessage(WebSocketTextMessage textMessage) {
        receivedTextToClient = textMessage.getText();
    }

    @Override
    public void onMessage(WebSocketBinaryMessage binaryMessage) {
        this.receivedByteBufferToClient = binaryMessage.getByteBuffer();
    }

    @Override
    public void onMessage(WebSocketControlMessage controlMessage) {
        if (controlMessage.getControlSignal() == WebSocketControlSignal.PONG) {
            this.isPongReceived = true;
        }
    }

    @Override
    public void onMessage(WebSocketCloseMessage closeMessage) {

    }

    @Override
    public void onError(Throwable throwable) {
        handleError(throwable);
    }

    @Override
    public void onIdleTimeout(WebSocketControlMessage controlMessage) {
        this.isIdleTimeout = true;
    }

    /**
     * Retrieve the latest text received to client.
     *
     * @return the latest text received to the client.
     */
    public String getReceivedTextToClient() {
        String tmp = receivedTextToClient;
        receivedTextToClient = null;
        return tmp;
    }

    /**
     * Retrieve the latest {@link ByteBuffer} received to client.
     *
     * @return the latest {@link ByteBuffer} received to client.
     */
    public ByteBuffer getReceivedByteBufferToClient() {
        ByteBuffer tmp = receivedByteBufferToClient;
        receivedByteBufferToClient = null;
        return tmp;
    }

    /**
     * Retrieve whether a pong is received client.
     *
     * @return true if a pong is received to client.
     */
    public boolean isPongReceived() {
        boolean tmp = isPongReceived;
        isPongReceived = false;
        return tmp;
    }

    /**
     * Check whether any idle timeout triggered or not.
     *
     * @return true if idle timeout is triggered.
     */
    public boolean isIdleTimeout() {
        boolean temp = isIdleTimeout;
        isIdleTimeout = false;
        return temp;
    }

    private void handleError(Throwable throwable) {
        log.error("Error handler received: " + throwable.getMessage());
    }

}
