
package com.altiusfortius.datastudio.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> checkHealth() {

        Map<String, Object> response = new HashMap<>();

        response.put("application", "AF Data Studio");
        response.put("status", "UP");

        Map<String, Object> databaseInfo = jdbcTemplate.queryForMap(
            "SELECT current_database() AS database, current_user AS username"
        );

        response.put("database_status", "CONNECTED");
        response.put("database", databaseInfo.get("database"));
        response.put("username", databaseInfo.get("username"));

        response.put("message",
            "Backend and PostgreSQL are connected successfully.");

        return response;
    }
}
