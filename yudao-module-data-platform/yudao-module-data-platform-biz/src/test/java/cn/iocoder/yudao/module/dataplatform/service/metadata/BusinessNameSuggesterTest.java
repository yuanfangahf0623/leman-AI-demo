package cn.iocoder.yudao.module.dataplatform.service.metadata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessNameSuggesterTest {

    private final BusinessNameSuggester suggester = new BusinessNameSuggester();

    @Test
    void shouldSuggestCoreTableAndFieldNames() {
        assertEquals("产品", suggester.suggestTableName("comProduct"));
        assertEquals("订单主表", suggester.suggestTableName("ordBillMain"));
        assertEquals("产品编码", suggester.suggestFieldName("ProductCode", null));
        assertEquals("源库中文备注", suggester.suggestFieldName("UnknownField", " 源库中文备注 "));
    }

    @Test
    void shouldClassifySensitiveAndIncrementalFields() {
        assertEquals("RESTRICTED", suggester.suggestSensitivity("ApiSecret"));
        assertEquals("SENSITIVE", suggester.suggestSensitivity("MobilePhone"));
        assertEquals("INTERNAL", suggester.suggestSensitivity("ProductName"));
        assertTrue(suggester.isIncrementalCandidate("UpdateTime", "datetime"));
        assertFalse(suggester.isIncrementalCandidate("Status", "int"));
    }
}
