/**
 *
 * Copyright (c) 2014, Deem Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.deem.zkui.utils;

import java.net.Socket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum CmdUtil {

    INSTANCE;
    private final static Logger logger = LoggerFactory.getLogger(CmdUtil.class);

    public String executeCmd(String cmd, String zkServer, String zkPort) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(zkServer, Integer.parseInt(zkPort)), 1000);
            socket.setSoTimeout(1000);
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 InputStreamReader reader = new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                out.print(cmd);
                out.flush();
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (sb.length() + count > 1024 * 1024 || System.nanoTime() > deadline) {
                        throw new IOException("Monitor response exceeds limits");
                    }
                    sb.append(buffer, 0, count);
                }
            }
        }
        return sb.toString();
    }
}
