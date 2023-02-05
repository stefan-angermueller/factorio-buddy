package de.angermueller.factorio.event;

import de.angermueller.factorio.domain.FactorioMetric;
import de.angermueller.factorio.repository.FactorioTimeSeriesRepository;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MetricEvent extends ApplicationEvent {

    private final String series;
    private final FactorioTimeSeriesRepository.Resolution resolution;
    private final FactorioMetric metric;

    public MetricEvent(Object source, String series, FactorioTimeSeriesRepository.Resolution resolution, FactorioMetric metric) {
        super(source);
        this.series = series;
        this.resolution = resolution;
        this.metric = metric;
    }

}
