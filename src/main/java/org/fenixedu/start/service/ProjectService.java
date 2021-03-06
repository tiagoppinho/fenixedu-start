/**
 * Copyright 2014 Instituto Superior Técnico
 * This project is part of the FenixEdu Project: https://fenixedu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fenixedu.start.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.loader.StringLoader;
import org.fenixedu.start.ProjectRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final APISyncService apiSyncService;
    private final PebbleEngine engine;
    private final Map<String, List<Resource>> resourcesByType;

    @Autowired
    public ProjectService(APISyncService apiSyncService, ApplicationContext ctx) throws IOException {
        ResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();
        this.resourcesByType =
                ImmutableMap.of("module", Arrays.asList(patternResolver.getResources("classpath:/templates/module/**/*")),
                        "webapp", Arrays.asList(patternResolver.getResources("classpath:/templates/webapp/**/*")));
        this.apiSyncService = apiSyncService;
        this.engine = new PebbleEngine(new StringLoader());
        this.engine.addExtension(new FenixEduStartExtension());
    }

    public Map<String, byte[]> buildModule(ProjectRequest request) throws PebbleException, IOException {
        return build(request, "module");
    }

    public Map<String, byte[]> buildWebapp(ProjectRequest request) throws PebbleException, IOException {
        return build(request, "webapp");
    }

    private Map<String, byte[]> build(ProjectRequest request, String type) throws PebbleException, IOException {
        List<Resource> resources = resourcesByType.get(type);
        Map<String, byte[]> project = new HashMap<>();
        for (Resource resource : resources) {
            if (resource.isReadable()) {
                String fileData = process(new String(ByteStreams.toByteArray(resource.getInputStream())), request);
                if (fileData != null) {
                    String filePath = getFilePath(resource, type);
                    project.put(process(filePath, request), fileData.getBytes());
                }
            }
        }
        return project;
    }

    private String getFilePath(Resource resource, String type) throws IOException {
        String path = URLDecoder.decode(resource.getURL().getPath(), "UTF-8");
        return path.substring(path.indexOf("/templates/" + type + "/") + ("/templates/" + type + "/").length());
    }

    private String process(String resource, ProjectRequest request) throws IOException, PebbleException {
        StringWriter writer = new StringWriter();
        engine.getTemplate(resource).evaluate(writer, ImmutableMap.of("request", request, "api", apiSyncService));
        String result = writer.toString();
        return result.trim().isEmpty() ? null : result;
    }
}
