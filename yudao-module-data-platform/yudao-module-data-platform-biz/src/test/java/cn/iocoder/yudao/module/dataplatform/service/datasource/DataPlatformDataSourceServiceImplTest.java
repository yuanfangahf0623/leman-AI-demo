package cn.iocoder.yudao.module.dataplatform.service.datasource;

import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformDataSourceDO;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformDataSourceMapper;
import cn.iocoder.yudao.module.dataplatform.framework.security.DataPlatformSecretCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlatformDataSourceServiceImplTest {

    @Mock
    private DataPlatformDataSourceMapper mapper;
    @Mock
    private DataPlatformSecretCipher cipher;
    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private DataPlatformDataSourceServiceImpl service;

    @Test
    void shouldEncryptPasswordBeforeInsert() {
        DataSourceSaveReqVO reqVO = request();
        when(cipher.encrypt("plain-secret")).thenReturn("cipher-value");
        when(mapper.insert(any(DataPlatformDataSourceDO.class))).thenAnswer(invocation -> {
            invocation.<DataPlatformDataSourceDO>getArgument(0).setId(10L);
            return 1;
        });

        assertEquals(10L, service.create(reqVO));
        ArgumentCaptor<DataPlatformDataSourceDO> captor = ArgumentCaptor.forClass(DataPlatformDataSourceDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("cipher-value", captor.getValue().getPasswordCipher());
    }

    @Test
    void shouldBuildRestrictedJdbcUrlAndHidePassword() {
        DataPlatformDataSourceDO dataSource = new DataPlatformDataSourceDO();
        dataSource.setId(1L);
        dataSource.setType("DORIS");
        dataSource.setHost("127.0.0.1");
        dataSource.setPort(9030);
        dataSource.setDatabaseName("ods");
        dataSource.setUsername("etl");
        dataSource.setPasswordCipher("cipher-value");
        when(mapper.selectById(1L)).thenReturn(dataSource);

        String url = service.buildJdbcUrl(dataSource);
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:9030/ods?"));
        assertFalse(url.contains("cipher-value"));
        assertTrue(service.get(1L).getPasswordConfigured());
        assertEquals("DATABASE", service.get(1L).getCategory());
    }

    @Test
    void shouldCreateTwoHaoHrApiSourceWithEncryptedSecret() {
        DataSourceSaveReqVO reqVO = request();
        reqVO.setName("2号人事部");
        reqVO.setCode("twohao_hr_api");
        reqVO.setType("TWO_HAO_HR");
        reqVO.setHost("https://openapi.2haohr.com");
        reqVO.setPort(443);
        reqVO.setDatabaseName("corp-id");
        reqVO.setUsername("app-id");
        reqVO.setPassword("app-secret");
        reqVO.setJdbcParams("{\"syncObjects\":[\"departments\",\"employees\"]}");
        when(cipher.encrypt("app-secret")).thenReturn("cipher-secret");
        when(mapper.insert(any(DataPlatformDataSourceDO.class))).thenAnswer(invocation -> {
            invocation.<DataPlatformDataSourceDO>getArgument(0).setId(20L);
            return 1;
        });

        assertEquals(20L, service.create(reqVO));
        ArgumentCaptor<DataPlatformDataSourceDO> captor = ArgumentCaptor.forClass(DataPlatformDataSourceDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("TWO_HAO_HR", captor.getValue().getType());
        assertEquals("cipher-secret", captor.getValue().getPasswordCipher());
    }

    @Test
    void shouldRejectJdbcUrlForTwoHaoHrApiSource() {
        DataPlatformDataSourceDO dataSource = new DataPlatformDataSourceDO();
        dataSource.setType("TWO_HAO_HR");
        dataSource.setHost("https://openapi.2haohr.com");
        dataSource.setPort(443);
        dataSource.setDatabaseName("corp-id");
        dataSource.setUsername("app-id");

        assertThrows(Exception.class, () -> service.buildJdbcUrl(dataSource));
    }

    private DataSourceSaveReqVO request() {
        DataSourceSaveReqVO reqVO = new DataSourceSaveReqVO();
        reqVO.setName("MES");
        reqVO.setCode("mes_db");
        reqVO.setType("MYSQL");
        reqVO.setHost("192.168.19.10");
        reqVO.setPort(3306);
        reqVO.setDatabaseName("mes");
        reqVO.setUsername("reader");
        reqVO.setPassword("plain-secret");
        reqVO.setStatus(0);
        return reqVO;
    }
}
