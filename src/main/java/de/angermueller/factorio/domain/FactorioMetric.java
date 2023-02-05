package de.angermueller.factorio.domain;

import lombok.Getter;

import java.util.Map;
import java.util.Set;

@Getter
public class FactorioMetric implements Comparable<FactorioMetric> {

    private final long gameTick;
    private final Map<String, Long> data;

    public FactorioMetric(long gameTick, Map<String, Long> data) {
        assert gameTick >= 0;
        assert data != null;
        this.gameTick = gameTick;
        this.data = data;
    }

    @Override
    public int compareTo(FactorioMetric o) {
        return Long.compare(gameTick, o.gameTick);
    }

    public Set<String> getDimensions() {
        return data.keySet();
    }

    @Override
    public String toString() {
        return "FactorioMetric{" +
                "gameTick=" + gameTick +
                ", data=<" + data.size() + " pairs>" +
                '}';
    }
}
