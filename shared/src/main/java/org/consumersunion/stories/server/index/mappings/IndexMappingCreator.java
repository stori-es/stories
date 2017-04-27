package org.consumersunion.stories.server.index.mappings;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.io.IOUtils;
import org.apache.http.nio.entity.NStringEntity;
import org.consumersunion.stories.server.annotations.Indexer;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.elasticsearch.rest.RestRequest.Method.PUT;

@Component
@Scope("prototype")
public class IndexMappingCreator {
    @JsonIgnore
    private final Provider<RestClient> restClientProvider;
    @JsonIgnore
    private final ObjectMapper indexerObjectMapper;
    @JsonIgnore
    private final String indexName;

    private Map<String, Object> mappings = new LinkedHashMap<String, Object>();

    @Inject
    IndexMappingCreator(
            Provider<RestClient> restClientProvider,
            @Indexer ObjectMapper indexerObjectMapper,
            @Indexer String indexName) {
        this.restClientProvider = restClientProvider;
        this.indexerObjectMapper = indexerObjectMapper;
        this.indexName = indexName;

        mappings.put("collections", new CollectionsMapping());
        mappings.put("stories", new StoriesMapping());
//        mappings.put("profiles", new ProfilesMapping());
    }

    public void create() throws IOException {
        RestClient restClient = restClientProvider.get();

        String content = indexerObjectMapper.writeValueAsString(this);

        try {
            Response response = restClient.performRequest(PUT.name(), "/" + indexName,
                    Collections.<String, String>emptyMap(), new NStringEntity(content));
            IOUtils.copy(response.getEntity().getContent(), System.out);
        } catch (ResponseException e) {
            String error = IOUtils.toString(e.getResponse().getEntity().getContent());
            System.err.println(error);
            if (error == null || !error.contains("already_exists")) {
                throw e;
            }

            update();
        }
    }

    private void update() throws IOException {
        RestClient restClient = restClientProvider.get();
        for (Map.Entry<String, Object> entry : mappings.entrySet()) {
            String type = entry.getKey();
            String content = indexerObjectMapper.writeValueAsString(entry.getValue());

            Response response = restClient.performRequest(PUT.name(), String.format("/%s/_mapping/%s", indexName, type),
                    Collections.<String, String>emptyMap(), new NStringEntity(content));
            IOUtils.copy(response.getEntity().getContent(), System.out);
        }
    }

    public Map<String, Object> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, Object> mappings) {
        this.mappings = mappings;
    }
}