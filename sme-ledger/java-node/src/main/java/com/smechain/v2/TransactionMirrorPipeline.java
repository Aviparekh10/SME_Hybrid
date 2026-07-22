package com.smechain.v2;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


public class TransactionMirrorPipeline {

    private final JdbcTemplate jdbcTemplate;

    public TransactionMirrorPipeline(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes the raw SQL statement. Your Python code will query 
     * this exact table ('ledger_mirror') to run risk models seamlessly.
     */
    public void saveToPostgreSQL(String jsonPayload) {
        String sql = "INSERT INTO ledger_mirror (data, recorded_at) VALUES (?::jsonb, NOW())";
        try {
            jdbcTemplate.update(sql, jsonPayload);
        } catch (Exception e) {
            System.err.println("Database Mirror Sync Delayed: " + e.getMessage());
        }
    }
}