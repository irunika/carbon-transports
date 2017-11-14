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

import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketCloseMessage;
import org.wso2.carbon.transport.http.netty.contractimpl.websocket.WebSocketMessageImpl;

/**
 * Implementation of {@link WebSocketCloseMessage}.
 */
public class WebSocketCloseMessageImpl extends WebSocketMessageImpl implements WebSocketCloseMessage {

    private final CloseWebSocketFrame closeWebSocketFrame;

    public WebSocketCloseMessageImpl(CloseWebSocketFrame closeWebSocketFrame) {
        this.closeWebSocketFrame = closeWebSocketFrame;
    }

    @Override
    public int getCloseCode() {
        return closeWebSocketFrame.statusCode();
    }

    @Override
    public String getCloseReason() {
        return closeWebSocketFrame.reasonText();
    }

    @Override
    public void release() {
        closeWebSocketFrame.release();
    }
}
