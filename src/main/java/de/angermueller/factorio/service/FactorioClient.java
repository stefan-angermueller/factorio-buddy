package de.angermueller.factorio.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.angermueller.factorio.util.LuaException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class FactorioClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RCONClient client;
    private final String functionName = "collectMetrics" + UUID.randomUUID().toString().replace("-", "");
    private boolean scriptLoaded = false;

    public FactorioClient(RCONClient client) {
        assert client != null;
        this.client = client;
    }

    public void loadScript() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(1024*1024);
        try(InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("collect_metrics.lua"))) {
            in.transferTo(out);
        }
        final String script = out.toString(StandardCharsets.UTF_8).replace("collect_metrics", functionName);
        sendCommand("/sc " + script);
        scriptLoaded = true;
    }

    protected String sendCommand(String command) throws IOException {
        final String response = client.sendCommand(command);
        int idx = response.indexOf("Error:");
        if(idx != -1) {
            throw new LuaException(response.substring(idx+6));
        }
        return response;
    }

    public CollectionResult collect() throws IOException {
        if(!scriptLoaded) {
            loadScript();
        }
        return MAPPER.readValue(sendCommand("/sc rcon.print(" + functionName + "())"), CollectionResult.class);
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CollectionResult {

        private boolean paused;
        private long gameTick;
        private Map<String, Long> itemsConsumed;
        private Map<String, Long> itemsProduced;

        @Override
        public String toString() {
            return "CollectionResult{" +
                    "paused=" + paused +
                    ", gameTick=" + gameTick +
                    ", itemsConsumed=" + Optional.ofNullable(itemsConsumed).map(m -> "<" + m.size() + " pairs>").orElse("null") +
                    ", itemsProduced=" + Optional.ofNullable(itemsProduced).map(m -> "<" + m.size() + " pairs>").orElse("null") +
                    '}';
        }
    }




}
