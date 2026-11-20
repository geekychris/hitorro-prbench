package com.hitorro.prbench;

import tools.jackson.databind.JsonNode;
import com.hitorro.util.json.JsonMappers;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP JSON is Boot 4's own Jackson 3 (tools.jackson) converter; HiTorro code and the endpoints use
 * Jackson 3 nodes too. Before 4.0.0 the app ran on Jackson 2 through spring-boot-jackson2, where a
 * Jackson 3 converter would have written a com.fasterxml JsonNode as a bean
 * ({"array":false,"nodeType":"OBJECT",...}). These tests pin the body and the converter.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prbench-json-test;DB_CLOSE_DELAY=-1",
        "app.github.token="
})
@AutoConfigureMockMvc
@Import(JsonNodeJsonTest.ProbeConfig.class)
class JsonNodeJsonTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    RequestMappingHandlerAdapter adapter;

    @Test
    void jsonNodeIsSerializedAsPlainJson() throws Exception {
        mvc.perform(get("/test/json-node").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"name\":\"prbench\",\"count\":3,\"nested\":{\"ok\":true},\"list\":[1,2]}", true));
    }

    @Test
    void jacksonConverterIsTheJsonWriter() {
        // The first converter able to write a JsonNode as JSON is the one used for responses.
        // (Spring Data's read-only projecting converter may sit ahead of it; it never writes.)
        HttpMessageConverter<?> firstJson = adapter.getMessageConverters().stream()
                .filter(c -> c.canWrite(ObjectNode.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertThat(firstJson).isInstanceOf(JacksonJsonHttpMessageConverter.class);
    }

    @Test
    void realEndpointReturnsJsonArray() throws Exception {
        mvc.perform(get("/api/issue-types/categories").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(JsonMappers.json()
                        .readTree(result.getResponse().getContentAsString()).isArray()).isTrue());
    }

    @TestConfiguration
    static class ProbeConfig {
        @RestController
        static class JsonNodeProbeController {
            @GetMapping("/test/json-node")
            JsonNode jsonNode() {
                ObjectNode n = JsonMappers.json().createObjectNode();
                n.put("name", "prbench");
                n.put("count", 3);
                n.putObject("nested").put("ok", true);
                n.putArray("list").add(1).add(2);
                return n;
            }
        }
    }
}
