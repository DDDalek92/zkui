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
package com.deem.zkui.controller;

import com.deem.zkui.dao.Dao;
import com.deem.zkui.utils.CsrfUtil;
import com.deem.zkui.utils.ImportSafety;
import com.deem.zkui.utils.ServletUtil;
import com.deem.zkui.utils.ZooKeeperUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.common.PathUtils;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = {"/import"})
public class Import extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("authName") == null
                || !ZooKeeperUtil.ROLE_ADMIN.equals(session.getAttribute("authRole"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Administrator role required for import");
            return;
        }
        try {
            Properties props = (Properties) getServletContext().getAttribute("globalProps");
            ServletFileUpload upload = new ServletFileUpload();
            upload.setSizeMax(2L * ImportSafety.MAX_BYTES);
            upload.setFileSizeMax(ImportSafety.MAX_BYTES);
            FileItemIterator items = upload.getItemIterator(request);
            Map<String, String> fields = new HashMap<>();
            String contents = "";
            int parts = 0;
            int files = 0;
            while (items.hasNext()) {
                if (++parts > ImportSafety.MAX_PARTS) throw new IOException("Too many import form parts");
                FileItemStream item = items.next();
                try (InputStream input = item.openStream()) {
                    String value = ImportSafety.read(input, item.isFormField() ? 8192 : ImportSafety.MAX_BYTES);
                    if (item.isFormField()) fields.put(item.getFieldName(), value);
                    else {
                        if (++files > 1) throw new IOException("Only one import file is allowed");
                        contents = value;
                    }
                }
            }
            String token = request.getHeader("X-CSRF-Token");
            if (token == null) token = fields.get("csrfToken");
            if (!CsrfUtil.valid(session, token)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                return;
            }
            if (contents.isEmpty()) {
                String source = fields.get("remoteUrl");
                if (source == null) source = fields.getOrDefault("scmServer", "")
                        + fields.getOrDefault("scmFileRevision", "") + "@" + fields.getOrDefault("scmFilePath", "");
                contents = ImportSafety.fetch(source, props);
            }
            com.deem.zkui.utils.PathPolicy paths = new com.deem.zkui.utils.PathPolicy(props);
            List<String> records = new ArrayList<>();
            int lineNumber = 0;
            for (String line : contents.split("\r?\n", -1)) {
                lineNumber++;
                if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                String path;
                if (line.startsWith("-")) path = line.substring(1);
                else {
                    int first = line.indexOf('=');
                    int second = line.indexOf('=', first + 1);
                    if (first < 1 || second <= first + 1) throw new IOException("Invalid import format at line " + lineNumber);
                    String parent = line.substring(0, first);
                    path = ("/".equals(parent) ? "" : parent) + "/" + line.substring(first + 1, second);
                }
                PathUtils.validatePath(path);
                try { paths.writable(path); }
                catch (com.deem.zkui.utils.PathPolicy.OutsideNamespaceException ex) {
                    throw new IOException("Import path is outside the permitted namespace");
                }
                if ("/".equals(path) || "/zookeeper".equals(path) || path.startsWith("/zookeeper/")) {
                    throw new IOException("Reserved import path at line " + lineNumber);
                }
                records.add(line);
                if (records.size() > 10000) throw new IOException("Import exceeds 10000 records");
            }
            if (records.isEmpty()) throw new IOException("Import contains no records");
            boolean overwrite = Boolean.parseBoolean(fields.getOrDefault("scmOverwrite", "false"));
            org.apache.zookeeper.ZooKeeper zk = ServletUtil.INSTANCE.getZookeeper(request, props);
            // Never create missing ancestors outside the configured namespace.
            if (zk.exists(paths.root(), false) == null) throw new IOException("Configured root does not exist");
            ZooKeeperUtil.INSTANCE.importData(records, overwrite, zk);
            // Values, filenames and URLs can contain secrets or markup; keep only a summary.
            new Dao(props).insertHistory((String) session.getAttribute("authName"), request.getRemoteAddr(),
                    "Imported " + records.size() + " entries; overwrite=" + overwrite);
            session.setAttribute("flashMsg", "Import Completed!");
            response.sendRedirect("/home");
        } catch (FileUploadBase.SizeLimitExceededException | FileUploadBase.FileSizeLimitExceededException | ImportSafety.TooLargeException ex) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Import exceeds input limits");
        } catch (FileUploadBase.FileUploadIOException ex) {
            if (ex.getCause() instanceof FileUploadBase.FileSizeLimitExceededException
                    || ex.getCause() instanceof FileUploadBase.SizeLimitExceededException) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Import exceeds input limits");
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid import upload");
            }
        } catch (ServletUtil.ZooKeeperUnavailableException ex) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "ZooKeeper is unavailable");
        } catch (FileUploadException | IOException | IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid, unavailable or unapproved import input");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Import interrupted");
        } catch (KeeperException.NoAuthException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "ZooKeeper identity is not authorized");
        } catch (KeeperException ex) {
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "ZooKeeper rejected the import");
        }
    }
}
