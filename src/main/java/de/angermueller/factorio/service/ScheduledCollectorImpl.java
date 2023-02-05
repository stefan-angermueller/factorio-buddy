package de.angermueller.factorio.service;

import de.angermueller.factorio.domain.FactorioMetric;
import de.angermueller.factorio.repository.FactorioTimeSeriesRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ScheduledCollectorImpl implements ScheduledCollector {

    private final FactorioClient client;
    private final FactorioTimeSeriesRepository timeSeriesRepository;

    public ScheduledCollectorImpl(FactorioClient client, FactorioTimeSeriesRepository timeSeriesRepository) {
        this.client = client;
        this.timeSeriesRepository = timeSeriesRepository;
    }

    @Override
    @Scheduled(fixedRate = 1000L)
    public void runCollection() {
        try {
            log.trace("Attempt to collect");
            final FactorioClient.CollectionResult result = client.collect();
            log.debug("Collected {}", result);
            Map<String, Long> data = new HashMap<>();
            for (Map.Entry<String, Long> entry : result.getItemsConsumed().entrySet()) {
                data.put("in_" + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Long> entry : result.getItemsProduced().entrySet()) {
                data.put("out_" + entry.getKey(), entry.getValue());
            }
            timeSeriesRepository.store(new FactorioMetric(result.getGameTick(), data));
        } catch (IOException e) {
            log.debug("Unable to collect because of {}", e.toString());
        }
    }

}
