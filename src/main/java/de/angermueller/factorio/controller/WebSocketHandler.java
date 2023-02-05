package de.angermueller.factorio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.angermueller.factorio.Application;
import de.angermueller.factorio.domain.FactorioMetric;
import de.angermueller.factorio.event.MetricEvent;
import de.angermueller.factorio.repository.FactorioTimeSeriesRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler implements ApplicationListener<MetricEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Set<WebSocketSession>> sessionMap = new HashMap<>();
    private final ReadWriteLock sessionMapLock = new ReentrantReadWriteLock();
    private final Map<String, FactorioTimeSeriesRepository> timeSeriesRepositoryMap;

    public WebSocketHandler(Collection<FactorioTimeSeriesRepository> timeSeriesRepositories) {
        Map<String, FactorioTimeSeriesRepository> timeSeriesRepositoryMap = new HashMap<>();
        for (FactorioTimeSeriesRepository repository : timeSeriesRepositories) {
            timeSeriesRepositoryMap.put(repository.getSeriesName(), repository);
        }
        this.timeSeriesRepositoryMap = Collections.unmodifiableMap(timeSeriesRepositoryMap);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.debug("Connection established: {}", session.getId());
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        sessionMapLock.writeLock().lock();
        try {
            Set<String> empty = new HashSet<>();
            for (Map.Entry<String, Set<WebSocketSession>> entry : sessionMap.entrySet()) {
                if(entry.getValue().remove(session)) {
                    if(entry.getValue().isEmpty()) {
                        empty.add(entry.getKey());
                    }
                }
            }
            for (String s : empty) {
                sessionMap.remove(s);
            }
        } finally {
            sessionMapLock.writeLock().unlock();
        }
        log.debug("Session closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SubscribeCommand subscribeCommand = MAPPER.readValue(message.getPayload(), SubscribeCommand.class);
        log.trace("New command {}", subscribeCommand);
        if(subscribeCommand.action.equals("subscribe")) {
            FactorioTimeSeriesRepository repository = timeSeriesRepositoryMap.get(subscribeCommand.stream);
            if(repository == null) {
                throw new IllegalArgumentException("Repository not found for series '" + subscribeCommand.stream + "'");
            }
            FactorioTimeSeriesRepository.Resolution resolution = new Application.BasicResolution(-1, subscribeCommand.resolution);
            if(!repository.getSupportedResolutions().contains(resolution)) {
                throw new IllegalArgumentException("Resolution '" + subscribeCommand.resolution + "' not supported for series '" + subscribeCommand.stream + "'");
            }
            sessionMapLock.writeLock().lock();
            try {
                if(subscribeCommand.maxHistory > 0) {
                    List<FactorioMetric> dbMetrics = repository.retrieve(resolution, 0L, subscribeCommand.maxHistory);
                    Collections.sort(dbMetrics);
                    ArrayList<MetricEventData> data = new ArrayList<>(dbMetrics.size());
                    dbMetrics.stream().map(this::pack).forEach(data::add);
                    try {
                        session.sendMessage(new TextMessage(MAPPER.writeValueAsString(new Metrics(subscribeCommand.stream, subscribeCommand.resolution, data))));
                    } catch (IOException e) {
                        log.warn("IOException while sending ws message", e);
                    }
                }
                final String key = subscribeCommand.stream + "/" + subscribeCommand.resolution;
                sessionMap.computeIfAbsent(key, ignored -> new HashSet<>()).add(session);
            } finally {
                sessionMapLock.writeLock().unlock();
            }
        } else if(subscribeCommand.action.equals("unsubscribe")) {
            sessionMapLock.writeLock().lock();
            try {
                sessionMap.get(subscribeCommand.stream + "/" + subscribeCommand.resolution).remove(session);
            } finally {
                sessionMapLock.writeLock().unlock();
            }
        } else {
            throw new IllegalArgumentException("Unknown action: " + subscribeCommand.action);
        }
    }

    @Override
    public void onApplicationEvent(MetricEvent event) {
        final String key = event.getSeries() + "/" + event.getResolution().identifier();
        MetricEventData data = pack(event.getMetric());
        Metrics metrics = new Metrics(event.getSeries(), event.getResolution().identifier(), List.of(data));
        sessionMapLock.readLock().lock();
        try {
            for (WebSocketSession session : sessionMap.getOrDefault(key, Set.of())) {
                try {
                    session.sendMessage(new TextMessage(MAPPER.writeValueAsString(metrics)));
                } catch (IOException e) {
                    log.warn("IOException while sending ws message", e);
                }
            }
        } finally {
            sessionMapLock.readLock().unlock();
        }
    }

    protected MetricEventData pack(FactorioMetric metric) {
        Map<String, AtomicLong> data = new HashMap<>();
        for (Map.Entry<String, Long> entry : metric.getData().entrySet()) {
            String key;
            long value;
            if(entry.getKey().startsWith("in_")) {
                key = entry.getKey().substring(3);
                value = entry.getValue();
            } else if(entry.getKey().startsWith("out_")) {
                key = entry.getKey().substring(4);
                value = -entry.getValue();
            } else {
                throw new IllegalArgumentException();
            }
            data.computeIfAbsent(key, ignored -> new AtomicLong(0)).addAndGet(value);
        }
        return new MetricEventData(
                metric.getGameTick(),
                data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())));
    }

    @Getter
    @AllArgsConstructor
    protected static class WebSocketSessionData {

        private final WebSocketSession session;

    }

    @Getter
    protected static class SubscribeCommand {

        private String action;
        private String stream;
        private String resolution;
        private long maxHistory;

        @Override
        public String toString() {
            return "SubscribeCommand{" +
                    "action='" + action + '\'' +
                    ", stream='" + stream + '\'' +
                    ", resolution='" + resolution + '\'' +
                    ", maxHistory=" + maxHistory +
                    '}';
        }
    }

    @Getter
    @AllArgsConstructor
    protected static class MetricEventData {

        private final long gameTick;
        private final Map<String, Long> values;

    }

    @Getter
    @AllArgsConstructor
    public static class Metrics {

        private final String stream;
        private final String resolution;
        private final List<MetricEventData> data;

    }

}
