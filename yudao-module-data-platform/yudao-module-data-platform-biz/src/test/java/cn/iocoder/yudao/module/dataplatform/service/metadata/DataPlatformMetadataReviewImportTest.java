package cn.iocoder.yudao.module.dataplatform.service.metadata;

import cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo.MetadataImportRespVO;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformDataSourceMapper;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformMetadataFieldMapper;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformMetadataTableMapper;
import cn.iocoder.yudao.module.dataplatform.service.datasource.DataPlatformDataSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataPlatformMetadataReviewImportTest {

    @Mock private DataPlatformMetadataTableMapper tableMapper;
    @Mock private DataPlatformMetadataFieldMapper fieldMapper;
    @Mock private DataPlatformDataSourceMapper dataSourceMapper;
    @Mock private DataPlatformDataSourceService dataSourceService;
    @Mock private JdbcTemplate jdbcTemplate;

    private DataPlatformMetadataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataPlatformMetadataServiceImpl(tableMapper, fieldMapper, dataSourceMapper,
                dataSourceService, new BusinessNameSuggester(), jdbcTemplate);
    }

    @Test
    void shouldImportOnlyConfirmedRows() {
        String content = "字段ID\t源Schema\t源表\t源字段\t数据类型\t业务中文名\t业务口径\t数据分类\t敏感等级\t增量字段\t确认状态\t定义来源\n" +
                "101\tdbo\tcomProduct\tProdID\tvarchar\t产品编号\t产品唯一编号\t主数据\t内部\t否\tCONFIRMED\tRULE\n" +
                "102\tdbo\tcomProduct\tProdName\tvarchar\t产品名称\t\t主数据\tINTERNAL\t否\tGENERATED\tRULE\n";
        when(jdbcTemplate.batchUpdate(anyString(), anyList())).thenReturn(new int[]{1});

        MetadataImportRespVO result = service.importReviewTsv(7L, new MockMultipartFile(
                "file", "review.tsv", "text/tab-separated-values", content.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, result.totalRows());
        assertEquals(1, result.updatedRows());
        assertEquals(1, result.skippedRows());
        assertEquals(0, result.errors().size());
        verify(jdbcTemplate).batchUpdate(anyString(), anyList());
    }

    @Test
    void shouldRejectInvalidSensitivityWithoutUpdating() {
        String content = "字段ID\t源Schema\t源表\t源字段\t数据类型\t业务中文名\t业务口径\t数据分类\t敏感等级\t增量字段\t确认状态\t定义来源\n" +
                "101\tdbo\tcomProduct\tProdID\tvarchar\t产品编号\t\t主数据\t未知\t否\tCONFIRMED\tRULE\n";

        MetadataImportRespVO result = service.importReviewTsv(7L, new MockMultipartFile(
                "file", "review.tsv", "text/tab-separated-values", content.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, result.totalRows());
        assertEquals(0, result.updatedRows());
        assertEquals(1, result.errors().size());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void shouldResolveConfiguredFieldToVerifiedPhysicalColumn() {
        assertEquals("ClassID", DataPlatformMetadataServiceImpl.resolveEvidenceField(
                "HrmClass", "HrmClassID", List.of("ClassID", "ClassName")));
        assertEquals("ClassName", DataPlatformMetadataServiceImpl.resolveEvidenceField(
                "HrmClass", "ClassName", List.of("ClassID", "ClassName")));
        assertEquals(null, DataPlatformMetadataServiceImpl.resolveEvidenceField(
                "HrmClass", "UnknownField", List.of("ClassID", "ClassName")));
    }
}
