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

import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketBinaryMessage;
import org.wso2.carbon.transport.http.netty.contractimpl.websocket.WebSocketMessageImpl;

import java.nio.ByteBuffer;

/**
 * Implementation of {@link WebSocketBinaryMessage}.
 */
public class WebSocketBinaryMessageImpl extends WebSocketMessageImpl implements WebSocketBinaryMessage {

    private final BinaryWebSocketFrame binaryWebSocketFrame;

    public WebSocketBinaryMessageImpl(BinaryWebSocketFrame binaryWebSocketFrame) {
        this.binaryWebSocketFrame = binaryWebSocketFrame;
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return binaryWebSocketFrame.content().nioBuffer();
    }

    @Override
    public byte[] getByteArray() {
        ByteBuffer buffer = binaryWebSocketFrame.content().nioBuffer();
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
    public boolean isFinalFragment() {
        return binaryWebSocketFrame.isFinalFragment();
    }

    @Override
    public void release() {
        binaryWebSocketFrame.release();
    }
}
