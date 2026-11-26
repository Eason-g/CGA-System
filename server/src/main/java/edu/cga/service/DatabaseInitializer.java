package edu.cga.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.sql.DataSource;

import edu.cga.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化器。
 * <ul>
 *   <li>app.db.init=if-empty：users 表不存在时执行 schema.sql + data.sql；</li>
 *   <li>always：每次启动都重建；never：不做任何处理；</li>
 *   <li>之后无论哪种模式，都把 password_hash 为空的用户设为演示密码的 BCrypt 哈希。</li>
 * </ul>
 * {@link #rebuild()} 同时供 POST /api/demo/reset 与集成测试使用。
 * <p>
 * 为什么用 {@link SmartInitializingSingleton} 而不是 ApplicationRunner：
 * ApplicationRunner 在应用上下文刷新完成、内嵌 Tomcat 已经开始接收请求之后才执行，
 * 第一次启动时如果请求先到，登录会因为种子用户的密码哈希尚未设置而偶发 401。
 * afterSingletonsInstantiated() 在所有单例 Bean 创建完毕后、上下文刷新的 finishRefresh 阶段之前执行；
 * 内嵌 Web 服务器要到 finishRefresh 阶段（WebServerStartStopLifecycle）才开始监听端口，
 * 因此初始化一定先于第一次请求完成。初始化失败则应用直接启动失败，不会带着半截数据对外服务。
 */
@Component
public class DatabaseInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DatabaseInitializer(DataSource dataSource, PasswordEncoder passwordEncoder, AppProperties props) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void afterSingletonsInstantiated() {
        switch (props.getDb().getInit()) {
            case ALWAYS -> {
                log.info("app.db.init=always，重建数据库表与种子数据");
                rebuild();
            }
            case IF_EMPTY -> {
                if (!usersTableExists()) {
                    log.info("未发现 users 表，执行建表与种子数据导入");
                    rebuild();
                } else {
                    initDemoPasswords();
                }
            }
            case NEVER -> {
                if (usersTableExists()) {
                    initDemoPasswords();
                }
            }
        }
    }

    /** 删除并重建全部表、导入种子数据、初始化演示密码 */
    public synchronized void rebuild() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource("db/schema.sql"));
        populator.addScript(new ClassPathResource("db/data.sql"));
        populator.execute(dataSource);
        initDemoPasswords();
    }

    /** 把 password_hash 为空的用户设为 app.demo-password 的 BCrypt 哈希（每个用户单独加盐） */
    public int initDemoPasswords() {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM users WHERE password_hash IS NULL OR password_hash = ''", String.class);
        for (String id : ids) {
            jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?",
                    passwordEncoder.encode(props.getDemoPassword()), id);
        }
        if (!ids.isEmpty()) {
            log.info("已为 {} 个用户设置演示密码", ids.size());
        }
        return ids.size();
    }

    private boolean usersTableExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'users'",
                Integer.class);
        return count != null && count > 0;
    }
}
