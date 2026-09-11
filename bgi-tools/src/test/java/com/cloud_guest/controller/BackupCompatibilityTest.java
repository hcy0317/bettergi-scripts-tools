package com.cloud_guest.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.cloud_guest.mapper.*;
import com.cloud_guest.properties.load.LoadProperties;
import com.cloud_guest.service.CacheService;
import com.cloud_guest.service.impl.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BackupCompatibilityTest {
    @Test
    void backupIncludesTheNewUidTeamRowsAlongsideExistingTables() throws Exception {
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("backup-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.sqlite.JDBC", "jdbc:sqlite::memory:", null, null)));
        for (Class<?> mapper : new Class<?>[]{UidMapper.class, UidTeamMapper.class, WsProxyMapper.class, AutoPlanMapper.class, DbKVMapper.class})
            config.addMapper(mapper);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)) {
            ScriptUtils.executeSqlScript(session.getConnection(), new ClassPathResource("sql/sqlite.sql"));
            try (var statement = session.getConnection().createStatement()) {
                statement.execute("ALTER TABLE auto_plan_config ADD COLUMN cultivate INTEGER DEFAULT 0");
                statement.execute("INSERT INTO uid_team_config(id,uid,team,team_type) VALUES (10,'123456789','team-a','fight')");
            }
            var uid = new UidServiceImpl();
            var team = new UidTeamServiceImpl();
            var ws = new WsProxyServiceImpl();
            var plan = new AutoPlanServiceImpl();
            var kv = new DbKVServiceImpl();
            ReflectionTestUtils.setField(uid, "baseMapper", session.getMapper(UidMapper.class));
            ReflectionTestUtils.setField(team, "baseMapper", session.getMapper(UidTeamMapper.class));
            ReflectionTestUtils.setField(ws, "baseMapper", session.getMapper(WsProxyMapper.class));
            ReflectionTestUtils.setField(plan, "baseMapper", session.getMapper(AutoPlanMapper.class));
            ReflectionTestUtils.setField(kv, "baseMapper", session.getMapper(DbKVMapper.class));
            var backup = new DataBackupRecoveryServiceImpl();
            ReflectionTestUtils.setField(backup, "uidService", uid);
            ReflectionTestUtils.setField(backup, "uidTeamService", team);
            ReflectionTestUtils.setField(backup, "wsProxyService", ws);
            ReflectionTestUtils.setField(backup, "autoPlanService", plan);
            ReflectionTestUtils.setField(backup, "dbKVService", kv);
            // Cache is the external boundary; table services, mappers and SQLite are real.
            ReflectionTestUtils.setField(backup, "cacheService", mock(CacheService.class));
            ReflectionTestUtils.setField(backup, "loadProperties", new LoadProperties());

            var data = backup.backupV1().getJSONObject("data");
            assertThat(data.containsKey(team.getSuffix())).isTrue();
            var rows = JSONUtil.parseArray(data.getStr(team.getSuffix()));
            assertThat(rows.size()).isEqualTo(1);
            assertThat(rows.getJSONObject(0).getStr("uid")).isEqualTo("123456789");
            assertThat(rows.getJSONObject(0).getStr("team")).isEqualTo("team-a");
            assertThat(data.containsKey(uid.getSuffix())).isTrue();
        }
    }
}
