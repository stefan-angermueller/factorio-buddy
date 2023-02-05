package de.angermueller.factorio.repository;

import de.angermueller.factorio.domain.FactorioMetric;

import java.util.List;
import java.util.Set;

public interface FactorioTimeSeriesRepository {

    String getSeriesName();

    Set<? extends Resolution> getSupportedResolutions();

    void store(FactorioMetric metric);

    List<FactorioMetric> retrieve(Resolution resolution, Long afterGameTick, long maxResults);

    interface Resolution extends Comparable<Resolution> {

        long ticksPerUnit();

        String identifier();

        @Override
        default int compareTo(Resolution o) {
            return Long.compare(ticksPerUnit(), o.ticksPerUnit());
        }

    }

}
