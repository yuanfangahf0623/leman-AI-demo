package cn.iocoder.yudao.module.dataplatform.service.sync;

import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformDataSourceDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformJobRunDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformSyncJobDO;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformJobRunMapper;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformSyncJobMapper;
import cn.iocoder.yudao.module.dataplatform.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.dataplatform.framework.config.DataPlatformProperties;
import cn.iocoder.yudao.module.dataplatform.service.datasource.DataPlatformDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeaTunnelJobExecutor {

    private final DataPlatformSyncJobMapper jobMapper;
    private final DataPlatformJobRunMapper runMapper;
    private final DataPlatformDataSourceService dataSourceService;
    private final DataPlatformProperties properties;
    private final SyncFieldMappingSqlBuilder mappingSqlBuilder;

    @Async("dataPlatformTaskExecutor")
    public void executeAsync(Long runId) {
        DataPlatformJobRunDO run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        DataPlatformSyncJobDO job = jobMapper.selectById(run.getJobId());
        if (job == null) {
            finish(run, "FAILED", null, "同步任务已删除");
            return;
        }
        DataPlatformDataSourceDO source = dataSourceService.requireDataSource(job.getSourceDataSourceId());
        DataPlatformDataSourceDO target = dataSourceService.requireDataSource(job.getTargetDataSourceId());
        String sourcePassword = dataSourceService.decryptPassword(source);
        String targetPassword = dataSourceService.decryptPassword(target);
        Path configPath = null;
        try {
            Path jobDir = Path.of(properties.getSeatunnel().getJobDirectory()).toAbsolutePath().normalize();
            Path logDir = Path.of(properties.getSeatunnel().getLogDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(jobDir);
            Files.createDirectories(logDir);
            configPath = jobDir.resolve("run-" + runId + ".conf");
            Path logPath = logDir.resolve("run-" + runId + ".log");
            Files.writeString(configPath, renderConfig(job, source, sourcePassword, target, targetPassword),
                    StandardCharsets.UTF_8);
            restrictFile(configPath);

            run.setStatus("RUNNING");
            run.setStartTime(LocalDateTime.now());
            run.setLogPath(logPath.toString());
            runMapper.updateById(run);
            log.info("SeaTunnel 同步开始, runId={}, jobId={}, batchId={}, sourceCode={}, target={}.{}",
                    runId, job.getId(), run.getBatchId(), source.getCode(), job.getTargetDatabase(), job.getTargetTable());

            Process process = new ProcessBuilder(properties.getSeatunnel().getCommand(), "--config", configPath.toString())
                    .redirectErrorStream(true).start();
            Thread outputReader = new Thread(() -> copyRedactedOutput(process, logPath, sourcePassword, targetPassword),
                    "seatunnel-output-" + runId);
            outputReader.setDaemon(true);
            outputReader.start();
            boolean completed = process.waitFor(properties.getSeatunnel().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                outputReader.join(TimeUnit.SECONDS.toMillis(10));
                finish(run, "TIMEOUT", null, "SeaTunnel 执行超时");
            } else if (process.exitValue() == 0) {
                outputReader.join(TimeUnit.SECONDS.toMillis(10));
                finish(run, "SUCCESS", 0, null);
            } else {
                outputReader.join(TimeUnit.SECONDS.toMillis(10));
                finish(run, "FAILED", process.exitValue(), "SeaTunnel 返回非零退出码");
            }
        } catch (Exception ex) {
            log.warn("SeaTunnel 同步失败, runId={}, jobId={}, batchId={}, errorType={}",
                    runId, run.getJobId(), run.getBatchId(), ex.getClass().getSimpleName());
            finish(run, "FAILED", null, "SeaTunnel 启动或执行失败");
        } finally {
            if (configPath != null) {
                try {
                    Files.deleteIfExists(configPath);
                } catch (Exception ex) {
                    log.warn("SeaTunnel 临时配置清理失败, runId={}, path={}", runId, configPath);
                }
            }
        }
    }

    private String renderConfig(DataPlatformSyncJobDO job, DataPlatformDataSourceDO source, String sourcePassword,
                                DataPlatformDataSourceDO target, String targetPassword) {
        DataSourceTypeEnum sourceType = DataSourceTypeEnum.of(source.getType());
        DataSourceTypeEnum targetType = DataSourceTypeEnum.of(target.getType());
        return "env {\n"
                + "  parallelism = " + job.getParallelism() + "\n"
                + "  job.mode = \"BATCH\"\n"
                + "}\n\nsource {\n  Jdbc {\n"
                + "    url = \"" + escape(dataSourceService.buildJdbcUrl(source)) + "\"\n"
                + "    driver = \"" + escape(sourceType.getDriverClassName()) + "\"\n"
                + "    user = \"" + escape(source.getUsername()) + "\"\n"
                + "    password = \"" + escape(sourcePassword) + "\"\n"
                + "    query = \"\"\"" + safeSql(mappingSqlBuilder.buildSourceSql(job.getSourceSql(), sourceType,
                        job.getMappingConfig())) + "\"\"\"\n"
                + "  }\n}\n\nsink {\n  Jdbc {\n"
                + "    url = \"" + escape(dataSourceService.buildJdbcUrl(target)) + "\"\n"
                + "    driver = \"" + escape(targetType.getDriverClassName()) + "\"\n"
                + "    user = \"" + escape(target.getUsername()) + "\"\n"
                + "    password = \"" + escape(targetPassword) + "\"\n"
                + "    query = \"\"\"" + safeSql(mappingSqlBuilder.buildSinkSql(job.getTargetDatabase(),
                        job.getTargetTable(), job.getMappingConfig(), job.getSinkSql())) + "\"\"\"\n"
                + "  }\n}\n";
    }

    private String safeSql(String value) {
        if (value.contains("\"\"\"")) {
            throw new IllegalArgumentException("SQL contains HOCON triple quote");
        }
        return value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private String redact(String line, String... secrets) {
        String result = line;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                result = result.replace(secret, "******");
            }
        }
        return result;
    }

    private void copyRedactedOutput(Process process, Path logPath, String... secrets) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(redact(line, secrets));
                writer.newLine();
            }
        } catch (Exception ex) {
            log.warn("SeaTunnel 输出日志采集失败, path={}, errorType={}", logPath, ex.getClass().getSimpleName());
        }
    }

    private void finish(DataPlatformJobRunDO run, String status, Integer exitCode, String errorMessage) {
        run.setStatus(status);
        run.setExitCode(exitCode);
        run.setErrorMessage(errorMessage);
        run.setEndTime(LocalDateTime.now());
        runMapper.updateById(run);
        log.info("SeaTunnel 同步结束, runId={}, jobId={}, batchId={}, status={}, exitCode={}",
                run.getId(), run.getJobId(), run.getBatchId(), status, exitCode);
    }

    private void restrictFile(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows development environment has no POSIX permissions.
        } catch (Exception ex) {
            log.warn("临时任务配置权限收紧失败, path={}", path);
        }
    }
}
