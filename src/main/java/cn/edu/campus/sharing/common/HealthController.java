package cn.edu.campus.sharing.common;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<HealthStatus>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(ApiResponse.ok(new HealthStatus("UP", "UP")));
        } catch (DataAccessException ex) {
            return ResponseEntity.status(503).body(
                    new ApiResponse<>(503, "数据库连接不可用", new HealthStatus("DOWN", "DOWN")));
        }
    }

    public record HealthStatus(String status, String database) {}
}
