package de.angermueller.factorio.repository;

import de.angermueller.factorio.domain.FactorioMetric;
import de.angermueller.factorio.event.MetricEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public abstract class SQLFactorioTimeSeriesRepositoryImpl implements FactorioTimeSeriesRepository {

    private final Map<Resolution, SeriesData> seriesDataMap = new HashMap<>();
    private final Lock lock = new ReentrantLock();
    private boolean initialised = false;
    protected final String seriesName;
    private final ApplicationEventPublisher applicationEventPublisher;

    public SQLFactorioTimeSeriesRepositoryImpl(String seriesName, ApplicationEventPublisher applicationEventPublisher) {
        assert seriesName != null && !seriesName.isEmpty();
        this.seriesName = seriesName;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @PostConstruct
    public void init() throws SQLException, IOException {
        List<Resolution> resolutions = new ArrayList<>(getSupportedResolutions());
        Collections.sort(resolutions);
        if(resolutions.isEmpty()) {
            throw new Error("Must support at least one resolution");
        }
        long lastTick = 0;
        Resolution first = resolutions.get(0);
        for (Resolution resolution : resolutions) {
            ensureTable(resolution);
            lastTick = Optional.ofNullable(retrieveLastTick(resolution)).orElse(lastTick);
            List<FactorioMetric> inputBuffer = new LinkedList<>(retrieveValues(first, lastTick, Integer.MAX_VALUE));
            Collections.sort(inputBuffer);
            seriesDataMap.put(resolution, new SeriesData(resolution, inputBuffer, lastTick));
        }
        initialised = true;
    }

    @Override
    public String getSeriesName() {
        return seriesName;
    }

    protected abstract void ensureTable(Resolution resolution) throws SQLException;

    protected abstract void doStoreValue(Resolution resolution, FactorioMetric metric) throws SQLException, IOException;

    protected abstract List<FactorioMetric> retrieveValues(Resolution resolution, long afterTick, long maxResult) throws SQLException, IOException;
    protected abstract Long retrieveLastTick(Resolution resolution) throws SQLException;

    protected void addMetric(FactorioMetric metric, SeriesData seriesData) throws SQLException, IOException {
        if(!initialised) {
            throw new IllegalStateException("Not initialised");
        }
        while (metric.getGameTick() - seriesData.lastStoredTick > seriesData.resolution.ticksPerUnit()) {
            long nextTick = seriesData.lastStoredTick + seriesData.resolution.ticksPerUnit();
            if(!seriesData.inputBuffer.isEmpty()) {
                // Don't store empty values, it screws up the graph
                storeValue(seriesData.resolution, new FactorioMetric(nextTick, aggregate(nextTick, seriesData.inputBuffer)));
            }
            seriesData.lastStoredTick = nextTick;
        }
        seriesData.inputBuffer.add(metric);
    }

    protected void storeValue(Resolution resolution, FactorioMetric metric) throws SQLException, IOException {
        doStoreValue(resolution, metric);
        if(applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new MetricEvent(this, seriesName, resolution, metric));
        }
    }

    protected Map<String, Long> aggregate(long targetTick, List<FactorioMetric> metrics) {
        // This is a simple max-aggregation
        Map<String, AtomicLong> sum = new HashMap<>();
        if(!metrics.isEmpty()) {
            for (FactorioMetric a : metrics) {
                for (Map.Entry<String, Long> entry : a.getData().entrySet()) {
                    AtomicLong atomicLong = sum.computeIfAbsent(entry.getKey(), ignored -> new AtomicLong(0L));
                    atomicLong.set(Math.max(atomicLong.get(), entry.getValue()));
                }
            }
        }
        return sum.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    @Override
    public void store(FactorioMetric metric) {
        if(!initialised) {
            throw new IllegalStateException("Not initialised");
        }
        lock.lock();
        try {
            for (SeriesData value : seriesDataMap.values()) {
                addMetric(metric, value);
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<FactorioMetric> retrieve(Resolution resolution, Long afterGameTick, long maxResults) {
        if(!initialised) {
            throw new IllegalStateException("Not initialised");
        }
        lock.lock();
        try {
            return retrieveValues(resolution, Optional.ofNullable(afterGameTick).orElse(0L), maxResults);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    protected static class SeriesData {

        private final Resolution resolution;
        private final List<FactorioMetric> inputBuffer;
        private long lastStoredTick;

        public SeriesData(Resolution resolution, List<FactorioMetric> inputBuffer, long lastStoredTick) {
            this.resolution = resolution;
            this.inputBuffer = inputBuffer;
            this.lastStoredTick = lastStoredTick;
        }
    }

}
