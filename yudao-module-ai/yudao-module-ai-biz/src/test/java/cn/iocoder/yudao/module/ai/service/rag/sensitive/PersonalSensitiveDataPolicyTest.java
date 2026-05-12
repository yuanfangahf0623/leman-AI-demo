package cn.iocoder.yudao.module.ai.service.rag.sensitive;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiSystemDictDataDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSystemDictDataMapper;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonalSensitiveDataPolicyTest {

    private final PersonalSensitiveDataPolicy policy = new PersonalSensitiveDataPolicy(null);

    @Test
    void shouldMatchSalaryAndIdCardQuestion() {
        assertTrue(policy.isSensitiveQuestion("张增波工资是多少？", "张增波工资是多少"));
        assertTrue(policy.isSensitiveQuestion("张增波身份证号是多少？", "张增波身份证号是多少"));
        assertFalse(policy.isSensitiveQuestion("现有技术栈有哪些？", "现有技术栈有哪些"));
    }

    @Test
    void shouldMatchPersonalSensitiveHit() {
        assertTrue(policy.isPersonalSensitiveHit(KnowledgeHit.builder()
                .documentTitle("绩效考核制度")
                .content("Sheet: 温春雨\n绩效目标确认单\n薪资结构：车间主任综合薪资 16000元/月。")
                .metadata(Map.of("title", "绩效考核制度"))
                .build()));
        assertTrue(policy.isPersonalSensitiveHit(KnowledgeHit.builder()
                .documentTitle("员工档案")
                .content("员工姓名：温春雨，身份证号：370100199001011234。")
                .metadata(Map.of("title", "员工档案"))
                .build()));
        assertFalse(policy.isPersonalSensitiveHit(KnowledgeHit.builder()
                .documentTitle("技术文档")
                .content("后端使用 Java、Spring Boot、MyBatis Plus。")
                .metadata(Map.of("title", "技术文档"))
                .build()));
    }

    @Test
    void shouldLoadRulesFromSystemDictData() {
        AiSystemDictDataMapper dictDataMapper = mock(AiSystemDictDataMapper.class);
        when(dictDataMapper.selectEnabledByDictTypes(anyCollection())).thenReturn(List.of(
                buildDictData(PersonalSensitiveDataPolicy.DICT_TYPE_QUESTION_KEYWORD, "mobile:手机号"),
                buildDictData(PersonalSensitiveDataPolicy.DICT_TYPE_HIT_MARKER, "mobile:手机号"),
                buildDictData(PersonalSensitiveDataPolicy.DICT_TYPE_VALUE_PATTERN, "mobile:1[3-9]\\d{9}")
        ));
        PersonalSensitiveDataPolicy dictPolicy = new PersonalSensitiveDataPolicy(dictDataMapper);

        assertTrue(dictPolicy.isSensitiveQuestion("张三手机号是多少？", "张三手机号是多少"));
        assertTrue(dictPolicy.isPersonalSensitiveHit(KnowledgeHit.builder()
                .documentTitle("员工档案")
                .content("员工姓名：张三，手机号：13800000000。")
                .metadata(Map.of("title", "员工档案"))
                .build()));
        assertFalse(dictPolicy.isSensitiveQuestion("现有技术栈有哪些？", "现有技术栈有哪些"));
    }

    private AiSystemDictDataDO buildDictData(String dictType, String value) {
        AiSystemDictDataDO dictData = new AiSystemDictDataDO();
        dictData.setId(1L);
        dictData.setDictType(dictType);
        dictData.setValue(value);
        dictData.setStatus(0);
        dictData.setDeleted(false);
        return dictData;
    }

}
