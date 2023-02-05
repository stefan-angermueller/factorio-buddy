package de.angermueller.factorio.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.angermueller.factorio.domain.FactorioMetric;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class H2FactorioTimeSeriesRepositoryImpl extends SQLFactorioTimeSeriesRepositoryImpl implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Long>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    private final Connection connection;
    private final Map<Resolution, PreparedStatements> statementsMap = new HashMap<>();

    public H2FactorioTimeSeriesRepositoryImpl(Connection connection, String seriesName, ApplicationEventPublisher applicationEventPublisher) throws SQLException {
        super(seriesName, applicationEventPublisher);
        assert connection != null;
        this.connection = connection;
    }

    @Override
    protected void ensureTable(Resolution resolution) throws SQLException {
        final String tableName = seriesName + "_" + resolution.identifier();
        try(Statement stm = connection.createStatement()) {
            stm.execute(("CREATE TABLE IF NOT EXISTS `{tableName}` (" +
                    "GAME_TICK BIGINT PRIMARY KEY," +
                    "DATA JSON NOT NULL" +
                    ");").replace("{tableName}", tableName));
        }
        PreparedStatement psInsert = connection.prepareStatement("INSERT INTO `{tableName}` (GAME_TICK, DATA) VALUES (?, ? FORMAT JSON);".replace("{tableName}", tableName));
        PreparedStatement psSelectLastTick = connection.prepareStatement("SELECT GAME_TICK FROM `{tableName}` ORDER BY GAME_TICK DESC LIMIT 1;".replace("{tableName}", tableName));
        PreparedStatement psSelect = connection.prepareStatement("SELECT GAME_TICK, DATA FROM `{tableName}` WHERE GAME_TICK > ? ORDER BY GAME_TICK DESC LIMIT ?;".replace("{tableName}", tableName));
        statementsMap.put(resolution, new PreparedStatements(psInsert, psSelectLastTick, psSelect));
    }

    @Override
    protected void doStoreValue(Resolution resolution, FactorioMetric metric) throws SQLException, IOException {
        PreparedStatement ps = statementsMap.get(resolution).psInsert;
        ps.setLong(1, metric.getGameTick());
        ps.setString(2, MAPPER.writeValueAsString(metric.getData()));
        ps.execute();
    }

    @Override
    protected List<FactorioMetric> retrieveValues(Resolution resolution, long afterTick, long maxResults) throws SQLException, IOException {
        PreparedStatement ps = statementsMap.get(resolution).psSelect;
        ps.setLong(1, afterTick);
        ps.setLong(2, maxResults);
        LinkedList<FactorioMetric> result = new LinkedList<>();
        try(ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new FactorioMetric(rs.getLong("GAME_TICK"), MAPPER.readValue(rs.getString("DATA"), MAP_TYPE_REFERENCE)));
            }
        }
        return result;
    }

    @Override
    protected Long retrieveLastTick(Resolution resolution) throws SQLException {
        PreparedStatement ps = statementsMap.get(resolution).psSelectLastTick;
        try(ResultSet rs = ps.executeQuery()) {
            if(rs.next()) {
                return rs.getLong("GAME_TICK");
            }
        }
        return null;
    }

    @Override
    @PreDestroy
    public void close() throws SQLException {
        for (PreparedStatements value : statementsMap.values()) {
            value.psInsert.close();
            value.psSelectLastTick.close();
            value.psSelect.close();
        }
    }

    protected record PreparedStatements(PreparedStatement psInsert, PreparedStatement psSelectLastTick, PreparedStatement psSelect) {

    }

}
