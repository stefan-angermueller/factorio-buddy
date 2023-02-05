package de.angermueller.factorio;

import de.angermueller.factorio.domain.FactorioMetric;
import de.angermueller.factorio.repository.FactorioTimeSeriesRepository;
import de.angermueller.factorio.repository.H2FactorioTimeSeriesRepositoryImpl;
import de.angermueller.factorio.service.FactorioClient;
import de.angermueller.factorio.service.OnDemandRCONClient;
import de.angermueller.factorio.service.RCONClient;
import de.angermueller.factorio.util.ClosableBucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

@EnableScheduling
@SpringBootApplication
public class Application {

    public static BasicResolution RESOLUTION_PER_SECOND = new BasicResolution(60, "seconds");
    public static BasicResolution RESOLUTION_PER_TEN_SECONDS = new BasicResolution(10*60, "ten_seconds");
    public static BasicResolution RESOLUTION_PER_MINUTE = new BasicResolution(60*60, "minutes");
    public static BasicResolution RESOLUTION_PER_TEN_MINUTES = new BasicResolution(40*60*60, "ten_minutes");

    @Value("${rcon.host}")
    private String rconHost;

    @Value("${rcon.port:27015}")
    private int rconPort;

    @Value("${rcon.password:}")
    private String rconPassword;

    private static final Set<BasicResolution> RESOLUTIONS = Set.of(
            RESOLUTION_PER_SECOND,
            RESOLUTION_PER_TEN_SECONDS,
            RESOLUTION_PER_MINUTE,
            RESOLUTION_PER_TEN_MINUTES
    );

    public static void main(String[] args) {
        new SpringApplicationBuilder(Application.class).run(args);
    }

    @Bean
    public FactorioClient factorioClient(RCONClient rconClient) {
        return new FactorioClient(rconClient);
    }

    @Bean
    public RCONClient rconClient() {
        return new OnDemandRCONClient(rconHost, rconPort, Optional.of(rconPassword).filter(s -> !s.isEmpty()).orElse(null));
    }

    @Bean
    public ClosableBucket<Connection> sqlConnection() throws SQLException {
        return new ClosableBucket<>(DriverManager.getConnection("jdbc:h2:./data/time_series"));
    }

    @Bean
    public H2FactorioTimeSeriesRepositoryImpl resourcesTimeSeriesRepository(ApplicationEventPublisher eventPublisher, ClosableBucket<Connection> connectionBucket) throws SQLException {
        return new H2FactorioTimeSeriesRepositoryImpl(connectionBucket.getValue(), "resources", eventPublisher) {
            @Override
            public Set<? extends Resolution> getSupportedResolutions() {
                return RESOLUTIONS;
            }
        };
    }

    public record BasicResolution(long ticksPerUnit, String identifier) implements FactorioTimeSeriesRepository.Resolution {

        public BasicResolution {
            assert identifier != null;
            assert ticksPerUnit > 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BasicResolution that = (BasicResolution) o;
            return identifier.equals(that.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier);
        }
    }

}