package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.module.system.dal.dataobject.SystemUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.SystemUserMapper;
import cn.iocoder.yudao.server.framework.security.LemanAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 本地联调管理员初始化器。密码从环境变量读取，不在代码或 SQL 中落地明文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserBootstrapRunner implements ApplicationRunner {

    private final LemanAuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final SystemUserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) {
        LemanAuthProperties.Bootstrap bootstrap = authProperties.getBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(bootstrap.getPassword())) {
            log.warn("LEMAN_ADMIN_PASSWORD 未配置，跳过本地联调管理员初始化");
            return;
        }
        SystemUserDO user = userMapper.selectByTenantIdAndUsername(bootstrap.getTenantId(), bootstrap.getUsername());
        if (user == null) {
            createAdmin(bootstrap);
            return;
        }
        updateAdminPassword(user, bootstrap.getPassword());
    }

    private void createAdmin(LemanAuthProperties.Bootstrap bootstrap) {
        SystemUserDO user = SystemUserDO.builder()
                .tenantId(bootstrap.getTenantId())
                .username(bootstrap.getUsername())
                .password(passwordEncoder.encode(bootstrap.getPassword()))
                .nickname("管理员")
                .deptId(bootstrap.getDeptId())
                .sex(0)
                .status(0)
                .creator("system")
                .updater("system")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .deleted(false)
                .build();
        userMapper.insert(user);
        log.info("本地联调管理员已初始化, username={}, tenantId={}", bootstrap.getUsername(), bootstrap.getTenantId());
    }

    private void updateAdminPassword(SystemUserDO user, String rawPassword) {
        SystemUserDO updateObj = new SystemUserDO();
        updateObj.setId(user.getId());
        updateObj.setPassword(passwordEncoder.encode(rawPassword));
        updateObj.setUpdater("system");
        updateObj.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(updateObj);
        log.info("本地联调管理员密码 Hash 已刷新, username={}, tenantId={}", user.getUsername(), user.getTenantId());
    }

}
