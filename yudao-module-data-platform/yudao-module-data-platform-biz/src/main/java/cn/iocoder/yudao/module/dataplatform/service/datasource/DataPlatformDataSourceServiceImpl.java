package cn.iocoder.yudao.module.dataplatform.service.datasource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourceRespVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformDataSourceDO;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformDataSourceMapper;
import cn.iocoder.yudao.module.dataplatform.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.dataplatform.framework.security.DataPlatformSecretCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.DATA_SOURCE_CONNECT_FAILED;
import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.DATA_SOURCE_TYPE_UNSUPPORTED;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataPlatformDataSourceServiceImpl implements DataPlatformDataSourceService {

    private static final String TWO_HAO_HR_TOKEN_PATH = "/api/home/get_token/";
    private static final int TWO_HAO_HR_TIMEOUT_SECONDS = 30;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final DataPlatformDataSourceMapper mapper;
    private final DataPlatformSecretCipher cipher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DataSourceSaveReqVO reqVO) {
        validateType(reqVO.getType());
        validateCodeUnique(reqVO.getCode(), null);
        DataPlatformDataSourceDO dataSource = toDO(reqVO);
        dataSource.setPasswordCipher(cipher.encrypt(reqVO.getPassword()));
        dataSource.setStatus(reqVO.getStatus() == null ? 0 : reqVO.getStatus());
        mapper.insert(dataSource);
        return dataSource.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DataSourceSaveReqVO reqVO) {
        DataPlatformDataSourceDO old = requireDataSource(reqVO.getId());
        validateType(reqVO.getType());
        validateCodeUnique(reqVO.getCode(), reqVO.getId());
        DataPlatformDataSourceDO update = toDO(reqVO);
        update.setPasswordCipher(StringUtils.hasText(reqVO.getPassword())
                ? cipher.encrypt(reqVO.getPassword()) : old.getPasswordCipher());
        update.setStatus(reqVO.getStatus() == null ? old.getStatus() : reqVO.getStatus());
        mapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDataSource(id);
        mapper.deleteById(id);
    }

    @Override
    public DataSourceRespVO get(Long id) {
        return toResp(requireDataSource(id));
    }

    @Override
    public PageResult<DataSourceRespVO> page(DataSourcePageReqVO reqVO) {
        PageResult<DataPlatformDataSourceDO> page = mapper.selectPage(reqVO);
        List<DataSourceRespVO> list = page.getList().stream().map(this::toResp).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public void testConnection(Long id) {
        testConnection(requireDataSource(id));
    }

    @Override
    public void testConnection(DataSourceSaveReqVO reqVO) {
        validateType(reqVO.getType());
        DataPlatformDataSourceDO dataSource = toDO(reqVO);
        String password = reqVO.getPassword();
        if (!StringUtils.hasText(password) && reqVO.getId() != null) {
            password = decryptPassword(requireDataSource(reqVO.getId()));
        }
        testConnection(dataSource, password == null ? "" : password);
    }

    @Override
    public DataPlatformDataSourceDO requireDataSource(Long id) {
        DataPlatformDataSourceDO dataSource = id == null ? null : mapper.selectById(id);
        if (dataSource == null) {
            throw new ServiceException(DATA_SOURCE_NOT_EXISTS, "数据源不存在");
        }
        return dataSource;
    }

    @Override
    public String decryptPassword(DataPlatformDataSourceDO dataSource) {
        return cipher.decrypt(dataSource.getPasswordCipher());
    }

    @Override
    public String buildJdbcUrl(DataPlatformDataSourceDO dataSource) {
        DataSourceTypeEnum type = validateType(dataSource.getType());
        if (!type.isJdbc()) {
            throw new ServiceException(DATA_SOURCE_TYPE_UNSUPPORTED, "该数据源类型不支持 JDBC URL");
        }
        String base = switch (type) {
            case MYSQL, DORIS -> "jdbc:mysql://" + dataSource.getHost() + ":" + dataSource.getPort()
                    + "/" + dataSource.getDatabaseName()
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&connectTimeout=5000&socketTimeout=15000";
            case POSTGRESQL -> "jdbc:postgresql://" + dataSource.getHost() + ":" + dataSource.getPort()
                    + "/" + dataSource.getDatabaseName() + "?connectTimeout=5&socketTimeout=15";
            case SQLSERVER -> "jdbc:sqlserver://" + dataSource.getHost() + ":" + dataSource.getPort()
                    + ";databaseName=" + dataSource.getDatabaseName() + ";loginTimeout=5;socketTimeout=15000;encrypt=false";
            case TWO_HAO_HR -> throw new ServiceException(DATA_SOURCE_TYPE_UNSUPPORTED, "2号人事部不支持 JDBC URL");
        };
        if (!StringUtils.hasText(dataSource.getJdbcParams())) {
            return base;
        }
        return base + ((type == DataSourceTypeEnum.SQLSERVER) ? ";" : "&") + dataSource.getJdbcParams().trim();
    }

    private void testConnection(DataPlatformDataSourceDO dataSource) {
        testConnection(dataSource, decryptPassword(dataSource));
    }

    private void testConnection(DataPlatformDataSourceDO dataSource, String password) {
        DataSourceTypeEnum type = validateType(dataSource.getType());
        if (type == DataSourceTypeEnum.TWO_HAO_HR) {
            testTwoHaoHrConnection(dataSource, password);
            return;
        }
        try {
            Class.forName(type.getDriverClassName());
            Properties props = new Properties();
            props.setProperty("user", dataSource.getUsername());
            props.setProperty("password", password);
            try (Connection ignored = DriverManager.getConnection(buildJdbcUrl(dataSource), props)) {
                log.info("数据源连接测试成功, dataSourceId={}, code={}, type={}, host={}, port={}",
                        dataSource.getId(), dataSource.getCode(), dataSource.getType(), dataSource.getHost(), dataSource.getPort());
            }
        } catch (Exception ex) {
            log.warn("数据源连接测试失败, dataSourceId={}, code={}, type={}, host={}, port={}, errorType={}",
                    dataSource.getId(), dataSource.getCode(), dataSource.getType(), dataSource.getHost(),
                    dataSource.getPort(), ex.getClass().getSimpleName());
            throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "数据源连接失败，请检查地址、账号和网络");
        }
    }

    private void testTwoHaoHrConnection(DataPlatformDataSourceDO dataSource, String appSecret) {
        String corpId = dataSource.getDatabaseName();
        String appId = dataSource.getUsername();
        if (!StringUtils.hasText(dataSource.getHost()) || !StringUtils.hasText(corpId)
                || !StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new ServiceException(DATA_SOURCE_CONNECT_FAILED,
                    "2号人事部 API 需要配置接口地址、企业 ID、应用 ID 和应用密钥");
        }
        URI uri = buildTwoHaoHrUri(dataSource);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("corp_id", corpId.trim());
        payload.put("app_id", appId.trim());
        payload.put("app_secret", appSecret.trim());
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(TWO_HAO_HR_TIMEOUT_SECONDS))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("2hao HR datasource test failed, dataSourceId={}, code={}, status={}",
                        dataSource.getId(), dataSource.getCode(), response.statusCode());
                throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "2号人事部 API 连接失败");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode errcodeNode = root.path("errcode");
            if (errcodeNode.isNumber() && errcodeNode.asInt() != 0) {
                log.warn("2hao HR datasource test returned error, dataSourceId={}, code={}, errcode={}",
                        dataSource.getId(), dataSource.getCode(), errcodeNode.asInt());
                throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "2号人事部 API 鉴权失败");
            }
            if (!StringUtils.hasText(root.path("data").path("access_token").asText(null))) {
                throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "2号人事部 API 响应缺少 access_token");
            }
            log.info("2hao HR datasource test success, dataSourceId={}, code={}, endpoint={}",
                    dataSource.getId(), dataSource.getCode(), sanitizeTwoHaoHrEndpoint(uri));
        } catch (JsonProcessingException ex) {
            throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "2号人事部 API 请求或响应 JSON 非法");
        } catch (IOException ex) {
            log.warn("2hao HR datasource test failed, dataSourceId={}, code={}, errorType={}",
                    dataSource.getId(), dataSource.getCode(), ex.getClass().getSimpleName());
            throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "2号人事部 API 连接失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "2号人事部 API 连接被中断");
        }
    }

    private URI buildTwoHaoHrUri(DataPlatformDataSourceDO dataSource) {
        try {
            String baseUrl = dataSource.getHost().trim();
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URI baseUri = URI.create(baseUrl);
            if (baseUri.getScheme() == null || baseUri.getHost() == null) {
                throw new IllegalArgumentException("missing scheme or host");
            }
            String path = (baseUri.getPath() == null ? "" : baseUri.getPath()) + TWO_HAO_HR_TOKEN_PATH;
            int port = baseUri.getPort();
            if (port < 0 && dataSource.getPort() != null && dataSource.getPort() > 0
                    && dataSource.getPort() != 80 && dataSource.getPort() != 443) {
                port = dataSource.getPort();
            }
            return new URI(baseUri.getScheme(), null, baseUri.getHost(), port, path, null, null);
        } catch (Exception ex) {
            throw new ServiceException(DATA_SOURCE_CONNECT_FAILED, "2号人事部 API 地址非法");
        }
    }

    private String sanitizeTwoHaoHrEndpoint(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + uri.getPath();
    }

    private DataSourceTypeEnum validateType(String code) {
        DataSourceTypeEnum type = code == null ? null : DataSourceTypeEnum.of(code);
        if (type == null) {
            throw new ServiceException(DATA_SOURCE_TYPE_UNSUPPORTED, "暂不支持该数据源类型");
        }
        return type;
    }

    private void validateCodeUnique(String code, Long id) {
        DataPlatformDataSourceDO existing = mapper.selectByCode(code);
        if (existing != null && !existing.getId().equals(id)) {
            throw new ServiceException(DATA_SOURCE_TYPE_UNSUPPORTED, "数据源编码已存在");
        }
    }

    private DataPlatformDataSourceDO toDO(DataSourceSaveReqVO reqVO) {
        DataPlatformDataSourceDO dataSource = new DataPlatformDataSourceDO();
        dataSource.setId(reqVO.getId());
        dataSource.setName(reqVO.getName().trim());
        dataSource.setCode(reqVO.getCode().trim());
        dataSource.setType(reqVO.getType().trim().toUpperCase());
        dataSource.setHost(reqVO.getHost().trim());
        dataSource.setPort(reqVO.getPort());
        dataSource.setDatabaseName(reqVO.getDatabaseName().trim());
        dataSource.setUsername(reqVO.getUsername().trim());
        dataSource.setJdbcParams(reqVO.getJdbcParams());
        dataSource.setStatus(reqVO.getStatus());
        dataSource.setRemark(reqVO.getRemark());
        return dataSource;
    }

    private DataSourceRespVO toResp(DataPlatformDataSourceDO source) {
        DataSourceRespVO resp = new DataSourceRespVO();
        resp.setId(source.getId());
        resp.setName(source.getName());
        resp.setCode(source.getCode());
        DataSourceTypeEnum type = validateType(source.getType());
        resp.setCategory(type.getCategory());
        resp.setType(source.getType());
        resp.setHost(source.getHost());
        resp.setPort(source.getPort());
        resp.setDatabaseName(source.getDatabaseName());
        resp.setUsername(source.getUsername());
        resp.setPasswordConfigured(StringUtils.hasText(source.getPasswordCipher()));
        resp.setJdbcParams(source.getJdbcParams());
        resp.setStatus(source.getStatus());
        resp.setRemark(source.getRemark());
        resp.setCreateTime(source.getCreateTime());
        resp.setUpdateTime(source.getUpdateTime());
        return resp;
    }
}
