package com.mcxyd.purearena;

import com.mcxyd.purearena.storage.SqliteDailyKillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDailyKillRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void 击杀数按日期和玩家累加并持久化() throws Exception {
        Path database = temporaryDirectory.resolve("stats.db");
        UUID playerId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 9, 12);

        try (SqliteDailyKillRepository repository = new SqliteDailyKillRepository(database)) {
            repository.initialize();
            repository.increment(today, playerId, "Alice");
            repository.increment(today, playerId, "AliceNew");
            var loaded = repository.load(today);
            assertEquals(2, loaded.get(playerId).amount());
            assertEquals("AliceNew", loaded.get(playerId).name());
        }

        try (SqliteDailyKillRepository reopened = new SqliteDailyKillRepository(database)) {
            reopened.initialize();
            assertEquals(2, reopened.load(today).get(playerId).amount());
        }
    }

    @Test
    void 不同日期的数据相互隔离() throws Exception {
        Path database = temporaryDirectory.resolve("stats.db");
        UUID playerId = UUID.randomUUID();
        try (SqliteDailyKillRepository repository = new SqliteDailyKillRepository(database)) {
            repository.initialize();
            repository.increment(LocalDate.of(2026, 9, 11), playerId, "Alice");
            assertTrue(repository.load(LocalDate.of(2026, 9, 12)).isEmpty());
        }
    }
}
