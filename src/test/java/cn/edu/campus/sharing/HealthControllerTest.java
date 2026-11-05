package cn.edu.campus.sharing;

import cn.edu.campus.sharing.common.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test
    void unavailableDatabaseReturns503WithoutConnectionDetails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new CannotGetJdbcConnectionException("private connection details"));
        var response = new HealthController(jdbc).health();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().data().database()).isEqualTo("DOWN");
        assertThat(response.getBody().message()).doesNotContain("private");
    }
}
