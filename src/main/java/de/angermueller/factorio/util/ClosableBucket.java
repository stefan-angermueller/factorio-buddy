package de.angermueller.factorio.util;

import jakarta.annotation.PreDestroy;
import lombok.Getter;

@Getter
public class ClosableBucket<V extends AutoCloseable> {

    private final V value;

    public ClosableBucket(V value) {
        this.value = value;
    }

    @PreDestroy
    public void close() throws Exception {
        value.close();
    }

}
