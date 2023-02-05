package de.angermueller.factorio;

import de.angermueller.factorio.controller.WebSocketHandler;
import de.angermueller.factorio.repository.FactorioTimeSeriesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Collection;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private Collection<FactorioTimeSeriesRepository> timeSeriesRepositories;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry.addHandler(webSocketHandler(), "/metrics");
    }

    @Bean
    public WebSocketHandler webSocketHandler() {
        return new WebSocketHandler(timeSeriesRepositories);
    }

}
