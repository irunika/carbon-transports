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

package org.wso2.carbon.transport.http.netty.contractimpl.websocket.message;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketControlMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketControlSignal;
import org.wso2.carbon.transport.http.netty.contractimpl.websocket.WebSocketMessageImpl;

import java.nio.ByteBuffer;

/**
 * Implementation of WebSocket control message.
 */
public class WebSocketControlMessageImpl extends WebSocketMessageImpl implements WebSocketControlMessage {

    private final WebSocketFrame webSocketFrame;
    private final WebSocketControlSignal controlSignal;

    public WebSocketControlMessageImpl(WebSocketControlSignal controlSignal, WebSocketFrame webSocketFrame) {
        this.webSocketFrame = webSocketFrame;
        this.controlSignal = controlSignal;
    }

    @Override
    public WebSocketControlSignal getControlSignal() {
        return controlSignal;
    }

    @Override
    public byte[] getByteArray() {
        if (WebSocketControlSignal.IDLE_TIMEOUT.equals(controlSignal)) {
            return null;
        }
        ByteBuffer buffer = webSocketFrame.content().nioBuffer();
        byte[] bytes;
        if (buffer.hasArray()) {
            bytes = buffer.array();
        } else {
            int remaining = buffer.remaining();
            bytes = new byte[remaining];
            for (int i = 0; i < remaining; i++) {
                bytes[i] = buffer.get();
            }
        }
        return bytes;
    }

    @Override
    public ByteBuffer getPayload() {
        if (WebSocketControlSignal.IDLE_TIMEOUT.equals(controlSignal)) {
            return null;
        }
        return webSocketFrame.content().nioBuffer();
    }

    @Override
    public void release() {
        if (!WebSocketControlSignal.IDLE_TIMEOUT.equals(controlSignal)) {
            webSocketFrame.release();
        }
    }
}
