package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiSystemDictDataDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 系统字典数据 Mapper。
 */
@Mapper
public interface AiSystemDictDataMapper extends BaseMapper<AiSystemDictDataDO> {

    @Select("""
            <script>
            SELECT id, sort, label, value, dict_type, status, remark, deleted
            FROM system_dict_data
            WHERE deleted = 0
              AND status = 0
              AND dict_type IN
              <foreach collection="dictTypes" item="dictType" open="(" separator="," close=")">
                  #{dictType}
              </foreach>
            ORDER BY dict_type ASC, sort ASC, id ASC
            </script>
            """)
    List<AiSystemDictDataDO> selectEnabledByDictTypes(@Param("dictTypes") Collection<String> dictTypes);

}
